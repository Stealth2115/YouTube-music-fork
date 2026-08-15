package com.agon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.ui.components.SleepTimerDialog
import com.agon.app.ui.components.VisualizerStyles
import com.agon.app.ui.components.formatCountdown
import com.agon.app.ui.components.pitchLabel
import com.agon.app.ui.theme.AccentOptions
import com.agon.app.ui.theme.BackgroundOptions
import com.agon.app.viewmodel.PlayerViewModel
import java.util.Locale

@Composable
fun SettingsScreen(vm: PlayerViewModel, onOpenEqualizer: () -> Unit) {
    var showTimer by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        item {
            SettingsCard("APPEARANCE") {
                Text("Accent", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 10.dp)) {
                    AccentOptions.forEach { (name, color) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(color)
                                    .clickable { vm.setAccent(name) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (vm.accentName == name) {
                                    Icon(Icons.Default.Check, null, tint = androidx.compose.ui.graphics.Color.White)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("Background", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    BackgroundOptions.forEach { bg ->
                        FilterChip(selected = vm.bgName == bg, onClick = { vm.setBackground(bg) }, label = { Text(bg) })
                    }
                }
            }
        }
        item {
            SettingsCard("AUDIO") {
                SettingRow(
                    Icons.Default.GraphicEq, "Equalizer & effects",
                    if (vm.eqEnabled) "On \u2022 ${vm.eqPreset}" else "Off",
                    onClick = onOpenEqualizer,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    Icons.Default.Tune, "Pitch", pitchLabel(vm.pitchSemitones),
                    trailing = {
                        TextButton(onClick = vm::resetPitch, enabled = vm.pitchSemitones != 0f) { Text("Reset") }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    Icons.Default.Speed, "Tempo", String.format(Locale.US, "%.2fx", vm.speed),
                    trailing = {
                        TextButton(onClick = { vm.setPlaybackSpeed(1f) }, enabled = vm.speed != 1f) { Text("Reset") }
                    },
                )
            }
        }
        item {
            SettingsCard("PLAYBACK") {
                SettingRow(
                    Icons.Default.MusicNote, "Resume last session", "Restore your queue when the app starts",
                    trailing = {
                        Switch(
                            checked = vm.resumeSession,
                            onCheckedChange = { vm.setResumeSession(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    Icons.Default.Bedtime, "Sleep timer",
                    vm.sleepRemainingMs?.let { "Stopping in ${formatCountdown(it)}" }
                        ?: if (vm.stopAfterCurrent) "Stopping after current song" else "Off",
                    onClick = { showTimer = true },
                )
            }
        }
        item {
            SettingsCard("VISUALIZER") {
                Text(
                    "Style shown on the player screen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    VisualizerStyles.forEach { s ->
                        FilterChip(selected = vm.visualizerStyle == s, onClick = { vm.setVisualizer(s) }, label = { Text(s) })
                    }
                }
            }
        }
        item {
            SettingsCard("LYRICS") {
                SettingRow(
                    Icons.Default.Lyrics, "Synchronized lyrics",
                    "Redline reads .lrc files stored next to your audio files (same name, .lrc extension). Open lyrics from the player screen.",
                )
            }
        }
        item {
            SettingsCard("LIBRARY") {
                SettingRow(
                    Icons.Default.LibraryMusic, "${vm.songs.size} songs",
                    "${vm.albums.size} albums \u2022 ${vm.artists.size} artists \u2022 ${vm.favorites.size} favorites",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    Icons.Default.Refresh, "Rescan library", "Scan device storage for new music",
                    onClick = vm::rescanLibrary,
                )
            }
        }
        item { AboutCard() }
    }

    if (showTimer) SleepTimerDialog(vm) { showTimer = false }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val base = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base).padding(vertical = 10.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
private fun AboutCard() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(56.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row {
                Text("RED", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text("LINE MUSIC", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
            Text("Version 1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Made by Natey", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("<3", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Crafted with ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                Text(" for music", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
