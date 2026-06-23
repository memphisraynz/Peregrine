package com.rayner.peregrine.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "preferences")
data class PreferenceEntity(
    @PrimaryKey val id: Int = 0,
    val defaultPlayerType: String = "hls",
    val vodBuffer: Int = 5
)
