package com.aiusage.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.util.Log
import androidx.core.content.ContextCompat
import com.aiusage.app.data.model.ThemeMode
import com.aiusage.app.data.store.AppRepository
import com.aiusage.app.widget.refreshWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AiUsageApp : Application() {
    lateinit var container: AppContainer
        private set

    private val widgetThemeScope = CoroutineScope(Dispatchers.Default)
    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED ||
                intent.action == "android.intent.action.UI_MODE_CHANGED"
            ) {
                refreshWidgetTheme()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction("android.intent.action.UI_MODE_CHANGED")
        }
        ContextCompat.registerReceiver(
            this,
            configReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshWidgetTheme()
    }

    private fun refreshWidgetTheme() {
        val mode = container.repository.state.value.settings.themeMode
        if (BuildConfig.DEBUG) {
            Log.d("AIUsageWidget", "theme receiver mode=$mode night=${resources.configuration.uiMode}")
        }
        if (mode != ThemeMode.SYSTEM) return
        widgetThemeScope.launch {
            val result = runCatching { refreshWidgets(this@AiUsageApp) }
            if (BuildConfig.DEBUG) {
                Log.d("AIUsageWidget", "theme updateAll=$result")
            }
        }
    }
}

class AppContainer(application: Application) {
    val repository: AppRepository = AppRepository(application.applicationContext)
}
