package com.chenyubang.core.data

private const val MOCK_IDIOMS = 3
private const val MOCK_EXAMPLES = 2
private const val PROGRESS_MAX = 100

/** P16 状态；CHECKING / DOWNLOADING 始终保留当前已安装版本与条数。 */
enum class PackVersionStatus {
    BUILTIN,
    OFFLINE,
    CHECKING,
    DOWNLOADING,
    UP_TO_DATE,
    UPDATE_OK,
    RECOVERABLE_FAIL,
    TEST_MOCK,
}

data class PackVersionState(
    val status: PackVersionStatus,
    val version: String,
    val idiomCount: Int = 0,
    val exampleCount: Int = 0,
    val mockIdiomCount: Int = 0,
    val mockExampleCount: Int = 0,
    val isTest: Boolean = false,
    val reason: String? = null,
    val progress: Int = 0,
) {
    val canRetry: Boolean
        get() = status == PackVersionStatus.RECOVERABLE_FAIL || status == PackVersionStatus.TEST_MOCK
}

data class PackMock(
    val idiomCount: Int = MOCK_IDIOMS,
    val exampleCount: Int = MOCK_EXAMPLES,
)

/** vX.Y.Z 逐段去前导零后按位数/字典序比较，不转 Int，巨大版本号也安全。 */
object PackVersion {
    fun compare(
        left: String,
        right: String,
    ): Int {
        val a = versionParts(left)
        val b = versionParts(right)
        for (index in 0 until VERSION_PARTS) {
            val result = comparePart(a[index], b[index])
            if (result != 0) return result
        }
        return 0
    }

    private fun versionParts(value: String): List<String> {
        require(Regex("^v\\d+\\.\\d+\\.\\d+$").matches(value)) { "Invalid pack version" }
        return value.removePrefix("v").split('.').map { it.trimStart('0').ifEmpty { "0" } }
    }

    private fun comparePart(
        left: String,
        right: String,
    ): Int {
        if (left.length != right.length) return left.length.compareTo(right.length)
        return left.compareTo(right)
    }

    private const val VERSION_PARTS = 3
}

fun PackVersionState.downloading(progress: Int): PackVersionState =
    copy(status = PackVersionStatus.DOWNLOADING, progress = progress.coerceIn(0, PROGRESS_MAX), reason = null)

fun testPackState(mock: PackMock = PackMock()): PackVersionState =
    PackVersionState(
        status = PackVersionStatus.TEST_MOCK,
        version = "测试数据（TEST）",
        isTest = true,
        idiomCount = mock.idiomCount,
        exampleCount = mock.exampleCount,
        mockIdiomCount = mock.idiomCount,
        mockExampleCount = mock.exampleCount,
        reason = "题库未获取成功",
    )
