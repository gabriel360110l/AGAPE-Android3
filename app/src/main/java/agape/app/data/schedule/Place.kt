package agape.app.data.schedule

import java.util.Locale

/** Where the sun times are computed for: a chosen city, or the phone's rough location. Stored as `lat;lon;label`. */
data class Place(val latitude: Double, val longitude: Double, val label: String) {
    fun encode(): String = String.format(Locale.ROOT, "%.4f;%.4f;%s", latitude, longitude, label.replace(';', ','))

    companion object {
        fun decode(value: String?): Place? {
            val parts = value?.split(';', limit = 3)?.takeIf { it.size == 3 } ?: return null
            val lat = parts[0].toDoubleOrNull()?.takeIf { it in -90.0..90.0 } ?: return null
            val lon = parts[1].toDoubleOrNull()?.takeIf { it in -180.0..180.0 } ?: return null
            return Place(lat, lon, parts[2])
        }

        /** Cities to choose from without sharing a location: the Low Countries and the old patriarchal sees. */
        val cities = listOf(
            Place(52.3676, 4.9041, "Amsterdam"), Place(51.9244, 4.4777, "Rotterdam"), Place(52.0705, 4.3007, "Den Haag"),
            Place(52.0907, 5.1214, "Utrecht"), Place(51.4416, 5.4697, "Eindhoven"), Place(53.2194, 6.5665, "Groningen"),
            Place(50.8514, 5.6910, "Maastricht"), Place(52.2215, 6.8937, "Enschede"), Place(51.2194, 4.4025, "Antwerpen"),
            Place(50.8503, 4.3517, "Brussel"), Place(51.0543, 3.7174, "Gent"), Place(51.5074, -0.1278, "London"),
            Place(41.9028, 12.4964, "Rome"), Place(41.0082, 28.9784, "Istanbul"), Place(31.2001, 29.9187, "Alexandrië"),
            Place(30.0444, 31.2357, "Cairo"), Place(36.2021, 37.1343, "Aleppo"), Place(33.5138, 36.2765, "Damascus"),
            Place(31.7683, 35.2137, "Jeruzalem"), Place(37.9838, 23.7275, "Athene"), Place(55.7558, 37.6173, "Moskou"),
            Place(40.1792, 44.4991, "Jerevan"), Place(9.0300, 38.7400, "Addis Abeba"), Place(15.3229, 38.9251, "Asmara"),
        )
    }
}
