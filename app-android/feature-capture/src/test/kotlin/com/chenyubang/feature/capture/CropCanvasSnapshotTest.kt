package com.chenyubang.feature.capture

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.ChenYuBangTheme
import com.chenyubang.core.ui.tokens.Tokens
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h812dp-xhdpi")
class CropCanvasSnapshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rotatedLight() = capture(night = false, requestedFontScale = 1f)

    @Test
    fun rotatedDark() = capture(night = true, requestedFontScale = 1f)

    @Test
    @Config(qualifiers = "w320dp-h720dp-xhdpi")
    fun largeFontLight() = capture(night = false, requestedFontScale = 2f)

    @Test
    @Config(qualifiers = "w320dp-h720dp-xhdpi")
    fun largeFontDark() = capture(night = true, requestedFontScale = 2f)

    private fun capture(
        night: Boolean,
        requestedFontScale: Float,
    ) {
        val previousFontScale = RuntimeEnvironment.getFontScale()
        RuntimeEnvironment.setFontScale(requestedFontScale)
        try {
            composeTestRule.setContent {
                val configuration = LocalConfiguration.current
                val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                val nightConfiguration =
                    Configuration(configuration).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
                    }
                CompositionLocalProvider(LocalConfiguration provides nightConfiguration) {
                    ChenYuBangTheme(darkTheme = night) {
                        CropSnapshotContent()
                    }
                }
            }
            if (requestedFontScale > 1f) {
                val titleBounds = composeTestRule.onNodeWithText("裁切与摆正").fetchSemanticsNode().boundsInRoot
                val stepBounds = composeTestRule.onNodeWithText("第 2 步 / 共 5 步").fetchSemanticsNode().boundsInRoot
                assertTrue(stepBounds.top >= titleBounds.bottom)
            }
            composeTestRule.onRoot().captureRoboImage()
        } finally {
            RuntimeEnvironment.setFontScale(previousFontScale)
        }
    }

    @Composable
    private fun CropSnapshotContent() {
        val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
        val bitmap =
            remember {
                Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888).apply {
                    Canvas(this).drawColor(Color.DKGRAY)
                }
            }
        Column(Modifier.fillMaxSize().background(colors.page)) {
            CropHeader(onBack = {}, colors = colors)
            CropCanvasBox(
                view = CropView(bitmap, RectF(0.1f, 0.4f, 0.9f, 0.6f), 1, 0f, false),
                colors = colors,
                onCropChange = {},
                onSizeChange = {},
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = Tokens.Space.pageX.dp),
            )
            Text(
                "已自动给出题目边界，拖动四角可调整；只留这一道题，邻题去掉识别更准。",
                color = colors.text3,
                fontSize = Tokens.FontRole.caption.sp,
                modifier =
                    Modifier.padding(
                        horizontal = Tokens.Space.pageX.dp,
                        vertical = Tokens.Space.cropHintTop.dp,
                    ),
            )
            CropActionRow(
                enabled = true,
                onRotate = {},
                onAdjust = {},
                onRetake = {},
                colors = colors,
            )
        }
    }
}
