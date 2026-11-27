package com.chenyubang.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.chenyubang.core.ui.tokens.Tokens

/** 构件取色入口：由 ChenYuBangTheme 按 darkTheme 注入，与 MaterialTheme 共用同一决策点。 */
val LocalColorSet = staticCompositionLocalOf<Tokens.ColorSet> { Tokens.Light }

/** 深浅标记：供需按深浅切换非 ColorSet 资源（如悬浮按钮阴影层）的构件读取。 */
val LocalDarkTheme = staticCompositionLocalOf { false }

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
    CompositionLocalProvider(
        LocalColorSet provides c,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            content = content,
        )
    }
}
