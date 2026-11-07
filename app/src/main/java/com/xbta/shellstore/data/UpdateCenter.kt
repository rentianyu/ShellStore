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

        /** 发现新版本，但当前在移动网络：只提示，不自动下 9MB */
        data class Available(val info: Updater.Info) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var readyInfo: Updater.Info? = null
    @Volatile private var availableInfo: Updater.Info? = null

    /** 本次启动是否已经检查过，避免每次回到前台重复跑 */
    @Volatile private var checkedOnce = false

    /** 阻塞调用，务必在 IO 线程执行 */
    fun checkAndDownload(ctx: Context, currentVersion: String, allowDownload: Boolean = true) {
        if (checkedOnce) return
        checkedOnce = true
        val s0 = _state.value
        if (s0 is State.Ready || s0 is State.Available) return

        _state.value = State.Checking
        val r = Updater.checkLatest()
        val info = r.info
        if (info == null || !Updater.isNewer(info.version, currentVersion)) {
            _state.value = State.Idle
            return
        }

        if (!allowDownload) {
            // 移动网络：先不下载，交给顶部提示条由用户决定
            availableInfo = info
            _state.value = State.Available(info)
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

    /**
     * 用户主动点了顶部提示条上的「下载」：不管当前是什么网络，立刻开下。
     * 阻塞，调用方放 IO 线程。
     */
    fun downloadNow(ctx: Context) {
        val info = availableInfo ?: (_state.value as? State.Available)?.info ?: return
        availableInfo = null
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
        if (s is State.Ready || s is State.Failed || s is State.Available) {
            availableInfo = null
            _state.value = State.Idle
        }
    }

    fun resetForManualCheck() {
        checkedOnce = false
    }
}
