package com.agon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.Album
import com.agon.app.data.ArtistInfo
import com.agon.app.data.Playlist
import com.agon.app.data.Song
import com.agon.app.ui.components.ConfirmDialog
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.LoadingState
import com.agon.app.ui.components.PlaylistTargetDialog
import com.agon.app.ui.components.SongCollectionSheet
import com.agon.app.ui.components.TextInputDialog
import com.agon.app.viewmodel.PlayerViewModel
import java.util.Calendar

private val homeTabs = listOf("Songs", "Albums", "Artists", "Favorites", "Playlists", "History")

/**
 * Combined Home: quick actions on top, then the whole local library (songs, albums,
 * artists, favorites, playlists and history) in tabs.
 */
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var albumSheet by remember { mutableStateOf<Album?>(null) }
    var artistSheet by remember { mutableStateOf<ArtistInfo?>(null) }
    var playlistTarget by remember { mutableStateOf<Song?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

    Column(Modifier.fillMaxSize()) {
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
        ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
        ) {
            homeTabs.forEachIndexed { i, t ->
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
            3 -> if (vm.favoriteSongs.isEmpty()) EmptyState(
                Icons.Default.FavoriteBorder,
                "No favorites yet",
                "Tap the heart on any song to collect it here.",
            ) else SongList(vm, vm.favoriteSongs) { playlistTarget = it }
            4 -> PlaylistsTab(
                vm,
                onOpenPlaylist = onOpenPlaylist,
                onCreate = { showCreate = true },
                onRename = { renameTarget = it },
                onDelete = { deleteTarget = it },
            )
            else -> HistoryTab(vm) { playlistTarget = it }
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
    if (showCreate) {
        TextInputDialog("New playlist", "", "Create", onDismiss = { showCreate = false }) {
            vm.createPlaylist(it)
        }
    }
    renameTarget?.let { p ->
        TextInputDialog("Rename playlist", p.name, "Rename", onDismiss = { renameTarget = null }) {
            vm.renamePlaylist(p.id, it)
        }
    }
    deleteTarget?.let { p ->
        ConfirmDialog(
            "Delete playlist?",
            "\"${p.name}\" will be removed. Songs stay in your library.",
            onDismiss = { deleteTarget = null },
        ) { vm.deletePlaylist(p.id) }
    }
}

@Composable
private fun PlaylistsTab(
    vm: PlayerViewModel,
    onOpenPlaylist: (Long) -> Unit,
    onCreate: () -> Unit,
    onRename: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
) {
    if (vm.playlists.isEmpty()) {
        EmptyState(
            Icons.AutoMirrored.Filled.QueueMusic,
            "No playlists yet",
            "Create playlists to organize your music your way.",
            actionLabel = "Create playlist",
            onAction = onCreate,
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    "${vm.playlists.size} playlists",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCreate) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }
        }
        items(vm.playlists, key = { it.id }) { p ->
            PlaylistRow(
                playlist = p,
                artUri = vm.playlistSongs(p).firstOrNull()?.artUri,
                onClick = { onOpenPlaylist(p.id) },
                onPlay = { vm.playSongs(vm.playlistSongs(p)) },
                onRename = { onRename(p) },
                onDelete = { onDelete(p) },
            )
        }
    }
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
