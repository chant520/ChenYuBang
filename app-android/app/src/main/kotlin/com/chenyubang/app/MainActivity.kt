package com.chenyubang.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.ui.ChenYuBangTheme

class MainActivity : ComponentActivity() {
    private val database by lazy { AppDatabase.open(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            ChenYuBangTheme(darkTheme = darkTheme) { AppRouteHost(database) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        database.close()
    }
}
