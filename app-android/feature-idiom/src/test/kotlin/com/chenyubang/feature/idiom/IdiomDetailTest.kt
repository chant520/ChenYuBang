package com.chenyubang.feature.idiom

import com.chenyubang.core.data.IdiomEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P05 五项取值 / 缺字段「暂无」判定 / JSON 数组解析（A2，验收第 2 条）。纯 JVM 单测。 */
class IdiomDetailTest {
    @Test
    fun allFiveFieldsPresent() {
        val view = IdiomDetail.viewOf(fullEntity())

        assertEquals("按部就班", view.word)
        assertEquals("àn bù jiù bān", view.pinyin)
        assertEquals("贬义", view.color)
        assertTrue(view.sense.contains("照老规矩办事"))
        assertEquals(listOf("望文生义：常被误解为有序完成", "对象误用：只形容做事方式"), view.pitfalls)
        assertEquals("墨守成规", view.synonyms.single())
        assertEquals("别出心裁", view.antonyms.single())
    }

    @Test
    fun missingFieldsBecomePlaceholderNotBlank() {
        val view =
            IdiomDetail.viewOf(
                fullEntity().copy(
                    pinyin = "  ",
                    sense = "",
                    color = "",
                    origin = null,
                    pitfalls = "[]",
                    synonyms = "",
                    antonyms = "null",
                ),
            )

        assertEquals(IdiomDetail.PLACEHOLDER, view.pinyin)
        assertEquals(IdiomDetail.PLACEHOLDER, view.sense)
        assertEquals(IdiomDetail.PLACEHOLDER, view.color)
        assertEquals(IdiomDetail.PLACEHOLDER, view.origin)
        assertEquals(IdiomDetail.PLACEHOLDER, IdiomDetail.presentList(view.pitfalls))
        assertEquals(IdiomDetail.PLACEHOLDER, IdiomDetail.presentList(view.synonyms))
        assertEquals(IdiomDetail.PLACEHOLDER, IdiomDetail.presentList(view.antonyms))
    }

    @Test
    fun colorNormalizesToThreeCategories() {
        assertEquals("褒义", IdiomDetail.colorText("褒"))
        assertEquals("褒义", IdiomDetail.colorText("褒义"))
        assertEquals("贬义", IdiomDetail.colorText("贬"))
        assertEquals("中性", IdiomDetail.colorText("中"))
        assertEquals(IdiomDetail.PLACEHOLDER, IdiomDetail.colorText(""))
    }

    @Test
    fun jsonArrayWithCommaInsideItemIsNotSplit() {
        val items = IdiomDetail.parseJsonStringArray("[\"对象误用：A, B 不能混用\",\"望文生义：C\"]")

        assertEquals(2, items.size)
        assertEquals("对象误用：A, B 不能混用", items[0])
        assertEquals("望文生义：C", items[1])
    }

    @Test
    fun emptyAndNonArrayInputs() {
        assertTrue(IdiomDetail.parseJsonStringArray(null).isEmpty())
        assertTrue(IdiomDetail.parseJsonStringArray("").isEmpty())
        assertTrue(IdiomDetail.parseJsonStringArray("[]").isEmpty())
        assertTrue(IdiomDetail.parseJsonStringArray("null").isEmpty())
        assertEquals(listOf("单条文本"), IdiomDetail.parseJsonStringArray("单条文本"))
    }

    @Test
    fun firstSentenceKeepsTerminalPunctuation() {
        assertEquals(
            "原指安排事务依照一定条理。",
            IdiomDetail.firstSentence("原指安排事务依照一定条理。后多指照老规矩办事。"),
        )
        assertEquals("无句读的整段", IdiomDetail.firstSentence("无句读的整段"))
        assertEquals(IdiomDetail.PLACEHOLDER, IdiomDetail.firstSentence(""))
    }

    private fun fullEntity() =
        IdiomEntity(
            id = "11111111-1111-4111-8111-111111111111",
            word = "按部就班",
            pinyin = "àn bù jiù bān",
            initials = "abjb",
            sense = "按：依照；部、班：门类与次序。原指安排事务依照一定条理，后多指照老规矩办事。",
            origin = "清·李宝嘉《官场现形记》",
            color = "贬义",
            pitfalls = "[\"望文生义：常被误解为有序完成\",\"对象误用：只形容做事方式\"]",
            synonyms = "[\"墨守成规\"]",
            antonyms = "[\"别出心裁\"]",
            frequency = 5,
        )
}
