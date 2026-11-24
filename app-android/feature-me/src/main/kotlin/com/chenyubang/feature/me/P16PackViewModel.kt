package com.chenyubang.feature.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.PackVersion
import com.chenyubang.core.data.PackVersionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * P16 题库版本与更新：离线只从 PackVersion.load(null) 取内置包（无 URL，不编造端点）。
 * 手动检查先 checking 再 check(null)；RECOVERABLE_FAIL / TEST_MOCK 从数据层状态映射，不自造数据。
 */
class P16PackViewModel(
    private val packVersion: PackVersion,
) : ViewModel() {
    private val _state = MutableStateFlow<PackVersionState?>(null)
    val state: StateFlow<PackVersionState?> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            try {
                _state.value = packVersion.load(null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 数据层 load 内部已逐级兜底，异常时保留空状态由页面不渲染版本区。
                _state.value = null
            }
        }
    }

    fun check() {
        val current = _state.value ?: return
        viewModelScope.launch {
            _state.value = packVersion.checking(current)
            try {
                _state.value = packVersion.check(null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = current
            }
        }
    }

    /** E18「继续离线使用」：只回内置包离线态，不发请求、不写库、不改版本与条数。 */
    fun continueOffline() {
        val current = _state.value ?: return
        _state.value = packVersion.offline(current)
    }
}
