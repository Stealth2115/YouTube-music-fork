package com.agon.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agon.app.data.formatFreq
import com.agon.app.player.EqPresets
import com.agon.app.ui.components.VerticalSlider
import com.agon.app.viewmodel.PlayerViewModel
import java.util.Locale

@Composable
fun EqualizerScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(
                "Equalizer & Effects",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = vm.eqEnabled,
                onCheckedChange = { vm.updateEqEnabled(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        if (!vm.fxAvailable) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "The audio effects engine isn't available on this device. Settings are saved but won't alter sound.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            (EqPresets.names + "Custom").forEach { p ->
                FilterChip(selected = vm.eqPreset == p, onClick = { vm.applyEqPreset(p) }, label = { Text(p) })
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            ) {
                vm.bandLevels.forEachIndexed { i, level ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            String.format(Locale.US, "%+d", level / 100),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (vm.eqEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        VerticalSlider(
                            value = level.toFloat(),
                            onValueChange = { vm.setBand(i, it.toInt()) },
                            valueRange = vm.eqMin.toFloat()..vm.eqMax.toFloat(),
                            enabled = vm.eqEnabled,
                            modifier = Modifier.width(44.dp).height(190.dp),
                        )
                        Text(
                            formatFreq(vm.bandFreqs.getOrElse(i) { 0 }),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                EffectSlider("Bass boost", vm.bassStrength, 1000, "${vm.bassStrength / 10}%", vm.eqEnabled) { vm.setBass(it) }
                EffectSlider("Virtualizer", vm.virtStrength, 1000, "${vm.virtStrength / 10}%", vm.eqEnabled) { vm.setVirtualizer(it) }
                EffectSlider("Loudness", vm.loudnessGain, 2000, "+${vm.loudnessGain / 100} dB", vm.eqEnabled) { vm.setLoudness(it) }
            }
        }

        OutlinedButton(
            onClick = vm::resetFx,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Default.RestartAlt, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Reset all effects")
        }
    }
}

@Composable
private fun EffectSlider(
    title: String,
    value: Int,
    max: Int,
    display: String,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(display, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..max.toFloat(),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}
