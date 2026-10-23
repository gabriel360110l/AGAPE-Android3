package agape.app.data.content

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** A Home background: a painted interior or a public-domain / CC0 photograph, with its credit. */
@Serializable
data class BackgroundEntry(
    val file: String,
    val title: String,
    val artist: String = "",
    val licence: String,
    val source: String = "",
)

/** The bundled backgrounds in `assets/backgrounds` (see tools/render_backgrounds.py and tools/build_backgrounds.py). */
class Backgrounds(private val context: Context) {
    val all: List<BackgroundEntry> by lazy {
        runCatching {
            context.assets.open("backgrounds/backgrounds.json").bufferedReader().use {
                Json { ignoreUnknownKeys = true }.decodeFromString<List<BackgroundEntry>>(it.readText())
            }
        }.getOrDefault(emptyList())
    }

    /** One background per day, the same all day, cycling through all of them. */
    fun forDate(date: LocalDate): BackgroundEntry? = pick(all, date)

    companion object {
        fun <T> pick(items: List<T>, date: LocalDate): T? =
            if (items.isEmpty()) null else items[Math.floorMod(date.toEpochDay(), items.size.toLong()).toInt()]
    }
}
