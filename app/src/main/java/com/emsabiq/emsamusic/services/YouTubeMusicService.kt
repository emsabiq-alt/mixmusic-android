package com.emsabiq.emsamusic.services

import com.emsabiq.emsamusic.models.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object YouTubeMusicService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
    private const val VISION_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"

    private var cachedVisitorData: String? = null

    suspend fun prewarm() = withContext(Dispatchers.IO) {
        fetchVisitorDataIfNeeded()
    }

    private fun fetchVisitorDataIfNeeded(): String? {
        if (!cachedVisitorData.isNullOrEmpty()) return cachedVisitorData

        try {
            val url = "https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false"
            val body = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "VISIONOS")
                        put("clientVersion", "1.02")
                        put("deviceMake", "Apple")
                        put("deviceModel", "RealityDevice17,1")
                        put("osName", "visionOS")
                        put("osVersion", "26.5.23O471")
                        put("userAgent", VISION_AGENT)
                        put("hl", "id")
                        put("gl", "ID")
                    })
                })
            }
            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("User-Agent", VISION_AGENT)
                .header("X-YouTube-Client-Name", "101")
                .header("X-YouTube-Client-Version", "1.02")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val vis = json.optJSONObject("responseContext")?.optString("visitorData")
                    if (!vis.isNullOrEmpty()) {
                        cachedVisitorData = vis
                        return vis
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    suspend fun fetchTop50Indonesia(): List<Track> = withContext(Dispatchers.IO) {
        try {
            val url = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
            val body = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240501.01.00")
                        put("hl", "id")
                        put("gl", "ID")
                    })
                })
                put("browseId", "VLPL4fGSI1pDJn5ObxTlEPlkkornHXUiKX1z")
            }

            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://music.youtube.com/")
                .header("Origin", "https://music.youtube.com")
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20240501.01.00")
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val json = JSONObject(resp.body?.string() ?: "")
                val tracks = mutableListOf<Track>()

                val sections = json.optJSONObject("contents")
                    ?.optJSONObject("twoColumnBrowseResultsRenderer")
                    ?.optJSONObject("secondaryContents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents") ?: JSONArray()

                for (i in 0 until sections.length()) {
                    val shelf = sections.getJSONObject(i).optJSONObject("musicPlaylistShelfRenderer") ?: continue
                    val items = shelf.optJSONArray("contents") ?: JSONArray()
                    for (j in 0 until items.length()) {
                        val r = items.getJSONObject(j).optJSONObject("musicResponsiveListItemRenderer") ?: continue
                        val flexCols = r.optJSONArray("flexColumns") ?: continue
                        if (flexCols.length() < 1) continue

                        val titleObj = flexCols.getJSONObject(0).optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
                        val title = titleObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""

                        var artist = "Various Artists"
                        if (flexCols.length() > 1) {
                            val artistObj = flexCols.getJSONObject(1).optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
                            artist = artistObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Various Artists"
                        }

                        val videoId = r.optJSONObject("playlistItemData")?.optString("videoId")
                            ?: r.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
                            ?: ""

                        var thumb = ""
                        val thumbs = r.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        if (thumbs != null && thumbs.length() > 0) {
                            thumb = thumbs.getJSONObject(thumbs.length() - 1).optString("url")
                        }

                        var durationSec: Int? = null
                        val fixedCols = r.optJSONArray("fixedColumns")
                        if (fixedCols != null && fixedCols.length() > 0) {
                            val timeStr = fixedCols.getJSONObject(0).optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                                ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                            if (timeStr.contains(":")) {
                                val parts = timeStr.split(":")
                                if (parts.size == 2) {
                                    val m = parts[0].toIntOrNull() ?: 0
                                    val s = parts[1].toIntOrNull() ?: 0
                                    durationSec = (m * 60) + s
                                }
                            }
                        }

                        if (videoId.isNotEmpty() && title.isNotEmpty()) {
                            tracks.add(Track(videoId, title, artist, thumb, durationSec))
                        }
                    }
                }
                return@withContext tracks
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        try {
            val url = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
            val body = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240501.01.00")
                        put("hl", "id")
                        put("gl", "ID")
                    })
                })
                put("query", query)
                put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D") // Filter Songs
            }

            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://music.youtube.com/")
                .header("Origin", "https://music.youtube.com")
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20240501.01.00")
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val json = JSONObject(resp.body?.string() ?: "")
                val tracks = mutableListOf<Track>()

                val tabs = json.optJSONObject("contents")
                    ?.optJSONObject("tabbedSearchResultsRenderer")
                    ?.optJSONArray("tabs") ?: JSONArray()
                if (tabs.length() == 0) return@withContext emptyList()

                val secList = tabs.getJSONObject(0).optJSONObject("tabRenderer")
                    ?.optJSONObject("content")?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents") ?: JSONArray()

                for (i in 0 until secList.length()) {
                    val shelf = secList.getJSONObject(i).optJSONObject("musicShelfRenderer") ?: continue
                    val items = shelf.optJSONArray("contents") ?: JSONArray()

                    for (j in 0 until items.length()) {
                        val r = items.getJSONObject(j).optJSONObject("musicResponsiveListItemRenderer") ?: continue
                        val flexCols = r.optJSONArray("flexColumns") ?: continue
                        if (flexCols.length() < 1) continue

                        val titleObj = flexCols.getJSONObject(0).optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
                        val title = titleObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""

                        var artist = "Various Artists"
                        var durationSec: Int? = null

                        if (flexCols.length() > 1) {
                            val textObj = flexCols.getJSONObject(1).optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
                            val runs = textObj?.optJSONArray("runs")
                            if (runs != null) {
                                for (k in 0 until runs.length()) {
                                    val t = runs.getJSONObject(k).optString("text")
                                    if (t != " • " && !t.contains(":")) {
                                        artist = t
                                        break
                                    }
                                }
                                val lastRun = runs.getJSONObject(runs.length() - 1).optString("text")
                                if (lastRun.contains(":")) {
                                    val parts = lastRun.split(":")
                                    if (parts.size == 2) {
                                        val m = parts[0].toIntOrNull() ?: 0
                                        val s = parts[1].toIntOrNull() ?: 0
                                        durationSec = (m * 60) + s
                                    }
                                }
                            }
                        }

                        val videoId = r.optJSONObject("playlistItemData")?.optString("videoId")
                            ?: r.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
                            ?: ""

                        var thumb = ""
                        val thumbs = r.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        if (thumbs != null && thumbs.length() > 0) {
                            thumb = thumbs.getJSONObject(thumbs.length() - 1).optString("url")
                        }

                        if (videoId.isNotEmpty() && title.isNotEmpty()) {
                            tracks.add(Track(videoId, title, artist, thumb, durationSec))
                        }
                    }
                }
                return@withContext tracks
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun resolveStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val vis = fetchVisitorDataIfNeeded() ?: ""
        try {
            val url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
            val body = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "VISIONOS")
                        put("clientVersion", "1.02")
                        put("deviceMake", "Apple")
                        put("deviceModel", "RealityDevice17,1")
                        put("osName", "visionOS")
                        put("osVersion", "26.5.23O471")
                        put("userAgent", VISION_AGENT)
                        put("hl", "id")
                        put("gl", "ID")
                        put("timeZone", "Asia/Jakarta")
                        put("utcOffsetMinutes", 420)
                        if (vis.isNotEmpty()) put("visitorData", vis)
                    })
                })
                put("videoId", videoId)
                put("playbackContext", JSONObject().put("contentPlaybackContext", JSONObject().put("html5Preference", "HTML5_PREF_WANTS")))
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("User-Agent", VISION_AGENT)
                .header("X-YouTube-Client-Name", "101")
                .header("X-YouTube-Client-Version", "1.02")
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val json = JSONObject(resp.body?.string() ?: "")
                val formats = json.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: JSONArray()

                for (i in 0 until formats.length()) {
                    val f = formats.getJSONObject(i)
                    val mime = f.optString("mimeType")
                    val u = f.optString("url")
                    if (mime.contains("audio") && u.isNotEmpty()) {
                        if (f.optInt("itag") == 140) return@withContext u
                    }
                }
                // Fallback to first available audio url
                for (i in 0 until formats.length()) {
                    val f = formats.getJSONObject(i)
                    val mime = f.optString("mimeType")
                    val u = f.optString("url")
                    if (mime.contains("audio") && u.isNotEmpty()) return@withContext u
                }
            }
        } catch (_: Exception) {}
        return@withContext null
    }
}
