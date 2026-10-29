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

    /** A reading plan's cover: its own picture, else one chosen by its id. Only public domain and CC0 pictures. */
    fun forPlan(planId: String): BackgroundEntry? {
        val free = all.filter { it.licence == "CC0" || it.licence.equals("Public domain", ignoreCase = true) }
        if (free.isEmpty()) return null
        return free.firstOrNull { it.file == PLAN_COVERS[planId] } ?: free[Math.floorMod(planId.hashCode(), free.size)]
    }

    companion object {
        /** Each plan's picture, chosen to fit what it reads. */
        val PLAN_COVERS = mapOf(
            "mark" to "backgrounds/art_candles.jpg",
            "gospels-acts" to "backgrounds/photo_chora_church_constantinople_6.jpg",
            "nt-90" to "backgrounds/photo_byzantine_mosaics_years_817_824_santa_pr.jpg",
            "redemptive" to "backgrounds/art_sanctuary.jpg",
            "bible-1y" to "backgrounds/photo_russian_orthodox_iconostasis_deventer.jpg",
            "bible-2y" to "backgrounds/photo_st_marys_cathedral_nave_edinburgh.jpg",
            "chronological" to "backgrounds/art_dome.jpg",
            "councils" to "backgrounds/photo_gaspare_fossati_louis_haghe_vue_g_n_rale.jpg",
        )

        fun <T> pick(items: List<T>, date: LocalDate): T? =
            if (items.isEmpty()) null else items[Math.floorMod(date.toEpochDay(), items.size.toLong()).toInt()]
    }
}
