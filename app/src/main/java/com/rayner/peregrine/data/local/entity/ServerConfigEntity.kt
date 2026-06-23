package com.rayner.peregrine.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "server_config")
data class ServerConfigEntity(
    @PrimaryKey val id: Int = 0,
    val serverUrl: String,
    val username: String? = null,
    val encryptedPassword: String? = null,
    val authCookie: String? = null,
    val authCookieExpiresAt: Long? = null,
    val isLoggedIn: Boolean = false,
    val isActive: Boolean = true,
    val defaultPlayerType: String = "hls", // "webrtc" or "hls"
    val vodBuffer: Int = 5 // Buffer in seconds
)
