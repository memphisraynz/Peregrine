package com.rayner.peregrine.ui.screens.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayner.peregrine.data.local.entity.ReviewItemEntity
import com.rayner.peregrine.data.remote.api.ServerUrlManager
import com.rayner.peregrine.domain.repository.FrigateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

enum class ReviewTab {
    ALERTS, DETECTIONS
}

data class ReviewUiState(
    val reviewItems: List<ReviewItemEntity> = emptyList(),
    val filteredItems: List<ReviewItemEntity> = emptyList(),
    val selectedTab: ReviewTab = ReviewTab.ALERTS,
    val isLoading: Boolean = false,
    val error: String? = null,
    val baseUrl: String = ""
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: FrigateRepository,
    private val serverUrlManager: ServerUrlManager,
    val okHttpClient: OkHttpClient,
    val imageLoader: coil3.ImageLoader
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(ReviewTab.ALERTS)
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ReviewUiState> = combine(
        repository.getReviewItemsFlow(),
        _selectedTab,
        _isLoading,
        _error,
        serverUrlManager.currentUrl
    ) { items, tab, loading, error, url ->
        val filtered = items.filter { item ->
            when (tab) {
                ReviewTab.ALERTS -> item.severity == "alert"
                ReviewTab.DETECTIONS -> item.severity == "detection"
            }
        }
        ReviewUiState(
            reviewItems = items,
            filteredItems = filtered,
            selectedTab = tab,
            isLoading = loading,
            error = error,
            baseUrl = url?.removeSuffix("/") ?: ""
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReviewUiState())

    init {
        refresh()
        
        // Also refresh when a valid URL is first detected, in case the initial init call failed due to no URL
        viewModelScope.launch {
            serverUrlManager.currentUrl
                .filterNotNull()
                .take(1)
                .collect {
                    if (uiState.value.reviewItems.isEmpty()) {
                        refresh()
                    }
                }
        }
    }

    fun setTab(tab: ReviewTab) {
        _selectedTab.value = tab
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = repository.refreshReviewItems()
            if (result.isFailure) {
                _error.value = "Failed to refresh: ${result.exceptionOrNull()?.message}"
            }
            _isLoading.value = false
        }
    }
}
