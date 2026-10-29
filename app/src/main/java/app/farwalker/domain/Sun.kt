package app.farwalker.domain

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/** Sunrise and sunset in minutes after local midnight; null on polar day or night. */
data class SunTimes(val rise: Int, val set: Int)

object Sun {
    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)

    /** The NOAA approximation, good to a couple of minutes. */
    fun times(date: LocalDate, lat: Double, lon: Double, zone: ZoneId = ZoneId.systemDefault()): SunTimes? {
        val n = date.dayOfYear
        val g = 2 * Math.PI / 365 * (n - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) -
            0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val zenith = rad(90.833)
        val x = cos(zenith) / (cos(rad(lat)) * cos(decl)) - tan(rad(lat)) * tan(decl)
        if (x > 1 || x < -1) return null
        val ha = deg(acos(x))
        val offset = zone.rules.getOffset(date.atStartOfDay(zone).toInstant()).totalSeconds / 60.0
        val noon = 720 - 4 * lon - eqTime + offset
        return SunTimes((noon - ha * 4).roundToInt(), (noon + ha * 4).roundToInt())
    }
}
