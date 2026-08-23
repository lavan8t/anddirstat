package com.kd.anddirstat.util

import android.Manifest
import android.app.AppOpsManager
import android.content.ContentUris
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
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.kd.anddirstat.model.CompactNode
import java.io.File
import java.util.Locale
import kotlin.math.abs

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

        if (sm != null && context != null) {
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
                    } else if (isPrimary) {
                        Environment.getExternalStorageDirectory()
                    } else null

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

    fun formatFileSize(bytes: Long, context: Context? = null): String {
        if (context != null) return android.text.format.Formatter.formatShortFileSize(context, bytes)
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
        var size = bytes.toDouble()
        var unitIdx = 0
        while (size >= 1024.0 && unitIdx < units.size - 1) {
            size /= 1024.0
            unitIdx++
        }
        return if (unitIdx == 0) "$bytes B" else String.format(Locale.US, "%.2f %s", size, units[unitIdx])
    }

    fun resolveActualFile(path: String, context: Context? = null): File? {
        if (path.isBlank()) return null
        val direct = File(path)
        if (direct.exists()) return direct

        val externalRoot = Environment.getExternalStorageDirectory().absolutePath
        val prefixesToStrip = listOf(
            "[Recycle Bin]/", "Recycle Bin/",
            "Device Storage/Files/", "Device Storage/",
            "Internal Storage/Files/", "Internal Storage/",
            "Files/",
            "/storage/emulated/0/",
            externalRoot
        )

        var relative = path
        for (p in prefixesToStrip) {
            if (relative.startsWith(p, ignoreCase = true)) {
                relative = relative.substring(p.length).removePrefix("/")
            }
        }

        val f = File(externalRoot, relative)
        if (f.exists()) return f

        val standardDirs = listOf(
            Environment.getExternalStorageDirectory(),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Screenshots"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        )

        for (dir in standardDirs) {
            val candidate = File(dir, relative)
            if (candidate.exists()) return candidate
            val candidateByName = File(dir, File(path).name)
            if (candidateByName.exists()) return candidateByName
        }

        val volumes = getAvailableStorageVolumes(context)
        for (vol in volumes) {
            val volFile = File(vol.path, relative)
            if (volFile.exists()) return volFile

            val cleanVolName = vol.name.trim()
            if (path.startsWith(cleanVolName, ignoreCase = true)) {
                val sub = path.substring(cleanVolName.length).removePrefix("/")
                val f2 = File(vol.path, sub)
                if (f2.exists()) return f2
            }
            val volCandidateByName = File(vol.path, File(path).name)
            if (volCandidateByName.exists()) return volCandidateByName
        }

        if (context != null) {
            try {
                val fileName = File(path).name
                val proj = arrayOf(MediaStore.MediaColumns.DATA)
                context.contentResolver.query(
                    MediaStore.Files.getContentUri("external"),
                    proj,
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=? OR ${MediaStore.MediaColumns.DATA} LIKE ?",
                    arrayOf(fileName, "%$relative"),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val realPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                        val mf = File(realPath)
                        if (mf.exists()) return mf
                    }
                }
            } catch (_: Exception) {}
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

    fun restoreTrashedFile(file: File, context: Context? = null): Boolean {
        if (!file.exists()) return false
        val parent = file.parentFile ?: return false
        var cleanName = file.name.removePrefix(".trashed-")
        if (cleanName.contains("-") && cleanName.substringBefore("-").all { it.isDigit() }) {
            cleanName = cleanName.substringAfter("-")
        }
        var target = File(parent, cleanName)
        if (target.exists()) {
            target = File(parent, "restored_$cleanName")
        }
        val success = file.renameTo(target)
        if (success && context != null) {
            try {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(target.absolutePath, file.absolutePath),
                    null,
                    null
                )
            } catch (_: Exception) {}
        }
        return success
    }

    fun getFolderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (!dir.isDirectory) return dir.length()
        var size = 0L
        val children = dir.listFiles() ?: return 0L
        for (c in children) {
            size += if (c.isDirectory) getFolderSize(c) else c.length()
        }
        return size
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
                AppNotifier.notify("Cannot launch uninstaller for $cleanPkg")
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

    fun getMimeType(file: File): String {
        val ext = file.extension.lowercase()
        val fromMap = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (!fromMap.isNullOrEmpty()) return fromMap

        return when (ext) {
            "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "svg", "ico", "dng", "raw" -> "image/*"
            "mp4", "mkv", "avi", "mov", "webm", "3gp", "ts", "wmv", "m4v", "flv", "vob", "ogv", "m2ts" -> "video/*"
            "mp3", "flac", "wav", "m4a", "ogg", "opus", "aac", "wma", "mid", "midi", "amr" -> "audio/*"
            "pdf" -> "application/pdf"
            "apk", "apks", "xapk", "apkm" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "txt", "log", "conf", "ini", "rc" -> "text/plain"
            "html", "htm" -> "text/html"
            "json" -> "application/json"
            "xml" -> "text/xml"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            else -> "*/*"
        }
    }

    fun openFile(context: Context, file: File) {
        val actual = if (file.exists()) file else resolveActualFile(file.path) ?: file
        if (!actual.exists()) {
            AppNotifier.notify("File not found: ${actual.name}")
            return
        }

        val mime = getMimeType(actual)
        var uri: Uri? = null

        // 1. Try FileProvider
        try {
            uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                actual
            )
        } catch (_: Exception) {}

        // 2. Try MediaStore ContentResolver if FileProvider failed
        if (uri == null && (mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/"))) {
            try {
                val baseUri = when {
                    mime.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    mime.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val proj = arrayOf(MediaStore.MediaColumns._ID)
                context.contentResolver.query(
                    baseUri,
                    proj,
                    "${MediaStore.MediaColumns.DATA}=?",
                    arrayOf(actual.absolutePath),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        uri = ContentUris.withAppendedId(baseUri, id)
                    }
                }
            } catch (_: Exception) {}
        }

        if (uri == null) {
            uri = Uri.fromFile(actual)
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                AppNotifier.notify("No application found to open ${actual.name}")
            }
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
                    val hash = abs(ext.hashCode())
                    val hue = (hash * 137.50777f) % 360f
                    Color.hsl(hue = hue, saturation = 0.90f, lightness = if (isDark) 0.70f else 0.42f)
                }
            }
        }
    }
}
