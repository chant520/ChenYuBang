package com.chenyubang.core.data

/**
 * 复习判分（开发规范 v1.7 §8.2，2026-11-23 用户拍板沿用 P10 重做口径）：
 * 以首次「我的答案」（user_example.myAnswer）为基准，本次所选不同＝答对、相同＝答错。
 * 首次答案未记录或本题未记录选项时无法比对，不伪造判分、不落 review_log。
 */
object ReviewScoring {
    const val RESULT_CORRECT = "对"
    const val RESULT_WRONG = "错"

    private val LETTERS = listOf("A", "B", "C", "D")

    /** 首次作答下标；空值或 A–D 之外为 null（不可比对）。 */
    fun firstPick(myAnswer: String?): Int? = LETTERS.indexOf(myAnswer?.trim()?.uppercase()).takeIf { it >= 0 }

    /** 本题是否记录了可供选择的选项（四个选项至少有一个非空）。 */
    fun hasOptions(options: List<String?>): Boolean = options.any { !it.isNullOrBlank() }

    /** 判分区主文案；无法比对时如实说明，绝不显示「正确答案」或解析。 */
    fun judge(
        myAnswer: String?,
        options: List<String?>,
        picked: Int?,
    ): ReviewJudgement {
        val first = firstPick(myAnswer)
        return when {
            picked == null || picked !in LETTERS.indices ->
                ReviewJudgement(comparable = false, result = null, message = "尚未选择")
            !hasOptions(options) ->
                ReviewJudgement(comparable = false, result = null, message = "本题未记录选项，无法比对")
            first == null ->
                ReviewJudgement(comparable = false, result = null, message = "本题未记录你的首次作答，无法比对")
            first == picked ->
                ReviewJudgement(comparable = true, result = RESULT_WRONG, message = "与首次作答相同")
            else ->
                ReviewJudgement(comparable = true, result = RESULT_CORRECT, message = "与首次作答不同")
        }
    }
}

/** [comparable] 为 false 时 [result] 为空，调用方不得落库、不得计入正确数。 */
data class ReviewJudgement(
    val comparable: Boolean,
    val result: String?,
    val message: String,
)
