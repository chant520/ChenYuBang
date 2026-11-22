package com.chenyubang.feature.mistake

import com.chenyubang.core.data.MistakeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A4 钉住：一道录入题挂多条错题记录时展示哪条——pickLatestMark 取 createdAt 倒序、
 * 同刻按 id 倒序的最新一条（iOS MistakeLibrary.swift:197-205 同口径）；
 * @Relation 关联无序，不得依赖列表到来顺序。
 */
class P09LatestMarkTest {
    private fun mistake(
        id: String,
        createdAt: String,
        state: String,
    ) = MistakeEntity(
        id = id,
        stem = "题干",
        optionA = "A",
        optionB = "B",
        optionC = "C",
        optionD = "D",
        state = state,
        createdAt = createdAt,
    )

    @Test
    fun picksLatestCreatedAtWhenMultipleMistakesOnOneExample() {
        val latest = mistake("m-new", "2026-11-22", "已掌握")
        val older = mistake("m-old", "2026-11-01", "待复习")
        assertEquals(latest, pickLatestMark(listOf(older, latest)))
        // 到来顺序翻转，结果不变（钉住「不依赖 @Relation 顺序」的语义）。
        assertEquals(latest, pickLatestMark(listOf(latest, older)))
    }

    @Test
    fun breaksCreatedAtTieByDescendingId() {
        val newer = mistake("m-b", "2026-11-22", "待复习")
        val olderSameTime = mistake("m-a", "2026-11-22", "已掌握")
        assertEquals(newer, pickLatestMark(listOf(olderSameTime, newer)))
    }

    @Test
    fun singleMistakePassesThroughAndEmptyGivesNull() {
        val only = mistake("m-1", "2026-11-01", "待复习")
        assertEquals(only, pickLatestMark(listOf(only)))
        assertNull(pickLatestMark(emptyList()))
    }
}
