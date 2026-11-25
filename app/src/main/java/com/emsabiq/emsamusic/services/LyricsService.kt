package com.emsabiq.emsamusic.services

import com.emsabiq.emsamusic.models.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class LyricsPackage(
    val lines: List<LyricLine>,
    val officialDuration: Double?
)

object LyricsService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""\s*[\(\[].*?(official|video|audio|lirik|lyrics|remix|clip|ft\.|feat\.|hd|4k).*?[\)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*(official|video|audio|lirik|lyrics).*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\|.*$"""), "")
            .trim()
    }

    fun cleanArtist(artist: String): String {
        return artist
            .replace(Regex("""\s*-\s*Topic""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*VEVO""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*Official""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    suspend fun fetchLyrics(title: String, artist: String, duration: Double? = null): LyricsPackage = withContext(Dispatchers.IO) {
        val cleanT = cleanTitle(title)
        val cleanA = cleanArtist(artist)

        fetchFromGet(cleanT, cleanA, duration)?.let { return@withContext it }
        fetchFromGet(title, artist, duration)?.let { return@withContext it }

        return@withContext LyricsPackage(emptyList(), null)
    }

    private fun fetchFromGet(title: String, artist: String, duration: Double?): LyricsPackage? {
        try {
            val encT = URLEncoder.encode(title, "UTF-8")
            val encA = URLEncoder.encode(artist, "UTF-8")
            var url = "https://lrclib.net/api/get?track_name=$encT&artist_name=$encA"
            if (duration != null && duration > 0) {
                url += "&duration=${duration.toInt()}"
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "EmsaMusic Android App")
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val json = JSONObject(resp.body?.string() ?: "")
                val dur = json.optDouble("duration", 0.0).takeIf { it > 0 }
                val synced = json.optString("syncedLyrics")
                val plain = json.optString("plainLyrics")

                if (synced.isNotEmpty()) {
                    val lines = parseLRC(synced)
                    val officialDur = dur ?: (lines.lastOrNull()?.time?.plus(5.0))
                    return LyricsPackage(lines, officialDur)
                } else if (plain.isNotEmpty()) {
                    val lines = plain.split("\n").map { LyricLine(0.0, it) }
                    return LyricsPackage(lines, dur)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun parseLRC(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("""\[(\d{2}):(\d{2}\.?\d*)\](.*)""")

        for (raw in lrc.lines()) {
            val match = regex.find(raw.trim()) ?: continue
            val (min, sec, text) = match.destructured
            val time = (min.toDoubleOrNull() ?: 0.0) * 60.0 + (sec.toDoubleOrNull() ?: 0.0)
            if (text.trim().isNotEmpty()) {
                lines.add(LyricLine(time, text.trim()))
            }
        }
        return lines.sortedBy { it.time }
    }
}
