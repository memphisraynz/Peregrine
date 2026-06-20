package com.rayner.peregrine.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "server_config")
data class ServerConfigEntity(
    @PrimaryKey val id: Int = 0,
    val serverUrl: String,
    val username: String? = null,
    val encryptedPassword: String? = null, // Will be handled by encrypted storage later or stored here for simplicity if needed
    val isActive: Boolean = true
)
