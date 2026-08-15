package com.agon.app.player

import kotlin.math.abs

/**
 * Curated speed + pitch combinations that are applied together with one tap.
 *
 * Values follow the conventions of the nightcore / slowed-and-reverbed remix
 * communities: "nightcore" is sped up and pitched up, while "deep", "daycore"
 * and "vaporwave" are slowed and pitched down. Pitch and tempo stay independent
 * under the hood (the app resamples them separately), so these presets just set
 * both knobs at once.
 */
object TempoPresets {

    data class Preset(
        val name: String,
        val speed: Float,
        val pitchSemitones: Float,
    )

    val presets: List<Preset> = listOf(
        Preset("Normal", 1.0f, 0f),
        Preset("Nightcore", 1.25f, 2f),
        Preset("Deep", 0.85f, -2f),
        Preset("Daycore", 0.8f, -3f),
        Preset("Vaporwave", 0.85f, -4f),
        Preset("Slowed", 0.7f, -1f),
        Preset("Chipmunk", 1.25f, 7f),
        Preset("Fast", 1.5f, 0f),
    )

    fun byName(name: String): Preset? = presets.firstOrNull { it.name == name }

    /** Best matching preset name for the current speed/pitch, or "Custom". */
    fun detect(speed: Float, pitch: Float): String =
        presets.firstOrNull {
            abs(it.speed - speed) < 0.001f && abs(it.pitchSemitones - pitch) < 0.001f
        }?.name ?: "Custom"
}
