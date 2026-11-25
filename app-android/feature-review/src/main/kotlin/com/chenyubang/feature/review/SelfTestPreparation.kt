package com.chenyubang.feature.review

/** 出题前置资格：空范围优先于候选项不足，两层都只读、不扩大正确答案范围。 */
enum class SelfTestPreparation {
    EmptyScope,
    InsufficientCandidates,
    Ready,
    ;

    companion object {
        fun from(
            scoped: List<SelfTestCandidate>,
            library: List<SelfTestCandidate>,
        ): SelfTestPreparation {
            val eligible = SelfTestEngine.eligible(library)
            val scopedIds = scoped.map { it.id.trim() }.toSet()
            return when {
                eligible.none { it.id in scopedIds } -> EmptyScope
                eligible.size < SelfTestEngine.OPTION_COUNT -> InsufficientCandidates
                else -> Ready
            }
        }
    }
}
