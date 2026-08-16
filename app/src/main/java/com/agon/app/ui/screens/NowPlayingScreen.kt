package com.agon.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import com.agon.app.data.Song
import com.agon.app.player.TempoPresets
import com.agon.app.data.formatTime
import com.agon.app.ui.components.Artwork
import com.agon.app.ui.components.MusicVisualizer
import com.agon.app.ui.components.PlaylistTargetDialog
import com.agon.app.ui.components.SleepTimerDialog
import com.agon.app.ui.components.formatCountdown
import com.agon.app.ui.components.pitchLabel
import com.agon.app.viewmodel.PlayerViewModel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun NowPlayingScreen(vm: PlayerViewModel, onClose: () -> Unit, onOpenEqualizer: () -> Unit) {
    val song = vm.currentSong
    if (song == null) {
        LaunchedEffect(Unit) { onClose() }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showVolumeSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var playlistTarget by remember { mutableStateOf<Song?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                // Fully opaque so nothing behind the player shows through. The accent is
                // composited over the background instead of layered with alpha.
                Brush.verticalGradient(
                    listOf(
                        lerp(MaterialTheme.colorScheme.background, accent, 0.2f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
            // Swallow taps that land on the background so they can't pass through to the
            // screen below. Buttons/sliders still work because they consume their own
            // gestures first (child nodes are processed before this parent).
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* no-op: just block pass-through */ },
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        // Top bar
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.KeyboardArrowDown, "Close", Modifier.size(30.dp))
            }
            Text(
                "NOW PLAYING",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { playlistTarget = song }) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist")
            }
        }

        // Playback failure banner (the snackbar lives behind this overlay, so surface
        // errors here instead).
        vm.playbackError?.let { err ->
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                ) {
                    Text(
                        err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = vm::retryCurrent) { Text("Retry") }
                    if (!vm.ytSignedIn) {
                        TextButton(onClick = vm::startYtLogin) { Text("Sign in") }
                    }
                }
            }
        }

        // Artwork / lyrics
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = showLyrics, label = "artLyrics") { ly ->
                if (ly) LyricsPanel(vm) else ArtworkBlock(song, vm.isPlaying, accent)
            }
        }

        MusicVisualizer(
            vm.visualizerStyle, vm.isPlaying, accent,
            Modifier.fillMaxWidth().height(44.dp),
        )
        Spacer(Modifier.height(14.dp))

        // Title / favorite
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val fav = song.id in vm.favorites
            IconButton(onClick = { vm.toggleFavorite(song.id) }) {
                Icon(
                    if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Favorite",
                    tint = if (fav) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        // Seek bar
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(0f) }
        val dur = vm.durationMs.coerceAtLeast(1L)
        val sliderValue = if (dragging) dragValue else (vm.positionMs.toFloat() / dur).coerceIn(0f, 1f)
        Slider(
            value = sliderValue,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                vm.seekTo((dragValue * dur).toLong())
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatTime(if (dragging) (dragValue * dur).toLong() else vm.positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatTime(vm.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))

        // Main controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = vm::toggleShuffle) {
                Icon(
                    Icons.Default.Shuffle, "Shuffle",
                    tint = if (vm.shuffleOn) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(40.dp))
            }
            PlayPauseButton(vm.isPlaying, vm::togglePlayPause)
            IconButton(onClick = vm::next, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipNext, "Next", Modifier.size(40.dp))
            }
            IconButton(onClick = vm::cycleRepeat) {
                Icon(
                    if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Repeat",
                    tint = if (vm.repeatMode != Player.REPEAT_MODE_OFF) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // Secondary controls
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SecondaryControl(Icons.Default.GraphicEq, "EQ", vm.eqEnabled, onOpenEqualizer)
            SecondaryControl(
                Icons.Default.Tune,
                if (vm.pitchSemitones != 0f) pitchLabel(vm.pitchSemitones) else "Pitch",
                vm.pitchSemitones != 0f || vm.speed != 1f,
            ) { showAudioSheet = true }
            SecondaryControl(
                Icons.Default.Bedtime,
                vm.sleepRemainingMs?.let { formatCountdown(it) } ?: "Timer",
                vm.sleepRemainingMs != null || vm.stopAfterCurrent,
            ) { showTimerDialog = true }
            SecondaryControl(Icons.Default.Lyrics, "Lyrics", showLyrics) { showLyrics = !showLyrics }
            SecondaryControl(Icons.AutoMirrored.Filled.VolumeUp, "Volume", false) { showVolumeSheet = true }
            SecondaryControl(Icons.AutoMirrored.Filled.QueueMusic, "Queue", false) { showQueueSheet = true }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Made by Natey<3",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
    }

    if (showAudioSheet) AudioSheet(vm) { showAudioSheet = false }
    if (showTimerDialog) SleepTimerDialog(vm) { showTimerDialog = false }
    if (showVolumeSheet) VolumeSheet(vm) { showVolumeSheet = false }
    if (showQueueSheet) QueueSheet(vm) { showQueueSheet = false }
    PlaylistTargetDialog(vm, playlistTarget) { playlistTarget = null }
}

@Composable
private fun ArtworkBlock(song: Song, playing: Boolean, accent: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAnim",
    )
    val idle by animateFloatAsState(if (playing) 1f else 0.96f, tween(400), label = "idle")
    val scale = if (playing) pulse * idle else idle
    AnimatedContent(
        targetState = song,
        transitionSpec = {
            (fadeIn(tween(400)) + scaleIn(initialScale = 0.9f, animationSpec = tween(400))) togetherWith fadeOut(tween(250))
        },
        label = "artSwap",
    ) { s ->
        Artwork(
            s.artUri,
            Modifier
                .aspectRatio(1f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(24.dp, RoundedCornerShape(26.dp), ambientColor = accent, spotColor = accent),
            corner = 26.dp,
        )
    }
}

@Composable
private fun LyricsPanel(vm: PlayerViewModel) {
    val song = vm.currentSong ?: return
    LaunchedEffect(song.id) { vm.loadLyrics(song) }
    val lyrics = vm.lyrics
    when {
        lyrics == null -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        lyrics.isEmpty() -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                Icons.Default.Lyrics, null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("No lyrics found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Place a .lrc file next to the audio file\nto see synced lyrics here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        else -> {
            val synced = lyrics.any { it.timeMs >= 0 }
            val listState = rememberLazyListState()
            val currentIndex = if (synced) lyrics.indexOfLast { it.timeMs in 0..vm.positionMs } else -1
            LaunchedEffect(currentIndex) {
                if (currentIndex >= 0) listState.animateScrollToItem(maxOf(0, currentIndex - 2))
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(lyrics) { i, line ->
                    val active = i == currentIndex
                    Text(
                        line.text,
                        style = if (active) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = synced && line.timeMs >= 0) { vm.seekTo(line.timeMs) }
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "pp")
    FilledIconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier.size(76.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
        ),
    ) {
        AnimatedContent(targetState = isPlaying, label = "ppIcon") { playing ->
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (playing) "Pause" else "Play",
                Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun SecondaryControl(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 36.dp)) {
            Text("Presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "One-tap speed + pitch combos: Nightcore, Deep, Vaporwave\u2026",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                TempoPresets.presets.forEach { preset ->
                    FilterChip(
                        selected = vm.tempoPreset == preset.name,
                        onClick = { vm.applyTempoPreset(preset.name) },
                        label = { Text(preset.name) },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pitch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(pitchLabel(vm.pitchSemitones), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = vm.pitchSemitones,
                onValueChange = { vm.setPitch((it * 2).roundToInt() / 2f) },
                valueRange = -12f..12f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("-12", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("+12", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.nudgePitch(-1f) }, modifier = Modifier.weight(1f)) { Text("-1") }
                OutlinedButton(onClick = { vm.nudgePitch(-0.5f) }, modifier = Modifier.weight(1f)) { Text("-\u00BD") }
                FilledTonalButton(onClick = vm::resetPitch, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.RestartAlt, "Reset pitch", Modifier.size(18.dp))
                }
                OutlinedButton(onClick = { vm.nudgePitch(0.5f) }, modifier = Modifier.weight(1f)) { Text("+\u00BD") }
                OutlinedButton(onClick = { vm.nudgePitch(1f) }, modifier = Modifier.weight(1f)) { Text("+1") }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                listOf(-7f, -5f, -2f, 0f, 2f, 5f, 7f).forEach { p ->
                    FilterChip(
                        selected = vm.pitchSemitones == p,
                        onClick = { vm.setPitch(p) },
                        label = { Text(if (p > 0) "+${p.toInt()}" else "${p.toInt()}") },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tempo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    String.format(Locale.US, "%.2fx", vm.speed),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = vm.speed,
                onValueChange = { vm.setPlaybackSpeed((it * 20).roundToInt() / 20f) },
                valueRange = 0.5f..2f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                    FilterChip(
                        selected = vm.speed == s,
                        onClick = { vm.setPlaybackSpeed(s) },
                        label = { Text(String.format(Locale.US, "%.2fx", s).removeSuffix("0").removeSuffix("0").removeSuffix(".")) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Pitch and tempo are processed independently \u2014 shift the key without changing speed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 44.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Volume", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "${(vm.volume * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = vm.volume,
                    onValueChange = { vm.updateVolume(it) },
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                )
                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Controls in-app playback volume.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxHeight(0.85f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Up Next", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${vm.queue.size} tracks \u2022 hold & drag to reorder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { vm.clearQueue() }, enabled = vm.queue.size > 1) { Text("Clear") }
            }
            Spacer(Modifier.height(8.dp))

            fun itemAt(index: Int) = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }
                                    ?.let { dragIndex = it.index; dragDelta = 0f }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                if (dragIndex >= 0) {
                                    dragDelta += amount.y
                                    val current = itemAt(dragIndex)
                                    if (current != null) {
                                        val start = current.offset + dragDelta
                                        val end = start + current.size
                                        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                            item.index != dragIndex &&
                                                ((dragDelta > 0 && end > item.offset + item.size / 2 && item.index > dragIndex) ||
                                                    (dragDelta < 0 && start < item.offset + item.size / 2 && item.index < dragIndex))
                                        }
                                        if (target != null && target.index < vm.queue.size) {
                                            vm.moveQueueItem(dragIndex, target.index)
                                            dragDelta -= (target.index - dragIndex) * current.size
                                            dragIndex = target.index
                                        }
                                    }
                                }
                            },
                            onDragEnd = { dragIndex = -1; dragDelta = 0f },
                            onDragCancel = { dragIndex = -1; dragDelta = 0f },
                        )
                    },
            ) {
                itemsIndexed(vm.queue) { i, s ->
                    val isDragging = i == dragIndex
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer { translationY = if (isDragging) dragDelta else 0f }
                            .fillMaxWidth()
                            .background(
                                if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest
                                else Color.Transparent
                            )
                            .clickable { vm.playFromQueue(i) }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            Icons.Default.DragHandle, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Artwork(s.artUri, Modifier.size(44.dp), corner = 8.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (i == vm.queueIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                        Text(
                            formatTime(s.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = { vm.removeFromQueue(i) }, enabled = vm.queue.size > 1) {
                            Icon(Icons.Default.Close, "Remove", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
