package com.agon.app.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer

/**
 * Wraps the platform audio effects attached to the player's audio session.
 * Every call is defensive: some devices/emulators don't support all effects.
 */
class AudioFxManager {

    private var equalizer: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null
    private var sessionId: Int = -1

    var bands: Int = 0; private set
    var minLevel: Int = -1500; private set
    var maxLevel: Int = 1500; private set
    var centerFreqs: List<Int> = emptyList(); private set // Hz

    fun attach(newSessionId: Int): Boolean {
        if (newSessionId == sessionId && equalizer != null) return true
        release()
        return try {
            val eq = Equalizer(0, newSessionId)
            equalizer = eq
            sessionId = newSessionId
            bands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            minLevel = range[0].toInt()
            maxLevel = range[1].toInt()
            centerFreqs = (0 until bands).map { (eq.getCenterFreq(it.toShort()) / 1000) }
            bass = runCatching { BassBoost(0, newSessionId) }.getOrNull()
            virt = runCatching { Virtualizer(0, newSessionId) }.getOrNull()
            loud = runCatching { LoudnessEnhancer(newSessionId) }.getOrNull()
            true
        } catch (t: Throwable) {
            release()
            false
        }
    }

    fun setEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
        runCatching { bass?.enabled = enabled }
        runCatching { virt?.enabled = enabled }
        runCatching { loud?.enabled = enabled }
    }

    fun setBand(index: Int, millibels: Int) {
        runCatching {
            equalizer?.setBandLevel(index.toShort(), millibels.coerceIn(minLevel, maxLevel).toShort())
        }
    }

    fun setBass(strength: Int) {
        runCatching { bass?.setStrength(strength.coerceIn(0, 1000).toShort()) }
    }

    fun setVirtualizer(strength: Int) {
        runCatching { virt?.setStrength(strength.coerceIn(0, 1000).toShort()) }
    }

    fun setLoudness(gainMb: Int) {
        runCatching { loud?.setTargetGain(gainMb.coerceIn(0, 2000)) }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bass?.release() }
        runCatching { virt?.release() }
        runCatching { loud?.release() }
        equalizer = null; bass = null; virt = null; loud = null
        sessionId = -1
        bands = 0
    }
}
