package com.chenyubang.feature.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chenyubang.core.data.PackUpdateController
import com.chenyubang.core.data.PackVersionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** P16 只观察共享状态；进页不检查，只有按钮调用协调器。 */
class P16PackViewModel(
    private val updates: PackUpdateController,
) : ViewModel() {
    val state: StateFlow<PackVersionState?> = updates.state

    fun check() {
        if (state.value == null) return
        viewModelScope.launch { updates.check() }
    }

    fun continueOffline() = updates.continueOffline()
}
