package com.agon.app.player

import kotlin.math.abs

/**
 * Curated speed + pitch combinations that are applied together with one tap.
 *
 * Pitch is matched to speed so most presets behave like a natural speed change
 * (like speeding up / slowing down a record): pitch in semitones = 12 * log2(speed).
 * That keeps the resampling clean instead of producing chipmunk/underwater
 * time-stretch artifacts. A few presets deliberately break the rule for effect.
 */
object TempoPresets {

    data class Preset(
        val name: String,
        val speed: Float,
        val pitchSemitones: Float,
    )

    val presets: List<Preset> = listOf(
        Preset("Normal", 1.0f, 0f),
        // Natural: 12*log2(1.25) ≈ +3.9 st
        Preset("Nightcore", 1.25f, 4f),
        // Natural: 12*log2(0.85) ≈ -2.8 st
        Preset("Deep", 0.85f, -3f),
        // Natural: 12*log2(0.80) ≈ -3.9 st
        Preset("Daycore", 0.80f, -4f),
        // Slowed with an extra pitch-down for the dreamy vaporwave wash.
        Preset("Vaporwave", 0.80f, -5f),
        // Natural: 12*log2(0.70) ≈ -6.2 st
        Preset("Slowed", 0.70f, -6f),
        // Comedy effect: a full octave up.
        Preset("Chipmunk", 1.25f, 12f),
        // Tempo-only: faster without changing the key.
        Preset("Fast", 1.5f, 0f),
    )

    fun byName(name: String): Preset? = presets.firstOrNull { it.name == name }

    /** Best matching preset name for the current speed/pitch, or "Custom". */
    fun detect(speed: Float, pitch: Float): String =
        presets.firstOrNull {
            abs(it.speed - speed) < 0.001f && abs(it.pitchSemitones - pitch) < 0.001f
        }?.name ?: "Custom"
}
