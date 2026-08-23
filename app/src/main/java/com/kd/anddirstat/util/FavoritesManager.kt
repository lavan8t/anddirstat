package com.kd.anddirstat.util

import android.content.Context

object FavoritesManager {
    private const val PREFS_NAME = "anddirstat_favorites"
    private const val KEY_STARRED_PATHS = "starred_paths"
    private const val KEY_RECENT_SEARCHES = "recent_searches"

    fun getStarredPaths(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_STARRED_PATHS, emptySet()) ?: emptySet()
    }

    fun isStarred(context: Context, path: String): Boolean {
        if (path.isBlank()) return false
        val starred = getStarredPaths(context)
        return starred.contains(path) || starred.any { path.startsWith("$it/") || it == path }
    }

    fun isDirectlyStarred(context: Context, path: String): Boolean {
        if (path.isBlank()) return false
        return getStarredPaths(context).contains(path)
    }

    fun toggleStar(context: Context, path: String): Boolean {
        if (path.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_STARRED_PATHS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val newState = if (current.contains(path)) {
            current.remove(path)
            false
        } else {
            current.add(path)
            true
        }
        prefs.edit().putStringSet(KEY_STARRED_PATHS, current).apply()
        return newState
    }

    fun getRecentSearches(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RECENT_SEARCHES, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|||").filter { it.isNotBlank() }
    }

    fun addRecentSearch(context: Context, query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getRecentSearches(context).toMutableList()
        current.remove(clean)
        current.add(0, clean)
        val trimmed = current.take(10)
        prefs.edit().putString(KEY_RECENT_SEARCHES, trimmed.joinToString("|||")).apply()
    }

    fun removeRecentSearch(context: Context, query: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = getRecentSearches(context).toMutableList()
        current.remove(query.trim())
        prefs.edit().putString(KEY_RECENT_SEARCHES, current.joinToString("|||")).apply()
    }

    fun clearRecentSearches(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_RECENT_SEARCHES).apply()
    }
}
