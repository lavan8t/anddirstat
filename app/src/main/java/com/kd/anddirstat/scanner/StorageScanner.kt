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
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class StorageScanner(private val context: Context) {

    // Strictly bounded to maximum 3 worker threads for minimal memory and CPU contention
    private val scanDispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()

    suspend fun scanStorage(
        includeFreeSpace: Boolean = true,
        useCacheIfValid: Boolean = false,
        onProgress: ((phase: String, detail: String) -> Unit)? = null
    ): CompactNode = withContext(scanDispatcher) {
        if (useCacheIfValid) {
            val cached = TreeCacheManager.loadTree(context)
            if (cached != null) {
                return@withContext cached
            }
        }

        val dataDir = Environment.getDataDirectory()
        val stat = StatFs(dataDir.path)
        val deviceTotalBytes = stat.totalBytes
        val freeBytes = stat.availableBytes
        val targetUsedBytes = maxOf(1L, deviceTotalBytes - freeBytes)

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

            onProgress?.invoke("$phase$etaStr", detail)
        }

        // Parallel scan: Run applications and storage directories across max 3 threads
        val filesDeferred = async(scanDispatcher) {
            updateProgress("Scanning Files", "Reading storage...")
            val rootPath = Environment.getExternalStorageDirectory()
            val mediaRootNode = CompactNode(name = "Files", isDirectory = true)
            scanDir(rootPath, mediaRootNode, totalScannedBytes, scannedFilesCount) { phase, detail ->
                updateProgress(phase, detail)
            }
            mediaRootNode
        }

        val appsDeferred = async(scanDispatcher) {
            updateProgress("Scanning Applications", "Enumerating packages...")
            scanAllInstalledApplications(totalScannedBytes) { phase, detail ->
                updateProgress(phase, detail)
            }
        }

        val mediaRootNode = filesDeferred.await()
        val (appsNode, totalAppsSize) = appsDeferred.await()

        updateProgress("Finalizing", "Assembling storage layout...")
        val accounted = mediaRootNode.size + totalAppsSize + freeBytes
        val systemSize = if (deviceTotalBytes > accounted) (deviceTotalBytes - accounted) else 0L

        val rootChildren = mutableListOf<CompactNode>()

        if (systemSize > 0L) {
            rootChildren.add(
                CompactNode(
                    name = "[System & OS]",
                    isDirectory = false,
                    size = systemSize
                )
            )
        }

        if (includeFreeSpace && freeBytes > 0L) {
            rootChildren.add(
                CompactNode(
                    name = "[Free Space]",
                    isDirectory = false,
                    size = freeBytes
                )
            )
        }

        if (appsNode != null && appsNode.size > 0L) {
            rootChildren.add(appsNode)
        }

        if (mediaRootNode.size > 0L) {
            rootChildren.add(mediaRootNode)
        }

        val effectiveTotal = if (includeFreeSpace) {
            deviceTotalBytes
        } else {
            systemSize + totalAppsSize + mediaRootNode.size
        }

        val finalRoot = CompactNode(
            name = "Device Storage",
            isDirectory = true,
            size = effectiveTotal,
            children = rootChildren.toTypedArray()
        )

        // Save tree to persistent cache for instant startup
        TreeCacheManager.saveTree(context, finalRoot)

        finalRoot
    }

    private fun scanAllInstalledApplications(
        totalScannedBytes: AtomicLong,
        onProgress: ((phase: String, detail: String) -> Unit)? = null
    ): Pair<CompactNode?, Long> {
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

            if (codeSize == 0L) {
                try {
                    val baseApk = File(appInfo.sourceDir)
                    if (baseApk.exists()) {
                        codeSize += baseApk.length()
                    }
                    appInfo.splitSourceDirs?.forEach { splitPath ->
                        val splitFile = File(splitPath)
                        if (splitFile.exists()) {
                            codeSize += splitFile.length()
                        }
                    }
                } catch (_: Exception) {
                }
            }

            if (dataSize == 0L && cacheSize == 0L) {
                try {
                    val extData = File("/storage/emulated/0/Android/data/${appInfo.packageName}")
                    if (extData.exists()) {
                        dataSize += getDirSize(extData)
                    }
                    val extMedia = File("/storage/emulated/0/Android/media/${appInfo.packageName}")
                    if (extMedia.exists()) {
                        dataSize += getDirSize(extMedia)
                    }
                    val extObb = File("/storage/emulated/0/Android/obb/${appInfo.packageName}")
                    if (extObb.exists()) {
                        dataSize += getDirSize(extObb)
                    }
                } catch (_: Exception) {
                }
            }

            if (codeSize > 0L) {
                appParts.add(CompactNode(name = "App Code (${appInfo.packageName}.apk)", isDirectory = false, size = codeSize))
                appTotal += codeSize
            }
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
                val fullLabel = if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) "$label (System)" else label
                appParts.sortByDescending { it.size }
                appNodes.add(
                    CompactNode(
                        name = fullLabel,
                        isDirectory = true,
                        size = appTotal,
                        children = if (appParts.isNotEmpty()) appParts.toTypedArray() else null
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
