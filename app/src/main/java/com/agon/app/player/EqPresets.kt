package com.agon.app.player

object EqPresets {

    val names = listOf(
        "Flat", "Bass Boost", "Treble Boost", "Vocal",
        "Rock", "Pop", "Electronic", "Classical"
    )

    // Normalized 5-point curves in [-1, 1]; interpolated onto the device's band count.
    private val curves = mapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
        "Bass Boost" to listOf(0.7f, 0.5f, 0.1f, 0f, 0f),
        "Treble Boost" to listOf(0f, 0f, 0.1f, 0.5f, 0.7f),
        "Vocal" to listOf(-0.2f, 0.15f, 0.5f, 0.35f, -0.1f),
        "Rock" to listOf(0.5f, 0.25f, -0.15f, 0.25f, 0.5f),
        "Pop" to listOf(-0.1f, 0.25f, 0.5f, 0.25f, -0.1f),
        "Electronic" to listOf(0.5f, 0.15f, 0f, 0.35f, 0.6f),
        "Classical" to listOf(0.35f, 0.25f, -0.1f, 0.25f, 0.4f),
    )

    fun curve(name: String, bandCount: Int, minMb: Int, maxMb: Int): List<Int> {
        if (bandCount <= 0) return emptyList()
        val c = curves[name] ?: return List(bandCount) { 0 }
        return List(bandCount) { i ->
            val pos = if (bandCount == 1) 0f else i.toFloat() / (bandCount - 1) * (c.size - 1)
            val lo = pos.toInt().coerceIn(0, c.size - 1)
            val hi = (lo + 1).coerceAtMost(c.size - 1)
            val t = pos - lo
            val v = c[lo] * (1 - t) + c[hi] * t
            val mb = if (v >= 0) v * maxMb else v * (-minMb)
            mb.toInt()
        }
    }
}
