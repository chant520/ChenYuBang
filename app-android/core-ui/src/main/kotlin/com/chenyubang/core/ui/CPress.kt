package com.chenyubang.core.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private const val PRESS_SCALE = 0.9f

private val pressDimColor = Color(PRESS_SCALE, PRESS_SCALE, PRESS_SCALE, 1f)

/** 按下反馈（规范 §5.0）：整体亮度 ×0.9，等效 CSS filter:brightness(.9)。 */
internal fun Modifier.pressDim(pressed: Boolean): Modifier =
    if (!pressed) {
        this
    } else {
        drawWithContent {
            drawContent()
            drawRect(color = pressDimColor, blendMode = BlendMode.Modulate)
        }
    }

/** 系统动画尺度为 0 视为 reduce motion（规范 §9.3 降级口径）。 */
@Composable
internal fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
