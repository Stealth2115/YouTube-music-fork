package com.agon.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

val VisualizerStyles = listOf("Bars", "Wave", "Mirror")

@Composable
fun MusicVisualizer(style: String, playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val count = 42
    val anims = remember { List(count) { Animatable(0.12f) } }
    LaunchedEffect(playing) {
        anims.forEach { anim ->
            launch {
                if (playing) {
                    while (isActive) {
                        anim.animateTo(
                            targetValue = Random.nextFloat() * 0.8f + 0.15f,
                            animationSpec = tween(Random.nextInt(150, 400), easing = LinearEasing),
                        )
                    }
                } else {
                    anim.animateTo(0.1f, tween(500))
                }
            }
        }
    }
    Canvas(modifier) {
        val values = anims.map { it.value }
        val n = values.size
        if (n == 0 || size.width <= 0f) return@Canvas
        val slot = size.width / n
        val barW = slot * 0.55f
        when (style) {
            "Wave" -> {
                val path = Path()
                values.forEachIndexed { i, v ->
                    val x = slot * i + slot / 2
                    val sign = if (i % 2 == 0) 1f else -1f
                    val y = size.height / 2 + (v - 0.5f) * size.height * 0.8f * sign
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color.copy(alpha = 0.9f), style = Stroke(width = 4f, cap = StrokeCap.Round))
            }
            "Mirror" -> {
                values.forEachIndexed { i, v ->
                    val h = (v * size.height / 2).coerceAtLeast(2f)
                    val x = slot * i + (slot - barW) / 2
                    drawRoundRect(
                        color.copy(alpha = 0.9f),
                        topLeft = Offset(x, size.height / 2 - h),
                        size = Size(barW, h * 2),
                        cornerRadius = CornerRadius(barW / 2),
                    )
                }
            }
            else -> {
                values.forEachIndexed { i, v ->
                    val h = (v * size.height).coerceAtLeast(3f)
                    val x = slot * i + (slot - barW) / 2
                    drawRoundRect(
                        color.copy(alpha = 0.9f),
                        topLeft = Offset(x, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2),
                    )
                }
            }
        }
    }
}
