package com.kd.anddirstat.scanner

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import com.kd.anddirstat.model.CompactNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

import com.kd.anddirstat.util.StorageVolumeInfo
import com.kd.anddirstat.util.FileUtils

class StorageScanner(private val context: Context) {

    // Scaled thread pool matching CPU core count (minimum 4, maximum 8) for high parallel throughput
    private val scanDispatcher = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceIn(4, 8)
    ).asCoroutineDispatcher()

    suspend fun scanStorage(
        selectedVolumes: List<StorageVolumeInfo> = emptyList(),
        includeFreeSpace: Boolean = true,
        scanApps: Boolean = true,
        useCacheIfValid: Boolean = false,
        onProgress: ((phase: String, detail: String, progress: Int, max: Int) -> Unit)? = null
    ): CompactNode = withContext(scanDispatcher) {
        val volumesToScan = if (selectedVolumes.isNotEmpty()) selectedVolumes else FileUtils.getAvailableStorageVolumes(context)

        if (useCacheIfValid && volumesToScan.size == 1 && volumesToScan.first().isPrimary && scanApps) {
            val cached = TreeCacheManager.loadTree(context)
            if (cached != null) {
                return@withContext cached
            }
        }

        val primaryVol = volumesToScan.firstOrNull { it.isPrimary }
        val externalVols = volumesToScan.filter { !it.isPrimary }

        var totalDeviceBytes = volumesToScan.sumOf { it.totalBytes }
        var totalFreeBytes = volumesToScan.sumOf { it.freeBytes }

        if (primaryVol != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
                if (ssm != null) {
                    val sTotal = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
                    val sFree = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
                    if (sTotal > 0L) {
                        totalDeviceBytes = sTotal + externalVols.sumOf { it.totalBytes }
                        totalFreeBytes = sFree + externalVols.sumOf { it.freeBytes }
                    }
                }
            } catch (_: Exception) {}
        }

        if (totalDeviceBytes == 0L) {
            val dataDir = Environment.getDataDirectory()
            val stat = StatFs(dataDir.path)
            totalDeviceBytes = stat.totalBytes
            totalFreeBytes = stat.availableBytes
        }

        val targetUsedBytes = maxOf(1L, totalDeviceBytes - totalFreeBytes)
        val startTimeMs = System.currentTimeMillis()
        val totalScannedBytes = AtomicLong(0L)
        val scannedFilesCount = AtomicInteger(0)

        fun updateProgress(phase: String, detail: String) {
            val elapsedMs = System.currentTimeMillis() - startTimeMs
            val bytes = totalScannedBytes.get()
            val etaStr = if (elapsedMs > 600 && bytes > 1024 * 1024) {
                val bytesPerMs = bytes.toDouble() / elapsedMs.toDouble()
                val remainingBytes = maxOf(0L, targetUsedBytes - bytes)
                val etaSec = (remainingBytes / (bytesPerMs * 1000.0)).toInt()
                if (etaSec in 1..3600) " (~${etaSec}s left)" else ""
            } else ""

            val pct = if (targetUsedBytes > 0) ((bytes.toDouble() / targetUsedBytes.toDouble()) * 100.0).toInt().coerceIn(0, 99) else 0
            onProgress?.invoke("$phase$etaStr", detail, pct, 100)
        }

        val rootChildren = mutableListOf<CompactNode>()

        // 1. Scan Primary Internal Storage if selected
        if (primaryVol != null) {
            val filesDeferred = async(scanDispatcher) {
                updateProgress("Scanning Internal Storage", "Reading internal files...")
                val rootDir = primaryVol.path
                val mediaRootNode = CompactNode(name = "Files", isDirectory = true)
                scanVolumeRoot(rootDir, mediaRootNode, totalScannedBytes, scannedFilesCount) { phase, detail ->
                    updateProgress(phase, detail)
                }
                mediaRootNode
            }

            val hasUsageAccess = FileUtils.checkUsageAccessPermission(context)
            val appsDeferred = if (scanApps && hasUsageAccess) {
                async(scanDispatcher) {
                    updateProgress("Scanning Applications", "Enumerating packages...")
                    scanAllInstalledApplications(totalScannedBytes) { phase, detail ->
                        updateProgress(phase, detail)
                    }
                }
            } else null

            val mediaRootNode = filesDeferred.await()
            val (appsNode, totalAppsSize) = if (appsDeferred != null) appsDeferred.await() else (null to 0L)

            var tempSystemSize = 0L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
                    if (ssm != null) {
                        val userStats = ssm.queryStatsForUser(StorageManager.UUID_DEFAULT, android.os.Process.myUserHandle())
                        tempSystemSize = userStats.cacheBytes
                    }
                } catch (_: Exception) {}
            }
            if (tempSystemSize == 0L) {
                try {
                    val cacheDir = Environment.getDownloadCacheDirectory()
                    if (cacheDir != null && cacheDir.exists()) {
                        tempSystemSize = getDirSize(cacheDir)
                    }
                } catch (_: Exception) {}
            }

            updateProgress("Finalizing", "Assembling internal storage layout...")
            val accounted = mediaRootNode.size + totalAppsSize + totalFreeBytes + tempSystemSize
            val systemSize = if (totalDeviceBytes > accounted) (totalDeviceBytes - accounted) else 0L

            if (systemSize > 0L) {
                rootChildren.add(
                    CompactNode(
                        name = "[System & OS]",
                        isDirectory = false,
                        size = systemSize
                    )
                )
            }

            if (tempSystemSize > 0L) {
                rootChildren.add(
                    CompactNode(
                        name = "[Temporary System Files]",
                        isDirectory = false,
                        size = tempSystemSize
                    )
                )
            }

            if (includeFreeSpace && totalFreeBytes > 0L && externalVols.isEmpty()) {
                rootChildren.add(
                    CompactNode(
                        name = "[Free Space]",
                        isDirectory = false,
                        size = totalFreeBytes
                    )
                )
            }

            if (appsNode != null && appsNode.size > 0L) {
                rootChildren.add(appsNode)
            }

            if (mediaRootNode.size > 0L) {
                rootChildren.add(mediaRootNode)
            }
        }

        // 2. Scan Removable SD Cards & USB Drives
        for (extVol in externalVols) {
            updateProgress("Scanning ${extVol.name}", "Reading external volume files...")
            val volRootNode = CompactNode(name = extVol.name, isDirectory = true)
            scanVolumeRoot(extVol.path, volRootNode, totalScannedBytes, scannedFilesCount) { phase, detail ->
                updateProgress(phase, detail)
            }
            if (volRootNode.size > 0L) {
                rootChildren.add(volRootNode)
            }
        }

        if (includeFreeSpace && totalFreeBytes > 0L && externalVols.isNotEmpty()) {
            rootChildren.add(
                CompactNode(
                    name = "[Free Space]",
                    isDirectory = false,
                    size = totalFreeBytes
                )
            )
        }

        val effectiveTotal = if (includeFreeSpace) {
            totalDeviceBytes
        } else {
            rootChildren.sumOf { it.size }
        }

        val rootName = if (volumesToScan.size == 1) volumesToScan.first().name else "Device Storage"

        val finalRoot = CompactNode(
            name = rootName,
            isDirectory = true,
            size = effectiveTotal,
            children = rootChildren.toTypedArray()
        )

        // Save tree to persistent cache in background IO thread
        if (volumesToScan.size == 1 && volumesToScan.first().isPrimary) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    TreeCacheManager.saveTree(context, finalRoot)
                } catch (_: Exception) {}
            }
        }

        finalRoot
    }

    private fun scanAllInstalledApplications(
        totalScannedBytes: AtomicLong,
        onProgress: ((phase: String, detail: String) -> Unit)? = null
    ): Pair<CompactNode?, Long> {
        if (!FileUtils.checkUsageAccessPermission(context)) {
            return null to 0L
        }

        val pm = context.packageManager
        val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        val userHandle = Process.myUserHandle()

        val apps = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of((PackageManager.MATCH_UNINSTALLED_PACKAGES or 0).toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES or 0)
            }
        } catch (_: Exception) {
            emptyList()
        }

        val appNodes = mutableListOf<CompactNode>()
        var totalAppsSize = 0L

        for (appInfo in apps) {
            val label = try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                appInfo.packageName
            }
            onProgress?.invoke("Scanning Applications", label)

            var appTotal = 0L
            val appParts = mutableListOf<CompactNode>()

            var codeSize = 0L
            var dataSize = 0L
            var cacheSize = 0L

            if (storageStatsManager != null) {
                try {
                    val uuid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            appInfo.storageUuid ?: StorageManager.UUID_DEFAULT
                        } catch (_: Exception) {
                            StorageManager.UUID_DEFAULT
                        }
                    } else {
                        StorageManager.UUID_DEFAULT
                    }

                    val stats = storageStatsManager.queryStatsForPackage(
                        uuid,
                        appInfo.packageName,
                        userHandle
                    )
                    codeSize = stats.appBytes
                    cacheSize = stats.cacheBytes
                    dataSize = maxOf(0L, stats.dataBytes - cacheSize)
                } catch (_: Exception) {
                }
            }

            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val fullLabel = label
            FileUtils.AppPackageRegistry.register(label, appInfo.packageName, isSystem = isSystemApp)

            appParts.add(CompactNode(name = "App Code (${appInfo.packageName}.apk)", isDirectory = false, size = maxOf(0L, codeSize)))
            appTotal += codeSize

            if (dataSize > 0L) {
                appParts.add(CompactNode(name = "Data", isDirectory = false, size = dataSize))
                appTotal += dataSize
            }
            if (cacheSize > 0L) {
                appParts.add(CompactNode(name = "Cache", isDirectory = false, size = cacheSize))
                appTotal += cacheSize
            }

            if (appTotal > 0L) {
                totalScannedBytes.addAndGet(appTotal)
                appParts.sortByDescending { it.size }
                appNodes.add(
                    CompactNode(
                        name = fullLabel,
                        isDirectory = true,
                        size = appTotal,
                        children = appParts.toTypedArray()
                    )
                )
                totalAppsSize += appTotal
            }
        }

        if (appNodes.isEmpty()) return Pair(null, 0L)
        appNodes.sortByDescending { it.size }
        return Pair(
            CompactNode(
                name = "Apps & System Packages",
                isDirectory = true,
                size = totalAppsSize,
                children = appNodes.toTypedArray()
            ),
            totalAppsSize
        )
    }

    private fun getDirSize(dir: File): Long {
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) getDirSize(f) else f.length()
        }
        return size
    }

    private suspend fun scanVolumeRoot(
        rootDir: File,
        parentNode: CompactNode,
        totalScannedBytes: AtomicLong,
        scannedFilesCount: AtomicInteger,
        onProgress: ((phase: String, detail: String) -> Unit)? = null
    ) = withContext(scanDispatcher) {
        val entries = rootDir.listFiles() ?: return@withContext
        val deferredList = entries.map { entry ->
            async(scanDispatcher) {
                if (entry.isDirectory) {
                    val count = scannedFilesCount.incrementAndGet()
                    if (count % 30 == 0) {
                        onProgress?.invoke("Scanning Files", entry.name)
                    }
                    val dirNode = CompactNode(name = entry.name, isDirectory = true)
                    scanDir(entry, dirNode, totalScannedBytes, scannedFilesCount, onProgress)
                    if (dirNode.size > 0L) dirNode else null
                } else {
                    val fileSize = entry.length()
                    if (fileSize > 0L) {
                        totalScannedBytes.addAndGet(fileSize)
                        scannedFilesCount.incrementAndGet()
                        CompactNode(name = entry.name, isDirectory = false, size = fileSize)
                    } else null
                }
            }
        }

        val children = deferredList.mapNotNull { it.await() }.sortedByDescending { it.size }
        if (children.isNotEmpty()) {
            parentNode.children = children.toTypedArray()
            parentNode.size = children.sumOf { it.size }
        } else {
            parentNode.size = 0L
        }
    }

    private fun scanDir(
        dir: File,
        parentNode: CompactNode,
        totalScannedBytes: AtomicLong,
        scannedFilesCount: AtomicInteger,
        onProgress: ((phase: String, detail: String) -> Unit)? = null
    ) {
        val entries = dir.listFiles() ?: return
        val tempChildren = mutableListOf<CompactNode>()
        var accumulatedSize = 0L

        for (entry in entries) {
            if (entry.isDirectory) {
                val count = scannedFilesCount.incrementAndGet()
                if (count % 30 == 0) {
                    val rel = entry.path.removePrefix("/storage/emulated/0/").removePrefix("/storage/emulated/0")
                    onProgress?.invoke("Scanning Files", rel.ifEmpty { entry.name })
                }
                val dirNode = CompactNode(name = entry.name, isDirectory = true)
                scanDir(entry, dirNode, totalScannedBytes, scannedFilesCount, onProgress)
                if (dirNode.size > 0L) {
                    tempChildren.add(dirNode)
                    accumulatedSize += dirNode.size
                }
            } else {
                val fileSize = entry.length()
                if (fileSize > 0L) {
                    tempChildren.add(CompactNode(name = entry.name, isDirectory = false, size = fileSize))
                    accumulatedSize += fileSize
                    totalScannedBytes.addAndGet(fileSize)
                }
                scannedFilesCount.incrementAndGet()
            }
        }

        tempChildren.sortByDescending { it.size }
        if (tempChildren.isNotEmpty()) parentNode.children = tempChildren.toTypedArray()
        parentNode.size = accumulatedSize
    }
}
