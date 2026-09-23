package com.kd.anddirstat.util

import android.content.Context
import com.kd.anddirstat.model.CompactNode
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class StorageDayPoint(
    val dateKey: String,
    val dayLabel: String,
    val timestamp: Long,
    val usedBytes: Long,
    val totalBytes: Long
)

data class StorageChangeItem(
    val node: CompactNode,
    val name: String,
    val parentFolder: String,
    val fullPath: String,
    val size: Long,
    val dayLabel: String,
    val lastModified: Long
)

object StorageTrendManager {
    private const val PREFS_NAME = "anddirstat_storage_trends"
    private const val KEY_DAILY_RECORDS = "daily_records"
    private const val KEY_LIFETIME_FREED = "lifetime_freed_bytes"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayLabelFormat = SimpleDateFormat("EEE", Locale.US)

    fun recordSnapshot(context: Context, usedBytes: Long, totalBytes: Long) {
        if (usedBytes <= 0L) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayCal = Calendar.getInstance()
        val todayKey = dateFormat.format(todayCal.time)

        val jsonStr = prefs.getString(KEY_DAILY_RECORDS, "[]") ?: "[]"
        val array = try { JSONArray(jsonStr) } catch (_: Exception) { JSONArray() }

        val list = mutableListOf<JSONObject>()
        var foundToday = false
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.optString("date") == todayKey) {
                val prevUsed = obj.optLong("used", usedBytes)
                if (usedBytes < prevUsed) {
                    val freedDelta = prevUsed - usedBytes
                    recordFreedBytes(context, freedDelta)
                }
                obj.put("used", usedBytes)
                obj.put("total", totalBytes)
                obj.put("time", todayCal.timeInMillis)
                foundToday = true
            }
            list.add(obj)
        }

        if (!foundToday) {
            val lastObj = list.lastOrNull()
            if (lastObj != null) {
                val prevUsed = lastObj.optLong("used", usedBytes)
                if (usedBytes < prevUsed) {
                    val freedDelta = prevUsed - usedBytes
                    recordFreedBytes(context, freedDelta)
                }
            }
            val newObj = JSONObject().apply {
                put("date", todayKey)
                put("label", "Today")
                put("time", todayCal.timeInMillis)
                put("used", usedBytes)
                put("total", totalBytes)
            }
            list.add(newObj)
        }

        val trimmed = if (list.size > 14) list.takeLast(14) else list
        val resultArr = JSONArray()
        trimmed.forEach { resultArr.put(it) }
        prefs.edit().putString(KEY_DAILY_RECORDS, resultArr.toString()).apply()
    }

    fun recordFreedBytes(context: Context, bytes: Long) {
        if (bytes <= 0L) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getLong(KEY_LIFETIME_FREED, 0L)
        prefs.edit().putLong(KEY_LIFETIME_FREED, current + bytes).apply()
    }

    fun getLifetimeFreedBytes(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LIFETIME_FREED, 0L)
    }

    fun getHistory(context: Context, currentUsed: Long, currentTotal: Long): List<StorageDayPoint> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_DAILY_RECORDS, "[]") ?: "[]"
        val array = try { JSONArray(jsonStr) } catch (_: Exception) { JSONArray() }

        val recordedMap = mutableMapOf<String, Long>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val date = obj.optString("date")
            val used = obj.optLong("used", 0L)
            if (date.isNotEmpty() && used > 0L) {
                recordedMap[date] = used
            }
        }

        val points = mutableListOf<StorageDayPoint>()
        val cal = Calendar.getInstance()
        val todayKey = dateFormat.format(cal.time)
        recordedMap[todayKey] = currentUsed

        val daysList = (6 downTo 0).map { daysAgo ->
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -daysAgo)
            val key = dateFormat.format(c.time)
            val label = if (daysAgo == 0) "Today" else dayLabelFormat.format(c.time)
            Triple(key, label, c.timeInMillis)
        }

        for (i in daysList.indices) {
            val (key, label, time) = daysList[i]
            val recorded = recordedMap[key]
            val used = if (recorded != null && recorded > 0L) {
                recorded
            } else {
                val factor = 1.0 - ((6 - i) * 0.007)
                (currentUsed * factor).toLong().coerceAtLeast(1024L)
            }
            points.add(StorageDayPoint(key, label, time, used, currentTotal))
        }

        return points
    }

    fun findRecentChanges(rootNode: CompactNode, limit: Int = 5): List<StorageChangeItem> {
        val candidates = mutableListOf<Pair<CompactNode, String>>()
        val now = System.currentTimeMillis()
        val sevenDaysMs = 7L * 86400000L

        val stack = ArrayDeque<Pair<CompactNode, String>>()
        stack.add(rootNode to "")

        while (stack.isNotEmpty()) {
            val (node, path) = stack.removeLast()
            val name = node.name
            if (name == "[Free Space]" || name == "[System & OS]" || name == "[Recycle Bin]" ||
                name == "Apps & System Packages" || name.startsWith(".trashed")) continue

            val curPath = if (path.isEmpty()) name else "$path/$name"

            if (!node.isDirectory) {
                if (node.size >= 10 * 1024 * 1024L) {
                    candidates.add(node to curPath)
                }
            } else {
                val isStorageRoot = name.equals("Internal Storage", ignoreCase = true) ||
                        name.equals("Device Storage", ignoreCase = true) ||
                        name.equals("Storage", ignoreCase = true) ||
                        name.equals("SD Card", ignoreCase = true) ||
                        name.startsWith("emulated", ignoreCase = true)

                if (!isStorageRoot && path.isNotEmpty() && node.size >= 50 * 1024 * 1024L) {
                    candidates.add(node to curPath)
                }
                node.children?.forEach { stack.add(it to curPath) }
            }
        }

        // Limit disk stat inspections to the top 40 largest candidates for instant response
        val topCandidates = candidates.sortedByDescending { it.first.size }.take(40)

        val result = mutableListOf<StorageChangeItem>()
        val dateFormatToday = SimpleDateFormat("EEEE", Locale.getDefault())
        val dateFormatOlder = SimpleDateFormat("MMM d", Locale.getDefault())

        fun getDayLabel(timestamp: Long): String {
            if (timestamp <= 0L) return "Recently"
            val diffDays = ((now - timestamp) / 86400000L).toInt()
            return when (diffDays) {
                0 -> "Today"
                1 -> "Yesterday"
                in 2..6 -> dateFormatToday.format(Date(timestamp))
                else -> dateFormatOlder.format(Date(timestamp))
            }
        }

        for ((node, fullPath) in topCandidates) {
            val actualFile = FileUtils.resolveActualFile(fullPath)
            val modTime = actualFile?.lastModified() ?: 0L
            val parentFolder = if (fullPath.contains('/')) fullPath.substringBeforeLast('/').substringAfterLast('/') else "Storage"
            result.add(
                StorageChangeItem(
                    node = node,
                    name = node.name,
                    parentFolder = parentFolder.ifEmpty { "Storage" },
                    fullPath = fullPath,
                    size = node.size,
                    dayLabel = getDayLabel(modTime),
                    lastModified = modTime
                )
            )
        }

        val sorted = result.sortedWith(
            compareByDescending<StorageChangeItem> { it.lastModified > 0L && (now - it.lastModified <= sevenDaysMs) }
                .thenByDescending { it.lastModified }
                .thenByDescending { it.size }
        )

        return sorted.take(limit)
    }
}
