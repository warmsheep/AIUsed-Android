package com.aiusage.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Toast
import com.aiusage.app.widget.UsageWidgetReceiver

/** Debug-only helper to pin the widget without navigating the launcher menus. */
class DebugPinWidgetActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, UsageWidgetReceiver::class.java)
        val supported = manager.isRequestPinAppWidgetSupported
        if (supported) {
            manager.requestPinAppWidget(provider, null, null)
            Toast.makeText(this, "请确认添加小组件", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Launcher 不支持一键添加", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
