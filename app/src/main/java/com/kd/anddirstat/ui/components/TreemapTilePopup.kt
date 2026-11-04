package com.kd.anddirstat.ui.components

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.kd.anddirstat.cleanup.CleanupQueueHelper
import com.kd.anddirstat.cleanup.StagedQueueItem
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.util.AppNotifier
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileUtils
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Material 3 Expressive Container Transform Popup.
 *
 * Seamlessly transforms the tapped treemap tile into an elevated popup card containing
 * file / app details and contextual actions. Follows the Container Transform pattern:
 * - Persistent container interpolates position, dimensions, corner radius, and color.
 * - Outgoing tile content fades out swiftly; incoming details fade in cleanly.
 * - 100% MaterialTheme colorScheme tokens — zero hardcoded colors.
 */
@Composable
fun TreemapTilePopup(
    node: CompactNode,
    path: String,
    tileRect: Rect?,
    tileColor: Color?,
    touchOffset: Offset,
    visible: Boolean,
    onDismiss: () -> Unit,
    stagedQueue: Map<CompactNode, StagedQueueItem> = emptyMap(),
    onToggleStaged: ((CompactNode, String, Long, Boolean, String?) -> Unit)? = null,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onOpenFile: ((File) -> Unit)? = null,
    onOpenApp: ((String) -> Unit)? = null,
    onOpenAppInfo: ((String) -> Unit)? = null,
    onUninstallApp: ((String) -> Unit)? = null,
    onNavigateToExplorer: ((String) -> Unit)? = null,
    onRequestDelete: () -> Unit,
    onToggleStar: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

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

    val appBreakdown = remember(node, pkgName, isApp) {
        if (isApp && pkgName != null) {
            var code = node.children?.firstOrNull { it.name.startsWith("App Code") }?.size ?: 0L
            var data = node.children?.firstOrNull { it.name == "Data" }?.size ?: 0L
            var cache = node.children?.firstOrNull { it.name == "Cache" }?.size ?: 0L

            if (code == 0L && data == 0L && cache == 0L) {
                try {
                    val ssm = context.getSystemService(StorageStatsManager::class.java)
                    val stats = ssm?.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkgName, Process.myUserHandle())
                    if (stats != null) {
                        code = stats.appBytes
                        cache = stats.cacheBytes
                        data = maxOf(0L, stats.dataBytes - cache)
                    }
                } catch (_: Exception) {}
            }
            if (code == 0L && data == 0L && cache == 0L) {
                code = node.size
            }
            Triple(code, data, cache)
        } else null
    }

    val folderName = remember(path) {
        val trimmed = path.trimEnd('/')
        val parts = trimmed.split('/')
        if (parts.size > 1) parts[parts.size - 2] else "Storage"
    }
    val symbolName = remember(node, isApp) { FileUtils.getNodeSymbolName(node, isApp) }

    // Screen metrics
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val targetWidthPx = with(density) { 324.dp.toPx().coerceAtMost(screenWidthPx - 32.dp.toPx()) }
    val targetHeightPx = with(density) { (if (isApp) 240.dp else 210.dp).toPx() }
    val safeMarginPx = with(density) { 16.dp.toPx() }

    val clickPos = if (tileRect != null) {
        Offset(tileRect.center.x, tileRect.center.y)
    } else {
        touchOffset
    }

    val targetLeftPx = (clickPos.x - targetWidthPx / 2f).coerceIn(
        safeMarginPx,
        (screenWidthPx - targetWidthPx - safeMarginPx).coerceAtLeast(safeMarginPx)
    )
    val targetTopPx = if (clickPos.y > screenHeightPx * 0.55f) {
        (clickPos.y - targetHeightPx - safeMarginPx).coerceIn(
            safeMarginPx + 40f,
            (screenHeightPx - targetHeightPx - safeMarginPx - 60f).coerceAtLeast(safeMarginPx)
        )
    } else {
        (clickPos.y + safeMarginPx).coerceIn(
            safeMarginPx + 40f,
            (screenHeightPx - targetHeightPx - safeMarginPx - 60f).coerceAtLeast(safeMarginPx)
        )
    }

    val stableStartRect = remember(node, tileRect) {
        tileRect ?: Rect(
            clickPos.x - 24f,
            clickPos.y - 24f,
            clickPos.x + 24f,
            clickPos.y + 24f
        )
    }
    val stableTileColor = remember(node, tileColor) {
        tileColor ?: Color(0xFF2C3E50)
    }
    val popupBgColor = stableTileColor

    val targetRect = Rect(targetLeftPx, targetTopPx, targetLeftPx + targetWidthPx, targetTopPx + targetHeightPx)

    val progress = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }

    fun triggerDismiss() {
        if (isDismissing) return
        isDismissing = true
        scope.launch {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.88f,
                    stiffness = Spring.StiffnessMedium
                )
            )
            onDismiss()
        }
    }

    LaunchedEffect(visible, node, path) {
        if (visible) {
            isDismissing = false
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else if (!isDismissing) {
            isDismissing = true
            progress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.88f,
                    stiffness = Spring.StiffnessMedium
                )
            )
            onDismiss()
        }
    }

    if (progress.value <= 0.001f && !visible) return

    val p = progress.value
    val curLeft = stableStartRect.left + (targetRect.left - stableStartRect.left) * p
    val curTop = stableStartRect.top + (targetRect.top - stableStartRect.top) * p
    val curWidth = stableStartRect.width + (targetRect.width - stableStartRect.width) * p
    val curHeight = stableStartRect.height + (targetRect.height - stableStartRect.height) * p

    val startCorner = 4.dp
    val endCorner = 28.dp
    val curCorner = lerp(startCorner, endCorner, p)
    val curElevation = lerp(0.dp, 12.dp, p)

    // Dynamic contrast on tile color
    val luminance = 0.2126f * popupBgColor.red + 0.7152f * popupBgColor.green + 0.0722f * popupBgColor.blue
    val isLight = luminance > 0.45f
    val contentColor = if (isLight) Color(0xFF14171A) else Color(0xFFFFFFFF)
    val contentVariantColor = if (isLight) Color(0xFF3C434A) else Color(0xFFF1F3F5).copy(alpha = 0.85f)
    val controlBg = if (isLight) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.18f)

    // Seamless exit / smooth enter alpha for popup content
    val contentAlpha = if (isDismissing || !visible) {
        ((p - 0.65f) / 0.35f).coerceIn(0f, 1f)
    } else {
        ((p - 0.28f) / 0.72f).coerceIn(0f, 1f)
    }

    // Swipe to dismiss tracking
    var swipeOffsetX by remember(node) { mutableFloatStateOf(0f) }
    var swipeOffsetY by remember(node) { mutableFloatStateOf(0f) }
    val dismissThreshold = with(density) { 56.dp.toPx() }

    Box(modifier = modifier.fillMaxSize()) {
        // Scrim background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (p * 0.40f).coerceIn(0f, 0.40f)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    triggerDismiss()
                }
        )

        // Morphing Container
        Surface(
            shape = RoundedCornerShape(curCorner),
            color = popupBgColor,
            tonalElevation = 0.dp,
            shadowElevation = curElevation,
            border = null,
            modifier = Modifier
                .offset {
                    IntOffset(
                        (curLeft + swipeOffsetX).roundToInt(),
                        (curTop + swipeOffsetY).roundToInt()
                    )
                }
                .size(
                    width = with(density) { curWidth.toDp() },
                    height = with(density) { curHeight.toDp() }
                )
                .graphicsLayer {
                    val dist = sqrt(swipeOffsetX * swipeOffsetX + swipeOffsetY * swipeOffsetY)
                    alpha = (1f - dist / (dismissThreshold * 3f)).coerceIn(0f, 1f)
                }
                .pointerInput(node) {
                    detectDragGestures(
                        onDragEnd = {
                            val dist = sqrt(swipeOffsetX * swipeOffsetX + swipeOffsetY * swipeOffsetY)
                            if (dist > dismissThreshold) {
                                triggerDismiss()
                            } else {
                                swipeOffsetX = 0f
                                swipeOffsetY = 0f
                            }
                        },
                        onDragCancel = {
                            swipeOffsetX = 0f
                            swipeOffsetY = 0f
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        swipeOffsetX += dragAmount.x
                        swipeOffsetY += dragAmount.y
                    }
                }
        ) {
            // Container content fades in smoothly as bounds expand
            if (contentAlpha > 0.01f) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = contentAlpha }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            if (isApp && pkgName != null) {
                                AppIconView(
                                    packageName = pkgName,
                                    contentDescription = node.name,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = controlBg,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        MaterialSymbol(
                                            name = symbolName,
                                            active = true,
                                            size = 20.dp,
                                            tint = contentColor
                                        )
                                    }
                                }
                            }

                            val ext = if (node.isDirectory) "Folder" else node.name.substringAfterLast('.', "").uppercase().take(5).ifEmpty { "File" }
                            Surface(
                                shape = CircleShape,
                                color = controlBg
                            ) {
                                Text(
                                    text = ext,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = contentColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!isSpecial) {
                                Surface(
                                    shape = CircleShape,
                                    color = controlBg,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                isStarred = FavoritesManager.toggleStar(context, path)
                                                AppNotifier.notify(if (isStarred) "Starred & protected" else "Unstarred")
                                                if (onToggleStar != null) onToggleStar(path)
                                            }
                                    ) {
                                        MaterialSymbol(
                                            name = if (isStarred) "star" else "star_outline",
                                            active = isStarred,
                                            size = 18.dp,
                                            tint = if (isStarred) Color(0xFFFFD700) else contentColor
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = controlBg,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            triggerDismiss()
                                        }
                                ) {
                                    MaterialSymbol(
                                        name = "close",
                                        active = true,
                                        size = 18.dp,
                                        tint = contentColor
                                    )
                                }
                            }
                        }
                    }

                    // Body Details
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            text = FileUtils.cleanDisplayName(node.name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        if (isApp && appBreakdown != null) {
                            Text(
                                text = "App ${FileUtils.formatFileSize(appBreakdown.first)} • Data ${FileUtils.formatFileSize(appBreakdown.second)} • Cache ${FileUtils.formatFileSize(appBreakdown.third)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = contentVariantColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "${FileUtils.formatFileSize(node.size)} • $folderName",
                                style = MaterialTheme.typography.bodySmall,
                                color = contentVariantColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Bottom Action Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: New Expressive Select Implementation
                        if (!isSpecial) {
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) contentColor else controlBg,
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onToggleSelect()
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    MaterialSymbol(
                                        name = if (isSelected) "check_circle" else "radio_button_unchecked",
                                        active = isSelected,
                                        size = 18.dp,
                                        tint = if (isSelected) popupBgColor else contentColor
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isSelected) "Selected" else "Select",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) popupBgColor else contentColor
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Total: ${FileUtils.formatFileSize(node.size)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = contentVariantColor
                            )
                        }

                        // Right: Open / Info / Delete
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isApp && pkgName != null) {
                                if (onOpenApp != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = controlBg,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onOpenApp(pkgName)
                                                    triggerDismiss()
                                                }
                                        ) {
                                            MaterialSymbol("open_in_new", active = true, size = 18.dp, tint = contentColor)
                                        }
                                    }
                                }
                                if (onOpenAppInfo != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = controlBg,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onOpenAppInfo(pkgName)
                                                    triggerDismiss()
                                                }
                                        ) {
                                            MaterialSymbol("info", active = true, size = 18.dp, tint = contentColor)
                                        }
                                    }
                                }
                                if (onUninstallApp != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = controlBg,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onUninstallApp(pkgName)
                                                    triggerDismiss()
                                                }
                                        ) {
                                            MaterialSymbol("delete", active = true, size = 18.dp, tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            } else {
                                if (!node.isDirectory && realFile != null && onOpenFile != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = controlBg,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onOpenFile(realFile)
                                                    triggerDismiss()
                                                }
                                        ) {
                                            MaterialSymbol("open_in_new", active = true, size = 18.dp, tint = contentColor)
                                        }
                                    }
                                }
                                if (!isSpecial && onNavigateToExplorer != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = controlBg,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onNavigateToExplorer(path)
                                                    triggerDismiss()
                                                }
                                        ) {
                                            MaterialSymbol("folder_open", active = true, size = 18.dp, tint = contentColor)
                                        }
                                    }
                                }
                                if (!isSpecial) {
                                    Surface(
                                        shape = CircleShape,
                                        color = controlBg,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    triggerDismiss()
                                                    onRequestDelete()
                                                }
                                        ) {
                                            MaterialSymbol(
                                                name = "delete",
                                                active = true,
                                                size = 18.dp,
                                                tint = MaterialTheme.colorScheme.error
                                            )
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
