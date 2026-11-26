package com.emsabiq.emsamusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.emsabiq.emsamusic.services.AudioPlayerManager
import com.emsabiq.emsamusic.services.LocalLibraryManager
import com.emsabiq.emsamusic.ui.theme.SpotifyCard
import com.emsabiq.emsamusic.ui.theme.SpotifyGreen
import com.emsabiq.emsamusic.ui.theme.SpotifyGrey

@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
    onExpand: () -> Unit
) {
    val track by AudioPlayerManager.currentTrack.collectAsState()
    val isPlaying by AudioPlayerManager.isPlaying.collectAsState()
    val isLoading by AudioPlayerManager.isLoading.collectAsState()
    val currentTime by AudioPlayerManager.currentTimeMs.collectAsState()
    val duration by AudioPlayerManager.durationMs.collectAsState()

    val likedTracks by LocalLibraryManager.likedTracks.collectAsState()
    val context = LocalContext.current
    val isLiked = track?.let { LocalLibraryManager.isLiked(it) } ?: false

    if (track == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF242424))
            .clickable { onExpand() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track?.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(6.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track?.title ?: "",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track?.artist ?: "",
                    color = SpotifyGrey,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // iOS-Style Heart Icon
            IconButton(
                onClick = { track?.let { LocalLibraryManager.toggleLike(context, it) } },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) SpotifyGreen else SpotifyGrey,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                IconButton(
                    onClick = { AudioPlayerManager.togglePlayPause() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Mini Progress Bar
        val progress = if (duration > 0) (currentTime.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = SpotifyGreen,
            trackColor = Color.White.copy(alpha = 0.15f)
        )
    }
}
