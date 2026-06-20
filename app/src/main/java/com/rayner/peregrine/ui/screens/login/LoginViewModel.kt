package com.rayner.peregrine.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayner.peregrine.data.local.entity.ServerConfigEntity
import com.rayner.peregrine.domain.repository.FrigateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoginSuccessful: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: FrigateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = repository.getServerConfig().firstOrNull()
            config?.let {
                _uiState.value = _uiState.value.copy(
                    serverUrl = it.serverUrl,
                    username = it.username ?: ""
                )
            }
        }
    }

    fun onServerUrlChange(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url)
    }

    fun onUsernameChange(username: String) {
        _uiState.value = _uiState.value.copy(username = username)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun onLoginClick() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val config = ServerConfigEntity(
                serverUrl = _uiState.value.serverUrl,
                username = _uiState.value.username,
                encryptedPassword = _uiState.value.password // FIXME: Encrypt password
            )
            repository.updateServerConfig(config)
            
            val result = repository.getVersion()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isLoading = false, isLoginSuccessful = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, 
                    error = "Failed to connect to Frigate: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }
}
