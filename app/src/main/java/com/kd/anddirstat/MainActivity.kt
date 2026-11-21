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
import com.kd.anddirstat.viewmodel.MainAppViewModel
import com.kd.anddirstat.viewmodel.StorageFilterSettings
import com.kd.anddirstat.viewmodel.StorageTreeViewModel
import com.kd.anddirstat.viewmodel.StorageTreeViewModelFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
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
import com.kd.anddirstat.ui.components.TreemapNavPill
import com.kd.anddirstat.ui.components.ScanningBanner
import com.kd.anddirstat.ui.components.TreeDeleteDialog
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
import com.kd.anddirstat.util.FileMaintenanceEngine
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
    private val viewModel: StorageTreeViewModel by viewModels { StorageTreeViewModelFactory(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.kd.anddirstat.util.StorageSnapshotScheduler.scheduleDailySnapshot(applicationContext)
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
fun MainApp(
    viewModel: StorageTreeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = StorageTreeViewModelFactory(LocalContext.current.applicationContext)
    )
) {
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
        val localConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
        val configuration = localConfiguration
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

        val mainAppViewModel = remember {
            MainAppViewModel(
                hasStoragePermission = FileUtils.checkStoragePermission(context),
                hasUsageAccess = FileUtils.checkUsageAccessPermission(context),
                usageAccessDismissed = prefs.getBoolean("usage_access_dismissed", false),
                showFreeSpace = prefs.getBoolean("show_free_space", true),
                showSystemApps = prefs.getBoolean("show_system_apps", true),
                showHiddenFiles = prefs.getBoolean("show_hidden_files", false),
                showSystemOS = prefs.getBoolean("show_system_os", false)
            )
        }
        val uiState by mainAppViewModel.uiState.collectAsState()

        val treeState by viewModel.uiState.collectAsState()
        val rawScannedNode = treeState.rawScannedNode
        val deviceTotalBytes = treeState.deviceTotalBytes
        val rootNode = treeState.rootNode
        val extensionStats = treeState.extensionStats
        val topFiles = treeState.topFiles
        val isLoading = treeState.isLoading || (uiState.hasStoragePermission && rootNode == null)
        val isBackgroundScanning = treeState.isBackgroundScanning
        val scanProgress = remember(treeState.scanPhase, treeState.scanDetail, treeState.scanProgress) {
            ScanProgress().apply {
                phase = treeState.scanPhase
                detail = treeState.scanDetail
                percent = treeState.scanProgress
            }
        }

        // Transient View geometry only; semantic selection state lives in MainAppViewModel.
        var lastTouchDown by remember { mutableStateOf(Offset.Zero) }
        var selectedTouchOffset by remember { mutableStateOf<Offset?>(null) }
        var selectedTileRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        var selectedTileColor by remember { mutableStateOf<Color?>(null) }
        var activePopupOffset by remember { mutableStateOf(Offset.Zero) }
        var activePopupTileRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        var activePopupTileColor by remember { mutableStateOf<Color?>(null) }
        val scrimAlpha = remember { Animatable(0f) }
        val scrimSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        var popupBackProgress by remember { mutableFloatStateOf(0f) }

        fun dismissPopupAnimated() {
            scope.launch {
                scrimAlpha.animateTo(0f, scrimSpec)
                mainAppViewModel.dismissPopup()
                activePopupTileRect = null
                popupBackProgress = 0f
            }
        }

        LaunchedEffect(uiState.activePopupNode) {
            if (uiState.activePopupNode == null) scrimAlpha.snapTo(0f)
        }

        LaunchedEffect(uiState.selectedNode, uiState.selectedPath, selectedTouchOffset, selectedTileRect, selectedTileColor) {
            if (uiState.selectedNode != null && uiState.selectedPath != null) {
                mainAppViewModel.onActivePopupChanged(uiState.selectedNode, uiState.selectedPath)
                activePopupOffset = selectedTouchOffset ?: lastTouchDown
                activePopupTileRect = selectedTileRect
                activePopupTileColor = selectedTileColor
            }
        }
        val isPopupVisible = uiState.selectedNode != null && uiState.selectedPath != null

        fun openNode(node: CompactNode, path: String) {
            val pkg = FileUtils.extractPackageName(node, path, context)
            if (pkg != null) {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) try { context.startActivity(intent) } catch (_: Exception) {}
            } else {
                FileUtils.openFile(context, FileUtils.resolveActualFile(path, context) ?: File(path))
            }
        }

        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: AppDestinations.TREE


        var isTreemapZoomed by remember { mutableStateOf(false) }
        var resetZoomKey by remember { mutableStateOf(0) }

        fun clearTreeSelection() = mainAppViewModel.clearTreeSelection()

        fun clearDiscoverSelection() = mainAppViewModel.clearDiscoverSelection()

        fun setTreeSelection(next: Map<CompactNode, String>) = mainAppViewModel.setTreeSelection(next)

        fun setDiscoverSelection(query: String, active: Boolean) = mainAppViewModel.setDiscoverSelection(query, active)

        fun storageSettings(fs: Boolean? = null, sa: Boolean? = null, hf: Boolean? = null, so: Boolean? = null) =
            StorageFilterSettings(
                showFreeSpace = fs ?: prefs.getBoolean("show_free_space", true),
                showSystemApps = sa ?: prefs.getBoolean("show_system_apps", true),
                showHiddenFiles = hf ?: prefs.getBoolean("show_hidden_files", false),
                showSystemOS = so ?: prefs.getBoolean("show_system_os", false)
            )

        fun updateFilter(fs: Boolean? = null, sa: Boolean? = null, hf: Boolean? = null, so: Boolean? = null) {
            val settings = storageSettings(fs, sa, hf, so)
            viewModel.updateFilter(settings)
            mainAppViewModel.setFilterFlags(
                showFreeSpace = settings.showFreeSpace,
                showSystemApps = settings.showSystemApps,
                showHiddenFiles = settings.showHiddenFiles,
                showSystemOS = settings.showSystemOS
            )
            clearTreeSelection()
            if (uiState.selectedNode != null) mainAppViewModel.onNodeSelected(null, null)
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
                    mainAppViewModel.setPermissions(hasUsageAccess = FileUtils.checkUsageAccessPermission(context))
                    if (uiState.hasStoragePermission) {
                        mainAppViewModel.setVolumes(FileUtils.getAvailableStorageVolumes(context))
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        fun performScan(volumesToScan: List<StorageVolumeInfo>, scanApps: Boolean = uiState.scanAppsSelected) {
            val effectiveScanApps = scanApps && FileUtils.checkUsageAccessPermission(context)
            if (!uiState.hasStoragePermission || (volumesToScan.isEmpty() && !effectiveScanApps)) return
            mainAppViewModel.setActiveVolume(if (volumesToScan.size == 1) volumesToScan.first() else null)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.performScan(volumesToScan, scanApps, storageSettings())
        }

        fun removeDeletedNodes(deletedNodes: Set<CompactNode>) {
            viewModel.removeDeletedNodes(deletedNodes, storageSettings())
            if (uiState.selectedNode != null && deletedNodes.contains(uiState.selectedNode)) {
                mainAppViewModel.onNodeSelected(null, null)
            }
        }

        fun requestScan() {
            if (!mainAppViewModel.uiState.value.hasStoragePermission) return
            val vols = FileUtils.getAvailableStorageVolumes(context)
            mainAppViewModel.setVolumes(vols)
            mainAppViewModel.setSelectedVolumeIds(vols.map { it.id }.toSet())
            mainAppViewModel.showVolumeSelectionDialog()
        }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            mainAppViewModel.setPermissions(hasStoragePermission = granted); if (granted) requestScan()
        }
        val manageStorageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val storage = FileUtils.checkStoragePermission(context)
            val usage = FileUtils.checkUsageAccessPermission(context)
            mainAppViewModel.setPermissions(hasStoragePermission = storage, hasUsageAccess = usage)
            if (storage) requestScan()
        }
        val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        LaunchedEffect(uiState.hasStoragePermission, uiState.hasUsageAccess, uiState.usageAccessDismissed) {
            if (uiState.hasStoragePermission && (uiState.hasUsageAccess || uiState.usageAccessDismissed) && rootNode == null) {
                val vols = FileUtils.getAvailableStorageVolumes(context)
                viewModel.restoreOrScan(vols, uiState.scanAppsSelected, storageSettings())
            }
        }

        val treeSelectionActive = uiState.selectedTreeNodes.isNotEmpty()
        val discoverSelectionActive = uiState.isDiscoverSearchActive || uiState.discoverSearchQuery.isNotEmpty()
        // One derived target for back handlers: the branch order makes them mutually
        // exclusive by construction instead of relying on overlapping guards.
        val backTarget = when {
            uiState.selectedNode != null -> BackTarget.NODE_POPUP
            treeSelectionActive && currentRoute == AppDestinations.TREE -> BackTarget.TREE_SELECTION
            discoverSelectionActive && currentRoute == AppDestinations.DISCOVER -> BackTarget.DISCOVER_SEARCH
            else -> BackTarget.NONE
        }

        PredictiveBackHandler(enabled = backTarget == BackTarget.NODE_POPUP) { progress ->
            try {
                progress.collect { popupBackProgress = it.progress }
                mainAppViewModel.dismissPopup()
                activePopupTileRect = null
                selectedTouchOffset = null
                selectedTileRect = null
                selectedTileColor = null
                popupBackProgress = 0f
            } catch (_: CancellationException) {
                popupBackProgress = 0f
            }
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
                !uiState.hasStoragePermission || (!uiState.hasUsageAccess && !uiState.usageAccessDismissed) -> {
                    PermissionScreen(
                        hasStoragePermission = uiState.hasStoragePermission,
                        hasUsageAccess = uiState.hasUsageAccess,
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
                                mainAppViewModel.setUsageAccessDismissed(true)
                                prefs.edit().putBoolean("usage_access_dismissed", true).apply()
                            }
                            if (uiState.hasStoragePermission) {
                                requestScan()
                            }
                        }
                    )
                }
                isLoading -> ScanProgressLoadingScreen(scanProgress)
                rootNode != null -> {
                    LaunchedEffect(currentRoute) {
                        mainAppViewModel.onNodeSelected(null, null)
                        selectedTouchOffset = null
                        clearTreeSelection()
                        clearDiscoverSelection()
                    }

                    val homeContent: @Composable () -> Unit = {
                        TreemapScreen(
                            rootNode = rootNode!!,
                            selectedNode = uiState.selectedNode,
                            selectedPath = uiState.selectedPath,
                            selectedTreeNodes = uiState.selectedTreeNodes,
                            resetZoomKey = resetZoomKey,
                            isDark = isDark,
                            pureBlack = pureBlack,
                            isLandscape = isLandscape,
                            detectedVolumes = uiState.detectedVolumes,
                            activeVolume = uiState.activeVolume,
                            showSystemOS = uiState.showSystemOS,
                            showSystemApps = uiState.showSystemApps,
                            showFreeSpace = uiState.showFreeSpace,
                            showHiddenFiles = uiState.showHiddenFiles,
                            onZoomChanged = { isTreemapZoomed = it },
                            onNodeSelected = { node, path, touchOffset -> mainAppViewModel.onNodeSelected(node, path); selectedTouchOffset = touchOffset ?: lastTouchDown },
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
                            onSelectVolume = { vol -> mainAppViewModel.setActiveVolume(vol); mainAppViewModel.setSelectedVolumeIds(setOf(vol.id)); performScan(listOf(vol), scanApps = vol.isPrimary) },
                            onOpenCustomDriveDialog = { mainAppViewModel.showVolumeSelectionDialog() },
                            onToggleShowSystemOS = { updateFilter(so = it); prefs.edit().putBoolean("show_system_os", it).apply() },
                            onToggleShowSystemApps = { updateFilter(sa = it); prefs.edit().putBoolean("show_system_apps", it).apply() },
                            onToggleShowFreeSpace = { updateFilter(fs = it); prefs.edit().putBoolean("show_free_space", it).apply() },
                            onToggleShowHiddenFiles = { updateFilter(hf = it); prefs.edit().putBoolean("show_hidden_files", it).apply() },
                            onClearSelection = { clearTreeSelection() },
                            onRequestDeleteSelected = { mainAppViewModel.showTreeDeleteDialog() },
                            onDismissPopup = { mainAppViewModel.onNodeSelected(null, null); selectedTouchOffset = null }
                        )
                    }

                    val explorerContent: @Composable () -> Unit = {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surfaceContainer
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
                                        mainAppViewModel.onNodeSelected(child, childPath)
                                        selectedTouchOffset = lastTouchDown
                                    },
                                    onNodesDeleted = { removeDeletedNodes(it) },
                                    onRefresh = { performScan(if (uiState.detectedVolumes.isNotEmpty()) uiState.detectedVolumes else FileUtils.getAvailableStorageVolumes(context)) },
                                    onNavigateTo = { navController.navigate(it) },
                                    onDismissPopup = { mainAppViewModel.onNodeSelected(null, null); selectedTouchOffset = null },
                                    targetPath = uiState.explorerTargetPath,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    val typesContent: @Composable () -> Unit = {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surfaceContainer
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
                                        mainAppViewModel.onNodeSelected(node, path)
                                        selectedTouchOffset = lastTouchDown
                                    },
                                    onNavigateTo = { navController.navigate(it) },
                                    onDismissPopup = { mainAppViewModel.onNodeSelected(null, null); selectedTouchOffset = null },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    val explorerContentWithBack: @Composable (triggerBack: () -> Unit) -> Unit = { triggerBack ->
                        explorerContent()
                    }
                    val typesContentWithBack: @Composable (triggerBack: () -> Unit) -> Unit = { triggerBack ->
                        typesContent()
                    }

                    val discoverContent: @Composable (triggerBack: (() -> Unit)?) -> Unit = { triggerBack ->
                        Scaffold(
                            modifier = Modifier.fillMaxSize().padding(start = if (isLandscape) WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(LocalLayoutDirection.current) else 0.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentWindowInsets = if (isLandscape) WindowInsets(0, 0, 0, 0) else WindowInsets.statusBars,
                            topBar = {
                                DiscoverSearchTopAppBar(
                                    query = uiState.discoverSearchQuery,
                                    isSearchActive = uiState.isDiscoverSearchActive,
                                    isLandscape = isLandscape,
                                    haptic = haptic,
                                    onQueryChange = {
                                        setDiscoverSelection(it, if (it.isNotBlank()) true else uiState.isDiscoverSearchActive)
                                        if (it.isNotBlank()) {
                                            FavoritesManager.addRecentSearch(context, it)
                                        }
                                    },
                                    onSearchActiveChange = { setDiscoverSelection(uiState.discoverSearchQuery, it) },
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
                                    searchQuery = uiState.discoverSearchQuery,
                                    isSearchActive = uiState.isDiscoverSearchActive || uiState.discoverSearchQuery.isNotEmpty(),
                                    onSearchQueryChange = { setDiscoverSelection(it, uiState.isDiscoverSearchActive) },
                                    onNodeClick = { node, path ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val realFile = FileUtils.resolveActualFile(path, context)
                                        if (realFile != null && realFile.exists() && !node.isDirectory) FileUtils.openFile(context, realFile)
                                    },
                                    onNodesDeleted = { removeDeletedNodes(it) },
                                    onRefresh = { performScan(if (uiState.detectedVolumes.isNotEmpty()) uiState.detectedVolumes else FileUtils.getAvailableStorageVolumes(context)) },
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
                                slideInHorizontally(initialOffsetX = { it })
                            },
                            exitTransition = {
                                slideOutHorizontally(targetOffsetX = { -it / 3 })
                            },
                            popEnterTransition = {
                                slideInHorizontally(initialOffsetX = { -it / 3 })
                            },
                            popExitTransition = {
                                slideOutHorizontally(targetOffsetX = { it })
                            }
                        ) {
                            composable(AppDestinations.TREE) {
                                homeContent()
                            }

                            composable(AppDestinations.EXPLORER) {
                                explorerContentWithBack({ navController.popBackStack() })
                            }

                            composable(AppDestinations.TYPES) {
                                typesContentWithBack({ navController.popBackStack() })
                            }

                            composable(AppDestinations.DISCOVER) {
                                discoverContent({ navController.popBackStack() })
                            }

                            composable(AppDestinations.SETTINGS) {
                                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                                        SettingsScreen(onBack = { navController.popBackStack() })
                                    }
                            }

                            composable(AppDestinations.CLEANER_DUPLICATES) {
                                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                                        DuplicatesCleanerView(onBack = { navController.popBackStack() })
                                    }
                            }

                            composable(AppDestinations.CLEANER_EMPTY_FOLDERS) {
                                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                                        EmptyFoldersCleanerView(onBack = { navController.popBackStack() })
                                    }
                            }

                            composable(AppDestinations.CLEANER_SCREENSHOTS) {
                                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                                        ScreenshotsCleanerView(onBack = { navController.popBackStack() })
                                    }
                            }

                            composable(AppDestinations.CLEANER_RECYCLE_BIN) {
                                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                                        RecycleBinCleanerView(onBack = { navController.popBackStack() })
                                    }
                            }

                            composable(AppDestinations.STARRED) {
                                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                                        StarredFilesScreen(onBack = { navController.popBackStack() })
                                    }
                            }

                            composable(AppDestinations.LARGEST_FILES) {
                                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                                        LargestFilesScreen(
                                            rootNode = (rawScannedNode ?: rootNode)!!,
                                            topFiles = topFiles,
                                            onBack = { navController.popBackStack() },
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

                    Box(modifier = Modifier.fillMaxSize()) {
                        TreemapCanvas(
                            rootNode = rootNode!!,
                            rootPath = uiState.activeVolume?.path?.absolutePath ?: rootNode!!.name,
                            selectedNode = uiState.selectedNode,
                            selectedNodes = uiState.selectedTreeNodes.keys,
                            resetKey = resetZoomKey,
                            isDark = isDark,
                            pureBlack = pureBlack,
                            isInteractive = (currentRoute == AppDestinations.TREE),
                            onZoomChanged = { isTreemapZoomed = it },
                            isLandscape = isLandscape,
                            onNodeSelected = { node, path, touchOffset, tileRect, tileColor ->
                                mainAppViewModel.onNodeSelected(node, path)
                                selectedTouchOffset = touchOffset ?: lastTouchDown
                                selectedTileRect = tileRect
                                selectedTileColor = tileColor
                            },
                            onDismissPopup = {
                                dismissPopupAnimated()
                                selectedTouchOffset = null
                                selectedTileRect = null
                                selectedTileColor = null
                            },
                            onFocusRectChanged = { rect -> activePopupTileRect = rect },
                            onToggleSelectNode = { node, path ->
                                setTreeSelection(uiState.selectedTreeNodes + (node to path))
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        Box(modifier = Modifier.fillMaxSize()) {
                            navHostContent()

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
                                    stagedQueueCount = uiState.stagedQueue.size,
                                    stagedReclaimableBytes = CleanupQueueHelper.calculateTotalReclaimable(uiState.stagedQueue.values),
                                    onOpenReviewSheet = { mainAppViewModel.showCleanupReviewSheet() },
                                    onClearStaged = { mainAppViewModel.clearStaged() },
                                    predictiveBackProgress = 0f,
                                    isPredictiveBackActive = false,
                                    predictiveBackTargetDestination = AppDestinations.TREE,
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
                                    }
                                )
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

            if (uiState.showCleanupReviewSheet) {
                CleanupQueueReviewSheet(
                    stagedItems = uiState.stagedQueue.values.toList(),
                    onUnstageItem = { item -> mainAppViewModel.onUnstageStaged(item.node) },
                    onClearAll = { mainAppViewModel.clearStaged() },
                    onCommitCleanup = { _ ->
                        mainAppViewModel.dismissCleanupReviewSheet()
                        val nodesToDelete = uiState.stagedQueue.keys.toSet()
                        removeDeletedNodes(nodesToDelete)
                        mainAppViewModel.clearStaged()
                    },
                    onDismiss = { mainAppViewModel.dismissCleanupReviewSheet() }
                )
            }



            if (uiState.showTreeDeleteDialog && uiState.selectedTreeNodes.isNotEmpty()) {
                TreeDeleteDialog(
                    selectedTreeNodes = uiState.selectedTreeNodes,
                    context = context,
                    onConfirmDelete = {
                        mainAppViewModel.dismissTreeDeleteDialog()
                        val items = uiState.selectedTreeNodes.toList()
                        scope.launch {
                            mainAppViewModel.setTreeDeleting(true)
                            val result = FileMaintenanceEngine.deleteTreeNodes(
                                context = context,
                                items = items,
                                onProgress = { cur, tot, name, isTrash ->
                                    mainAppViewModel.setDeleteProgress(cur, tot, name, isTrash)
                                }
                            )
                            mainAppViewModel.setTreeDeleting(false)
                            clearTreeSelection()
                            if (result.deletedCount > 0) {
                                performScan(if (uiState.detectedVolumes.isNotEmpty()) uiState.detectedVolumes else FileUtils.getAvailableStorageVolumes(context))
                            } else {
                                result.blockReasons.firstOrNull()?.let { reason ->
                                    mainAppViewModel.setBlockReason(reason)
                                    AppNotifier.notify("Deletion blocked: $reason")
                                }
                            }
                        }
                    },
                    onDismiss = { mainAppViewModel.dismissTreeDeleteDialog() }
                )
            }

            DeletionProgressDialog(
                visible = uiState.isTreeDeleting,
                currentCount = uiState.treeDeleteCurrentCount,
                totalCount = uiState.treeDeleteTotalCount,
                currentFileName = uiState.treeDeleteCurrentFileName,
                isTrash = uiState.treeDeleteIsTrash
            )

            if (uiState.showVolumeSelectionDialog && uiState.detectedVolumes.isNotEmpty()) {
                VolumeSelectionBottomSheet(
                    detectedVolumes = uiState.detectedVolumes,
                    selectedVolumeIds = uiState.selectedVolumeIds,
                    onToggleVolume = { volId -> mainAppViewModel.onToggleVolume(volId) },
                    scanAppsSelected = uiState.scanAppsSelected,
                    onToggleScanApps = { mainAppViewModel.setScanAppsSelected(it) },
                    onConfirm = {
                        mainAppViewModel.dismissVolumeSelectionDialog()
                        val toScan = uiState.detectedVolumes.filter { uiState.selectedVolumeIds.contains(it.id) }
                        performScan(toScan, scanApps = uiState.scanAppsSelected)
                    },
                    onDismiss = { mainAppViewModel.dismissVolumeSelectionDialog() }
                )
            }

            if (uiState.activePopupNode != null && activePopupTileRect != null) {
                val r = activePopupTileRect!!
                val screenW = with(LocalDensity.current) { localConfiguration.screenWidthDp.dp.toPx() }
                val screenH = with(LocalDensity.current) { localConfiguration.screenHeightDp.dp.toPx() }
                val node = uiState.activePopupNode!!
                val path = uiState.activePopupPath!!
                val isApp = remember(node, path) { FileUtils.extractPackageName(node, path, context) != null }
                val pkg = remember(node, path) { FileUtils.extractPackageName(node, path, context) }

                LaunchedEffect(Unit) {
                    popupBackProgress = 0f
                    scrimAlpha.animateTo(1f, scrimSpec)
                }
                val popupAlpha = scrimAlpha.value * (1f - popupBackProgress)

                val blockMaxW = with(LocalDensity.current) { minOf(screenW - 32f, 640f).toDp() }
                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = popupAlpha
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                    ) {
                        drawRect(Color.Black.copy(alpha = 0.70f), size = size)
                        drawRect(
                            Color.Transparent,
                            topLeft = Offset(r.left, r.top),
                            size = Size(r.width, r.height),
                            blendMode = BlendMode.Clear
                        )
                    }
                    Layout(
                        content = {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = blockMaxW)
                                    .graphicsLayer {
                                        alpha = popupAlpha
                                        translationY = 24f * popupBackProgress
                                        val s = 1f - 0.04f * popupBackProgress
                                        scaleX = s
                                        scaleY = s
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = node.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.widthIn(max = 280.dp)
                                )
                                Text(
                                    text = FileUtils.formatFileSize(node.size),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                                ) {
                                    val onSurface = MaterialTheme.colorScheme.onSecondaryContainer
                                    val error = MaterialTheme.colorScheme.error
                                    ExpressiveIcon("open_in_new", onSurface) { openNode(node, path) }
                                    if (isApp) {
                                        ExpressiveIcon("info", onSurface) {
                                            try {
                                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
                                            } catch (_: Exception) {}
                                        }
                                        ExpressiveIcon("delete_forever", error) {
                                            scope.launch { FileMaintenanceEngine.uninstallAppGuarded(context, pkg ?: "") }
                                        }
                                    }
                                    ExpressiveIcon("delete", error) { mainAppViewModel.requestNodeDelete(node, path) }
                                }
                            }
                        }
                    ) { measurables, constraints ->
                        val block = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
                        val y = (screenH * 2f / 3f)
                            .coerceAtMost((screenH - block.height - 16f).coerceAtLeast(16f))
                            .toInt()
                        val x = ((screenW - block.width) / 2f).toInt().coerceAtLeast(16)
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            block.place(x.toInt(), y.toInt())
                        }
                    }
                }
            }

            if (uiState.nodeToDelete != null) {
                val (delNode, delPath) = uiState.nodeToDelete!!
                AlertDialog(
                    onDismissRequest = { mainAppViewModel.dismissNodeDelete() },
                    title = { Text("Delete file?") },
                    text = { Text("Are you sure you want to delete ${delNode.name} (${FileUtils.formatFileSize(delNode.size)})?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val file = FileUtils.resolveActualFile(delPath, context) ?: File(delPath)
                                    if (file.exists()) {
                                        FileMaintenanceEngine.deleteFileGuarded(context, file)
                                    }
                                    setTreeSelection(uiState.selectedTreeNodes - delNode)
                                    mainAppViewModel.dismissNodeDelete()
                                    performScan(if (uiState.detectedVolumes.isNotEmpty()) uiState.detectedVolumes else FileUtils.getAvailableStorageVolumes(context))
                                }
                            }
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { mainAppViewModel.dismissNodeDelete() }) { Text("Cancel") }
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

@Composable
private fun ExpressiveIcon(name: String, tint: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = ExpressiveMotionTokens.SpatialFloatSpring,
        label = "action_press"
    )
    IconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(52.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = tint
        )
    ) {
        MaterialSymbol(name, active = true, size = 24.dp, tint = tint)
    }
}
