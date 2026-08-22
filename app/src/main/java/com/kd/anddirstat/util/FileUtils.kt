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
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Storage
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
        val externalRoot = Environment.getExternalStorageDirectory().absolutePath
        val relative = when {
            path.startsWith("Files/") -> path.removePrefix("Files/")
            path == "Files" -> ""
            else -> null
        } ?: return null
        return File(externalRoot, relative)
    }

    fun openFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val ext = file.extension.lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun extractPackageName(node: CompactNode): String? {
        val codeChild = node.children?.firstOrNull { it.name.startsWith("App Code (") }
        if (codeChild != null) {
            return codeChild.name.substringAfter("App Code (").substringBefore(".apk)")
        }
        if (node.name.startsWith("App Code (") && node.name.endsWith(".apk)")) {
            return node.name.substringAfter("App Code (").substringBefore(".apk)")
        }
        return null
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
            name == "apps & system packages" || isAppNode -> Icons.Outlined.Apps
            node.isDirectory -> if (node.children?.isNotEmpty() == true) Icons.Outlined.FolderOpen else Icons.Outlined.Folder
            name.endsWith(".apk") || name.endsWith(".apks") || name.endsWith(".xapk") -> Icons.Outlined.Apps
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
            "mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "wmv", "m4v" -> Icons.Outlined.Movie
            "mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma", "mid" -> Icons.Outlined.MusicNote
            "jpg", "jpeg", "png", "webp", "heic", "raw", "svg", "gif", "bmp", "ico" -> Icons.Outlined.Image
            "apk", "apks", "xapk", "apkm", "obb", "aab" -> Icons.Outlined.Apps
            "pdf", "doc", "docx", "txt", "xlsx", "xls", "ppt", "pptx", "csv", "epub" -> Icons.Outlined.Description
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "tgz" -> Icons.Outlined.Archive
            else -> Icons.AutoMirrored.Outlined.InsertDriveFile
        }
    }
}
