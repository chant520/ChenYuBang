package com.chenyubang.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chenyubang.core.data.ExamScopeSelection
import com.chenyubang.core.ui.CButton
import com.chenyubang.core.ui.CButtonVariant
import com.chenyubang.core.ui.CCheckRow
import com.chenyubang.core.ui.tokens.Tokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ExamScopeEditor(
    initial: ExamScopeSelection,
    availableProvinces: List<String>,
    firstLaunch: Boolean,
    onSave: suspend (ExamScopeSelection) -> Unit,
    onComplete: (ExamScopeSelection) -> Unit,
) {
    var national by rememberSaveable { mutableStateOf(initial.national) }
    var provincial by rememberSaveable { mutableStateOf(initial.provincial) }
    var institution by rememberSaveable { mutableStateOf(initial.institution) }
    var provinces by rememberSaveable { mutableStateOf(initial.provinces.sorted().toTypedArray()) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val selection = ExamScopeSelection(national, provincial, institution, provinces.toSet())
    val coroutine = rememberCoroutineScope()
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    val finish = {
        saving = true
        failed = false
        coroutine.launch {
            try {
                onSave(selection)
                onComplete(selection)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            } finally {
                saving = false
            }
        }
        Unit
    }
    Column(
        Modifier.fillMaxSize().background(colors.page).verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Space.pageX.dp, vertical = Tokens.Space.contentTop.dp),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.block.dp),
    ) {
        Text("成语帮 · 考试范围", color = colors.head, fontSize = Tokens.Font.n20.sp)
        CCheckRow("国考", national, { national = it }, enabled = !saving)
        CCheckRow("省考", provincial, {
            provincial = it
        }, enabled = !saving, warning = "未选省份".takeIf { selection.provinceMissing })
        CCheckRow("事业编", institution, { institution = it }, enabled = !saving)
        CCheckRow("教师编", false, {}, enabled = false, trailing = "题库到位后开放", showDivider = false)
        if (provincial) {
            ScopeProvinceRows(availableProvinces, provinces.toSet(), !saving) {
                provinces = it.sorted().toTypedArray()
            }
        }
        if (selection.isEmpty) {
            Text(
                "不选范围时成语库和例题库会是空的，可以稍后在『我的』里改",
                color = colors.text3,
                fontSize = Tokens.Font.n12.sp,
            )
        }
        if (selection.provinceMissing) Text("省考内容暂不显示，选好省份即可看到", color = colors.warnfg, fontSize = Tokens.Font.n12.sp)
        if (failed) Text("范围保存失败，请重试；当前选择已保留。", color = colors.warnfg, fontSize = Tokens.Font.n12.sp)
        CButton(if (firstLaunch) "进入" else "保存", finish, loading = saving)
        if (firstLaunch) CButton("跳过", finish, variant = CButtonVariant.Ghost, enabled = !saving)
    }
}

@Composable
private fun ScopeProvinceRows(
    available: List<String>,
    selected: Set<String>,
    enabled: Boolean,
    onChange: (Set<String>) -> Unit,
) {
    available.forEach { province ->
        CCheckRow(province, province in selected, { checked ->
            onChange(if (checked) selected + province else selected - province)
        }, enabled = enabled)
    }
}
