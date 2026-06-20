package com.rayner.peregrine.data.remote.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("user") val user: String,
    @SerializedName("password") val password: String
)
