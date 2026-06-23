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
    val allReviews: List<ReviewItemEntity> = emptyList(),
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
        repository.getExploreEventsFlow(),
        _isLoading,
        _error,
        _snapshotTimestamp,
        serverUrlManager.currentUrl,
        _cameraUiStates
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        val entities = array[0] as List<com.rayner.peregrine.data.local.entity.CameraEntity>
        @Suppress("UNCHECKED_CAST")
        val reviews = array[1] as List<ReviewItemEntity>
        @Suppress("UNCHECKED_CAST")
        val events = array[2] as List<com.rayner.peregrine.data.local.entity.ExploreEventEntity>
        val loading = array[3] as Boolean
        val error = array[4] as String?
        val ts = array[5] as Long
        val url = array[6] as String?
        @Suppress("UNCHECKED_CAST")
        val uiStates = array[7] as Map<String, CameraUiState>

        val cameras = entities.map { entity ->
            val ui = uiStates[entity.name] ?: CameraUiState()
            val lastReview = reviews.firstOrNull { it.camera == entity.name }
            val hasActiveEvent = events.any { it.camera == entity.name && it.endTime == null }
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
                useHls = ui.useHls ?: entity.useHls,
                hasMotion = hasActiveEvent,
                lastReviewItem = lastReview
            )
        }
        val active = reviews.filter { !it.hasBeenReviewed && it.severity == "alert" }.take(10)
        LiveUiState(
            cameras = cameras,
            activeReviews = active,
            allReviews = reviews,
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
            repository.refreshReviewItems(limit = 100) // Get a good chunk initially

            if (result.isFailure && uiState.value.cameras.isEmpty()) {
                _error.value = "Failed to load cameras"
            }
            _isLoading.value = false
        }
    }

    private fun startSnapshotUpdates() {
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                
                // Immediately update timestamps to trigger UI fetch
                _snapshotTimestamp.value = now

                // Refresh reviews and events to find active alerts/motion
                repository.refreshReviewItems(limit = 10, severity = "alert", reviewed = 0)
                repository.refreshExploreEvents() 

                val state = uiState.value
                val hasAlerts = state.activeReviews.isNotEmpty()
                val hasActiveMotion = state.cameras.any { it.hasMotion }
                
                kotlinx.coroutines.delay(if (hasAlerts || hasActiveMotion) 1000L else 5000L)
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
