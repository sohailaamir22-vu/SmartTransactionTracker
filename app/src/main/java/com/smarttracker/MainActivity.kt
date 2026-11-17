package com.smarttracker

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.smarttracker.ui.AppTheme
import com.smarttracker.ui.TrackerApp
import com.smarttracker.ui.isDarkMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        setContent {
            var mode by remember { mutableStateOf(prefs.getString("theme", "system") ?: "system") }
            val dark = isDarkMode(mode, isSystemInDarkTheme())
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            AppTheme(mode) {
                TrackerApp(
                    themeMode = mode,
                    onThemeChange = {
                        mode = it
                        prefs.edit().putString("theme", it).apply()
                    },
                )
            }
        }
    }
}
