package com.agon.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.YouTubeLibrary
import com.agon.app.ui.components.YouTubeResults
import com.agon.app.viewmodel.PlayerViewModel

/**
 * Dedicated YouTube Music section: account sign-in, the signed-in library (liked songs
 * and playlists), and search - all in one top-level destination instead of a hidden tab.
 */
@Composable
fun YouTubeMusicScreen(vm: PlayerViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()

    // Only talk to the network while this screen is on the back stack.
    LaunchedEffect(q) { vm.updateYtQuery(q) }
    DisposableEffect(Unit) { onDispose { vm.clearYtSearch() } }

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "YouTube Music",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (vm.ytSignedIn) {
                        "${vm.ytLikedSongs.size} liked songs \u2022 ${vm.ytPlaylists.size} playlists"
                    } else {
                        "Search, or sign in for your library"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (vm.ytSignedIn) {
                TextButton(onClick = vm::signOutYt) { Text("Sign out") }
            } else {
                TextButton(onClick = vm::startYtLogin) { Text("Sign in") }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Songs, artists, albums on YouTube\u2026") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") }
                }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        if (q.isBlank()) {
            if (vm.ytSignedIn) {
                YouTubeLibrary(vm)
            } else {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = "YouTube Music",
                    subtitle = "Search the full YouTube catalog, or sign in with Google to see your liked songs and playlists.",
                    actionLabel = "Sign in with Google",
                    onAction = vm::startYtLogin,
                )
            }
        } else {
            YouTubeResults(vm, q)
        }
    }
}
