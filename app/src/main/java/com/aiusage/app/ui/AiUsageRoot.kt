package com.aiusage.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiusage.app.data.model.ProviderConfig
import com.aiusage.app.data.model.ProviderKind
import com.aiusage.app.ui.add.AddProviderSheet
import com.aiusage.app.ui.home.HomeScreen
import com.aiusage.app.ui.settings.SettingsSheet
import com.aiusage.app.ui.theme.AiUsageTheme
import com.aiusage.app.ui.theme.LocalAiuPalette
import kotlinx.coroutines.launch

@Composable
fun AiUsageRoot(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshIfStale()
    }
    AiUsageTheme(mode = state.settings.themeMode) {
        RootContent(state = state, viewModel = viewModel)
    }
}

@Composable
private fun RootContent(
    state: com.aiusage.app.data.model.AppState,
    viewModel: MainViewModel,
) {
    val palette = LocalAiuPalette.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<ProviderConfig?>(null) }

    HomeScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onRefreshAll = viewModel::refreshAll,
        onAdd = { showAdd = true },
        onEdit = { editingProvider = it },
        onRemove = { removeTarget = it },
        onRefreshProvider = viewModel::refreshProvider,
        onSettings = { showSettings = true },
    )

    if (showAdd || editingProvider != null) {
        val editing = editingProvider
        AddProviderSheet(
            editing = editing,
            existingKinds = state.providers.map { it.kind }.toSet(),
            onSubmit = { kind: ProviderKind, key: String, region ->
                val result = if (editing == null) {
                    viewModel.addProvider(kind, key, region)
                } else {
                    viewModel.updateProvider(editing, key, region)
                }
                if (result.isSuccess) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (editing == null) "已添加 ${kind.displayName}" else "已更新 ${kind.displayName}",
                        )
                    }
                }
                result
            },
            onDismiss = {
                showAdd = false
                editingProvider = null
            },
            onRemove = editing?.let { provider ->
                {
                    scope.launch {
                        viewModel.removeProvider(provider.id)
                        editingProvider = null
                        snackbarHostState.showSnackbar("已移除 ${provider.kind.displayName}")
                    }
                }
            },
        )
    }

    if (showSettings) {
        SettingsSheet(
            settings = state.settings,
            onDismiss = { showSettings = false },
            onThemeChange = viewModel::setTheme,
            onRefreshChange = viewModel::setRefreshMinutes,
        )
    }

    removeTarget?.let { provider ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("移除 ${provider.kind.displayName}？") },
            text = { Text("将删除本机保存的密钥和额度缓存，此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeTarget = null
                        scope.launch {
                            viewModel.removeProvider(provider.id)
                            snackbarHostState.showSnackbar("已移除 ${provider.kind.displayName}")
                        }
                    },
                ) {
                    Text("移除", color = palette.coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) {
                    Text("取消")
                }
            },
            containerColor = palette.cardElevated,
        )
    }
}
