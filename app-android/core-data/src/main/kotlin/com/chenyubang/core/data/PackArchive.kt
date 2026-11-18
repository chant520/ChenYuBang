package com.chenyubang.core.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

/** No filesystem extraction, ZIP64, encryption, hidden records, or unbounded reads. */
internal object PackArchive {
    const val MAX_INPUT_BYTES = 4 * 1024 * 1024
    const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
    const val MAX_EXPANDED_BYTES = 16 * 1024 * 1024
    val names = setOf("manifest.json", "idioms.json", "examples.json")
    private const val BUFFER_BYTES = 8192

    fun read(input: InputStream): Map<String, ByteArray> {
        val archive = boundedRead(input, MAX_INPUT_BYTES)
        val directory = PackZipDirectory(archive).entries()
        val files = linkedMapOf<String, ByteArray>()
        var remaining = MAX_EXPANDED_BYTES
        ZipInputStream(ByteArrayInputStream(archive), Charsets.UTF_8).use { zip ->
            directory.forEach { expected ->
                val entry = requireNotNull(zip.nextEntry) { "Missing ZIP local entry" }
                require(!entry.isDirectory && entry.name == expected.name) { "ZIP directory mismatch" }
                val bytes = boundedRead(zip, minOf(MAX_ENTRY_BYTES, remaining))
                remaining -= bytes.size
                val crc = CRC32().apply { update(bytes) }.value
                require(bytes.size.toLong() == expected.size && crc == expected.crc) { "ZIP size/CRC mismatch" }
                files[expected.name] = bytes
                zip.closeEntry()
            }
            require(zip.nextEntry == null) { "Extra ZIP local entry" }
        }
        return files
    }

    private fun boundedRead(
        input: InputStream,
        limit: Int,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            // Read at most one byte beyond the limit; reject before storing it.
            val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
            if (count == -1) return output.toByteArray()
            if (count == 0) {
                val byte = input.read()
                if (byte == -1) break
                require(output.size() < limit) { "Pack byte limit" }
                output.write(byte)
            } else {
                require(count <= limit - output.size()) { "Pack byte limit" }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }
}

internal data class PackZipEntry(
    val name: String,
    val flags: Int,
    val method: Int,
    val crc: Long,
    val compressed: Long,
    val size: Long,
    val offset: Int,
)

private const val EOCD_BYTES = 22
private const val CENTRAL_BYTES = 46
private const val LOCAL_BYTES = 30
private const val EOCD_SIGNATURE = 0x06054b50L
private const val CENTRAL_SIGNATURE = 0x02014b50L
private const val LOCAL_SIGNATURE = 0x04034b50L
private const val DESCRIPTOR_SIGNATURE = 0x08074b50L
private const val DESCRIPTOR_BYTES = 12
private const val SIGNATURE_BYTES = 4
private const val DEFLATED_METHOD = 8
private const val DESCRIPTOR_FLAG = 8
private const val ALLOWED_FLAGS = 0x080e
private const val DIRECTORY_ATTRIBUTE = 0x10L
private const val UNIX_TYPE_MASK = 0xf000L
private const val UNIX_DIRECTORY = 0x4000L
private const val WORD_SHIFT = 16
private const val BYTE_BITS = 8
private const val BYTE_MASK = 0xff

private object ZipOffsets {
    const val END_DISK = 4
    const val END_CENTRAL_DISK = 6
    const val END_DISK_ENTRIES = 8
    const val END_ENTRIES = 10
    const val END_SIZE = 12
    const val END_OFFSET = 16
    const val END_COMMENT = 20
    const val CENTRAL_FLAGS = 8
    const val CENTRAL_METHOD = 10
    const val CENTRAL_CRC = 16
    const val CENTRAL_COMPRESSED = 20
    const val CENTRAL_SIZE = 24
    const val CENTRAL_NAME = 28
    const val CENTRAL_EXTRA = 30
    const val CENTRAL_COMMENT = 32
    const val CENTRAL_DISK = 34
    const val CENTRAL_ATTRIBUTES = 38
    const val CENTRAL_OFFSET = 42
    const val LOCAL_FLAGS = 6
    const val LOCAL_METHOD = 8
    const val LOCAL_CRC = 14
    const val LOCAL_COMPRESSED = 18
    const val LOCAL_SIZE = 22
    const val LOCAL_NAME = 26
    const val LOCAL_EXTRA = 28
    const val DESCRIPTOR_COMPRESSED = 4
    const val DESCRIPTOR_SIZE = 8
}

/** ZipInputStream alone accepts a ZIP truncated before its central directory. Verify both views first. */
private class PackZipDirectory(private val bytes: ByteArray) {
    fun entries(): List<PackZipEntry> {
        val end = bytes.size - EOCD_BYTES
        require(end >= 0 && uint(end) == EOCD_SIGNATURE) { "Missing ZIP EOCD (comments unsupported)" }
        require(ushort(end + ZipOffsets.END_DISK) == 0 && ushort(end + ZipOffsets.END_CENTRAL_DISK) == 0) {
            "Multidisk ZIP unsupported"
        }
        require(
            ushort(end + ZipOffsets.END_DISK_ENTRIES) == PackArchive.names.size &&
                ushort(end + ZipOffsets.END_ENTRIES) == PackArchive.names.size,
        ) { "ZIP must have exactly three entries" }
        require(ushort(end + ZipOffsets.END_COMMENT) == 0) { "ZIP comment unsupported" }
        val start = uint(end + ZipOffsets.END_OFFSET).toInt()
        require(
            start >= 0 && uint(end + ZipOffsets.END_SIZE) + start == end.toLong(),
        ) { "ZIP central directory bounds" }
        val records = centralEntries(start, end).sortedBy { it.offset }
        require(records.map { it.name }.toSet() == PackArchive.names) { "Duplicate/extra ZIP paths" }
        var next = 0
        records.forEach { entry ->
            require(entry.offset == next) { "ZIP local overlap, gap, or prefix" }
            next = localEnd(entry)
        }
        require(next == start) { "ZIP local/central directory mismatch" }
        return records
    }

    private fun centralEntries(
        start: Int,
        end: Int,
    ): List<PackZipEntry> {
        var position = start
        val result = mutableListOf<PackZipEntry>()
        repeat(PackArchive.names.size) {
            require(uint(position) == CENTRAL_SIGNATURE) { "Invalid ZIP central entry" }
            val length = ushort(position + ZipOffsets.CENTRAL_NAME)
            val extra = ushort(position + ZipOffsets.CENTRAL_EXTRA)
            val comment = ushort(position + ZipOffsets.CENTRAL_COMMENT)
            require(ushort(position + ZipOffsets.CENTRAL_DISK) == 0) { "Multidisk ZIP entry" }
            val attributes = uint(position + ZipOffsets.CENTRAL_ATTRIBUTES)
            require(attributes and DIRECTORY_ATTRIBUTE == 0L) { "ZIP directory rejected" }
            require((attributes shr WORD_SHIFT) and UNIX_TYPE_MASK != UNIX_DIRECTORY) { "ZIP directory rejected" }
            val entry =
                PackZipEntry(
                    name = name(position + CENTRAL_BYTES, length),
                    flags = ushort(position + ZipOffsets.CENTRAL_FLAGS),
                    method = ushort(position + ZipOffsets.CENTRAL_METHOD),
                    crc = uint(position + ZipOffsets.CENTRAL_CRC),
                    compressed = uint(position + ZipOffsets.CENTRAL_COMPRESSED),
                    size = uint(position + ZipOffsets.CENTRAL_SIZE),
                    offset = uint(position + ZipOffsets.CENTRAL_OFFSET).toInt(),
                )
            require(entry.flags and ALLOWED_FLAGS.inv() == 0) { "Unsupported ZIP flags" }
            require(entry.method == 0 || entry.method == DEFLATED_METHOD) { "Unsupported ZIP method" }
            require(entry.size <= PackArchive.MAX_ENTRY_BYTES && entry.compressed <= PackArchive.MAX_INPUT_BYTES) {
                "ZIP entry byte limit"
            }
            result += entry
            position += CENTRAL_BYTES + length + extra + comment
            require(position <= end) { "Truncated ZIP central entry" }
        }
        require(position == end) { "ZIP central directory extra records" }
        require(result.sumOf { it.size } <= PackArchive.MAX_EXPANDED_BYTES) { "ZIP expanded byte limit" }
        return result
    }

    private fun localEnd(entry: PackZipEntry): Int {
        val offset = entry.offset
        require(uint(offset) == LOCAL_SIGNATURE) { "Invalid ZIP local header" }
        require(
            ushort(offset + ZipOffsets.LOCAL_FLAGS) == entry.flags &&
                ushort(offset + ZipOffsets.LOCAL_METHOD) == entry.method,
        ) { "ZIP flags/method mismatch" }
        val length = ushort(offset + ZipOffsets.LOCAL_NAME)
        val extra = ushort(offset + ZipOffsets.LOCAL_EXTRA)
        require(name(offset + LOCAL_BYTES, length) == entry.name) { "ZIP path mismatch" }
        val descriptor = entry.flags and DESCRIPTOR_FLAG != 0
        localSize(uint(offset + ZipOffsets.LOCAL_CRC), entry.crc, descriptor)
        localSize(uint(offset + ZipOffsets.LOCAL_COMPRESSED), entry.compressed, descriptor)
        localSize(uint(offset + ZipOffsets.LOCAL_SIZE), entry.size, descriptor)
        val end = offset.toLong() + LOCAL_BYTES + length + extra + entry.compressed
        require(end in 0..bytes.size.toLong()) { "Truncated ZIP body" }
        return if (descriptor) descriptorEnd(end.toInt(), entry) else end.toInt()
    }

    private fun descriptorEnd(
        offset: Int,
        entry: PackZipEntry,
    ): Int {
        val start = if (uint(offset) == DESCRIPTOR_SIGNATURE) offset + SIGNATURE_BYTES else offset
        require(uint(start) == entry.crc) { "ZIP descriptor CRC mismatch" }
        require(
            uint(start + ZipOffsets.DESCRIPTOR_COMPRESSED) == entry.compressed &&
                uint(start + ZipOffsets.DESCRIPTOR_SIZE) == entry.size,
        ) { "ZIP descriptor size mismatch" }
        return start + DESCRIPTOR_BYTES
    }

    private fun localSize(
        actual: Long,
        expected: Long,
        descriptor: Boolean,
    ) {
        require(actual == expected || (descriptor && actual == 0L)) { "ZIP local/central size or CRC mismatch" }
    }

    private fun name(
        offset: Int,
        length: Int,
    ): String {
        require(offset >= 0 && offset.toLong() + length <= bytes.size) { "Truncated ZIP name" }
        val result = bytes.copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)
        require(result in PackArchive.names) { "ZIP path rejected" }
        return result
    }

    private fun ushort(offset: Int): Int = uintBytes(offset, 2).toInt()

    private fun uint(offset: Int): Long = uintBytes(offset, SIGNATURE_BYTES)

    private fun uintBytes(
        offset: Int,
        count: Int,
    ): Long {
        require(offset >= 0 && offset.toLong() + count <= bytes.size) { "Truncated ZIP record" }
        var result = 0L
        repeat(count) { index ->
            result = result or ((bytes[offset + index].toInt() and BYTE_MASK).toLong() shl (index * BYTE_BITS))
        }
        return result
    }
}
