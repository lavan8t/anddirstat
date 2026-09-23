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
                val accentColor = remember(node, pkgName, isApp, nameLower) {
                    when {
                        isApp && pkgName != null -> AppIconCache.getDominantColor(pkgName) ?: Color(0xFF6750A4)
                        nameLower.endsWith(".apk") -> Color(0xFFE53935)
                        nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || nameLower.endsWith(".mov") || nameLower.endsWith(".avi") -> Color(0xFF3B82F6)
                        nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") || nameLower.endsWith(".webp") || nameLower.endsWith(".gif") -> Color(0xFF10B981)
                        nameLower.endsWith(".mp3") || nameLower.endsWith(".flac") || nameLower.endsWith(".wav") || nameLower.endsWith(".m4a") -> Color(0xFFF59E0B)
                        nameLower.endsWith(".pdf") || nameLower.endsWith(".doc") || nameLower.endsWith(".docx") || nameLower.endsWith(".txt") -> Color(0xFFEC4899)
                        nameLower.endsWith(".zip") || nameLower.endsWith(".tar") || nameLower.endsWith(".gz") || nameLower.endsWith(".rar") || nameLower.endsWith(".7z") -> Color(0xFF8B5CF6)
                        node.isDirectory -> Color(0xFF38BDF8)
                        else -> Color(0xFF6366F1)
                    }
                }
                val symbolName = remember(node, isApp) { FileUtils.getNodeSymbolName(node, isApp) }

                val density = LocalDensity.current
                val displayMetrics = remember { context.resources.displayMetrics }
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels

                val menuWidthPx = with(density) { 276.dp.roundToPx() }
                val menuHeightPx = with(density) { (if (isApp) 210.dp else 190.dp).roundToPx() }
                val safeMargin = with(density) { 10.dp.roundToPx() }

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
                            shape = RoundedCornerShape(22.dp),
                            color = Color(0xFF14161F),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                            shadowElevation = 10.dp,
                            modifier = Modifier.widthIn(min = 250.dp, max = 280.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                            ) {
                                // Full-cover preview background
                                if (isMedia && realFile != null) {
                                    MediaThumbnailView(
                                        node = node,
                                        path = path,
                                        fallbackTint = accentColor,
                                        modifier = Modifier.matchParentSize()
                                    )
                                } else if (isApp && pkgName != null) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        accentColor.copy(alpha = 0.45f),
                                                        Color(0xFF0F1118)
                                                    ),
                                                    radius = 400f
                                                )
                                            )
                                    )
                                    MaterialSymbol(
                                        name = "apps",
                                        active = true,
                                        size = 110.dp,
                                        tint = accentColor.copy(alpha = 0.12f),
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(end = 8.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        accentColor.copy(alpha = 0.35f),
                                                        Color(0xFF0F1118)
                                                    )
                                                )
                                            )
                                    )
                                    MaterialSymbol(
                                        name = symbolName,
                                        active = true,
                                        size = 110.dp,
                                        tint = accentColor.copy(alpha = 0.12f),
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(end = 8.dp)
                                    )
                                }

                                // Accent color vertical gradient overlay
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    accentColor.copy(alpha = 0.25f),
                                                    Color.Black.copy(alpha = 0.60f),
                                                    Color.Black.copy(alpha = 0.92f)
                                                )
                                            )
                                        )
                                )

                                // Foreground details and action buttons directly over gradient (no solid background)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    // Top Row: App Icon / File Type Badge on left + Close Button on right
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isApp && pkgName != null) {
                                            AppIconView(
                                                packageName = pkgName,
                                                contentDescription = node.name,
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                            )
                                        } else {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                MaterialSymbol(
                                                    name = symbolName,
                                                    active = true,
                                                    size = 16.dp,
                                                    tint = accentColor
                                                )
                                                val ext = if (node.isDirectory) "Folder" else node.name.substringAfterLast('.', "").uppercase().take(5).ifEmpty { "File" }
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = ext,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                selectedNode = null
                                                selectedPath = null
                                                selectedTouchOffset = null
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            MaterialSymbol(
                                                name = "close",
                                                active = true,
                                                size = 18.dp,
                                                tint = Color.White.copy(alpha = 0.85f)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // File / App Name
                                    Text(
                                        text = FileUtils.cleanDisplayName(node.name),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(3.dp))

                                    // Subtitle / Breakdown
                                    if (isApp && appBreakdown != null) {
                                        Text(
                                            text = "App ${FileUtils.formatFileSize(appBreakdown.first)} • Data ${FileUtils.formatFileSize(appBreakdown.second)} • Cache ${FileUtils.formatFileSize(appBreakdown.third)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.80f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else {
                                        Text(
                                            text = "${FileUtils.formatFileSize(node.size)} • $folderName",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.80f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Bottom Actions Row (Directly over gradient, no solid background)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left: Select / Deselect or Total or Show in Folder
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
                                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                                            ) {
                                                MaterialSymbol(
                                                    name = "check_circle",
                                                    active = isSelected,
                                                    size = 18.dp,
                                                    tint = if (isSelected) accentColor else Color.White.copy(alpha = 0.75f)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = if (isSelected) "Deselect" else "Select",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isSelected) accentColor else Color.White
                                                )
                                            }
                                        } else if (isApp) {
                                            Text(
                                                text = "Total: ${FileUtils.formatFileSize(node.size)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = accentColor,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
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
                                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                                            ) {
                                                MaterialSymbol(
                                                    name = "folder_open",
                                                    active = true,
                                                    size = 18.dp,
                                                    tint = accentColor
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = "Folder",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.width(1.dp))
                                        }

                                        // Right: Action Icons (Open, Info, Star, Delete)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            if (isApp && pkgName != null) {
                                                val launchIntent = remember(pkgName) { context.packageManager.getLaunchIntentForPackage(pkgName) }
                                                if (launchIntent != null) {
                                                    IconButton(
                                                        onClick = {
                                                            try { context.startActivity(launchIntent) } catch (_: Exception) {}
                                                            selectedNode = null; selectedPath = null; selectedTouchOffset = null
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        MaterialSymbol("open_in_new", active = true, size = 19.dp, tint = Color.White)
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        try {
                                                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkgName")))
                                                        } catch (_: Exception) {}
                                                        selectedNode = null; selectedPath = null; selectedTouchOffset = null
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    MaterialSymbol("info", active = true, size = 19.dp, tint = Color.White)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        FileUtils.uninstallApp(context, pkgName)
                                                        selectedNode = null; selectedPath = null; selectedTouchOffset = null
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    MaterialSymbol("delete", active = true, size = 19.dp, tint = Color(0xFFFF6B6B))
                                                }
                                            } else {
                                                if (!isSpecial && !node.isDirectory) {
                                                    IconButton(
                                                        onClick = {
                                                            val targetFile = realFile ?: File(path)
                                                            FileUtils.openFile(context, targetFile)
                                                            selectedNode = null; selectedPath = null; selectedTouchOffset = null
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        MaterialSymbol("open_in_new", active = true, size = 19.dp, tint = Color.White)
                                                    }
                                                }
                                                if (!isSpecial) {
                                                    IconButton(
                                                        onClick = {
                                                            isStarred = FavoritesManager.toggleStar(context, path)
                                                            AppNotifier.notify(if (isStarred) "Starred & protected" else "Unstarred")
                                                            selectedNode = null; selectedPath = null; selectedTouchOffset = null
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        MaterialSymbol(
                                                            name = if (isStarred) "star" else "star_outline",
                                                            active = isStarred,
                                                            size = 19.dp,
                                                            tint = if (isStarred) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.85f)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            val targetN = node
                                                            val targetP = path
                                                            selectedNode = null; selectedPath = null; selectedTouchOffset = null
                                                            nodeToDelete = targetN to targetP
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        MaterialSymbol("delete", active = true, size = 19.dp, tint = Color(0xFFFF6B6B))
                                                    }
                                                }
                                            }
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
