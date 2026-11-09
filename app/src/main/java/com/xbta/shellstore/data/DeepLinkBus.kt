package com.xbta.shellstore.data

import com.xbta.shellstore.core.DeepLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 外部 deep link → Compose 界面的中转站。
 *
 * MainActivity 在 onCreate / onNewIntent 里 [post]，App() 里 collect 后执行。
 * 包一层自增 [Event.seq] 是为了让「同一条链接连点两次」也能触发两次
 * （StateFlow 对相同值不重发）。
 */
object DeepLinkBus {

    data class Event(val seq: Long, val cmd: DeepLink.Cmd)

    private val _events = MutableStateFlow<Event?>(null)
    val events: StateFlow<Event?> = _events.asStateFlow()

    private var seq = 0L

    /** @return 是否是本 App 的链接 */
    fun post(raw: String?): Boolean {
        val cmd = DeepLink.parse(raw) ?: return false
        seq += 1
        _events.value = Event(seq, cmd)
        return true
    }

    fun consume(e: Event) {
        if (_events.value?.seq == e.seq) _events.value = null
    }
}
