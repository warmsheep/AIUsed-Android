package com.aiusage.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aiusage.app.AiUsageApp
import com.aiusage.app.data.model.ProviderConfig
import com.aiusage.app.data.model.ProviderKind
import com.aiusage.app.data.model.Region
import com.aiusage.app.data.model.ThemeMode
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AiUsageApp).container.repository

    val state: StateFlow<com.aiusage.app.data.model.AppState> = repository.state

    fun refreshAll() {
        viewModelScope.launch { repository.refreshAll() }
    }

    fun refreshIfStale() {
        viewModelScope.launch { repository.refreshIfStale() }
    }

    fun refreshProvider(providerId: String) {
        viewModelScope.launch { repository.refreshProvider(providerId) }
    }

    suspend fun addProvider(kind: ProviderKind, key: String, region: Region): Result<Unit> =
        repository.addProvider(kind, key, region)

    suspend fun updateProvider(provider: ProviderConfig, key: String?, region: Region): Result<Unit> =
        repository.updateProvider(provider.id, key, region)

    suspend fun removeProvider(providerId: String) = repository.removeProvider(providerId)

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { repository.updateTheme(mode) }
    }

    fun setRefreshMinutes(minutes: Int) {
        viewModelScope.launch { repository.updateRefreshMinutes(minutes) }
    }
}
