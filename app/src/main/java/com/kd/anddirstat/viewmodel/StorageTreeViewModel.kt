package com.kd.anddirstat.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.ExtensionStat
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.scanner.StorageFilterHelper
import com.kd.anddirstat.scanner.StorageScanner
import com.kd.anddirstat.scanner.TreeCacheManager
import com.kd.anddirstat.ui.components.AppIconCache
import com.kd.anddirstat.util.AppNotifier
import com.kd.anddirstat.util.FileUtils
import com.kd.anddirstat.util.StorageTrendManager
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

class StorageTreeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(StorageTreeUiState())
    val uiState: StateFlow<StorageTreeUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    fun restoreOrScan(
        context: Context,
        volumes: List<StorageVolumeInfo>,
        scanApps: Boolean,
        prefs: SharedPreferences
    ) {
        if (_uiState.value.rootNode != null) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    scanPhase = "Restoring storage map...",
                    scanDetail = "Loading saved data...",
                    scanProgress = 0
                )
            }
            val cached = withContext(Dispatchers.IO) {
                try { TreeCacheManager.loadTree(context) } catch (_: Exception) { null }
            }
            if (cached != null) {
                val showFreeSpace = prefs.getBoolean("show_free_space", true)
                val showSystemApps = prefs.getBoolean("show_system_apps", true)
                val showHiddenFiles = prefs.getBoolean("show_hidden_files", false)
                val showSystemOS = prefs.getBoolean("show_system_os", false)
                val (filtered, stats, top) = withContext(Dispatchers.Default) {
                    val f = StorageFilterHelper.filterStorageTree(cached, showFreeSpace, showSystemApps, showHiddenFiles, showSystemOS, cached.size)
                    if (f != null) {
                        val s = async { StorageFilterHelper.aggregateExtensionStats(f) }
                        val t = async { StorageFilterHelper.aggregateTopFiles(f) }
                        Triple(f, s.await(), t.await())
                    } else Triple(null, emptyList(), emptyList())
                }
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
                performScan(context, volumes, scanApps, prefs)
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun performScan(
        context: Context,
        volumesToScan: List<StorageVolumeInfo>,
        scanApps: Boolean,
        prefs: SharedPreferences
    ) {
        val effectiveScanApps = scanApps && FileUtils.checkUsageAccessPermission(context)
        if (!FileUtils.checkStoragePermission(context) || (volumesToScan.isEmpty() && !effectiveScanApps)) return

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

        AppNotifier.updateProgress(context, title = SCAN_PHASE_START, detail = SCAN_DETAIL_START, indeterminate = true, type = "scan")
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val showFreeSpace = prefs.getBoolean("show_free_space", true)
            val showSystemApps = prefs.getBoolean("show_system_apps", true)
            val showHiddenFiles = prefs.getBoolean("show_hidden_files", false)
            val showSystemOS = prefs.getBoolean("show_system_os", false)

            val scanner = StorageScanner(context)
            var lastPublishMs = 0L
            val scanned = scanner.scanStorage(selectedVolumes = volumesToScan, includeFreeSpace = true, scanApps = effectiveScanApps) { phase, detail, progress, max ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastPublishMs >= SCAN_PUBLISH_INTERVAL_MS) {
                    lastPublishMs = now
                    _uiState.update { it.copy(scanPhase = phase, scanDetail = detail, scanProgress = progress) }
                    AppNotifier.updateProgress(context, title = phase, detail = detail, progress = progress, max = max, indeterminate = false, type = "scan")
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
            AppNotifier.updateProgress(context, title = SCAN_PHASE_FINALIZING, detail = SCAN_DETAIL_FINALIZING, progress = 100, max = 100, indeterminate = false, type = "scan")

            val (filtered, computedStats, computedTopFiles) = withContext(Dispatchers.Default) {
                val f = StorageFilterHelper.filterStorageTree(scanned, showFreeSpace, showSystemApps, showHiddenFiles, showSystemOS, scanned.size)
                if (f != null) {
                    val statsJob = async { StorageFilterHelper.aggregateExtensionStats(f) }
                    val topJob = async { StorageFilterHelper.aggregateTopFiles(f) }
                    Triple(f, statsJob.await(), topJob.await())
                } else {
                    Triple(null, emptyList(), emptyList())
                }
            }

            _uiState.update {
                it.copy(
                    rootNode = filtered,
                    extensionStats = computedStats,
                    topFiles = computedTopFiles,
                    isLoading = false,
                    isBackgroundScanning = false
                )
            }
            AppNotifier.finishActivity(context)

            launch(Dispatchers.IO) {
                StorageTrendManager.recordSnapshot(context, scanned.size, scanned.size)
                scanned.children?.firstOrNull { it.name == "Apps & System Packages" }?.children?.forEach { appNode ->
                    val pkg = FileUtils.extractPackageName(appNode)
                    if (pkg != null && AppIconCache.peek(pkg) == null) AppIconCache.get(context, pkg)
                }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _uiState.update { it.copy(isLoading = false, isBackgroundScanning = false) }
    }

    fun updateFilter(
        prefs: SharedPreferences,
        fs: Boolean? = null,
        sa: Boolean? = null,
        hf: Boolean? = null,
        so: Boolean? = null
    ) {
        val raw = _uiState.value.rawScannedNode ?: return
        val showFreeSpace = fs ?: prefs.getBoolean("show_free_space", true)
        val showSystemApps = sa ?: prefs.getBoolean("show_system_apps", true)
        val showHiddenFiles = hf ?: prefs.getBoolean("show_hidden_files", false)
        val showSystemOS = so ?: prefs.getBoolean("show_system_os", false)

        viewModelScope.launch(Dispatchers.Default) {
            val filtered = StorageFilterHelper.filterStorageTree(raw, showFreeSpace, showSystemApps, showHiddenFiles, showSystemOS, _uiState.value.deviceTotalBytes)
            val newStats = if (filtered != null) StorageFilterHelper.aggregateExtensionStats(filtered) else emptyList()
            val newTop = if (filtered != null) StorageFilterHelper.aggregateTopFiles(filtered) else emptyList()
            _uiState.update {
                it.copy(
                    rootNode = filtered,
                    extensionStats = newStats,
                    topFiles = newTop
                )
            }
        }
    }

    fun removeDeletedNodes(context: Context, deletedNodes: Set<CompactNode>, prefs: SharedPreferences) {
        if (deletedNodes.isEmpty()) return
        val currentRaw = _uiState.value.rawScannedNode ?: return
        val showFreeSpace = prefs.getBoolean("show_free_space", true)
        val showSystemApps = prefs.getBoolean("show_system_apps", true)
        val showHiddenFiles = prefs.getBoolean("show_hidden_files", false)
        val showSystemOS = prefs.getBoolean("show_system_os", false)

        viewModelScope.launch(Dispatchers.Default) {
            val updatedRaw = StorageFilterHelper.pruneNodes(currentRaw, deletedNodes)
            val filtered = StorageFilterHelper.filterStorageTree(updatedRaw, showFreeSpace, showSystemApps, showHiddenFiles, showSystemOS, updatedRaw.size)
            val newStats = async { if (filtered != null) StorageFilterHelper.aggregateExtensionStats(filtered) else emptyList() }
            val newTop = async { if (filtered != null) StorageFilterHelper.aggregateTopFiles(filtered) else emptyList() }
            val computedStats = newStats.await()
            val computedTop = newTop.await()

            _uiState.update {
                it.copy(
                    rawScannedNode = updatedRaw,
                    deviceTotalBytes = updatedRaw.size,
                    rootNode = filtered,
                    extensionStats = computedStats,
                    topFiles = computedTop
                )
            }
            withContext(Dispatchers.IO) {
                try { TreeCacheManager.saveTree(context, updatedRaw) } catch (_: Exception) {}
            }
        }
    }
}
