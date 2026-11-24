package com.chenyubang.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal object PackFixtureTest {
    const val IDIOM_COUNT = 59
    const val EXAMPLE_COUNT = 11
    const val DIGEST = "f0e98e97a47ea92945f042fa5f0d8439ab17a4fddca10941b14089f9203c7fc0"

    fun bytes(): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/pack/pack-v1.0.0.zip")).use { it.readBytes() }

    fun files(): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes()
            }
        }
        return result
    }

    fun archive(files: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    fun json(name: String): JsonElement = PackJson.parse(files().getValue("$name.json"))

    fun validate(bytes: ByteArray = bytes()): ValidatedPack = ValidatedPack.readValidated(ByteArrayInputStream(bytes))

    /** Every semantic mutation recomputes counts and digest, so it actually tests schema/identity boundaries. */
    fun mutate(
        idioms: (JsonArray) -> JsonArray = { it },
        examples: (JsonArray) -> JsonArray = { it },
        manifest: (JsonObject) -> JsonObject = { it },
    ): ByteArray {
        val words = idioms(json("idioms") as JsonArray)
        val questions = examples(json("examples") as JsonArray)
        val metadata =
            (json("manifest") as JsonObject)
                .with(
                    "counts",
                    JsonObject(
                        mapOf("idioms" to JsonPrimitive(words.size), "examples" to JsonPrimitive(questions.size)),
                    ),
                )
                .with("sha256", JsonPrimitive(PackCanonical.digest(words, questions)))
        return archive(
            mapOf(
                "manifest.json" to PackCanonical.text(manifest(metadata)).toByteArray(),
                "idioms.json" to PackCanonical.text(words).toByteArray(),
                "examples.json" to PackCanonical.text(questions).toByteArray(),
            ),
        )
    }

    fun withoutIds(
        ids: Set<String>,
        version: String? = null,
    ): ByteArray =
        mutate(
            idioms = { JsonArray(it.filterNot { row -> (row as JsonObject).string("id") in ids }) },
            examples = { rows ->
                JsonArray(
                    rows.map { element ->
                        val row = element as JsonObject
                        val links = row.getValue("idiomIds") as JsonArray
                        row.with("idiomIds", JsonArray(links.filterNot { (it as JsonPrimitive).content in ids }))
                    }.filter { (it.getValue("idiomIds") as JsonArray).isNotEmpty() },
                )
            },
            manifest = { if (version == null) it else it.with("packVersion", JsonPrimitive(version)) },
        )
}

internal fun JsonObject.with(
    key: String,
    value: JsonElement,
): JsonObject = JsonObject(this + (key to value))

internal fun JsonArray.firstChanged(change: (JsonObject) -> JsonObject): JsonArray =
    JsonArray(mapIndexed { index, row -> if (index == 0) change(row as JsonObject) else row })
