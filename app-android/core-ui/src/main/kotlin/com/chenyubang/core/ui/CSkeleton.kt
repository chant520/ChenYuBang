package com.chenyubang.core.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chenyubang.core.ui.tokens.Tokens

private const val SHIMMER_PERIOD_MS = 1500
private const val SHIMMER_EASING_X1 = 0.42f
private const val SHIMMER_EASING_X2 = 0.58f
private val shimmerEasing = CubicBezierEasing(SHIMMER_EASING_X1, 0f, SHIMMER_EASING_X2, 1f)

/** 骨架块（规范 §5.10）：skel 底 + skelhi 高光横移 1.5s ease-in-out；reduce motion 静态。 */
@Composable
fun CSkeletonBlock(
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val c = LocalColorSet.current
    val reduceMotion = rememberReduceMotion()
    val shape = RoundedCornerShape(Tokens.Radius.r4.dp)
    val progress by rememberInfiniteTransition(label = "shimmer")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SHIMMER_PERIOD_MS, easing = shimmerEasing), RepeatMode.Restart),
            label = "shimmerProgress",
        )
    Box(
        modifier =
            modifier
                .height(height)
                .clip(shape)
                .background(c.skel, shape)
                .then(
                    if (reduceMotion) {
                        Modifier
                    } else {
                        Modifier.drawWithContent {
                            drawContent()
                            val x = (progress * 2f - 1f) * size.width
                            drawRect(
                                brush =
                                    Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, c.skelhi, Color.Transparent),
                                        startX = x,
                                        endX = x + size.width,
                                    ),
                                topLeft = Offset.Zero,
                                size = Size(size.width, size.height),
                            )
                        }
                    },
                ),
    )
}
