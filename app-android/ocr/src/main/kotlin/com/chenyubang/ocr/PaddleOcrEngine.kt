package com.chenyubang.ocr

import android.content.Context
import android.graphics.Bitmap
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import com.baidu.paddle.lite.Tensor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 端侧实现：Paddle-Lite 2.10 + 官方 PP-OCRv3 det/rec .nb（opt v2.10），模型随包、零联网。
 * det/rec 模型与 so 的 opt 版本必须一致，否则 native 层 Fatal；替换任一模型前先核对 .nb 版本头。
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
                runCatching { runPipeline(imagePath) }.fold(
                    onSuccess = { lines ->
                        val classified = OcrLineClassifier.classifyWithBoxes(lines)
                        OcrResult(
                            stem = classified.stem.ifBlank { null },
                            options = classified.options,
                            rawLines = lines.map { it.text },
                            durationMs = elapsedMillis(started),
                            guessedOptions = classified.guessed,
                        )
                    },
                    onFailure = { failure ->
                        if (failure is CancellationException) throw failure
                        // 失败原因只进 OcrResult 并映射到 P19 文案；不打印堆栈，更不记录识别文本/学习内容。
                        invalidateSession()
                        OcrResult(
                            stem = null,
                            options = emptyMap(),
                            rawLines = emptyList(),
                            durationMs = elapsedMillis(started),
                            failureReason = failure.javaClass.simpleName,
                        )
                    },
                )
            }
        }

    private fun runPipeline(imagePath: String): List<OcrLineGrouper.Line> {
        val active = ensureSession()
        val decoded = OcrBitmapOps.decode(imagePath)
        val source = OcrBitmapOps.upscale(decoded)
        return try {
            val boxes = detect(active, source)
            val lines = boxes.mapNotNull { box -> readLine(active, source, box, active.dictionary) }
            OcrLineGrouper.groupRows(lines).flatten()
        } finally {
            // upscale 尺寸一致时返回 decoded 本身，只回收一次；不回收调用方持有的图。
            if (source !== decoded) source.recycle()
            decoded.recycle()
        }
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
        var detector: PaddlePredictor? = null
        var recognizer: PaddlePredictor? = null
        return runCatching {
            val createdDetector = createPredictor(files.detector).also { detector = it }
            val createdRecognizer = createPredictor(files.recognizer).also { recognizer = it }
            Session(createdDetector, createdRecognizer, readDictionary(files.dictionary))
        }.getOrElse { failure ->
            if (failure is CancellationException) throw failure
            recognizer?.release()
            detector?.release()
            throw failure
        }
    }

    /** Session 替换/销毁时显式释放 native 指针，不依赖 finalize 的 GC 时机。 */
    private fun invalidateSession() {
        session?.release()
        session = null
    }

    private fun createPredictor(model: File): PaddlePredictor {
        val config =
            MobileConfig().apply {
                setModelFromFile(model.absolutePath)
                setThreads(THREADS)
                setPowerMode(PowerMode.LITE_POWER_HIGH)
            }
        val predictor = PaddlePredictor.createPaddlePredictor(config)
        return checkNotNull(predictor) { "predictor init failed: ${model.name}" }
    }

    private fun detect(
        active: Session,
        source: Bitmap,
    ): List<OcrDetPostProcess.Box> {
        val (width, height) = OcrTensor.detSize(source.width, source.height)
        OcrBitmapOps.scaledPixels(source, width, height) { pixels ->
            active.detInput.resize(longArrayOf(1, CHANNEL_COUNT.toLong(), height.toLong(), width.toLong()))
            check(active.detInput.setData(OcrTensor.detTensor(pixels, width, height))) { "det data rejected" }
        }
        check(active.detector.run()) { "det run failed" }
        val scaleX = source.width.toFloat() / width
        val scaleY = source.height.toFloat() / height
        return OcrDetPostProcess.boxes(active.detOutput.getFloatData(), width, height)
            .map { it.scaled(scaleX, scaleY) }
            .filter { it.width >= MIN_BOX_SIDE && it.height >= MIN_BOX_SIDE }
    }

    private fun readLine(
        active: Session,
        source: Bitmap,
        box: OcrDetPostProcess.Box,
        dictionary: List<String>,
    ): OcrLineGrouper.Line? {
        val crop = OcrBitmapOps.crop(source, box) ?: return null
        return try {
            recognizeCrop(active, crop, dictionary)?.let { text -> OcrLineGrouper.Line(box, text) }
        } finally {
            crop.recycle()
        }
    }

    /** 单行失败（空框、低置信）只丢这一行，不拖垮整张图。 */
    private fun recognizeCrop(
        active: Session,
        crop: Bitmap,
        dictionary: List<String>,
    ): String? =
        runCatching { runModel(active, crop, dictionary) }
            .getOrNull()
            ?.takeIf { it.score >= MIN_LINE_SCORE }
            ?.text
            ?.takeIf { it.isNotBlank() }

    private fun runModel(
        active: Session,
        crop: Bitmap,
        dictionary: List<String>,
    ): CtcDecoder.Decoded? {
        val (width, height) = OcrTensor.recSize(crop.width, crop.height)
        OcrBitmapOps.scaledPixels(crop, width, height) { pixels ->
            active.recInput.resize(longArrayOf(1, CHANNEL_COUNT.toLong(), height.toLong(), width.toLong()))
            check(active.recInput.setData(OcrTensor.recTensor(pixels, width, height))) { "rec data rejected" }
        }
        check(active.recognizer.run()) { "rec run failed" }
        val output = active.recOutput
        val shape = output.shape()
        return CtcDecoder.decode(output.getFloatData(), shape[1].toInt(), shape[2].toInt(), dictionary)
    }

    /** 输入/输出 Tensor 包装在 Session 内缓存复用，替换/销毁时与 predictor 一起显式释放。 */
    private class Session(
        val detector: PaddlePredictor,
        val recognizer: PaddlePredictor,
        val dictionary: List<String>,
    ) {
        val detInput: Tensor = checkNotNull(detector.getInput(0)) { "det input missing" }
        val detOutput: Tensor = checkNotNull(detector.getOutput(0)) { "det output missing" }
        val recInput: Tensor = checkNotNull(recognizer.getInput(0)) { "rec input missing" }
        val recOutput: Tensor = checkNotNull(recognizer.getOutput(0)) { "rec output missing" }

        fun release() {
            detInput.release()
            detOutput.release()
            recInput.release()
            recOutput.release()
            detector.release()
            recognizer.release()
        }
    }

    private companion object {
        const val THREADS = 2
        const val CHANNEL_COUNT = 3
        const val MIN_LINE_SCORE = 0.5f
        const val MIN_BOX_SIDE = 2f
    }
}

private const val NANOS_PER_MILLI = 1_000_000L
private const val SPACE_TOKEN = " "

private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started) / NANOS_PER_MILLI

/** rec 模型训练时 use_space_char=true：字符类 = 字典 + 空格，调用方须追加。 */
private fun readDictionary(file: File): List<String> =
    file.readLines(Charsets.UTF_8).filter { it.isNotEmpty() } + SPACE_TOKEN
