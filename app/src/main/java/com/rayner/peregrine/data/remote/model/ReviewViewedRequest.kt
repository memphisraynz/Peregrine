package com.rayner.peregrine.data.remote.model

import com.google.gson.annotations.SerializedName

data class ReviewViewedRequest(
    @SerializedName("ids")
    val ids: List<String>,
    @SerializedName("reviewed")
    val reviewed: Boolean = true
)
