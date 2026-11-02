package com.xbta.shellstore.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用自更新。启动后静默检查，有新版本就后台下载，下完再弹窗让用户装。
 * 检查失败不打扰用户（后台任务，静默失败）。
 */
object UpdateCenter {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class Downloading(val tag: String, val pct: Int) : State
        data class Ready(val tag: String) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var readyInfo: Updater.Info? = null

    /** 本次启动是否已经检查过，避免每次回到前台重复跑 */
    @Volatile private var checkedOnce = false

    /** 阻塞调用，务必在 IO 线程执行 */
    fun checkAndDownload(ctx: Context, currentVersion: String) {
        if (checkedOnce) return
        checkedOnce = true
        if (_state.value is State.Ready) return

        _state.value = State.Checking
        val r = Updater.checkLatest()
        val info = r.info
        if (info == null || !Updater.isNewer(info.version, currentVersion)) {
            _state.value = State.Idle
            return
        }

        _state.value = State.Downloading(info.version, 0)
        val err = Updater.download(ctx, info) { pct ->
            _state.value = State.Downloading(info.version, pct)
        }
        if (err == null) {
            readyInfo = info
            _state.value = State.Ready(info.version)
        } else {
            _state.value = State.Failed(err)
        }
    }

    fun install(ctx: Context): String? {
        val info = readyInfo ?: return "没有待安装的更新包"
        return Updater.install(ctx, info)
    }

    /** 用户点了「稍后」，本次运行不再提 */
    fun dismiss() {
        if (_state.value is State.Ready) _state.value = State.Idle
    }

    fun resetForManualCheck() {
        checkedOnce = false
    }
}
