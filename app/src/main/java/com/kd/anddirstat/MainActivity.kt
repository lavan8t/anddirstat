package com.kd.anddirstat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.kd.anddirstat.ui.components.*
import com.kd.anddirstat.ui.screens.*
import com.kd.anddirstat.util.AppNotifier
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileUtils
import com.kd.anddirstat.util.LiveActivityPill
import com.kd.anddirstat.util.StorageVolumeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

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

        var hasStoragePermission by remember { mutableStateOf(FileUtils.checkStoragePermission(context)) }
        var hasUsageAccess by remember { mutableStateOf(FileUtils.checkUsageAccessPermission(context)) }
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
        var isLoading by remember { mutableStateOf(hasStoragePermission && rootNode == null) }
        var scanPhase by remember { mutableStateOf("Analyzing storage...") }
        var scanDetail by remember { mutableStateOf("Starting scan...") }

        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: AppDestinations.TREE

        var currentScale by remember { mutableStateOf(1f) }
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
                    if (hasStoragePermission) detectedVolumes = FileUtils.getAvailableStorageVolumes(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
        fun performScan(volumesToScan: List<StorageVolumeInfo>, scanApps: Boolean = scanAppsSelected) {
            if (!hasStoragePermission || (volumesToScan.isEmpty() && !scanApps)) return
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
                val scanned = scanner.scanStorage(selectedVolumes = volumesToScan, includeFreeSpace = true, scanApps = scanApps) { phase, detail, progress, max ->
                    scanPhase = phase; scanDetail = detail
                    AppNotifier.updateProgress(context, title = phase, detail = detail, progress = progress, max = max, indeterminate = false, type = "scan")
                }
                rawScannedNode = scanned; deviceTotalBytes = scanned.size
                val filtered = withContext(Dispatchers.Default) {
                    StorageFilterHelper.filterStorageTree(scanned, showFreeSpace, showSystemApps, showHiddenFiles, showSystemOS, scanned.size)
                }
                rootNode = filtered
                extensionStats = if (filtered != null) StorageFilterHelper.aggregateExtensionStats(filtered) else emptyList()
                topFiles = if (filtered != null) StorageFilterHelper.aggregateTopFiles(filtered) else emptyList()
                selectedNode = null; selectedPath = null; isLoading = false
                AppNotifier.finishActivity(context, "Storage scan complete (${FileUtils.formatFileSize(scanned.size)})")
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

        LaunchedEffect(hasStoragePermission) {
            if (hasStoragePermission && rootNode == null) {
                val vols = FileUtils.getAvailableStorageVolumes(context)
                val cached = withContext(Dispatchers.IO) { try { TreeCacheManager.loadTree(context) } catch (_: Exception) { null } }
                if (cached != null) {
                    isLoading = true; scanPhase = "Restoring storage map..."; scanDetail = "Loading saved data..."
                    rawScannedNode = cached; deviceTotalBytes = cached.size
                    val filtered = withContext(Dispatchers.Default) {
                        StorageFilterHelper.filterStorageTree(cached, showFreeSpace, showSystemApps, showHiddenFiles, showSystemOS, cached.size)
                    }
                    rootNode = filtered
                    extensionStats = if (filtered != null) StorageFilterHelper.aggregateExtensionStats(filtered) else emptyList()
                    topFiles = if (filtered != null) StorageFilterHelper.aggregateTopFiles(filtered) else emptyList()
                    isLoading = false
                } else if (vols.isNotEmpty()) performScan(vols) else { isLoading = false; requestScan() }
            }
        }

        PredictiveBackHandler(enabled = selectedNode != null) { progress ->
            try { progress.collect {}; selectedNode = null; selectedPath = null } catch (_: CancellationException) {}
        }
        PredictiveBackHandler(enabled = selectedTreeNodes.isNotEmpty() && currentRoute == AppDestinations.TREE && selectedNode == null) { progress ->
            try { progress.collect {}; selectedTreeNodes = emptyMap() } catch (_: CancellationException) {}
        }
        PredictiveBackHandler(enabled = (isDiscoverSearchActive || discoverSearchQuery.isNotEmpty()) && currentRoute == AppDestinations.DISCOVER && selectedNode == null) { progress ->
            try { progress.collect {}; isDiscoverSearchActive = false; discoverSearchQuery = "" } catch (_: CancellationException) {}
        }

        val destIndexMap = remember { mapOf(AppDestinations.TREE to 0, AppDestinations.EXPLORER to 1, AppDestinations.TYPES to 2, AppDestinations.DISCOVER to 3) }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                !hasStoragePermission -> PermissionScreen(onGrant = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) manageStorageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    else permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                })
                isLoading -> LoadingScreen(phase = scanPhase, detail = scanDetail)
                rootNode != null -> {
                    NavHost(
                        navController = navController,
                        startDestination = AppDestinations.TREE,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = {
                            val isFwd = (destIndexMap[targetState.destination.route] ?: -1) > (destIndexMap[initialState.destination.route] ?: -1)
                            if (isLandscape) slideInVertically(initialOffsetY = { if (isFwd) (it * 0.15f).toInt() else (-it * 0.15f).toInt() }, animationSpec = tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(200, easing = FastOutSlowInEasing))
                            else slideInHorizontally(initialOffsetX = { if (isFwd) (it * 0.15f).toInt() else (-it * 0.15f).toInt() }, animationSpec = tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(200, easing = FastOutSlowInEasing))
                        },
                        exitTransition = {
                            val isFwd = (destIndexMap[targetState.destination.route] ?: -1) > (destIndexMap[initialState.destination.route] ?: -1)
                            if (isLandscape) slideOutVertically(targetOffsetY = { if (isFwd) (-it * 0.10f).toInt() else (it * 0.10f).toInt() }, animationSpec = tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(150, easing = FastOutSlowInEasing))
                            else slideOutHorizontally(targetOffsetX = { if (isFwd) (-it * 0.10f).toInt() else (it * 0.10f).toInt() }, animationSpec = tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(150, easing = FastOutSlowInEasing))
                        },
                        popEnterTransition = {
                            if (isLandscape) slideInVertically(initialOffsetY = { (-it * 0.10f).toInt() }, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(200, easing = FastOutSlowInEasing))
                            else slideInHorizontally(initialOffsetX = { (-it * 0.10f).toInt() }, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(200, easing = FastOutSlowInEasing))
                        },
                        popExitTransition = {
                            if (isLandscape) slideOutVertically(targetOffsetY = { (it * 0.15f).toInt() }, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(180, easing = FastOutSlowInEasing))
                            else slideOutHorizontally(targetOffsetX = { (it * 0.15f).toInt() }, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(180, easing = FastOutSlowInEasing))
                        }
                    ) {
                        composable(AppDestinations.TREE) {
                            TreemapScreen(
                                rootNode = rootNode!!,
                                selectedNode = selectedNode,
                                selectedPath = selectedPath,
                                selectedTreeNodes = selectedTreeNodes,
                                resetZoomKey = resetZoomKey,
                                currentScale = currentScale,
                                isDark = isDark,
                                pureBlack = pureBlack,
                                isLandscape = isLandscape,
                                detectedVolumes = detectedVolumes,
                                activeVolume = activeVolume,
                                showSystemOS = showSystemOS,
                                showSystemApps = showSystemApps,
                                showFreeSpace = showFreeSpace,
                                showHiddenFiles = showHiddenFiles,
                                onScaleChanged = { currentScale = it },
                                onResetZoom = { resetZoomKey++; currentScale = 1f },
                                onNodeSelected = { node, path -> selectedNode = node; selectedPath = path },
                                onNavigate = { navController.navigate(it) },
                                onRescanClick = { requestScan() },
                                onSettingsClick = { navController.navigate(AppDestinations.SETTINGS) },
                                onSelectVolume = { vol -> activeVolume = vol; selectedVolumeIds = setOf(vol.id); performScan(listOf(vol), scanApps = vol.isPrimary) },
                                onOpenCustomDriveDialog = { showVolumeSelectionDialog = true },
                                onToggleShowSystemOS = { updateFilter(so = it); prefs.edit().putBoolean("show_system_os", it).apply() },
                                onToggleShowSystemApps = { updateFilter(sa = it); prefs.edit().putBoolean("show_system_apps", it).apply() },
                                onToggleShowFreeSpace = { updateFilter(fs = it); prefs.edit().putBoolean("show_free_space", it).apply() },
                                onToggleShowHiddenFiles = { updateFilter(hf = it); prefs.edit().putBoolean("show_hidden_files", it).apply() },
                                onClearSelection = { selectedTreeNodes = emptyMap() },
                                onRequestDeleteSelected = { showTreeDeleteDialog = true }
                            )
                        }

                        composable(AppDestinations.EXPLORER) {
                            AppNavScaffold(
                                title = "Explorer",
                                isLandscape = isLandscape,
                                onBack = { navController.popBackStack() },
                                detectedVolumes = detectedVolumes,
                                activeVolume = activeVolume,
                                onSelectVolume = { vol -> activeVolume = vol; selectedVolumeIds = setOf(vol.id); performScan(listOf(vol), scanApps = vol.isPrimary) },
                                onOpenCustomDialog = { showVolumeSelectionDialog = true },
                                onRescanClick = { requestScan() },
                                onSettingsClick = { navController.navigate(AppDestinations.SETTINGS) },
                                showSystemOS = showSystemOS,
                                onToggleShowSystemOS = { updateFilter(so = it); prefs.edit().putBoolean("show_system_os", it).apply() },
                                showSystemApps = showSystemApps,
                                onToggleShowSystemApps = { updateFilter(sa = it); prefs.edit().putBoolean("show_system_apps", it).apply() },
                                showFreeSpace = showFreeSpace,
                                onToggleShowFreeSpace = { updateFilter(fs = it); prefs.edit().putBoolean("show_free_space", it).apply() },
                                showHiddenFiles = showHiddenFiles,
                                onToggleShowHiddenFiles = { updateFilter(hf = it); prefs.edit().putBoolean("show_hidden_files", it).apply() }
                            ) {
                                ExplorerView(
                                    rootNode = (rawScannedNode ?: rootNode)!!,
                                    onNodeClick = { child, childPath ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val realFile = FileUtils.resolveActualFile(childPath, context)
                                        if (realFile != null && realFile.exists() && !child.isDirectory) FileUtils.openFile(context, realFile)
                                    },
                                    onNodesDeleted = { removeDeletedNodes(it) },
                                    onRefresh = { performScan(if (detectedVolumes.isNotEmpty()) detectedVolumes else FileUtils.getAvailableStorageVolumes(context)) },
                                    onNavigateTo = { navController.navigate(it) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        composable(AppDestinations.TYPES) {
                            AppNavScaffold(
                                title = "Usage",
                                isLandscape = isLandscape,
                                onBack = { navController.popBackStack() },
                                detectedVolumes = detectedVolumes,
                                activeVolume = activeVolume,
                                onSelectVolume = { vol -> activeVolume = vol; selectedVolumeIds = setOf(vol.id); performScan(listOf(vol), scanApps = vol.isPrimary) },
                                onOpenCustomDialog = { showVolumeSelectionDialog = true },
                                onRescanClick = { requestScan() },
                                onSettingsClick = { navController.navigate(AppDestinations.SETTINGS) },
                                showSystemOS = showSystemOS,
                                onToggleShowSystemOS = { updateFilter(so = it); prefs.edit().putBoolean("show_system_os", it).apply() },
                                showSystemApps = showSystemApps,
                                onToggleShowSystemApps = { updateFilter(sa = it); prefs.edit().putBoolean("show_system_apps", it).apply() },
                                showFreeSpace = showFreeSpace,
                                onToggleShowFreeSpace = { updateFilter(fs = it); prefs.edit().putBoolean("show_free_space", it).apply() },
                                showHiddenFiles = showHiddenFiles,
                                onToggleShowHiddenFiles = { updateFilter(hf = it); prefs.edit().putBoolean("show_hidden_files", it).apply() }
                            ) {
                                FileTypesView(
                                    rootNode = (rawScannedNode ?: rootNode)!!,
                                    stats = extensionStats,
                                    totalDeviceSize = deviceTotalBytes,
                                    onNavigateTo = { navController.navigate(it) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        composable(AppDestinations.DISCOVER) {
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentWindowInsets = if (isLandscape) WindowInsets(0, 0, 0, 0) else WindowInsets.statusBars,
                                topBar = {
                                    TopAppBar(
                                        windowInsets = if (isLandscape) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets,
                                        title = {
                                            Surface(
                                                shape = RoundedCornerShape(24.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                modifier = Modifier.fillMaxWidth().height(44.dp).padding(end = 8.dp)
                                            ) {
                                                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(
                                                        onClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            if (isDiscoverSearchActive || discoverSearchQuery.isNotEmpty()) {
                                                                isDiscoverSearchActive = false; discoverSearchQuery = ""
                                                            } else {
                                                                navController.popBackStack()
                                                            }
                                                        },
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        MaterialSymbol("arrow_back", active = true, size = 20.dp, tint = MaterialTheme.colorScheme.onSurface)
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    BasicTextField(
                                                        value = discoverSearchQuery,
                                                        onValueChange = {
                                                            discoverSearchQuery = it
                                                            if (it.isNotBlank()) {
                                                                isDiscoverSearchActive = true; FavoritesManager.addRecentSearch(context, it)
                                                            }
                                                        },
                                                        singleLine = true,
                                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                                        decorationBox = { inner ->
                                                            if (discoverSearchQuery.isEmpty()) {
                                                                Text("Search files...", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                                            }
                                                            inner()
                                                        },
                                                        modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) isDiscoverSearchActive = true }
                                                    )
                                                    if (discoverSearchQuery.isNotEmpty()) {
                                                        IconButton(
                                                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); discoverSearchQuery = "" },
                                                            modifier = Modifier.size(30.dp)
                                                        ) {
                                                            MaterialSymbol("close", active = true, size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                                    )
                                }
                            ) { discPadding ->
                                Box(modifier = Modifier.fillMaxSize().padding(top = discPadding.calculateTopPadding()).navigationBarsPadding()) {
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
                    }
                }
            }

            LiveActivityPill(modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp))

            if (hasStoragePermission && !hasUsageAccess) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Grant Usage Access to index app cache & data", color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(10.dp))
                        FilledTonalButton(
                            onClick = { manageStorageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Grant", style = MaterialTheme.typography.labelMedium)
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
                        val itemsToDelete = selectedTreeNodes.toList()
                        val (starredItems, unstarredItems) = itemsToDelete.partition { (_, path) -> FavoritesManager.isStarred(context, path) }
                        if (unstarredItems.isEmpty()) {
                            AppNotifier.notify("Cannot delete starred files. Unstar them manually first.")
                            return@TreeDeleteDialog
                        }
                        val allAlreadyTrashed = unstarredItems.all { (node, path) ->
                            node.name.startsWith(".trashed") || path.contains(".trashed") || path.contains("[Recycle Bin]")
                        }
                        scope.launch {
                            isTreeDeleting = true
                            treeDeleteTotalCount = unstarredItems.size
                            treeDeleteIsTrash = !allAlreadyTrashed
                            var processedCount = 0
                            val packagesToUninstall = mutableListOf<String>()

                            withContext(Dispatchers.IO) {
                                unstarredItems.forEachIndexed { index, (node, path) ->
                                    treeDeleteCurrentCount = index + 1
                                    treeDeleteCurrentFileName = node.name
                                    val pkg = FileUtils.extractPackageName(node, path, context)
                                    if (pkg != null) {
                                        packagesToUninstall.add(pkg)
                                    } else {
                                        try {
                                            val f = FileUtils.resolveActualFile(path) ?: FileUtils.resolveActualFile(node.name)
                                            if (f != null && f.exists()) {
                                                if (FileUtils.deleteOrTrashFile(f, context)) processedCount++
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                            if (packagesToUninstall.isNotEmpty()) FileUtils.uninstallApps(context, packagesToUninstall)
                            isTreeDeleting = false
                            selectedTreeNodes = emptyMap()
                            val baseMsg = if (allAlreadyTrashed) "Deleted $processedCount items permanently" else "Moved $processedCount items to Recycle Bin"
                            val msg = if (starredItems.isNotEmpty()) "$baseMsg (Skipped ${starredItems.size} starred items)" else baseMsg
                            AppNotifier.notify(msg)
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

            if (currentRoute == AppDestinations.TREE && selectedNode != null && selectedPath != null) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { selectedNode = null; selectedPath = null },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 0.dp,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    ExpressiveNodeDetailsSheet(
                        node = selectedNode!!,
                        path = selectedPath!!,
                        isSelected = selectedTreeNodes.containsKey(selectedNode),
                        onToggleSelect = {
                            val n = selectedNode; val p = selectedPath
                            if (n != null && p != null) selectedTreeNodes = if (selectedTreeNodes.containsKey(n)) selectedTreeNodes - n else selectedTreeNodes + (n to p)
                        },
                        onDismiss = { selectedNode = null; selectedPath = null },
                        onDeleted = {
                            val n = selectedNode
                            if (n != null) selectedTreeNodes = selectedTreeNodes - n
                            selectedNode = null; selectedPath = null
                            performScan(if (detectedVolumes.isNotEmpty()) detectedVolumes else FileUtils.getAvailableStorageVolumes(context))
                        }
                    )
                }
            }

            StackedSnackbarHost(
                maxStacks = 3,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        bottom = if (currentRoute == AppDestinations.TREE && !isLandscape && selectedTreeNodes.isEmpty()) 76.dp else 16.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
            )
        }
    }
}
