package com.chenyubang.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class PackArchiveTest {
    @Test
    fun authenticArchiveAndSystemDescriptorArchiveBothValidate() {
        assertEquals(PackFixtureTest.DIGEST, PackFixtureTest.validate().sha256)
        assertEquals(
            PackFixtureTest.DIGEST,
            PackFixtureTest.validate(PackFixtureTest.archive(PackFixtureTest.files())).sha256,
        )
    }

    @Test
    fun rejectsExtraFileAndDirectory() {
        for (name in listOf("extra.json", "bad/")) {
            rejected(PackFixtureTest.archive(PackFixtureTest.files() + (name to byteArrayOf())))
        }
    }

    @Test
    fun rejectsDuplicateRootPaths() {
        rejected(PackZipMutationTest.rename(PackFixtureTest.bytes(), "examples.json", "manifest.json"))
    }

    @Test
    fun rejectsTraversalAndAbsolutePaths() {
        for (name in listOf("../bad.json", "/idiom.json", "..\\bad.json")) {
            rejected(PackFixtureTest.archive(PackFixtureTest.files() - "idioms.json" + (name to byteArrayOf())))
        }
    }

    @Test
    fun rejectsMissingCentralDirectoryEvenWhenStreamingReaderAcceptsAllThreeFiles() {
        val bytes = PackFixtureTest.bytes().copyOf(PackZipMutationTest.centralOffset(PackFixtureTest.bytes()))
        var streamed = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (zip.nextEntry != null) {
                zip.readBytes()
                streamed++
            }
        }
        assertEquals(3, streamed)
        rejected(bytes)
    }

    @Test
    fun rejectsTruncatedEndAndBody() {
        val bytes = PackFixtureTest.bytes()
        rejected(bytes.copyOf(bytes.size - 1))
        rejected(bytes.copyOf(bytes.size / 2))
        rejected(bytes + byteArrayOf(0))
    }

    @Test
    fun rejectsCrcCorruptionInBothHeaders() {
        rejected(PackZipMutationTest.corruptCrc(PackFixtureTest.bytes()))
    }

    @Test
    fun rejectsDisagreeingCentralAndLocalPaths() {
        val bytes = PackFixtureTest.bytes().clone()
        val offset = PackZipMutationTest.centralOffset(bytes)
        bytes[offset + PackZipMutationTest.CENTRAL_HEADER_BYTES] = 'x'.code.toByte()
        rejected(bytes)
    }

    @Test
    fun callerStreamIsNotClosed() {
        var closed = false
        val input =
            object : ByteArrayInputStream(PackFixtureTest.bytes()) {
                override fun close() {
                    closed = true
                    super.close()
                }
            }
        ValidatedPack.readValidated(input)
        assertTrue(!closed)
    }

    private fun rejected(bytes: ByteArray) {
        assertThrows(Exception::class.java) { PackFixtureTest.validate(bytes) }
    }
}
