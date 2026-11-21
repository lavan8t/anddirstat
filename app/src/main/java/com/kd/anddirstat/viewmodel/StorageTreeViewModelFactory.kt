package com.kd.anddirstat.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kd.anddirstat.util.AndroidIconWarmer
import com.kd.anddirstat.util.AndroidPermissionGate
import com.kd.anddirstat.util.AndroidScanProgress
import com.kd.anddirstat.util.AndroidSnapshotSink
import com.kd.anddirstat.util.AndroidTreeScanner
import com.kd.anddirstat.util.AndroidTreeStore

class StorageTreeViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(StorageTreeViewModel::class.java)) {
            "Unknown ViewModel class: $modelClass"
        }
        return StorageTreeViewModel(
            treeStore = AndroidTreeStore(appContext),
            progressSink = AndroidScanProgress(appContext),
            permissions = AndroidPermissionGate(appContext),
            scanner = AndroidTreeScanner(appContext),
            snapshots = AndroidSnapshotSink(appContext),
            icons = AndroidIconWarmer(appContext)
        ) as T
    }
}
