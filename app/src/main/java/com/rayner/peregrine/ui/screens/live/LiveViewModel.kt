package com.rayner.peregrine.ui.screens.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import com.rayner.peregrine.data.local.entity.ReviewItemEntity
import com.rayner.peregrine.data.remote.api.ServerUrlManager
import com.rayner.peregrine.data.remote.ws.FrigateWebSocketManager
import com.rayner.peregrine.domain.model.Camera
import com.rayner.peregrine.domain.repository.FrigateRepository
import com.google.gson.Gson
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
    val baseUrl: String = ""
)

@HiltViewModel
class LiveViewModel @Inject constructor(
    private val repository: FrigateRepository,
    private val serverUrlManager: ServerUrlManager,
    private val wsManager: FrigateWebSocketManager,
    val imageLoader: ImageLoader,
    val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _cameraSnapshotTimestamps = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val _cameraUiStates = MutableStateFlow<Map<String, CameraUiState>>(emptyMap())
    private val _wsMotionStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())

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
        _cameraSnapshotTimestamps,
        serverUrlManager.currentUrl,
        _cameraUiStates,
        _wsMotionStates,
        repository.getPreferencesFlow().map { it?.alertsFilterDays ?: -12 }
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        val entities = array[0] as List<com.rayner.peregrine.data.local.entity.CameraEntity>
        @Suppress("UNCHECKED_CAST")
        val reviews = array[1] as List<ReviewItemEntity>
        @Suppress("UNCHECKED_CAST")
        val events = array[2] as List<com.rayner.peregrine.data.local.entity.ExploreEventEntity>
        val loading = array[3] as Boolean
        val error = array[4] as String?
        @Suppress("UNCHECKED_CAST")
        val snapshotTimestamps = array[5] as Map<String, Long>
        val url = array[6] as String?
        @Suppress("UNCHECKED_CAST")
        val uiStates = array[7] as Map<String, CameraUiState>
        @Suppress("UNCHECKED_CAST")
        val wsMotion = array[8] as Map<String, Boolean>
        val filterDays = array[9] as Int

        val cameras = entities.map { entity ->
            val ui = uiStates[entity.name] ?: CameraUiState()
            val lastReview = reviews.firstOrNull { it.camera == entity.name }
            val hasMotion = wsMotion[entity.name] ?: false
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
                hasMotion = hasMotion,
                snapshotTimestamp = snapshotTimestamps[entity.name] ?: 0L,
                lastReviewItem = lastReview
            )
        }
        
        val active = reviews.filter { review ->
            val isUnreviewedAlert = !review.hasBeenReviewed && review.severity.lowercase() == "alert"
            val passesTimeFilter = when {
                filterDays == 0 -> true
                filterDays == -12 -> {
                    val cutoff = (System.currentTimeMillis() / 1000) - (12 * 60 * 60)
                    review.startTime >= cutoff
                }
                filterDays > 0 -> {
                    val cutoff = (System.currentTimeMillis() / 1000) - (filterDays * 24 * 60 * 60)
                    review.startTime >= cutoff
                }
                else -> true
            }
            
            isUnreviewedAlert && passesTimeFilter
        }.take(10)

        LiveUiState(
            cameras = cameras,
            activeReviews = active,
            allReviews = reviews,
            isLoading = loading,
            error = error,
            baseUrl = url?.removeSuffix("/") ?: ""
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveUiState())

    init {
        loadData()
        startSnapshotUpdates()
        observeConfig()
        observeWebSocket()
    }

    private fun observeWebSocket() {
        val gson = Gson()
        viewModelScope.launch {
            wsManager.updates.collect { msg ->
                // Initial bulk state or full update
                if (msg.topic == "camera_activity") {
                    val payload = msg.payload
                    val activityMap = when (payload) {
                        is Map<*, *> -> payload
                        is String -> try {
                            gson.fromJson(payload, Map::class.java)
                        } catch (e: Exception) {
                            null
                        }
                        else -> null
                    }

                    if (activityMap != null) {
                        val newStates = activityMap.mapNotNull { (cameraName, activityAny) ->
                            val activity = activityAny as? Map<*, *>
                            // Follow the motion boolean directly from the activity state
                            val isMotion = activity?.get("motion") == true
                            cameraName.toString() to isMotion
                        }.toMap()

                        if (newStates.isNotEmpty()) {
                            _wsMotionStates.update { it + newStates }
                        }
                    }
                }

                // Real-time updates (e.g., "camera_name/motion" or "frigate/camera_name/motion")
                val parts = msg.topic.split("/")
                val motionIndex = parts.indexOf("motion")
                if (motionIndex > 0) {
                    val cameraName = parts[motionIndex - 1]
                    val isMotion = msg.payload == "ON"
                    _wsMotionStates.update { it + (cameraName to isMotion) }
                }
            }
        }
    }

    fun onResume() {
        wsManager.connect()
    }

    fun onPause() {
        wsManager.disconnect()
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
            // Ensure we get unreviewed alerts specifically
            repository.refreshReviewItems(limit = 100, reviewed = 0)

            if (result.isFailure && uiState.value.cameras.isEmpty()) {
                _error.value = "Failed to load cameras"
            }
            _isLoading.value = false
        }
    }

    private fun startSnapshotUpdates() {
        viewModelScope.launch {
            val lastUpdatePerCamera = mutableMapOf<String, Long>()
            while (true) {
                val now = System.currentTimeMillis()
                
                // Fetch explore events for motion, but DON'T refresh reviews in background
                // This prevents the list from jumping while we're using it.
                // Reviews will be refreshed when the user returns to the screen (onResume).
                repository.refreshExploreEvents() 

                val state = uiState.value
                val hasAnyActivity = state.activeReviews.isNotEmpty() || state.cameras.any { it.hasMotion }
                
                // Update timestamps per camera
                _cameraSnapshotTimestamps.update { current ->
                    val next = current.toMutableMap()
                    state.cameras.forEach { camera ->
                        val lastUpdate = lastUpdatePerCamera[camera.name] ?: 0L
                        val shouldUpdate = camera.hasMotion || (now - lastUpdate >= 5000L)
                        
                        if (shouldUpdate) {
                            next[camera.name] = now
                            lastUpdatePerCamera[camera.name] = now
                        }
                    }
                    next
                }

                kotlinx.coroutines.delay(if (hasAnyActivity) 1000L else 2000L)
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
