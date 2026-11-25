package com.emsabiq.emsamusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emsabiq.emsamusic.models.Track
import com.emsabiq.emsamusic.services.AudioPlayerManager
import com.emsabiq.emsamusic.services.YouTubeMusicService
import com.emsabiq.emsamusic.ui.theme.SpotifyBlack
import com.emsabiq.emsamusic.ui.theme.SpotifyCard
import com.emsabiq.emsamusic.ui.theme.SpotifyGreen
import com.emsabiq.emsamusic.ui.theme.SpotifyGrey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
            .padding(horizontal = 16.dp)
            .statusBarsPadding()
    ) {
        Text(
            text = "Cari",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
        )

        // Search Bar
        TextField(
            value = query,
            onValueChange = {
                query = it
                searchJob?.cancel()
                if (it.trim().isNotEmpty()) {
                    searchJob = scope.launch {
                        delay(400) // Debounce
                        isSearching = true
                        val res = YouTubeMusicService.search(it)
                        searchResults = res
                        isSearching = false
                    }
                } else {
                    searchResults = emptyList()
                    isSearching = false
                }
            },
            placeholder = { Text("Apa yang ingin kamu dengarkan?", color = SpotifyGrey, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SpotifyGrey) },
            colors = TextFieldDefaults.textFieldColors(
                containerColor = SpotifyCard,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SpotifyGreen)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(searchResults) { index, track ->
                    TrackRow(
                        rank = index + 1,
                        track = track,
                        onClick = {
                            AudioPlayerManager.play(track, fromQueue = searchResults)
                        }
                    )
                }
            }
        }
    }
}
