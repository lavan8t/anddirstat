package com.kd.anddirstat.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class WhatsappDayPoint(
    val dateKey: String,
    val label: String,
    val totalBytes: Long,
    val addedBytes: Long,
    val addedFiles: Int
)

/** Stores daily WhatsApp media totals and the bytes/files added that day. */
object WhatsappTrendManager {
    private const val PREFS_NAME = "anddirstat_whatsapp_trends"
    private const val KEY_DAYS = "days"
    private const val MAX_DAYS = 14

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayFormat = SimpleDateFormat("EEE", Locale.US)

    fun recordSnapshot(context: Context, totalBytes: Long, addedBytes: Long, addedFiles: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayKey = dateFormat.format(Calendar.getInstance().time)
        val array = try {
            JSONArray(prefs.getString(KEY_DAYS, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
        val list = mutableListOf<JSONObject>()
        var replaced = false
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.optString("date") == todayKey) {
                obj.put("total", totalBytes)
                obj.put("added", addedBytes)
                obj.put("files", addedFiles)
                replaced = true
            }
            list.add(obj)
        }
        if (!replaced) {
            list.add(JSONObject().apply {
                put("date", todayKey)
                put("total", totalBytes)
                put("added", addedBytes)
                put("files", addedFiles)
            })
        }
        val trimmed = if (list.size > MAX_DAYS) list.takeLast(MAX_DAYS) else list
        val result = JSONArray()
        trimmed.forEach { result.put(it) }
        prefs.edit().putString(KEY_DAYS, result.toString()).apply()
    }

    fun getHistory(context: Context): List<WhatsappDayPoint> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = try {
            JSONArray(prefs.getString(KEY_DAYS, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
        val points = mutableListOf<WhatsappDayPoint>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val date = obj.optString("date")
            if (date.isEmpty()) continue
            val cal = Calendar.getInstance().apply { time = dateFormat.parse(date) ?: continue }
            val todayKey = dateFormat.format(Calendar.getInstance().time)
            points.add(
                WhatsappDayPoint(
                    dateKey = date,
                    label = if (date == todayKey) "Today" else dayFormat.format(cal.time),
                    totalBytes = obj.optLong("total", 0L),
                    addedBytes = obj.optLong("added", 0L),
                    addedFiles = obj.optInt("files", 0)
                )
            )
        }
        return points
    }
}
