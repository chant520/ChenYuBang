package com.chenyubang.core.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object PackZipMutationTest {
    const val CENTRAL_HEADER_BYTES = 46
    private const val EOCD_BYTES = 22
    private const val DIRECTORY_OFFSET = 16
    private const val NAME_LENGTH = 28
    private const val EXTRA_LENGTH = 30
    private const val COMMENT_LENGTH = 32
    private const val LOCAL_OFFSET = 42
    private const val CRC_OFFSET = 16
    private const val LOCAL_CRC_OFFSET = 14
    private const val SIZE_OFFSET = 24
    private const val LOCAL_SIZE_OFFSET = 22
    private const val LOCAL_HEADER_BYTES = 30
    private const val LOCAL_NAME_LENGTH = 26
    private const val LOCAL_EXTRA_LENGTH = 28
    private const val COMPRESSED_OFFSET = 20
    private const val FLAGS_OFFSET = 8
    private const val DESCRIPTOR_FLAG = 8
    private const val SIGNATURE_BYTES = 4
    private const val DESCRIPTOR_SIZE_OFFSET = 8
    private const val DESCRIPTOR_SIGNATURE = 0x08074b50
    private const val WORD_MASK = 0xffff

    fun centralOffset(bytes: ByteArray): Int = buffer(bytes).getInt(bytes.size - EOCD_BYTES + DIRECTORY_OFFSET)

    fun rename(
        bytes: ByteArray,
        old: String,
        new: String,
    ): ByteArray {
        require(old.length == new.length)
        val result = bytes.clone()
        val needle = old.toByteArray()
        for (index in 0..result.size - needle.size) {
            if (needle.indices.all { result[index + it] == needle[it] }) {
                new.toByteArray().copyInto(result, index)
            }
        }
        return result
    }

    fun corruptCrc(bytes: ByteArray): ByteArray {
        val result = bytes.clone()
        val data = buffer(result)
        val central = centralOffset(bytes)
        val local = data.getInt(central + LOCAL_OFFSET)
        val wrong = data.getInt(central + CRC_OFFSET) xor 1
        data.putInt(central + CRC_OFFSET, wrong)
        data.putInt(local + LOCAL_CRC_OFFSET, wrong)
        return result
    }

    fun declaredSize(
        bytes: ByteArray,
        name: String,
        size: Int,
    ): ByteArray {
        val result = bytes.clone()
        val data = buffer(result)
        val central =
            records(bytes).first { position ->
                val length = unsignedShort(data, position + NAME_LENGTH)
                result.copyOfRange(position + CENTRAL_HEADER_BYTES, position + CENTRAL_HEADER_BYTES + length)
                    .toString(Charsets.US_ASCII) == name
            }
        val local = data.getInt(central + LOCAL_OFFSET)
        data.putInt(central + SIZE_OFFSET, size)
        if (unsignedShort(data, central + FLAGS_OFFSET) and DESCRIPTOR_FLAG == 0) {
            data.putInt(local + LOCAL_SIZE_OFFSET, size)
        } else {
            val end =
                local + LOCAL_HEADER_BYTES + unsignedShort(data, local + LOCAL_NAME_LENGTH) +
                    unsignedShort(data, local + LOCAL_EXTRA_LENGTH) + data.getInt(central + COMPRESSED_OFFSET)
            val descriptor = if (data.getInt(end) == DESCRIPTOR_SIGNATURE) end + SIGNATURE_BYTES else end
            data.putInt(descriptor + DESCRIPTOR_SIZE_OFFSET, size)
        }
        return result
    }

    private fun records(bytes: ByteArray): List<Int> {
        var position = centralOffset(bytes)
        val data = buffer(bytes)
        return List(PackArchive.names.size) {
            val start = position
            position += CENTRAL_HEADER_BYTES + unsignedShort(data, start + NAME_LENGTH) +
                unsignedShort(data, start + EXTRA_LENGTH) + unsignedShort(data, start + COMMENT_LENGTH)
            start
        }
    }

    private fun unsignedShort(
        buffer: ByteBuffer,
        offset: Int,
    ): Int = buffer.getShort(offset).toInt() and WORD_MASK
}

internal fun buffer(bytes: ByteArray): ByteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
