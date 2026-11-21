package com.kd.anddirstat.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.ExtensionStat
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.scanner.StorageFilterHelper
import com.kd.anddirstat.util.FileUtils
import com.kd.anddirstat.util.StorageVolumeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SCAN_PUBLISH_INTERVAL_MS = 250L
private const val SCAN_PHASE_START = "Scanning Storage..."
private const val SCAN_DETAIL_START = "Reading volumes & indexing directories"
private const val SCAN_PHASE_FINALIZING = "Finalizing Storage Tree..."
private const val SCAN_DETAIL_FINALIZING = "Building category breakdowns & extension stats"

data class StorageTreeUiState(
    val rawScannedNode: CompactNode? = null,
    val deviceTotalBytes: Long = 0L,
    val rootNode: CompactNode? = null,
    val extensionStats: List<ExtensionStat> = emptyList(),
    val topFiles: List<TopFileEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isBackgroundScanning: Boolean = false,
    val scanPhase: String = SCAN_PHASE_START,
    val scanDetail: String = SCAN_DETAIL_START,
    val scanProgress: Int = 0
)

class StorageTreeViewModel(
    private val treeStore: TreeStore,
    private val progressSink: ScanProgressSink,
    private val permissions: StoragePermissionGate,
    private val scanner: TreeScannerPort,
    private val snapshots: SnapshotSink,
    private val icons: IconWarmer
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageTreeUiState())
    val uiState: StateFlow<StorageTreeUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    fun restoreOrScan(
        volumes: List<StorageVolumeInfo>,
        scanApps: Boolean,
        settings: StorageFilterSettings
    ) {
        if (_uiState.value.rootNode != null) return
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    scanPhase = "Restoring storage map...",
                    scanDetail = "Loading saved data...",
                    scanProgress = 0
                )
            }
            val cached = withContext(Dispatchers.IO) { treeStore.load() }
            if (cached != null) {
                val (filtered, stats, top) = filterTree(cached, settings)
                _uiState.update {
                    it.copy(
                        rawScannedNode = cached,
                        deviceTotalBytes = cached.size,
                        rootNode = filtered,
                        extensionStats = stats,
                        topFiles = top,
                        isLoading = false
                    )
                }
            } else if (volumes.isNotEmpty()) {
                performScan(volumes, scanApps, settings)
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun performScan(
        volumesToScan: List<StorageVolumeInfo>,
        scanApps: Boolean,
        settings: StorageFilterSettings
    ) {
        val effectiveScanApps = scanApps && permissions.usageAllowed()
        if (!permissions.storageAllowed() || (volumesToScan.isEmpty() && !effectiveScanApps)) return

        val hasTree = _uiState.value.rootNode != null
        _uiState.update {
            it.copy(
                isLoading = !hasTree,
                isBackgroundScanning = hasTree,
                scanPhase = SCAN_PHASE_START,
                scanDetail = SCAN_DETAIL_START,
                scanProgress = 0
            )
        }

        progressSink.update(SCAN_PHASE_START, SCAN_DETAIL_START, 0, 100, true)
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.Default) {
            var lastPublishMs = 0L
            val scanned = scanner.scan(volumesToScan, includeFreeSpace = true, scanApps = effectiveScanApps) { phase, detail, progress, max ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastPublishMs >= SCAN_PUBLISH_INTERVAL_MS) {
                    lastPublishMs = now
                    _uiState.update { it.copy(scanPhase = phase, scanDetail = detail, scanProgress = progress) }
                    progressSink.update(phase, detail, progress, max, false)
                }
            }

            _uiState.update {
                it.copy(
                    rawScannedNode = scanned,
                    deviceTotalBytes = scanned.size,
                    scanPhase = SCAN_PHASE_FINALIZING,
                    scanDetail = SCAN_DETAIL_FINALIZING,
                    scanProgress = 100
                )
            }
            progressSink.update(SCAN_PHASE_FINALIZING, SCAN_DETAIL_FINALIZING, 100, 100, false)

            val (filtered, computedStats, computedTopFiles) = filterTree(scanned, settings)
            _uiState.update {
                it.copy(
                    rootNode = filtered,
                    extensionStats = computedStats,
                    topFiles = computedTopFiles,
                    isLoading = false,
                    isBackgroundScanning = false
                )
            }
            progressSink.finish()

            launch(Dispatchers.IO) {
                snapshots.record(scanned.size, scanned.size)
                val packageNames = scanned.children
                    ?.firstOrNull { it.name == "Apps & System Packages" }
                    ?.children
                    ?.mapNotNull { FileUtils.extractPackageName(it) }
                    ?: emptyList()
                icons.warm(packageNames)
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _uiState.update { it.copy(isLoading = false, isBackgroundScanning = false) }
    }

    fun updateFilter(settings: StorageFilterSettings) {
        val raw = _uiState.value.rawScannedNode ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val (filtered, stats, top) = filterTree(raw, settings)
            _uiState.update {
                it.copy(rootNode = filtered, extensionStats = stats, topFiles = top)
            }
        }
    }

    fun removeDeletedNodes(deletedNodes: Set<CompactNode>, settings: StorageFilterSettings) {
        if (deletedNodes.isEmpty()) return
        val currentRaw = _uiState.value.rawScannedNode ?: return

        viewModelScope.launch(Dispatchers.Default) {
            val updatedRaw = StorageFilterHelper.pruneNodes(currentRaw, deletedNodes)
            val (filtered, stats, top) = filterTree(updatedRaw, settings)
            _uiState.update {
                it.copy(
                    rawScannedNode = updatedRaw,
                    deviceTotalBytes = updatedRaw.size,
                    rootNode = filtered,
                    extensionStats = stats,
                    topFiles = top
                )
            }
            withContext(Dispatchers.IO) { treeStore.save(updatedRaw) }
        }
    }

    private suspend fun filterTree(
        root: CompactNode,
        settings: StorageFilterSettings
    ): Triple<CompactNode?, List<ExtensionStat>, List<TopFileEntry>> = withContext(Dispatchers.Default) {
        val filtered = StorageFilterHelper.filterStorageTree(
            root,
            settings.showFreeSpace,
            settings.showSystemApps,
            settings.showHiddenFiles,
            settings.showSystemOS,
            root.size
        )
        if (filtered == null) {
            Triple(null, emptyList(), emptyList())
        } else {
            val stats = async { StorageFilterHelper.aggregateExtensionStats(filtered) }
            val top = async { StorageFilterHelper.aggregateTopFiles(filtered) }
            Triple(filtered, stats.await(), top.await())
        }
    }
}
