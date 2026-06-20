package com.rayner.peregrine.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayner.peregrine.domain.repository.FrigateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogsUiState(
    val logs: String = "",
    val selectedService: String = "frigate",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val repository: FrigateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadLogs()
    }

    fun loadLogs(service: String = _uiState.value.selectedService) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, selectedService = service)
            val result = repository.getServerLogs(service)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    logs = result.getOrNull() ?: ""
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load logs"
                )
            }
        }
    }
}
