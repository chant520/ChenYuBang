package com.chenyubang.feature.mistake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class MistakeFormattingTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun formatsIsoTimestampAsRecordedDate() {
        assertEquals("2026-11-14 录入", formatRecordedAt("2026-11-14T03:00:00Z", utc))
        // 跨时区按给定时区取当地日期，而不是直接截 ISO 的日期部分。
        assertEquals("2026-11-13 录入", formatRecordedAt("2026-11-14T03:00:00Z", ZoneId.of("America/New_York")))
    }

    @Test
    fun keepsOriginalTextWhenInputIsEmptyOrInvalid() {
        assertEquals("", formatRecordedAt(null, utc))
        assertEquals("", formatRecordedAt("", utc))
        assertEquals("昨天", formatRecordedAt("昨天", utc))
    }

    @Test
    fun summarizesTagsByCountThenName() {
        assertEquals("望文生义 2 · 对象误用 1", tagSummaryLine(listOf("望文生义", "对象误用", "望文生义")))
        assertEquals("对象误用 1 · 望文生义 1", tagSummaryLine(listOf("望文生义", "对象误用")))
        assertNull(tagSummaryLine(emptyList()))
    }

    @Test
    fun namesUnknownExamTypeAndJoinsSense() {
        assertEquals("未标注考试类型", examTypeLabel(null))
        assertEquals("未标注考试类型", examTypeLabel("  "))
        assertEquals("国考", examTypeLabel("国考"))
        assertEquals("贬义。原指依照条理。", idiomSenseLine("贬义", "原指依照条理。"))
        assertEquals("原指依照条理。", idiomSenseLine(null, "原指依照条理。"))
        assertEquals("贬义", idiomSenseLine("贬义", ""))
    }
}
