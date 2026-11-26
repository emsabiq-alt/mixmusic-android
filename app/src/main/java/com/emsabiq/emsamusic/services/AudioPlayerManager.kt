package com.emsabiq.emsamusic.services

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.emsabiq.emsamusic.models.LyricLine
import com.emsabiq.emsamusic.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AudioPlayerManager {
    private var exoPlayer: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _currentTimeMs = MutableStateFlow(0L)
    val currentTimeMs = _currentTimeMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics = _lyrics.asStateFlow()

    private val _currentLyricIndex = MutableStateFlow(0)
    val currentLyricIndex = _currentLyricIndex.asStateFlow()

    private var officialDurationMs: Long? = null
    private var preloadedTrackId: String? = null
    private var preloadedStreamUrl: String? = null
    private var isTransitioning = false

    val queue = mutableListOf<Track>()
    val originalQueue = mutableListOf<Track>()

    fun getOrCreatePlayer(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            // Instant buffer load control: start playback with only 500ms of buffer
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15_000, // minBufferMs
                    30_000, // maxBufferMs
                    500,    // bufferForPlaybackMs (Instant!)
                    1_000   // bufferForPlaybackAfterRebufferMs
                )
                .build()

            exoPlayer = ExoPlayer.Builder(context.applicationContext)
                .setLoadControl(loadControl)
                .build().apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            _isPlaying.value = playing
                            if (playing) {
                                startProgressTracking()
                            } else {
                                progressJob?.cancel()
                            }
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            when (state) {
                                Player.STATE_BUFFERING -> _isLoading.value = true
                                Player.STATE_READY -> {
                                    _isLoading.value = false
                                    val d = duration
                                    if (d > 0) {
                                        val off = officialDurationMs
                                        if (off != null && off > 30_000) {
                                            _durationMs.value = off
                                        } else if (_durationMs.value == 0L) {
                                            _durationMs.value = d
                                        }
                                    }
                                }
                                Player.STATE_ENDED -> handleTrackEnded()
                                else -> {}
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            _isLoading.value = false
                            _isPlaying.value = false
                        }
                    })
                }
        }
        return exoPlayer!!
    }

    fun play(track: Track, fromQueue: List<Track> = emptyList()) {
        _currentTrack.value = track
        _isLoading.value = true
        _lyrics.value = emptyList()
        _currentLyricIndex.value = 0
        _currentTimeMs.value = 0L
        _durationMs.value = (track.durationSeconds ?: 0) * 1000L
        officialDurationMs = track.durationSeconds?.let { it * 1000L }
        isTransitioning = false

        if (fromQueue.isNotEmpty()) {
            originalQueue.clear()
            originalQueue.addAll(fromQueue)
            queue.clear()
            val idx = fromQueue.indexOfFirst { it.id == track.id }
            if (idx != -1 && idx < fromQueue.size - 1) {
                queue.addAll(fromQueue.subList(idx + 1, fromQueue.size))
            }
        }

        // 1. Fetch lyrics & official duration in parallel
        scope.launch {
            val pkg = LyricsService.fetchLyrics(track.title, track.artist, track.durationSeconds?.toDouble())
            _lyrics.value = pkg.lines
            pkg.officialDuration?.let { offSec ->
                val offMs = (offSec * 1000).toLong()
                officialDurationMs = offMs
                if (_durationMs.value > (offMs + 10_000) || _durationMs.value == 0L) {
                    _durationMs.value = offMs
                }
            }
        }

        // 2. Resolve Direct Stream URL (with Preload Cache) and play via ExoPlayer
        scope.launch {
            if (preloadedTrackId == track.id && preloadedStreamUrl != null) {
                val cachedUrl = preloadedStreamUrl!!
                startExoPlayback(cachedUrl, track, track.thumbnailUrl)
                preloadNextTrack()
                return@launch
            }

            var actualId = track.id
            var resolvedThumb = track.thumbnailUrl

            if (track.id.startsWith("sp:") || track.thumbnailUrl.isEmpty()) {
                val found = YouTubeMusicService.search("${track.title} ${track.artist}")
                if (found.isNotEmpty()) {
                    actualId = found[0].id
                    if (found[0].thumbnailUrl.isNotEmpty()) {
                        resolvedThumb = found[0].thumbnailUrl
                        _currentTrack.value = track.copy(thumbnailUrl = resolvedThumb)
                    }
                }
            }
            val streamUrl = YouTubeMusicService.resolveStreamUrl(actualId)
            if (streamUrl != null) {
                startExoPlayback(streamUrl, track, resolvedThumb)
                preloadNextTrack()
            } else {
                _isLoading.value = false
            }
        }
    }

    private fun startExoPlayback(url: String, track: Track, thumb: String) {
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(if (thumb.isNotEmpty()) android.net.Uri.parse(thumb) else null)
                    .build()
            )
            .build()

        exoPlayer?.let { p ->
            p.setMediaItem(mediaItem)
            p.prepare()
            p.play()
        }
    }

    private fun preloadNextTrack() {
        val next = if (queue.isNotEmpty()) queue.first()
        else if (originalQueue.isNotEmpty()) {
            val cur = _currentTrack.value
            val idx = originalQueue.indexOfFirst { it.id == cur?.id }
            val nextIdx = if (idx != -1) (idx + 1) % originalQueue.size else 0
            originalQueue[nextIdx]
        } else null

        if (next != null) {
            scope.launch {
                var actualId = next.id
                if (next.id.startsWith("sp:")) {
                    val found = YouTubeMusicService.search("${next.title} ${next.artist}")
                    if (found.isNotEmpty()) actualId = found[0].id
                }
                val url = YouTubeMusicService.resolveStreamUrl(actualId)
                if (url != null) {
                    preloadedTrackId = next.id
                    preloadedStreamUrl = url
                }
            }
        }
    }

    fun pause() {
        exoPlayer?.pause()
        _isPlaying.value = false
    }

    fun resume() {
        exoPlayer?.play()
        _isPlaying.value = true
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else resume()
    }

    fun seekTo(positionMs: Long) {
        _currentTimeMs.value = positionMs
        exoPlayer?.seekTo(positionMs)
    }

    fun playNext() {
        if (queue.isNotEmpty()) {
            val next = queue.removeAt(0)
            play(next, originalQueue)
            return
        }
        // Seamless looping
        if (originalQueue.isNotEmpty()) {
            val cur = _currentTrack.value
            val idx = originalQueue.indexOfFirst { it.id == cur?.id }
            val nextIdx = if (idx != -1) (idx + 1) % originalQueue.size else 0
            play(originalQueue[nextIdx], originalQueue)
        }
    }

    fun playPrevious() {
        if (_currentTimeMs.value > 3000) {
            seekTo(0)
            return
        }
        if (originalQueue.isNotEmpty()) {
            val cur = _currentTrack.value
            val idx = originalQueue.indexOfFirst { it.id == cur?.id }
            val prevIdx = if (idx > 0) idx - 1 else originalQueue.size - 1
            play(originalQueue[prevIdx], originalQueue)
        } else {
            seekTo(0)
        }
    }

    private fun handleTrackEnded() {
        playNext()
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { p ->
                    val pos = p.currentPosition
                    _currentTimeMs.value = pos

                    // Cut off long video silence: if we reached the official duration, auto-advance
                    val maxDur = _durationMs.value
                    if (maxDur > 0 && pos >= (maxDur - 500) && _isPlaying.value && !isTransitioning) {
                        isTransitioning = true
                        handleTrackEnded()
                        return@launch
                    }

                    // Update synced lyric line
                    val currentSec = pos / 1000.0
                    val lList = _lyrics.value
                    for (i in lList.indices.reversed()) {
                        if (currentSec >= lList[i].time) {
                            if (_currentLyricIndex.value != i) {
                                _currentLyricIndex.value = i
                            }
                            break
                        }
                    }
                }
                delay(250)
            }
        }
    }
}
