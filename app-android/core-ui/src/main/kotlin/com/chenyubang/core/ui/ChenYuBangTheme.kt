package com.chenyubang.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.chenyubang.core.ui.tokens.Tokens

/**
 * 全局主题。颜色只从 Tokens 取，页面里禁止出现十六进制（规范 §6 / §12.2）。
 */
@Composable
fun ChenYuBangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val c: Tokens.ColorSet = if (darkTheme) Tokens.Dark else Tokens.Light
    val scheme =
        if (darkTheme) {
            darkColorScheme(
                primary = c.accent,
                onPrimary = c.segonfg,
                secondary = c.accent2,
                background = c.page,
                onBackground = c.text,
                surface = c.surface,
                onSurface = c.text,
                error = c.errfill,
                onError = c.errfillfg,
            )
        } else {
            lightColorScheme(
                primary = c.accent,
                onPrimary = c.segonfg,
                secondary = c.accent2,
                background = c.page,
                onBackground = c.text,
                surface = c.surface,
                onSurface = c.text,
                error = c.errfill,
                onError = c.errfillfg,
            )
        }
    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
