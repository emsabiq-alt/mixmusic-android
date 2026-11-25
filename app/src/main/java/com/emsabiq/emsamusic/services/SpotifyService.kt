package com.emsabiq.emsamusic.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.emsabiq.emsamusic.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

data class SpotifyUser(
    val name: String,
    val imageUrl: String?
)

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val imageUrl: String?
)

object SpotifyService {
    val clientId = "5e98adbd48744236a380dca10ee8a36a"
    val redirectUri = "emsamusic://callback"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val _userProfile = MutableStateFlow<SpotifyUser?>(null)
    val userProfile = _userProfile.asStateFlow()

    private val _userPlaylists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
    val userPlaylists = _userPlaylists.asStateFlow()

    private val _likedTracks = MutableStateFlow<List<Track>>(emptyList())
    val likedTracks = _likedTracks.asStateFlow()

    private var accessToken: String? = null
    private var codeVerifier: String = ""

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("emsamusic_spotify", Context.MODE_PRIVATE)
        accessToken = prefs.getString("access_token", null)
        if (!accessToken.isNullOrEmpty()) {
            _isLoggedIn.value = true
            loadAllData()
        }
    }

    fun startLogin(context: Context) {
        codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)

        val scopes = "user-read-private user-read-email playlist-read-private playlist-read-collaborative user-library-read"
        val authUrl = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("scope", scopes)
            .appendQueryParameter("show_dialog", "true")
            .build()

        val intent = Intent(Intent.ACTION_VIEW, authUrl).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun handleAuthRedirect(context: Context, uri: Uri) {
        val code = uri.getQueryParameter("code") ?: return
        scope.launch {
            exchangeCodeForToken(context, code)
        }
    }

    private suspend fun exchangeCodeForToken(context: Context, code: String) = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri)
                .add("code_verifier", codeVerifier)
                .build()

            val request = Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .post(body)
                .build()

            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "")
                    val token = json.getString("access_token")
                    accessToken = token
                    _isLoggedIn.value = true

                    val prefs = context.getSharedPreferences("emsamusic_spotify", Context.MODE_PRIVATE)
                    prefs.edit().putString("access_token", token).apply()

                    loadAllData()
                }
            }
        } catch (_: Exception) {}
    }

    fun logout(context: Context) {
        accessToken = null
        _isLoggedIn.value = false
        _userProfile.value = null
        _userPlaylists.value = emptyList()
        _likedTracks.value = emptyList()

        val prefs = context.getSharedPreferences("emsamusic_spotify", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun loadAllData() {
        scope.launch {
            fetchProfile()
            fetchPlaylists()
            fetchLikedSongs()
        }
    }

    private suspend fun fetchProfile() = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext
        try {
            val req = Request.Builder()
                .url("https://api.spotify.com/v1/me")
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "")
                    val name = json.optString("display_name", "Spotify User")
                    var img: String? = null
                    val images = json.optJSONArray("images")
                    if (images != null && images.length() > 0) {
                        img = images.getJSONObject(0).optString("url")
                    }
                    _userProfile.value = SpotifyUser(name, img)
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun fetchPlaylists() = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext
        try {
            val req = Request.Builder()
                .url("https://api.spotify.com/v1/me/playlists?limit=50")
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "")
                    val items = json.optJSONArray("items") ?: JSONArray()
                    val result = mutableListOf<SpotifyPlaylist>()

                    for (i in 0 until items.length()) {
                        val p = items.getJSONObject(i)
                        val id = p.getString("id")
                        val name = p.getString("name")
                        val total = p.optJSONObject("tracks")?.optInt("total", 0) ?: 0
                        var img: String? = null
                        val imgs = p.optJSONArray("images")
                        if (imgs != null && imgs.length() > 0) {
                            img = imgs.getJSONObject(0).optString("url")
                        }
                        result.add(SpotifyPlaylist(id, name, total, img))
                    }
                    _userPlaylists.value = result
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun fetchLikedSongs() = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext
        try {
            val req = Request.Builder()
                .url("https://api.spotify.com/v1/me/tracks?limit=50")
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "")
                    val items = json.optJSONArray("items") ?: JSONArray()
                    val result = mutableListOf<Track>()

                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val t = item.optJSONObject("track") ?: continue
                        val id = t.getString("id")
                        val name = t.getString("name")
                        var artist = "Various Artists"
                        val artists = t.optJSONArray("artists")
                        if (artists != null && artists.length() > 0) {
                            artist = artists.getJSONObject(0).optString("name", "Various Artists")
                        }
                        var thumb = ""
                        val imgs = t.optJSONObject("album")?.optJSONArray("images")
                        if (imgs != null && imgs.length() > 0) {
                            thumb = imgs.getJSONObject(0).optString("url")
                        }
                        val durMs = t.optInt("duration_ms", 0)
                        result.add(Track("sp:$id", name, artist, thumb, durMs / 1000))
                    }
                    _likedTracks.value = result
                }
            }
        } catch (_: Exception) {}
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
