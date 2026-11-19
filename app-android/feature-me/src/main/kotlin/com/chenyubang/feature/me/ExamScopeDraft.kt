package com.chenyubang.feature.me

import com.chenyubang.core.data.ExamScopeSelection

/** 考试类型（E1；教师编不在 MVP 可选范围，由禁用行表达）。 */
enum class ExamScopeType {
    National,
    Provincial,
    Institution,
}

/**
 * P14 / P01 共用的范围草稿（未保存态）。
 *
 * 省份口径（信息架构 v1.4，推翻决策 I）：省考勾选时省份默认全选、省考内容正常显示，
 * 省份仅为可增减的筛选维度，不存在「未选省份」异常态。
 * 关闭省考时保留已选省份，便于再次打开时沿用（与数据层 `examScope.provinces` 口径一致）。
 */
data class ExamScopeDraft(
    val national: Boolean = true,
    val provincial: Boolean = true,
    val institution: Boolean = true,
    val provinces: Set<String> = emptySet(),
) {
    /** 三类全不选：允许进入，但成语库/例题库会是空的（IA 5.4 末行）。 */
    val isEmpty: Boolean get() = !national && !provincial && !institution

    /**
     * 切换勾选态。[availableProvinces] 为当前题库下的可选省份：
     * 开启省考且此前未选过省份时，默认全选所有可选省份（省份仅为筛选维度，可逐个取消）。
     */
    fun toggle(
        type: ExamScopeType,
        availableProvinces: List<String> = emptyList(),
    ): ExamScopeDraft =
        when (type) {
            ExamScopeType.National -> copy(national = !national)
            ExamScopeType.Institution -> copy(institution = !institution)
            ExamScopeType.Provincial ->
                if (!provincial && provinces.isEmpty() && availableProvinces.isNotEmpty()) {
                    copy(provincial = true, provinces = availableProvinces.toSet())
                } else {
                    copy(provincial = !provincial)
                }
        }

    fun toggleProvince(province: String): ExamScopeDraft =
        copy(provinces = if (province in provinces) provinces - province else provinces + province)

    fun toSelection(): ExamScopeSelection =
        ExamScopeSelection(
            national = national,
            provincial = provincial,
            institution = institution,
            provinces = provinces,
        )

    /**
     * 省考行右侧的说明文字：省份覆盖全部可选省份时写「省份全选」，
     * 收窄后逐个列出；全省份取消（筛选维度允许）或题库暂无省份数据时只写「已选」。
     */
    fun provincialTrailing(availableProvinces: List<String>): String =
        when {
            !provincial -> "未选"
            provinces.isEmpty() -> "已选"
            availableProvinces.isNotEmpty() && provinces == availableProvinces.toSet() -> "已选 · 省份全选"
            else -> "已选 · " + provinces.sorted().joinToString("、")
        }

    companion object {
        fun from(selection: ExamScopeSelection): ExamScopeDraft =
            ExamScopeDraft(
                national = selection.national,
                provincial = selection.provincial,
                institution = selection.institution,
                provinces = selection.provinces,
            )
    }
}

/** P14 保存口子：由 app 层注入（写 `exam_scope` + 重建 `idiom_scope`）。 */
fun interface ExamScopeSaver {
    suspend fun save(
        scope: ExamScopeSelection,
        complete: Boolean,
    )
}
