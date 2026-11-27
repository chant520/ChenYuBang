package com.chenyubang.feature.capture

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaptureImageStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `sampleSize caps long edge by power of two`() {
        assertEquals(1, CaptureImageStore.sampleSize(3200, 100))
        assertEquals(2, CaptureImageStore.sampleSize(6400, 100))
        assertEquals(4, CaptureImageStore.sampleSize(10000, 9000))
    }

    @Test
    fun `delete removes cached capture`() {
        val file = File(temporaryFolder.root, "capture.jpg").apply { writeText("x") }
        assertTrue(CaptureImageStore.delete(file))
        assertFalse(file.exists())
        assertTrue(CaptureImageStore.delete(file))
    }

    @Test
    fun `importFromUri catches SecurityException`() =
        runBlocking {
            val uri = Uri.parse("content://test/denied")
            val context = ApplicationProvider.getApplicationContext<Context>()
            Shadows.shadowOf(context.contentResolver).registerInputStream(
                uri,
                object : InputStream() {
                    override fun read(): Int = throw SecurityException("denied")

                    override fun read(
                        b: ByteArray,
                        off: Int,
                        len: Int,
                    ): Int = throw SecurityException("denied")
                },
            )
            assertNull(CaptureImageStore.importFromUri(context, uri))
        }

    @Test
    fun `loadCropBitmap converges loader failure to null`() {
        assertNull(loadCropBitmap("bad") { throw OutOfMemoryError("oom") })
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        assertSame(bitmap, loadCropBitmap("good") { bitmap })
    }
}
