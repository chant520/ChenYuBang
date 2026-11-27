package com.chenyubang.feature.capture

import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CropGeometryTest {
    @Test
    fun cornerCannotEnterLetterbox() {
        val moved =
            moveCorner(
                crop = RectF(0.25f, 0.1f, 0.75f, 0.8f),
                corner = CORNER_LEFT_TOP,
                drag = Offset(-120f, -100f),
                size = IntSize(300, 500),
                minSizePx = 48f,
                bounds = RectF(0.125f, 0f, 0.875f, 0.8f),
            )

        assertEquals(0.125f, moved.left, 0.0001f)
        assertEquals(0f, moved.top, 0.0001f)
        assertEquals(0.75f, moved.right, 0.0001f)
        assertEquals(0.8f, moved.bottom, 0.0001f)
    }

    @Test
    fun cornerCannotCrossMinimumOppositeEdge() {
        val moved =
            moveCorner(
                crop = RectF(0.2f, 0.2f, 0.8f, 0.8f),
                corner = CORNER_RIGHT_BOTTOM,
                drag = Offset(-210f, -350f),
                size = IntSize(300, 500),
                minSizePx = 48f,
            )

        assertEquals(0.2f, moved.left, 0.0001f)
        assertEquals(0.2f, moved.top, 0.0001f)
        assertEquals(0.36f, moved.right, 0.0001f)
        assertEquals(0.296f, moved.bottom, 0.0001f)
    }
}
