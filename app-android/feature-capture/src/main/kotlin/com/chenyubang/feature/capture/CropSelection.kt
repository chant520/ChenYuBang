package com.chenyubang.feature.capture

import android.graphics.RectF
import androidx.compose.ui.unit.IntSize
import java.io.Serializable

/** P19 的四个选项固定按 A–D 排列，顺序即高保真稿与 P20 的字段顺序。 */
val OcrOptionKeys = listOf("A", "B", "C", "D")

/** P19 交出的题干与选项草稿；P20 返回时用同一份回填（信息架构 v1.4 第 30 条）。 */
data class OcrDraft(
    val stem: String,
    val options: List<String>,
) : Serializable {
    /** 选项始终补齐到四项，缺的用空串，避免 P19 输入框数量随识别结果跳动。 */
    fun paddedOptions(): List<String> = List(OcrOptionKeys.size) { index -> options.getOrElse(index) { "" } }

    companion object {
        private const val serialVersionUID = 1L
    }
}

/** P18 的框选与摆正参数（相对显示容器归一化 0–1）；P19 返回 P18 时据此还原（IA 第 29 条）。 */
data class CropFrame(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val quarterTurns: Int,
    val fineDegrees: Float,
    val containerWidth: Int = 0,
    val containerHeight: Int = 0,
) : Serializable {
    fun toRectF(): RectF = RectF(left, top, right, bottom)

    companion object {
        private const val serialVersionUID = 1L

        fun of(
            rect: RectF,
            quarterTurns: Int,
            fineDegrees: Float,
            containerSize: IntSize,
        ): CropFrame =
            CropFrame(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                quarterTurns,
                fineDegrees,
                containerSize.width,
                containerSize.height,
            )
    }
}

/** P18「下一步」交出的结果：导出图路径 + 框选参数。 */
data class CropSelection(
    val path: String,
    val frame: CropFrame,
)
