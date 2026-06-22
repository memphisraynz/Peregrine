package com.rayner.peregrine.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cameras")
data class CameraEntity(
    @PrimaryKey val name: String,
    val width: Int,
    val height: Int,
    val mjpegUrl: String,
    val snapshotUrl: String,
    val hlsUrl: String?,
    val mseUrl: String?,
    val useHls: Boolean
)
