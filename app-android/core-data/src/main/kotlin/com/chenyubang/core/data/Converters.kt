package com.chenyubang.core.data

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** List<String> ↔ JSON 文本，用于 mistake.tagNames 等列。 */
class Converters {
    private val json = Json { encodeDefaults = false }

    @TypeConverter
    fun fromList(value: List<String>): String = json.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun toList(value: String): List<String> =
        if (value.isBlank()) {
            emptyList()
        } else {
            json.decodeFromString(ListSerializer(String.serializer()), value)
        }
}
