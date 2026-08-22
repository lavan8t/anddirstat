package com.kd.anddirstat.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.components.MediaThumbnailView
import com.kd.anddirstat.util.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExpressiveNodeDetailsSheet(
    node: CompactNode,
    path: String,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val realFile = remember(path) { FileUtils.resolveActualFile(path) }
    val isRealFile = realFile != null && realFile.exists()
    val appChildren = node.children
    val isAppNode = appChildren != null && appChildren.any {
        it.name.startsWith("App Code") || it.name == "Data" || it.name == "Cache"
    }

    val nameLower = remember(node.name) { node.name.lowercase() }
    val isMediaFile = remember(nameLower) {
        nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || nameLower.endsWith(".avi") ||
        nameLower.endsWith(".mov") || nameLower.endsWith(".webm") || nameLower.endsWith(".3gp") ||
        nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".png") ||
        nameLower.endsWith(".webp") || nameLower.endsWith(".heic") || nameLower.endsWith(".gif")
    }

    val materialItemColor = when {
        isAppNode || nameLower.endsWith(".apk") -> MaterialTheme.colorScheme.error
        nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || nameLower.endsWith(".avi") -> MaterialTheme.colorScheme.primary
        nameLower.endsWith(".jpg") || nameLower.endsWith(".png") || nameLower.endsWith(".webp") -> MaterialTheme.colorScheme.tertiary
        nameLower.endsWith(".mp3") || nameLower.endsWith(".flac") || nameLower.endsWith(".wav") -> MaterialTheme.colorScheme.secondary
        nameLower.endsWith(".pdf") || nameLower.endsWith(".doc") || nameLower.endsWith(".txt") -> MaterialTheme.colorScheme.secondary
        node.isDirectory -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }

    val codeSize = appChildren?.firstOrNull { it.name.startsWith("App Code") }?.size ?: 0L
    val dataSize = appChildren?.firstOrNull { it.name == "Data" }?.size ?: 0L
    val cacheSize = appChildren?.firstOrNull { it.name == "Cache" }?.size ?: 0L
    val pkgName = remember(node) { FileUtils.extractPackageName(node) }

    val dateFormatted = remember(realFile) {
        if (realFile != null && realFile.exists() && realFile.lastModified() > 0) {
            val df = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
            df.format(Date(realFile.lastModified()))
        } else null
    }

    val displayName = if (isAppNode) node.name else if (isRealFile) realFile!!.name else path.substringAfterLast('/')

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding()
    ) {
        // Header Row: Icon/Thumbnail + Full Name + Path + Modified Date
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isAppNode && pkgName != null) {
                AppIconView(
                    packageName = pkgName,
                    contentDescription = node.name,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            } else if (isMediaFile && isRealFile) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    MediaThumbnailView(
                        node = node,
                        path = path,
                        fallbackTint = materialItemColor,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = FileUtils.getNodeIcon(node, isAppNode),
                        contentDescription = null,
                        tint = materialItemColor,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (dateFormatted != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            val isSpecialNode = nameLower == "[system & os]" || nameLower == "system & os" ||
                                nameLower == "[free space]" || nameLower == "free space"

            if (!isSpecialNode) {
                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(
                        onClick = onToggleSelect,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        MaterialSymbol(
                            name = if (isSelected) "check_circle" else "check_circle_outline",
                            active = isSelected,
                            size = 24.dp,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Total Size Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Size",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = FileUtils.formatFileSize(node.size),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // App Size Breakdown (Vertical column: App Code -> Data -> Cache)
        if (isAppNode) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AppSizeRow(label = "App Code", size = codeSize)
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    AppSizeRow(label = "Data", size = dataSize)
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    AppSizeRow(label = "Cache", size = cacheSize)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isAppNode && pkgName != null) {
                val launchIntent = remember(pkgName) { context.packageManager.getLaunchIntentForPackage(pkgName) }
                if (launchIntent != null) {
                    IconButton(
                        onClick = {
                            try {
                                context.startActivity(launchIntent)
                                onDismiss()
                            } catch (_: Exception) {}
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.size(52.dp)
                    ) {
                        MaterialSymbol("open_in_new", active = true, size = 24.dp, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }

                IconButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkgName"))
                            )
                        } catch (_: Exception) {}
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.size(52.dp)
                ) {
                    MaterialSymbol("info", active = true, size = 24.dp, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }

                IconButton(
                    onClick = {
                        if (pkgName != null) {
                            FileUtils.uninstallApp(context, pkgName)
                        }
                        onDismiss()
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.size(52.dp)
                ) {
                    MaterialSymbol("delete", active = true, size = 24.dp, tint = MaterialTheme.colorScheme.onError)
                }
            }

            if (isRealFile && !realFile!!.isDirectory) {
                IconButton(
                    onClick = {
                        FileUtils.openFile(context, realFile)
                        onDismiss()
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.size(52.dp)
                ) {
                    MaterialSymbol("open_in_new", active = true, size = 24.dp, tint = MaterialTheme.colorScheme.onPrimary)
                }
                IconButton(
                    onClick = { showDeleteConfirmation = true },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.size(52.dp)
                ) {
                    MaterialSymbol("delete", active = true, size = 24.dp, tint = MaterialTheme.colorScheme.onError)
                }
            }
        }

        if (showDeleteConfirmation && realFile != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                icon = {
                    Icon(
                        imageVector = FileUtils.getNodeIcon(node),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(
                        text = "Delete file?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "This action is permanent and cannot be undone.\n\n${displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmation = false
                            if (realFile.delete()) {
                                Toast.makeText(context, "Deleted: ${realFile.name}", Toast.LENGTH_SHORT).show()
                                onDeleted()
                            } else {
                                Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun AppSizeRow(label: String, size: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = FileUtils.formatFileSize(size),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


