package com.chenyubang.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class PackArchiveBoundsTest {
    @Test
    fun compressedInputIsBoundedWhileReading() {
        val real = PackFixtureTest.bytes()
        val bytes = real.copyOf(PackArchive.MAX_INPUT_BYTES + 1)
        var consumed = 0
        val input =
            object : ByteArrayInputStream(bytes) {
                override fun read(
                    target: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int = super.read(target, offset, length).also { if (it > 0) consumed += it }
            }
        val failure = assertThrows(IllegalArgumentException::class.java) { ValidatedPack.readValidated(input) }
        assertTrue(failure.message.orEmpty().contains("byte limit"))
        assertEquals(PackArchive.MAX_INPUT_BYTES + 1, consumed)
    }

    @Test
    fun rejectsDeclaredEntryExpansionBeyondLimit() {
        val files = PackFixtureTest.files()
        val padded = files.getValue("idioms.json").copyOf(PackArchive.MAX_ENTRY_BYTES + 1)
        assertThrows(IllegalArgumentException::class.java) {
            PackFixtureTest.validate(PackFixtureTest.archive(files + ("idioms.json" to padded)))
        }
    }

    @Test
    fun forgedSmallSizeCannotBypassReadTimeExpansionLimit() {
        val files = PackFixtureTest.files()
        val padded = files.getValue("idioms.json").copyOf(PackArchive.MAX_ENTRY_BYTES + PADDING_BYTES)
        val archive = PackFixtureTest.archive(files + ("idioms.json" to padded))
        val forged = PackZipMutationTest.declaredSize(archive, "idioms.json", 1)
        val failure = assertThrows(IllegalArgumentException::class.java) { PackFixtureTest.validate(forged) }
        assertTrue(failure.message.orEmpty().contains("Pack byte limit"))
    }

    @Test
    fun rejectsAggregateExpansionBeyondLimit() {
        val files = PackFixtureTest.files().toMutableMap()
        files["idioms.json"] = files.getValue("idioms.json").copyOf(PackArchive.MAX_ENTRY_BYTES)
        files["examples.json"] = files.getValue("examples.json").copyOf(PackArchive.MAX_ENTRY_BYTES)
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                PackFixtureTest.validate(PackFixtureTest.archive(files))
            }
        assertTrue(failure.message.orEmpty().contains("expanded byte limit"))
    }

    @Test
    fun rejectsDeepJsonBeforeRecursingUnboundedly() {
        val files = PackFixtureTest.files()
        val wrapped =
            "[".repeat(EXCESS_DEPTH).toByteArray() + files.getValue("idioms.json") +
                "]".repeat(EXCESS_DEPTH).toByteArray()
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                PackFixtureTest.validate(PackFixtureTest.archive(files + ("idioms.json" to wrapped)))
            }
        assertTrue(failure.message.orEmpty().contains("depth limit"))
    }

    @Test
    fun rejectsMalformedUtf8AndTrailingJson() {
        val files = PackFixtureTest.files()
        for (tail in listOf(byteArrayOf(INVALID_UTF8), " null".toByteArray())) {
            assertThrows(Exception::class.java) {
                PackFixtureTest.validate(
                    PackFixtureTest.archive(files + ("idioms.json" to files.getValue("idioms.json") + tail)),
                )
            }
        }
    }

    companion object {
        private const val PADDING_BYTES = 4096
        private const val EXCESS_DEPTH = 40
        private const val INVALID_UTF8: Byte = -128
    }
}
