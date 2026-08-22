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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.treemap.getNodeColor
import com.kd.anddirstat.ui.components.AppIconView
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.util.FileUtils
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ExpressiveNodeDetailsCard(
    node: CompactNode,
    path: String,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val realFile = remember(path) { FileUtils.resolveActualFile(path) }
    val isRealFile = realFile != null && realFile.exists()
    val appChildren = node.children
    val isAppNode = appChildren != null && appChildren.any {
        it.name.startsWith("App Code") || it.name == "Data" || it.name == "Cache"
    }
    val itemIcon = FileUtils.getNodeIcon(node, isAppNode)
    val itemColor = getNodeColor(node)
    val codeSize = appChildren?.firstOrNull { it.name.startsWith("App Code") }?.size ?: 0L
    val dataSize = appChildren?.firstOrNull { it.name == "Data" }?.size ?: 0L
    val cacheSize = appChildren?.firstOrNull { it.name == "Cache" }?.size ?: 0L
    val pkgName = remember(node) { FileUtils.extractPackageName(node) }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = null,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row: Icon + Title + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isAppNode && pkgName != null) {
                    AppIconView(
                        packageName = pkgName,
                        contentDescription = node.name,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = itemColor.copy(alpha = 0.20f),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = itemIcon,
                                contentDescription = null,
                                tint = itemColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAppNode) node.name else if (isRealFile) realFile!!.name else path.substringAfterLast('/'),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(36.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        MaterialSymbol(
                            name = "close",
                            active = true,
                            size = 18.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Size Container (Filled banner)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = FileUtils.formatFileSize(node.size),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${NumberFormat.getNumberInstance(Locale.US).format(node.size)} Bytes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = if (isAppNode) "Application" else if (node.isDirectory) "Directory" else "File",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (isAppNode) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("App Code", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(FileUtils.formatFileSize(codeSize), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(FileUtils.formatFileSize(dataSize), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Cache", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(FileUtils.formatFileSize(cacheSize), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons (Filled only, M3 Colors only, No borders)
            if (isAppNode && pkgName != null) {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$pkgName")
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MaterialSymbol("info", active = true, size = 20.dp, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("App Info & Storage", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            } else if (isRealFile && !realFile!!.isDirectory) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { FileUtils.openFile(context, realFile) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        MaterialSymbol("open_in_new", active = true, size = 20.dp, tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            if (realFile.delete()) {
                                Toast.makeText(context, "Deleted: ${realFile.name}", Toast.LENGTH_SHORT).show()
                                onDeleted()
                            } else {
                                Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        MaterialSymbol("delete", active = true, size = 20.dp, tint = MaterialTheme.colorScheme.onError)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
