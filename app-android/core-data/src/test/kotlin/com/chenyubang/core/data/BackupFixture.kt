package com.chenyubang.core.data

import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayOutputStream

/**
 * 备份专项单测的数据桩：全部 id 用合法 UUID 形状，语义与 shared/fixtures/backup/data.json 对齐。
 * 不读 shared/fixtures、不改 schema；用生产编组点 BackupDocument 造 data/manifest，再打包成严格 ZIP。
 */
@Suppress("MagicNumber")
internal object BackupFixture {
    const val IDIOM_1 = "11111111-1111-4111-8111-111111111111"
    const val IDIOM_2 = "22222222-2222-4222-8222-222222222222"
    const val EXAMPLE_A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    const val EXAMPLE_B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
    const val MISTAKE_A = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
    const val MISTAKE_B = "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
    const val REVIEW_A = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
    const val REVIEW_B = "ffffffff-ffff-4fff-8fff-ffffffffffff"
    const val UTC = "2026-11-20T00:00:00Z"
    const val STEM = "下列句子中成语使用恰当的一项是分析题"

    fun mistake(
        id: String = MISTAKE_A,
        userExampleId: String? = EXAMPLE_A,
        idiomIds: List<String> = listOf(IDIOM_1),
        imagePath: String? = "images/incoming.jpg",
    ): BackupMistake =
        BackupMistake(
            id = id,
            userExampleId = userExampleId,
            imagePath = imagePath,
            ocrText = STEM,
            stem = STEM,
            options = BackupOptions("甲选项", "乙选项", "丙选项", "丁选项"),
            myAnswer = "B",
            reason = "搭配不当",
            tagNames = listOf("搭配不当"),
            examType = "国考",
            year = 2023,
            region = "中央",
            state = "待复习",
            createdAt = UTC,
            lastReviewedAt = UTC,
            reviewCount = 1,
            idiomIds = idiomIds,
        )

    fun userExample(
        id: String = EXAMPLE_A,
        idiomIds: List<String> = listOf(IDIOM_1),
        imagePath: String? = "images/incoming.jpg",
    ): BackupUserExample =
        BackupUserExample(
            id = id,
            imagePath = imagePath,
            ocrText = STEM,
            stem = STEM,
            optionA = "甲选项",
            optionB = "乙选项",
            optionC = "丙选项",
            optionD = "丁选项",
            myAnswer = "B",
            isMistake = true,
            examType = "省考",
            year = 2022,
            region = "湖北",
            createdAt = UTC,
            idiomIds = idiomIds,
        )

    fun review(
        id: String = REVIEW_A,
        mistakeId: String = MISTAKE_A,
    ): BackupReview = BackupReview(id, mistakeId, "错", UTC)

    fun idiomState(idiomId: String = IDIOM_1): BackupIdiomState = BackupIdiomState(idiomId, "学过", true)

    fun scope(
        type: String = "国考",
        province: String? = "中央",
    ): BackupScope = BackupScope(type, province, true)

    fun incoming(): BackupSnapshot =
        BackupSnapshot(
            mistakes = listOf(mistake()),
            userExamples = listOf(userExample()),
            reviews = listOf(review()),
            idiomStates = listOf(idiomState()),
            scopes = listOf(scope(), scope("省考", "湖北")),
            tags = listOf(BackupTag("搭配不当")),
        )

    fun data(snapshot: BackupSnapshot): JsonObject = BackupDocument.toJson(snapshot)

    fun manifest(
        snapshot: BackupSnapshot,
        data: JsonObject = data(snapshot),
    ): JsonObject = BackupDocument.manifest(snapshot.counts(), UTC, BackupDocument.sha256(data))

    fun zip(
        data: JsonObject,
        manifest: JsonObject,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        BackupArchive.write(
            output,
            manifest.toString().toByteArray(Charsets.UTF_8),
            data.toString().toByteArray(Charsets.UTF_8),
        )
        return output.toByteArray()
    }

    fun zip(snapshot: BackupSnapshot): ByteArray = zip(data(snapshot), manifest(snapshot))

    fun validated(snapshot: BackupSnapshot): ValidatedBackup {
        val data = data(snapshot)
        return ValidatedBackup(
            schemaVersion = BackupSpec.SCHEMA_VERSION,
            appVersion = BackupSpec.APP_VERSION,
            build = BackupSpec.BUILD,
            exportedAt = UTC,
            counts = snapshot.counts(),
            sha256 = BackupDocument.sha256(data),
            snapshot = snapshot,
        )
    }
}
