package com.rayner.peregrine

import android.app.Application
import com.rayner.peregrine.data.repository.FrigateRepositoryImpl
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PeregrineApp : Application() {

    @Inject
    lateinit var repository: FrigateRepositoryImpl

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            repository.restorePersistedAuthCookie()
        }
    }
}
