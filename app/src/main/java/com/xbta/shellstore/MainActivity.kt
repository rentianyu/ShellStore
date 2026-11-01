package com.xbta.shellstore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35+ 强制 edge-to-edge，不处理 insets 标题就会顶进状态栏
        enableEdgeToEdge()
        Store.init(this)
        setContent { App() }
    }
}
