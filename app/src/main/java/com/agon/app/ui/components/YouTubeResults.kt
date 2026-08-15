package com.agon.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agon.app.data.formatTime
import com.agon.app.data.youtube.YtAlbum
import com.agon.app.data.youtube.YtArtist
import com.agon.app.data.youtube.YtTrack
import com.agon.app.viewmodel.PlayerViewModel

/**
 * YouTube Music results for the search screen. Purely additive: it reuses the same
 * row layout, artwork component and player entry points as the local library.
 */
@Composable
fun YouTubeResults(vm: PlayerViewModel, query: String) {
    val results = vm.ytResults
    when {
        query.length < 2 -> EmptyState(
            Icons.Default.Search,
            "Search YouTube Music",
            "Find songs, artists and albums from YouTube and play them right here.",
        )
        vm.ytSearching && results.isEmpty -> LoadingState("Searching YouTube Music\u2026")
        results.isEmpty -> EmptyState(
            Icons.Default.SearchOff,
            "No results",
            vm.ytError ?: "Nothing on YouTube Music matches \"$query\".",
        )
        else -> LazyColumn(Modifier.fillMaxSize()) {
            if (results.tracks.isNotEmpty()) {
                item(key = "yth-songs") { SectionHeader("Songs") }
                items(results.tracks, key = { "ytt${it.videoId}" }) { track ->
                    YtTrackRow(
                        track = track,
                        onClick = { vm.playYtTrack(track, results.tracks) },
                        onPlayNext = { vm.playYtTrackNext(track) },
                        onAddToQueue = { vm.queueYtTrack(track) },
                    )
                }
            }
            if (results.albums.isNotEmpty()) {
                item(key = "yth-albums") { SectionHeader("Albums") }
                items(results.albums, key = { "yta${it.browseId}" }) { album ->
                    YtAlbumRow(album) { vm.playYtAlbum(album) }
                }
            }
            if (results.artists.isNotEmpty()) {
                item(key = "yth-artists") { SectionHeader("Artists") }
                items(results.artists, key = { "ytr${it.browseId}" }) { artist ->
                    YtArtistRow(artist) { vm.playYtArtist(artist) }
                }
            }
            item(key = "yt-foot") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun YtTrackRow(
    track: YtTrack,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Artwork(track.thumbnailUrl, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (track.durationMs > 0) "${track.artist} \u2022 ${formatTime(track.durationMs)}" else track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    leadingIcon = { Icon(Icons.Default.SkipNext, null) },
                    onClick = { menuOpen = false; onPlayNext() },
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                    onClick = { menuOpen = false; onAddToQueue() },
                )
            }
        }
    }
}

@Composable
private fun YtAlbumRow(album: YtAlbum, onPlay: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Artwork(album.thumbnailUrl, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                album.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (album.year.isNotEmpty()) "${album.artist} \u2022 ${album.year}" else album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Default.PlayArrow, "Play album", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun YtArtistRow(artist: YtArtist, onPlay: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        if (artist.thumbnailUrl.isEmpty()) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) }
        } else {
            Artwork(artist.thumbnailUrl, Modifier.size(48.dp), corner = 24.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                artist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                artist.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(Icons.Default.PlayArrow, "Play artist", tint = MaterialTheme.colorScheme.primary)
    }
}
