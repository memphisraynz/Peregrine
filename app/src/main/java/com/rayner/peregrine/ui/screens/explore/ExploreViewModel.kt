package com.rayner.peregrine.ui.screens.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayner.peregrine.domain.repository.FrigateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExploreUiState(
    val labels: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: FrigateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadLabels()
    }

    fun loadLabels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.getEvents()
            if (result.isSuccess) {
                val events = result.getOrDefault(emptyList())
                val labels = events.mapNotNull { it["label"] as? String }.distinct()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    labels = labels
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load tags"
                )
            }
        }
    }
}
