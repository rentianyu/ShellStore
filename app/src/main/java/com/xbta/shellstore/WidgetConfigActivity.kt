package com.xbta.shellstore

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.xbta.shellstore.data.Store
import com.xbta.shellstore.ui.theme.AppTheme
import com.xbta.shellstore.ui.WidgetPickerScreen

/**
 * 添加桌面小部件时由系统调起：选一条命令绑定上去。
 *
 * 系统要求这个 Activity 返回 RESULT_OK 时带上 EXTRA_APPWIDGET_ID，
 * 否则桌面会认为配置失败、把小部件撤掉。
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        // 先假设用户取消，下面成功时才改成 RESULT_OK
        setResult(RESULT_CANCELED)

        val widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Compose 的 lambda 里 this 是 BoxScope/Composer，拿 Activity 得先存一份
        val activity = this
        setContent {
            AppTheme(darkTheme = false) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    WidgetPickerScreen(
                        title = "小部件执行哪条命令",
                        onPick = { entryId ->
                            Store.setWidgetEntry(widgetId, entryId)
                            ShellWidgetProvider.update(activity, widgetId)
                            setResult(
                                RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            )
                            finish()
                        },
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
