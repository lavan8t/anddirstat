package com.kd.anddirstat.util

import android.content.Context
import android.os.Environment
import java.io.File
import com.kd.anddirstat.cleanup.DeletionSafetyGuard
import com.kd.anddirstat.cleanup.SafetyResult
import java.util.ArrayDeque

enum class WhatsappMediaKind(val label: String) {
    IMAGE("Images"),
    VIDEO("Videos"),
    AUDIO("Audio"),
    DOCUMENT("Documents"),
    OTHER("Other")
}

data class WhatsappCleanupCandidate(
    val file: File,
    val size: Long,
    val lastModified: Long,
    val kind: WhatsappMediaKind,
    val isSentByUser: Boolean
)

/** Finds old WhatsApp media with keep rules for user-sent files and selected file types. */
object WhatsappCleaner {
    private const val ONE_YEAR_MS = 365L * 24 * 60 * 60 * 1000

    private val roots: List<File>
        get() = listOf(
            File(Environment.getExternalStorageDirectory(), "WhatsApp/Media"),
            File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media")
        )

    fun findOldCandidates(
        context: Context,
        minAgeMs: Long = ONE_YEAR_MS,
        keepKinds: Set<WhatsappMediaKind> = emptySet(),
        keepSentByUser: Boolean = true,
        limit: Int = 5000
    ): List<WhatsappCleanupCandidate> {
        val cutoff = System.currentTimeMillis() - minAgeMs
        val found = mutableListOf<WhatsappCleanupCandidate>()
        val stack = ArrayDeque<File>()
        roots.forEach { root ->
            if (root.exists() && root.isDirectory) stack.add(root)
        }

        while (stack.isNotEmpty() && found.size < limit) {
            val file = stack.removeLast()
            if (file.isDirectory) {
                file.listFiles()?.forEach { stack.add(it) }
                continue
            }
            if (file.lastModified() > cutoff) continue
            val kind = kindOf(file)
            val isSent = file.parentFile?.name.equals("Sent", ignoreCase = true) == true
            if (isSent && keepSentByUser) continue
            if (keepKinds.contains(kind)) continue
            if (DeletionSafetyGuard.checkSafety(null, file.absolutePath, context) !is SafetyResult.Allowed) continue
            found.add(WhatsappCleanupCandidate(file, file.length(), file.lastModified(), kind, isSent))
        }
        return found.sortedByDescending { it.size }.take(limit)
    }

    private fun kindOf(file: File): WhatsappMediaKind {
        val name = file.name.lowercase()
        val path = file.absolutePath.lowercase()
        return when {
            path.contains("whatsapp images") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".webp") -> WhatsappMediaKind.IMAGE
            path.contains("whatsapp video") || name.endsWith(".mp4") || name.endsWith(".mkv") -> WhatsappMediaKind.VIDEO
            path.contains("whatsapp audio") || path.contains("voice notes") ||
                name.endsWith(".mp3") || name.endsWith(".ogg") || name.endsWith(".opus") ||
                name.endsWith(".m4a") -> WhatsappMediaKind.AUDIO
            path.contains("documents") || name.endsWith(".pdf") || name.endsWith(".doc") ||
                name.endsWith(".docx") || name.endsWith(".xls") || name.endsWith(".xlsx") ||
                name.endsWith(".ppt") || name.endsWith(".pptx") || name.endsWith(".txt") -> WhatsappMediaKind.DOCUMENT
            else -> WhatsappMediaKind.OTHER
        }
    }
}
