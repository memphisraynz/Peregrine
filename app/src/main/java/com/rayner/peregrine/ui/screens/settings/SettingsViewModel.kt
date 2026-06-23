package com.rayner.peregrine.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayner.peregrine.data.remote.api.ServerUrlManager
import com.rayner.peregrine.domain.repository.FrigateRepository
import com.rayner.peregrine.data.local.entity.PreferenceEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val defaultPlayerType: String = "hls",
    val vodBuffer: Int = 5,
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
                    _uiState.update { it.copy(
                        serverUrl = config.serverUrl,
                        username = config.username ?: ""
                    ) }
                }
            }
        }
        
        viewModelScope.launch {
            repository.getPreferencesFlow().collectLatest { prefs ->
                val p = prefs ?: PreferenceEntity()
                _uiState.update { it.copy(
                    defaultPlayerType = p.defaultPlayerType,
                    vodBuffer = p.vodBuffer
                ) }
            }
        }
    }

    fun setDefaultPlayerType(type: String) {
        viewModelScope.launch {
            val current = repository.getPreferencesFlow().firstOrNull() ?: PreferenceEntity()
            repository.updatePreferences(current.copy(defaultPlayerType = type))
        }
    }

    fun setVodBuffer(seconds: Int) {
        viewModelScope.launch {
            val current = repository.getPreferencesFlow().firstOrNull() ?: PreferenceEntity()
            repository.updatePreferences(current.copy(vodBuffer = seconds))
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
