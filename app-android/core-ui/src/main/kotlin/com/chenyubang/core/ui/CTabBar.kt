package com.chenyubang.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.ui.tokens.Tokens

@Composable
fun CTabBar(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalColorSet.current
    val icons = listOf(CChromeIcon.Book, CChromeIcon.Example, CChromeIcon.Mistake, CChromeIcon.Me)
    require(labels.size == icons.size)
    Column(modifier.background(colors.tabbg)) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        Row {
            labels.forEachIndexed { index, label ->
                val active = index == selected
                val color = if (active) colors.accent else colors.text3
                Column(
                    Modifier.weight(1f).clickable(role = Role.Tab, onClick = { onSelect(index) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxWidth().height(Tokens.Space.tabIndicator.dp)
                            .background(if (active) colors.accent else Color.Transparent),
                    )
                    Column(
                        Modifier.padding(top = Tokens.Space.tabTop.dp, bottom = Tokens.Space.tabBottom.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CChromeGlyph(icons[index], color, Modifier.size(Tokens.Icon.n18.dp))
                        Text(
                            label,
                            Modifier.padding(top = Tokens.Space.tabIconGap.dp),
                            color = color,
                            fontSize = Tokens.FontRole.label.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
