package com.chenyubang.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import java.io.File

/** P10 编辑保存输入：只含可改字段；不可改列（ocrText / imagePath / isMistake / 状态与时间）由编辑器原样保留。 */
data class ExampleEditInput(
    val id: String,
    val stem: String,
    val optionA: String?,
    val optionB: String?,
    val optionC: String?,
    val optionD: String?,
    val myAnswer: String?,
    val examType: String?,
    val year: Int?,
    val reason: String?,
    val tagNames: List<String>,
    val idiomIds: List<String>,
)

/** P10 写库失败原因；页面只把 Database / NotFound 归到「保存失败，请重试」，字段级非法在进编辑器前拦截。 */
sealed class ExampleEditError(message: String) : Exception(message) {
    object NotFound : ExampleEditError("题不在库")

    object EmptyStem : ExampleEditError("题干不能为空")

    object NoIdioms : ExampleEditError("至少保留一条归属")

    object InvalidAnswer : ExampleEditError("我的答案只能是 A/B/C/D")

    object InvalidTag : ExampleEditError("标签需为 1–8 个字")

    object InvalidYear : ExampleEditError("年份为 4 位数字")

    object Database : ExampleEditError("写入失败")
}

/**
 * P10 字段级口径（与 P20 复用同一串）：页面就地标红用的是这些判定，编辑器落库前再挡一层。
 * 年份按清单「4 位数字」校验，不套 P20 的 2000–2100 范围。
 */
object ExampleEditRules {
    const val TAG_MIN = 1
    const val TAG_MAX = 8
    private val ANSWERS = setOf("A", "B", "C", "D")
    private val FOUR_DIGITS = Regex("\\d{4}")

    fun answerInvalid(raw: String?): Boolean = normalizedAnswer(raw)?.let { it !in ANSWERS } ?: false

    fun yearInvalid(raw: String?): Boolean {
        val text = raw?.trim().orEmpty()
        return text.isNotEmpty() && !FOUR_DIGITS.matches(text)
    }

    fun tagInvalid(tag: String): Boolean = tag.length !in TAG_MIN..TAG_MAX

    fun normalizedAnswer(raw: String?): String? = raw?.trim()?.uppercase()?.ifEmpty { null }

    fun normalizedYear(raw: String?): Int? = raw?.trim()?.toIntOrNull()

    /** user_example 可空选项：全空格＝缺项写 NULL。 */
    fun option(raw: String?): String? = raw?.trim()?.ifEmpty { null }

    /** mistake 的 v1 非空列：全空格＝缺项写空串，与 UserExampleSaver 同口径。 */
    fun snapshotOption(raw: String?): String = option(raw) ?: ""
}

/**
 * P10 编辑与删除的写库口子（开发规范 v1.7 §8.1 / 交接清单 B3 / B5）。
 *
 * [edit] 一个事务内：写 user_example 正文列 → 刷新该题全部 mistake 的正文快照列与 reason/tagNames
 * → 整批重写 user_example_idiom 与 mistake_idiom（按 id 去重保序、两表顺序一致）。
 * mistake.state / createdAt / lastReviewedAt / reviewCount 与 review_log 不碰；题不在库抛 [ExampleEditError.NotFound] 不落半改。
 *
 * [deleteMarked] 一个事务删该题全部 mistake + mistake_idiom + review_log，并把 user_example.isMistake 归位 false；
 * 不删录入题与原图。[deleteUnmarked] 事务删 user_example + user_example_idiom 后再删本机原图，
 * 原图删不掉只留孤儿文件，不回滚已提交事务。
 */
class UserExampleEditor(
    private val database: AppDatabase,
    private val imagesDirectory: File? = null,
) {
    private val readDao get() = database.userExampleDao()
    private val writeDao get() = database.userExampleEditDao()

    @Suppress("ThrowsCount")
    suspend fun edit(input: ExampleEditInput) {
        validate(input)
        val idioms = deduplicated(input.idiomIds)
        try {
            database.withTransaction {
                if (readDao.byId(input.id) == null) throw ExampleEditError.NotFound
                val options = listOf(input.optionA, input.optionB, input.optionC, input.optionD)
                val normalized = options.map(ExampleEditRules::option)
                val snapshots = options.map(ExampleEditRules::snapshotOption)
                writeDao.updateExampleBody(
                    id = input.id,
                    stem = input.stem.trim(),
                    optionA = normalized[0],
                    optionB = normalized[1],
                    optionC = normalized[2],
                    optionD = normalized[3],
                    myAnswer = ExampleEditRules.normalizedAnswer(input.myAnswer),
                    examType = input.examType?.trim()?.ifEmpty { null },
                    year = input.year,
                )
                writeDao.deleteExampleIdioms(input.id)
                writeDao.insertExampleIdioms(idioms.map { UserExampleIdiomEntity(input.id, it) })
                readDao.mistakesOf(input.id).forEach { mark ->
                    writeDao.updateMistakeBody(
                        id = mark.id,
                        stem = input.stem.trim(),
                        optionA = snapshots[0],
                        optionB = snapshots[1],
                        optionC = snapshots[2],
                        optionD = snapshots[3],
                        myAnswer = ExampleEditRules.normalizedAnswer(input.myAnswer),
                        reason = input.reason?.trim()?.ifEmpty { null },
                        tagNames = input.tagNames,
                        examType = input.examType?.trim()?.ifEmpty { null },
                        year = input.year,
                    )
                    writeDao.deleteMistakeIdioms(mark.id)
                    writeDao.insertMistakeIdioms(idioms.map { MistakeIdiomEntity(mark.id, it) })
                }
            }
        } catch (problem: ExampleEditError) {
            throw problem
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw ExampleEditError.Database
        }
    }

    suspend fun deleteMarked(id: String) {
        try {
            database.withTransaction {
                readDao.mistakesOf(id).forEach { mark ->
                    writeDao.deleteReviewLogsOf(mark.id)
                    writeDao.deleteMistakeIdioms(mark.id)
                    writeDao.deleteMistake(mark.id)
                }
                writeDao.clearMistakeFlag(id)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw ExampleEditError.Database
        }
    }

    suspend fun deleteUnmarked(id: String) {
        try {
            database.withTransaction {
                writeDao.deleteExampleIdioms(id)
                writeDao.deleteExample(id)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw ExampleEditError.Database
        }
        imagesDirectory?.let { runCatching { File(it, "$id.jpg").delete() } }
    }

    @Suppress("ThrowsCount")
    private fun validate(input: ExampleEditInput) {
        if (input.stem.isBlank()) throw ExampleEditError.EmptyStem
        if (input.idiomIds.isEmpty()) throw ExampleEditError.NoIdioms
        if (ExampleEditRules.answerInvalid(input.myAnswer)) throw ExampleEditError.InvalidAnswer
        if (input.tagNames.any(ExampleEditRules::tagInvalid)) throw ExampleEditError.InvalidTag
        if (input.year != null && input.year.toString().length != YEAR_DIGITS) throw ExampleEditError.InvalidYear
    }

    private fun deduplicated(ids: List<String>): List<String> {
        val seen = HashSet<String>()
        return buildList { ids.forEach { if (seen.add(it)) add(it) } }
    }

    private companion object {
        const val YEAR_DIGITS = 4
    }
}
