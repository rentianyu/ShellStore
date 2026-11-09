package com.xbta.shellstore

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xbta.shellstore.data.DeepLinkBus
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 崩溃现场记录要在一切之前装，否则早期异常抓不到
        com.xbta.shellstore.data.CrashLog.install(this)
        // targetSdk 35+ 强制 edge-to-edge，不处理 insets 标题就会顶进状态栏
        enableEdgeToEdge()
        Store.init(this)
        // 冷启动带链接：先入队，App() 首次组合时会消费
        handleDeepLink(intent)
        setContent { App() }
    }

    // singleTask：App 已在前台时点链接走这里，不重建界面
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        // 只认自己的 scheme，其余（浏览器点开的 http 等）交给系统
        if (!data.scheme.equals("shellstore", ignoreCase = true)) return
        DeepLinkBus.post(data.toString())
        // 消费掉，避免旋转屏幕 / 重回前台时重复执行同一条链接
        intent.data = null
    }
}
