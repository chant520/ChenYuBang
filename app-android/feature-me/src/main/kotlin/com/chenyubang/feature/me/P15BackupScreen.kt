package com.chenyubang.feature.me

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.chenyubang.core.ui.CBadge
import com.chenyubang.core.ui.CBadgeKind
import com.chenyubang.core.ui.CBar
import com.chenyubang.core.ui.CBarKind
import com.chenyubang.core.ui.CCard
import com.chenyubang.core.ui.CChromeGlyph
import com.chenyubang.core.ui.CChromeIcon
import com.chenyubang.core.ui.CLabel
import com.chenyubang.core.ui.CList
import com.chenyubang.core.ui.CListRow
import com.chenyubang.core.ui.tokens.Tokens
import java.io.File

/**
 * P15 备份与恢复（路由 settings/backup）。主态逐字文案见视觉契约第 1.1 节。
 * 存本地走 SAF CreateDocument → exportTo；另存网盘走 exportToFile(cacheDir/backups) →
 * FileProvider + ACTION_SEND/CHOOSER，不接账号、不写相册、全程零上传。
 */
@Composable
fun P15BackupRoute(
    vm: P15BackupViewModel,
    onPresentImportMode: () -> Unit,
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()

    val createLocal =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) vm.exportToUri(uri)
        }
    val openImport =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.pickImport(uri, onPresentImportMode)
        }
    val shareDir = remember { File(context.cacheDir, "backups") }

    P15BackupContent(
        state = state,
        onLocal = { createLocal.launch("backup.zip") },
        onShare = { vm.exportToFile(shareDir) { file -> launchSystemShare(context, file) } },
        onPick = { openImport.launch(IMPORT_MIME) },
    )
}

/** 可测性最小重构：Route 与快照共用同一主体，视觉/文案/令牌与业务逻辑均不变。 */
@Composable
internal fun P15BackupContent(
    state: P15BackupUiState,
    onLocal: () -> Unit = {},
    onShare: () -> Unit = {},
    onPick: () -> Unit = {},
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
        ExportCard(onLocal = onLocal, onShare = onShare)
        ImportCard(onPick = onPick)

        CCard(soft = true) {
            CLabel("最近一次备份时间")
            Spacer(Modifier.size(Tokens.Space.lmetaTop.dp))
            CBadge("进阶位", CBadgeKind.Neutral)
            Spacer(Modifier.size(Tokens.Space.lsubTop.dp))
            Text(
                text = P15_LAST_BACKUP_VALUE,
                color = colors.text3,
                fontSize = Tokens.FontRole.caption.sp,
            )
        }

        state.banner?.let { P15BannerBar(it) }

        Text(
            text = "全程不登录、不上传、不代传：文件只在你和网盘之间走。",
            color = colors.text3,
            fontSize = Tokens.FontRole.caption.sp,
            lineHeight = (Tokens.FontRole.caption * TEXT_LINE_HEIGHT).sp,
        )
        Spacer(Modifier.size(Tokens.Space.fabBottom.dp))
    }
}

@Composable
private fun ExportCard(
    onLocal: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    CCard {
        CLabel("导出备份")
        Spacer(Modifier.size(Tokens.Space.lsubTop.dp))
        Text(
            text = "导出的内容：错题、录入题、收藏、学习状态。不含题库本身。",
            color = colors.text2,
            fontSize = Tokens.FontRole.body.sp,
            lineHeight = (Tokens.FontRole.body * TEXT_LINE_HEIGHT).sp,
        )
        Spacer(Modifier.size(Tokens.Space.lmetaTop.dp))
        CList {
            ActionRow("存到手机本地", onClick = onLocal)
            ActionRow("通过系统文件 / 分享面板\n另存到网盘", showDivider = false, onClick = onShare)
        }
    }
}

@Composable
private fun ImportCard(onPick: () -> Unit) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    CCard {
        CLabel("导入备份")
        Spacer(Modifier.size(Tokens.Space.lsubTop.dp))
        Text(
            text = "读入前会先问你：覆盖还是合并（见 P22）。",
            color = colors.text2,
            fontSize = Tokens.FontRole.body.sp,
            lineHeight = (Tokens.FontRole.body * TEXT_LINE_HEIGHT).sp,
        )
        Spacer(Modifier.size(Tokens.Space.lmetaTop.dp))
        CList {
            ActionRow("从手机本地选文件", onClick = onPick)
            ActionRow("从网盘选文件", showDivider = false, onClick = onPick)
        }
    }
}

@Composable
private fun ActionRow(
    label: String,
    showDivider: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) Tokens.Dark else Tokens.Light
    CListRow(
        name = label,
        pinyin = "",
        showDivider = showDivider,
        onClick = onClick,
        meta = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                CChromeGlyph(
                    kind = CChromeIcon.Chevron,
                    color = colors.accent,
                    modifier = Modifier.size(Tokens.Icon.n15.dp),
                )
            }
        },
    )
}

@Composable
private fun P15BannerBar(banner: P15Banner) {
    when (banner) {
        is P15Banner.Exported ->
            CBar(
                kind = CBarKind.Info,
                title = P15_EXPORT_SUCCESS_TITLE,
                subtitle = p15ExportCounts(banner.counts),
            )
        is P15Banner.Importing ->
            CBar(kind = CBarKind.Info, title = P15_IMPORTING)
        is P15Banner.HardError ->
            CBar(
                kind = CBarKind.Error,
                title = banner.title,
                subtitle = banner.detail.ifBlank { null },
            )
        is P15Banner.Imported ->
            CBar(
                kind = CBarKind.Info,
                title = P15_IMPORT_DONE_TITLE,
                subtitle = p15ImportDetail(banner.result),
            )
    }
}

/** 系统分享面板另存：把缓存目录的 zip 以 content:// 交给系统，不接网盘账号。 */
private fun launchSystemShare(
    context: android.content.Context,
    file: File,
) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(send, "另存到网盘"))
}

private const val TEXT_LINE_HEIGHT = 1.7f
private val IMPORT_MIME = arrayOf("application/zip", "application/octet-stream")
