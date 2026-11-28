// GENERATED FILE — DO NOT EDIT.
// Source: shared/tokens/tokens.json (docs/设计规范-v1.8.md)
// Regenerate: python tools/gen_tokens.py

package com.chenyubang.core.ui.tokens

import androidx.compose.ui.graphics.Color

/** 设计令牌。数值唯一来源：docs/设计规范-v1.8.md */
object Tokens {

    /** 浅/深色组共同接口：if/else 选色组时保留类型 */
    interface ColorSet {
        val accent: Color
        val accent2: Color
        val btnbg: Color
        val btnfg: Color
        val btnline: Color
        val cam: Color
        val camdim: Color
        val camfg: Color
        val camfill: Color
        val camghostline: Color
        val camhint: Color
        val camstroke: Color
        val chkfg: Color
        val cropedge: Color
        val cut: Color
        val disbg: Color
        val disfg: Color
        val disline: Color
        val errbg: Color
        val errfg: Color
        val errfill: Color
        val errfillfg: Color
        val errline: Color
        val fab: Color
        val fabicon: Color
        val head: Color
        val hot: Color
        val inputbg: Color
        val line: Color
        val line2: Color
        val loadbg: Color
        val loadfg: Color
        val modal: Color
        val page: Color
        val phbg: Color
        val phline: Color
        val seg: Color
        val segon: Color
        val segonfg: Color
        val skel: Color
        val skelhi: Color
        val surface: Color
        val surface2: Color
        val tabbg: Color
        val text: Color
        val text2: Color
        val text3: Color
        val tintbg: Color
        val tintfg: Color
        val tintline: Color
        val warnbg: Color
        val warnfg: Color
        val warnline: Color
    }

    /** Light 模式（规范 2.2） */
    object Light : ColorSet {
        override val accent = Color(0xFF008000)
        override val accent2 = Color(0xFF009E60)
        override val btnbg = Color(0xFF008000)
        override val btnfg = Color(0xFFFFFFFF)
        override val btnline = Color(0xFF008000)
        override val cam = Color(0xFF0B1410)
        override val camdim = Color(0xBFFFFFFF)
        override val camfg = Color(0xD9FFFFFF)
        override val camfill = Color(0xE6FFFFFF)
        override val camghostline = Color(0x4CFFFFFF)
        override val camhint = Color(0x99FFFFFF)
        override val camstroke = Color(0x80FFFFFF)
        override val chkfg = Color(0xFFFFFFFF)
        override val cropedge = Color(0x8CFFFFFF)
        override val cut = Color(0xFFBBDCC9)
        override val disbg = Color(0xFFE6EFE9)
        override val disfg = Color(0xFF93A99C)
        override val disline = Color(0xFFDCE8E1)
        override val errbg = Color(0xFFFDECEA)
        override val errfg = Color(0xFFC62828)
        override val errfill = Color(0xFFC62828)
        override val errfillfg = Color(0xFFFFFFFF)
        override val errline = Color(0xFFF0BEBE)
        override val fab = Color(0xFF008000)
        override val fabicon = Color(0xFFFFFFFF)
        override val head = Color(0xFF12291C)
        override val hot = Color(0xFF009E60)
        override val inputbg = Color(0xFFF7FBF8)
        override val line = Color(0xFFD6EADD)
        override val line2 = Color(0xFFE4F1E9)
        override val loadbg = Color(0xFF008000)
        override val loadfg = Color(0xFFFFFFFF)
        override val modal = Color(0x8C0C1A12)
        override val page = Color(0xFFF3F9F5)
        override val phbg = Color(0xFFECF7F0)
        override val phline = Color(0xFFCFE8D9)
        override val seg = Color(0xFFECF7F0)
        override val segon = Color(0xFF008000)
        override val segonfg = Color(0xFFFFFFFF)
        override val skel = Color(0xFFDCE9E0)
        override val skelhi = Color(0xFFF1F8F3)
        override val surface = Color(0xFFFFFFFF)
        override val surface2 = Color(0xFFEDF8F1)
        override val tabbg = Color(0xFFFFFFFF)
        override val text = Color(0xFF183828)
        override val text2 = Color(0xFF4F6B5B)
        override val text3 = Color(0xFF5E7569)
        override val tintbg = Color(0xFFF0FAF4)
        override val tintfg = Color(0xFF008000)
        override val tintline = Color(0xFFBFE6CC)
        override val warnbg = Color(0xFFFFF3E6)
        override val warnfg = Color(0xFFC2410C)
        override val warnline = Color(0xFFF3C9A8)
    }

    /** Dark 模式（规范 2.3） */
    object Dark : ColorSet {
        override val accent = Color(0xFF50C878)
        override val accent2 = Color(0xFF009E60)
        override val btnbg = Color(0xFF50C878)
        override val btnfg = Color(0xFF0C1A12)
        override val btnline = Color(0xFF50C878)
        override val cam = Color(0xFF0B1410)
        override val camdim = Color(0xBFFFFFFF)
        override val camfg = Color(0xD9FFFFFF)
        override val camfill = Color(0xE6FFFFFF)
        override val camghostline = Color(0x4CFFFFFF)
        override val camhint = Color(0x99FFFFFF)
        override val camstroke = Color(0x80FFFFFF)
        override val chkfg = Color(0xFF0C1A12)
        override val cropedge = Color(0x8CFFFFFF)
        override val cut = Color(0xFF2E4A39)
        override val disbg = Color(0xFF1E3327)
        override val disfg = Color(0xFF6E8A78)
        override val disline = Color(0xFF2B4636)
        override val errbg = Color(0x24FF8A80)
        override val errfg = Color(0xFFFF8A80)
        override val errfill = Color(0xFFFF8A80)
        override val errfillfg = Color(0xFF0C1A12)
        override val errline = Color(0x6BFF8A80)
        override val fab = Color(0xFF50C878)
        override val fabicon = Color(0xFF0C1A12)
        override val head = Color(0xFFE8F4EB)
        override val hot = Color(0xFF009E60)
        override val inputbg = Color(0xFF152A20)
        override val line = Color(0xFF2B4636)
        override val line2 = Color(0xFF23392C)
        override val loadbg = Color(0xFF50C878)
        override val loadfg = Color(0xFF0C1A12)
        override val modal = Color(0x9E000000)
        override val page = Color(0xFF101E17)
        override val phbg = Color(0xFF16291F)
        override val phline = Color(0xFF2A4433)
        override val seg = Color(0xFF16291F)
        override val segon = Color(0xFF50C878)
        override val segonfg = Color(0xFF0C1A12)
        override val skel = Color(0xFF23392C)
        override val skelhi = Color(0xFF2E4A39)
        override val surface = Color(0xFF1A3126)
        override val surface2 = Color(0xFF152A20)
        override val tabbg = Color(0xFF15251C)
        override val text = Color(0xFFD5E8DA)
        override val text2 = Color(0xFFA6C2AE)
        override val text3 = Color(0xFF7E9B88)
        override val tintbg = Color(0x1F50C878)
        override val tintfg = Color(0xFF50C878)
        override val tintline = Color(0x6150C878)
        override val warnbg = Color(0x24FF8A00)
        override val warnfg = Color(0xFFFF8A00)
        override val warnline = Color(0x6BFF8A00)
    }

    /** 字号（规范 3.2，11 档），单位 dp/sp */
    object Font {
        val n30 = 30
        val n20 = 20
        val n15 = 15
        val n14 = 14
        val n135 = 13.5
        val n13 = 13
        val n125 = 12.5
        val n12 = 12
        val n115 = 11.5
        val n11 = 11
        val n10 = 10
    }

    /** Android 语义字号（规范 3.4），单位 dp/sp */
    object FontRole {
        val displayNumber = 36
        val metric = 20
        val pageTitle = 20
        val contentTitle = 18
        val sectionTitle = 16
        val itemTitle = 16
        val body = 16
        val input = 16
        val supporting = 14
        val caption = 13
        val label = 12
        val button = 14
    }

    /** 圆角（规范 4.1），单位 dp/sp */
    object Radius {
        val r6 = 6
        val r5 = 5
        val r4 = 4
        val r3 = 3
        val r2 = 2
    }

    /** 间距（规范 4.2），单位 dp */
    object Space {
        val pageX = 20
        val block = 16
        val contentTop = 18
        val btnY = 13
        val dangerY = 12
        val ghostY = 10
        val topBtnX = 11
        val topBtnY = 7
        val tagX = 9
        val tagY = 4
        val badgeX = 6
        val badgeY = 2
        val fabSize = 52
        val fabRight = 20
        val fabBottom = 88
        val hitMin = 44
        val n64 = 64
        val lrowX = 16
        val lrowY = 13
        val lt1Gap = 8
        val lsubTop = 5
        val lmetaTop = 6
        val lmetaGap = 8
        val segPad = 3
        val segY = 8
        val chkGap = 10
        val chkY = 12
        val chkSize = 18
        val barX = 14
        val barY = 12
        val barGap = 10
        val inputMinH = 42
        val inputX = 12
        val inputY = 11
        val inputDisY = 10
        val imgphH = 150
        val imgphHSmall = 96
        val tabTop = 11
        val tabBottom = 13
        val tabIconGap = 5
        val tabIndicator = 2
        val modalWidth = 315
        val camTopY = 12
        val camTopGap = 10
        val camRowX = 26
        val camRowTop = 16
        val camRowBottom = 22
        val camShutter = 58
        val camRing = 3
        val camShutterIn = 44
        val camGallery = 40
        val camDenyW = 290
        val camDenyPadX = 18
        val camDenyPadY = 20
        val camDenyIcon = 30
        val camDenyGap = 10
        val camDenyHint = 12
        val camDenyTextGap = 6
        val cropHeadTop = 20
        val cropHeadBottom = 12
        val cropTop = 16
        val cropEdge = 1
        val cropHandle = 18
        val cropHandleStroke = 2
        val cropMin = 48
        val cropHintTop = 12
        val cropHintBottom = 14
        val cropRowGap = 8
        val cropRowBottom = 14
        val cropAdjustTop = 10
        val cropAdjustBottom = 4
        val ocrTop = 16
        val ocrThumb = 44
        val ocrThumbGap = 8
        val ocrRowBottom = 12
        val ocrInputGap = 12
        val ocrHintBottom = 14
        val ocrGhostTop = 11
        val ocrGhostBottom = 12
        val ocrFailGap = 16
        val ocrNoteTop = 6
    }

    /** 图标尺寸（规范 6），单位 dp */
    object Icon {
        val n30 = 30
        val n26 = 26
        val n24 = 24
        val n21 = 21
        val n20 = 20
        val n18 = 18
        val n15 = 15
        val n14 = 14
        val n13 = 13
        val n12 = 12
    }

    /** 投影层参数（规范 2.2/2.3）：CSS box-shadow 解析结果，实现端按平台等效映射 */
    data class ShadowLayer(
        val offsetX: Float,
        val offsetY: Float,
        val blur: Float,
        val spread: Float,
        val color: Color,
    )

    object Shadow {
        val fabLight: List<ShadowLayer> = listOf(
            ShadowLayer(0f, 8f, 18f, 0f, Color(0x38008000)),
        )
        val fabDark: List<ShadowLayer> = listOf(
            ShadowLayer(0f, 0f, 0f, 1f, Color(0x5950C878)),
            ShadowLayer(0f, 8f, 24f, 0f, Color(0x4C50C878)),
        )
    }
}
