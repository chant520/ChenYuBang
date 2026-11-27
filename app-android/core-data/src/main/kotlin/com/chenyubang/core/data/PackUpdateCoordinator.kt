package com.chenyubang.core.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** P03 / P06 / P16 共用的更新状态与唯一协调器。 */
interface PackUpdateController {
    val state: StateFlow<PackVersionState?>

    suspend fun check(): PackCheckOutcome

    fun continueOffline()

    /** 取消内部协程、清空在途请求并复位状态；调用后 check() 不再写状态。 */
    fun close()
}

enum class PackCheckOutcome { UPDATED, UNCHANGED, FAILED }

class PackUpdateCoordinator(
    context: Context,
    private val importer: PackImporter,
    private val source: PackUpdateSource,
    internal val store: PackInstallStore = PackInstallStore(context),
    private val mock: PackMock = PackMock(),
) : PackUpdateController {
    private val _state = MutableStateFlow<PackVersionState?>(null)
    override val state: StateFlow<PackVersionState?> = _state.asStateFlow()
    private val operation = Mutex()
    private val startOperation = Any()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var inFlight: Deferred<PackCheckOutcome>? = null
    private val closed = AtomicBoolean(false)

    /** close() 前统一的状态写入口，关闭后所有回写（含取消分支）均为空操作。 */
    private fun setState(value: PackVersionState?) {
        if (!closed.get()) _state.value = value
    }

    /** 启动只恢复本机 ZIP / 内置包，不联网；ZIP 已落盘而导入中断时由这里修复。 */
    suspend fun bootstrap() {
        operation.withLock {
            val pack = store.bootstrap(importer)
            setState(
                when {
                    pack == null -> PackVersionState(PackVersionStatus.RECOVERABLE_FAIL, "暂无可用题库", reason = "题库尚未获取")
                    pack.isMock -> testPackState(PackMock(pack.pack.counts.idioms, pack.pack.counts.examples))
                    else -> installedState(pack.pack, PackVersionStatus.BUILTIN)
                },
            )
        }
    }

    /** 完整请求/安装共用一个 Deferred；第二个进页等待同一结果，不重复发请求。 */
    override suspend fun check(): PackCheckOutcome {
        if (closed.get()) throw CancellationException("PackUpdateCoordinator 已关闭")
        val shared =
            synchronized(startOperation) {
                inFlight ?: scope.async(start = CoroutineStart.LAZY) { performCheck() }.also { deferred ->
                    inFlight = deferred
                    deferred.invokeOnCompletion {
                        synchronized(startOperation) {
                            if (inFlight === deferred) inFlight = null
                        }
                    }
                }
            }
        return shared.await()
    }

    private suspend fun performCheck(): PackCheckOutcome =
        operation.withLock {
            val base = _state.value
            if (base != null) setState(base.copy(status = PackVersionStatus.CHECKING, reason = null, progress = 0))
            runCatching { performCheck(base) }.fold(
                onSuccess = { it },
                onFailure = { error ->
                    if (error is CancellationException) {
                        setState(base)
                        throw error
                    }
                    failed(base, error.message ?: "题库更新失败")
                },
            )
        }

    private suspend fun performCheck(base: PackVersionState?): PackCheckOutcome =
        when (val latest = source.latest()) {
            ChannelManifestResult.Unavailable -> failed(base, "未配置题库更新地址")
            is ChannelManifestResult.Recoverable -> failed(base, latest.reason)
            is ChannelManifestResult.Success -> {
                val installedVersion = base?.let { if (it.isTest) MOCK_PACK_VERSION else it.version }
                if (
                    installedVersion != null &&
                    PackVersion.compare(latest.manifest.packVersion, installedVersion) <= 0
                ) {
                    if (base?.isTest == true) {
                        failed(base, "远端题库版本不高于测试数据")
                    } else {
                        setState(
                            requireNotNull(base).copy(
                                status = PackVersionStatus.UP_TO_DATE,
                                reason = null,
                                progress = 0,
                            ),
                        )
                        PackCheckOutcome.UNCHANGED
                    }
                } else {
                    downloadAndInstall(latest, base)
                }
            }
        }

    private suspend fun downloadAndInstall(
        latest: ChannelManifestResult.Success,
        base: PackVersionState?,
    ): PackCheckOutcome {
        require(store.installed.parentFile?.isDirectory == true || store.installed.parentFile?.mkdirs() == true) {
            "无法创建题库目录"
        }
        val downloaded = File.createTempFile("pack-download-", ".zip", store.installed.parentFile)
        try {
            if (base != null) setState(base.downloading(0))
            when (
                val result =
                    source.download(latest.manifest, downloaded) { progress ->
                        if (base != null) setState(base.downloading(progress))
                    }
            ) {
                is PackDownloadResult.Recoverable -> return failed(base, result.reason)
                PackDownloadResult.Success -> Unit
            }
            require(downloaded.length() == latest.manifest.sizeBytes.toLong()) { "题库下载字节数不符" }
            val validated = downloaded.inputStream().use(importer::validate)
            require(
                validated.packVersion == latest.manifest.packVersion &&
                    validated.builtAt == latest.manifest.builtAt &&
                    validated.counts == latest.manifest.counts &&
                    validated.sha256 == latest.manifest.sha256,
            ) { "题库包与通道清单不一致" }
            store.install(downloaded, importer) { importer.import(it) }
            setState(installedState(validated, PackVersionStatus.UPDATE_OK))
            return PackCheckOutcome.UPDATED
        } finally {
            downloaded.delete()
        }
    }

    private fun failed(
        base: PackVersionState?,
        reason: String,
    ): PackCheckOutcome {
        setState(
            when {
                base?.isTest == true ->
                    base.copy(status = PackVersionStatus.RECOVERABLE_FAIL, reason = reason, progress = 0)
                base == null -> testPackState(mock).copy(status = PackVersionStatus.RECOVERABLE_FAIL, reason = reason)
                else -> base.copy(status = PackVersionStatus.RECOVERABLE_FAIL, reason = reason, progress = 0)
            },
        )
        return PackCheckOutcome.FAILED
    }

    override fun continueOffline() {
        val current = _state.value ?: return
        if (current.isTest) {
            setState(testPackState(PackMock(current.mockIdiomCount, current.mockExampleCount)))
        } else if (current.version.isNotBlank()) {
            setState(current.copy(status = PackVersionStatus.OFFLINE, reason = null, progress = 0))
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val pending: Deferred<PackCheckOutcome>?
        synchronized(startOperation) {
            pending = inFlight
            inFlight = null
        }
        scope.cancel()
        pending?.cancel()
        _state.value = null
    }

    private fun installedState(
        pack: ValidatedPack,
        status: PackVersionStatus,
    ): PackVersionState = PackVersionState(status, pack.packVersion, pack.counts.idioms, pack.counts.examples)

    private companion object {
        const val MOCK_PACK_VERSION = "v0.0.1"
    }
}
