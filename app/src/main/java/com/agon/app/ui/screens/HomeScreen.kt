package com.agon.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.sp
import com.agon.app.data.Song
import com.agon.app.ui.components.Artwork
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.LoadingState
import com.agon.app.ui.components.PlaylistTargetDialog
import com.agon.app.ui.components.SectionHeader
import com.agon.app.ui.components.VmSongRow
import com.agon.app.viewmodel.PlayerViewModel
import java.util.Calendar

@Composable
fun HomeScreen(
    vm: PlayerViewModel,
    onOpenSearch: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    when {
        vm.isLoading -> LoadingState("Scanning your music\u2026")
        vm.songs.isEmpty() -> EmptyState(
            icon = Icons.Default.LibraryMusic,
            title = "No music found",
            subtitle = "Add audio files to your device storage, then rescan your library.",
            actionLabel = "Rescan",
            onAction = vm::rescanLibrary,
        )
        else -> HomeContent(vm, onOpenSearch, onOpenPlaylist)
    }
}

@Composable
private fun HomeContent(
    vm: PlayerViewModel,
    onOpenSearch: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    var playlistTarget by remember { mutableStateOf<Song?>(null) }
    val recents = vm.recentSongs
    val recentlyAdded = remember(vm.songs) { vm.songs.sortedByDescending { it.dateAdded }.take(8) }
    val favorites = vm.favoriteSongs

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(
                            "RED",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp,
                        )
                        Text(
                            "LINE",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                        )
                    }
                    Text(
                        greeting(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Default.Search, "Search", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Button(onClick = vm::shuffleAll, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Shuffle, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle all")
                }
                FilledTonalButton(onClick = { vm.playSongs(vm.songs) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play all")
                }
            }
        }
        if (recents.isNotEmpty()) {
            item { SectionHeader("Recently played") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(recents.take(12), key = { _, s -> s.id }) { i, s ->
                        Column(
                            modifier = Modifier
                                .width(132.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { vm.playSongs(recents, i) }
                                .padding(4.dp),
                        ) {
                            Artwork(s.artUri, Modifier.fillMaxWidth().aspectRatio(1f), corner = 12.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                s.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                s.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (vm.playlists.isNotEmpty()) {
            item { SectionHeader("Your playlists") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(vm.playlists, key = { _, p -> p.id }) { _, p ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.clickable { onOpenPlaylist(p.id) },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.QueueMusic, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(p.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(
                                        "${p.songIds.size} songs",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (favorites.isNotEmpty()) {
            item { SectionHeader("Favorites") }
            itemsIndexed(favorites.take(5), key = { _, s -> "fav${s.id}" }) { i, s ->
                VmSongRow(vm, s, onClick = { vm.playSongs(favorites, i) }, onAddToPlaylist = { playlistTarget = s })
            }
        }
        item { SectionHeader("Recently added") }
        itemsIndexed(recentlyAdded, key = { _, s -> "new${s.id}" }) { i, s ->
            VmSongRow(vm, s, onClick = { vm.playSongs(recentlyAdded, i) }, onAddToPlaylist = { playlistTarget = s })
        }
    }
    PlaylistTargetDialog(vm, playlistTarget) { playlistTarget = null }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> "Late night session"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}
