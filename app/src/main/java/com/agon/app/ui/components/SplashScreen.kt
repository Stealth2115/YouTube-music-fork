package com.agon.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animated brand splash shown over the app for a moment after launch: the logo springs in,
 * the wordmark fades up and animated equalizer bars start moving, then the caller fades the
 * whole overlay out into the main UI.
 */
@Composable
fun AnimatedSplash(onFinished: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    val scale = remember { Animatable(0.4f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val barsAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            )
        }
        launch { logoAlpha.animateTo(1f, tween(450)) }
        launch {
            delay(200)
            textAlpha.animateTo(1f, tween(450))
        }
        launch {
            delay(420)
            barsAlpha.animateTo(1f, tween(350))
        }
        delay(1650)
        onFinished()
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(104.dp)
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value; alpha = logoAlpha.value }
                    .shadow(24.dp, RoundedCornerShape(30.dp), ambientColor = accent, spotColor = accent)
                    .clip(RoundedCornerShape(30.dp))
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(56.dp))
            }
            Spacer(Modifier.height(26.dp))
            Row(Modifier.graphicsLayer { alpha = textAlpha.value }) {
                Text(
                    "RED",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = accent,
                    letterSpacing = 2.sp,
                )
                Text(
                    "LINE",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = onSurface,
                    letterSpacing = 2.sp,
                )
            }
            Text(
                "MUSIC",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 8.sp,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value },
            )
            Spacer(Modifier.height(30.dp))
            MusicVisualizer(
                "Bars",
                playing = true,
                color = accent,
                modifier = Modifier
                    .graphicsLayer { alpha = barsAlpha.value }
                    .width(160.dp)
                    .height(30.dp),
            )
        }
    }
}
