package com.emsabiq.emsamusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.emsabiq.emsamusic.services.AudioPlayerManager
import com.emsabiq.emsamusic.services.LocalLibraryManager
import com.emsabiq.emsamusic.ui.theme.SpotifyBlack
import com.emsabiq.emsamusic.ui.theme.SpotifyGreen
import com.emsabiq.emsamusic.ui.theme.SpotifyGrey
import java.util.Locale

@Composable
fun NowPlayingScreen(
    onDismiss: () -> Unit
) {
    val track by AudioPlayerManager.currentTrack.collectAsState()
    val isPlaying by AudioPlayerManager.isPlaying.collectAsState()
    val isLoading by AudioPlayerManager.isLoading.collectAsState()
    val currentTime by AudioPlayerManager.currentTimeMs.collectAsState()
    val duration by AudioPlayerManager.durationMs.collectAsState()
    val lyrics by AudioPlayerManager.lyrics.collectAsState()
    val currentLyricIdx by AudioPlayerManager.currentLyricIndex.collectAsState()
    val isShuffle by AudioPlayerManager.isShuffle.collectAsState()
    val repeatMode by AudioPlayerManager.repeatMode.collectAsState()

    val likedTracks by LocalLibraryManager.likedTracks.collectAsState()
    val context = LocalContext.current
    val isLiked = track?.let { LocalLibraryManager.isLiked(it) } ?: false

    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragSliderValue by remember { mutableFloatStateOf(0f) }

    val listState = rememberLazyListState()

    LaunchedEffect(currentLyricIdx) {
        if (showLyrics && currentLyricIdx >= 0 && currentLyricIdx < lyrics.size) {
            listState.animateScrollToItem(currentLyricIdx)
        }
    }

    if (track == null) return

    val thumbUrl = track?.thumbnailUrl ?: ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // 1. Dynamic Background Aura (Apple Music & iOS Live Ambient)
        if (thumbUrl.isNotEmpty()) {
            AsyncImage(
                model = thumbUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(65.dp)
            )
        }

        // Spotify Atmospheric Dark Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF22252C).copy(alpha = 0.85f),
                            Color(0xFF121212)
                        )
                    )
                )
        )

        // Main Foreground Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // Top Bar (Exact iOS Structure)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Dismiss",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "MEMUTAR DARI KOLEKSI",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = track?.artist ?: "EmsaMusic",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = { /* More Menu / Options */ },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Middle Content: Artwork vs Lyrics vs Queue
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (showLyrics) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        itemsIndexed(lyrics) { index, line ->
                            val isCurrent = index == currentLyricIdx
                            Text(
                                text = line.text,
                                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.35f),
                                fontSize = if (isCurrent) 22.sp else 18.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                lineHeight = 28.sp
                            )
                        }
                    }
                } else if (showQueue) {
                    // Queue List
                    val q = AudioPlayerManager.queue
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "Antrean Lagu Berikutnya",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        itemsIndexed(q) { idx, t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { AudioPlayerManager.play(t, fromQueue = AudioPlayerManager.originalQueue) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = t.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = t.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = t.artist,
                                        color = SpotifyGrey,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // iOS Perfect Square Artwork with Drop Shadow
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .shadow(
                                elevation = 16.dp,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = Color.Black.copy(alpha = 0.6f),
                                spotColor = Color.Black.copy(alpha = 0.6f)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF282828)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumbUrl.isNotEmpty()) {
                            AsyncImage(
                                model = thumbUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = SpotifyGrey,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title, Artist, & Like Heart (Exact iOS Layout)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track?.title ?: "",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track?.artist ?: "",
                        color = SpotifyGrey,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Heart Button
                IconButton(
                    onClick = {
                        track?.let { LocalLibraryManager.toggleLike(context, it) }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) SpotifyGreen else SpotifyGrey,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scrubber Slider & Monospaced Time Labels
            val maxDur = duration.toFloat().coerceAtLeast(1f)
            val sliderPos = if (isDraggingSlider) dragSliderValue else currentTime.toFloat().coerceIn(0f, maxDur)

            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderPos,
                    onValueChange = {
                        isDraggingSlider = true
                        dragSliderValue = it
                    },
                    onValueChangeFinished = {
                        isDraggingSlider = false
                        AudioPlayerManager.seekTo(dragSliderValue.toLong())
                    },
                    valueRange = 0f..maxDur,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = SpotifyGreen,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(sliderPos.toLong()),
                        color = SpotifyGrey,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatTime(duration),
                        color = SpotifyGrey,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Transport Controls (Shuffle, Prev, Big Play, Next, Repeat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(
                    onClick = { AudioPlayerManager.toggleShuffle() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffle) SpotifyGreen else SpotifyGrey,
                            modifier = Modifier.size(20.dp)
                        )
                        if (isShuffle) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(4.dp)
                                    .background(SpotifyGreen, CircleShape)
                            )
                        }
                    }
                }

                // Previous
                IconButton(
                    onClick = { AudioPlayerManager.playPrevious() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Signature Spotify Solid White Play/Pause Button
                Surface(
                    onClick = { AudioPlayerManager.togglePlayPause() },
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(26.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                // Next
                IconButton(
                    onClick = { AudioPlayerManager.playNext() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Repeat
                IconButton(
                    onClick = { AudioPlayerManager.cycleRepeatMode() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = when (repeatMode) {
                                androidx.media3.common.Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) SpotifyGreen else SpotifyGrey,
                            modifier = Modifier.size(20.dp)
                        )
                        if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(4.dp)
                                    .background(SpotifyGreen, CircleShape)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Utilities: Lyrics Toggle & Queue Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // Lyrics Toggle Button
                IconButton(
                    onClick = {
                        showLyrics = !showLyrics
                        if (showLyrics) showQueue = false
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = "Lyrics",
                        tint = if (showLyrics) SpotifyGreen else SpotifyGrey,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Queue Toggle Button
                IconButton(
                    onClick = {
                        showQueue = !showQueue
                        if (showQueue) showLyrics = false
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = if (showQueue) SpotifyGreen else SpotifyGrey,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    val m = sec / 60
    val s = sec % 60
    return String.format(Locale.getDefault(), "%d:%02d", m, s)
}
