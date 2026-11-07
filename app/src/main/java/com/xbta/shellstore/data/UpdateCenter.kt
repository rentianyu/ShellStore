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
        data class Downloading(
            val tag: String,
            val pct: Int,
            val line: String = "",
            val index: Int = 0,
            val lines: Int = 0,
            val bytes: Long = 0,
            val total: Long = 0
        ) : State
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
        val err = Updater.download(
            ctx, info,
            onProgress = { pct ->
                val cur = _state.value as? State.Downloading
                _state.value = (cur ?: State.Downloading(info.version, pct))
                    .copy(tag = info.version, pct = pct)
            },
            onDetail = { line, index, lines, bytes, total ->
                _state.value = State.Downloading(
                    tag = info.version,
                    pct = if (total > 0) (bytes * 100 / total).toInt().coerceIn(0, 99) else 0,
                    line = line, index = index, lines = lines, bytes = bytes, total = total
                )
            }
        )
        if (err == null) {
            readyInfo = info
            _state.value = State.Ready(info.version)
        } else {
            _state.value = State.Failed(err)
        }
    }

    fun install(ctx: Context): String? {
        val info = readyInfo ?: return "没有待安装的更新包"
        val err = Updater.install(ctx, info)
        if (err != null) {
            // 包坏了不要留在缓存里假装「已就绪」，回到可重试状态
            readyInfo = null
            _state.value = State.Failed(err)
        }
        return err
    }

    /**
     * 手动重试：清掉缓存包、重新检查并下载。阻塞，调用方放 IO 线程。
     * 用于启动时自动更新失败后用户点顶部提示条。
     */
    fun retry(ctx: Context, currentVersion: String) {
        checkedOnce = false
        // 注意：这里**不清缓存**。半成品文件保留着才能断点续传，
        // 一清就得从 0 重下——这正是之前「一直重复下载」的成因。
        readyInfo = null
        _state.value = State.Idle
        checkAndDownload(ctx, currentVersion)
    }

    /** 用户点了「稍后」或关掉了失败提示条：本次运行不再提 */
    fun dismiss() {
        val s = _state.value
        if (s is State.Ready || s is State.Failed) _state.value = State.Idle
    }

    fun resetForManualCheck() {
        checkedOnce = false
    }
}
