package com.agon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agon.app.data.Album
import com.agon.app.data.ArtistInfo
import com.agon.app.data.Song
import com.agon.app.ui.components.Artwork
import com.agon.app.ui.components.EmptyState
import com.agon.app.ui.components.PlaylistTargetDialog
import com.agon.app.ui.components.SectionHeader
import com.agon.app.ui.components.SongCollectionSheet
import com.agon.app.ui.components.VmSongRow
import com.agon.app.ui.components.YouTubeResults
import com.agon.app.viewmodel.PlayerViewModel

private val categories = listOf("All", "Songs", "Albums", "Artists", "Playlists", "YouTube")

@Composable
fun SearchScreen(vm: PlayerViewModel, onOpenPlaylist: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var albumSheet by remember { mutableStateOf<Album?>(null) }
    var artistSheet by remember { mutableStateOf<ArtistInfo?>(null) }
    var playlistTarget by remember { mutableStateOf<Song?>(null) }

    val q = query.trim()
    val youtubeTab = category == "YouTube"

    // Only the YouTube tab talks to the network, and only while it is visible.
    LaunchedEffect(youtubeTab, q) {
        if (youtubeTab) vm.updateYtQuery(q) else vm.clearYtSearch()
    }
    DisposableEffect(Unit) { onDispose { vm.clearYtSearch() } }

    val songResults = remember(q, vm.songs) {
        if (q.isBlank()) emptyList()
        else vm.songs.filter { it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) }
    }
    val albumResults = remember(q, vm.albums) {
        if (q.isBlank()) emptyList() else vm.albums.filter { it.name.contains(q, true) || it.artist.contains(q, true) }
    }
    val artistResults = remember(q, vm.artists) {
        if (q.isBlank()) emptyList() else vm.artists.filter { it.name.contains(q, true) }
    }
    val playlistResults = remember(q, vm.playlists) {
        if (q.isBlank()) emptyList() else vm.playlists.filter { it.name.contains(q, true) }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Search",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Songs, artists, albums\u2026") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") }
                }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            categories.forEach { c ->
                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
            }
        }
        when {
            youtubeTab -> YouTubeResults(vm, q)
            q.isBlank() -> EmptyState(
                Icons.Default.Search,
                "Search your library",
                "${vm.songs.size} songs \u2022 ${vm.albums.size} albums \u2022 ${vm.artists.size} artists",
            )
            songResults.isEmpty() && albumResults.isEmpty() && artistResults.isEmpty() && playlistResults.isEmpty() ->
                EmptyState(Icons.Default.SearchOff, "No results", "Nothing matches \"$q\".")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (songResults.isNotEmpty() && (category == "All" || category == "Songs")) {
                    item(key = "hs") { SectionHeader("Songs") }
                    itemsIndexed(songResults.take(30), key = { _, s -> "s${s.id}" }) { i, s ->
                        VmSongRow(vm, s, onClick = { vm.playSongs(songResults, i) }, onAddToPlaylist = { playlistTarget = s })
                    }
                }
                if (albumResults.isNotEmpty() && (category == "All" || category == "Albums")) {
                    item(key = "ha") { SectionHeader("Albums") }
                    itemsIndexed(albumResults.take(15), key = { _, a -> "a${a.id}" }) { _, a ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { albumSheet = a }.padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Artwork(a.artUri, Modifier.size(48.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(a.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${a.artist} \u2022 ${a.songCount} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
                if (artistResults.isNotEmpty() && (category == "All" || category == "Artists")) {
                    item(key = "hr") { SectionHeader("Artists") }
                    itemsIndexed(artistResults.take(15), key = { _, a -> "r${a.name}" }) { _, a ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { artistSheet = a }.padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Box(
                                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(a.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${a.songCount} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (playlistResults.isNotEmpty() && (category == "All" || category == "Playlists")) {
                    item(key = "hp") { SectionHeader("Playlists") }
                    itemsIndexed(playlistResults, key = { _, p -> "p${p.id}" }) { _, p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onOpenPlaylist(p.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${p.songIds.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    albumSheet?.let { a ->
        SongCollectionSheet(a.name, "${a.artist} \u2022 ${a.songCount} songs", vm.songs.filter { it.albumId == a.id }, vm) { albumSheet = null }
    }
    artistSheet?.let { a ->
        SongCollectionSheet(a.name, "${a.songCount} songs", vm.songs.filter { it.artist == a.name }, vm) { artistSheet = null }
    }
    PlaylistTargetDialog(vm, playlistTarget) { playlistTarget = null }
}
