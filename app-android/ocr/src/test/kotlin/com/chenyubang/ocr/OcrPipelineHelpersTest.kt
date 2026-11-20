package com.chenyubang.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯 JVM 部分：上采样、张量归一、DB 后处理、CTC 解码、行合并与题干/选项切分。 */
class OcrPipelineHelpersTest {
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
    fun `识别张量按零点五归一`() {
        val tensor = OcrTensor.recTensor(intArrayOf(0xFF808080.toInt()), 1, 1)
        assertEquals((128f / 255f - 0.5f) / 0.5f, tensor[0], 1e-5f)
        assertEquals(tensor[0], tensor[1], 1e-6f)
        assertEquals(tensor[1], tensor[2], 1e-6f)
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
        val decoded = CtcDecoder.decode(probabilities, timeSteps = 4, classes = 3, dictionary = listOf("a", "b"))
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
    fun `按A-D标记切成题干与选项`() {
        val (stem, options) =
            OcrLineClassifier.classify(
                listOf("下列成语使用正确的是（ ）", "A．首当其冲", "B. 差强人意", "C、罄竹难书", "D）同日而语"),
            )
        assertEquals("下列成语使用正确的是（ ）", stem)
        assertEquals(mapOf("A" to "首当其冲", "B" to "差强人意", "C" to "罄竹难书", "D" to "同日而语"), options)
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
    fun `空识别结果判空`() {
        assertTrue(OcrResult(stem = null, options = emptyMap(), rawLines = emptyList(), durationMs = 0).isEmpty)
        assertFalse(OcrResult(stem = "题干", options = emptyMap(), rawLines = listOf("题干"), durationMs = 1).isEmpty)
    }
}
