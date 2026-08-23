package com.kd.anddirstat

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.screens.SettingsView

class SettingsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs = remember { getSharedPreferences("anddirstat_prefs", Context.MODE_PRIVATE) }
            val themePref = prefs.getString("app_theme", AppTheme.SYSTEM.key) ?: AppTheme.SYSTEM.key
            var currentTheme by remember {
                mutableStateOf(AppTheme.entries.firstOrNull { it.key == themePref } ?: AppTheme.SYSTEM)
            }
            var pureBlack by remember { mutableStateOf(prefs.getBoolean("pure_black", false)) }
            var dynamicTheme by remember {
                mutableStateOf(prefs.getBoolean("dynamic_theme", android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S))
            }
            val accentPref = prefs.getString("accent_color", AccentColor.GREEN.key) ?: AccentColor.GREEN.key
            var accentColor by remember {
                mutableStateOf(AccentColor.entries.firstOrNull { it.key == accentPref } ?: AccentColor.GREEN)
            }

            var showFreeSpace by remember { mutableStateOf(prefs.getBoolean("show_free_space", true)) }
            var showSystemApps by remember { mutableStateOf(prefs.getBoolean("show_system_apps", true)) }
            var showHiddenFiles by remember { mutableStateOf(prefs.getBoolean("show_hidden_files", false)) }
            var showSystemOS by remember { mutableStateOf(prefs.getBoolean("show_system_os", false)) }

            val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

            AndDirStatTheme(
                appTheme = currentTheme,
                pureBlack = pureBlack,
                dynamicTheme = dynamicTheme,
                accentColor = accentColor
            ) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    containerColor = MaterialTheme.colorScheme.surface,
                    topBar = {
                        LargeTopAppBar(
                            title = {
                                Text(
                                    text = "Settings",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    MaterialSymbol(
                                        name = "arrow_back",
                                        active = true,
                                        size = 24.dp,
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                actionIconContentColor = Color.Unspecified
                            ),
                            scrollBehavior = scrollBehavior
                        )
                    }
                ) { paddingValues ->
                    SettingsView(
                        currentTheme = currentTheme,
                        onSelectTheme = { selectedTheme ->
                            currentTheme = selectedTheme
                            prefs.edit { putString("app_theme", selectedTheme.key) }
                        },
                        pureBlack = pureBlack,
                        onTogglePureBlack = { v ->
                            pureBlack = v
                            prefs.edit { putBoolean("pure_black", v) }
                        },
                        dynamicTheme = dynamicTheme,
                        onToggleDynamicTheme = { v ->
                            dynamicTheme = v
                            prefs.edit { putBoolean("dynamic_theme", v) }
                        },
                        accentColor = accentColor,
                        onSelectAccent = { a ->
                            accentColor = a
                            prefs.edit { putString("accent_color", a.key) }
                        },
                        showFreeSpace = showFreeSpace,
                        onToggleShowFreeSpace = { v ->
                            showFreeSpace = v
                            prefs.edit { putBoolean("show_free_space", v) }
                        },
                        showSystemApps = showSystemApps,
                        onToggleShowSystemApps = { v ->
                            showSystemApps = v
                            prefs.edit { putBoolean("show_system_apps", v) }
                        },
                        showHiddenFiles = showHiddenFiles,
                        onToggleShowHiddenFiles = { v ->
                            showHiddenFiles = v
                            prefs.edit { putBoolean("show_hidden_files", v) }
                        },
                        showSystemOS = showSystemOS,
                        onToggleShowSystemOS = { v ->
                            showSystemOS = v
                            prefs.edit { putBoolean("show_system_os", v) }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
            }
        }
    }
}
