package com.chenyubang.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.data.ExamScopeSelection
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.tokens.Tokens

/** P13 我的：现有设置入口集中展示，D2 自测是全屏路由，不跨 feature 依赖。 */
@Composable
fun P13MeContent(
    scope: ExamScopeSelection,
    actions: MeActions,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.page)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Space.pageX.dp, vertical = Tokens.Space.contentTop.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
    ) {
        SettingsRow("考试范围", scope.description, onClick = actions.onOpenScope)
        SettingsRow("成语自测", "10 题，两种题型交替", onClick = actions.onOpenSelfTest)
        SettingsRow("备份与恢复", "学习数据只存本机", onClick = actions.onOpenBackup)
        SettingsRow("题库版本", onClick = actions.onOpenPack)
        Text(
            "学习数据只存本机，不注册不登录；联网只用于获取或更新题库，以及把备份另存到网盘。",
            color = colors.text3,
            fontSize = Tokens.FontRole.supporting.sp,
            lineHeight = (Tokens.FontRole.supporting * 1.6f).sp,
        )
        SettingsRow("清空数据", onClick = actions.onClear)
        SettingsRow("拍照录题", onClick = actions.onCapture)
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = subtitle?.let { "$title，$it" } ?: title }
            .padding(horizontal = Tokens.Space.lrowX.dp, vertical = Tokens.Space.lrowY.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, fontSize = Tokens.FontRole.itemTitle.sp, fontWeight = FontWeight.W600)
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = colors.text3,
                    fontSize = Tokens.FontRole.caption.sp,
                    modifier = Modifier.padding(top = Tokens.Space.lsubTop.dp),
                )
            }
        }
        CChromeGlyph(CChromeIcon.Chevron, colors.accent, Modifier.padding(start = Tokens.Space.lt1Gap.dp))
    }
}
