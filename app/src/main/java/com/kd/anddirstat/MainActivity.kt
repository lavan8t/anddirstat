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
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.kd.anddirstat.ui.components.AppNavScaffold
import com.kd.anddirstat.ui.components.DeletionProgressDialog
import com.kd.anddirstat.ui.components.DiscoverSearchTopAppBar
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.ui.components.StackedSnackbarHost
import com.kd.anddirstat.ui.components.TreeDeleteDialog
import com.kd.anddirstat.ui.components.TreemapNavPill
import com.kd.anddirstat.ui.components.VolumeSelectionDialog
import com.kd.anddirstat.ui.screens.DiscoverView
import com.kd.anddirstat.ui.screens.ExplorerView
import com.kd.anddirstat.ui.screens.FileTypesView
import com.kd.anddirstat.ui.screens.LargestFilesScreen
import com.kd.anddirstat.ui.screens.LoadingScreen
import com.kd.anddirstat.ui.screens.PermissionScreen
import com.kd.anddirstat.ui.screens.SettingsScreen
import com.kd.anddirstat.ui.screens.TreemapScreen
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
        setContent { MainApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("anddirstat_prefs", Context.MODE_PRIVATE) }

    val themePref = prefs.getString("app_theme", AppTheme.SYSTEM.key) ?: AppTheme.SYSTEM.key
    var currentTheme by remember { mutableStateOf(AppTheme.entries.firstOrNull { it.key == themePref } ?: AppTheme.SYSTEM) }
    var pureBlack by remember { mutableStateOf(prefs.getBoolean("pure_black", false)) }
    var dynamicTheme by remember { mutableStateOf(prefs.getBoolean("dynamic_theme", false)) }
    val accentPref = prefs.getString("accent_color", AccentColor.GREEN.key) ?: AccentColor.GREEN.key
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
                if (isLandscape) {
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insetsController.hide(WindowInsetsCompat.Type.statusBars())
                } else {
                    insetsController.show(WindowInsetsCompat.Type.statusBars())
                }
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

        var rawScannedNode by remember { mutableStateOf<CompactNode?>(null) }
        var deviceTotalBytes by remember { mutableLongStateOf(0L) }
        var rootNode by remember { mutableStateOf<CompactNode?>(null) }
        var extensionStats by remember { mutableStateOf<List<ExtensionStat>>(emptyList()) }
        var topFiles by remember { mutableStateOf<List<TopFileEntry>>(emptyList()) }
        var selectedNode by remember { mutableStateOf<CompactNode?>(null) }
        var selectedPath by remember { mutableStateOf<String?>(null) }
        var lastTouchDown by remember { mutableStateOf(Offset.Zero) }
        var selectedTouchOffset by remember { mutableStateOf<Offset?>(null) }
        var explorerTargetPath by remember { mutableStateOf<String?>(null) }
        var nodeToDelete by remember { mutableStateOf<Pair<CompactNode, String>?>(null) }
        var isLoading by remember { mutableStateOf(hasStoragePermission && rootNode == null) }
        var scanPhase by remember { mutableStateOf("Analyzing storage...") }
        var scanDetail by remember { mutableStateOf("Starting scan...") }

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

        fun updateFilter(fs: Boolean = showFreeSpace, sa: Boolean = showSystemApps, hf: Boolean = showHiddenFiles, so: Boolean = showSystemOS) {
            showFreeSpace = fs; showSystemApps = sa; showHiddenFiles = hf; showSystemOS = so
            val raw = rawScannedNode ?: return
            val filtered = StorageFilterHelper.filterStorageTree(raw, fs, sa, hf, so, deviceTotalBytes)
            rootNode = filtered
            extensionStats = if (filtered != null) StorageFilterHelper.aggregateExtensionStats(filtered) else emptyList()
            topFiles = if (filtered != null) StorageFilterHelper.aggregateTopFiles(filtered) else emptyList()
            selectedTreeNodes = emptyMap()
            if (selectedNode != null) { selectedNode = null; selectedPath = null }
        }

        DisposableEffect(prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                when (key) {
                    "app_theme" -> currentTheme = AppTheme.entries.firstOrNull { it.key == prefs.getString("app_theme", AppTheme.SYSTEM.key) } ?: AppTheme.SYSTEM
                    "pure_black" -> pureBlack = prefs.getBoolean("pure_black", false)
                    "dynamic_theme" -> dynamicTheme = prefs.getBoolean("dynamic_theme", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    "accent_color" -> accentColor = AccentColor.entries.firstOrNull { it.key == prefs.getString("accent_color", AccentColor.GREEN.key) } ?: AccentColor.GREEN
                    "show_hidden_files" -> updateFilter(hf = prefs.getBoolean("show_hidden_files", true))
                    "show_free_space" -> updateFilter(fs = prefs.getBoolean("show_free_space", true))
                    "show_system_apps" -> updateFilter(sa = prefs.getBoolean("show_system_apps", true))
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
            isLoading = true
            scanPhase = "Analyzing storage..."; scanDetail = "Starting scan..."
            AppNotifier.updateProgress(context, title = scanPhase, detail = scanDetail, indeterminate = true, type = "scan")
            scope.launch {
                showFreeSpace = prefs.getBoolean("show_free_space", true)
                showSystemApps = prefs.getBoolean("show_system_apps", true)
                showHiddenFiles = prefs.getBoolean("show_hidden_files", false)
                showSystemOS = prefs.getBoolean("show_system_os", false)
                val scanner = StorageScanner(context)
                val scanned = scanner.scanStorage(selectedVolumes = volumesToScan, includeFreeSpace = true, scanApps = effectiveScanApps) { phase, detail, progress, max ->
                    scanPhase = phase; scanDetail = detail
                    AppNotifier.updateProgress(context, title = phase, detail = detail, progress = progress, max = max, indeterminate = false, type = "scan")
                }
                rawScannedNode = scanned; deviceTotalBytes = scanned.size
                val filtered = withContext(Dispatchers.Default) {
                    StorageFilterHelper.filterStorageTree(scanned, showFreeSpace, showSystemApps, showHiddenFiles, showSystemOS, scanned.size)
                }
                rootNode = filtered
                if (filtered != null) {
                    withContext(Dispatchers.Default) {
                        val statsJob = async { StorageFilterHelper.aggregateExtensionStats(filtered) }
                        val topJob = async { StorageFilterHelper.aggregateTopFiles(filtered) }
                        extensionStats = statsJob.await()
                        topFiles = topJob.await()
                    }
                } else {
                    extensionStats = emptyList()
                    topFiles = emptyList()
                }
                selectedNode = null; selectedPath = null; isLoading = false
                StorageTrendManager.recordSnapshot(context, scanned.size, deviceTotalBytes)
                AppNotifier.finishActivity(context)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                launch(Dispatchers.IO) {
                    scanned.children?.firstOrNull { it.name == "Apps & System Packages" }?.children?.forEach { appNode ->
                        val pkg = FileUtils.extractPackageName(appNode)
                        if (pkg != null) AppIconCache.get(context, pkg)
                    }
                }
            }
        }

        fun removeDeletedNodes(deletedNodes: Set<CompactNode>) {
            if (deletedNodes.isEmpty()) return
            val currentRaw = rawScannedNode ?: return
            scope.launch(Dispatchers.Default) {
                val updatedRaw = StorageFilterHelper.pruneNodes(currentRaw, deletedNodes)
                rawScannedNode = updatedRaw; deviceTotalBytes = updatedRaw.size
                val filtered = StorageFilterHelper.filterStorageTree(updatedRaw, showFreeSpace, showSystemApps, showHiddenFiles, showSystemOS, updatedRaw.size)
                withContext(Dispatchers.Main) {
                    rootNode = filtered
                    extensionStats = if (filtered != null) StorageFilterHelper.aggregateExtensionStats(filtered) else emptyList()
                    topFiles = if (filtered != null) StorageFilterHelper.aggregateTopFiles(filtered) else emptyList()
                    if (selectedNode != null && deletedNodes.contains(selectedNode)) { selectedNode = null; selectedPath = null }
                }
                withContext(Dispatchers.IO) { try { TreeCacheManager.saveTree(context, updatedRaw) } catch (_: Exception) {} }
            }
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
                val cached = withContext(Dispatchers.IO) { try { TreeCacheManager.loadTree(context) } catch (_: Exception) { null } }
                if (cached != null) {
                    isLoading = true; scanPhase = "Restoring storage map..."; scanDetail = "Loading saved data..."
                    rawScannedNode = cached; deviceTotalBytes = cached.size
                    val filtered = withContext(Dispatchers.Default) {
                        StorageFilterHelper.filterStorageTree(cached, showFreeSpace, showSystemApps, showHiddenFiles, showSystemOS, cached.size)
                    }
                    rootNode = filtered
                    if (filtered != null) {
                        withContext(Dispatchers.Default) {
                            val statsJob = async { StorageFilterHelper.aggregateExtensionStats(filtered) }
                            val topJob = async { StorageFilterHelper.aggregateTopFiles(filtered) }
                            extensionStats = statsJob.await()
                            topFiles = topJob.await()
                        }
                    } else {
                        extensionStats = emptyList()
                        topFiles = emptyList()
                    }
                    isLoading = false
                } else if (vols.isNotEmpty()) performScan(vols) else { isLoading = false; requestScan() }
            }
        }

        PredictiveBackHandler(enabled = selectedNode != null) { progress ->
            try { progress.collect {}; selectedNode = null; selectedPath = null; selectedTouchOffset = null } catch (_: CancellationException) {}
        }
        PredictiveBackHandler(enabled = selectedTreeNodes.isNotEmpty() && currentRoute == AppDestinations.TREE && selectedNode == null) { progress ->
            try { progress.collect {}; selectedTreeNodes = emptyMap() } catch (_: CancellationException) {}
        }
        PredictiveBackHandler(enabled = (isDiscoverSearchActive || discoverSearchQuery.isNotEmpty()) && currentRoute == AppDestinations.DISCOVER && selectedNode == null) { progress ->
            try { progress.collect {}; isDiscoverSearchActive = false; discoverSearchQuery = "" } catch (_: CancellationException) {}
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
                !hasStoragePermission -> PermissionScreen(onGrant = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) manageStorageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    else permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                })
                !hasUsageAccess && !usageAccessDismissed -> PermissionScreen(
                    title = "Usage Access Required",
                    description = "Grant Usage Access to let AndDirStat inspect installed app cache, sizes, and package storage usage.",
                    icon = "apps",
                    grantButtonText = "Grant Permission",
                    onGrant = {
                        manageStorageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    onDismiss = {
                        usageAccessDismissed = true
                        prefs.edit().putBoolean("usage_access_dismissed", true).apply()
                    }
                )
                isLoading -> LoadingScreen(phase = scanPhase, detail = scanDetail)
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
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = AppDestinations.TREE,
                            modifier = Modifier.fillMaxSize(),
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = {
                            slideInHorizontally(
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                                initialOffsetX = { -it }
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                                targetOffsetX = { it }
                            )
                        }
                    ) {
                        composable(AppDestinations.TREE) {
                            TreemapScreen(
                                rootNode = rootNode!!,
                                selectedNode = selectedNode,
                                selectedPath = selectedPath,
                                selectedTreeNodes = selectedTreeNodes,
                                resetZoomKey = resetZoomKey,
                                isZoomed = isTreemapZoomed,
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
                                onResetZoom = { resetZoomKey++; isTreemapZoomed = false },
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
                                onClearSelection = { selectedTreeNodes = emptyMap() },
                                onRequestDeleteSelected = { showTreeDeleteDialog = true },
                                onDismissPopup = { selectedNode = null; selectedPath = null; selectedTouchOffset = null }
                            )
                        }

                        composable(AppDestinations.EXPLORER) {
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

                        composable(AppDestinations.TYPES) {
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

                        composable(AppDestinations.DISCOVER) {
                            Scaffold(
                                modifier = Modifier.fillMaxSize().padding(start = if (isLandscape) WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(LocalLayoutDirection.current) else 0.dp),
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentWindowInsets = if (isLandscape) WindowInsets(0, 0, 0, 0) else WindowInsets.statusBars,
                                topBar = {
                                    DiscoverSearchTopAppBar(
                                        query = discoverSearchQuery,
                                        isSearchActive = isDiscoverSearchActive,
                                        isLandscape = isLandscape,
                                        haptic = haptic,
                                        onQueryChange = {
                                            discoverSearchQuery = it
                                            if (it.isNotBlank()) {
                                                isDiscoverSearchActive = true
                                                FavoritesManager.addRecentSearch(context, it)
                                            }
                                        },
                                        onSearchActiveChange = { isDiscoverSearchActive = it },
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            ) { discPadding ->
                                Box(modifier = Modifier.fillMaxSize().padding(top = discPadding.calculateTopPadding(), bottom = discPadding.calculateBottomPadding())) {
                                    DiscoverView(
                                        rootNode = (rawScannedNode ?: rootNode)!!,
                                        topFiles = topFiles,
                                        searchQuery = discoverSearchQuery,
                                        isSearchActive = isDiscoverSearchActive || discoverSearchQuery.isNotEmpty(),
                                        onSearchQueryChange = { discoverSearchQuery = it },
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

                        composable(AppDestinations.SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
                        composable(AppDestinations.CLEANER_DUPLICATES) { DuplicatesCleanerView(onBack = { navController.popBackStack() }) }
                        composable(AppDestinations.CLEANER_EMPTY_FOLDERS) { EmptyFoldersCleanerView(onBack = { navController.popBackStack() }) }
                        composable(AppDestinations.CLEANER_SCREENSHOTS) { ScreenshotsCleanerView(onBack = { navController.popBackStack() }) }
                        composable(AppDestinations.CLEANER_RECYCLE_BIN) { RecycleBinCleanerView(onBack = { navController.popBackStack() }) }
                        composable(AppDestinations.STARRED) { StarredFilesScreen(onBack = { navController.popBackStack() }) }
                        composable(AppDestinations.LARGEST_FILES) {
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

                    if (showNavPill) {
                        Box(
                            modifier = if (isLandscape) {
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 16.dp)
                            } else {
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = 14.dp)
                            }
                        ) {
                            TreemapNavPill(
                                isLandscape = isLandscape,
                                currentDestination = currentRoute,
                                onBack = { navController.popBackStack() },
                                selectedTreeNodes = selectedTreeNodes,
                                onClearSelection = { selectedTreeNodes = emptyMap() },
                                onDeleteSelected = { showTreeDeleteDialog = true },
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
                    }
                }
            }
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
                            selectedTreeNodes = emptyMap()
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
                VolumeSelectionDialog(
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

            if (selectedNode != null && selectedPath != null) {
                val node = selectedNode!!
                val path = selectedPath!!

                val isApp = remember(node, path) {
                    FileUtils.extractPackageName(node, path, context) != null ||
                    node.children?.any { it.name.startsWith("App Code") } == true
                }
                val pkgName = remember(node, path) { FileUtils.extractPackageName(node, path, context) }
                val isFreeSpace = node.name.equals("[free space]", ignoreCase = true) || node.name.equals("free space", ignoreCase = true)
                val isSystemOS = node.name.equals("[system & os]", ignoreCase = true) || node.name.equals("system & os", ignoreCase = true)
                val isSpecial = isFreeSpace || isSystemOS
                val realFile = remember(node, path) {
                    FileUtils.resolveActualFile(path, context) ?: FileUtils.resolveActualFile(node.name, context)
                }
                var isStarred by remember(path) { mutableStateOf(FavoritesManager.isStarred(context, path)) }
                val isSelected = selectedTreeNodes.containsKey(node)
                val appBreakdown = remember(node, pkgName, isApp) {
                    if (isApp && pkgName != null) {
                        var code = node.children?.firstOrNull { it.name.startsWith("App Code") }?.size ?: 0L
                        var data = node.children?.firstOrNull { it.name == "Data" }?.size ?: 0L
                        var cache = node.children?.firstOrNull { it.name == "Cache" }?.size ?: 0L

                        if (code == 0L && data == 0L && cache == 0L) {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val ssm = context.getSystemService(StorageStatsManager::class.java)
                                    val stats = ssm?.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkgName, Process.myUserHandle())
                                    if (stats != null) {
                                        code = stats.appBytes
                                        cache = stats.cacheBytes
                                        data = maxOf(0L, stats.dataBytes - cache)
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                        if (code == 0L && data == 0L && cache == 0L) {
                            code = node.size
                        }
                        Triple(code, data, cache)
                    } else null
                }

                val density = LocalDensity.current
                val displayMetrics = remember { context.resources.displayMetrics }
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels

                val menuWidthPx = with(density) { 260.dp.roundToPx() }
                val menuHeightPx = with(density) { if (isApp) 230.dp.roundToPx() else 270.dp.roundToPx() }
                val safeMargin = with(density) { 8.dp.roundToPx() }

                val clickPos = selectedTouchOffset ?: lastTouchDown
                val targetX = (clickPos.x.roundToInt() - menuWidthPx / 2)
                    .coerceIn(safeMargin, (screenWidth - menuWidthPx - safeMargin).coerceAtLeast(safeMargin))
                val targetY = if (clickPos.y > screenHeight * 0.55f) {
                    (clickPos.y.roundToInt() - menuHeightPx - safeMargin)
                        .coerceIn(safeMargin, (screenHeight - menuHeightPx - safeMargin).coerceAtLeast(safeMargin))
                } else {
                    (clickPos.y.roundToInt() + safeMargin)
                        .coerceIn(safeMargin, (screenHeight - menuHeightPx - safeMargin).coerceAtLeast(safeMargin))
                }

                val animatedTargetX by animateIntAsState(
                    targetValue = targetX,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    label = "popupTargetX"
                )
                val animatedTargetY by animateIntAsState(
                    targetValue = targetY,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    label = "popupTargetY"
                )

                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(animatedTargetX, animatedTargetY),
                    onDismissRequest = {
                        selectedNode = null
                        selectedPath = null
                        selectedTouchOffset = null
                    },
                    properties = PopupProperties(
                        focusable = false,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = false
                    )
                ) {
                    var popupVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { popupVisible = true }

                    AnimatedVisibility(
                        visible = popupVisible,
                        enter = fadeIn() + scaleIn(initialScale = 0.90f),
                        exit = fadeOut() + scaleOut(targetScale = 0.90f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 4.dp,
                            shadowElevation = 8.dp,
                            modifier = Modifier.widthIn(min = 230.dp, max = 264.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isApp && pkgName != null && appBreakdown != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        AppIconView(
                                            packageName = pkgName,
                                            contentDescription = node.name,
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = FileUtils.cleanDisplayName(node.name),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight(500),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "App",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = FileUtils.formatFileSize(appBreakdown.first),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight(500),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Data",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = FileUtils.formatFileSize(appBreakdown.second),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight(500),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Cache",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = FileUtils.formatFileSize(appBreakdown.third),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight(500),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        val nameLower = remember(node.name) { node.name.lowercase() }
                                        val isMedia = remember(nameLower) {
                                            nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || nameLower.endsWith(".avi") ||
                                            nameLower.endsWith(".mov") || nameLower.endsWith(".webm") || nameLower.endsWith(".3gp") ||
                                            nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") ||
                                            nameLower.endsWith(".webp") || nameLower.endsWith(".heic") || nameLower.endsWith(".gif") ||
                                            nameLower.endsWith(".apk")
                                        }
                                        val folderName = remember(path) {
                                            val trimmed = path.trimEnd('/')
                                            val parts = trimmed.split('/')
                                            if (parts.size > 1) parts[parts.size - 2] else "Storage"
                                        }

                                        if (realFile != null && isMedia) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(144.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                            ) {
                                                MediaThumbnailView(
                                                    node = node,
                                                    path = path,
                                                    fallbackTint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(58.dp)
                                                        .align(Alignment.BottomCenter)
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                                            )
                                                        )
                                                )
                                                Column(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = FileUtils.cleanDisplayName(node.name),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight(500),
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${FileUtils.formatFileSize(node.size)} • $folderName",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White.copy(alpha = 0.85f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        } else {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(84.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                            ) {
                                                val symbolName = remember(node, isApp) { FileUtils.getNodeSymbolName(node, isApp) }
                                                MaterialSymbol(
                                                    name = symbolName,
                                                    active = true,
                                                    size = 40.dp,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                                                Text(
                                                    text = FileUtils.cleanDisplayName(node.name),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight(500),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Spacer(modifier = Modifier.height(2.dp))

                                                Text(
                                                    text = "${FileUtils.formatFileSize(node.size)} • $folderName",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {

                            // Above row with text: Select & Total size / Show in folder
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: Select / Deselect
                                if (currentRoute == AppDestinations.TREE && !isSpecial) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedTreeNodes = if (selectedTreeNodes.containsKey(node)) {
                                                    selectedTreeNodes - node
                                                } else {
                                                    selectedTreeNodes + (node to path)
                                                }
                                                selectedNode = null
                                                selectedPath = null
                                                selectedTouchOffset = null
                                            }
                                            .padding(horizontal = 6.dp, vertical = 6.dp)
                                    ) {
                                        MaterialSymbol(
                                            name = "check_circle",
                                            active = isSelected,
                                            size = 20.dp,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isSelected) "Deselect" else "Select",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }

                                // Right: Total size (for apps) or Show in folder (for regular files)
                                if (isApp) {
                                    Text(
                                        text = "Total: ${FileUtils.formatFileSize(node.size)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                                    )
                                } else if (!isSpecial) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                val targetP = path
                                                selectedNode = null
                                                selectedPath = null
                                                selectedTouchOffset = null
                                                explorerTargetPath = targetP
                                                navController.navigate(AppDestinations.EXPLORER)
                                            }
                                            .padding(horizontal = 6.dp, vertical = 6.dp)
                                    ) {
                                        MaterialSymbol(
                                            name = "folder_open",
                                            active = true,
                                            size = 20.dp,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Show in Folder",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Bottom action icons (Open, Info, Star, Delete) without any background fill
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Open or Open App
                                if (isApp && pkgName != null) {
                                    val launchIntent = remember(pkgName) { context.packageManager.getLaunchIntentForPackage(pkgName) }
                                    if (launchIntent != null) {
                                        IconButton(
                                            onClick = {
                                                try { context.startActivity(launchIntent) } catch (_: Exception) {}
                                                selectedNode = null; selectedPath = null; selectedTouchOffset = null
                                            }
                                        ) {
                                            MaterialSymbol("open_in_new", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            try {
                                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkgName")))
                                            } catch (_: Exception) {}
                                            selectedNode = null; selectedPath = null; selectedTouchOffset = null
                                        }
                                    ) {
                                        MaterialSymbol("info", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                } else if (!isApp && !isSpecial && !node.isDirectory) {
                                    IconButton(
                                        onClick = {
                                            val targetFile = realFile ?: File(path)
                                            FileUtils.openFile(context, targetFile)
                                            selectedNode = null; selectedPath = null; selectedTouchOffset = null
                                        }
                                    ) {
                                        MaterialSymbol("open_in_new", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                }

                                // Star / Favorite
                                if (!isApp && !isSpecial) {
                                    IconButton(
                                        onClick = {
                                            isStarred = FavoritesManager.toggleStar(context, path)
                                            AppNotifier.notify(if (isStarred) "Starred & protected" else "Unstarred")
                                            selectedNode = null; selectedPath = null; selectedTouchOffset = null
                                        }
                                    ) {
                                        MaterialSymbol(
                                            name = if (isStarred) "star" else "star_outline",
                                            active = isStarred,
                                            size = 22.dp,
                                            tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Delete / Uninstall
                                if (isApp && pkgName != null) {
                                    IconButton(
                                        onClick = {
                                            FileUtils.uninstallApp(context, pkgName)
                                            selectedNode = null; selectedPath = null; selectedTouchOffset = null
                                        }
                                    ) {
                                        MaterialSymbol("delete", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.error)
                                    }
                                } else if (!isSpecial) {
                                    IconButton(
                                        onClick = {
                                            val targetN = node
                                            val targetP = path
                                            selectedNode = null; selectedPath = null; selectedTouchOffset = null
                                            nodeToDelete = targetN to targetP
                                        }
                                    ) {
                                        MaterialSymbol("delete", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.error)
                                    }
                            }
                        }
                    }
                }
            }
        }
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
                                selectedTreeNodes = selectedTreeNodes - delNode
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
}
