package com.emsabiq.emsamusic.models

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val durationSeconds: Int? = null
) {
    val durationFormatted: String
        get() {
            val sec = durationSeconds ?: return "--:--"
            val m = sec / 60
            val s = sec % 60
            return String.format("%d:%02d", m, s)
        }
}

data class LyricLine(
    val time: Double,
    val text: String
)
