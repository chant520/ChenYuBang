package com.chenyubang.feature.me

import com.chenyubang.core.data.ExamScopeSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P14 范围草稿与省份默认全选逻辑。纯函数单测，不依赖数据库。
 */
class ExamScopeDraftTest {
    private val provinces = listOf("北京", "河南", "贵州", "广东")

    @Test
    fun defaultDraftMatchesProductDefault() {
        val draft = ExamScopeDraft()

        assertTrue(draft.national)
        assertTrue(draft.provincial)
        assertTrue(draft.institution)
        assertTrue(draft.provinces.isEmpty())
        assertFalse(draft.isEmpty)
    }

    @Test
    fun togglingProvincialOnDefaultsToAllAvailableProvinces() {
        val draft = ExamScopeDraft(provincial = false).toggle(ExamScopeType.Provincial, provinces)

        assertTrue(draft.provincial)
        assertEquals(provinces.toSet(), draft.provinces)
        assertEquals("已选 · 省份全选", draft.provincialTrailing(provinces))
    }

    @Test
    fun togglingProvincialOnWithoutAvailableProvincesKeepsEmpty() {
        val draft = ExamScopeDraft(provincial = false).toggle(ExamScopeType.Provincial)

        assertTrue(draft.provincial)
        assertTrue(draft.provinces.isEmpty())
    }

    @Test
    fun togglingProvincialOnKeepsPreviouslySelectedProvinces() {
        val draft =
            ExamScopeDraft(provincial = false, provinces = setOf("河南"))
                .toggle(ExamScopeType.Provincial, provinces)

        assertTrue(draft.provincial)
        assertEquals(setOf("河南"), draft.provinces)
    }

    @Test
    fun removingOneProvinceFiltersBySelection() {
        val draft =
            ExamScopeDraft(provinces = provinces.toSet())
                .toggleProvince("河南")

        assertEquals(provinces.toSet() - "河南", draft.provinces)
        assertEquals("已选 · 北京、广东、贵州", draft.provincialTrailing(provinces))
    }

    @Test
    fun removingAllProvincesIsAnAllowedFilterState() {
        val draft =
            provinces.fold(ExamScopeDraft(provinces = provinces.toSet())) { current, province ->
                current.toggleProvince(province)
            }

        assertTrue(draft.provincial)
        assertTrue(draft.provinces.isEmpty())
        assertEquals("已选", draft.provincialTrailing(provinces))
    }

    @Test
    fun togglingSameProvinceRemovesIt() {
        val draft = ExamScopeDraft().toggleProvince("河南").toggleProvince("贵州").toggleProvince("河南")

        assertEquals(setOf("贵州"), draft.provinces)
    }

    @Test
    fun turningPronvincialOffKeepsSelectedProvinces() {
        val draft = ExamScopeDraft(provinces = setOf("河南")).toggle(ExamScopeType.Provincial, provinces)

        assertFalse(draft.provincial)
        assertEquals(setOf("河南"), draft.provinces)
        assertEquals("未选", draft.provincialTrailing(provinces))
    }

    @Test
    fun allUncheckedIsAllowedButEmpty() {
        val draft =
            ExamScopeDraft()
                .toggle(ExamScopeType.National, provinces)
                .toggle(ExamScopeType.Provincial, provinces)
                .toggle(ExamScopeType.Institution, provinces)

        assertTrue(draft.isEmpty)
    }

    @Test
    fun trailingTextReflectsProvincialState() {
        assertEquals("已选", ExamScopeDraft().provincialTrailing(emptyList()))
        assertEquals("已选 · 省份全选", ExamScopeDraft(provinces = provinces.toSet()).provincialTrailing(provinces))
        assertEquals("未选", ExamScopeDraft(provincial = false).provincialTrailing(provinces))
        assertEquals(
            "已选 · 河南、贵州",
            ExamScopeDraft(provinces = setOf("贵州", "河南")).provincialTrailing(provinces),
        )
    }

    @Test
    fun draftRoundTripsThroughSelection() {
        val selection =
            ExamScopeSelection(
                national = true,
                provincial = true,
                institution = false,
                provinces = setOf("贵州"),
            )
        val draft = ExamScopeDraft.from(selection)

        assertEquals(selection, draft.toSelection())
        assertFalse(draft.isEmpty)
        assertEquals("已选 · 贵州", draft.provincialTrailing(provinces))
    }
}
