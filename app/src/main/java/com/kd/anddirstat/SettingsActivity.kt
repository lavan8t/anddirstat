package com.kd.anddirstat

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.kd.anddirstat.ui.components.MaterialSymbol
import com.kd.anddirstat.ui.screens.SettingsView

class SettingsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = remember { getSharedPreferences("anddirstat_prefs", Context.MODE_PRIVATE) }
            val themePref = prefs.getString("app_theme", AppTheme.SYSTEM.key) ?: AppTheme.SYSTEM.key
            var currentTheme by remember {
                mutableStateOf(AppTheme.entries.firstOrNull { it.key == themePref } ?: AppTheme.SYSTEM)
            }
            var pureBlack by remember { mutableStateOf(prefs.getBoolean("pure_black", false)) }
            val accentPref = prefs.getString("accent_color", AccentColor.GREEN.key) ?: AccentColor.GREEN.key
            var accentColor by remember {
                mutableStateOf(AccentColor.entries.firstOrNull { it.key == accentPref } ?: AccentColor.GREEN)
            }
            var showHiddenFiles by remember { mutableStateOf(prefs.getBoolean("show_hidden_files", true)) }

            var entered by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                entered = true
            }

            AndDirStatTheme(appTheme = currentTheme, pureBlack = pureBlack, accentColor = accentColor) {
                AnimatedVisibility(
                    visible = entered,
                    enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                            slideInHorizontally(
                                initialOffsetX = { it / 3 },
                                animationSpec = tween(220, easing = FastOutSlowInEasing)
                            ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.surface,
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = "Settings",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        MaterialSymbol(
                                            name = "arrow_back",
                                            active = true,
                                            size = 22.dp,
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                                )
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
                            accentColor = accentColor,
                            onSelectAccent = { a ->
                                accentColor = a
                                prefs.edit { putString("accent_color", a.key) }
                            },
                            showHiddenFiles = showHiddenFiles,
                            onToggleShowHiddenFiles = { h ->
                                showHiddenFiles = h
                                prefs.edit { putBoolean("show_hidden_files", h) }
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
}
