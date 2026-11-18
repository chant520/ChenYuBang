package com.chenyubang.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.InputStream

data class PackCounts(val idioms: Int, val examples: Int)

data class PackImportResult(
    val packVersion: String,
    val schemaVersion: Int,
    val builtAt: String,
    val counts: PackCounts,
    val sha256: String,
    val retainedIdioms: Int,
)

/** No public constructor or copy: the only creation path reads and validates the complete input. */
class ValidatedPack private constructor(
    manifest: JsonObject,
    internal val idioms: JsonArray,
    internal val examples: JsonArray,
) {
    val packVersion: String = manifest.string("packVersion")
    val schemaVersion: Int = manifest.integer("schemaVersion")
    val builtAt: String = manifest.string("builtAt")
    val counts: PackCounts = PackCounts(idioms.size, examples.size)
    val sha256: String = manifest.string("sha256")

    companion object {
        internal fun readValidated(input: InputStream): ValidatedPack {
            val files = PackArchive.read(input)
            val metadata = PackJson.parse(files.getValue("manifest.json"))
            val idioms = PackJson.parse(files.getValue("idioms.json"))
            val examples = PackJson.parse(files.getValue("examples.json"))
            PackSchema.validate(metadata, idioms, examples)
            val manifest = metadata as JsonObject
            val words = idioms as JsonArray
            val questions = examples as JsonArray
            val expected = manifest.getValue("counts") as JsonObject
            val counts = PackCounts(words.size, questions.size)
            require(expected.integer("idioms") == counts.idioms && expected.integer("examples") == counts.examples) {
                "Pack manifest counts mismatch"
            }
            val digest = PackCanonical.digest(words, questions)
            require(manifest.string("sha256") == digest) { "Pack digest mismatch" }
            return ValidatedPack(manifest, words, questions)
        }
    }
}
