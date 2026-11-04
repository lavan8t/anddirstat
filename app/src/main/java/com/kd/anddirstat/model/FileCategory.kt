package com.kd.anddirstat.model

import androidx.compose.ui.graphics.Color

enum class FileCategory(
    val id: String,
    val displayName: String,
    val statCategory: String,
    val iconName: String,
    val defaultColor: Color,
    val darkColor: Color,
    val lightColor: Color,
    val extensions: Set<String>
) {
    VIDEO(
        id = "videos",
        displayName = "Videos",
        statCategory = "Video",
        iconName = "movie",
        defaultColor = Color(0xFFFB923C),
        darkColor = Color(0xFF60A5FA),
        lightColor = Color(0xFF1D4ED8),
        extensions = setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "ts", "wmv", "m4v", "mpg", "mpeg", "vob", "ogv", "m2ts")
    ),
    IMAGE(
        id = "images",
        displayName = "Images",
        statCategory = "Image",
        iconName = "image",
        defaultColor = Color(0xFF34D399),
        darkColor = Color(0xFF34D399),
        lightColor = Color(0xFF059669),
        extensions = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "raw", "svg", "gif", "bmp", "ico", "dng", "cr2", "nef")
    ),
    AUDIO(
        id = "audio",
        displayName = "Audio",
        statCategory = "Audio",
        iconName = "music_note",
        defaultColor = Color(0xFFC084FC),
        darkColor = Color(0xFFC084FC),
        lightColor = Color(0xFF7C3AED),
        extensions = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma", "mid", "midi", "alac", "amr")
    ),
    DOCUMENT(
        id = "docs",
        displayName = "Documents",
        statCategory = "Document",
        iconName = "description",
        defaultColor = Color(0xFF38BDF8),
        darkColor = Color(0xFF38BDF8),
        lightColor = Color(0xFF0284C7),
        extensions = setOf("pdf", "doc", "docx", "txt", "xlsx", "xls", "ppt", "pptx", "csv", "epub", "mobi", "log", "rtf", "html", "htm", "json", "xml", "md", "yaml", "yml", "conf", "ini", "rc")
    ),
    APP(
        id = "apps",
        displayName = "Apps & Packages",
        statCategory = "App Package",
        iconName = "apps",
        defaultColor = Color(0xFF3B82F6),
        darkColor = Color(0xFF4ADE80),
        lightColor = Color(0xFF16A34A),
        extensions = setOf("apk", "xapk", "apks", "apkm", "aab", "obb")
    ),
    ARCHIVE(
        id = "archives",
        displayName = "Archives & Disk Images",
        statCategory = "Archive",
        iconName = "archive",
        defaultColor = Color(0xFFF59E0B),
        darkColor = Color(0xFFFBBF24),
        lightColor = Color(0xFFD97706),
        extensions = setOf("zip", "rar", "7z", "tar", "gz", "iso", "bin", "img", "dmg", "xz", "bz2", "tgz")
    ),
    TRASHED(
        id = "bin",
        displayName = "Recycle Bin",
        statCategory = "Recycle Bin",
        iconName = "delete",
        defaultColor = Color(0xFFF43F5E),
        darkColor = Color(0xFFFF3366),
        lightColor = Color(0xFFE11D48),
        extensions = setOf("[trashed]", "trashed", "recycle bin", "[recycle bin]")
    ),
    OTHER(
        id = "others",
        displayName = "Other Files",
        statCategory = "Other",
        iconName = "folder",
        defaultColor = Color(0xFF94A3B8),
        darkColor = Color(0xFF94A3B8),
        lightColor = Color(0xFF64748B),
        extensions = emptySet()
    );

    companion object {
        val CODE_EXTENSIONS: Set<String> = setOf("html", "xml", "json", "js", "css", "ts", "kt", "java", "c", "cpp", "py", "sh")
        val BINARY_EXTENSIONS: Set<String> = setOf("so", "dex", "jar", "class", "exe", "dll")

        private val extensionMap: Map<String, FileCategory> = buildMap {
            for (category in FileCategory.entries) {
                for (ext in category.extensions) {
                    put(ext, category)
                }
            }
        }

        fun fromExtension(rawExt: String): FileCategory {
            val clean = rawExt.lowercase().trim().removePrefix(".")
            return extensionMap[clean] ?: OTHER
        }
    }
}
