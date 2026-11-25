package com.emsabiq.emsamusic

import android.app.Application
import com.emsabiq.emsamusic.services.AudioPlayerManager
import com.emsabiq.emsamusic.services.YouTubeMusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EmsaMusicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AudioPlayerManager.getOrCreatePlayer(this)
        CoroutineScope(Dispatchers.IO).launch {
            YouTubeMusicService.prewarm()
        }
    }
}
