package com.chenyubang.feature.idiom

import com.chenyubang.core.data.ExampleEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P05「系统题库」真题行取值单测（工作包 1.6）。
 *
 * 覆盖：四字段齐全的出处行、缺地区 / 缺年份跳过、全缺归「暂无」、题干与题型缺字段。
 */
class IdiomExamplesTest {
    private fun example(
        stem: String = "依次填入划横线部分最恰当的一项是……",
        year: Int = 2023,
        region: String = "国考",
        examType: String = "言语理解",
        questionType: String = "逻辑填空",
    ) = ExampleEntity(
        id = "example-1",
        stem = stem,
        optionA = "按部就班",
        optionB = "墨守成规",
        optionC = "别出心裁",
        optionD = "标新立异",
        answer = "A",
        explain = "按部就班指按照一定的条理做事。",
        examType = examType,
        year = year,
        region = region,
        questionType = questionType,
    )

    @Test
    fun `字段齐全时出处行按年份地区考试类型拼接`() {
        val view = IdiomExamples.viewOf(example())
        assertEquals("2023 · 国考 · 言语理解", view.sourceLine)
        assertEquals("逻辑填空", view.questionType)
        assertEquals("依次填入划横线部分最恰当的一项是……", view.stem)
    }

    @Test
    fun `缺地区时跳过该段`() {
        val view = IdiomExamples.viewOf(example(region = "  "))
        assertEquals("2023 · 言语理解", view.sourceLine)
    }

    @Test
    fun `缺年份时跳过该段`() {
        val view = IdiomExamples.viewOf(example(year = 0))
        assertEquals("国考 · 言语理解", view.sourceLine)
    }

    @Test
    fun `出处全缺时归暂无`() {
        val view = IdiomExamples.viewOf(example(year = 0, region = "", examType = ""))
        assertEquals(IdiomDetail.PLACEHOLDER, view.sourceLine)
    }

    @Test
    fun `题干与题型缺字段归暂无`() {
        val view = IdiomExamples.viewOf(example(stem = " ", questionType = ""))
        assertEquals(IdiomDetail.PLACEHOLDER, view.stem)
        assertEquals(IdiomDetail.PLACEHOLDER, view.questionType)
    }

    @Test
    fun `保留真题 id 供点击跳转`() {
        assertEquals("example-1", IdiomExamples.viewOf(example()).id)
    }
}
