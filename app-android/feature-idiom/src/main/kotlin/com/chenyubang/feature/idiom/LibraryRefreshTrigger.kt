package com.chenyubang.feature.idiom

import com.chenyubang.core.data.PackCheckOutcome

/** 每次真实进入 P03 调用一次；UPDATED 时页面重读本机库，FAILED 只给橙色提示。 */
fun interface LibraryRefreshTrigger {
    suspend fun onEnterLibrary(): PackCheckOutcome
}

val NoLibraryRefreshTrigger: LibraryRefreshTrigger = LibraryRefreshTrigger { PackCheckOutcome.UNCHANGED }
