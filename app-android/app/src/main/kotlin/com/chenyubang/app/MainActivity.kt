package com.chenyubang.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.chenyubang.core.data.AppDatabase
import com.chenyubang.core.ui.ChenYuBangTheme

class MainActivity : ComponentActivity() {
    private val database by lazy { AppDatabase.open(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChenYuBangTheme { AppRouteHost(database) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        database.close()
    }
}
