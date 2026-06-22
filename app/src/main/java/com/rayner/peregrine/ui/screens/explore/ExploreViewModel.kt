package com.rayner.peregrine.ui.screens.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayner.peregrine.data.local.entity.ExploreEventEntity
import com.rayner.peregrine.data.remote.api.ServerUrlManager
import com.rayner.peregrine.domain.repository.FrigateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

data class ExploreUiState(
    val events: List<ExploreEventEntity> = emptyList(),
    val filteredEvents: List<ExploreEventEntity> = emptyList(),
    val labels: List<String> = emptyList(),
    val selectedLabels: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val baseUrl: String = ""
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: FrigateRepository,
    private val serverUrlManager: ServerUrlManager,
    val imageLoader: coil3.ImageLoader,
    val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _selectedLabels = MutableStateFlow(emptySet<String>())
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ExploreUiState> = combine(
        repository.getExploreEventsFlow(),
        _selectedLabels,
        _isLoading,
        _error,
        serverUrlManager.currentUrl
    ) { events, selected, loading, error, url ->
        val labels = events.map { it.label }.distinct().sorted()
        val filtered = if (selected.isEmpty()) events else events.filter { selected.contains(it.label) }
        ExploreUiState(
            events = events,
            filteredEvents = filtered,
            labels = labels,
            selectedLabels = selected,
            isLoading = loading,
            error = error,
            baseUrl = url?.removeSuffix("/") ?: ""
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExploreUiState())

    init {
        refresh()

        // Also refresh when a valid URL is first detected
        viewModelScope.launch {
            serverUrlManager.currentUrl
                .filterNotNull()
                .take(1)
                .collect {
                    if (uiState.value.events.isEmpty()) {
                        refresh()
                    }
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = repository.refreshExploreEvents()
            if (result.isFailure) {
                _error.value = "Failed to refresh: ${result.exceptionOrNull()?.message}"
            }
            _isLoading.value = false
        }
    }

    fun toggleLabel(label: String) {
        _selectedLabels.update { state ->
            if (state.contains(label)) {
                state - label
            } else {
                state + label
            }
        }
    }
}
