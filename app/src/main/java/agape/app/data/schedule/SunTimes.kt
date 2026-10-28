package agape.app.data.schedule

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise, sunset, solar noon and civil twilight for a place and day, computed on the phone (NOAA's solar position
 * equations, accurate to about a minute below the polar circles). Null when the sun does not rise or set that day.
 */
data class SunTimes(
    val dawn: LocalTime?,
    val sunrise: LocalTime?,
    val noon: LocalTime,
    val sunset: LocalTime?,
    val dusk: LocalTime?,
) {
    companion object {
        fun of(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): SunTimes {
            // Julian century at local noon of the date, then the sun's declination and the equation of time.
            val noonUtc = ZonedDateTime.of(date, LocalTime.NOON, zone).toInstant()
            val jd = noonUtc.epochSecond / 86400.0 + 2440587.5
            val t = (jd - 2451545.0) / 36525.0
            val l0 = norm(280.46646 + t * (36000.76983 + t * 0.0003032))
            val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
            val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
            val c = sin(rad(m)) * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin(rad(2 * m)) * (0.019993 - 0.000101 * t) + sin(rad(3 * m)) * 0.000289
            val lambda = l0 + c - 0.00569 - 0.00478 * sin(rad(125.04 - 1934.136 * t))
            val eps0 = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
            val eps = eps0 + 0.00256 * cos(rad(125.04 - 1934.136 * t))
            val decl = asin(sin(rad(eps)) * sin(rad(lambda)))
            val y = tan(rad(eps / 2)) * tan(rad(eps / 2))
            val eqTime = 4 * deg(
                y * sin(2 * rad(l0)) - 2 * e * sin(rad(m)) + 4 * e * y * sin(rad(m)) * cos(2 * rad(l0)) -
                    0.5 * y * y * sin(4 * rad(l0)) - 1.25 * e * e * sin(2 * rad(m)),
            )
            val offsetMin = zone.rules.getOffset(noonUtc).totalSeconds / 60.0
            val noonMin = 720 - 4 * longitude - eqTime + offsetMin

            fun at(altitude: Double, rising: Boolean): LocalTime? {
                val cosH = (sin(rad(altitude)) - sin(rad(latitude)) * sin(decl)) / (cos(rad(latitude)) * cos(decl))
                if (cosH < -1 || cosH > 1) return null
                val h = deg(acos(cosH))
                return time(if (rising) noonMin - 4 * h else noonMin + 4 * h)
            }
            return SunTimes(
                dawn = at(-6.0, true), sunrise = at(-0.833, true), noon = time(noonMin)!!,
                sunset = at(-0.833, false), dusk = at(-6.0, false),
            )
        }

        private fun time(minutes: Double): LocalTime? {
            val m = ((minutes % 1440) + 1440) % 1440
            return LocalTime.of(floor(m / 60).toInt(), floor(m % 60).toInt(), ((m * 60) % 60).toInt())
        }

        private fun rad(d: Double) = d * PI / 180
        private fun deg(r: Double) = r * 180 / PI
        private fun norm(d: Double) = ((d % 360) + 360) % 360
    }
}
