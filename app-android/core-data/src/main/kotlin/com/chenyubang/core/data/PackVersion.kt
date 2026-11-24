package com.chenyubang.core.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val VERSION_PARTS = 3
private const val MOCK_IDIOMS = 3
private const val MOCK_EXAMPLES = 2

/** P16 更新状态；同一时刻只取一个值，CHECKING 不改动已展示的版本与条数。 */
enum class PackVersionStatus {
    BUILTIN,
    CHECKING,
    DOWNLOADING,
    UP_TO_DATE,
    UPDATE_OK,
    RECOVERABLE_FAIL,
    TEST_MOCK,
}

/**
 * P16 状态模型。真实题库条数只在 idiomCount/exampleCount；mock 条数单独放 mock*Count 且
 * isTest=true，绝不混写。RECOVERABLE_FAIL 保留 base.version 供「重试」。
 */
@Suppress("LongParameterList")
data class PackVersionState(
    val status: PackVersionStatus,
    val version: String,
    val idiomCount: Int = 0,
    val exampleCount: Int = 0,
    val mockIdiomCount: Int = 0,
    val mockExampleCount: Int = 0,
    val isTest: Boolean = false,
    val reason: String? = null,
    /** DOWNLOADING 的 0..100 整数进度，仅显示用；其余态恒为 0。 */
    val progress: Int = 0,
) {
    val canRetry: Boolean
        get() =
            status == PackVersionStatus.RECOVERABLE_FAIL ||
                status == PackVersionStatus.TEST_MOCK
}

/** mock 条数取 shared/fixtures 样例实际数（3 成语 / 2 真题），不冒充真实题库。 */
data class PackMock(
    val idiomCount: Int = MOCK_IDIOMS,
    val exampleCount: Int = MOCK_EXAMPLES,
)

/**
 * 4.4 题库版本离线态数据（P16）。三级兜底不可颠倒：内置包 → 联网 → TEST mock。
 * 本类只产出状态，不写数据库；UPDATE_OK 由调用方用 PackImporter 落地题库包。
 * 包校验失败（sha256 不符 / manifest 异常 / 下载中断）＝橙可恢复，保留当前版本并给重试。
 */
class PackVersion(
    context: Context,
    private val source: PackUpdateSource = HttpPackUpdateSource(),
    private val builtinAsset: String = DEFAULT_ASSET,
    private val mock: PackMock = PackMock(),
) {
    private val appContext = context.applicationContext

    /** 离线加载：内置包可读即 BUILTIN；不可读才尝试联网；两者都失败才 TEST_MOCK。 */
    suspend fun load(url: String? = null): PackVersionState =
        withContext(Dispatchers.IO) {
            val base = runCatching { readBuiltin() }.getOrNull()
            if (base != null) return@withContext base
            if (!url.isNullOrBlank()) {
                val fetched = source.fetch(url)
                if (fetched is PackUpdateFetch.Success) {
                    val remote =
                        runCatching { ValidatedPack.readValidated(fetched.zipBytes.inputStream()) }.getOrNull()
                    if (remote != null) return@withContext remoteState(remote, null)
                }
            }
            mockState()
        }

    /** 进更新中态：仅换 status，版本号与条数沿用当前状态。 */
    fun checking(current: PackVersionState): PackVersionState = current.copy(status = PackVersionStatus.CHECKING)

    /** 进下载中态：仅换 status 与 0..100 整数进度，版本号与条数沿用当前状态，供快照注入显示。 */
    fun downloading(
        current: PackVersionState,
        progress: Int,
    ): PackVersionState = current.copy(status = PackVersionStatus.DOWNLOADING, progress = progress.coerceIn(0, 100))

    /**
     * E18「继续离线使用」：回到当前内置包离线态，仅换 status/reason/progress；
     * 不发请求、不写库、不改版本号与条数。
     */
    fun offline(current: PackVersionState): PackVersionState =
        current.copy(status = PackVersionStatus.BUILTIN, reason = null, progress = 0)

    /** 手动「检查题库更新」。远端更高 → UPDATE_OK；相等/更低 → UP_TO_DATE；任一步失败 → 可恢复。 */
    suspend fun check(url: String?): PackVersionState =
        withContext(Dispatchers.IO) {
            val base = runCatching { readBuiltin() }.getOrNull()
            if (url.isNullOrBlank()) return@withContext recoverable(base, "未配置题库更新地址")
            when (val fetched = source.fetch(url)) {
                is PackUpdateFetch.Unavailable -> recoverable(base, "未配置题库更新地址")
                is PackUpdateFetch.Recoverable -> recoverable(base, fetched.reason)
                is PackUpdateFetch.Success -> {
                    val remote =
                        runCatching { ValidatedPack.readValidated(fetched.zipBytes.inputStream()) }.getOrNull()
                    if (remote == null) recoverable(base, "题库包校验失败（摘要或字段不符）") else remoteState(remote, base)
                }
            }
        }

    private fun readBuiltin(): PackVersionState =
        appContext.assets.open(builtinAsset).use { input ->
            val pack = ValidatedPack.readValidated(input)
            PackVersionState(
                status = PackVersionStatus.BUILTIN,
                version = pack.packVersion,
                idiomCount = pack.counts.idioms,
                exampleCount = pack.counts.examples,
            )
        }

    private fun mockState(): PackVersionState =
        PackVersionState(
            status = PackVersionStatus.TEST_MOCK,
            version = TEST_VERSION,
            isTest = true,
            mockIdiomCount = mock.idiomCount,
            mockExampleCount = mock.exampleCount,
            reason = "题库未获取成功",
        )

    private fun recoverable(
        base: PackVersionState?,
        reason: String,
    ): PackVersionState = base?.copy(status = PackVersionStatus.RECOVERABLE_FAIL, reason = reason) ?: mockState()

    private fun remoteState(
        remote: ValidatedPack,
        base: PackVersionState?,
    ): PackVersionState =
        if (base == null || compare(remote.packVersion, base.version) > 0) {
            PackVersionState(
                status = PackVersionStatus.UPDATE_OK,
                version = remote.packVersion,
                idiomCount = remote.counts.idioms,
                exampleCount = remote.counts.examples,
            )
        } else {
            base.copy(status = PackVersionStatus.UP_TO_DATE)
        }

    private fun compare(
        left: String,
        right: String,
    ): Int {
        val a = left.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until VERSION_PARTS) {
            val result = a.getOrElse(index) { 0 }.compareTo(b.getOrElse(index) { 0 })
            if (result != 0) return result
        }
        return 0
    }

    private companion object {
        const val DEFAULT_ASSET = "pack-v1.0.0.zip"
        const val TEST_VERSION = "测试数据（TEST）"
    }
}
