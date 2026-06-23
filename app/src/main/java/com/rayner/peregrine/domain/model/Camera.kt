package com.rayner.peregrine.domain.model

data class Camera(
    val name: String,
    val width: Int = 1920,
    val height: Int = 1080,
    val detect: Boolean = false,
    val recording: Boolean = false,
    val snapshots: Boolean = false,
    val mjpegUrl: String,
    val snapshotUrl: String,
    val hlsUrl: String? = null,
    val mseUrl: String? = null,
    val isLive: Boolean = false,
    val isMicEnabled: Boolean = false,
    val isSpeakerEnabled: Boolean = false,
    val useHls: Boolean = true,
    val lastReviewItem: com.rayner.peregrine.data.local.entity.ReviewItemEntity? = null
)
