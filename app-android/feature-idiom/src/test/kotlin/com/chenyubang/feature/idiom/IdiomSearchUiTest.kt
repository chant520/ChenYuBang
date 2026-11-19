package com.chenyubang.feature.idiom

import com.chenyubang.core.data.IdiomEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P04 四种输入分类、结果映射与查无结果空态判定（A3，验收第 3 条）。纯 JVM 单测。 */
class IdiomSearchUiTest {
    @Test
    fun fourInputKindsAreClassified() {
        assertEquals(SearchInputKind.Empty, IdiomSearchUi.classify(""))
        assertEquals(SearchInputKind.Empty, IdiomSearchUi.classify("   "))
        assertEquals(SearchInputKind.HanSingle, IdiomSearchUi.classify("按"))
        assertEquals(SearchInputKind.HanPhrase, IdiomSearchUi.classify("按部就班"))
        assertEquals(SearchInputKind.Roman, IdiomSearchUi.classify("anbujiuban"))
        assertEquals(SearchInputKind.Roman, IdiomSearchUi.classify("abjb"))
    }

    @Test
    fun resultRowCarriesWordPinyinAndSenseFirstSentence() {
        val rows = IdiomSearchUi.from(listOf(entity()))

        assertEquals(1, rows.size)
        assertEquals("按部就班", rows[0].word)
        assertEquals("àn bù jiù bān", rows[0].pinyin)
        assertEquals("原指安排事务依照一定条理。", rows[0].senseFirst)
    }

    @Test
    fun noResultOnlyWhenQueryPresentAndRowsEmpty() {
        assertTrue(IdiomSearchUi.showNoResult("zzzz", emptyList()))
        assertTrue(IdiomSearchUi.showNoResult("不存在", emptyList()))
        assertFalse(IdiomSearchUi.showNoResult("", emptyList()))
        assertFalse(IdiomSearchUi.showNoResult("按", IdiomSearchUi.from(listOf(entity()))))
    }

    private fun entity() =
        IdiomEntity(
            id = "11111111-1111-4111-8111-111111111111",
            word = "按部就班",
            pinyin = "àn bù jiù bān",
            initials = "abjb",
            sense = "原指安排事务依照一定条理。后多指照老规矩办事。",
            color = "贬义",
            frequency = 5,
        )
}
