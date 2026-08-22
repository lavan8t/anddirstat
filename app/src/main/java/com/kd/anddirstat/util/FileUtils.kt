package com.kd.anddirstat.util

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.kd.anddirstat.model.CompactNode
import java.io.File
import java.util.Locale

object FileUtils {

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format(Locale.US, "%.2f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    fun resolveActualFile(path: String): File? {
        val direct = File(path)
        if (direct.exists()) return direct

        val externalRoot = Environment.getExternalStorageDirectory().absolutePath
        val relative = when {
            path.startsWith("Device Storage/Files/") -> path.removePrefix("Device Storage/Files/")
            path.startsWith("Device Storage/") -> path.removePrefix("Device Storage/")
            path.startsWith("Files/") -> path.removePrefix("Files/")
            path == "Files" || path == "Device Storage" -> ""
            path.startsWith("/storage/emulated/0/") -> path.removePrefix("/storage/emulated/0/")
            path.startsWith(externalRoot) -> path.removePrefix(externalRoot).removePrefix("/")
            else -> path
        }
        val f = File(externalRoot, relative)
        if (f.exists()) return f
        return null
    }

    fun extractPackageName(node: CompactNode): String? {
        val name = node.name
        if (name.startsWith("App Code (") && name.endsWith(")")) {
            return name.substringAfter("App Code (").substringBefore(".apk)").removeSuffix(")")
        }
        if (name.startsWith("APK (") && name.endsWith(")")) {
            return name.substringAfter("APK (").substringBefore(".apk)").removeSuffix(")")
        }
        val appCodeChild = node.children?.firstOrNull { it.name.startsWith("App Code (") || it.name.startsWith("APK (") }
        if (appCodeChild != null) {
            val childName = appCodeChild.name
            return if (childName.startsWith("App Code (")) {
                childName.substringAfter("App Code (").substringBefore(".apk)").removeSuffix(")")
            } else {
                childName.substringAfter("APK (").substringBefore(".apk)").removeSuffix(")")
            }
        }
        return null
    }

    fun uninstallApp(context: Context, packageName: String) {
        val uri = Uri.parse("package:$packageName")
        val intent = Intent(Intent.ACTION_DELETE, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                val fallback = Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri).apply {
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (_: Exception) {
                Toast.makeText(context, "Cannot launch uninstaller for $packageName", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun uninstallApps(context: Context, packageNames: List<String>) {
        packageNames.distinct().forEach { pkg ->
            uninstallApp(context, pkg)
        }
    }

    fun openFile(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val ext = file.extension.lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No application found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    @Suppress("DEPRECATION")
    fun checkUsageAccessPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getNodeIcon(node: CompactNode, isAppNode: Boolean = false): ImageVector {
        val name = node.name.lowercase()
        return when {
            name == "[free space]" -> Icons.Outlined.Storage
            name == "[system & os]" -> Icons.Outlined.Storage
            name == "[recycle bin]" || name == "recycle bin" -> Icons.Outlined.Delete
            name == "apps & system packages" || isAppNode -> Icons.Outlined.Apps
            node.isDirectory -> if (node.children?.isNotEmpty() == true) Icons.Outlined.FolderOpen else Icons.Outlined.Folder
            name.endsWith(".apk") || name.endsWith(".apks") || name.endsWith(".xapk") || name.endsWith(".apkm") || name.endsWith(".obb") || name.endsWith(".aab") -> Icons.Outlined.Android
            name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") || name.endsWith(".tar") || name.endsWith(".gz") -> Icons.Outlined.Archive
            name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".heic") || name.endsWith(".gif") || name.endsWith(".svg") -> Icons.Outlined.Image
            name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".mov") || name.endsWith(".webm") || name.endsWith(".3gp") -> Icons.Outlined.Movie
            name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".ogg") || name.endsWith(".opus") -> Icons.Outlined.MusicNote
            name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx") || name.endsWith(".txt") || name.endsWith(".xlsx") || name.endsWith(".pptx") -> Icons.Outlined.Description
            else -> Icons.AutoMirrored.Outlined.InsertDriveFile
        }
    }

    fun getExtensionIcon(extension: String): ImageVector {
        val ext = extension.lowercase().removePrefix(".")
        return when (ext) {
            "trashed", "recycle bin", "[recycle bin]" -> Icons.Outlined.Delete
            "apk", "apks", "xapk", "apkm", "obb", "aab" -> Icons.Outlined.Android
            "mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "wmv", "m4v" -> Icons.Outlined.Movie
            "mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma", "mid" -> Icons.Outlined.MusicNote
            "jpg", "jpeg", "png", "webp", "heic", "raw", "svg", "gif", "bmp", "ico" -> Icons.Outlined.Image
            "pdf", "doc", "docx", "txt", "xlsx", "xls", "ppt", "pptx", "csv", "epub" -> Icons.Outlined.Description
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "tgz" -> Icons.Outlined.Archive
            else -> Icons.AutoMirrored.Outlined.InsertDriveFile
        }
    }

    fun getNodeIconColor(node: CompactNode, isDark: Boolean): Color {
        val name = node.name
        if (name == "[Free Space]") return if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
        if (name == "[System & OS]") return if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
        if (name == "[Recycle Bin]" || name == "Recycle Bin") return if (isDark) Color(0xFFFF2A6D) else Color(0xFFE11D48)
        if (name == "Cache" || name == "App Cache") return if (isDark) Color(0xFFFF9800) else Color(0xFFE65100)
        if (name == "Data" || name == "App Data") return if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
        if (node.isDirectory) return if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)

        val ext = if (name.startsWith("App Code") || name.startsWith("APK (") || name.endsWith(".apk", ignoreCase = true) || name.endsWith(".obb", ignoreCase = true)) {
            "apk"
        } else {
            val dotIdx = name.lastIndexOf('.')
            if (dotIdx >= 0 && dotIdx < name.length - 1) name.substring(dotIdx + 1).lowercase() else ""
        }
        return getFileTypeIconColor(ext, isDark)
    }

    fun getFileTypeIconColor(extension: String, isDark: Boolean): Color {
        val ext = extension.lowercase().removePrefix(".")
        return when (ext) {
            "trashed", "recycle bin", "[recycle bin]" ->
                if (isDark) Color(0xFFFF2A6D) else Color(0xFFE11D48)
            "mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "wmv", "m4v" ->
                if (isDark) Color(0xFF3B82F6) else Color(0xFF1D4ED8)
            "mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma", "mid" ->
                if (isDark) Color(0xFFC084FC) else Color(0xFF9333EA)
            "jpg", "jpeg", "png", "webp", "heic", "raw", "svg", "gif", "bmp", "ico" ->
                if (isDark) Color(0xFFFB923C) else Color(0xFFEA580C)
            "apk", "apks", "xapk", "apkm", "obb", "aab" ->
                if (isDark) Color(0xFF34D399) else Color(0xFF059669)
            "pdf", "doc", "docx", "txt", "xlsx", "xls", "ppt", "pptx", "csv", "epub" ->
                if (isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488)
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "tgz" ->
                if (isDark) Color(0xFF22D3EE) else Color(0xFF0891B2)
            "so", "bin", "dex", "jar", "class", "exe", "dll" ->
                if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
            "html", "xml", "json", "js", "css", "ts", "kt", "java", "c", "cpp", "py" ->
                if (isDark) Color(0xFFFACC15) else Color(0xFFCA8A04)
            else -> {
                if (ext.isEmpty()) {
                    if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                } else {
                    val hash = Math.abs(ext.hashCode())
                    val hue = (hash * 137.507764f) % 360f
                    Color.hsl(hue = hue, saturation = 0.85f, lightness = if (isDark) 0.65f else 0.40f)
                }
            }
        }
    }
}
