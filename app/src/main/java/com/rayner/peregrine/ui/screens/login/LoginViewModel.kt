package com.rayner.peregrine.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayner.peregrine.data.local.entity.ServerConfigEntity
import com.rayner.peregrine.data.remote.api.ServerUrlManager
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
    private val repository: FrigateRepository,
    private val serverUrlManager: ServerUrlManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = repository.getServerConfig().firstOrNull()
            config?.let {
                serverUrlManager.setUrl(it.serverUrl)
                _uiState.value = _uiState.value.copy(
                    serverUrl = it.serverUrl,
                    username = it.username ?: "",
                    isLoginSuccessful = false
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
            var url = _uiState.value.serverUrl.trim()
            if (url.isEmpty()) return@launch

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }
            url = url.removeSuffix("/")

            serverUrlManager.setUrl(url)
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, serverUrl = url)
            
            // Attempt Login
            val loginResult = repository.login(_uiState.value.username, _uiState.value.password)
            
            if (loginResult.isSuccess) {
                val existingConfig = repository.getServerConfig().firstOrNull()
                val config = (existingConfig ?: ServerConfigEntity(serverUrl = url)).copy(
                    serverUrl = url,
                    username = _uiState.value.username,
                    encryptedPassword = _uiState.value.password,
                    isLoggedIn = true
                )
                repository.updateServerConfig(config)
                _uiState.value = _uiState.value.copy(isLoading = false, isLoginSuccessful = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, 
                    error = loginResult.exceptionOrNull()?.message ?: "Login failed"
                )
            }
        }
    }
}
