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
    // Hoisted out of the draw phase: these were re-allocated on every single frame
    // (a list of 42 floats, a Path and one Color per bar) which churned the heap at 60fps.
    val drawColor = remember(color) { color.copy(alpha = 0.9f) }
    val wavePath = remember { Path() }
    val stroke = remember { Stroke(width = 4f, cap = StrokeCap.Round) }

    Canvas(modifier) {
        val n = anims.size
        if (n == 0 || size.width <= 0f) return@Canvas
        val slot = size.width / n
        val barW = slot * 0.55f
        val halfBarW = barW / 2
        val corner = CornerRadius(halfBarW)
        val height = size.height
        when (style) {
            "Wave" -> {
                wavePath.reset()
                val halfHeight = height / 2
                val amplitude = height * 0.8f
                for (i in 0 until n) {
                    val v = anims[i].value
                    val x = slot * i + slot / 2
                    val sign = if (i % 2 == 0) 1f else -1f
                    val y = halfHeight + (v - 0.5f) * amplitude * sign
                    if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                }
                drawPath(wavePath, drawColor, style = stroke)
            }
            "Mirror" -> {
                val halfHeight = height / 2
                for (i in 0 until n) {
                    val h = (anims[i].value * halfHeight).coerceAtLeast(2f)
                    val x = slot * i + (slot - barW) / 2
                    drawRoundRect(
                        drawColor,
                        topLeft = Offset(x, halfHeight - h),
                        size = Size(barW, h * 2),
                        cornerRadius = corner,
                    )
                }
            }
            else -> {
                for (i in 0 until n) {
                    val h = (anims[i].value * height).coerceAtLeast(3f)
                    val x = slot * i + (slot - barW) / 2
                    drawRoundRect(
                        drawColor,
                        topLeft = Offset(x, height - h),
                        size = Size(barW, h),
                        cornerRadius = corner,
                    )
                }
            }
        }
    }
}
