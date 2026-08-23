package com.kd.anddirstat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.kd.anddirstat.util.AppNotifier
import com.kd.anddirstat.util.LiveActivityPill
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import kotlinx.coroutines.delay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.kd.anddirstat.model.AppDestinations
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.model.ExtensionStat
import com.kd.anddirstat.model.NavEntry
import com.kd.anddirstat.model.TopFileEntry
import com.kd.anddirstat.scanner.StorageFilterHelper
import com.kd.anddirstat.scanner.StorageScanner
import com.kd.anddirstat.scanner.TreeCacheManager
import com.kd.anddirstat.treemap.TreemapCanvas
import com.kd.anddirstat.ui.components.AppIconCache
import com.kd.anddirstat.ui.components.AppTooltip
import com.kd.anddirstat.ui.components.DeletionProgressDialog
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.screens.DiscoverView
import com.kd.anddirstat.ui.screens.ExplorerView
import com.kd.anddirstat.ui.screens.ExpressiveNodeDetailsSheet
import com.kd.anddirstat.ui.screens.FileTypesView
import com.kd.anddirstat.ui.screens.LoadingScreen
import com.kd.anddirstat.ui.screens.PermissionScreen
import com.kd.anddirstat.util.FileUtils
import com.kd.anddirstat.util.StorageVolumeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
        }
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent {
            MainApp()
        }
    }
}

enum class TitleLeadState { ICON, AND }

private var hasPlayedTitleLaunchAnimation = false

@OptIn(ExperimentalTextApi::class)
@Composable
fun AnimatedAppTitle(modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var leadState by remember {
        mutableStateOf(if (hasPlayedTitleLaunchAnimation) TitleLeadState.AND else TitleLeadState.ICON)
    }
    val fontWidth = remember { Animatable(if (hasPlayedTitleLaunchAnimation) 100f else 140f) }

    LaunchedEffect(Unit) {
        if (!hasPlayedTitleLaunchAnimation) {
            delay(500)
            leadState = TitleLeadState.AND
            fontWidth.snapTo(140f)
            fontWidth.animateTo(100f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
            hasPlayedTitleLaunchAnimation = true
        }
    }

    val currentFontWidth = fontWidth.value
    val dynamicAndFamily = remember(currentFontWidth) {
        FontFamily(
            Font(
                resId = R.font.google_sans_flex,
                weight = FontWeight.Bold,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(700),
                    FontVariation.Setting("ROND", 0f),
                    FontVariation.Setting("wdth", currentFontWidth)
                )
            )
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch {
                    leadState = TitleLeadState.ICON
                    delay(300)
                    leadState = TitleLeadState.AND
                    fontWidth.snapTo(140f)
                    fontWidth.animateTo(100f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
                }
            }
    ) {
        AnimatedContent(
            targetState = leadState,
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                    .togetherWith(fadeOut(tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150)))
            },
            label = "title_lead"
        ) { state ->
            when (state) {
                TitleLeadState.ICON -> {
                    Image(
                        painter = painterResource(R.drawable.ic_appbar),
                        contentDescription = "AndDirStat",
                        modifier = Modifier
                            .height(22.dp)
                            .padding(end = 4.dp)
                    )
                }
                TitleLeadState.AND -> {
                    Text(
                        text = "And",
                        fontFamily = dynamicAndFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Text(
            text = "DirStat",
            fontFamily = GoogleSansFlexFontFamily,
            fontWeight = FontWeight.Light,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
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
    var currentTheme by remember {
        mutableStateOf(AppTheme.entries.firstOrNull { it.key == themePref } ?: AppTheme.SYSTEM)
    }
    var pureBlack by remember { mutableStateOf(prefs.getBoolean("pure_black", false)) }
    var dynamicTheme by remember {
        mutableStateOf(prefs.getBoolean("dynamic_theme", false))
    }
    val accentPref = prefs.getString("accent_color", AccentColor.GREEN.key) ?: AccentColor.GREEN.key
    var accentColor by remember {
        mutableStateOf(AccentColor.entries.firstOrNull { it.key == accentPref } ?: AccentColor.GREEN)
    }

    AndDirStatTheme(appTheme = currentTheme, pureBlack = pureBlack, dynamicTheme = dynamicTheme, accentColor = accentColor) {
        val systemDark = isSystemInDarkTheme()
        val isDark = when (currentTheme) {
            AppTheme.SYSTEM -> systemDark
            AppTheme.LIGHT -> false
            AppTheme.DARK -> true
        }

        var hasStoragePermission by remember { mutableStateOf(FileUtils.checkStoragePermission(context)) }
        var hasUsageAccess by remember { mutableStateOf(FileUtils.checkUsageAccessPermission(context)) }
        var showFreeSpace by remember { mutableStateOf(prefs.getBoolean("show_free_space", true)) }
        var showSystemApps by remember { mutableStateOf(prefs.getBoolean("show_system_apps", true)) }
        var showHiddenFiles by remember { mutableStateOf(prefs.getBoolean("show_hidden_files", false)) }
        var showSystemOS by remember { mutableStateOf(prefs.getBoolean("show_system_os", false)) }
        var showFilterMenu by remember { mutableStateOf(false) }

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

        var currentRoute by remember { mutableStateOf(AppDestinations.TREE) }
        var previousRoute by remember { mutableStateOf(AppDestinations.TREE) }

        var explorerNode by remember { mutableStateOf<CompactNode?>(null) }
        var explorerPath by remember { mutableStateOf("Device Storage") }
        var explorerStack by remember { mutableStateOf<List<NavEntry>>(emptyList()) }

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

        fun applyFilter(
            freeSpace: Boolean,
            systemApps: Boolean,
            hiddenFiles: Boolean = showHiddenFiles,
            systemOS: Boolean = showSystemOS
        ) {
            val raw = rawScannedNode ?: return
            val filtered = StorageFilterHelper.filterStorageTree(raw, freeSpace, systemApps, hiddenFiles, systemOS, deviceTotalBytes)
            rootNode = filtered
            explorerNode = filtered
            explorerPath = filtered?.name ?: "Device Storage"
            explorerStack = emptyList()
            extensionStats = if (filtered != null) StorageFilterHelper.aggregateExtensionStats(filtered) else emptyList()
            topFiles = if (filtered != null) StorageFilterHelper.aggregateTopFiles(filtered) else emptyList()
            selectedTreeNodes = emptyMap()
            if (selectedNode != null) {
                selectedNode = null
                selectedPath = null
            }
        }

        DisposableEffect(prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                when (key) {
                    "app_theme" -> {
                        val newTheme = prefs.getString("app_theme", AppTheme.SYSTEM.key) ?: AppTheme.SYSTEM.key
                        currentTheme = AppTheme.entries.firstOrNull { it.key == newTheme } ?: AppTheme.SYSTEM
                    }
                    "pure_black" -> {
                        pureBlack = prefs.getBoolean("pure_black", false)
                    }
                    "dynamic_theme" -> {
                        dynamicTheme = prefs.getBoolean("dynamic_theme", android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                    }
                    "accent_color" -> {
                        val newAccent = prefs.getString("accent_color", AccentColor.GREEN.key) ?: AccentColor.GREEN.key
                        accentColor = AccentColor.entries.firstOrNull { it.key == newAccent } ?: AccentColor.GREEN
                    }
                    "show_hidden_files" -> {
                        val newH = prefs.getBoolean("show_hidden_files", true)
                        showHiddenFiles = newH
                        applyFilter(showFreeSpace, showSystemApps, newH)
                    }
                    "show_free_space" -> {
                        val newF = prefs.getBoolean("show_free_space", true)
                        showFreeSpace = newF
                        applyFilter(newF, showSystemApps, showHiddenFiles)
                    }
                    "show_system_apps" -> {
                        val newS = prefs.getBoolean("show_system_apps", true)
                        showSystemApps = newS
                        applyFilter(showFreeSpace, newS, showHiddenFiles)
                    }
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                FileUtils.AppUninstallerQueue.onResume(context)
                if (hasStoragePermission) {
                    detectedVolumes = FileUtils.getAvailableStorageVolumes(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun navigateTo(dest: String) {
        if (currentRoute == dest) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        previousRoute = currentRoute
        currentRoute = dest
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun performScan(volumesToScan: List<StorageVolumeInfo>, scanApps: Boolean = scanAppsSelected) {
        if (!hasStoragePermission || (volumesToScan.isEmpty() && !scanApps)) return
        activeVolume = if (volumesToScan.size == 1) volumesToScan.first() else null
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        isLoading = true
        scanPhase = "Analyzing storage..."
        scanDetail = "Starting scan..."
        AppNotifier.updateProgress(context, title = scanPhase, detail = scanDetail, indeterminate = true, type = "scan")
        scope.launch {
            val freeSpacePref = prefs.getBoolean("show_free_space", true)
            val systemAppsPref = prefs.getBoolean("show_system_apps", true)
            val hiddenFilesPref = prefs.getBoolean("show_hidden_files", false)
            val systemOSPref = prefs.getBoolean("show_system_os", false)
            showFreeSpace = freeSpacePref
            showSystemApps = systemAppsPref
            showHiddenFiles = hiddenFilesPref
            showSystemOS = systemOSPref
            val scanner = StorageScanner(context)
            val scanned = scanner.scanStorage(selectedVolumes = volumesToScan, includeFreeSpace = true, scanApps = scanApps) { phase, detail, progress, max ->
                scanPhase = phase
                scanDetail = detail
                AppNotifier.updateProgress(context, title = phase, detail = detail, progress = progress, max = max, indeterminate = false, type = "scan")
            }
            rawScannedNode = scanned
            deviceTotalBytes = scanned.size

            val filtered = withContext(Dispatchers.Default) {
                StorageFilterHelper.filterStorageTree(scanned, freeSpacePref, systemAppsPref, hiddenFilesPref, systemOSPref, scanned.size)
            }
            rootNode = filtered
            explorerNode = filtered
            explorerPath = filtered?.name ?: "Device Storage"
            explorerStack = emptyList()
            extensionStats = if (filtered != null) StorageFilterHelper.aggregateExtensionStats(filtered) else emptyList()
            topFiles = if (filtered != null) StorageFilterHelper.aggregateTopFiles(filtered) else emptyList()
            selectedNode = null
            selectedPath = null
            isLoading = false
            AppNotifier.finishActivity(context, "Storage scan complete (${FileUtils.formatFileSize(scanned.size)})")
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

            // Prefetch app icons asynchronously in background IO
            launch(Dispatchers.IO) {
                val appsParent = scanned.children?.firstOrNull { it.name == "Apps & System Packages" }
                appsParent?.children?.forEach { appNode ->
                    val pkg = FileUtils.extractPackageName(appNode)
                    if (pkg != null) {
                        AppIconCache.get(context, pkg)
                    }
                }
            }
        }
    }

    fun removeDeletedNodes(deletedNodes: Set<CompactNode>) {
        if (deletedNodes.isEmpty()) return
        val currentRaw = rawScannedNode ?: return
        scope.launch(Dispatchers.Default) {
            val freeSpacePref = prefs.getBoolean("show_free_space", true)
            val systemAppsPref = prefs.getBoolean("show_system_apps", true)
            val hiddenFilesPref = prefs.getBoolean("show_hidden_files", false)
            val systemOSPref = prefs.getBoolean("show_system_os", false)

            val updatedRaw = StorageFilterHelper.pruneNodes(currentRaw, deletedNodes)
            rawScannedNode = updatedRaw
            deviceTotalBytes = updatedRaw.size

            val filtered = StorageFilterHelper.filterStorageTree(
                updatedRaw, freeSpacePref, systemAppsPref, hiddenFilesPref, systemOSPref, updatedRaw.size
            )

            withContext(Dispatchers.Main) {
                rootNode = filtered
                explorerNode = filtered
                extensionStats = if (filtered != null) StorageFilterHelper.aggregateExtensionStats(filtered) else emptyList()
                topFiles = if (filtered != null) StorageFilterHelper.aggregateTopFiles(filtered) else emptyList()
                if (selectedNode != null && deletedNodes.contains(selectedNode)) {
                    selectedNode = null
                    selectedPath = null
                }
            }

            withContext(Dispatchers.IO) {
                try {
                    TreeCacheManager.saveTree(context, updatedRaw)
                } catch (_: Exception) {}
            }
        }
    }

    fun requestScan() {
        if (!hasStoragePermission) return
        val vols = FileUtils.getAvailableStorageVolumes(context)
        detectedVolumes = vols
        selectedVolumeIds = vols.map { it.id }.toSet()
        showVolumeSelectionDialog = true
    }

    fun triggerScan() {
        requestScan()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasStoragePermission = granted
        if (granted) requestScan()
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasStoragePermission = FileUtils.checkStoragePermission(context)
        hasUsageAccess = FileUtils.checkUsageAccessPermission(context)
        if (hasStoragePermission) requestScan()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(hasStoragePermission) {
        if (hasStoragePermission && rootNode == null) {
            val vols = FileUtils.getAvailableStorageVolumes(context)
            val cached = withContext(Dispatchers.IO) {
                try {
                    TreeCacheManager.loadTree(context)
                } catch (_: Exception) {
                    null
                }
            }
            if (cached != null) {
                isLoading = true
                scanPhase = "Restoring storage map..."
                scanDetail = "Loading saved data..."
                rawScannedNode = cached
                deviceTotalBytes = cached.size
                val freeSpacePref = prefs.getBoolean("show_free_space", true)
                val systemAppsPref = prefs.getBoolean("show_system_apps", true)
                val hiddenFilesPref = prefs.getBoolean("show_hidden_files", false)
                val systemOSPref = prefs.getBoolean("show_system_os", false)
                val filtered = withContext(Dispatchers.Default) {
                    StorageFilterHelper.filterStorageTree(cached, freeSpacePref, systemAppsPref, hiddenFilesPref, systemOSPref, cached.size)
                }
                rootNode = filtered
                explorerNode = filtered
                explorerPath = filtered?.name ?: "Device Storage"
                extensionStats = if (filtered != null) StorageFilterHelper.aggregateExtensionStats(filtered) else emptyList()
                topFiles = if (filtered != null) StorageFilterHelper.aggregateTopFiles(filtered) else emptyList()
                isLoading = false
            } else {
                if (vols.isNotEmpty()) {
                    performScan(vols)
                } else {
                    isLoading = false
                    requestScan()
                }
            }
        }
    }

    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    var isPredictiveBackActive by remember { mutableStateOf(false) }
    var predictiveBackSwipeEdge by remember { mutableIntStateOf(androidx.activity.BackEventCompat.EDGE_LEFT) }

    PredictiveBackHandler(enabled = selectedNode != null) { progress ->
        try {
            isPredictiveBackActive = true
            progress.collect { backEvent ->
                predictiveBackSwipeEdge = backEvent.swipeEdge
                predictiveBackProgress = backEvent.progress
            }
            selectedNode = null
            selectedPath = null
        } catch (_: CancellationException) {
        } finally {
            isPredictiveBackActive = false
            predictiveBackProgress = 0f
        }
    }

    PredictiveBackHandler(enabled = selectedTreeNodes.isNotEmpty() && currentRoute == AppDestinations.TREE && selectedNode == null) { progress ->
        try {
            isPredictiveBackActive = true
            progress.collect { backEvent ->
                predictiveBackSwipeEdge = backEvent.swipeEdge
                predictiveBackProgress = backEvent.progress
            }
            selectedTreeNodes = emptyMap()
        } catch (_: CancellationException) {
        } finally {
            isPredictiveBackActive = false
            predictiveBackProgress = 0f
        }
    }

    PredictiveBackHandler(enabled = (isDiscoverSearchActive || discoverSearchQuery.isNotEmpty()) && currentRoute == AppDestinations.DISCOVER && selectedNode == null) { progress ->
        try {
            isPredictiveBackActive = true
            progress.collect { backEvent ->
                predictiveBackSwipeEdge = backEvent.swipeEdge
                predictiveBackProgress = backEvent.progress
            }
            isDiscoverSearchActive = false
            discoverSearchQuery = ""
        } catch (_: CancellationException) {
        } finally {
            isPredictiveBackActive = false
            predictiveBackProgress = 0f
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        com.kd.anddirstat.util.AppNotifier.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            val renderHeaderActions: @Composable () -> Unit = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Box {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showFilterMenu = true
                            },
                            enabled = !isLoading && hasStoragePermission && rawScannedNode != null
                        ) {
                            MaterialSymbol("filter_list", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.onSurface)
                        }

                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                            modifier = Modifier.width(280.dp),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 3.dp,
                            shadowElevation = 3.dp
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Show OS space",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    MaterialSymbol(
                                        name = "settings",
                                        active = showSystemOS,
                                        size = 24.dp,
                                        tint = if (showSystemOS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    Checkbox(
                                        checked = showSystemOS,
                                        onCheckedChange = null
                                    )
                                },
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val newVal = !showSystemOS
                                    showSystemOS = newVal
                                    prefs.edit().putBoolean("show_system_os", newVal).apply()
                                    applyFilter(showFreeSpace, showSystemApps, showHiddenFiles, newVal)
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Show system apps",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    MaterialSymbol(
                                        name = "android",
                                        active = showSystemApps,
                                        size = 24.dp,
                                        tint = if (showSystemApps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    Checkbox(
                                        checked = showSystemApps,
                                        onCheckedChange = null
                                    )
                                },
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val newVal = !showSystemApps
                                    showSystemApps = newVal
                                    prefs.edit().putBoolean("show_system_apps", newVal).apply()
                                    applyFilter(showFreeSpace, newVal, showHiddenFiles, showSystemOS)
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Show free space",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    MaterialSymbol(
                                        name = "storage",
                                        active = showFreeSpace,
                                        size = 24.dp,
                                        tint = if (showFreeSpace) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    Checkbox(
                                        checked = showFreeSpace,
                                        onCheckedChange = null
                                    )
                                },
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val newVal = !showFreeSpace
                                    showFreeSpace = newVal
                                    prefs.edit().putBoolean("show_free_space", newVal).apply()
                                    applyFilter(newVal, showSystemApps, showHiddenFiles, showSystemOS)
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Show hidden files",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    MaterialSymbol(
                                        name = "visibility",
                                        active = showHiddenFiles,
                                        size = 24.dp,
                                        tint = if (showHiddenFiles) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    Checkbox(
                                        checked = showHiddenFiles,
                                        onCheckedChange = null
                                    )
                                },
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val newVal = !showHiddenFiles
                                    showHiddenFiles = newVal
                                    prefs.edit().putBoolean("show_hidden_files", newVal).apply()
                                    applyFilter(showFreeSpace, showSystemApps, newVal, showSystemOS)
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            triggerScan()
                        },
                        enabled = !isLoading && hasStoragePermission
                    ) {
                        MaterialSymbol("refresh", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.onSurface)
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        }
                    ) {
                        MaterialSymbol("settings", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                when (currentRoute) {
                    AppDestinations.TREE -> {
                        TopAppBar(
                            title = {
                                AnimatedAppTitle()
                            },
                            actions = { renderHeaderActions() },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                actionIconContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    AppDestinations.EXPLORER -> {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "Explorer",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            },
                            actions = { renderHeaderActions() },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                actionIconContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    AppDestinations.TYPES -> {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "File Types",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    AppDestinations.DISCOVER -> {
                        TopAppBar(
                            title = {
                                Surface(
                                    shape = RoundedCornerShape(28.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .padding(end = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isDiscoverSearchActive || discoverSearchQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    isDiscoverSearchActive = false
                                                    discoverSearchQuery = ""
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                MaterialSymbol(
                                                    name = "arrow_back",
                                                    active = true,
                                                    size = 22.dp,
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        } else {
                                            MaterialSymbol(
                                                name = "search",
                                                active = true,
                                                size = 24.dp,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        BasicTextField(
                                            value = discoverSearchQuery,
                                            onValueChange = {
                                                discoverSearchQuery = it
                                                if (it.isNotBlank()) {
                                                    isDiscoverSearchActive = true
                                                    com.kd.anddirstat.util.FavoritesManager.addRecentSearch(context, it)
                                                }
                                            },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 16.sp
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            decorationBox = { innerTextField ->
                                                if (discoverSearchQuery.isEmpty()) {
                                                    Text(
                                                        text = "Search files...",
                                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                    )
                                                }
                                                innerTextField()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .onFocusChanged { focusState ->
                                                    if (focusState.isFocused) {
                                                        isDiscoverSearchActive = true
                                                    }
                                                }
                                        )
                                        if (discoverSearchQuery.isNotEmpty()) {
                                            AppTooltip(text = "Clear search") {
                                                IconButton(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        discoverSearchQuery = ""
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    MaterialSymbol(
                                                        name = "close",
                                                        active = true,
                                                        size = 20.dp,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }

                // Active Drive Selector Banner below TopAppBar - only shown when multiple drives / external devices are attached
                if (!isLoading && hasStoragePermission && detectedVolumes.size > 1) {
                    val currentVol = activeVolume ?: detectedVolumes.firstOrNull { it.isPrimary } ?: detectedVolumes.firstOrNull()
                    val volName = currentVol?.name ?: "All Drives"
                    val volIcon = when {
                        currentVol?.isUsb == true -> "usb"
                        currentVol?.isRemovable == true -> "sd_card"
                        currentVol == null -> "storage"
                        else -> "smartphone"
                    }

                    var showDriveDropdown by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showDriveDropdown = true
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                MaterialSymbol(
                                    name = volIcon,
                                    active = true,
                                    size = 16.dp,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = volName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                MaterialSymbol(
                                    name = "arrow_drop_down",
                                    active = true,
                                    size = 18.dp,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showDriveDropdown,
                            onDismissRequest = { showDriveDropdown = false }
                        ) {
                            Text(
                                text = "Select Drive to Map",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                            detectedVolumes.forEach { vol ->
                                val isCurrent = (currentVol?.id == vol.id)
                                val icon = when {
                                    vol.isUsb -> "usb"
                                    vol.isRemovable -> "sd_card"
                                    else -> "smartphone"
                                }
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = vol.name,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${FileUtils.formatFileSize(vol.freeBytes)} free of ${FileUtils.formatFileSize(vol.totalBytes)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        MaterialSymbol(
                                            name = icon,
                                            active = isCurrent,
                                            size = 20.dp,
                                            tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        showDriveDropdown = false
                                        activeVolume = vol
                                        selectedVolumeIds = setOf(vol.id)
                                        performScan(listOf(vol), scanApps = vol.isPrimary)
                                    }
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Custom / Multi-Drive Scan...",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                leadingIcon = {
                                    MaterialSymbol(
                                        name = "tune",
                                        active = true,
                                        size = 20.dp,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    showDriveDropdown = false
                                    showVolumeSelectionDialog = true
                                }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp
            ) {
                val destinations = listOf(
                    Triple(AppDestinations.TREE, "grid_view", "Tree"),
                    Triple(
                        AppDestinations.EXPLORER,
                        if (currentRoute == AppDestinations.EXPLORER) "folder_open" else "folder",
                        "Explorer"
                    ),
                    Triple(AppDestinations.TYPES, "pie_chart", "Types"),
                    Triple(AppDestinations.DISCOVER, "explore", "Discover")
                )

                destinations.forEach { (dest, iconName, label) ->
                    val selected = currentRoute == dest
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.08f else 1.0f,
                        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                        label = "navIconScale"
                    )
                    val targetWeight = if (selected) 700f else 400f
                    val animatedWeight by animateFloatAsState(
                        targetValue = targetWeight,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "navWeightAnimation"
                    )

                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateTo(dest) },
                        icon = {
                            MaterialSymbol(
                                name = iconName,
                                active = selected,
                                size = 24.dp,
                                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontFamily = GoogleSansFlexFontFamily,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight(animatedWeight.toInt().coerceIn(100, 1000)),
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        val destIndexMap = remember {
            mapOf(
                AppDestinations.TREE to 0,
                AppDestinations.EXPLORER to 1,
                AppDestinations.TYPES to 2,
                AppDestinations.DISCOVER to 3
            )
        }

        val backScale = if (isPredictiveBackActive) 1f - (predictiveBackProgress * 0.06f) else 1f
        val backAlpha = if (isPredictiveBackActive) 1f - (predictiveBackProgress * 0.15f) else 1f
        val backCornerRadius = if (isPredictiveBackActive) (predictiveBackProgress * 24).dp else 0.dp
        val slideDirection = if (predictiveBackSwipeEdge == androidx.activity.BackEventCompat.EDGE_RIGHT) -1f else 1f
        val slideOffsetX = if (isPredictiveBackActive) (predictiveBackProgress * 72f * slideDirection) else 0f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .graphicsLayer {
                    translationX = with(density) { slideOffsetX.dp.toPx() }
                    scaleX = backScale
                    scaleY = backScale
                    alpha = backAlpha
                }
                .clip(RoundedCornerShape(backCornerRadius))
        ) {
            when {
                !hasStoragePermission -> {
                    PermissionScreen(
                        onGrant = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                manageStorageLauncher.launch(intent)
                            } else {
                                permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                        }
                    )
                }
                isLoading -> {
                    LoadingScreen(
                        phase = scanPhase,
                        detail = scanDetail
                    )
                }
                rootNode != null -> {
                    // Treemap Canvas is retained in GPU memory
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = if (currentRoute == AppDestinations.TREE) 1f else 0f
                            }
                    ) {
                        if (currentRoute == AppDestinations.TREE) {
                            TreemapCanvas(
                                rootNode = rootNode!!,
                                rootPath = rootNode!!.name,
                                selectedNode = selectedNode,
                                selectedNodes = selectedTreeNodes.keys,
                                resetKey = resetZoomKey,
                                isDark = isDark,
                                pureBlack = pureBlack,
                                onScaleChanged = { currentScale = it },
                                onNodeSelected = { node, path ->
                                    selectedNode = node
                                    selectedPath = path
                                },
                            )
                        }

                        // Floating Selection Bar for Treemap (Rich, expressive, sitting right above bottom navbar)
                        AnimatedVisibility(
                            visible = selectedTreeNodes.isNotEmpty() && currentRoute == AppDestinations.TREE,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessHigh)
                            ) + fadeIn(
                                animationSpec = tween(120, easing = FastOutSlowInEasing)
                            ) + scaleIn(
                                initialScale = 0.92f,
                                animationSpec = spring(stiffness = Spring.StiffnessHigh)
                            ),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = spring(stiffness = Spring.StiffnessHigh)
                            ) + fadeOut(
                                animationSpec = tween(100, easing = FastOutSlowInEasing)
                            ) + scaleOut(
                                targetScale = 0.92f,
                                animationSpec = spring(stiffness = Spring.StiffnessHigh)
                            ),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shadowElevation = 3.dp,
                                tonalElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.size(42.dp)
                                        ) {
                                            IconButton(
                                                onClick = { selectedTreeNodes = emptyMap() },
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                MaterialSymbol(
                                                    name = "close",
                                                    active = true,
                                                    size = 22.dp,
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = "${selectedTreeNodes.size} selected",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = FileUtils.formatFileSize(selectedTreeNodes.keys.sumOf { it.size }, context),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = { showTreeDeleteDialog = true },
                                        shape = RoundedCornerShape(24.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                                        modifier = Modifier.height(44.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            MaterialSymbol(
                                                name = "delete",
                                                active = true,
                                                size = 20.dp,
                                                tint = MaterialTheme.colorScheme.onError
                                            )
                                            Text(
                                                text = "Delete",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onError
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showTreeDeleteDialog && selectedTreeNodes.isNotEmpty()) {
                        val count = selectedTreeNodes.size
                        val totalBytes = selectedTreeNodes.keys.sumOf { it.size }
                        val hasApps = selectedTreeNodes.any { (node, path) -> FileUtils.extractPackageName(node, path, context) != null }
                        val allApps = selectedTreeNodes.all { (node, path) -> FileUtils.extractPackageName(node, path, context) != null }
                        val allAlreadyTrashed = selectedTreeNodes.all { (node, path) -> node.name.startsWith(".trashed") || path.contains(".trashed") || path.contains("[Recycle Bin]") }

                        AlertDialog(
                            onDismissRequest = { showTreeDeleteDialog = false },
                            icon = {
                                MaterialSymbol(
                                    name = "delete",
                                    active = true,
                                    size = 28.dp,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            title = {
                                Text(
                                    text = when {
                                        allApps && count == 1 -> "Uninstall 1 app?"
                                        allApps -> "Uninstall $count apps?"
                                        hasApps -> "Delete / Uninstall $count items?"
                                        allAlreadyTrashed && count == 1 -> "Delete 1 item permanently?"
                                        allAlreadyTrashed -> "Delete $count items permanently?"
                                        count == 1 -> "Move 1 item to Recycle Bin?"
                                        else -> "Move $count items to Recycle Bin?"
                                    },
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Text(
                                    text = when {
                                        allApps -> "$count applications will be uninstalled from device."
                                        allAlreadyTrashed -> "This action is permanent and cannot be undone.\n\n${FileUtils.formatFileSize(totalBytes)} will be freed permanently"
                                        else -> "Selected items will be moved to the Recycle Bin.\n\n${FileUtils.formatFileSize(totalBytes)} to be moved"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showTreeDeleteDialog = false
                                        val itemsToDelete = selectedTreeNodes.toList()
                                        val (starredItems, unstarredItems) = itemsToDelete.partition { (node, path) ->
                                            com.kd.anddirstat.util.FavoritesManager.isStarred(context, path)
                                        }

                                        if (unstarredItems.isEmpty()) {
                                            com.kd.anddirstat.util.AppNotifier.notify("Cannot delete starred files. Unstar them manually first.")
                                            return@TextButton
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
                                                                if (FileUtils.deleteOrTrashFile(f, context)) {
                                                                    processedCount++
                                                                }
                                                            }
                                                        } catch (_: Exception) {}
                                                    }
                                                }
                                            }

                                            if (packagesToUninstall.isNotEmpty()) {
                                                FileUtils.uninstallApps(context, packagesToUninstall)
                                            }

                                            isTreeDeleting = false
                                            selectedTreeNodes = emptyMap()
                                            val baseMsg = if (allAlreadyTrashed) "Deleted $processedCount items permanently" else "Moved $processedCount items to Recycle Bin"
                                            val msg = if (starredItems.isNotEmpty()) "$baseMsg (Skipped ${starredItems.size} starred items)" else baseMsg
                                            com.kd.anddirstat.util.AppNotifier.notify(msg)
                                            performScan(detectedVolumes.ifEmpty { FileUtils.getAvailableStorageVolumes(context) })
                                        }
                                    }
                                ) {
                                    Text(
                                        text = if (allApps) "Uninstall" else if (hasApps) "Delete / Uninstall" else if (allAlreadyTrashed) "Delete" else "Move to Bin",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showTreeDeleteDialog = false }) {
                                    Text("Cancel")
                                }
                            }
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
                        AlertDialog(
                            onDismissRequest = { showVolumeSelectionDialog = false },
                            title = {
                                Text(
                                    text = "Select what to scan",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Section 1: Storage Drives
                                    Text(
                                        text = "Storage Drives",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )

                                    detectedVolumes.forEach { vol ->
                                        val isSelected = selectedVolumeIds.contains(vol.id)
                                        val iconName = when {
                                            vol.isUsb -> "usb"
                                            vol.isRemovable -> "sd_card"
                                            else -> "smartphone"
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable {
                                                    selectedVolumeIds = if (isSelected) {
                                                        if (selectedVolumeIds.size > 1 || scanAppsSelected) selectedVolumeIds - vol.id else selectedVolumeIds
                                                    } else {
                                                        selectedVolumeIds + vol.id
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                MaterialSymbol(
                                                    name = iconName,
                                                    active = true,
                                                    size = 28.dp,
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = vol.name,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "${FileUtils.formatFileSize(vol.freeBytes)} free of ${FileUtils.formatFileSize(vol.totalBytes)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Section 2: Apps & System Packages
                                    Text(
                                        text = "Applications",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (scanAppsSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                if (scanAppsSelected && selectedVolumeIds.isEmpty()) {
                                                    // Keep at least one item selected
                                                } else {
                                                    scanAppsSelected = !scanAppsSelected
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            MaterialSymbol(
                                                name = "apps",
                                                active = true,
                                                size = 28.dp,
                                                tint = if (scanAppsSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Installed Apps & Packages",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Scan app binaries, caches, and app storage",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                val totalCount = selectedVolumeIds.size + if (scanAppsSelected) 1 else 0
                                FilledTonalButton(
                                    onClick = {
                                        showVolumeSelectionDialog = false
                                        val toScan = detectedVolumes.filter { selectedVolumeIds.contains(it.id) }
                                        performScan(toScan, scanApps = scanAppsSelected)
                                    },
                                    enabled = selectedVolumeIds.isNotEmpty() || scanAppsSelected
                                ) {
                                    Text("Scan ($totalCount)", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showVolumeSelectionDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    // Universal file/app details ModalBottomSheet
                    if (selectedNode != null && selectedPath != null) {
                        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        ModalBottomSheet(
                            onDismissRequest = {
                                selectedNode = null
                                selectedPath = null
                            },
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
                                    val n = selectedNode
                                    val p = selectedPath
                                    if (n != null && p != null) {
                                        selectedTreeNodes = if (selectedTreeNodes.containsKey(n)) selectedTreeNodes - n else selectedTreeNodes + (n to p)
                                    }
                                },
                                onDismiss = {
                                    selectedNode = null
                                    selectedPath = null
                                },
                                onDeleted = {
                                    val n = selectedNode
                                    if (n != null) {
                                        selectedTreeNodes = selectedTreeNodes - n
                                    }
                                    selectedNode = null
                                    selectedPath = null
                                    performScan(detectedVolumes.ifEmpty { FileUtils.getAvailableStorageVolumes(context) })
                                }
                            )
                        }
                    }

                    // Accelerated Slide Transitions between main pages
                    AnimatedContent(
                        targetState = currentRoute,
                        transitionSpec = {
                            val initialIdx = destIndexMap[initialState] ?: 0
                            val targetIdx = destIndexMap[targetState] ?: 0
                            val towards = if (targetIdx > initialIdx) AnimatedContentTransitionScope.SlideDirection.Left
                            else AnimatedContentTransitionScope.SlideDirection.Right

                            (slideIntoContainer(
                                towards = towards,
                                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                            ) + fadeIn(
                                animationSpec = tween(durationMillis = 180)
                            )).togetherWith(
                                slideOutOfContainer(
                                    towards = towards,
                                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                                ) + fadeOut(
                                    animationSpec = tween(durationMillis = 150)
                                )
                            )
                        },
                        label = "pageSlideTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { route ->
                        when (route) {
                            AppDestinations.TREE -> {
                                Spacer(modifier = Modifier.fillMaxSize())
                            }
                            AppDestinations.EXPLORER -> {
                                ExplorerView(
                                    rootNode = (rawScannedNode ?: rootNode)!!,
                                    onNodeClick = { child, childPath ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedNode = child
                                        selectedPath = childPath
                                    },
                                    onNodesDeleted = { removeDeletedNodes(it) },
                                    onRefresh = {
                                        performScan(detectedVolumes.ifEmpty { FileUtils.getAvailableStorageVolumes(context) })
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            AppDestinations.TYPES -> {
                                FileTypesView(
                                    rootNode = (rawScannedNode ?: rootNode)!!,
                                    stats = extensionStats,
                                    totalDeviceSize = if (deviceTotalBytes > 0L) deviceTotalBytes else (rawScannedNode ?: rootNode)!!.size,
                                    onNodeClick = { node, path ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedNode = node
                                        selectedPath = path
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            AppDestinations.DISCOVER -> {
                                DiscoverView(
                                    rootNode = (rawScannedNode ?: rootNode)!!,
                                    topFiles = topFiles,
                                    searchQuery = discoverSearchQuery,
                                    isSearchActive = isDiscoverSearchActive || discoverSearchQuery.isNotEmpty(),
                                    onSearchQueryChange = { discoverSearchQuery = it },
                                    onNodeClick = { node, path ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedNode = node
                                        selectedPath = path
                                    },
                                    onNodesDeleted = { removeDeletedNodes(it) },
                                    onRefresh = {
                                        performScan(detectedVolumes.ifEmpty { FileUtils.getAvailableStorageVolumes(context) })
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            // Live Activity Floating Pill
            LiveActivityPill(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
            )

            // Usage Access Warning Banner
            if (hasStoragePermission && !hasUsageAccess) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Grant Usage Access to index app cache & data",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        FilledTonalButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                manageStorageLauncher.launch(intent)
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Grant", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
    }
}
