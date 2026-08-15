package com.agon.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agon.app.data.Playlist
import com.agon.app.data.Song
import com.agon.app.data.formatTime
import com.agon.app.ui.components.Artwork
import com.agon.app.ui.components.ConfirmDialog
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.PlaylistTargetDialog
import com.agon.app.ui.components.TextInputDialog
import com.agon.app.ui.components.VmSongRow
import com.agon.app.viewmodel.PlayerViewModel

@Composable
fun PlaylistsScreen(vm: PlayerViewModel, onOpenPlaylist: (Long) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Playlists",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (vm.playlists.isEmpty()) {
                EmptyState(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    "No playlists yet",
                    "Create playlists to organize your music your way.",
                    actionLabel = "Create playlist",
                    onAction = { showCreate = true },
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 92.dp)) {
                    items(vm.playlists, key = { it.id }) { p ->
                        PlaylistRow(
                            playlist = p,
                            artUri = vm.playlistSongs(p).firstOrNull()?.artUri,
                            onClick = { onOpenPlaylist(p.id) },
                            onPlay = { vm.playSongs(vm.playlistSongs(p)) },
                            onRename = { renameTarget = p },
                            onDelete = { deleteTarget = p },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { showCreate = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) {
            Icon(Icons.Default.Add, "New playlist")
        }
    }

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
private fun PlaylistRow(
    playlist: Playlist,
    artUri: String?,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Artwork(artUri, Modifier.size(54.dp), corner = 12.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${playlist.songIds.size} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onPlay, enabled = playlist.songIds.isNotEmpty()) {
                Icon(Icons.Default.PlayArrow, "Play", tint = MaterialTheme.colorScheme.primary)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailScreen(vm: PlayerViewModel, playlistId: Long, onBack: () -> Unit) {
    val playlist = vm.playlists.firstOrNull { it.id == playlistId }
    if (playlist == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val songs = vm.playlistSongs(playlist)
    var showAdd by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var playlistTarget by remember { mutableStateOf<Song?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${songs.size} songs \u2022 ${formatTime(songs.sumOf { it.durationMs })}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { renaming = true }) {
                Icon(Icons.Default.Edit, "Rename", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { deleting = true }) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Button(onClick = { vm.playSongs(songs) }, enabled = songs.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Play")
            }
            FilledTonalButton(
                onClick = { if (songs.isNotEmpty()) vm.playSongs(songs, songs.indices.random(), shuffled = true) },
                enabled = songs.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Shuffle")
            }
            FilledTonalButton(onClick = { showAdd = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (songs.isEmpty()) {
            EmptyState(
                Icons.AutoMirrored.Filled.QueueMusic,
                "This playlist is empty",
                "Add songs from your library to get started.",
                actionLabel = "Add songs",
                onAction = { showAdd = true },
            )
        } else {
            LazyColumn {
                itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                    VmSongRow(
                        vm, s,
                        onClick = { vm.playSongs(songs, i) },
                        onAddToPlaylist = { playlistTarget = s },
                        extraMenuLabel = "Remove from playlist",
                        onExtraMenu = { vm.removeFromPlaylist(playlist.id, s.id) },
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    if (showAdd) AddSongsSheet(vm, playlist.id) { showAdd = false }
    if (renaming) {
        TextInputDialog("Rename playlist", playlist.name, "Rename", onDismiss = { renaming = false }) {
            vm.renamePlaylist(playlist.id, it)
        }
    }
    if (deleting) {
        ConfirmDialog(
            "Delete playlist?",
            "\"${playlist.name}\" will be removed. Songs stay in your library.",
            onDismiss = { deleting = false },
        ) {
            vm.deletePlaylist(playlist.id)
            onBack()
        }
    }
    PlaylistTargetDialog(vm, playlistTarget) { playlistTarget = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSongsSheet(vm: PlayerViewModel, playlistId: Long, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val current = vm.playlists.firstOrNull { it.id == playlistId }
    val results = remember(query, vm.songs) {
        if (query.isBlank()) vm.songs
        else vm.songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxHeight(0.85f)) {
            Text(
                "Add songs",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search songs\u2026") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
            LazyColumn(Modifier.weight(1f)) {
                items(results, key = { it.id }) { s ->
                    val inList = current != null && s.id in current.songIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.togglePlaylistSong(playlistId, s.id) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Artwork(s.artUri, Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(
                            if (inList) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                            contentDescription = if (inList) "In playlist" else "Add",
                            tint = if (inList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
