package com.kd.anddirstat.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kd.anddirstat.R

@DrawableRes
fun getMaterialSymbolRes(name: String, active: Boolean = true): Int {
    val clean = name.lowercase().trim()
    return if (active) {
        when (clean) {
            "grid_view" -> R.drawable.sym_grid_view_1
            "folder" -> R.drawable.sym_folder_1
            "folder_open" -> R.drawable.sym_folder_open_1
            "folder_zip" -> R.drawable.sym_folder_zip_1
            "folder_special" -> R.drawable.sym_folder_special_1
            "folder_delete" -> R.drawable.sym_folder_delete_1
            "pie_chart" -> R.drawable.sym_pie_chart_1
            "explore", "discover", "compass" -> R.drawable.sym_explore_1
            "search" -> R.drawable.sym_search_1
            "close" -> R.drawable.sym_close_1
            "arrow_back" -> R.drawable.sym_arrow_back_1
            "arrow_forward" -> R.drawable.sym_arrow_forward_1
            "chevron_right" -> R.drawable.sym_chevron_right_1
            "expand_more" -> R.drawable.sym_expand_more_1
            "expand_less" -> R.drawable.sym_expand_less_1
            "more_vert" -> R.drawable.sym_more_vert_1
            "more_horiz" -> R.drawable.sym_more_horiz_1
            "refresh", "reset", "restart_alt" -> R.drawable.sym_refresh_1
            "settings" -> R.drawable.sym_settings_1
            "description", "draft", "insert_drive_file" -> R.drawable.sym_description_1
            "image" -> R.drawable.sym_image_1
            "movie" -> R.drawable.sym_movie_1
            "music_note" -> R.drawable.sym_music_note_1
            "code" -> R.drawable.sym_code_1
            "android" -> R.drawable.sym_android_1
            "apps" -> R.drawable.sym_apps_1
            "storage" -> R.drawable.sym_storage_1
            "cached" -> R.drawable.sym_cached_1
            "category" -> R.drawable.sym_category_1
            "delete" -> R.drawable.sym_delete_1
            "delete_outline" -> R.drawable.sym_delete_outline_1
            "delete_sweep" -> R.drawable.sym_delete_sweep_1
            "delete_forever" -> R.drawable.sym_delete_forever_1
            "restore_from_trash" -> R.drawable.sym_restore_from_trash_1
            "content_copy" -> R.drawable.sym_content_copy_1
            "camera_alt", "screenshot_monitor" -> R.drawable.sym_camera_alt_1
            "file_download", "download" -> R.drawable.sym_file_download_1
            "auto_fix_high" -> R.drawable.sym_auto_fix_high_1
            "filter_list", "filter_alt" -> R.drawable.sym_filter_list_1
            "sort", "sort_by_alpha" -> R.drawable.sym_sort_1
            "schedule" -> R.drawable.sym_schedule_1
            "history" -> R.drawable.sym_history_1
            "select_all" -> R.drawable.sym_select_all_1
            "deselect" -> R.drawable.sym_deselect_1
            "clear_all", "clear" -> R.drawable.sym_clear_all_1
            "star" -> R.drawable.sym_star_1
            "star_outline" -> R.drawable.sym_star_0
            "favorite" -> R.drawable.sym_favorite_1
            "share" -> R.drawable.sym_share_1
            "open_in_new" -> R.drawable.sym_open_in_new_1
            "content_cut" -> R.drawable.sym_content_cut_1
            "archive" -> R.drawable.sym_archive_1
            "unarchive" -> R.drawable.sym_unarchive_1
            "visibility" -> R.drawable.sym_visibility_1
            "lock" -> R.drawable.sym_lock_1
            "lock_open" -> R.drawable.sym_lock_open_1
            "smartphone" -> R.drawable.sym_smartphone_1
            "sd_card" -> R.drawable.sym_sd_card_1
            "usb" -> R.drawable.sym_usb_1
            "zoom_in" -> R.drawable.sym_zoom_in_1
            "zoom_out" -> R.drawable.sym_zoom_out_1
            "check" -> R.drawable.sym_check_1
            "check_circle" -> R.drawable.sym_check_circle_1
            "warning" -> R.drawable.sym_warning_1
            "error" -> R.drawable.sym_error_1
            "info" -> R.drawable.sym_info_1
            "help" -> R.drawable.sym_help_1
            "view_list" -> R.drawable.sym_view_list_1
            "sync" -> R.drawable.sym_sync_1
            "analytics" -> R.drawable.sym_analytics_1
            "palette" -> R.drawable.sym_palette_1
            "dark_mode" -> R.drawable.sym_dark_mode_1
            "light_mode" -> R.drawable.sym_light_mode_1
            "brightness_medium" -> R.drawable.sym_brightness_medium_1
            "github" -> R.drawable.sym_github_1
            else -> R.drawable.sym_description_1
        }
    } else {
        when (clean) {
            "grid_view" -> R.drawable.sym_grid_view_0
            "folder" -> R.drawable.sym_folder_0
            "folder_open" -> R.drawable.sym_folder_open_0
            "folder_zip" -> R.drawable.sym_folder_zip_0
            "folder_special" -> R.drawable.sym_folder_special_0
            "folder_delete" -> R.drawable.sym_folder_delete_0
            "pie_chart" -> R.drawable.sym_pie_chart_0
            "explore", "discover", "compass" -> R.drawable.sym_explore_0
            "search" -> R.drawable.sym_search_0
            "close" -> R.drawable.sym_close_0
            "arrow_back" -> R.drawable.sym_arrow_back_0
            "arrow_forward" -> R.drawable.sym_arrow_forward_0
            "chevron_right" -> R.drawable.sym_chevron_right_0
            "expand_more" -> R.drawable.sym_expand_more_0
            "expand_less" -> R.drawable.sym_expand_less_0
            "more_vert" -> R.drawable.sym_more_vert_0
            "more_horiz" -> R.drawable.sym_more_horiz_0
            "refresh", "reset", "restart_alt" -> R.drawable.sym_refresh_0
            "settings" -> R.drawable.sym_settings_0
            "description", "draft", "insert_drive_file" -> R.drawable.sym_description_0
            "image" -> R.drawable.sym_image_0
            "movie" -> R.drawable.sym_movie_0
            "music_note" -> R.drawable.sym_music_note_0
            "code" -> R.drawable.sym_code_0
            "android" -> R.drawable.sym_android_0
            "apps" -> R.drawable.sym_apps_0
            "storage" -> R.drawable.sym_storage_0
            "cached" -> R.drawable.sym_cached_0
            "category" -> R.drawable.sym_category_0
            "delete" -> R.drawable.sym_delete_0
            "delete_outline" -> R.drawable.sym_delete_outline_0
            "delete_sweep" -> R.drawable.sym_delete_sweep_0
            "delete_forever" -> R.drawable.sym_delete_forever_0
            "restore_from_trash" -> R.drawable.sym_restore_from_trash_0
            "content_copy" -> R.drawable.sym_content_copy_0
            "camera_alt", "screenshot_monitor" -> R.drawable.sym_camera_alt_0
            "file_download", "download" -> R.drawable.sym_file_download_0
            "auto_fix_high" -> R.drawable.sym_auto_fix_high_0
            "filter_list", "filter_alt" -> R.drawable.sym_filter_list_0
            "sort", "sort_by_alpha" -> R.drawable.sym_sort_0
            "schedule" -> R.drawable.sym_schedule_0
            "history" -> R.drawable.sym_history_0
            "select_all" -> R.drawable.sym_select_all_0
            "deselect" -> R.drawable.sym_deselect_0
            "clear_all", "clear" -> R.drawable.sym_clear_all_0
            "star", "star_outline" -> R.drawable.sym_star_0
            "favorite" -> R.drawable.sym_favorite_0
            "share" -> R.drawable.sym_share_0
            "open_in_new" -> R.drawable.sym_open_in_new_0
            "content_cut" -> R.drawable.sym_content_cut_0
            "archive" -> R.drawable.sym_archive_0
            "unarchive" -> R.drawable.sym_unarchive_0
            "visibility" -> R.drawable.sym_visibility_0
            "lock" -> R.drawable.sym_lock_0
            "lock_open" -> R.drawable.sym_lock_open_0
            "smartphone" -> R.drawable.sym_smartphone_0
            "sd_card" -> R.drawable.sym_sd_card_0
            "usb" -> R.drawable.sym_usb_0
            "zoom_in" -> R.drawable.sym_zoom_in_0
            "zoom_out" -> R.drawable.sym_zoom_out_0
            "check" -> R.drawable.sym_check_0
            "check_circle" -> R.drawable.sym_check_circle_0
            "warning" -> R.drawable.sym_warning_0
            "error" -> R.drawable.sym_error_0
            "info" -> R.drawable.sym_info_0
            "help" -> R.drawable.sym_help_0
            "view_list" -> R.drawable.sym_view_list_0
            "sync" -> R.drawable.sym_sync_0
            "analytics" -> R.drawable.sym_analytics_0
            "palette" -> R.drawable.sym_palette_0
            "dark_mode" -> R.drawable.sym_dark_mode_0
            "light_mode" -> R.drawable.sym_light_mode_0
            "brightness_medium" -> R.drawable.sym_brightness_medium_0
            "github" -> R.drawable.sym_github_0
            else -> R.drawable.sym_description_0
        }
    }
}

@Composable
fun getMaterialSymbolVector(name: String, active: Boolean = true): ImageVector {
    return ImageVector.vectorResource(getMaterialSymbolRes(name, active))
}

@Composable
fun MaterialSymbol(
    name: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    size: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Icon(
        painter = painterResource(getMaterialSymbolRes(name, active)),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size)
    )
}

