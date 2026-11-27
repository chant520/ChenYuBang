package com.chenyubang.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 纯 JVM 部分：上采样、张量归一、DB 后处理、CTC 解码、行合并与题干/选项切分、模型文件校验。 */
class OcrPipelineHelpersTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `短边不足1400按比例放大`() {
        assertEquals(2450 to 1400, OcrUpscale.targetSize(700, 400))
    }

    @Test
    fun `长边超4000再压回`() {
        assertEquals(4000 to 800, OcrUpscale.targetSize(5000, 1000))
    }

    @Test
    fun `短边已足够时保持原尺寸`() {
        assertEquals(3000 to 2000, OcrUpscale.targetSize(3000, 2000))
    }

    @Test
    fun `检测尺寸不超过960且对齐32`() {
        assertEquals(960 to 480, OcrTensor.detSize(1000, 500))
        assertEquals(96 to 32, OcrTensor.detSize(100, 50))
    }

    @Test
    fun `极端小图也对齐到32`() {
        assertEquals(32 to 32, OcrTensor.detSize(10, 10))
    }

    @Test
    fun `检测张量按BGR均值方差归一`() {
        val tensor = OcrTensor.detTensor(intArrayOf(0xFFFFFFFF.toInt()), 1, 1)
        assertEquals(3, tensor.size)
        assertEquals((1f - 0.485f) / 0.229f, tensor[0], 1e-5f)
        assertEquals((1f - 0.456f) / 0.224f, tensor[1], 1e-5f)
        assertEquals((1f - 0.406f) / 0.225f, tensor[2], 1e-5f)
    }

    @Test
    fun `识别张量按BGR顺序归一`() {
        val tensor = OcrTensor.recTensor(intArrayOf(0xFFFF0000.toInt()), 1, 1)
        assertEquals(-1f, tensor[0], 1e-5f)
        assertEquals(-1f, tensor[1], 1e-5f)
        assertEquals(1f, tensor[2], 1e-5f)
    }

    @Test
    fun `识别尺寸高压到48宽按比例`() {
        assertEquals(200 to 48, OcrTensor.recSize(300, 72))
        assertEquals(1600 to 48, OcrTensor.recSize(9000, 270))
    }

    @Test
    fun `DB后处理取连通域并外扩`() {
        val probability = floatArrayOf(1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f)
        val boxes = OcrDetPostProcess.boxes(probability, width = 4, height = 2)
        assertEquals(1, boxes.size)
        val box = boxes.single()
        assertEquals(1f, box.score, 1e-6f)
        assertEquals(0f, box.left, 1e-6f)
        assertEquals(2.8f, box.right, 1e-5f)
        assertEquals(2f, box.bottom, 1e-5f)
    }

    @Test
    fun `DB后处理低于分数阈值不产框`() {
        val probability = FloatArray(8) { 0.4f }
        assertTrue(OcrDetPostProcess.boxes(probability, width = 4, height = 2).isEmpty())
    }

    @Test
    fun `框坐标可放回上采样图尺度`() {
        val box = OcrDetPostProcess.Box(1f, 2f, 3f, 4f, 0.9f).scaled(2f, 3f)
        assertEquals(OcrDetPostProcess.Box(2f, 6f, 6f, 12f, 0.9f), box)
    }

    @Test
    fun `CTC跳过空白与连续重复`() {
        val probabilities =
            floatArrayOf(
                0.1f, 0.9f, 0f,
                0.1f, 0.9f, 0f,
                0.9f, 0.05f, 0.05f,
                0.1f, 0.1f, 0.8f,
            )
        val decoded =
            CtcDecoder.decode(
                probabilities,
                timeSteps = 4,
                classes = 3,
                dictionary = listOf("a", "b"),
            )
        assertEquals("ab", decoded?.text)
        assertEquals(0.85f, decoded?.score ?: 0f, 1e-5f)
    }

    @Test
    fun `CTC全空白返回null`() {
        val probabilities = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
        assertNull(CtcDecoder.decode(probabilities, timeSteps = 2, classes = 3, dictionary = listOf("a", "b")))
    }

    @Test
    fun `同一行两段按从左到右拼接`() {
        val lines =
            listOf(
                OcrLineGrouper.Line(OcrDetPostProcess.Box(60f, 12f, 90f, 28f, 0.9f), "干"),
                OcrLineGrouper.Line(OcrDetPostProcess.Box(0f, 10f, 50f, 30f, 0.9f), "题"),
            )
        assertEquals(listOf("题干"), OcrLineGrouper.group(lines))
    }

    @Test
    fun `不同行按上到下排序`() {
        val lines =
            listOf(
                OcrLineGrouper.Line(OcrDetPostProcess.Box(0f, 100f, 60f, 120f, 0.9f), "第二行"),
                OcrLineGrouper.Line(OcrDetPostProcess.Box(0f, 0f, 60f, 20f, 0.9f), "第一行"),
            )
        assertEquals(listOf("第一行", "第二行"), OcrLineGrouper.group(lines))
    }

    @Test
    fun `双列垂直错位按y行x列聚类不丢选项`() {
        val lines =
            listOf(
                OcrLineGrouper.Line(OcrDetPostProcess.Box(0f, 0f, 300f, 20f, 0.9f), "题干"),
                OcrLineGrouper.Line(OcrDetPostProcess.Box(0f, 100f, 140f, 120f, 0.9f), "A．甲"),
                // B 标记漏识且与 A 垂直错位（重叠 40% < 50%），x 落在右列。
                OcrLineGrouper.Line(OcrDetPostProcess.Box(160f, 112f, 300f, 132f, 0.9f), "乙"),
                OcrLineGrouper.Line(OcrDetPostProcess.Box(0f, 140f, 140f, 160f, 0.9f), "C．丙"),
                OcrLineGrouper.Line(OcrDetPostProcess.Box(160f, 152f, 300f, 172f, 0.9f), "丁"),
            )
        val classified = OcrLineClassifier.classifyWithBoxes(lines)
        assertEquals("题干", classified.stem)
        assertEquals(mapOf("A" to "甲", "B" to "乙", "C" to "丙", "D" to "丁"), classified.options)
        assertFalse(classified.guessed)
    }

    @Test
    fun `末四行长或有句末标点时不猜选项`() {
        val longTail =
            listOf(
                "题干第一行",
                "题干第二行",
                "题干第三行",
                "题干第四行",
                "这是一个很长的陈述句不能当选项",
                "另一个同样很长的陈述句不能当选项",
                "再一行很长的陈述句也不是选项",
                "最后一行很长的陈述句也不是选项",
            )
        val longResult = OcrLineClassifier.classifyDetailed(longTail)
        assertTrue(longResult.options.isEmpty())
        assertFalse(longResult.guessed)
        assertEquals(longTail.joinToString("\n"), longResult.stem)

        val punctuated = listOf("题干一", "题干二", "题干三", "题干四", "甲。", "乙！", "丙？", "丁；")
        val punctuatedResult = OcrLineClassifier.classifyDetailed(punctuated)
        assertTrue(punctuatedResult.options.isEmpty())
        assertFalse(punctuatedResult.guessed)
    }

    @Test
    fun `末四行短且无标点时回填并标记推测`() {
        val result =
            OcrLineClassifier.classifyDetailed(
                listOf("题干第一行", "题干第二行", "题干第三行", "题干第四行", "同气连枝", "异曲同工", "同日而语", "同声相应"),
            )
        assertTrue(result.guessed)
        assertEquals(mapOf("A" to "同气连枝", "B" to "异曲同工", "C" to "同日而语", "D" to "同声相应"), result.options)
    }

    @Test
    fun `按A-D标记切成题干与选项`() {
        val (stem, options) =
            OcrLineClassifier.classify(
                listOf("下列成语使用正确的是（ ）", "A．首当其冲", "B. 差强人意", "C、罄竹难书", "D）同日而语"),
            )
        assertEquals("下列成语使用正确的是（ ）", stem)
        assertEquals(mapOf("A" to "首当其冲", "B" to "差强人意", "C" to "罄竹难书", "D" to "同日而语"), options)
    }

    @Test
    fun `双列两选项并成一行按标记切开`() {
        val (_, options) =
            OcrLineClassifier.classify(listOf("题干", "A．异曲同工B、同气连枝", "C、同日而语D）同声相应"))
        assertEquals(
            mapOf("A" to "异曲同工", "B" to "同气连枝", "C" to "同日而语", "D" to "同声相应"),
            options,
        )
    }

    @Test
    fun `双列四个选项全并成一行按标记切开`() {
        val (_, options) =
            OcrLineClassifier.classify(listOf("题干", "A. 甲B、乙C)丙D：丁"))
        assertEquals(mapOf("A" to "甲", "B" to "乙", "C" to "丙", "D" to "丁"), options)
    }

    @Test
    fun `选项正文英文字母不误切成选项`() {
        val (_, options) = OcrLineClassifier.classify(listOf("题干", "A．AB测试正确"))
        assertEquals(mapOf("A" to "AB测试正确"), options)
    }

    @Test
    fun `标记后的无标记行并入上一个选项`() {
        val (_, options) = OcrLineClassifier.classify(listOf("题干", "A．千里", "之行／始于足下"))
        assertEquals(mapOf("A" to "千里之行／始于足下"), options)
    }

    @Test
    fun `全角标记归一成半角`() {
        val (_, options) = OcrLineClassifier.classify(listOf("题干", "Ａ．一目了然"))
        assertEquals(mapOf("A" to "一目了然"), options)
    }

    @Test
    fun `选项标记全部漏识时末四行回填ABCD`() {
        val lines =
            listOf(
                "题干第一行",
                "题干第二行",
                "题干第三行",
                "题干第四行",
                "同气连枝",
                "异曲同工",
                "同日而语",
                "同声相应",
            )
        val (stem, options) = OcrLineClassifier.classify(lines)
        assertEquals("题干第一行\n题干第二行\n题干第三行\n题干第四行", stem)
        assertEquals(mapOf("A" to "同气连枝", "B" to "异曲同工", "C" to "同日而语", "D" to "同声相应"), options)
    }

    @Test
    fun `无标记但不足五行不猜选项`() {
        val (stem, options) = OcrLineClassifier.classify(listOf("题干第一行", "题干第二行", "选项一", "选项二"))
        assertEquals("题干第一行\n题干第二行\n选项一\n选项二", stem)
        assertTrue(options.isEmpty())
    }

    @Test
    fun `已有部分标记时不启用末四行猜测`() {
        val (stem, options) = OcrLineClassifier.classify(listOf("题干", "A. 甲", "乙", "丙", "丁"))
        assertEquals("题干", stem)
        assertEquals(mapOf("A" to "甲乙丙丁"), options)
    }

    @Test
    fun `空识别结果判空`() {
        assertTrue(OcrResult(stem = null, options = emptyMap(), rawLines = emptyList(), durationMs = 0).isEmpty)
        assertFalse(OcrResult(stem = "题干", options = emptyMap(), rawLines = listOf("题干"), durationMs = 1).isEmpty)
    }

    @Test
    fun `失败结果映射P19文案且不携带识别文本`() {
        val failed =
            OcrResult(
                stem = null,
                options = emptyMap(),
                rawLines = emptyList(),
                durationMs = 5,
                failureReason = "IOException",
            )
        assertTrue(failed.failed)
        assertTrue(failed.isEmpty)
        assertEquals(OcrResult.P19_FAILURE_TEXT, failed.failureText)
        assertNull(failed.stem)

        val ok = OcrResult(stem = "题干", options = mapOf("A" to "甲"), rawLines = listOf("题干", "A. 甲"), durationMs = 6)
        assertFalse(ok.failed)
        assertNull(ok.failureText)
    }

    @Test
    fun `模型校验接受正确字节数与v2_10版本头`() {
        val file = File(temporaryFolder.root, "det.nb").apply { writeBytes(modelBytes(OCR_MODEL_VERSION)) }
        assertTrue(OcrModelValidator.isValid(file, file.length(), versioned = true))
        assertFalse(OcrModelValidator.isValid(file, file.length() + 1, versioned = true))
    }

    @Test
    fun `模型版本头不符即判无效字典不校验版本头`() {
        val file = File(temporaryFolder.root, "bad.nb").apply { writeBytes(modelBytes("v3.00")) }
        assertFalse(OcrModelValidator.isValid(file, file.length(), versioned = true))
        assertTrue(OcrModelValidator.isValid(file, file.length(), versioned = false))
        assertFalse(OcrModelValidator.isValid(File(temporaryFolder.root, "missing.nb"), 8, versioned = true))
    }

    private fun modelBytes(version: String): ByteArray =
        byteArrayOf(2, 0) + version.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
}
