package com.chenyubang.ocr

import android.content.Context
import android.graphics.Bitmap
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 端侧实现：Paddle-Lite 2.10 + PP-OCRv3 slim det/rec，模型随包、零联网。
 * 推理全程在 [Dispatchers.Default]，同一实例串行执行（PaddlePredictor 非线程安全）。
 * 真机精度复测后置（B1）；代理实测 18/20＝90% 只作参考，不视为达标。
 */
class PaddleOcrEngine(context: Context) : OcrEngine {
    private val modelStore = OcrModelStore(context)
    private val mutex = Mutex()

    @Volatile
    private var session: Session? = null

    override suspend fun recognize(imagePath: String): OcrResult =
        withContext(Dispatchers.Default) {
            val started = System.nanoTime()
            mutex.withLock {
                val lines = runCatching { runPipeline(imagePath) }.getOrNull().orEmpty()
                val (stem, options) = OcrLineClassifier.classify(lines)
                OcrResult(
                    stem = stem.ifBlank { null },
                    options = options,
                    rawLines = lines,
                    durationMs = (System.nanoTime() - started) / NANOS_PER_MILLI,
                )
            }
        }

    private fun runPipeline(imagePath: String): List<String> {
        val active = ensureSession()
        val source = OcrBitmapOps.upscale(OcrBitmapOps.decode(imagePath))
        val boxes = detect(active.detector, source)
        val lines = boxes.mapNotNull { box -> readLine(active.recognizer, source, box, active.dictionary) }
        return OcrLineGrouper.group(lines)
    }

    private fun ensureSession(): Session {
        val existing = session
        if (existing != null) return existing
        val created = loadSession()
        session = created
        return created
    }

    private fun loadSession(): Session {
        val files = modelStore.files()
        return Session(
            detector = createPredictor(files.detector),
            recognizer = createPredictor(files.recognizer),
            dictionary = readDictionary(files.dictionary),
        )
    }

    private fun createPredictor(model: File): PaddlePredictor {
        val config =
            MobileConfig().apply {
                setModelFromFile(model.absolutePath)
                setThreads(THREADS)
                setPowerMode(PowerMode.LITE_POWER_HIGH)
            }
        return checkNotNull(PaddlePredictor.createPaddlePredictor(config)) { "predictor init failed" }
    }

    /** rec 模型训练时 use_space_char=true：字符类 = 字典 + 空格，调用方须追加。 */
    private fun readDictionary(file: File): List<String> =
        file.readLines(Charsets.UTF_8).filter { it.isNotEmpty() } + SPACE_TOKEN

    private fun detect(
        predictor: PaddlePredictor,
        source: Bitmap,
    ): List<OcrDetPostProcess.Box> {
        val (width, height) = OcrTensor.detSize(source.width, source.height)
        val pixels = OcrBitmapOps.pixels(OcrBitmapOps.scaled(source, width, height))
        val tensor = checkNotNull(predictor.getInput(0)) { "det input missing" }
        tensor.resize(longArrayOf(1, CHANNEL_COUNT.toLong(), height.toLong(), width.toLong()))
        check(tensor.setData(OcrTensor.detTensor(pixels, width, height))) { "det data rejected" }
        check(predictor.run()) { "det run failed" }
        val output = checkNotNull(predictor.getOutput(0)) { "det output missing" }
        val shape = output.shape()
        val scaleX = source.width.toFloat() / width
        val scaleY = source.height.toFloat() / height
        return OcrDetPostProcess.boxes(output.getFloatData(), width, height)
            .map { it.scaled(scaleX, scaleY) }
            .filter { it.width >= MIN_BOX_SIDE && it.height >= MIN_BOX_SIDE }
    }

    private fun readLine(
        predictor: PaddlePredictor,
        source: Bitmap,
        box: OcrDetPostProcess.Box,
        dictionary: List<String>,
    ): OcrLineGrouper.Line? =
        OcrBitmapOps.crop(source, box)?.let { crop ->
            recognizeCrop(predictor, crop, dictionary)?.let { text -> OcrLineGrouper.Line(box, text) }
        }

    /** 单行失败（空框、低置信）只丢这一行，不拖垮整张图。 */
    private fun recognizeCrop(
        predictor: PaddlePredictor,
        crop: Bitmap,
        dictionary: List<String>,
    ): String? =
        runCatching { runModel(predictor, crop, dictionary) }
            .getOrNull()
            ?.takeIf { it.score >= MIN_LINE_SCORE }
            ?.text
            ?.takeIf { it.isNotBlank() }

    private fun runModel(
        predictor: PaddlePredictor,
        crop: Bitmap,
        dictionary: List<String>,
    ): CtcDecoder.Decoded? {
        val (width, height) = OcrTensor.recSize(crop.width, crop.height)
        val pixels = OcrBitmapOps.pixels(OcrBitmapOps.scaled(crop, width, height))
        val tensor = checkNotNull(predictor.getInput(0)) { "rec input missing" }
        tensor.resize(longArrayOf(1, CHANNEL_COUNT.toLong(), height.toLong(), width.toLong()))
        check(tensor.setData(OcrTensor.recTensor(pixels, width, height))) { "rec data rejected" }
        check(predictor.run()) { "rec run failed" }
        val output = checkNotNull(predictor.getOutput(0)) { "rec output missing" }
        val shape = output.shape()
        return CtcDecoder.decode(output.getFloatData(), shape[1].toInt(), shape[2].toInt(), dictionary)
    }

    private class Session(
        val detector: PaddlePredictor,
        val recognizer: PaddlePredictor,
        val dictionary: List<String>,
    )

    private companion object {
        const val THREADS = 2
        const val CHANNEL_COUNT = 3
        const val MIN_LINE_SCORE = 0.5f
        const val MIN_BOX_SIDE = 2f
        const val NANOS_PER_MILLI = 1_000_000L
        const val SPACE_TOKEN = " "
    }
}
