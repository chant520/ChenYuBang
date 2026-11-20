package com.chenyubang.feature.capture

import com.chenyubang.core.data.IdiomEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P20Matcher 纯函数回归：三档判定 / 并列最匹配 / 无模糊命中 / JSON 降级 / 截断保最匹配（由旧 VM 测试迁入）。 */
class P20MatcherTest {
    private val anbu = idiom(ID_ANBU, "按部就班", 5, "[\"墨守成规\",\"循规蹈矩\"]")
    private val biechu = idiom(ID_BIECHU, "别出心裁", 4, "[\"独出心裁\",\"标新立异\"]")
    private val moshou = idiom(ID_MOSHOU, "墨守成规", 4, "[\"故步自封\",\"按部就班\"]")
    private val sample = listOf(anbu, biechu, moshou)

    @Test
    fun mergedTextPrefixesOptions() {
        val text =
            P20Matcher.mergedText(
                OcrDraft(
                    stem = "下列各句中，加点的成语使用不恰当的一项是……",
                    options = listOf("新厂长按部就班，提出了一整套创新方案", "他做事标新立异", "", ""),
                ),
            )
        assertTrue(text.contains("A．新厂长按部就班"))
    }

    @Test
    fun threeTiersWithUniqueBest() {
        val draft =
            OcrDraft(
                stem = "下列各句中，加点的成语使用不恰当的一项是……",
                options = listOf("新厂长按部就班，提出了一整套创新方案", "他做事标新立异，从不墨守成规", "", ""),
            )
        val result = P20Matcher.buildPool(P20Matcher.mergedText(draft), sample)
        assertEquals(3, result.candidates.size)
        assertEquals(ID_ANBU, result.bestId)
        assertEquals(MatchTier.Best, tierOf(result, ID_ANBU))
        assertEquals(MatchTier.SameQuestion, tierOf(result, ID_MOSHOU))
        assertEquals(MatchTier.Semantic, tierOf(result, ID_BIECHU))
        assertEquals(1, result.candidates.count { it.id == result.bestId })
    }

    @Test
    fun bestTieBrokenByFrequencyThenWord() {
        val x = idiom("id-y", "乙成语", 4)
        val y = idiom("id-j", "甲成语", 4)
        val result = P20Matcher.buildPool("乙成语 和 甲成语", listOf(x, y))
        assertEquals("id-y", result.bestId)
        assertEquals(MatchTier.Best, tierOf(result, "id-y"))
        assertEquals(MatchTier.SameQuestion, tierOf(result, "id-j"))
    }

    @Test
    fun typoDoesNotFuzzyMatch() {
        val result = P20Matcher.buildPool("新厂长按步就班，提出了一整套方案", sample)
        assertTrue(result.candidates.isEmpty())
        assertNull(result.bestId)
    }

    @Test
    fun synonymsJsonFailureDegradesToEmpty() {
        assertEquals(emptyList<String>(), P20Matcher.synonymsOf("{not json"))
        assertEquals(emptyList<String>(), P20Matcher.synonymsOf("[\"ok\", broken"))
        val broken = idiom("id-broken", "无关词", 3, "{\"标新立异\"}")
        assertTrue(P20Matcher.buildPool("他做事标新立异", listOf(broken)).candidates.isEmpty())
    }

    @Test
    fun bestSurvivesCandidateTruncation() {
        val filler = (0 until 60).map { index -> idiom("id-f$index", "阿%02d".format(index), 1) }
        val best = idiom("id-best", "词最末", 99)
        val all = filler + best
        val result = P20Matcher.buildPool(all.joinToString(" ") { it.word }, all)
        assertEquals(50, result.candidates.size)
        assertTrue(result.candidates.any { it.id == "id-best" })
        assertEquals("id-best", result.bestId)
        assertEquals(MatchTier.Best, tierOf(result, "id-best"))
    }

    @Test
    fun manualAndAddedCandidatesCarryExpectedTierLabels() {
        assertEquals(MatchTier.Manual, P20Matcher.manualCandidate(biechu).tier)
        assertEquals("手动选择", P20Matcher.manualCandidate(biechu).tier.label)
        assertEquals(MatchTier.Added, P20Matcher.addedCandidate(biechu).tier)
        assertNull(P20Matcher.addedCandidate(biechu).tier.label)
    }

    private fun tierOf(
        result: P20PoolResult,
        id: String,
    ): MatchTier = result.candidates.first { it.id == id }.tier

    private fun idiom(
        id: String,
        word: String,
        frequency: Int,
        synonyms: String = "[]",
    ) = IdiomEntity(
        id = id,
        word = word,
        pinyin = "pin yin",
        initials = "py",
        sense = "释义。",
        color = "中性",
        synonyms = synonyms,
        frequency = frequency,
    )

    private companion object {
        const val ID_ANBU = "11111111-1111-4111-8111-111111111111"
        const val ID_BIECHU = "22222222-2222-4222-8222-222222222222"
        const val ID_MOSHOU = "33333333-3333-4333-8333-333333333333"
    }
}
