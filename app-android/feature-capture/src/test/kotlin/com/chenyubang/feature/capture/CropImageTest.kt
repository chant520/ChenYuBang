package com.chenyubang.feature.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import androidx.compose.ui.unit.IntSize
import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CropImageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun suggestionIncludesWholeWideQuestionSample() {
        val source = requireNotNull(CropImage.load(File("../../docs/OCR-DEMO/1.PNG").absolutePath))
        val suggested = CropImage.suggest(source)
        assertTrue("suggested=$suggested", suggested.left < 0.05f && suggested.right > 0.95f)
        assertTrue("suggested=$suggested", suggested.top < 0.05f && suggested.bottom > 0.9f)
    }

    @Test
    fun suggestKeeps360WideSourceAliveForExport() {
        val source =
            Bitmap.createBitmap(360, 720, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
                for (y in 100 until 620) {
                    for (x in 40 until 320) {
                        setPixel(x, y, Color.BLACK)
                    }
                }
            }
        val suggested = CropImage.suggest(source)
        assertFalse("suggest 回收了源宽等于分析宽的图", source.isRecycled)

        val view =
            viewport(imageWidth = 360, imageHeight = 720, containerWidth = 360f, containerHeight = 720f)
        val displayCrop = CropDisplayGeometry.sourceCropToDisplay(suggested, view)
        val frame = CropFrame.of(displayCrop, 0, 0f, IntSize(360, 720))
        val target = File(temporaryFolder.root, "360-wide-crop.jpg")

        assertTrue(CropImage.export(CropExport(source, frame, 360f, 720f, target)))
        val output = requireNotNull(BitmapFactory.decodeFile(target.absolutePath))
        assertTrue(output.width > 0 && output.height > 0)
    }

    @Test
    fun suggestedWideQuestionExportKeepsInkAwayFromEveryEdge() {
        val source = requireNotNull(CropImage.load(File("../../docs/OCR-DEMO/1.PNG").absolutePath))
        val view =
            viewport(
                imageWidth = source.width,
                imageHeight = source.height,
                containerWidth = 970f,
                containerHeight = 1442f,
            )
        val displayCrop = CropDisplayGeometry.sourceCropToDisplay(CropImage.suggest(source), view)
        val frame =
            CropFrame.of(
                displayCrop,
                quarterTurns = 0,
                fineDegrees = 0f,
                containerSize = IntSize(970, 1442),
            )
        val target = File(temporaryFolder.root, "suggested-crop.jpg")

        assertTrue(CropImage.export(CropExport(source, frame, 970f, 1442f, target)))
        val output = requireNotNull(BitmapFactory.decodeFile(target.absolutePath))
        val edgePixels =
            (0 until output.width).flatMap { x ->
                listOf(output.getPixel(x, 0), output.getPixel(x, output.height - 1))
            } +
                (0 until output.height).flatMap { y ->
                    listOf(output.getPixel(0, y), output.getPixel(output.width - 1, y))
                }
        assertTrue(
            "exported=${output.width}x${output.height}",
            edgePixels.all {
                Color.red(it) > 220 && Color.green(it) > 220 && Color.blue(it) > 220
            },
        )
    }

    @Test
    fun previewContainerMatchesCaptureAspectRatio() {
        assertEquals(3f / 4f, PREVIEW_ASPECT_RATIO, 0.0001f)
    }

    @Test
    fun displayMatrixUsesExplicitAffineValues() {
        val values = FloatArray(9)
        CropDisplayGeometry.displayMatrix(viewport()).getValues(values)
        assertEquals(0.625f, values[0], 0.0001f)
        assertEquals(25f, values[2], 0.0001f)
        assertEquals(0f, values[5], 0.0001f)
    }

    @Test
    fun displayMatrixUsesQuarterTurnAffineValues() {
        val values = FloatArray(9)
        CropDisplayGeometry.displayMatrix(viewport(quarterTurns = 1)).getValues(values)

        assertEquals(0f, values[0], 0.0001f)
        assertEquals(-0.375f, values[1], 0.0001f)
        assertEquals(0f, values[2], 0.0001f)
        assertEquals(0.375f, values[3], 0.0001f)
        assertEquals(0f, values[4], 0.0001f)
        assertEquals(325f, values[5], 0.0001f)
    }

    @Test
    fun sourceCropMapsThroughCenteredLetterbox() {
        val mapped =
            CropDisplayGeometry.sourceCropToDisplay(RectF(0f, 0f, 1f, 1f), viewport())

        assertRect(RectF(0.083333f, 0f, 0.916667f, 1f), mapped)
    }

    @Test
    fun sourceCropMapsThroughQuarterTurn() {
        val mapped =
            CropDisplayGeometry.sourceCropToDisplay(
                RectF(0.25f, 0.25f, 0.75f, 0.75f),
                viewport(quarterTurns = 1),
            )

        assertRect(RectF(0.25f, 0.425f, 0.75f, 0.575f), mapped)
    }

    @Test
    fun fineRotationKeepsWholeImageInsideContainer() {
        for (quarterTurns in 0 until ROTATION_QUARTERS) {
            for (angle in listOf(-15f, 15f)) {
                val bounds =
                    CropDisplayGeometry.displayedImageBounds(viewport(quarterTurns = quarterTurns, fineDegrees = angle))

                assertTrue("quarter=$quarterTurns angle=$angle left=${bounds.left}", bounds.left >= 0f)
                assertTrue("quarter=$quarterTurns angle=$angle top=${bounds.top}", bounds.top >= 0f)
                assertTrue("quarter=$quarterTurns angle=$angle right=${bounds.right}", bounds.right <= 1f)
                assertTrue("quarter=$quarterTurns angle=$angle bottom=${bounds.bottom}", bounds.bottom <= 1f)
            }
        }
        assertTrue(
            CropDisplayGeometry.fitScale(viewport(fineDegrees = 15f)) <
                CropDisplayGeometry.fitScale(viewport(fineDegrees = 0f)),
        )
    }

    @Test
    fun resizeRemapsRelativeToDisplayedImage() {
        val remapped =
            CropDisplayGeometry.remapCrop(
                crop = RectF(0.4f, 0.4f, 0.6f, 0.6f),
                from = viewport(),
                to = viewport(containerWidth = 600f),
            )

        assertRect(RectF(0.45f, 0.4f, 0.55f, 0.6f), remapped)
    }

    @Test
    fun exportUsesPreviewFieldOfViewAndCropOrigin() {
        val source =
            Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888).apply {
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        setPixel(
                            x,
                            y,
                            when {
                                y < 40 && x < 20 -> Color.RED
                                y < 40 -> Color.GREEN
                                x < 20 -> Color.BLUE
                                else -> Color.YELLOW
                            },
                        )
                    }
                }
            }
        val frame =
            CropFrame.of(
                RectF(0f, 0.35f, 1f, 0.65f),
                quarterTurns = 1,
                fineDegrees = 0f,
                containerSize = IntSize(300, 500),
            )
        val target = File(temporaryFolder.root, "crop.jpg")
        val values = FloatArray(9)
        CropDisplayGeometry.exportMatrix(CropExport(source, frame, 300f, 500f, target), 1f).getValues(values)
        assertEquals(0f, values[0], 0.0001f)
        assertEquals(-3.75f, values[1], 0.0001f)
        assertEquals(0f, values[2], 0.0001f)
        assertEquals(3.75f, values[3], 0.0001f)
        assertEquals(150f, values[5], 0.0001f)
        assertMappedColor(source, values, 20f, 20f, Color.GREEN)
        assertMappedColor(source, values, 20f, 130f, Color.RED)
        assertMappedColor(source, values, 280f, 20f, Color.YELLOW)
        assertMappedColor(source, values, 280f, 130f, Color.BLUE)

        assertTrue(CropImage.export(CropExport(source, frame, 300f, 500f, target)))
        val output = BitmapFactory.decodeFile(target.absolutePath)
        assertEquals(300, output.width)
        assertEquals(150, output.height)
    }

    @Test
    fun loadAppliesExifRotationBeforeCropping() {
        val source =
            Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888).apply {
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        setPixel(x, y, if (x < width / 2) Color.RED else Color.BLUE)
                    }
                }
            }
        val target = File(temporaryFolder.root, "exif.jpg")
        target.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        ExifInterface(target.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val loaded = requireNotNull(CropImage.load(target.absolutePath))
        assertEquals(80, loaded.width)
        assertEquals(40, loaded.height)
        val top = loaded.getPixel(40, 10)
        val bottom = loaded.getPixel(40, 30)
        assertTrue(Color.red(top) > 240 && Color.blue(top) < 15)
        assertTrue(Color.blue(bottom) > 240 && Color.red(bottom) < 15)
    }

    @Test
    fun applyExifOrientationRecyclesSourceOnRotation() {
        val source = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val target = File(temporaryFolder.root, "exif-rotated-source.jpg")
        target.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        ExifInterface(target.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        val rotated = CropImage.applyExifOrientation(target.absolutePath, source)
        assertTrue("旋转分支未回收原图", source.isRecycled)
        assertFalse(rotated.isRecycled)
        assertEquals(80, rotated.width)
        assertEquals(40, rotated.height)
    }

    @Test
    fun applyExifOrientationKeepsSourceOnIdentity() {
        val source = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val target = File(temporaryFolder.root, "exif-normal-source.jpg")
        target.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        ExifInterface(target.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            saveAttributes()
        }

        val result = CropImage.applyExifOrientation(target.absolutePath, source)
        assertTrue(result === source)
        assertFalse(source.isRecycled)
    }

    @Test
    fun exportShrinksMaximumDimension() {
        val source = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888)
        val frame = CropFrame.of(RectF(0f, 0f, 1f, 1f), 0, 0f, IntSize(5000, 4000))
        val target = File(temporaryFolder.root, "large-crop.jpg")

        assertTrue(CropImage.export(CropExport(source, frame, 5000f, 4000f, target)))
        val output = BitmapFactory.decodeFile(target.absolutePath)
        assertEquals(4096, output.width)
        assertEquals(3277, output.height)
    }

    @Test
    fun exportRejectsEmptyCrop() {
        val source = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888)
        val frame = CropFrame.of(RectF(0.5f, 0.5f, 0.5f, 0.5f), 0, 0f, IntSize(300, 500))
        val target = File(temporaryFolder.root, "empty-crop.jpg")

        assertFalse(CropImage.export(CropExport(source, frame, 300f, 500f, target)))
        assertFalse(target.exists())
    }

    private fun viewport(
        imageWidth: Int = 400,
        imageHeight: Int = 800,
        containerWidth: Float = 300f,
        containerHeight: Float = 500f,
        quarterTurns: Int = 0,
        fineDegrees: Float = 0f,
    ): CropViewport =
        CropViewport(
            imageWidth,
            imageHeight,
            containerWidth,
            containerHeight,
            quarterTurns,
            fineDegrees,
        )

    private fun assertMappedColor(
        source: Bitmap,
        values: FloatArray,
        outputX: Float,
        outputY: Float,
        expected: Int,
    ) {
        val determinant = values[0] * values[4] - values[1] * values[3]
        val translatedX = outputX - values[2]
        val translatedY = outputY - values[5]
        val sourceX =
            ((values[4] * translatedX - values[3] * translatedY) / determinant)
                .roundToInt()
                .coerceIn(0, source.width - 1)
        val sourceY =
            ((-values[1] * translatedX + values[0] * translatedY) / determinant)
                .roundToInt()
                .coerceIn(0, source.height - 1)
        assertEquals(
            "output($outputX, $outputY) maps to source($sourceX, $sourceY)",
            expected,
            source.getPixel(sourceX, sourceY),
        )
    }

    private fun assertRect(
        expected: RectF,
        actual: RectF,
    ) {
        assertEquals(expected.left, actual.left, 0.0001f)
        assertEquals(expected.top, actual.top, 0.0001f)
        assertEquals(expected.right, actual.right, 0.0001f)
        assertEquals(expected.bottom, actual.bottom, 0.0001f)
    }
}
