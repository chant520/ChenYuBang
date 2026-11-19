package com.chenyubang.feature.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 成语高亮匹配纯函数单测（工作包 1.5，验收 5/7）。
 *
 * 覆盖：
 *  - 题干一次命中多个成语（一题多成语）；
 *  - 未知成语 / 词表为空不命中；
 *  - 重叠子串只命中更长词（「墨守成规」优先于其子串）；
 *  - 命中区间互不重叠、按位置升序；
 *  - 题干 / 选项 / 解析里同一成语重复出现时全部标记。
 */
class IdiomHighlightTest {
    private val wordIndex =
        mapOf(
            "idiom-anbu" to "按部就班",
            "idiom-moshou" to "墨守成规",
            "idiom-biechu" to "别出心裁",
        )

    @Test
    fun `题干一次命中多个成语`() {
        val text = "新厂长按部就班地创新，又墨守成规地守旧。"
        val hits = IdiomHighlight.match(text, wordIndex)
        assertEquals(2, hits.size)
        assertEquals("按部就班", hits[0].word)
        assertEquals("墨守成规", hits[1].word)
        assertEquals("idiom-anbu", hits[0].idiomId)
        assertEquals("idiom-moshou", hits[1].idiomId)
    }

    @Test
    fun `未知成语不命中`() {
        val text = "新厂长循序渐进地推进方案。"
        assertTrue(IdiomHighlight.match(text, wordIndex).isEmpty())
    }

    @Test
    fun `词表为空不命中`() {
        assertTrue(IdiomHighlight.match("按部就班", emptyMap()).isEmpty())
    }

    @Test
    fun `空文本不命中`() {
        assertTrue(IdiomHighlight.match("", wordIndex).isEmpty())
    }

    @Test
    fun `重叠子串命中更长词`() {
        // 「墨守成规」含「成规」，词表无「成规」时不受影响；
        // 若词表含短词「成规」，长词优先、不产生重叠命中。
        val withShort =
            wordIndex + ("idiom-chenggui" to "成规")
        val hits = IdiomHighlight.match("做法墨守成规难有突破", withShort)
        assertEquals(1, hits.size)
        assertEquals("墨守成规", hits.single().word)
    }

    @Test
    fun `同一成语重复出现全部标记且区间互不重叠`() {
        val text = "按部就班不是按部就班地拖延。"
        val hits = IdiomHighlight.match(text, wordIndex)
        assertEquals(2, hits.size)
        assertEquals(0, hits[0].start)
        assertEquals(4, hits[0].end)
        assertEquals(6, hits[1].start)
        assertEquals(10, hits[1].end)
        assertEquals("按部就班", text.substring(hits[0].start, hits[0].end))
        assertEquals("按部就班", text.substring(hits[1].start, hits[1].end))
    }

    @Test
    fun `命中区间覆盖全部成语文本`() {
        val text = "别出心裁地解决难题"
        val hits = IdiomHighlight.match(text, wordIndex)
        assertEquals(1, hits.size)
        assertEquals(0, hits[0].start)
        assertEquals(4, hits[0].end)
        assertEquals("别出心裁", text.substring(hits[0].start, hits[0].end))
    }
}
