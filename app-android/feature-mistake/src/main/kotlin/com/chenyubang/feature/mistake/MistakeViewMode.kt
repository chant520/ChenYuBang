package com.chenyubang.feature.mistake

/**
 * P08 视图切换（信息架构 v1.4 第 290 行）：按成语分组 / 按录入时间 / 按考试类型。
 *
 * enum 天然 Serializable，直接放进 app 的 RouterState 跟着路由状态一起保存；
 * 返回 P08 时保持原视图，默认「按成语分组」。
 */
enum class MistakeViewMode(val label: String) {
    ByIdiom("按成语分组"),
    ByTime("按录入时间"),
    ByExamType("按考试类型"),
    ;

    companion object {
        val labels: List<String> = entries.map { it.label }
    }
}
