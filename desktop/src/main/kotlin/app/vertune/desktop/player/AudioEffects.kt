package app.vertune.desktop.player

import app.vertune.desktop.data.Settings
import app.vitune.providers.sponsorblock.SponsorBlock
import app.vitune.providers.sponsorblock.models.Action
import app.vitune.providers.sponsorblock.requests.segments
import java.util.Locale

/**
 * The phone's audio settings (skip silence, loudness normalization, bass boost, reverb, speed
 * and pitch), done with ffmpeg audio filters while decoding.
 */
object AudioEffects {
    fun speed(): Float = Settings.speed.value.coerceIn(MIN_RATE, MAX_RATE)

    fun filter(): String? {
        val parts = buildList {
            if (Settings.skipSilence.value) {
                val seconds = (Settings.minimumSilenceMs.value.coerceAtLeast(1) / 1000.0)
                add("silenceremove=stop_periods=-1:stop_duration=${fmt(seconds)}:stop_threshold=-50dB")
            }
            if (Settings.loudnessNormalization.value) {
                add("loudnorm=I=${fmt((TARGET_LOUDNESS + Settings.loudnessBaseGain.value).toDouble().coerceIn(-70.0, -5.0))}:TP=-1.5:LRA=11")
            }
            if (Settings.bassBoost.value) {
                add("bass=g=${fmt(Settings.bassBoostAmount.value * MAX_BASS_DB.toDouble())}")
            }
            reverb(Settings.reverb.value)?.let(::add)
            val pitch = Settings.pitch.value.coerceIn(MIN_RATE, MAX_RATE)
            val speed = speed()
            if (pitch != 1f) {
                add("asetrate=${(SAMPLE_RATE * pitch).toInt()}")
                add("aresample=$SAMPLE_RATE")
            }
            val tempo = speed / pitch
            if (tempo != 1f) addAll(atempo(tempo))
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(",")
    }

    /** atempo only accepts 0.5–2 in older ffmpeg versions, so big changes are chained. */
    private fun atempo(value: Float): List<String> {
        var v = value.toDouble()
        val out = mutableListOf<String>()
        while (v > 2.0) { out += "atempo=2.0"; v /= 2.0 }
        while (v < 0.5) { out += "atempo=0.5"; v /= 0.5 }
        out += "atempo=${fmt(v)}"
        return out
    }

    private fun reverb(r: Settings.Reverb) = when (r) {
        Settings.Reverb.None -> null
        Settings.Reverb.SmallRoom -> "aecho=0.8:0.88:20:0.25"
        Settings.Reverb.MediumRoom -> "aecho=0.8:0.88:40|60:0.3|0.2"
        Settings.Reverb.LargeRoom -> "aecho=0.8:0.9:60|90:0.35|0.25"
        Settings.Reverb.MediumHall -> "aecho=0.8:0.9:80|120:0.35|0.25"
        Settings.Reverb.LargeHall -> "aecho=0.8:0.9:120|200:0.4|0.3"
        Settings.Reverb.Plate -> "aecho=0.8:0.85:15|30|45:0.3|0.25|0.2"
    }

    private fun fmt(d: Double) = String.format(Locale.ROOT, "%.3f", d)

    private const val SAMPLE_RATE = 44_100
    private const val TARGET_LOUDNESS = -19f
    private const val MAX_BASS_DB = 20f
    const val MIN_RATE = 0.25f
    const val MAX_RATE = 4f
}

/** SponsorBlock segments for a song, in milliseconds (the phone's "SponsorBlock" setting). */
object SponsorSegments {
    private val cache = HashMap<String, List<LongRange>>()

    suspend fun get(id: String): List<LongRange> {
        synchronized(cache) { cache[id]?.let { return it } }
        val segments = SponsorBlock.segments(id)?.getOrNull().orEmpty()
            .filter { it.action == Action.Skip && it.end > it.start }
            .map { it.start.inWholeMilliseconds..it.end.inWholeMilliseconds }
        synchronized(cache) { cache[id] = segments }
        return segments
    }
}
