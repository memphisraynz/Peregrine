package com.rayner.peregrine.ui.screens.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import com.rayner.peregrine.domain.model.Camera
import com.rayner.peregrine.domain.repository.FrigateRepository
import okhttp3.OkHttpClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveUiState(
    val cameras: List<Camera> = emptyList(),
    val activeReviews: List<Map<String, Any>> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val snapshotTimestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val repository: FrigateRepository,
    val imageLoader: ImageLoader,
    val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadData()
        startSnapshotUpdates()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val camerasResult = repository.getCameras()
            val reviewResult = repository.getReviewItems()

            val activeReviews = reviewResult.getOrDefault(emptyList()).filter { it["end_time"] == null }
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                cameras = camerasResult.getOrDefault(emptyList()),
                activeReviews = activeReviews
            )

            if (camerasResult.isFailure) {
                _uiState.value = _uiState.value.copy(error = "Failed to load cameras")
            }
        }
    }

    private fun startSnapshotUpdates() {
        viewModelScope.launch {
            while (true) {
                // Refresh active reviews to check for motion
                val reviewResult = repository.getReviewItems()
                val activeReviews = reviewResult.getOrDefault(emptyList()).filter { it["end_time"] == null }
                
                _uiState.value = _uiState.value.copy(
                    activeReviews = activeReviews,
                    snapshotTimestamp = System.currentTimeMillis()
                )
                
                val hasMotion = activeReviews.isNotEmpty()
                kotlinx.coroutines.delay(if (hasMotion) 1000L else 5000L)
            }
        }
    }

    fun setLive(cameraName: String, isLive: Boolean) {
        val updatedCameras = _uiState.value.cameras.map {
            if (it.name == cameraName) it.copy(isLive = isLive) else it
        }
        _uiState.value = _uiState.value.copy(cameras = updatedCameras)
    }

    fun toggleLive(cameraName: String) {
        val camera = _uiState.value.cameras.firstOrNull { it.name == cameraName } ?: return
        setLive(cameraName, !camera.isLive)
    }

    fun toggleMic(cameraName: String) {
        val updatedCameras = _uiState.value.cameras.map {
            if (it.name == cameraName) it.copy(isMicEnabled = !it.isMicEnabled) else it
        }
        _uiState.value = _uiState.value.copy(cameras = updatedCameras)
    }

    fun toggleSpeaker(cameraName: String) {
        val updatedCameras = _uiState.value.cameras.map {
            if (it.name == cameraName) {
                val newState = !it.isSpeakerEnabled
                it.copy(
                    isSpeakerEnabled = newState,
                    isLive = if (newState) true else it.isLive
                )
            } else it
        }
        _uiState.value = _uiState.value.copy(cameras = updatedCameras)
    }

    fun togglePlayerType(cameraName: String) {
        val updatedCameras = _uiState.value.cameras.map {
            if (it.name == cameraName) it.copy(useHls = !it.useHls) else it
        }
        _uiState.value = _uiState.value.copy(cameras = updatedCameras)
    }
}
