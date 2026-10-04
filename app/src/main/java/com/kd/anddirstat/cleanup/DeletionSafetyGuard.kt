package com.kd.anddirstat.cleanup

import android.content.Context
import com.kd.anddirstat.model.CompactNode
import com.kd.anddirstat.util.FavoritesManager
import com.kd.anddirstat.util.FileUtils

sealed class SafetyResult {
    object Allowed : SafetyResult()
    data class Blocked(val reason: String) : SafetyResult()
}

object DeletionSafetyGuard {

    private val BLOCKED_EXACT_PATHS = setOf(
        "/",
        "/storage",
        "/storage/emulated",
        "/storage/emulated/0",
        "/storage/emulated/0/",
        "/sdcard",
        "/sdcard/",
        "/data",
        "/system",
        "/vendor",
        "/product",
        "/apex",
        "/boot",
        "/recovery",
        "/data/system",
        "/data/dalvik-cache"
    )

    private val BLOCKED_PREFIXES = listOf(
        "/system/",
        "/vendor/",
        "/product/",
        "/apex/",
        "/boot/",
        "/data/system/",
        "/data/dalvik-cache/"
    )

    fun checkSafety(node: CompactNode?, path: String, context: Context? = null): SafetyResult {
        val cleanPath = path.trim().replace('\\', '/')
        val normalized = cleanPath.removeSuffix("/")
        val name = node?.name?.trim() ?: cleanPath.substringAfterLast('/')

        // 1. Synthetic and virtual containers
        if (name == "[Free Space]" || name == "Free Space" ||
            name == "[System & OS]" || name == "System & OS" ||
            name == "Apps & System Packages" || cleanPath == "Apps & System Packages"
        ) {
            return SafetyResult.Blocked("System virtual items cannot be deleted")
        }

        // 2. Exact blocked root paths
        if (cleanPath.isEmpty() || BLOCKED_EXACT_PATHS.contains(cleanPath) || BLOCKED_EXACT_PATHS.contains(normalized) || BLOCKED_EXACT_PATHS.contains("$normalized/")) {
            return SafetyResult.Blocked("Root storage mounts cannot be deleted")
        }

        // 3. System partitions and directories
        for (prefix in BLOCKED_PREFIXES) {
            if (cleanPath.startsWith(prefix, ignoreCase = true)) {
                return SafetyResult.Blocked("Android system files are protected")
            }
        }

        // 4. Android framework root containers
        if (normalized.equals("/storage/emulated/0/Android", ignoreCase = true) ||
            normalized.equals("/storage/emulated/0/Android/data", ignoreCase = true) ||
            normalized.equals("/storage/emulated/0/Android/obb", ignoreCase = true) ||
            (normalized.equals("Android", ignoreCase = true) && node?.isDirectory == true)
        ) {
            return SafetyResult.Blocked("Core Android framework directories cannot be deleted")
        }

        // 5. Volume roots check
        if (context != null) {
            val volumes = FileUtils.getAvailableStorageVolumes(context)
            for (vol in volumes) {
                val volPath = vol.path.absolutePath.removeSuffix("/")
                if (normalized.equals(volPath, ignoreCase = true)) {
                    return SafetyResult.Blocked("Storage volume roots cannot be deleted")
                }
            }
            // 6. User starred protection
            if (FavoritesManager.isStarred(context, cleanPath)) {
                return SafetyResult.Blocked("Item is starred and protected")
            }
        }

        return SafetyResult.Allowed
    }

    fun isDeletionAllowed(node: CompactNode?, path: String, context: Context? = null): Boolean {
        return checkSafety(node, path, context) is SafetyResult.Allowed
    }
}
