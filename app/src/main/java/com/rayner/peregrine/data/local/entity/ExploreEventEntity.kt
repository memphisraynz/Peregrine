package com.rayner.peregrine.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "explore_events")
data class ExploreEventEntity(
    @PrimaryKey val id: String,
    val camera: String,
    val label: String,
    val startTime: Double,
    val endTime: Double?,
    val thumbUrl: String
)
