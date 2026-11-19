package com.chenyubang.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.tokens.Tokens

/** 顶部区：左返回 / 页名 / 该页上下文操作（信息架构 v1.2 §2）。 */
@Composable
internal fun RouteHeader(
    router: RouterState,
    back: () -> Unit,
    onSearch: (() -> Unit)? = null,
) {
    // P04 搜索（全屏浮层）与 P05 详情（二级页）自带完整顶部区：浮层是关闭 X，详情是返回 + 页名。
    // 这里跳过外壳头部，避免出现两层返回/页名（信息架构 v1.3 §2）。
    if (router.current.route == Route.Search || router.current.route == Route.Idiom) return
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.pageX.dp, vertical = Tokens.Space.contentTop.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (router.stack.size > 1 && router.current.route != Route.Done) {
            CButton("返回", back, variant = CButtonVariant.Ghost, modifier = Modifier.weight(1f))
        }
        Text(
            router.current.route.title,
            color = colors.head,
            fontSize = Tokens.Font.n20.sp,
            modifier = Modifier.weight(2f),
        )
        if (onSearch != null) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier.size(Tokens.Space.hitMin.dp)
                    .clickable(interactionSource = interaction, indication = null, onClick = onSearch)
                    .semantics { contentDescription = "搜索成语" },
                contentAlignment = Alignment.Center,
            ) {
                CChromeGlyph(
                    kind = CChromeIcon.Search,
                    color = colors.accent,
                    modifier = Modifier.size(Tokens.Icon.n21.dp),
                )
            }
        }
    }
}
