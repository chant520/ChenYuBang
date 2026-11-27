package com.chenyubang.feature.capture

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.chenyubang.core.ui.tokens.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CropCanvasGestureTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun draggingCornerUpdatesCrop() {
        var crop by mutableStateOf(RectF(0.1f, 0.1f, 0.9f, 0.9f))
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        composeTestRule.setContent {
            Box(Modifier.size(400.dp)) {
                CropCanvasBox(
                    view = CropView(bitmap, crop, 0, 0f, false),
                    colors = Tokens.Dark,
                    onCropChange = { crop = it },
                    onSizeChange = {},
                    modifier = Modifier.testTag("crop"),
                )
            }
        }

        composeTestRule.onNodeWithTag("crop").performTouchInput {
            swipe(Offset(40f, 40f), Offset(100f, 100f), durationMillis = 500)
        }

        assertTrue(crop.left in 0.2f..0.27f)
        assertTrue(crop.top in 0.2f..0.27f)
        assertEquals(0.9f, crop.right, 0.001f)
        assertEquals(0.9f, crop.bottom, 0.001f)
    }
}
