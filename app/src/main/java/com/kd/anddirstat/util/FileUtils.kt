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
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
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

data class StorageVolumeInfo(
    val id: String,
    val name: String,
    val path: File,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val isUsb: Boolean,
    val totalBytes: Long,
    val freeBytes: Long
)

object FileUtils {

    fun getAvailableStorageVolumes(context: Context? = null): List<StorageVolumeInfo> {
        val list = mutableListOf<StorageVolumeInfo>()
        val sm = context?.getSystemService(Context.STORAGE_SERVICE) as? StorageManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && sm != null && context != null) {
            val volumes = sm.storageVolumes
            for (vol in volumes) {
                val state = vol.state
                if (state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY) {
                    val isPrimary = vol.isPrimary
                    val isRemovable = vol.isRemovable
                    val desc = vol.getDescription(context)
                    val isUsb = desc.contains("USB", ignoreCase = true) || vol.mediaStoreVolumeName?.contains("usb", ignoreCase = true) == true

                    val dir: File? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        vol.directory
                    } else {
                        try {
                            val getPathMethod = vol.javaClass.getMethod("getPathFile")
                            getPathMethod.invoke(vol) as? File
                        } catch (_: Exception) {
                            if (isPrimary) Environment.getExternalStorageDirectory() else null
                        }
                    }

                    if (dir != null && dir.exists()) {
                        var total = 0L
                        var free = 0L
                        try {
                            val stat = StatFs(dir.absolutePath)
                            total = stat.totalBytes
                            free = stat.availableBytes
                        } catch (_: Exception) {}

                        list.add(
                            StorageVolumeInfo(
                                id = dir.absolutePath,
                                name = if (isPrimary) "Internal Storage" else if (isUsb) "USB Drive ($desc)" else desc,
                                path = dir,
                                isPrimary = isPrimary,
                                isRemovable = isRemovable,
                                isUsb = isUsb,
                                totalBytes = total,
                                freeBytes = free
                            )
                        )
                    }
                }
            }
        }

        if (list.isEmpty()) {
            val ext = Environment.getExternalStorageDirectory()
            var total = 0L
            var free = 0L
            try {
                val stat = StatFs(ext.absolutePath)
                total = stat.totalBytes
                free = stat.availableBytes
            } catch (_: Exception) {}

            list.add(
                StorageVolumeInfo(
                    id = ext.absolutePath,
                    name = "Internal Storage",
                    path = ext,
                    isPrimary = true,
                    isRemovable = false,
                    isUsb = false,
                    totalBytes = total,
                    freeBytes = free
                )
            )
        }

        return list
    }

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
            path.startsWith("[Recycle Bin]/") -> path.removePrefix("[Recycle Bin]/")
            path.startsWith("Device Storage/Files/") -> path.removePrefix("Device Storage/Files/")
            path.startsWith("Device Storage/") -> path.removePrefix("Device Storage/")
            path.startsWith("Files/") -> path.removePrefix("Files/")
            path == "Files" || path == "Device Storage" || path == "[Recycle Bin]" -> ""
            path.startsWith("/storage/emulated/0/") -> path.removePrefix("/storage/emulated/0/")
            path.startsWith(externalRoot) -> path.removePrefix(externalRoot).removePrefix("/")
            else -> path
        }
        val f = File(externalRoot, relative)
        if (f.exists()) return f

        if (relative.startsWith(".trashed")) {
            val candidateDirs = listOf(
                Environment.getExternalStorageDirectory(),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            )
            for (dir in candidateDirs) {
                val candidate = File(dir, relative)
                if (candidate.exists()) return candidate
            }
        }

        for (vol in getAvailableStorageVolumes(null)) {
            val volFile = File(vol.path, relative)
            if (volFile.exists()) return volFile

            if (path.startsWith("${vol.name}/")) {
                val sub = path.removePrefix("${vol.name}/")
                val f2 = File(vol.path, sub)
                if (f2.exists()) return f2
            }
        }
        return null
    }

    fun deleteOrTrashFile(file: File, context: Context? = null): Boolean {
        if (!file.exists()) return false
        val parent = file.parentFile ?: return file.deleteRecursively()
        val name = file.name

        // If already in Recycle Bin or starts with .trashed, permanently delete
        if (name.startsWith(".trashed") || parent.name.startsWith(".trashed") || parent.name.equals("[Recycle Bin]", ignoreCase = true)) {
            val deleted = file.deleteRecursively()
            if (context != null) {
                try {
                    val uri = MediaStore.Files.getContentUri("external")
                    context.contentResolver.delete(uri, "${MediaStore.MediaColumns.DATA}=?", arrayOf(file.absolutePath))
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                } catch (_: Exception) {}
            }
            return deleted
        }

        // Rename to .trashed-<original_name>
        val target = File(parent, ".trashed-${file.name}")
        if (file.renameTo(target)) {
            if (context != null) {
                try {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath, target.absolutePath),
                        null,
                        null
                    )
                } catch (_: Exception) {}
            }
            return true
        }

        // Fallback with timestamp if file with same name exists
        val timestampTarget = File(parent, ".trashed-${System.currentTimeMillis()}-${file.name}")
        if (file.renameTo(timestampTarget)) {
            if (context != null) {
                try {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath, timestampTarget.absolutePath),
                        null,
                        null
                    )
                } catch (_: Exception) {}
            }
            return true
        }

        val deleted = file.deleteRecursively()
        if (context != null) {
            try {
                val uri = MediaStore.Files.getContentUri("external")
                context.contentResolver.delete(uri, "${MediaStore.MediaColumns.DATA}=?", arrayOf(file.absolutePath))
                android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
            } catch (_: Exception) {}
        }
        return deleted
    }

    object AppPackageRegistry {
        private val labelToPkg = java.util.concurrent.ConcurrentHashMap<String, String>()
        private val pkgToLabel = java.util.concurrent.ConcurrentHashMap<String, String>()

        fun register(label: String, pkg: String) {
            val cleanLabel = label.removeSuffix(" (System)").trim()
            labelToPkg[cleanLabel.lowercase()] = pkg
            labelToPkg[label.lowercase()] = pkg
            pkgToLabel[pkg] = cleanLabel
        }

        fun getPackageName(label: String?): String? {
            if (label.isNullOrBlank()) return null
            val clean = label.removeSuffix(" (System)").trim().lowercase()
            return labelToPkg[clean] ?: labelToPkg[label.trim().lowercase()]
        }
    }

    fun extractPackageName(node: CompactNode, path: String? = null, context: Context? = null): String? {
        val name = node.name

        // 1. Direct App Code child name or node name
        if (name.startsWith("App Code (")) {
            val pkg = name.substringAfter("App Code (").substringBefore(".apk").trim()
            if (pkg.isNotEmpty() && pkg.contains(".")) return pkg
        }

        // 2. If node has an "App Code (*.apk)" child, extract package from it
        val codeChild = node.children?.firstOrNull { it.name.startsWith("App Code (") }
        if (codeChild != null) {
            val pkg = codeChild.name.substringAfter("App Code (").substringBefore(".apk").trim()
            if (pkg.isNotEmpty() && pkg.contains(".")) return pkg
        }

        // 3. If path or parent is specifically inside "Apps & System Packages"
        val isAppTree = path != null && (path.contains("Apps & System Packages") || path.startsWith("Apps/"))
        if (isAppTree) {
            AppPackageRegistry.getPackageName(name)?.let { return it }
            val cleanName = name.removeSuffix(" (System)").trim()
            AppPackageRegistry.getPackageName(cleanName)?.let { return it }
        }

        return null
    }

    fun uninstallApp(context: Context, packageName: String) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isEmpty()) return

        var launched = false
        // 1. Try standard ACTION_DELETE uninstaller intent
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$cleanPkg")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            launched = true
        } catch (_: Exception) {}

        // 2. Try ACTION_UNINSTALL_PACKAGE uninstaller intent
        if (!launched) {
            try {
                @Suppress("DEPRECATION")
                val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                    data = Uri.parse("package:$cleanPkg")
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                launched = true
            } catch (_: Exception) {}
        }

        // 3. Fallback to application details settings where user can press Uninstall/Force Stop
        if (!launched) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$cleanPkg")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                launched = true
            } catch (_: Exception) {
                Toast.makeText(context, "Cannot launch uninstaller for $cleanPkg", Toast.LENGTH_SHORT).show()
            }
        }
    }

    object AppUninstallerQueue {
        private val queue = mutableListOf<String>()
        private var isProcessing = false

        fun start(context: Context, packageNames: List<String>) {
            queue.clear()
            queue.addAll(packageNames.distinct())
            isProcessing = true
            popNext(context)
        }

        fun onResume(context: Context) {
            if (isProcessing && queue.isNotEmpty()) {
                popNext(context)
            } else {
                isProcessing = false
            }
        }

        private fun popNext(context: Context) {
            if (queue.isEmpty()) {
                isProcessing = false
                return
            }
            val nextPkg = queue.removeAt(0)
            uninstallApp(context, nextPkg)
        }
    }

    fun uninstallApps(context: Context, packageNames: List<String>) {
        val distinctPkgs = packageNames.distinct()
        if (distinctPkgs.isEmpty()) return
        AppUninstallerQueue.start(context, distinctPkgs)
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

    fun getNodeSymbolName(node: CompactNode, isAppNode: Boolean = false): String {
        val name = node.name.lowercase()
        return when {
            name == "[free space]" -> "storage"
            name == "[system & os]" -> "settings"
            name == "[recycle bin]" || name == "recycle bin" -> "delete"
            name == "apps & system packages" || isAppNode -> "apps"
            node.isDirectory -> if (node.children?.isNotEmpty() == true) "folder_open" else "folder"
            name.endsWith(".apk") || name.endsWith(".apks") || name.endsWith(".xapk") || name.endsWith(".apkm") || name.endsWith(".obb") || name.endsWith(".aab") -> "android"
            name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") || name.endsWith(".tar") || name.endsWith(".gz") || name.endsWith(".bz2") || name.endsWith(".xz") || name.endsWith(".iso") || name.endsWith(".tgz") -> "folder_zip"
            name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".heic") || name.endsWith(".gif") || name.endsWith(".svg") || name.endsWith(".bmp") || name.endsWith(".ico") || name.endsWith(".dng") || name.endsWith(".raw") -> "image"
            name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".mov") || name.endsWith(".webm") || name.endsWith(".3gp") || name.endsWith(".ts") || name.endsWith(".wmv") || name.endsWith(".m4v") || name.endsWith(".flv") -> "movie"
            name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".ogg") || name.endsWith(".opus") || name.endsWith(".aac") || name.endsWith(".wma") || name.endsWith(".mid") -> "music_note"
            name.endsWith(".pdf") || name.endsWith(".doc") || name.endsWith(".docx") || name.endsWith(".txt") || name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".ppt") || name.endsWith(".pptx") || name.endsWith(".csv") || name.endsWith(".epub") -> "description"
            name.endsWith(".html") || name.endsWith(".xml") || name.endsWith(".json") || name.endsWith(".js") || name.endsWith(".css") || name.endsWith(".ts") || name.endsWith(".kt") || name.endsWith(".java") || name.endsWith(".py") || name.endsWith(".sh") -> "code"
            else -> "draft"
        }
    }

    fun getExtensionSymbolName(extension: String): String {
        val ext = extension.lowercase().removePrefix(".")
        return when (ext) {
            "trashed", "recycle bin", "[recycle bin]" -> "delete"
            "apk", "apks", "xapk", "apkm", "obb", "aab" -> "android"
            "mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "wmv", "m4v" -> "movie"
            "mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma", "mid" -> "music_note"
            "jpg", "jpeg", "png", "webp", "heic", "raw", "svg", "gif", "bmp", "ico", "dng" -> "image"
            "pdf", "doc", "docx", "txt", "xlsx", "xls", "ppt", "pptx", "csv", "epub" -> "description"
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "tgz", "dmg", "bin" -> "folder_zip"
            "html", "xml", "json", "js", "css", "ts", "kt", "java", "c", "cpp", "py", "sh" -> "code"
            else -> "draft"
        }
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
        if (name == "[Recycle Bin]" || name == "Recycle Bin") return if (isDark) Color(0xFFFF3366) else Color(0xFFE11D48)
        if (name == "Cache" || name == "App Cache") return if (isDark) Color(0xFFFB923C) else Color(0xFFEA580C)
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
                if (isDark) Color(0xFFFF3366) else Color(0xFFE11D48)
            "mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "wmv", "m4v" ->
                if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
            "mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma", "mid" ->
                if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED)
            "jpg", "jpeg", "png", "webp", "heic", "raw", "svg", "gif", "bmp", "ico", "dng" ->
                if (isDark) Color(0xFF34D399) else Color(0xFF059669)
            "apk", "apks", "xapk", "apkm", "obb", "aab" ->
                if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)
            "pdf", "doc", "docx", "txt", "xlsx", "xls", "ppt", "pptx", "csv", "epub" ->
                if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "bin", "dmg", "tgz" ->
                if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
            "so", "dex", "jar", "class", "exe", "dll" ->
                if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
            "html", "xml", "json", "js", "css", "ts", "kt", "java", "c", "cpp", "py", "sh" ->
                if (isDark) Color(0xFFFCD34D) else Color(0xFFB45309)
            else -> {
                if (ext.isEmpty()) {
                    if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
                } else {
                    val hash = Math.abs(ext.hashCode())
                    val hue = (hash * 137.507764f) % 360f
                    Color.hsl(hue = hue, saturation = 0.90f, lightness = if (isDark) 0.70f else 0.42f)
                }
            }
        }
    }
}
