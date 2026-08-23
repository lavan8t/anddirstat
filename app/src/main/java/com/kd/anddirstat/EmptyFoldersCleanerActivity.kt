package com.kd.anddirstat

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kd.anddirstat.ui.components.DeletionProgressDialog
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EmptyFoldersCleanerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
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
        enableEdgeToEdge()

        setContent {
            AndDirStatAppTheme {
                EmptyFoldersCleanerView(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmptyFoldersCleanerView(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(true) }
    var emptyFolders by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }

    var isDeleting by remember { mutableStateOf(false) }
    var deleteCurrentCount by remember { mutableIntStateOf(0) }
    var deleteTotalCount by remember { mutableIntStateOf(0) }
    var deleteCurrentFileName by remember { mutableStateOf("") }

    fun scanEmptyFolders() {
        scope.launch {
            isScanning = true
            val results = withContext(Dispatchers.IO) {
                val found = mutableListOf<File>()

                fun isFolderEmpty(dir: File): Boolean {
                    if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return false
                    val name = dir.name.lowercase()
                    if (name == "android" || name.startsWith(".trashed") || name == "system volume information") return false
                    val children = dir.listFiles() ?: return false
                    if (children.isEmpty()) {
                        found.add(dir)
                        return true
                    }
                    var allSubEmpty = true
                    for (child in children) {
                        if (child.isDirectory) {
                            val subEmpty = isFolderEmpty(child)
                            if (!subEmpty) allSubEmpty = false
                        } else {
                            allSubEmpty = false
                        }
                    }
                    if (allSubEmpty) {
                        found.add(dir)
                        return true
                    }
                    return false
                }

                // Scan root storage directory
                val rootStorage = Environment.getExternalStorageDirectory()
                if (rootStorage != null && rootStorage.exists()) {
                    val topDirs = rootStorage.listFiles()?.filter { it.isDirectory && it.canRead() } ?: emptyList()
                    for (dir in topDirs) {
                        val n = dir.name.lowercase()
                        if (n != "android" && !n.startsWith(".")) {
                            isFolderEmpty(dir)
                        }
                    }
                }

                found.distinctBy { it.absolutePath }.sortedBy { it.absolutePath }
            }

            emptyFolders = results
            selectedFolders = results.map { it.absolutePath }.toSet()
            isScanning = false
        }
    }

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    LaunchedEffect(Unit) {
        scanEmptyFolders()
    }

    LaunchedEffect(Unit) {
        com.kd.anddirstat.util.AppNotifier.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    fun handleDeleteSelected() {
        val toDelete = emptyFolders.filter { selectedFolders.contains(it.absolutePath) }
        if (toDelete.isEmpty()) return

        val (starred, unstarred) = toDelete.partition { FavoritesManager.isStarred(context, it.absolutePath) }
        if (unstarred.isEmpty()) {
            com.kd.anddirstat.util.AppNotifier.notify("Cannot delete starred folders. Unstar them first.")
            return
        }

        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            isDeleting = true
            deleteTotalCount = unstarred.size
            var deletedCount = 0

            withContext(Dispatchers.IO) {
                // Delete deepest folders first
                val sortedToDelete = unstarred.sortedByDescending { it.absolutePath.length }
                sortedToDelete.forEachIndexed { index, file ->
                    deleteCurrentCount = index + 1
                    deleteCurrentFileName = file.name
                    try {
                        if (file.exists() && file.isDirectory) {
                            if (file.delete()) {
                                deletedCount++
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            isDeleting = false
            val baseMsg = "Deleted $deletedCount empty folders"
            val msg = if (starred.isNotEmpty()) "$baseMsg (Skipped ${starred.size} starred)" else baseMsg
            com.kd.anddirstat.util.AppNotifier.notify(msg)
            scanEmptyFolders()
        }
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Empty Folders Cleaner",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isScanning && emptyFolders.isNotEmpty()) {
                            Text(
                                text = "${selectedFolders.size} of ${emptyFolders.size} selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        MaterialSymbol("arrow_back", active = true, size = 24.dp)
                    }
                },
                actions = {
                    if (!isScanning && emptyFolders.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedFolders = if (selectedFolders.size == emptyFolders.size) {
                                    emptySet()
                                } else {
                                    emptyFolders.map { it.path }.toSet()
                                }
                            }
                        ) {
                            Text(
                                text = if (selectedFolders.size == emptyFolders.size) "Deselect All" else "Select All",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isScanning -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Scanning storage for empty folders...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                emptyFolders.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                MaterialSymbol("check_circle", active = true, size = 40.dp, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "No Empty Folders!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your storage is tidy and free of empty directories.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onBack,
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Text("Back to Discover", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(emptyFolders, key = { it.absolutePath }) { file ->
                                val isSelected = selectedFolders.contains(file.absolutePath)
                                val isStarred = FavoritesManager.isStarred(context, file.absolutePath)

                                Card(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedFolders = if (isSelected) {
                                            selectedFolders - file.absolutePath
                                        } else {
                                            selectedFolders + file.absolutePath
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                        else
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedFolders = if (checked) {
                                                    selectedFolders + file.absolutePath
                                                } else {
                                                    selectedFolders - file.absolutePath
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MaterialTheme.colorScheme.primary
                                            )
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        MaterialSymbol(
                                            name = "folder_open",
                                            active = true,
                                            size = 24.dp,
                                            tint = MaterialTheme.colorScheme.primary
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = file.name.ifEmpty { file.absolutePath.substringAfterLast('/') },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (isStarred) {
                                                    MaterialSymbol("star", active = true, size = 16.dp, tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = file.absolutePath,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Action Bar: Thicc Fully Rounded Delete Button
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Button(
                                    onClick = { handleDeleteSelected() },
                                    enabled = selectedFolders.isNotEmpty(),
                                    shape = RoundedCornerShape(28.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        MaterialSymbol(
                                            name = "delete",
                                            active = selectedFolders.isNotEmpty(),
                                            size = 22.dp,
                                            tint = if (selectedFolders.isNotEmpty()) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                        Text(
                                            text = if (selectedFolders.isNotEmpty())
                                                "Delete ${selectedFolders.size} Empty Folders"
                                            else
                                                "Select Folders to Delete",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            DeletionProgressDialog(
                visible = isDeleting,
                currentCount = deleteCurrentCount,
                totalCount = deleteTotalCount,
                currentFileName = deleteCurrentFileName,
                isTrash = false
            )
        }
    }
}
