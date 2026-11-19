package com.chenyubang.feature.example

import com.chenyubang.core.data.ExampleEntity

/**
 * P07 例题详情展示模型（信息架构 v1.4 P07 ①–⑧）。
 *
 * 页面组成严格按 IA v1.4：顶部区（返回、页名「例题详情」、标记预留位）、出处行
 * （②年份·地区·考试类型·题型）、题干、四选项、正确答案+一句解析、关联成语区。
 * 题干/选项/解析里出现的成语会在 UI 层经 [IdiomHighlight] 高亮并点击跳 P05（验收 5），
 * 本题承载多个成语（B3 一题多成语）时关联成语区并排展示全部（验收 6）。
 *
 * 缺字段一律用 [ExampleDetail.PLACEHOLDER]（与 IdiomDetail.PLACEHOLDER 同口径）。
 */
data class ExampleDetailView(
    val id: String,
    val stem: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val answer: String,
    val explain: String,
    /** 出处行：「年份 · 地区 · 考试类型 · 题型」。 */
    val sourceLine: String,
)

/** P07 展示模型取值 / 缺字段判定（纯函数，便于单测；不依赖 Activity / Room）。 */
object ExampleDetail {
    /** 缺字段统一占位，与 IdiomDetail.PLACEHOLDER 同口径（开发规范 §5 约定「暂无」）。 */
    const val PLACEHOLDER = "暂无"

    fun viewOf(entity: ExampleEntity): ExampleDetailView =
        ExampleDetailView(
            id = entity.id,
            stem = present(entity.stem),
            optionA = present(entity.optionA),
            optionB = present(entity.optionB),
            optionC = present(entity.optionC),
            optionD = present(entity.optionD),
            answer = present(entity.answer),
            explain = present(entity.explain),
            sourceLine = sourceLine(entity),
        )

    /** 空串 / 纯空白 / null 一律归 [PLACEHOLDER]。 */
    fun present(value: String?): String = value?.trim()?.takeIf { it.isNotEmpty() } ?: PLACEHOLDER

    /**
     * 出处行「②年份·地区·考试类型·题型」：逐项有值才拼接，缺项跳过；
     * 全部缺失时为 [PLACEHOLDER]。
     */
    fun sourceLine(entity: ExampleEntity): String {
        val year = entity.year.takeIf { it > 0 }?.toString()
        val parts =
            listOfNotNull(year, present(entity.region), present(entity.examType), present(entity.questionType))
                .filter { it != PLACEHOLDER }
        return parts.joinToString(" · ").ifBlank { PLACEHOLDER }
    }
}
