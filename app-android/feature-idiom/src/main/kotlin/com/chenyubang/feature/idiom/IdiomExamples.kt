package com.chenyubang.feature.idiom

import com.chenyubang.core.data.ExampleEntity

/**
 * P05「相关题目 · 系统题库」单行展示模型（信息架构 v1.4 P05 ⑧、设计稿 A P05）。
 *
 * 每条含题干摘要 + 年份 · 地区 · 考试类型 + 题型，点击进 P07；缺字段已在
 * [IdiomExamples.viewOf] 里归到「暂无」，页面层不再判空。
 */
data class IdiomExampleView(
    val id: String,
    val stem: String,
    val sourceLine: String,
    val questionType: String,
)

/** 系统真题行取值（纯函数，便于单测）：只做字段呈现，不生成或改写任何题目内容。 */
object IdiomExamples {
    fun viewOf(entity: ExampleEntity): IdiomExampleView =
        IdiomExampleView(
            id = entity.id,
            stem = IdiomDetail.present(entity.stem),
            sourceLine = sourceLine(entity),
            questionType = IdiomDetail.present(entity.questionType),
        )

    /** 年份 · 地区 · 考试类型；缺项跳过，全缺归「暂无」。 */
    fun sourceLine(entity: ExampleEntity): String =
        listOfNotNull(
            entity.year.takeIf { it > 0 }?.toString(),
            presentOrNull(entity.region),
            presentOrNull(entity.examType),
        ).joinToString(" · ").ifBlank { IdiomDetail.PLACEHOLDER }

    private fun presentOrNull(value: String?): String? =
        IdiomDetail.present(value).takeUnless { it == IdiomDetail.PLACEHOLDER }
}
