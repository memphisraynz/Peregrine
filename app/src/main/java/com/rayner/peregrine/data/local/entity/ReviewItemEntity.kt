package com.rayner.peregrine.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "review_items")
@TypeConverters(ReviewItemConverters::class)
data class ReviewItemEntity(
    @PrimaryKey val id: String,
    val camera: String,
    val severity: String,
    val startTime: Double,
    val endTime: Double?,
    val thumbPath: String,
    val hasBeenReviewed: Boolean,
    val primaryLabel: String?,
    val subLabels: List<String> = emptyList(),
    val objects: List<String> = emptyList()
)

class ReviewItemConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }
}
