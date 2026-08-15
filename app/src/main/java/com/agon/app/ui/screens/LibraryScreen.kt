package com.agon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agon.app.data.Album
import com.agon.app.data.ArtistInfo
import com.agon.app.data.Song
import com.agon.app.ui.components.Artwork
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.PlaylistTargetDialog
import com.agon.app.ui.components.SongCollectionSheet
import com.agon.app.ui.components.VmSongRow
import com.agon.app.viewmodel.PlayerViewModel

private val tabs = listOf("Songs", "Albums", "Artists", "Favorites", "History")

@Composable
fun LibraryScreen(vm: PlayerViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var albumSheet by remember { mutableStateOf<Album?>(null) }
    var artistSheet by remember { mutableStateOf<ArtistInfo?>(null) }
    var playlistTarget by remember { mutableStateOf<Song?>(null) }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Library",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
        ) {
            tabs.forEachIndexed { i, t ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(t) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (tab) {
            0 -> SongList(vm, vm.songs) { playlistTarget = it }
            1 -> AlbumsGrid(vm) { albumSheet = it }
            2 -> ArtistsList(vm) { artistSheet = it }
            3 -> {
                if (vm.favoriteSongs.isEmpty()) EmptyState(
                    Icons.Default.FavoriteBorder,
                    "No favorites yet",
                    "Tap the heart on any song to collect it here.",
                ) else SongList(vm, vm.favoriteSongs) { playlistTarget = it }
            }
            4 -> HistoryTab(vm) { playlistTarget = it }
        }
    }

    albumSheet?.let { a ->
        SongCollectionSheet(
            title = a.name,
            subtitle = "${a.artist} \u2022 ${a.songCount} songs",
            songs = vm.songs.filter { it.albumId == a.id },
            vm = vm,
        ) { albumSheet = null }
    }
    artistSheet?.let { a ->
        SongCollectionSheet(
            title = a.name,
            subtitle = "${a.songCount} songs \u2022 ${a.albumCount} albums",
            songs = vm.songs.filter { it.artist == a.name },
            vm = vm,
        ) { artistSheet = null }
    }
    PlaylistTargetDialog(vm, playlistTarget) { playlistTarget = null }
}

@Composable
private fun SongList(vm: PlayerViewModel, list: List<Song>, onAddToPlaylist: (Song) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    "${list.size} songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.playSongs(list) }, enabled = list.isNotEmpty()) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play")
                }
                TextButton(
                    onClick = { if (list.isNotEmpty()) vm.playSongs(list, list.indices.random(), shuffled = true) },
                    enabled = list.isNotEmpty(),
                ) {
                    Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Shuffle")
                }
            }
        }
        itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
            VmSongRow(vm, s, onClick = { vm.playSongs(list, i) }, onAddToPlaylist = { onAddToPlaylist(s) })
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun AlbumsGrid(vm: PlayerViewModel, onOpen: (Album) -> Unit) {
    if (vm.albums.isEmpty()) {
        EmptyState(Icons.Default.FavoriteBorder, "No albums", "Your device has no album metadata yet.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(vm.albums, key = { it.id }) { album ->
            Column(
                Modifier.clip(RoundedCornerShape(14.dp)).clickable { onOpen(album) },
            ) {
                Artwork(album.artUri, Modifier.fillMaxWidth().aspectRatio(1f), corner = 14.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    album.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Text(
                    "${album.artist} \u2022 ${album.songCount} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ArtistsList(vm: PlayerViewModel, onOpen: (ArtistInfo) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(vm.artists, key = { it.name }) { artist ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(artist) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Person, null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(artist.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${artist.songCount} songs \u2022 ${artist.albumCount} albums",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(vm: PlayerViewModel, onAddToPlaylist: (Song) -> Unit) {
    val list = vm.recentSongs
    if (list.isEmpty()) {
        EmptyState(Icons.Default.History, "No history yet", "Songs you play will appear here.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text(
                    "${list.size} recently played",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::clearHistory) { Text("Clear") }
            }
        }
        itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
            VmSongRow(vm, s, onClick = { vm.playSongs(list, i) }, onAddToPlaylist = { onAddToPlaylist(s) })
        }
    }
}
