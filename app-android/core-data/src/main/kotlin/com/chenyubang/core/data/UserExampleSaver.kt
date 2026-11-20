package com.chenyubang.core.data

import kotlinx.coroutines.CancellationException
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** 一条已勾选归属（id + 词，候选顺序；手动增补排末尾）。 */
data class SavedIdiom(
    val id: String,
    val word: String,
)

/** P20 保存输入；题干/选项来自 OcrDraft，归属来自 P20 已勾选集合。 */
data class CaptureSaveInput(
    val imagePath: String,
    val ocrText: String,
    val stem: String,
    val optionA: String? = null,
    val optionB: String? = null,
    val optionC: String? = null,
    val optionD: String? = null,
    val myAnswer: String? = null,
    val isMistake: Boolean,
    val reason: String = "",
    val tagNames: List<String> = emptyList(),
    val examType: String? = null,
    val year: Int? = null,
    val idioms: List<SavedIdiom>,
    val createdAt: String = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
)

/** 保存回执；mistakeId 仅在标错时存在。 */
data class SavedCapture(
    val userExampleId: String,
    val mistakeId: String?,
    val idioms: List<SavedIdiom>,
)

/** 保存失败原因（页面统一提示「保存失败，请重试」）。 */
sealed class CaptureSaveError(message: String) : Exception(message) {
    object NoIdioms : CaptureSaveError("至少勾选一条归属")

    object EmptyStem : CaptureSaveError("题干为空")

    object InvalidAnswer : CaptureSaveError("我的答案只能是 A/B/C/D")

    object InvalidTag : CaptureSaveError("标签需为 1–8 个字")

    object InvalidExamType : CaptureSaveError("考试类型不支持")

    object InvalidYear : CaptureSaveError("年份需在 2000–2100")

    object NoImage : CaptureSaveError("原图缺失")

    object ImageCopyFailed : CaptureSaveError("原图复制失败")

    object Database : CaptureSaveError("写入失败")
}

/**
 * P20 保存入口（规范 §8.1 / 验收第 11 条）：
 * 先复制拍照原图到 filesDir/images/&lt;userExampleId&gt;.jpg，DB 只存相对路径；
 * 再由 [UserExampleDao.persistCapture] 单事务写 user_example + user_example_idiom（全部勾选、至少一条），
 * 仅标错时追加 mistake + mistake_idiom。DB 失败时删除刚复制的图片，不留半条数据或孤儿文件。
 *
 * [newId] / [now] 可注入，便于单测固定 id 与时间戳。
 */
class UserExampleSaver(
    private val dao: UserExampleDao,
    private val imagesDirectory: File,
    private val newId: () -> String = { UUID.randomUUID().toString().lowercase() },
    private val now: () -> String = { Instant.now().truncatedTo(ChronoUnit.SECONDS).toString() },
) {
    suspend fun save(input: CaptureSaveInput): SavedCapture {
        val idioms = deduplicated(input.idioms)
        val source = File(input.imagePath)
        validate(input, idioms, source)?.let { throw it }
        val userExampleId = newId()
        val mistakeId = if (input.isMistake) newId() else null
        val target = File(imagesDirectory, "$userExampleId.jpg")
        if (!copyImage(source, target)) throw CaptureSaveError.ImageCopyFailed
        return persistAndCleanup(input, idioms, target, userExampleId, mistakeId)
    }

    /** 所有前置校验集中在此，返回首个错误由调用方统一抛出，避免 save 内多 throw。 */
    private fun validate(
        input: CaptureSaveInput,
        idioms: List<SavedIdiom>,
        source: File,
    ): CaptureSaveError? =
        when {
            idioms.isEmpty() || idioms.any { it.id.isBlank() || it.word.isBlank() } -> CaptureSaveError.NoIdioms
            input.stem.isBlank() -> CaptureSaveError.EmptyStem
            input.myAnswer != null && input.myAnswer !in ANSWER_KEYS -> CaptureSaveError.InvalidAnswer
            input.isMistake && input.tagNames.any { it.length !in TAG_MIN_LENGTH..TAG_MAX_LENGTH } ->
                CaptureSaveError.InvalidTag
            input.examType != null && input.examType !in EXAM_TYPES -> CaptureSaveError.InvalidExamType
            input.year != null && input.year !in YEAR_MIN..YEAR_MAX -> CaptureSaveError.InvalidYear
            !source.exists() || !source.isFile -> CaptureSaveError.NoImage
            else -> null
        }

    /** 单事务落库；取消或失败都删除刚复制的图片，不留半条数据或孤儿文件。 */
    private suspend fun persistAndCleanup(
        input: CaptureSaveInput,
        idioms: List<SavedIdiom>,
        target: File,
        userExampleId: String,
        mistakeId: String?,
    ): SavedCapture =
        try {
            val relativePath = "images/$userExampleId.jpg"
            val createdAt = input.createdAt.ifBlank { now() }
            dao.persistCapture(
                userExample = buildUserExample(input, userExampleId, relativePath, createdAt),
                idioms = idioms.map { it.id },
                mistake = buildMistake(input, userExampleId, mistakeId, relativePath, createdAt),
            )
            SavedCapture(userExampleId, mistakeId, idioms)
        } catch (cancelled: CancellationException) {
            target.delete()
            throw cancelled
        } catch (_: Exception) {
            target.delete()
            throw CaptureSaveError.Database
        }

    private fun buildUserExample(
        input: CaptureSaveInput,
        id: String,
        relativePath: String,
        createdAt: String,
    ) = UserExampleEntity(
        id = id,
        imagePath = relativePath,
        ocrText = input.ocrText.ifBlank { null },
        stem = input.stem.trim(),
        optionA = input.optionA?.trim()?.ifEmpty { null },
        optionB = input.optionB?.trim()?.ifEmpty { null },
        optionC = input.optionC?.trim()?.ifEmpty { null },
        optionD = input.optionD?.trim()?.ifEmpty { null },
        myAnswer = input.myAnswer,
        isMistake = input.isMistake,
        examType = input.examType,
        year = input.year,
        region = null,
        createdAt = createdAt,
    )

    private fun buildMistake(
        input: CaptureSaveInput,
        userExampleId: String,
        id: String?,
        relativePath: String,
        createdAt: String,
    ): MistakeEntity? =
        id?.let {
            MistakeEntity(
                id = it,
                userExampleId = userExampleId,
                imagePath = relativePath,
                ocrText = input.ocrText.ifBlank { null },
                stem = input.stem.trim(),
                optionA = input.optionA ?: "",
                optionB = input.optionB ?: "",
                optionC = input.optionC ?: "",
                optionD = input.optionD ?: "",
                myAnswer = input.myAnswer,
                reason = input.reason.trim().ifEmpty { null },
                tagNames = input.tagNames,
                examType = input.examType,
                year = input.year,
                region = null,
                state = STATE_PENDING,
                createdAt = createdAt,
                reviewCount = 0,
            )
        }

    private fun deduplicated(idioms: List<SavedIdiom>): List<SavedIdiom> {
        val seen = HashSet<String>()
        return buildList {
            idioms.forEach { idiom -> if (seen.add(idiom.id)) add(idiom) }
        }
    }

    private fun copyImage(
        source: File,
        target: File,
    ): Boolean =
        runCatching {
            imagesDirectory.mkdirs()
            if (target.exists()) target.delete()
            source.copyTo(target, overwrite = true)
        }.isSuccess

    private companion object {
        val ANSWER_KEYS = setOf("A", "B", "C", "D")
        val EXAM_TYPES = setOf("国考", "省考", "事业编")
        const val TAG_MIN_LENGTH = 1
        const val TAG_MAX_LENGTH = 8
        const val YEAR_MIN = 2000
        const val YEAR_MAX = 2100
        const val STATE_PENDING = "待复习"
    }
}
