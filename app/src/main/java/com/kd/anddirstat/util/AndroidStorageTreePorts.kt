package com.kd.anddirstat.util

import android.content.Context
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.scanner.StorageScanner
import com.kd.anddirstat.scanner.TreeCacheManager
import com.kd.anddirstat.ui.components.AppIconCache
import com.kd.anddirstat.viewmodel.IconWarmer
import com.kd.anddirstat.viewmodel.ScanProgressSink
import com.kd.anddirstat.viewmodel.SnapshotSink
import com.kd.anddirstat.viewmodel.StoragePermissionGate
import com.kd.anddirstat.viewmodel.TreeScannerPort
import com.kd.anddirstat.viewmodel.TreeStore

class AndroidTreeStore(private val context: Context) : TreeStore {
    override suspend fun load(): CompactNode? = TreeCacheManager.loadTree(context)
    override suspend fun save(tree: CompactNode) {
        TreeCacheManager.saveTree(context, tree)
    }
}

class AndroidScanProgress(private val context: Context) : ScanProgressSink {
    override fun update(title: String, detail: String, progress: Int, max: Int, indeterminate: Boolean) {
        AppNotifier.updateProgress(
            context = context,
            title = title,
            detail = detail,
            progress = progress,
            max = max,
            indeterminate = indeterminate,
            type = "scan"
        )
    }

    override fun finish(message: String?) = AppNotifier.finishActivity(context, message)
}

class AndroidPermissionGate(private val context: Context) : StoragePermissionGate {
    override fun storageAllowed(): Boolean = FileUtils.checkStoragePermission(context)
    override fun usageAllowed(): Boolean = FileUtils.checkUsageAccessPermission(context)
}

class AndroidTreeScanner(private val context: Context) : TreeScannerPort {
    override suspend fun scan(
        selectedVolumes: List<StorageVolumeInfo>,
        includeFreeSpace: Boolean,
        scanApps: Boolean,
        onProgress: (String, String, Int, Int) -> Unit
    ): CompactNode = StorageScanner(context).scanStorage(
        selectedVolumes = selectedVolumes,
        includeFreeSpace = includeFreeSpace,
        scanApps = scanApps,
        onProgress = onProgress
    )
}

class AndroidSnapshotSink(private val context: Context) : SnapshotSink {
    override fun record(totalBytes: Long, usedBytes: Long) =
        StorageTrendManager.recordSnapshot(context, totalBytes, usedBytes)
}

class AndroidIconWarmer(private val context: Context) : IconWarmer {
    override fun warm(packageNames: List<String>) {
        packageNames.forEach { pkg ->
            if (AppIconCache.peek(pkg) == null) AppIconCache.get(context, pkg)
        }
    }
}
