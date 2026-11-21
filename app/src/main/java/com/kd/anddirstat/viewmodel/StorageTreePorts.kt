package com.kd.anddirstat.viewmodel

import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.util.StorageVolumeInfo

data class StorageFilterSettings(
    val showFreeSpace: Boolean = true,
    val showSystemApps: Boolean = true,
    val showHiddenFiles: Boolean = false,
    val showSystemOS: Boolean = false
)

interface TreeStore {
    suspend fun load(): CompactNode?
    suspend fun save(tree: CompactNode)
}

interface ScanProgressSink {
    fun update(title: String, detail: String, progress: Int, max: Int, indeterminate: Boolean)
    fun finish(message: String? = null)
}

interface StoragePermissionGate {
    fun storageAllowed(): Boolean
    fun usageAllowed(): Boolean
}

interface TreeScannerPort {
    suspend fun scan(
        selectedVolumes: List<StorageVolumeInfo>,
        includeFreeSpace: Boolean,
        scanApps: Boolean,
        onProgress: (String, String, Int, Int) -> Unit
    ): CompactNode
}

interface SnapshotSink {
    fun record(totalBytes: Long, usedBytes: Long)
}

interface IconWarmer {
    fun warm(packageNames: List<String>)
}
