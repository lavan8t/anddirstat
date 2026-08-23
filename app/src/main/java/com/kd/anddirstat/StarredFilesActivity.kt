package com.kd.anddirstat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kd.anddirstat.ui.components.AppTooltip
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.util.AppNotifier
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileUtils
import java.io.File

data class StarredItem(
    val path: String,
    val file: File,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val exists: Boolean
)

class StarredFilesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("anddirstat_prefs", Context.MODE_PRIVATE)
        val themePref = prefs.getString("app_theme", AppTheme.SYSTEM.key) ?: AppTheme.SYSTEM.key
        val currentTheme = AppTheme.entries.firstOrNull { it.key == themePref } ?: AppTheme.SYSTEM
        val pureBlack = prefs.getBoolean("pure_black", false)
        val dynamicTheme = prefs.getBoolean("dynamic_theme", false)
        val accentPref = prefs.getString("accent_color", AccentColor.GREEN.key) ?: AccentColor.GREEN.key
        val accentColor = AccentColor.entries.firstOrNull { it.key == accentPref } ?: AccentColor.GREEN

        setContent {
            AndDirStatTheme(
                appTheme = currentTheme,
                pureBlack = pureBlack,
                dynamicTheme = dynamicTheme,
                accentColor = accentColor
            ) {
                StarredFilesScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredFilesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }
    var starredList by remember { mutableStateOf<List<StarredItem>>(emptyList()) }
    val selectedPaths = remember { mutableStateListOf<String>() }

    fun refreshList() {
        val paths = FavoritesManager.getStarredPaths(context)
        starredList = paths.map { path ->
            val file = File(path)
            StarredItem(
                path = path,
                file = file,
                name = file.name.ifEmpty { path },
                size = if (file.exists()) file.length() else 0L,
                isDirectory = file.isDirectory,
                exists = file.exists()
            )
        }.sortedBy { it.name.lowercase() }
    }

    LaunchedEffect(Unit) {
        refreshList()
    }

    val filteredList = remember(starredList, searchQuery) {
        if (searchQuery.isBlank()) starredList
        else starredList.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.path.contains(searchQuery, ignoreCase = true)
        }
    }

    val totalSize = remember(starredList) { starredList.sumOf { it.size } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search starred files…") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "Starred",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            searchQuery = ""
                        } else {
                            onBack()
                        }
                    }) {
                        MaterialSymbol(name = "arrow_back", active = true)
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            MaterialSymbol(name = "search", active = true)
                        }
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isGridView = !isGridView
                        }) {
                            MaterialSymbol(name = if (isGridView) "view_list" else "grid_view", active = true)
                        }
                    } else if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            MaterialSymbol(name = "close", active = true)
                        }
                    }
                    if (selectedPaths.isNotEmpty()) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedPaths.forEach { FavoritesManager.toggleStar(context, it) }
                            selectedPaths.clear()
                            refreshList()
                            AppNotifier.notify("Unstarred selected items")
                        }) {
                            MaterialSymbol(name = "star_outline", active = true, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selectedPaths.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${selectedPaths.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedPaths.forEach { FavoritesManager.toggleStar(context, it) }
                                    selectedPaths.clear()
                                    refreshList()
                                    AppNotifier.notify("Unstarred selected items")
                                }
                            ) {
                                MaterialSymbol(name = "star_outline", active = true, size = 18.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Unstar")
                            }
                            IconButton(onClick = { selectedPaths.clear() }) {
                                MaterialSymbol(name = "close", active = true)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (starredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    MaterialSymbol(
                        name = "star",
                        active = true,
                        size = 56.dp,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "No Starred Files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Star files to protect them from deletion and access them quickly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 6.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    start = 6.dp,
                    end = 6.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                items(filteredList, key = { it.path }) { item ->
                    val isSelected = selectedPaths.contains(item.path)
                    val isMedia = remember(item.name, item.isDirectory) {
                        if (item.isDirectory) false
                        else {
                            val l = item.name.lowercase()
                            l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp") ||
                            l.endsWith(".heic") || l.endsWith(".gif") || l.endsWith(".mp4") || l.endsWith(".mkv") ||
                            l.endsWith(".apk")
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = {
                                    if (selectedPaths.isNotEmpty()) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (isSelected) selectedPaths.remove(item.path) else selectedPaths.add(item.path)
                                    } else if (item.exists) {
                                        FileUtils.openFile(context, item.file)
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isSelected) selectedPaths.remove(item.path) else selectedPaths.add(item.path)
                                }
                            )
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isMedia && item.exists) {
                                MediaThumbnailView(path = item.path, modifier = Modifier.fillMaxSize())
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    MaterialSymbol(
                                        name = if (item.isDirectory) "folder" else "description",
                                        active = true,
                                        size = 40.dp,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Bottom Scrim with file name and size
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                        )
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Column {
                                    Text(
                                        text = FileUtils.middleEllipsis(item.name, 16),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (item.isDirectory) "Folder" else FileUtils.formatFileSize(item.size, context),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            MaterialSymbol("check", active = true, size = 20.dp, tint = MaterialTheme.colorScheme.onPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                itemsIndexed(filteredList, key = { _, item -> item.path }) { _, item ->
                    val isSelected = selectedPaths.contains(item.path)
                    val isMedia = remember(item.name, item.isDirectory) {
                        if (item.isDirectory) false
                        else {
                            val l = item.name.lowercase()
                            l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp") ||
                            l.endsWith(".heic") || l.endsWith(".gif") || l.endsWith(".mp4") || l.endsWith(".mkv") ||
                            l.endsWith(".apk")
                        }
                    }

                    ListItem(
                        leadingContent = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (isSelected) selectedPaths.remove(item.path) else selectedPaths.add(item.path)
                                    }
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        MaterialSymbol(name = "check", active = true, size = 24.dp, tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                } else if (isMedia && item.exists) {
                                    MediaThumbnailView(
                                        path = item.path,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            MaterialSymbol(
                                                name = if (item.isDirectory) "folder" else "description",
                                                active = true,
                                                size = 24.dp,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        headlineContent = {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                text = "${if (item.isDirectory) "Folder" else FileUtils.formatFileSize(item.size, context)} • ${item.path}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                FavoritesManager.toggleStar(context, item.path)
                                refreshList()
                                AppNotifier.notify("Unstarred ${item.name}")
                            }) {
                                MaterialSymbol(
                                    name = "star",
                                    active = true,
                                    size = 22.dp,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (selectedPaths.isNotEmpty()) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (isSelected) selectedPaths.remove(item.path) else selectedPaths.add(item.path)
                                    } else if (item.exists) {
                                        FileUtils.openFile(context, item.file)
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isSelected) selectedPaths.remove(item.path) else selectedPaths.add(item.path)
                                }
                            )
                    )
                }
            }
        }
    }
}
