package com.aiusage.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.aiusage.app.ui.AiUsageRoot
import com.aiusage.app.widget.refreshWidgets
import com.aiusage.app.ui.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        splash.setKeepOnScreenCondition { !viewModel.state.value.loaded }
        setContent {
            AiUsageRoot(viewModel = viewModel)
        }
    }

    override fun onStop() {
        super.onStop()
        // The launcher may ignore a RemoteViews update while this activity is
        // foregrounded. Re-apply on background so theme changes are visible
        // as soon as the user returns to the home screen.
        lifecycleScope.launch {
            runCatching { refreshWidgets(applicationContext) }
        }
    }
}
