package com.kd.anddirstat.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderDelete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.Drafts
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun getMaterialSymbolVector(name: String, active: Boolean = true): ImageVector {
    val clean = name.lowercase().trim()
    return if (active) {
        when (clean) {
            "explore", "compass", "discover" -> Icons.Rounded.Explore
            "search" -> Icons.Rounded.Search
            "close" -> Icons.Rounded.Close
            "clear", "clear_all" -> Icons.Rounded.ClearAll
            "delete" -> Icons.Rounded.Delete
            "delete_sweep" -> Icons.Rounded.DeleteSweep
            "delete_forever" -> Icons.Rounded.DeleteForever
            "delete_outline" -> Icons.Rounded.DeleteOutline
            "restore_from_trash" -> Icons.Rounded.RestoreFromTrash
            "refresh" -> Icons.Rounded.Refresh
            "arrow_back" -> Icons.AutoMirrored.Rounded.ArrowBack
            "arrow_forward" -> Icons.AutoMirrored.Rounded.ArrowForward
            "chevron_right" -> Icons.Rounded.ChevronRight
            "expand_more" -> Icons.Rounded.ExpandMore
            "expand_less" -> Icons.Rounded.ExpandLess
            "folder" -> Icons.Rounded.Folder
            "folder_open" -> Icons.Rounded.FolderOpen
            "folder_zip" -> Icons.Rounded.FolderZip
            "folder_special" -> Icons.Rounded.FolderSpecial
            "folder_delete" -> Icons.Rounded.FolderDelete
            "description" -> Icons.Rounded.Description
            "image" -> Icons.Rounded.Image
            "movie" -> Icons.Rounded.Movie
            "music_note" -> Icons.Rounded.MusicNote
            "android" -> Icons.Rounded.Android
            "apps" -> Icons.Rounded.Apps
            "draft" -> Icons.AutoMirrored.Rounded.InsertDriveFile
            "code" -> Icons.Rounded.Code
            "check" -> Icons.Rounded.Check
            "check_circle" -> Icons.Rounded.CheckCircle
            "star" -> Icons.Rounded.Star
            "star_outline" -> Icons.Rounded.StarBorder
            "favorite" -> Icons.Rounded.Favorite
            "sort", "sort_by_alpha" -> Icons.AutoMirrored.Rounded.Sort
            "filter_list", "filter_alt" -> Icons.Rounded.FilterList
            "explore", "compass", "discover" -> Icons.Rounded.Explore
            "settings" -> Icons.Rounded.Settings
            "analytics" -> Icons.Rounded.Analytics
            "storage" -> Icons.Rounded.Storage
            "pie_chart" -> Icons.Rounded.PieChart
            "schedule" -> Icons.Rounded.Schedule
            "history" -> Icons.Rounded.History
            "auto_fix_high" -> Icons.Rounded.AutoFixHigh
            "content_copy" -> Icons.Rounded.ContentCopy
            "select_all" -> Icons.Rounded.SelectAll
            "deselect" -> Icons.Rounded.Deselect
            "open_in_new" -> Icons.AutoMirrored.Rounded.OpenInNew
            "share" -> Icons.Rounded.Share
            "info" -> Icons.Rounded.Info
            "warning" -> Icons.Rounded.Warning
            "error" -> Icons.Rounded.Error
            "help" -> Icons.AutoMirrored.Rounded.Help
            "view_list" -> Icons.AutoMirrored.Rounded.ViewList
            "grid_view" -> Icons.Rounded.GridView
            "more_vert" -> Icons.Rounded.MoreVert
            "more_horiz" -> Icons.Rounded.MoreHoriz
            "unarchive" -> Icons.Rounded.Unarchive
            "archive" -> Icons.Rounded.Archive
            "content_cut" -> Icons.Rounded.ContentCut
            "lock" -> Icons.Rounded.Lock
            "lock_open" -> Icons.Rounded.LockOpen
            "camera_alt", "screenshot_monitor" -> Icons.Rounded.CameraAlt
            "palette" -> Icons.Rounded.Palette
            "dark_mode" -> Icons.Rounded.DarkMode
            "light_mode" -> Icons.Rounded.LightMode
            "sync" -> Icons.Rounded.Sync
            "download", "file_download" -> Icons.Rounded.FileDownload
            "zoom_in" -> Icons.Rounded.ZoomIn
            "zoom_out" -> Icons.Rounded.ZoomOut
            "category" -> Icons.Rounded.Category
            "sd_card" -> Icons.Rounded.SdCard
            "smartphone" -> Icons.Rounded.Smartphone
            "usb" -> Icons.Rounded.Usb
            "visibility" -> Icons.Rounded.Visibility
            "brightness_medium" -> Icons.Rounded.BrightnessMedium
            else -> Icons.AutoMirrored.Rounded.InsertDriveFile
        }
    } else {
        when (clean) {
            "explore", "compass", "discover" -> Icons.Outlined.Explore
            "search" -> Icons.Outlined.Search
            "close" -> Icons.Outlined.Close
            "clear", "clear_all" -> Icons.Outlined.ClearAll
            "delete" -> Icons.Outlined.Delete
            "delete_sweep" -> Icons.Outlined.DeleteSweep
            "delete_forever" -> Icons.Outlined.DeleteForever
            "delete_outline" -> Icons.Outlined.DeleteOutline
            "restore_from_trash" -> Icons.Outlined.RestoreFromTrash
            "refresh" -> Icons.Outlined.Refresh
            "arrow_back" -> Icons.AutoMirrored.Outlined.ArrowBack
            "arrow_forward" -> Icons.AutoMirrored.Outlined.ArrowForward
            "chevron_right" -> Icons.Outlined.ChevronRight
            "expand_more" -> Icons.Outlined.ExpandMore
            "expand_less" -> Icons.Outlined.ExpandLess
            "folder" -> Icons.Outlined.Folder
            "folder_open" -> Icons.Outlined.FolderOpen
            "folder_zip" -> Icons.Outlined.FolderZip
            "folder_special" -> Icons.Outlined.FolderSpecial
            "folder_delete" -> Icons.Outlined.FolderDelete
            "description" -> Icons.Outlined.Description
            "image" -> Icons.Outlined.Image
            "movie" -> Icons.Outlined.Movie
            "music_note" -> Icons.Outlined.MusicNote
            "android" -> Icons.Outlined.Android
            "apps" -> Icons.Outlined.Apps
            "draft" -> Icons.AutoMirrored.Outlined.InsertDriveFile
            "code" -> Icons.Outlined.Code
            "check" -> Icons.Outlined.Check
            "check_circle" -> Icons.Outlined.CheckCircle
            "star", "star_outline" -> Icons.Outlined.StarBorder
            "favorite" -> Icons.Outlined.FavoriteBorder
            "sort", "sort_by_alpha" -> Icons.AutoMirrored.Outlined.Sort
            "filter_list", "filter_alt" -> Icons.Outlined.FilterList
            "explore", "compass", "discover" -> Icons.Outlined.Explore
            "settings" -> Icons.Outlined.Settings
            "analytics" -> Icons.Outlined.Analytics
            "storage" -> Icons.Outlined.Storage
            "pie_chart" -> Icons.Outlined.PieChart
            "schedule" -> Icons.Outlined.Schedule
            "history" -> Icons.Outlined.History
            "auto_fix_high" -> Icons.Outlined.AutoFixHigh
            "content_copy" -> Icons.Outlined.ContentCopy
            "select_all" -> Icons.Outlined.SelectAll
            "deselect" -> Icons.Outlined.Deselect
            "open_in_new" -> Icons.AutoMirrored.Outlined.OpenInNew
            "share" -> Icons.Outlined.Share
            "info" -> Icons.Outlined.Info
            "warning" -> Icons.Outlined.Warning
            "error" -> Icons.Outlined.Error
            "help" -> Icons.AutoMirrored.Outlined.Help
            "view_list" -> Icons.AutoMirrored.Outlined.ViewList
            "grid_view" -> Icons.Outlined.GridView
            "more_vert" -> Icons.Outlined.MoreVert
            "more_horiz" -> Icons.Outlined.MoreHoriz
            "unarchive" -> Icons.Outlined.Unarchive
            "archive" -> Icons.Outlined.Archive
            "content_cut" -> Icons.Outlined.ContentCut
            "lock" -> Icons.Outlined.Lock
            "lock_open" -> Icons.Outlined.LockOpen
            "camera_alt", "screenshot_monitor" -> Icons.Outlined.CameraAlt
            "palette" -> Icons.Outlined.Palette
            "dark_mode" -> Icons.Outlined.DarkMode
            "light_mode" -> Icons.Outlined.LightMode
            "sync" -> Icons.Outlined.Sync
            "download", "file_download" -> Icons.Outlined.FileDownload
            "zoom_in" -> Icons.Outlined.ZoomIn
            "zoom_out" -> Icons.Outlined.ZoomOut
            "category" -> Icons.Outlined.Category
            "sd_card" -> Icons.Outlined.SdCard
            "smartphone" -> Icons.Outlined.Smartphone
            "usb" -> Icons.Outlined.Usb
            "visibility" -> Icons.Outlined.Visibility
            "brightness_medium" -> Icons.Outlined.BrightnessMedium
            else -> Icons.AutoMirrored.Outlined.InsertDriveFile
        }
    }
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
        imageVector = getMaterialSymbolVector(name, active),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size)
    )
}
