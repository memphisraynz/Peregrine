package com.rayner.peregrine.ui.screens.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayner.peregrine.data.local.entity.ReviewItemEntity
import com.rayner.peregrine.data.remote.api.ServerUrlManager
import com.rayner.peregrine.domain.repository.FrigateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import coil3.ImageLoader
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
    val baseUrl: String = "",
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: FrigateRepository,
    private val serverUrlManager: ServerUrlManager,
    val okHttpClient: OkHttpClient,
    val imageLoader: ImageLoader,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(ReviewTab.ALERTS)
    private val _isLoading = MutableStateFlow(value = false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ReviewUiState> = combine(
        repository.getReviewItemsFlow(),
        _selectedTab,
        _isLoading,
        _error,
        serverUrlManager.currentUrl,
    ) { items, tab, loading, error, url ->
        val filtered = items.filter { item ->
            val severity = item.severity.lowercase()
            when (tab) {
                ReviewTab.ALERTS -> severity == "alert"
                ReviewTab.DETECTIONS -> severity == "detection"
            }
        }
        ReviewUiState(
            reviewItems = items,
            filteredItems = filtered,
            selectedTab = tab,
            isLoading = loading,
            error = error,
            baseUrl = url?.removeSuffix("/") ?: "",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReviewUiState())

    init {
        // Initial refresh for both to seed the DB
        refresh()
        
        // Refresh specifically when the tab changes to ensure we have data for that severity
        viewModelScope.launch {
            _selectedTab.collect { tab ->
                val severity = when (tab) {
                    ReviewTab.ALERTS -> "alert"
                    ReviewTab.DETECTIONS -> "detection"
                }
                repository.refreshReviewItems(limit = 50, severity = severity)
            }
        }

        // Also refresh when a valid URL is first detected
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
            
            // Fetch a bit of both to start
            repository.refreshReviewItems(limit = 50, severity = "alert")
            repository.refreshReviewItems(limit = 50, severity = "detection")

            _isLoading.value = false
        }
    }

    fun markAsReviewed(id: String) {
        viewModelScope.launch {
            repository.markReviewed(listOf(id))
        }
    }
}
