package com.emsabiq.emsamusic

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.emsabiq.emsamusic.services.PlaybackService
import com.emsabiq.emsamusic.ui.components.MiniPlayer
import com.emsabiq.emsamusic.ui.components.NowPlayingScreen
import com.emsabiq.emsamusic.ui.screens.HomeScreen
import com.emsabiq.emsamusic.ui.screens.SearchScreen
import com.emsabiq.emsamusic.ui.theme.EmsaMusicTheme
import com.emsabiq.emsamusic.ui.theme.SpotifyBlack
import com.emsabiq.emsamusic.ui.theme.SpotifyDarkGrey
import com.emsabiq.emsamusic.ui.theme.SpotifyGreen
import com.emsabiq.emsamusic.ui.theme.SpotifyGrey

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start media session service for background playback
        val serviceIntent = Intent(this, PlaybackService::class.java)
        startService(serviceIntent)

        setContent {
            EmsaMusicTheme {
                MainContent()
            }
        }
    }
}

@Composable
fun MainContent() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showNowPlaying by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = SpotifyBlack,
        bottomBar = {
            Column {
                MiniPlayer(
                    onExpand = { showNowPlaying = true }
                )

                NavigationBar(
                    containerColor = SpotifyDarkGrey,
                    contentColor = Color.White
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Utama") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SpotifyGreen,
                            selectedTextColor = SpotifyGreen,
                            unselectedIconColor = SpotifyGrey,
                            unselectedTextColor = SpotifyGrey,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Cari") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SpotifyGreen,
                            selectedTextColor = SpotifyGreen,
                            unselectedIconColor = SpotifyGrey,
                            unselectedTextColor = SpotifyGrey,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> HomeScreen()
                1 -> SearchScreen()
            }
        }
    }

    if (showNowPlaying) {
        NowPlayingScreen(
            onDismiss = { showNowPlaying = false }
        )
    }
}
