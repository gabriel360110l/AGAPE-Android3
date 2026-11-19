package agape.app

import agape.app.data.rosary.PrayerRope
import agape.app.data.rosary.RosaryPrayer
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared prayers say the same words everywhere: tools/source/common_prayers_nl.json is the one source. */
class PrayerTextTest {
    private val content = File("src/main/assets/content")
    private val common: Map<String, JsonObject> =
        Json.parseToJsonElement(File("../tools/source/common_prayers_nl.json").readText())
            .jsonObject.getValue("prayers").jsonArray
            .associate { it.jsonObject.getValue("id").jsonPrimitive.content to it.jsonObject }

    private fun text(id: String) = common.getValue(id).getValue("text").jsonPrimitive.content

    private val extras: Map<String, JsonObject> =
        Json.parseToJsonElement(File(content, "prayers_extra.json").readText()).jsonArray
            .associate { it.jsonObject.getValue("id").jsonPrimitive.content to it.jsonObject }

    private fun dutch(id: String) =
        extras.getValue(id).getValue("body").jsonObject.getValue("nl").jsonArray.joinToString(" ") { it.jsonPrimitive.content }

    private val filesOf = mapOf(
        "EASTERN_ORTHODOX" to listOf("prayers_eo.json", "prayers_extra.json"),
        "ORIENTAL_ORTHODOX" to listOf("prayers_oo.json"),
        "CATHOLIC" to listOf("prayers_ca.json", "prayers_extra.json"),
    )

    private fun lines(id: String) = common.getValue(id).getValue("lines").jsonArray.map { it.jsonPrimitive.content }

    /** Older lines of a prayer kept line by line, without the lines that did not change. */
    private fun oldLines(p: JsonObject): List<String> {
        val now = p["lines"]?.jsonArray?.map { it.jsonPrimitive.content } ?: return emptyList()
        return p["variant_lines"]?.jsonArray.orEmpty().flatMap { old ->
            old.jsonArray.map { it.jsonPrimitive.content }.filterIndexed { i, o -> o != now.getOrNull(i) }
        }
    }

    private fun traditions(id: String) = extras.getValue(id).getValue("traditions").jsonArray.map { it.jsonPrimitive.content }

    /** What one tradition sees in the prayer list, as one text. */
    private fun visibleTo(tradition: String) =
        extras.keys.filter { tradition in traditions(it) }.joinToString("\n") { dutch(it) }

    @Test fun noOldVariantIsLeft() {
        for ((id, p) in common) {
            val old = (p["variants"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()) +
                (p["fragments"]?.jsonArray?.map { it.jsonArray[0].jsonPrimitive.content } ?: emptyList()) +
                oldLines(p)
            for (name in filesOf.getValue(p.getValue("tradition").jsonPrimitive.content)) {
                val file = File(content, name).readText()
                for (o in old) assertTrue("$name still has an old form of $id: $o", !file.contains(o))
            }
        }
    }

    @Test fun rosaryUsesTheCommonTexts() {
        assertEquals(text("ca-our-father"), RosaryPrayer.OUR_FATHER.text.nl)
        assertEquals(text("ca-hail-mary"), RosaryPrayer.HAIL_MARY.text.nl)
        assertEquals(text("ca-glory-be"), RosaryPrayer.GLORY_BE.text.nl)
        assertEquals(text("ca-fatima"), RosaryPrayer.FATIMA.text.nl)
        assertTrue(RosaryPrayer.HAIL_HOLY_QUEEN.text.nl.startsWith(text("ca-salve-regina")))
        assertEquals(text("jesus-prayer"), PrayerRope.CHOTKI.prayer.nl)
    }

    @Test fun prayerListUsesTheCommonTexts() {
        assertEquals(text("ca-our-father"), dutch("our-father-ca"))
        assertEquals(text("oo-our-father"), dutch("our-father-oo"))
        assertTrue(dutch("our-father").startsWith(text("eo-our-father")))
        assertEquals(text("ca-hail-mary"), dutch("hail-mary"))
        assertEquals(text("ca-glory-be"), dutch("glory-be"))
        assertTrue(dutch("jesus-prayer").startsWith(text("jesus-prayer")))
    }

    /** Each tradition's hours only pull in prayers of that tradition, so the Catholic hours get the Catholic Our Father. */
    @Test fun hoursOnlyUsePrayersOfTheirTradition() {
        for ((tradition, file) in listOf("CATHOLIC" to "prayers_ca.json", "EASTERN_ORTHODOX" to "prayers_eo.json", "ORIENTAL_ORTHODOX" to "prayers_oo.json")) {
            val ids = mutableSetOf<String>()
            fun walk(e: JsonElement) {
                when (e) {
                    is JsonObject -> {
                        (e["prayerId"] as? JsonPrimitive)?.let { ids += it.content }
                        e.values.forEach(::walk)
                    }
                    is JsonArray -> e.forEach(::walk)
                    else -> Unit
                }
            }
            walk(Json.parseToJsonElement(File(content, file).readText()))
            for (id in ids) {
                val traditions = extras[id]?.getValue("traditions")?.jsonArray?.map { it.jsonPrimitive.content }
                assertTrue("$file uses $id, which is not a $tradition prayer", traditions != null && tradition in traditions)
            }
        }
    }

    /**
     * Each tradition gets one Creed and one Our Father in the prayer list, in the words of its own source, and never
     * the text of another tradition: the Catholic Missal, the Byzantine liturgy (orthodoxdomein.nl), and the Coptic
     * Church in Utrecht, whose Creed is said in the plural after "Wij verheffen u".
     */
    @Test fun eachTraditionHasItsOwnCreedAndOurFather() {
        val own = mapOf(
            "CATHOLIC" to listOf("creed-catholic" to text("ca-creed"), "our-father-ca" to text("ca-our-father")),
            "EASTERN_ORTHODOX" to listOf("creed-nicene" to text("eo-creed"), "our-father" to text("eo-our-father")),
            "ORIENTAL_ORTHODOX" to listOf(
                "creed-oo" to text("oo-creed-intro") + " " + text("oo-creed"),
                "our-father-oo" to text("oo-our-father"),
            ),
        )
        for ((tradition, prayers) in own) {
            val creeds = extras.keys.filter { it.startsWith("creed") && tradition in traditions(it) }
            val fathers = extras.keys.filter { it.startsWith("our-father") && tradition in traditions(it) }
            assertEquals("$tradition creeds", listOf(prayers[0].first), creeds)
            assertEquals("$tradition Our Fathers", listOf(prayers[1].first), fathers)
            for ((id, source) in prayers) {
                assertEquals("$id", listOf(tradition), traditions(id))
                assertTrue("$id is not its source text", dutch(id) == source || dutch(id).startsWith("$source "))
            }
            val seen = visibleTo(tradition)
            for ((other, theirs) in own) if (other != tradition) for ((id, source) in theirs) {
                assertTrue("$tradition sees the text of $id", !seen.contains(source))
            }
        }
        assertTrue(text("oo-creed").startsWith("In waarheid geloven wij"))
        assertTrue(lines("oo-creed").drop(1).all { it.startsWith("Wij ") || it.startsWith("Ja, wij ") || it.startsWith("Hij ") })
    }

    /** The Agpeya hours say the Coptic Creed and its introduction, in the same words as the prayer list. */
    @Test fun agpeyaSaysTheCopticCreed() {
        val agpeya = File(content, "prayers_oo.json").readText()
        for (line in lines("oo-creed-intro") + lines("oo-creed")) assertTrue("Agpeya lacks: $line", agpeya.contains(line))
        for (line in lines("eo-creed") + lines("ca-creed")) assertTrue("Agpeya has: $line", !agpeya.contains(line))
        val catholic = File(content, "prayers_ca.json").readText() + File(content, "prayers_eo.json").readText()
        for (line in lines("oo-creed")) assertTrue("Not Coptic hours have: $line", !catholic.contains(line))
    }

    /**
     * The Sub tuum and the prayers around Scripture: Catholics get the Catholic words (the Nationale Raad voor
     * Liturgie's Sub tuum, "Moeder Gods", the Dutch sign of the cross) and nothing Byzantine; the Oriental Orthodox get
     * the Sub tuum without the Byzantine hymns; the Byzantine hymns stay with the Eastern Orthodox.
     */
    @Test fun eachTraditionPraysTheSubTuumInItsOwnWords() {
        assertEquals(listOf("CATHOLIC"), traditions("sub-tuum-ca"))
        assertEquals(text("ca-sub-tuum"), dutch("sub-tuum-ca"))
        assertEquals(listOf("ORIENTAL_ORTHODOX"), traditions("sub-tuum-oo"))
        assertEquals(listOf("EASTERN_ORTHODOX"), traditions("sub-tuum"))
        assertTrue(dutch("sub-tuum").startsWith("Het is waarlijk waardig"))
        for (byzantine in listOf("Het is waarlijk waardig", "Eerwaardiger dan de cherubs", "Maagd Theotokos, verheug u")) {
            assertTrue(!visibleTo("CATHOLIC").contains(byzantine))
            assertTrue(!visibleTo("ORIENTAL_ORTHODOX").contains(byzantine))
        }
        assertTrue(dutch("sub-tuum-oo").startsWith("Onder uw hoede nemen wij onze toevlucht"))

        val catholic = visibleTo("CATHOLIC")
        assertTrue("Catholics see Theotokos", !catholic.contains("Theotokos"))
        assertTrue(!catholic.contains("In de Naam van de Vader"))
        for (id in listOf("before-scripture-ca", "after-scripture-ca")) {
            assertEquals(listOf("CATHOLIC"), traditions(id))
            assertTrue(dutch(id).contains("Moeder Gods"))
        }
        assertTrue(dutch("before-scripture-ca").startsWith(text("ca-sign-of-the-cross")))
        for (id in listOf("before-scripture", "after-scripture")) {
            assertEquals(listOf("EASTERN_ORTHODOX", "ORIENTAL_ORTHODOX"), traditions(id))
            assertTrue(dutch(id).contains("Theotokos"))
        }
    }

    /** The Agpeya prays the Trisagion with its lines on Christ, in every hour that has it, the Veil included. */
    @Test fun agpeyaPraysTheCopticTrisagion() {
        val hours = Json.parseToJsonElement(File(content, "prayers_oo.json").readText()).jsonObject.getValue("hours").jsonArray
        var found = 0
        for (hour in hours) for (section in hour.jsonObject.getValue("sections").jsonArray) {
            val nl = section.jsonObject["body"]?.jsonObject?.get("nl")?.jsonArray?.map { it.jsonPrimitive.content } ?: continue
            val trisagion = nl.filter { it.startsWith("Heilige God, heilige Sterke", ignoreCase = true) }
            if (trisagion.isEmpty()) continue
            found++
            val id = hour.jsonObject.getValue("id").jsonPrimitive.content
            assertTrue("$id has the Byzantine Trisagion", trisagion.none { it.contains("Onsterfelijke, ontferm") })
            assertTrue("$id lacks the Virgin", trisagion.any { it.contains("uit de Maagd geboren") })
        }
        assertTrue(found >= 3)
    }
}
