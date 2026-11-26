package com.emsabiq.emsamusic.services

import android.content.Context
import com.emsabiq.emsamusic.models.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object LocalLibraryManager {
    private const val PREF_NAME = "emsamusic_library"
    private const val KEY_LIKED = "liked_tracks"

    private val _likedTracks = MutableStateFlow<List<Track>>(emptyList())
    val likedTracks = _likedTracks.asStateFlow()

    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_LIKED, null) ?: "[]"
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<Track>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Track(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        thumbnailUrl = obj.optString("thumbnailUrl", ""),
                        durationSeconds = if (obj.has("durationSeconds")) obj.getInt("durationSeconds") else null
                    )
                )
            }
            _likedTracks.value = list
        } catch (_: Exception) {}
    }

    fun isLiked(track: Track): Boolean {
        return _likedTracks.value.any { it.id == track.id }
    }

    fun toggleLike(context: Context, track: Track) {
        val current = _likedTracks.value.toMutableList()
        val idx = current.indexOfFirst { it.id == track.id }
        if (idx != -1) {
            current.removeAt(idx)
        } else {
            current.add(0, track)
        }
        _likedTracks.value = current
        save(context, current)
    }

    private fun save(context: Context, list: List<Track>) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (t in list) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("title", t.title)
            obj.put("artist", t.artist)
            obj.put("thumbnailUrl", t.thumbnailUrl)
            t.durationSeconds?.let { obj.put("durationSeconds", it) }
            arr.put(obj)
        }
        sp.edit().putString(KEY_LIKED, arr.toString()).apply()
    }
}
