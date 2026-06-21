package com.rayner.peregrine.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayner.peregrine.data.remote.api.ServerUrlManager
import com.rayner.peregrine.domain.repository.FrigateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val defaultPlayerType: String = "hls",
    val isLoading: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: FrigateRepository,
    private val serverUrlManager: ServerUrlManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getServerConfig().collectLatest { config ->
                if (config != null) {
                    _uiState.value = _uiState.value.copy(
                        serverUrl = config.serverUrl,
                        username = config.username ?: "",
                        defaultPlayerType = config.defaultPlayerType
                    )
                } else {
                    _uiState.value = SettingsUiState()
                }
            }
        }
    }

    fun setDefaultPlayerType(type: String) {
        viewModelScope.launch {
            val config = repository.getServerConfig().firstOrNull() ?: return@launch
            repository.updateServerConfig(config.copy(defaultPlayerType = type))
        }
    }

    fun onLogout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            repository.clearServerConfig()
            serverUrlManager.clear()
            onLoggedOut()
        }
    }
}
