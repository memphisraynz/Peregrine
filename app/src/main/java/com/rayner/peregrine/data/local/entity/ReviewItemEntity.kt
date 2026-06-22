package com.rayner.peregrine.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "review_items")
data class ReviewItemEntity(
    @PrimaryKey val id: String,
    val camera: String,
    val severity: String,
    val startTime: Double,
    val endTime: Double?,
    val thumbPath: String,
    val hasBeenReviewed: Boolean,
    val primaryLabel: String?
)
