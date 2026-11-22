package com.chenyubang.feature.mistake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P10 编辑态纯逻辑：字段级置灰、页面级提示、草稿归一、归属增删保序、保存/取消不落库口径。 */
class P10EditLogicTest {
    private fun detail(isMistake: Boolean = true) =
        P10UserExampleDetail(
            id = "ue-1",
            stem = "题干",
            options = listOf("甲", null, "丙", "丁"),
            myAnswer = "B",
            isMistake = isMistake,
            examType = "国考",
            year = 2025,
            reason = "错因",
            tagNames = listOf("标签"),
            idioms = listOf(P10IdiomRef("i1", "按部就班"), P10IdiomRef("i2", "别出心裁")),
            imagePath = null,
        )

    private fun draft(isMistake: Boolean = true) = P10EditTransitions.draftOf(detail(isMistake))

    @Test
    fun draftOfMapsOnlyEditableFieldsAndKeepsReadonlyImage() {
        val d = draft()
        assertEquals("题干", d.stem)
        assertEquals(listOf("甲", "", "丙", "丁"), d.options)
        assertEquals("B", d.answer)
        assertEquals(listOf("i1", "i2"), d.idiomIds)
        assertEquals("2025", d.year)
        assertEquals("国考", d.examType)
        assertEquals("错因", d.reason)
        assertEquals(listOf("标签"), d.tags)
    }

    @Test
    fun fieldErrorsCoverAnswerYearAndTagOnly() {
        assertFalse(P10EditTransitions.fieldErrors(draft()).hasInvalid)
        assertTrue(P10EditTransitions.fieldErrors(draft().copy(answer = "E")).answer)
        assertTrue(P10EditTransitions.fieldErrors(draft().copy(year = "20")).year)
        assertTrue(P10EditTransitions.fieldErrors(draft().copy(tagDraft = "九个字的超长标签内容啊")).tag)
        assertEquals("年份为 4 位数字", P10EditTransitions.HINT_YEAR)
        assertEquals("标签需为 1–8 个字", P10EditTransitions.HINT_TAG)
    }

    @Test
    fun pageMessageOnlyAppearsForBlankStemOrEmptyIdioms() {
        assertEquals(P10EditTransitions.EMPTY_STEM, P10EditTransitions.pageMessage(draft().copy(stem = "   ")))
        assertEquals(P10EditTransitions.NO_IDIOM, P10EditTransitions.pageMessage(draft().copy(idiomIds = emptyList())))
        assertNull(P10EditTransitions.pageMessage(draft()))
    }

    @Test
    fun addTagRejectsInvalidLengthAndDuplicates() {
        assertEquals(listOf("标签", "高频"), P10EditTransitions.addTag(draft().copy(tagDraft = " 高频 ")).tags)
        assertEquals("标签", P10EditTransitions.addTag(draft().copy(tagDraft = "标签")).tags.single())
        assertEquals(
            listOf("标签"),
            P10EditTransitions.addTag(draft().copy(tagDraft = "九个字的超长标签内容啊")).tags,
        )
    }

    @Test
    fun idiomsDeduplicateKeepOrderAndNextFillsMissing() {
        val candidates = listOf(P10IdiomRef("i3", "望文生义"), P10IdiomRef("i1", "按部就班"))
        assertEquals(listOf("i1", "i2", "i3"), P10EditTransitions.addIdiom(draft(), "i3").idiomIds)
        assertEquals(listOf("i1", "i2"), P10EditTransitions.addIdiom(draft(), "i1").idiomIds)
        assertEquals(listOf("i1", "i2", "i3"), P10EditTransitions.nextIdiom(draft(), candidates).idiomIds)
    }

    @Test
    fun toInputTrimsOptionsAndNormalizesAnswerYear() {
        val changed =
            draft().copy(
                stem = " 新题 ",
                options = listOf(" 甲 ", "  ", "丙", "丁"),
                answer = " d ",
                year = "2026",
            )
        val input = P10EditTransitions.toInput("ue-1", changed)
        assertEquals("新题", input.stem)
        assertEquals("甲", input.optionA)
        assertNull(input.optionB)
        assertEquals("D", input.myAnswer)
        assertEquals(2026, input.year)
    }

    @Test
    fun changeRecomputesErrorsAndKeepsDraftInPlace() {
        val edit = P10EditState(editing = true, draft = draft())
        val invalid = P10EditTransitions.change(edit) { it.copy(year = "20") }
        assertTrue(invalid.errors.year)
        assertEquals("20", invalid.draft!!.year)
        val valid = P10EditTransitions.change(invalid) { it.copy(year = "2026") }
        assertFalse(valid.errors.year)
        assertEquals("2026", valid.draft!!.year)
    }
}
