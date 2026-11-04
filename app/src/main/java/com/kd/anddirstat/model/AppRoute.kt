package com.kd.anddirstat.model

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object Tree : AppRoute

    @Serializable
    data object Explorer : AppRoute

    @Serializable
    data object Types : AppRoute

    @Serializable
    data object Discover : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object CleanerDuplicates : AppRoute

    @Serializable
    data object CleanerEmptyFolders : AppRoute

    @Serializable
    data object CleanerScreenshots : AppRoute

    @Serializable
    data object CleanerRecycleBin : AppRoute

    @Serializable
    data object Starred : AppRoute

    @Serializable
    data object LargestFiles : AppRoute

    companion object {
        val TOP_LEVEL: Set<AppRoute> = setOf(Tree, Explorer, Types, Discover)
        fun isTopLevel(route: AppRoute): Boolean = route in TOP_LEVEL

        fun fromString(key: String): AppRoute = when (key) {
            AppDestinations.TREE -> Tree
            AppDestinations.EXPLORER -> Explorer
            AppDestinations.TYPES -> Types
            AppDestinations.DISCOVER -> Discover
            AppDestinations.SETTINGS -> Settings
            AppDestinations.CLEANER_DUPLICATES -> CleanerDuplicates
            AppDestinations.CLEANER_EMPTY_FOLDERS -> CleanerEmptyFolders
            AppDestinations.CLEANER_SCREENSHOTS -> CleanerScreenshots
            AppDestinations.CLEANER_RECYCLE_BIN -> CleanerRecycleBin
            AppDestinations.STARRED -> Starred
            AppDestinations.LARGEST_FILES -> LargestFiles
            else -> Tree
        }

        fun toRouteString(route: AppRoute): String = when (route) {
            Tree -> AppDestinations.TREE
            Explorer -> AppDestinations.EXPLORER
            Types -> AppDestinations.TYPES
            Discover -> AppDestinations.DISCOVER
            Settings -> AppDestinations.SETTINGS
            CleanerDuplicates -> AppDestinations.CLEANER_DUPLICATES
            CleanerEmptyFolders -> AppDestinations.CLEANER_EMPTY_FOLDERS
            CleanerScreenshots -> AppDestinations.CLEANER_SCREENSHOTS
            CleanerRecycleBin -> AppDestinations.CLEANER_RECYCLE_BIN
            Starred -> AppDestinations.STARRED
            LargestFiles -> AppDestinations.LARGEST_FILES
        }
    }
}
