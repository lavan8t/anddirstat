package com.kd.anddirstat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import android.content.SharedPreferences
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.screens.DiscoverView
import com.kd.anddirstat.ui.screens.ExplorerView
import com.kd.anddirstat.ui.screens.ExpressiveNodeDetailsSheet
import com.kd.anddirstat.ui.screens.FileTypesView
import com.kd.anddirstat.ui.screens.LoadingScreen
import com.kd.anddirstat.ui.screens.PermissionScreen
import com.kd.anddirstat.ui.screens.SettingsView
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent {
            MainApp()
        }
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
        mutableStateOf(prefs.getBoolean("dynamic_theme", android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S))
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
        var showHiddenFiles by remember { mutableStateOf(prefs.getBoolean("show_hidden_files", true)) }
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
        var selectedTreeNodes by remember { mutableStateOf(setOf<CompactNode>()) }
        var showTreeDeleteDialog by remember { mutableStateOf(false) }

        fun applyFilter(freeSpace: Boolean, systemApps: Boolean, hiddenFiles: Boolean = showHiddenFiles) {
            val raw = rawScannedNode ?: return
            val filtered = StorageFilterHelper.filterStorageTree(raw, freeSpace, systemApps, hiddenFiles, deviceTotalBytes)
            rootNode = filtered
            explorerNode = filtered
            explorerPath = filtered?.name ?: "Device Storage"
            explorerStack = emptyList()
            extensionStats = if (filtered != null) StorageFilterHelper.aggregateExtensionStats(filtered) else emptyList()
            topFiles = if (filtered != null) StorageFilterHelper.aggregateTopFiles(filtered) else emptyList()
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

    fun navigateTo(dest: String) {
        if (currentRoute == dest) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        previousRoute = currentRoute
        currentRoute = dest
    }

    fun triggerScan() {
        if (!hasStoragePermission) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        isLoading = true
        scanPhase = "Analyzing storage..."
        scanDetail = "Starting scan..."
        scope.launch {
            val freeSpacePref = prefs.getBoolean("show_free_space", true)
            val systemAppsPref = prefs.getBoolean("show_system_apps", true)
            val hiddenFilesPref = prefs.getBoolean("show_hidden_files", true)
            showFreeSpace = freeSpacePref
            showSystemApps = systemAppsPref
            showHiddenFiles = hiddenFilesPref
            val scanner = StorageScanner(context)
            val scanned = scanner.scanStorage(includeFreeSpace = true) { phase, detail ->
                scanPhase = phase
                scanDetail = detail
            }
            rawScannedNode = scanned
            deviceTotalBytes = scanned.size

            val filtered = withContext(Dispatchers.Default) {
                StorageFilterHelper.filterStorageTree(scanned, freeSpacePref, systemAppsPref, hiddenFilesPref, scanned.size)
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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasStoragePermission = granted
        if (granted) triggerScan()
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasStoragePermission = FileUtils.checkStoragePermission(context)
        hasUsageAccess = FileUtils.checkUsageAccessPermission(context)
        if (hasStoragePermission) triggerScan()
    }

    LaunchedEffect(hasStoragePermission) {
        if (hasStoragePermission && rootNode == null) {
            isLoading = true
            scanPhase = "Restoring storage map..."
            scanDetail = "Loading cached data..."
            val cached = withContext(Dispatchers.IO) {
                TreeCacheManager.loadTree(context)
            }
            if (cached != null) {
                rawScannedNode = cached
                deviceTotalBytes = cached.size
                val freeSpacePref = prefs.getBoolean("show_free_space", true)
                val systemAppsPref = prefs.getBoolean("show_system_apps", true)
                val hiddenFilesPref = prefs.getBoolean("show_hidden_files", true)
                val filtered = withContext(Dispatchers.Default) {
                    StorageFilterHelper.filterStorageTree(cached, freeSpacePref, systemAppsPref, hiddenFilesPref, cached.size)
                }
                rootNode = filtered
                explorerNode = filtered
                explorerPath = filtered?.name ?: "Device Storage"
                extensionStats = if (filtered != null) StorageFilterHelper.aggregateExtensionStats(filtered) else emptyList()
                topFiles = if (filtered != null) StorageFilterHelper.aggregateTopFiles(filtered) else emptyList()
                isLoading = false
            } else {
                triggerScan()
            }
        }
    }

    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    var isPredictiveBackActive by remember { mutableStateOf(false) }

    PredictiveBackHandler(enabled = selectedNode != null) { progress ->
        try {
            isPredictiveBackActive = true
            progress.collect { backEvent ->
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
                predictiveBackProgress = backEvent.progress
            }
            selectedTreeNodes = emptySet()
        } catch (_: CancellationException) {
        } finally {
            isPredictiveBackActive = false
            predictiveBackProgress = 0f
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            when (currentRoute) {
                AppDestinations.TREE -> {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "And",
                                    fontFamily = GoogleSansFlexTitleAndFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "DirStat",
                                    fontFamily = GoogleSansFlexTitleDirStatFamily,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        actions = {
                            Box {
                                IconButton(
                                    onClick = { showFilterMenu = true },
                                    enabled = !isLoading && hasStoragePermission && rawScannedNode != null
                                ) {
                                    MaterialSymbol("filter_list", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(
                                    expanded = showFilterMenu,
                                    onDismissRequest = { showFilterMenu = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Show System Apps",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        leadingIcon = {
                                            Checkbox(
                                                checked = showSystemApps,
                                                onCheckedChange = null
                                            )
                                        },
                                        onClick = {
                                            val newVal = !showSystemApps
                                            showSystemApps = newVal
                                            prefs.edit().putBoolean("show_system_apps", newVal).apply()
                                            applyFilter(showFreeSpace, newVal, showHiddenFiles)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Show Free Space",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        leadingIcon = {
                                            Checkbox(
                                                checked = showFreeSpace,
                                                onCheckedChange = null
                                            )
                                        },
                                        onClick = {
                                            val newVal = !showFreeSpace
                                            showFreeSpace = newVal
                                            prefs.edit().putBoolean("show_free_space", newVal).apply()
                                            applyFilter(newVal, showSystemApps, showHiddenFiles)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Show Hidden Files",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        leadingIcon = {
                                            Checkbox(
                                                checked = showHiddenFiles,
                                                onCheckedChange = null
                                            )
                                        },
                                        onClick = {
                                            val newVal = !showHiddenFiles
                                            showHiddenFiles = newVal
                                            prefs.edit().putBoolean("show_hidden_files", newVal).apply()
                                            applyFilter(showFreeSpace, showSystemApps, newVal)
                                        }
                                    )
                                }
                            }
                            IconButton(
                                onClick = { triggerScan() },
                                enabled = !isLoading && hasStoragePermission
                            ) {
                                MaterialSymbol("refresh", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(
                                onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }
                            ) {
                                MaterialSymbol("settings", active = true, size = 22.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
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
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                AppDestinations.DISCOVER -> {
                    TopAppBar(
                        title = {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MaterialSymbol(
                                        name = "search",
                                        active = true,
                                        size = 22.dp,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    BasicTextField(
                                        value = discoverSearchQuery,
                                        onValueChange = { discoverSearchQuery = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        decorationBox = { innerTextField ->
                                            if (discoverSearchQuery.isEmpty()) {
                                                Text(
                                                    text = "Search files...",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                            innerTextField()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (discoverSearchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { discoverSearchQuery = "" },
                                            modifier = Modifier.size(24.dp)
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
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
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
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
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

        val backScale = if (isPredictiveBackActive) 1f - (predictiveBackProgress * 0.08f) else 1f
        val backAlpha = if (isPredictiveBackActive) 1f - (predictiveBackProgress * 0.20f) else 1f
        val backCornerRadius = if (isPredictiveBackActive) (predictiveBackProgress * 24).dp else 0.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .graphicsLayer {
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
                        TreemapCanvas(
                            rootNode = rootNode!!,
                            rootPath = rootNode!!.name,
                            selectedNode = selectedNode,
                            selectedNodes = selectedTreeNodes,
                            resetKey = resetZoomKey,
                            isDark = isDark,
                            pureBlack = pureBlack,
                            onScaleChanged = { currentScale = it },
                            onNodeSelected = { node, path ->
                                selectedNode = node
                                selectedPath = path
                            },
                        )

                        // Floating Selection Bar for Treemap
                        AnimatedVisibility(
                            visible = selectedTreeNodes.isNotEmpty() && currentRoute == AppDestinations.TREE,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shadowElevation = 8.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { selectedTreeNodes = emptySet() },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            MaterialSymbol(
                                                name = "close",
                                                active = true,
                                                size = 20.dp,
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "${selectedTreeNodes.size} selected",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = FileUtils.formatFileSize(selectedTreeNodes.sumOf { it.size }),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        IconButton(
                                            onClick = { showTreeDeleteDialog = true },
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            MaterialSymbol(
                                                name = "delete",
                                                active = true,
                                                size = 20.dp,
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showTreeDeleteDialog && selectedTreeNodes.isNotEmpty()) {
                        val count = selectedTreeNodes.size
                        val totalBytes = selectedTreeNodes.sumOf { it.size }
                        AlertDialog(
                            onDismissRequest = { showTreeDeleteDialog = false },
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            title = {
                                Text(
                                    text = if (count == 1) "Delete 1 item?" else "Delete $count items?",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Text(
                                    text = "This action is permanent and cannot be undone.\n\nTotal space to free: ${FileUtils.formatFileSize(totalBytes)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showTreeDeleteDialog = false
                                        selectedTreeNodes.forEach { node ->
                                            try {
                                                val f = FileUtils.resolveActualFile(node.name)
                                                if (f != null && f.exists()) f.deleteRecursively()
                                            } catch (_: Exception) {}
                                        }
                                        selectedTreeNodes = emptySet()
                                        triggerScan()
                                    }
                                ) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showTreeDeleteDialog = false }) {
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
                                isSelected = selectedTreeNodes.contains(selectedNode),
                                onToggleSelect = {
                                    val n = selectedNode
                                    if (n != null) {
                                        selectedTreeNodes = if (selectedTreeNodes.contains(n)) selectedTreeNodes - n else selectedTreeNodes + n
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
                                    triggerScan()
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
                                    rootNode = rootNode!!,
                                    onNodeClick = { child, childPath ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedNode = child
                                        selectedPath = childPath
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            AppDestinations.TYPES -> {
                                FileTypesView(
                                    rootNode = rootNode!!,
                                    stats = extensionStats,
                                    totalDeviceSize = if (deviceTotalBytes > 0L) deviceTotalBytes else rootNode!!.size,
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
                                    rootNode = rootNode!!,
                                    topFiles = topFiles,
                                    searchQuery = discoverSearchQuery,
                                    onNodeClick = { node, path ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedNode = node
                                        selectedPath = path
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

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
