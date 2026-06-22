package com.rayner.peregrine.ui.screens.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import com.rayner.peregrine.data.local.entity.ReviewItemEntity
import com.rayner.peregrine.data.remote.api.ServerUrlManager
import com.rayner.peregrine.domain.model.Camera
import com.rayner.peregrine.domain.repository.FrigateRepository
import okhttp3.OkHttpClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveUiState(
    val cameras: List<Camera> = emptyList(),
    val activeReviews: List<ReviewItemEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val snapshotTimestamp: Long = System.currentTimeMillis(),
    val baseUrl: String = ""
)

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val repository: FrigateRepository,
    private val serverUrlManager: ServerUrlManager,
    val imageLoader: ImageLoader,
    val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _snapshotTimestamp = MutableStateFlow(System.currentTimeMillis())
    private val _cameraUiStates = MutableStateFlow<Map<String, CameraUiState>>(emptyMap())

    data class CameraUiState(
        val isLive: Boolean = false,
        val isMicEnabled: Boolean = false,
        val isSpeakerEnabled: Boolean = false,
        val useHls: Boolean? = null
    )

    val uiState: StateFlow<LiveUiState> = combine(
        repository.getCamerasFlow(),
        repository.getReviewItemsFlow(),
        _isLoading,
        _error,
        combine(_snapshotTimestamp, serverUrlManager.currentUrl, _cameraUiStates) { ts, url, uiStates -> 
            Triple(ts, url, uiStates)
        }
    ) { entities, reviews, loading, error, extra ->
        val (ts, url, uiStates) = extra
        val cameras = entities.map { entity ->
            val ui = uiStates[entity.name] ?: CameraUiState()
            Camera(
                name = entity.name,
                width = entity.width,
                height = entity.height,
                mjpegUrl = entity.mjpegUrl,
                snapshotUrl = entity.snapshotUrl,
                hlsUrl = entity.hlsUrl,
                mseUrl = entity.mseUrl,
                isLive = ui.isLive,
                isMicEnabled = ui.isMicEnabled,
                isSpeakerEnabled = ui.isSpeakerEnabled,
                useHls = ui.useHls ?: entity.useHls
            )
        }
        val active = reviews.filter { it.endTime == null }
        LiveUiState(
            cameras = cameras,
            activeReviews = active,
            isLoading = loading,
            error = error,
            snapshotTimestamp = ts,
            baseUrl = url?.removeSuffix("/") ?: ""
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveUiState())

    init {
        loadData()
        startSnapshotUpdates()
        observeConfig()
    }

    private fun observeConfig() {
        viewModelScope.launch {
            repository.getServerConfig().collectLatest {
                loadData()
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = repository.refreshCameras()
            repository.refreshReviewItems()

            if (result.isFailure && uiState.value.cameras.isEmpty()) {
                _error.value = "Failed to load cameras"
            }
            _isLoading.value = false
        }
    }

    private fun startSnapshotUpdates() {
        viewModelScope.launch {
            while (true) {
                repository.refreshReviewItems()
                _snapshotTimestamp.value = System.currentTimeMillis()
                
                val hasMotion = uiState.value.activeReviews.isNotEmpty()
                kotlinx.coroutines.delay(if (hasMotion) 1000L else 5000L)
            }
        }
    }

    fun setLive(cameraName: String, isLive: Boolean) {
        _cameraUiStates.update { current ->
            val old = current[cameraName] ?: CameraUiState()
            current + (cameraName to old.copy(isLive = isLive))
        }
    }

    fun toggleLive(cameraName: String) {
        _cameraUiStates.update { current ->
            val old = current[cameraName] ?: CameraUiState()
            current + (cameraName to old.copy(isLive = !old.isLive))
        }
    }

    fun toggleMic(cameraName: String) {
        _cameraUiStates.update { current ->
            val old = current[cameraName] ?: CameraUiState()
            current + (cameraName to old.copy(isMicEnabled = !old.isMicEnabled))
        }
    }

    fun toggleSpeaker(cameraName: String) {
        _cameraUiStates.update { current ->
            val old = current[cameraName] ?: CameraUiState()
            val newState = !old.isSpeakerEnabled
            current + (cameraName to old.copy(
                isSpeakerEnabled = newState,
                isLive = if (newState) true else old.isLive
            ))
        }
    }

    fun togglePlayerType(cameraName: String) {
        _cameraUiStates.update { current ->
            val old = current[cameraName] ?: CameraUiState()
            val currentActual = uiState.value.cameras.find { it.name == cameraName }?.useHls ?: true
            current + (cameraName to old.copy(useHls = !currentActual))
        }
    }
}
