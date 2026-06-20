package com.rayner.peregrine.data.remote.api

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerUrlManager @Inject constructor() {
    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl = _currentUrl

    fun setUrl(url: String) {
        _currentUrl.value = url
    }

    fun clear() {
        _currentUrl.value = null
    }

    fun getUrl(): String? = _currentUrl.value
}
