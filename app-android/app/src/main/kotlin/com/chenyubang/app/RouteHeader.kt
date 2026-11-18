package com.chenyubang.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.tokens.Tokens

@Composable
internal fun RouteHeader(
    router: RouterState,
    back: () -> Unit,
    cancelCapture: () -> Unit,
) {
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
        if (router.captureBaseDepth != null) {
            CButton(
                "取消",
                cancelCapture,
                variant = CButtonVariant.Ghost,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
