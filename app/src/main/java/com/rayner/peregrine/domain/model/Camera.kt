package com.rayner.peregrine.domain.model

data class Camera(
    val name: String,
    val detect: Boolean = false,
    val recording: Boolean = false,
    val snapshots: Boolean = false,
    val mjpegUrl: String,
    val snapshotUrl: String,
    val hlsUrl: String? = null
)
