package com.chenyubang.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.tokens.Tokens

private const val TAG_LINE_HEIGHT = 1.4f

/** 标签（规范 §5.2）：静态信息展示，无交互态。 */
@Composable
fun CTag(
    text: String,
    modifier: Modifier = Modifier,
    style: CTagStyle = CTagStyle.Solid,
) {
    val c = LocalColorSet.current
    val shape = RoundedCornerShape(Tokens.Radius.r4.dp)
    val background =
        when (style) {
            CTagStyle.Solid -> c.tintbg
            CTagStyle.Ghost -> Color.Transparent
            CTagStyle.Selected -> c.accent
            CTagStyle.Disabled -> c.disbg
        }
    val foreground =
        when (style) {
            CTagStyle.Solid -> c.tintfg
            CTagStyle.Ghost -> c.text3
            CTagStyle.Selected -> c.segonfg
            CTagStyle.Disabled -> c.disfg
        }
    val line =
        when (style) {
            CTagStyle.Solid -> c.tintline
            CTagStyle.Ghost -> c.line
            CTagStyle.Selected -> c.accent
            CTagStyle.Disabled -> c.disline
        }
    Box(
        modifier =
            modifier
                .background(background, shape)
                .then(
                    if (style == CTagStyle.Ghost) {
                        Modifier.dashedBorder(line, Tokens.Radius.r4.dp)
                    } else {
                        Modifier.border(1.dp, line, shape)
                    },
                )
                .padding(horizontal = Tokens.Space.tagX.dp, vertical = Tokens.Space.tagY.dp),
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = Tokens.FontRole.label.sp,
            lineHeight = (Tokens.FontRole.label * TAG_LINE_HEIGHT).sp,
        )
    }
}
