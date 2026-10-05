package com.kd.anddirstat.treemap

import androidx.compose.ui.graphics.Color
import com.kd.anddirstat.model.CompactNode
import kotlin.math.abs

/** extension -> (dark, light). Built once. */
private val ExtensionColors: Map<String, Pair<Color, Color>> = mapOf(
    "mp4" to (Color(0xFF1565C0) to Color(0xFF1976D2)),
    "mkv" to (Color(0xFF0D47A1) to Color(0xFF1565C0)),
    "avi" to (Color(0xFF1976D2) to Color(0xFF2196F3)),
    "mov" to (Color(0xFF0277BD) to Color(0xFF039BE5)),
    "webm" to (Color(0xFF283593) to Color(0xFF3F51B5)),
    "flv" to (Color(0xFF303F9F) to Color(0xFF5C6BC0)),
    "3gp" to (Color(0xFF1A237E) to Color(0xFF3949AB)),
    "ts" to (Color(0xFF01579B) to Color(0xFF0288D1)),
    "wmv" to (Color(0xFF006064) to Color(0xFF00838F)),
    "m4v" to (Color(0xFF311B92) to Color(0xFF4527A0)),
    "mpg" to (Color(0xFF1E88E5) to Color(0xFF42A5F5)),
    "mpeg" to (Color(0xFF1E88E5) to Color(0xFF42A5F5)),
    "vob" to (Color(0xFF1E88E5) to Color(0xFF42A5F5)),
    "mp3" to (Color(0xFF6A1B9A) to Color(0xFF8E24AA)),
    "flac" to (Color(0xFF4A148C) to Color(0xFF6A1B9A)),
    "wav" to (Color(0xFF7B1FA2) to Color(0xFF9C27B0)),
    "m4a" to (Color(0xFF8E24AA) to Color(0xFFAB47BC)),
    "ogg" to (Color(0xFF512DA8) to Color(0xFF673AB7)),
    "aac" to (Color(0xFF5E35B1) to Color(0xFF7E57C2)),
    "opus" to (Color(0xFF4527A0) to Color(0xFF5E35B1)),
    "wma" to (Color(0xFF7E57C2) to Color(0xFF9575CD)),
    "mid" to (Color(0xFFBA68C8) to Color(0xFFCE93D8)),
    "midi" to (Color(0xFFBA68C8) to Color(0xFFCE93D8)),
    "alac" to (Color(0xFF6200EA) to Color(0xFF7C4DFF)),
    "amr" to (Color(0xFF6200EA) to Color(0xFF7C4DFF)),
    "jpg" to (Color(0xFFE65100) to Color(0xFFF57C00)),
    "jpeg" to (Color(0xFFE65100) to Color(0xFFF57C00)),
    "png" to (Color(0xFFD84315) to Color(0xFFFF5722)),
    "webp" to (Color(0xFFF57C00) to Color(0xFFFF9800)),
    "heic" to (Color(0xFFBF360C) to Color(0xFFE64A19)),
    "heif" to (Color(0xFFBF360C) to Color(0xFFE64A19)),
    "gif" to (Color(0xFFFF6F00) to Color(0xFFFFA000)),
    "svg" to (Color(0xFFE64A19) to Color(0xFFFF7043)),
    "raw" to (Color(0xFFEF6C00) to Color(0xFFFB8C00)),
    "dng" to (Color(0xFFEF6C00) to Color(0xFFFB8C00)),
    "cr2" to (Color(0xFFEF6C00) to Color(0xFFFB8C00)),
    "nef" to (Color(0xFFEF6C00) to Color(0xFFFB8C00)),
    "bmp" to (Color(0xFFFF8F00) to Color(0xFFFFB300)),
    "ico" to (Color(0xFFFF8F00) to Color(0xFFFFB300)),
    "pdf" to (Color(0xFF1B5E20) to Color(0xFF2E7D32)),
    "doc" to (Color(0xFF2E7D32) to Color(0xFF388E3C)),
    "docx" to (Color(0xFF2E7D32) to Color(0xFF388E3C)),
    "txt" to (Color(0xFF388E3C) to Color(0xFF43A047)),
    "log" to (Color(0xFF388E3C) to Color(0xFF43A047)),
    "rtf" to (Color(0xFF388E3C) to Color(0xFF43A047)),
    "xlsx" to (Color(0xFF004D40) to Color(0xFF00695C)),
    "xls" to (Color(0xFF004D40) to Color(0xFF00695C)),
    "pptx" to (Color(0xFF00695C) to Color(0xFF00796B)),
    "ppt" to (Color(0xFF00695C) to Color(0xFF00796B)),
    "csv" to (Color(0xFF33691E) to Color(0xFF558B2F)),
    "epub" to (Color(0xFF558B2F) to Color(0xFF689F38)),
    "mobi" to (Color(0xFF558B2F) to Color(0xFF689F38)),
    "zip" to (Color(0xFF006064) to Color(0xFF00838F)),
    "rar" to (Color(0xFF00838F) to Color(0xFF0097A7)),
    "7z" to (Color(0xFF004D40) to Color(0xFF00695C)),
    "tar" to (Color(0xFF00796B) to Color(0xFF00897B)),
    "gz" to (Color(0xFF0097A7) to Color(0xFF00ACC1)),
    "tgz" to (Color(0xFF0097A7) to Color(0xFF00ACC1)),
    "bz2" to (Color(0xFF00838F) to Color(0xFF00BCD4)),
    "xz" to (Color(0xFF00838F) to Color(0xFF00BCD4)),
    "iso" to (Color(0xFF004D40) to Color(0xFF00796B)),
    "img" to (Color(0xFF004D40) to Color(0xFF00796B)),
    "dmg" to (Color(0xFF004D40) to Color(0xFF00796B)),
    "kt" to (Color(0xFFF57F17) to Color(0xFFFBC02D)),
    "kts" to (Color(0xFFF57F17) to Color(0xFFFBC02D)),
    "java" to (Color(0xFFF9A825) to Color(0xFFFDD835)),
    "py" to (Color(0xFFE65100) to Color(0xFFEF6C00)),
    "js" to (Color(0xFFFF8F00) to Color(0xFFFFA000)),
    "mjs" to (Color(0xFFFF8F00) to Color(0xFFFFA000)),
    "cjs" to (Color(0xFFFF8F00) to Color(0xFFFFA000)),
    "tsx" to (Color(0xFF0277BD) to Color(0xFF039BE5)),
    "html" to (Color(0xFFE64A19) to Color(0xFFFF5722)),
    "htm" to (Color(0xFFE64A19) to Color(0xFFFF5722)),
    "css" to (Color(0xFF0288D1) to Color(0xFF29B6F6)),
    "scss" to (Color(0xFF0288D1) to Color(0xFF29B6F6)),
    "json" to (Color(0xFFFFB300) to Color(0xFFFFCA28)),
    "xml" to (Color(0xFFFF8F00) to Color(0xFFFFB300)),
    "yaml" to (Color(0xFFF57F17) to Color(0xFFFFEE58)),
    "yml" to (Color(0xFFF57F17) to Color(0xFFFFEE58)),
    "c" to (Color(0xFF0D47A1) to Color(0xFF1976D2)),
    "cpp" to (Color(0xFF0D47A1) to Color(0xFF1976D2)),
    "h" to (Color(0xFF0D47A1) to Color(0xFF1976D2)),
    "hpp" to (Color(0xFF0D47A1) to Color(0xFF1976D2)),
    "md" to (Color(0xFF6D4C41) to Color(0xFF8D6E63)),
    "apk" to (Color(0xFFB71C1C) to Color(0xFFD32F2F)),
    "apks" to (Color(0xFF880E4F) to Color(0xFFC2185B)),
    "xapk" to (Color(0xFF880E4F) to Color(0xFFC2185B)),
    "apkm" to (Color(0xFF880E4F) to Color(0xFFC2185B)),
    "aab" to (Color(0xFF880E4F) to Color(0xFFC2185B)),
    "obb" to (Color(0xFFAD1457) to Color(0xFFD81B60)),
    "so" to (Color(0xFF4A148C) to Color(0xFF7B1FA2)),
    "dex" to (Color(0xFF3E2723) to Color(0xFF5D4037)),
    "bin" to (Color(0xFF4E342E) to Color(0xFF6D4C41)),
    "exe" to (Color(0xFF4E342E) to Color(0xFF6D4C41)),
    "dll" to (Color(0xFF4E342E) to Color(0xFF6D4C41)),
    "jar" to (Color(0xFF5D4037) to Color(0xFF795548)),
    "class" to (Color(0xFF5D4037) to Color(0xFF795548)),
)

fun getNodeColor(node: CompactNode, isDark: Boolean = true): Color {
    val name = node.name
    if (name == "[Free Space]") return if (isDark) Color(0xFF1E293B) else Color(0xFF64748B)
    if (name == "[System & OS]") return if (isDark) Color(0xFF141923) else Color(0xFF475569)
    if (name == "[Temporary System Files]" || name == "Temporary System Files") return if (isDark) Color(0xFF5A3000) else Color(0xFFF59E0B)
    if (name == "[Recycle Bin]" || name.startsWith(".trashed")) return if (isDark) Color(0xFF701830) else Color(0xFFE11D48)
    if (name == "Cache" || name == "App Cache") return if (isDark) Color(0xFF8A3E00) else Color(0xFFF59E0B)
    if (name == "Data" || name == "App Data") return if (isDark) Color(0xFF2E3F59) else Color(0xFF64748B)

    val children = node.children
    if (children != null && children.any { it.name.startsWith("App Code") }) {
        return if (isDark) Color(0xFF123D78) else Color(0xFF1976D2)
    }
    if (node.isDirectory) return if (isDark) Color(0xFF151922) else Color(0xFF2C3240)

    val ext = if (name.startsWith("App Code") || name.startsWith("APK (") || name.endsWith(".apk", ignoreCase = true)) {
        "apk"
    } else {
        val dotIdx = name.lastIndexOf('.')
        if (dotIdx >= 0 && dotIdx < name.length - 1) name.substring(dotIdx + 1).lowercase() else ""
    }

    if (ext.isEmpty() || ext == name.lowercase()) {
        return if (isDark) Color(0xFF37474F) else Color(0xFF78909C)
    }


    val pair = ExtensionColors[ext]
    if (pair != null) return if (isDark) pair.first else pair.second
    val hue = (abs(ext.hashCode()) * 137.50777f) % 360f
    return Color.hsl(hue = hue, saturation = 0.65f, lightness = if (isDark) 0.28f else 0.50f)
}
