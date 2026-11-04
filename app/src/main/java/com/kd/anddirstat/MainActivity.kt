package com.kd.anddirstat

import android.Manifest
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import com.kd.anddirstat.viewmodel.StorageTreeViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kd.anddirstat.model.AppDestinations
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.ExtensionStat
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.scanner.StorageFilterHelper
import com.kd.anddirstat.scanner.StorageScanner
import com.kd.anddirstat.scanner.TreeCacheManager
import com.kd.anddirstat.ui.components.AppIconCache
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.ui.components.DeletionProgressDialog
import com.kd.anddirstat.ui.components.DiscoverSearchTopAppBar
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.ui.components.StackedSnackbarHost
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import com.kd.anddirstat.cleanup.CleanupQueueHelper
import com.kd.anddirstat.cleanup.StagedQueueItem
import com.kd.anddirstat.ui.components.CleanupQueueReviewSheet
import com.kd.anddirstat.ui.components.PredictiveSlideBackContainer
import com.kd.anddirstat.ui.components.ScanningBanner
import com.kd.anddirstat.ui.components.TreeDeleteDialog
import com.kd.anddirstat.ui.components.TreemapNavPill
import com.kd.anddirstat.ui.components.TreemapNavRail
import com.kd.anddirstat.ui.components.TreemapTilePopup
import com.kd.anddirstat.ui.components.VolumeSelectionBottomSheet
import com.kd.anddirstat.ui.screens.DiscoverView
import com.kd.anddirstat.ui.screens.ExplorerView
import com.kd.anddirstat.ui.screens.FileTypesView
import com.kd.anddirstat.ui.screens.LargestFilesScreen
import com.kd.anddirstat.ui.screens.LoadingScreen
import com.kd.anddirstat.ui.screens.PermissionScreen
import com.kd.anddirstat.ui.screens.SettingsScreen
import com.kd.anddirstat.ui.screens.TreemapScreen
import com.kd.anddirstat.ui.screens.cleaners.*
import com.kd.anddirstat.treemap.TreemapBitmapCache
import com.kd.anddirstat.treemap.TreemapCanvas
import com.kd.anddirstat.util.AppNotifier
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileUtils
import com.kd.anddirstat.util.StorageTrendManager
import com.kd.anddirstat.util.StorageVolumeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val viewModel: StorageTreeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_right, R.anim.slide_out_left)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { MainApp(viewModel) }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        TreemapBitmapCache.trim(level)
    }
}

private const val SCAN_PUBLISH_INTERVAL_MS = 250L
private const val SCAN_PHASE_START = "Analyzing storage..."
private const val SCAN_DETAIL_START = "Starting scan..."
private const val SCAN_PHASE_FINALIZING = "Finalizing"
private const val SCAN_DETAIL_FINALIZING = "Rendering treemap..."

private class ScanProgress {
    var phase by mutableStateOf(SCAN_PHASE_START)
    var detail by mutableStateOf(SCAN_DETAIL_START)
    var percent by mutableStateOf(0)
}

private enum class BackTarget { NONE, NODE_POPUP, TREE_SELECTION, DISCOVER_SEARCH, NAV_PILL }

@Composable
private fun ScanProgressLoadingScreen(progress: ScanProgress) {
    LoadingScreen(phase = progress.phase, detail = progress.detail)
}

@Composable
private fun ScanProgressBanner(
    progress: ScanProgress,
    visible: Boolean,
    onCancelScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    ScanningBanner(
        visible = visible,
        phase = progress.phase,
        detail = progress.detail,
        progressPercent = progress.percent,
        onCancelScan = onCancelScan,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: StorageTreeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("anddirstat_prefs", Context.MODE_PRIVATE) }

    val themePref = remember { prefs.getString("app_theme", AppTheme.SYSTEM.key) ?: AppTheme.SYSTEM.key }
    val pureBlackPref = remember { prefs.getBoolean("pure_black", false) }
    val dynamicThemePref = remember { prefs.getBoolean("dynamic_theme", false) }
    val accentPref = remember { prefs.getString("accent_color", AccentColor.GREEN.key) ?: AccentColor.GREEN.key }
    var currentTheme by remember { mutableStateOf(AppTheme.entries.firstOrNull { it.key == themePref } ?: AppTheme.SYSTEM) }
    var pureBlack by remember { mutableStateOf(pureBlackPref) }
    var dynamicTheme by remember { mutableStateOf(dynamicThemePref) }
    var accentColor by remember { mutableStateOf(AccentColor.entries.firstOrNull { it.key == accentPref } ?: AccentColor.GREEN) }

    AndDirStatTheme(appTheme = currentTheme, pureBlack = pureBlack, dynamicTheme = dynamicTheme, accentColor = accentColor) {
        val isDark = when (currentTheme) {
            AppTheme.SYSTEM -> isSystemInDarkTheme()
            AppTheme.LIGHT -> false
            AppTheme.DARK -> true
        }
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val activity = (context as? ComponentActivity)

        LaunchedEffect(isLandscape) {
            activity?.window?.let { window ->
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.statusBars())
                insetsController.show(WindowInsetsCompat.Type.navigationBars())
            }
        }

        LaunchedEffect(Unit) {
            AppNotifier.dismissAll(context)
        }

        var hasStoragePermission by remember { mutableStateOf(FileUtils.checkStoragePermission(context)) }
        var hasUsageAccess by remember { mutableStateOf(FileUtils.checkUsageAccessPermission(context)) }
        var usageAccessDismissed by remember { mutableStateOf(prefs.getBoolean("usage_access_dismissed", false)) }
        var showFreeSpace by remember { mutableStateOf(prefs.getBoolean("show_free_space", true)) }
        var showSystemApps by remember { mutableStateOf(prefs.getBoolean("show_system_apps", true)) }
        var showHiddenFiles by remember { mutableStateOf(prefs.getBoolean("show_hidden_files", false)) }
        var showSystemOS by remember { mutableStateOf(prefs.getBoolean("show_system_os", false)) }

        val treeState by viewModel.uiState.collectAsState()
        val rawScannedNode = treeState.rawScannedNode
        val deviceTotalBytes = treeState.deviceTotalBytes
        val rootNode = treeState.rootNode
        val extensionStats = treeState.extensionStats
        val topFiles = treeState.topFiles
        val isLoading = treeState.isLoading || (hasStoragePermission && rootNode == null)
        val isBackgroundScanning = treeState.isBackgroundScanning
        val scanProgress = remember(treeState.scanPhase, treeState.scanDetail, treeState.scanProgress) {
            ScanProgress().apply {
                phase = treeState.scanPhase
                detail = treeState.scanDetail
                percent = treeState.scanProgress
            }
        }

        var selectedNode by remember { mutableStateOf<CompactNode?>(null) }
        var selectedPath by remember { mutableStateOf<String?>(null) }
        var lastTouchDown by remember { mutableStateOf(Offset.Zero) }
        var selectedTouchOffset by remember { mutableStateOf<Offset?>(null) }
        var selectedTileRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        var selectedTileColor by remember { mutableStateOf<Color?>(null) }
        var activePopupNode by remember { mutableStateOf<CompactNode?>(null) }
        var activePopupPath by remember { mutableStateOf<String?>(null) }
        var activePopupOffset by remember { mutableStateOf(Offset.Zero) }
        var activePopupTileRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        var activePopupTileColor by remember { mutableStateOf<Color?>(null) }

        LaunchedEffect(selectedNode, selectedPath, selectedTouchOffset, selectedTileRect, selectedTileColor) {
            if (selectedNode != null && selectedPath != null) {
                activePopupNode = selectedNode
                activePopupPath = selectedPath
                activePopupOffset = selectedTouchOffset ?: lastTouchDown
                activePopupTileRect = selectedTileRect
                activePopupTileColor = selectedTileColor
            }
        }
        val isPopupVisible = selectedNode != null && selectedPath != null
        var explorerTargetPath by remember { mutableStateOf<String?>(null) }
        var nodeToDelete by remember { mutableStateOf<Pair<CompactNode, String>?>(null) }

        var stagedQueue by remember { mutableStateOf<Map<CompactNode, StagedQueueItem>>(emptyMap()) }
        var showCleanupReviewSheet by remember { mutableStateOf(false) }
        val stagedReclaimableBytes = remember(stagedQueue) {
            CleanupQueueHelper.calculateTotalReclaimable(stagedQueue.values)
        }
        var pillBackProgress by remember { mutableStateOf(0f) }
        var isPillBackActive by remember { mutableStateOf(false) }

        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: AppDestinations.TREE

        var isTreemapZoomed by remember { mutableStateOf(false) }
        var resetZoomKey by remember { mutableStateOf(0) }
        var discoverSearchQuery by remember { mutableStateOf("") }
        var isDiscoverSearchActive by remember { mutableStateOf(false) }
        var selectedTreeNodes by remember { mutableStateOf(mapOf<CompactNode, String>()) }
        var showTreeDeleteDialog by remember { mutableStateOf(false) }
        var showVolumeSelectionDialog by remember { mutableStateOf(false) }
        var detectedVolumes by remember { mutableStateOf(emptyList<StorageVolumeInfo>()) }
        var selectedVolumeIds by remember { mutableStateOf(setOf<String>()) }
        var activeVolume by remember { mutableStateOf<StorageVolumeInfo?>(null) }
        var scanAppsSelected by remember { mutableStateOf(true) }

        var isTreeDeleting by remember { mutableStateOf(false) }
        var treeDeleteCurrentCount by remember { mutableStateOf(0) }
        var treeDeleteTotalCount by remember { mutableStateOf(0) }
        var treeDeleteCurrentFileName by remember { mutableStateOf("") }
        var treeDeleteIsTrash by remember { mutableStateOf(true) }

        fun clearTreeSelection() {
            selectedTreeNodes = emptyMap()
        }

        fun clearDiscoverSelection() {
            isDiscoverSearchActive = false
            discoverSearchQuery = ""
        }

        // Both selection modes live in this composition, so acquiring one must release the other
        // or a tab switch leaves two live selection UIs and nothing clears the stale one.
        fun setTreeSelection(next: Map<CompactNode, String>) {
            if (next.isNotEmpty()) clearDiscoverSelection()
            selectedTreeNodes = next
        }

        fun setDiscoverSelection(query: String, active: Boolean) {
            if (query.isNotBlank() || active) clearTreeSelection()
            discoverSearchQuery = query
            isDiscoverSearchActive = active
        }

        fun updateFilter(fs: Boolean? = null, sa: Boolean? = null, hf: Boolean? = null, so: Boolean? = null) {
            viewModel.updateFilter(prefs, fs, sa, hf, so)
            clearTreeSelection()
            if (selectedNode != null) { selectedNode = null; selectedPath = null }
        }

        DisposableEffect(prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                when (key) {
                    "app_theme" -> currentTheme = AppTheme.entries.firstOrNull { it.key == prefs.getString("app_theme", AppTheme.SYSTEM.key) } ?: AppTheme.SYSTEM
                    "pure_black" -> pureBlack = prefs.getBoolean("pure_black", false)
                    "dynamic_theme" -> dynamicTheme = prefs.getBoolean("dynamic_theme", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    "accent_color" -> accentColor = AccentColor.entries.firstOrNull { it.key == prefs.getString("accent_color", AccentColor.GREEN.key) } ?: AccentColor.GREEN
                    "show_hidden_files", "show_free_space", "show_system_apps", "show_system_os" -> updateFilter()
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }

        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    FileUtils.AppUninstallerQueue.onResume(context)
                    hasUsageAccess = FileUtils.checkUsageAccessPermission(context)
                    if (hasStoragePermission) detectedVolumes = FileUtils.getAvailableStorageVolumes(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        fun performScan(volumesToScan: List<StorageVolumeInfo>, scanApps: Boolean = scanAppsSelected) {
            val effectiveScanApps = scanApps && FileUtils.checkUsageAccessPermission(context)
            if (!hasStoragePermission || (volumesToScan.isEmpty() && !effectiveScanApps)) return
            activeVolume = if (volumesToScan.size == 1) volumesToScan.first() else null
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.performScan(context, volumesToScan, scanApps, prefs)
        }

        fun removeDeletedNodes(deletedNodes: Set<CompactNode>) {
            viewModel.removeDeletedNodes(context, deletedNodes, prefs)
            if (selectedNode != null && deletedNodes.contains(selectedNode)) { selectedNode = null; selectedPath = null }
        }

        fun requestScan() {
            if (!hasStoragePermission) return
            val vols = FileUtils.getAvailableStorageVolumes(context)
            detectedVolumes = vols; selectedVolumeIds = vols.map { it.id }.toSet(); showVolumeSelectionDialog = true
        }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasStoragePermission = granted; if (granted) requestScan()
        }
        val manageStorageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            hasStoragePermission = FileUtils.checkStoragePermission(context)
            hasUsageAccess = FileUtils.checkUsageAccessPermission(context)
            if (hasStoragePermission) requestScan()
        }
        val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        LaunchedEffect(hasStoragePermission, hasUsageAccess, usageAccessDismissed) {
            if (hasStoragePermission && (hasUsageAccess || usageAccessDismissed) && rootNode == null) {
                val vols = FileUtils.getAvailableStorageVolumes(context)
                viewModel.restoreOrScan(context, vols, scanAppsSelected, prefs)
            }
        }

        val treeSelectionActive = selectedTreeNodes.isNotEmpty()
        val discoverSelectionActive = isDiscoverSearchActive || discoverSearchQuery.isNotEmpty()
        // One derived target for back handlers: the branch order makes them mutually
        // exclusive by construction instead of relying on overlapping guards.
        val backTarget = when {
            selectedNode != null -> BackTarget.NODE_POPUP
            treeSelectionActive && currentRoute == AppDestinations.TREE -> BackTarget.TREE_SELECTION
            discoverSelectionActive && currentRoute == AppDestinations.DISCOVER -> BackTarget.DISCOVER_SEARCH
            else -> BackTarget.NONE
        }

        PredictiveBackHandler(enabled = backTarget == BackTarget.NODE_POPUP) { progress ->
            try {
                progress.collect {}
                selectedNode = null
                selectedPath = null
                selectedTouchOffset = null
                selectedTileRect = null
                selectedTileColor = null
            } catch (_: CancellationException) {}
        }
        PredictiveBackHandler(enabled = backTarget == BackTarget.TREE_SELECTION) { progress ->
            try { progress.collect {}; clearTreeSelection() } catch (_: CancellationException) {}
        }
        PredictiveBackHandler(enabled = backTarget == BackTarget.DISCOVER_SEARCH) { progress ->
            try { progress.collect {}; clearDiscoverSelection() } catch (_: CancellationException) {}
        }

        val destIndexMap = remember { mapOf(AppDestinations.TREE to 0, AppDestinations.EXPLORER to 1, AppDestinations.TYPES to 2, AppDestinations.DISCOVER to 3) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        lastTouchDown = down.position
                    }
                }
        ) {
            when {
                !hasStoragePermission || (!hasUsageAccess && !usageAccessDismissed) -> {
                    PermissionScreen(
                        hasStoragePermission = hasStoragePermission,
                        hasUsageAccess = hasUsageAccess,
                        onGrantStorage = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                manageStorageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            } else {
                                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        },
                        onGrantUsageAccess = {
                            try {
                                manageStorageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                })
                            } catch (_: Exception) {
                                manageStorageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                        },
                        onContinue = { skipUsageAccess ->
                            if (skipUsageAccess) {
                                usageAccessDismissed = true
                                prefs.edit().putBoolean("usage_access_dismissed", true).apply()
                            }
                            if (hasStoragePermission) {
                                requestScan()
                            }
                        }
                    )
                }
                isLoading -> ScanProgressLoadingScreen(scanProgress)
                rootNode != null -> {
                    val showNavPill = currentRoute == AppDestinations.TREE ||
                                      currentRoute == AppDestinations.EXPLORER ||
                                      currentRoute == AppDestinations.TYPES ||
                                      currentRoute == AppDestinations.DISCOVER ||
                                      currentRoute == AppDestinations.SETTINGS

                    LaunchedEffect(currentRoute) {
                        selectedNode = null
                        selectedPath = null
                        selectedTouchOffset = null
                        clearTreeSelection()
                        clearDiscoverSelection()
                    }

                    val homeContent: @Composable () -> Unit = {
                        TreemapScreen(
                            rootNode = rootNode!!,
                            selectedNode = selectedNode,
                            selectedPath = selectedPath,
                            selectedTreeNodes = selectedTreeNodes,
                            resetZoomKey = resetZoomKey,
                            isDark = isDark,
                            pureBlack = pureBlack,
                            isLandscape = isLandscape,
                            detectedVolumes = detectedVolumes,
                            activeVolume = activeVolume,
                            showSystemOS = showSystemOS,
                            showSystemApps = showSystemApps,
                            showFreeSpace = showFreeSpace,
                            showHiddenFiles = showHiddenFiles,
                            onZoomChanged = { isTreemapZoomed = it },
                            onNodeSelected = { node, path, touchOffset -> selectedNode = node; selectedPath = path; selectedTouchOffset = touchOffset ?: lastTouchDown },
                            onNavigate = {
                                if (it == AppDestinations.TREE) {
                                    resetZoomKey++
                                    isTreemapZoomed = false
                                } else {
                                    navController.navigate(it)
                                }
                            },
                            onRescanClick = { requestScan() },
                            onSettingsClick = { navController.navigate(AppDestinations.SETTINGS) },
                            onSelectVolume = { vol -> activeVolume = vol; selectedVolumeIds = setOf(vol.id); performScan(listOf(vol), scanApps = vol.isPrimary) },
                            onOpenCustomDriveDialog = { showVolumeSelectionDialog = true },
                            onToggleShowSystemOS = { updateFilter(so = it); prefs.edit().putBoolean("show_system_os", it).apply() },
                            onToggleShowSystemApps = { updateFilter(sa = it); prefs.edit().putBoolean("show_system_apps", it).apply() },
                            onToggleShowFreeSpace = { updateFilter(fs = it); prefs.edit().putBoolean("show_free_space", it).apply() },
                            onToggleShowHiddenFiles = { updateFilter(hf = it); prefs.edit().putBoolean("show_hidden_files", it).apply() },
                            onClearSelection = { clearTreeSelection() },
                            onRequestDeleteSelected = { showTreeDeleteDialog = true },
                            onDismissPopup = { selectedNode = null; selectedPath = null; selectedTouchOffset = null }
                        )
                    }

                    val explorerContent: @Composable () -> Unit = {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                            ) {
                                ExplorerView(
                                    rootNode = (rawScannedNode ?: rootNode)!!,
                                    onNodeClick = { child, childPath ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedNode = child
                                        selectedPath = childPath
                                        selectedTouchOffset = lastTouchDown
                                    },
                                    onNodesDeleted = { removeDeletedNodes(it) },
                                    onRefresh = { performScan(if (detectedVolumes.isNotEmpty()) detectedVolumes else FileUtils.getAvailableStorageVolumes(context)) },
                                    onNavigateTo = { navController.navigate(it) },
                                    onDismissPopup = { selectedNode = null; selectedPath = null; selectedTouchOffset = null },
                                    targetPath = explorerTargetPath,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    val typesContent: @Composable () -> Unit = {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                            ) {
                                FileTypesView(
                                    rootNode = (rawScannedNode ?: rootNode)!!,
                                    stats = extensionStats,
                                    totalDeviceSize = deviceTotalBytes,
                                    onNodeClick = { node, path ->
                                        selectedNode = node
                                        selectedPath = path
                                        selectedTouchOffset = lastTouchDown
                                    },
                                    onNavigateTo = { navController.navigate(it) },
                                    onDismissPopup = { selectedNode = null; selectedPath = null; selectedTouchOffset = null },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    val discoverContent: @Composable (triggerBack: (() -> Unit)?) -> Unit = { triggerBack ->
                        Scaffold(
                            modifier = Modifier.fillMaxSize().padding(start = if (isLandscape) WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(LocalLayoutDirection.current) else 0.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
                            contentWindowInsets = if (isLandscape) WindowInsets(0, 0, 0, 0) else WindowInsets.statusBars,
                            topBar = {
                                DiscoverSearchTopAppBar(
                                    query = discoverSearchQuery,
                                    isSearchActive = isDiscoverSearchActive,
                                    isLandscape = isLandscape,
                                    haptic = haptic,
                                    onQueryChange = {
                                        setDiscoverSelection(it, if (it.isNotBlank()) true else isDiscoverSearchActive)
                                        if (it.isNotBlank()) {
                                            FavoritesManager.addRecentSearch(context, it)
                                        }
                                    },
                                    onSearchActiveChange = { setDiscoverSelection(discoverSearchQuery, it) },
                                    onBack = {
                                        if (triggerBack != null) triggerBack() else navController.popBackStack()
                                    }
                                )
                            }
                        ) { discPadding ->
                            Box(modifier = Modifier.fillMaxSize().padding(top = discPadding.calculateTopPadding(), bottom = discPadding.calculateBottomPadding())) {
                                DiscoverView(
                                    rootNode = (rawScannedNode ?: rootNode)!!,
                                    topFiles = topFiles,
                                    searchQuery = discoverSearchQuery,
                                    isSearchActive = isDiscoverSearchActive || discoverSearchQuery.isNotEmpty(),
                                    onSearchQueryChange = { setDiscoverSelection(it, isDiscoverSearchActive) },
                                    onNodeClick = { node, path ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val realFile = FileUtils.resolveActualFile(path, context)
                                        if (realFile != null && realFile.exists() && !node.isDirectory) FileUtils.openFile(context, realFile)
                                    },
                                    onNodesDeleted = { removeDeletedNodes(it) },
                                    onRefresh = { performScan(if (detectedVolumes.isNotEmpty()) detectedVolumes else FileUtils.getAvailableStorageVolumes(context)) },
                                    onNavigateTo = { navController.navigate(it) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    val navHostContent = @Composable {
                        NavHost(
                            navController = navController,
                            startDestination = AppDestinations.TREE,
                            modifier = Modifier.fillMaxSize(),
                            enterTransition = {
                                slideInHorizontally(
                                    initialOffsetX = { fullWidth -> fullWidth },
                                    animationSpec = spring(
                                        dampingRatio = 0.8f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            },
                            exitTransition = {
                                slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> -fullWidth / 3 },
                                    animationSpec = spring(
                                        dampingRatio = 0.8f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            },
                            popEnterTransition = {
                                EnterTransition.None
                            },
                            popExitTransition = {
                                ExitTransition.None
                            }
                        ) {
                            composable(AppDestinations.TREE) {
                                homeContent()
                            }

                            composable(AppDestinations.EXPLORER) {
                                PredictiveSlideBackContainer(
                                    onBackPressed = { navController.popBackStack() },
                                    backgroundContent = homeContent
                                ) { triggerBack ->
                                    explorerContent()
                                }
                            }

                            composable(AppDestinations.TYPES) {
                                PredictiveSlideBackContainer(
                                    onBackPressed = { navController.popBackStack() },
                                    backgroundContent = homeContent
                                ) { triggerBack ->
                                    typesContent()
                                }
                            }

                            composable(AppDestinations.DISCOVER) {
                                PredictiveSlideBackContainer(
                                    onBackPressed = { navController.popBackStack() },
                                    backgroundContent = homeContent,
                                    enabled = !isDiscoverSearchActive && discoverSearchQuery.isEmpty()
                                ) { triggerBack ->
                                    discoverContent(triggerBack)
                                }
                            }

                            composable(AppDestinations.SETTINGS) {
                                PredictiveSlideBackContainer(
                                    onBackPressed = { navController.popBackStack() },
                                    backgroundContent = homeContent
                                ) { triggerBack ->
                                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)) {
                                        SettingsScreen(onBack = triggerBack)
                                    }
                                }
                            }

                            composable(AppDestinations.CLEANER_DUPLICATES) {
                                PredictiveSlideBackContainer(
                                    onBackPressed = { navController.popBackStack() },
                                    backgroundContent = { discoverContent(null) }
                                ) { triggerBack ->
                                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)) {
                                        DuplicatesCleanerView(onBack = triggerBack)
                                    }
                                }
                            }

                            composable(AppDestinations.CLEANER_EMPTY_FOLDERS) {
                                PredictiveSlideBackContainer(
                                    onBackPressed = { navController.popBackStack() },
                                    backgroundContent = { discoverContent(null) }
                                ) { triggerBack ->
                                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)) {
                                        EmptyFoldersCleanerView(onBack = triggerBack)
                                    }
                                }
                            }

                            composable(AppDestinations.CLEANER_SCREENSHOTS) {
                                PredictiveSlideBackContainer(
                                    onBackPressed = { navController.popBackStack() },
                                    backgroundContent = { discoverContent(null) }
                                ) { triggerBack ->
                                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)) {
                                        ScreenshotsCleanerView(onBack = triggerBack)
                                    }
                                }
                            }

                            composable(AppDestinations.CLEANER_RECYCLE_BIN) {
                                PredictiveSlideBackContainer(
                                    onBackPressed = { navController.popBackStack() },
                                    backgroundContent = { discoverContent(null) }
                                ) { triggerBack ->
                                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)) {
                                        RecycleBinCleanerView(onBack = triggerBack)
                                    }
                                }
                            }

                            composable(AppDestinations.STARRED) {
                                PredictiveSlideBackContainer(
                                    onBackPressed = { navController.popBackStack() },
                                    backgroundContent = homeContent
                                ) { triggerBack ->
                                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)) {
                                        StarredFilesScreen(onBack = triggerBack)
                                    }
                                }
                            }

                            composable(AppDestinations.LARGEST_FILES) {
                                PredictiveSlideBackContainer(
                                    onBackPressed = { navController.popBackStack() },
                                    backgroundContent = { discoverContent(null) }
                                ) { triggerBack ->
                                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)) {
                                        LargestFilesScreen(
                                            rootNode = (rawScannedNode ?: rootNode)!!,
                                            topFiles = topFiles,
                                            onBack = triggerBack,
                                            onNodeClick = { node, path ->
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val realFile = FileUtils.resolveActualFile(path, context)
                                                if (realFile != null && realFile.exists() && !node.isDirectory) FileUtils.openFile(context, realFile)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        TreemapCanvas(
                            rootNode = rootNode!!,
                            rootPath = activeVolume?.path?.absolutePath ?: rootNode!!.name,
                            selectedNode = selectedNode,
                            selectedNodes = selectedTreeNodes.keys,
                            resetKey = resetZoomKey,
                            isDark = isDark,
                            pureBlack = pureBlack,
                            isInteractive = (currentRoute == AppDestinations.TREE),
                            onZoomChanged = { isTreemapZoomed = it },
                            isLandscape = isLandscape,
                            onNodeSelected = { node, path, touchOffset, tileRect, tileColor ->
                                selectedNode = node
                                selectedPath = path
                                selectedTouchOffset = touchOffset ?: lastTouchDown
                                selectedTileRect = tileRect
                                selectedTileColor = tileColor
                            },
                            onDismissPopup = {
                                selectedNode = null
                                selectedPath = null
                                selectedTouchOffset = null
                                selectedTileRect = null
                                selectedTileColor = null
                            },
                            onToggleSelectNode = { node, path ->
                                setTreeSelection(
                                    if (selectedTreeNodes.containsKey(node)) {
                                        selectedTreeNodes - node
                                    } else {
                                        selectedTreeNodes + (node to path)
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isLandscape && showNavPill) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                TreemapNavRail(
                                    currentDestination = currentRoute,
                                    onNavigate = { dest ->
                                        if (dest == currentRoute) {
                                            if (dest == AppDestinations.TREE) {
                                                resetZoomKey++
                                                isTreemapZoomed = false
                                            }
                                        } else {
                                            navController.navigate(dest) {
                                                popUpTo(AppDestinations.TREE) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    selectedTreeNodes = selectedTreeNodes,
                                    stagedQueueCount = stagedQueue.size,
                                    stagedReclaimableBytes = stagedReclaimableBytes,
                                    onOpenReviewSheet = { showCleanupReviewSheet = true },
                                    onClearStaged = { stagedQueue = emptyMap() },
                                    onClearSelection = { clearTreeSelection() },
                                    onDeleteSelected = { showTreeDeleteDialog = true }
                                )

                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    navHostContent()
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                navHostContent()

                                if (showNavPill) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .navigationBarsPadding()
                                            .padding(bottom = 14.dp)
                                    ) {
                                        TreemapNavPill(
                                            isLandscape = isLandscape,
                                            currentDestination = currentRoute,
                                            onBack = { navController.popBackStack() },
                                            stagedQueueCount = stagedQueue.size,
                                            stagedReclaimableBytes = stagedReclaimableBytes,
                                            onOpenReviewSheet = { showCleanupReviewSheet = true },
                                            onClearStaged = { stagedQueue = emptyMap() },
                                            selectedTreeNodes = selectedTreeNodes,
                                            onClearSelection = { clearTreeSelection() },
                                            onDeleteSelected = { showTreeDeleteDialog = true },
                                            predictiveBackProgress = pillBackProgress,
                                            isPredictiveBackActive = isPillBackActive,
                                            predictiveBackTargetDestination = AppDestinations.TREE,
                                            onNavigate = { dest ->
                                                if (dest == currentRoute) {
                                                    if (dest == AppDestinations.TREE) {
                                                        resetZoomKey++
                                                        isTreemapZoomed = false
                                                    } else {
                                                        navController.popBackStack(AppDestinations.TREE, inclusive = false)
                                                    }
                                                } else {
                                                    navController.navigate(dest) {
                                                        popUpTo(AppDestinations.TREE) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }

                                ScanProgressBanner(
                                    progress = scanProgress,
                                    visible = isBackgroundScanning,
                                    onCancelScan = {
                                        viewModel.cancelScan()
                                        AppNotifier.finishActivity(context)
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .statusBarsPadding()
                                )
                            }
                        }
                    }
                }
            }

            if (showCleanupReviewSheet) {
                CleanupQueueReviewSheet(
                    stagedItems = stagedQueue.values.toList(),
                    onUnstageItem = { item -> stagedQueue = stagedQueue - item.node },
                    onClearAll = { stagedQueue = emptyMap() },
                    onCommitCleanup = { _ ->
                        showCleanupReviewSheet = false
                        val nodesToDelete = stagedQueue.keys.toSet()
                        removeDeletedNodes(nodesToDelete)
                        stagedQueue = emptyMap()
                    },
                    onDismiss = { showCleanupReviewSheet = false }
                )
            }



            if (showTreeDeleteDialog && selectedTreeNodes.isNotEmpty()) {
                TreeDeleteDialog(
                    selectedTreeNodes = selectedTreeNodes,
                    context = context,
                    onConfirmDelete = {
                        showTreeDeleteDialog = false
                        val items = selectedTreeNodes.toList()
                        scope.launch {
                            isTreeDeleting = true
                            com.kd.anddirstat.util.FileMaintenanceEngine.deleteTreeNodes(
                                context = context,
                                items = items,
                                onProgress = { cur, tot, name, isTrash ->
                                    treeDeleteCurrentCount = cur
                                    treeDeleteTotalCount = tot
                                    treeDeleteCurrentFileName = name
                                    treeDeleteIsTrash = isTrash
                                }
                            )
                            isTreeDeleting = false
                            clearTreeSelection()
                            performScan(if (detectedVolumes.isNotEmpty()) detectedVolumes else FileUtils.getAvailableStorageVolumes(context))
                        }
                    },
                    onDismiss = { showTreeDeleteDialog = false }
                )
            }

            DeletionProgressDialog(
                visible = isTreeDeleting,
                currentCount = treeDeleteCurrentCount,
                totalCount = treeDeleteTotalCount,
                currentFileName = treeDeleteCurrentFileName,
                isTrash = treeDeleteIsTrash
            )

            if (showVolumeSelectionDialog && detectedVolumes.isNotEmpty()) {
                VolumeSelectionBottomSheet(
                    detectedVolumes = detectedVolumes,
                    selectedVolumeIds = selectedVolumeIds,
                    onToggleVolume = { volId ->
                        selectedVolumeIds = if (selectedVolumeIds.contains(volId)) {
                            if (selectedVolumeIds.size > 1 || scanAppsSelected) selectedVolumeIds - volId else selectedVolumeIds
                        } else {
                            selectedVolumeIds + volId
                        }
                    },
                    scanAppsSelected = scanAppsSelected,
                    onToggleScanApps = { scanAppsSelected = it },
                    onConfirm = {
                        showVolumeSelectionDialog = false
                        val toScan = detectedVolumes.filter { selectedVolumeIds.contains(it.id) }
                        performScan(toScan, scanApps = scanAppsSelected)
                    },
                    onDismiss = { showVolumeSelectionDialog = false }
                )
            }

            if (activePopupNode != null && activePopupPath != null) {
                TreemapTilePopup(
                    node = activePopupNode!!,
                    path = activePopupPath!!,
                    tileRect = activePopupTileRect,
                    tileColor = activePopupTileColor,
                    touchOffset = activePopupOffset,
                    visible = isPopupVisible,
                    onDismiss = {
                        selectedNode = null
                        selectedPath = null
                        selectedTouchOffset = null
                        selectedTileRect = null
                        selectedTileColor = null
                        activePopupNode = null
                        activePopupPath = null
                        activePopupTileRect = null
                        activePopupTileColor = null
                    },
                    stagedQueue = stagedQueue,
                    onToggleStaged = { n, p, size, isApp, pkg ->
                        stagedQueue = CleanupQueueHelper.toggleItem(
                            stagedQueue,
                            StagedQueueItem(
                                node = n,
                                path = p,
                                reclaimableBytes = size,
                                isApp = isApp,
                                pkgName = pkg
                            )
                        )
                    },
                    isSelected = selectedTreeNodes.containsKey(activePopupNode),
                    onToggleSelect = {
                        val n = activePopupNode ?: return@TreemapTilePopup
                        val p = activePopupPath ?: return@TreemapTilePopup
                        setTreeSelection(
                            if (selectedTreeNodes.containsKey(n)) {
                                selectedTreeNodes - n
                            } else {
                                selectedTreeNodes + (n to p)
                            }
                        )
                    },
                    onOpenFile = { file ->
                        FileUtils.openFile(context, file)
                    },
                    onOpenApp = { pkg ->
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            try { context.startActivity(launchIntent) } catch (_: Exception) {}
                        }
                    },
                    onOpenAppInfo = { pkg ->
                        try {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
                        } catch (_: Exception) {}
                    },
                    onUninstallApp = { pkg ->
                        FileUtils.uninstallApp(context, pkg)
                    },
                    onNavigateToExplorer = { p ->
                        explorerTargetPath = p
                        navController.navigate(AppDestinations.EXPLORER)
                    },
                    onRequestDelete = {
                        val n = activePopupNode ?: return@TreemapTilePopup
                        val p = activePopupPath ?: return@TreemapTilePopup
                        nodeToDelete = n to p
                    }
                )
            }

            if (nodeToDelete != null) {
                val (delNode, delPath) = nodeToDelete!!
                AlertDialog(
                    onDismissRequest = { nodeToDelete = null },
                    title = { Text("Delete file?") },
                    text = { Text("Are you sure you want to delete ${delNode.name} (${FileUtils.formatFileSize(delNode.size)})?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val file = FileUtils.resolveActualFile(delPath, context) ?: File(delPath)
                                if (file.exists()) {
                                    val fLen = file.length()
                                    if (FileUtils.deleteOrTrashFile(file, context)) {
                                        StorageTrendManager.recordFreedBytes(context, fLen)
                                    }
                                }
                                setTreeSelection(selectedTreeNodes - delNode)
                                nodeToDelete = null
                                performScan(if (detectedVolumes.isNotEmpty()) detectedVolumes else FileUtils.getAvailableStorageVolumes(context))
                            }
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { nodeToDelete = null }) { Text("Cancel") }
                    }
                )
            }

            StackedSnackbarHost(
                maxStacks = 3,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        bottom = if (!isLandscape) 76.dp else 16.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
            )
        }
    }
}
