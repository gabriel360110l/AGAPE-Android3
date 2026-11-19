package agape.app

import agape.app.data.rosary.PrayerRope
import agape.app.data.rosary.RosaryPrayer
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The English prayers per tradition, from tools/source/common_prayers_en.json: the Catholic texts in the approved
 * English (USCCB; the Roman Missal, ICEL 2010; the Liturgy of the Hours), the Byzantine ones in the English of the
 * Orthodox Church in America, the Coptic ones from the Agpeya (copticchurch.net).
 */
class EnglishPrayerTextTest {
    private val content = File("src/main/assets/content")
    private val common: Map<String, JsonObject> =
        Json.parseToJsonElement(File("../tools/source/common_prayers_en.json").readText())
            .jsonObject.getValue("prayers").jsonArray
            .associate { it.jsonObject.getValue("id").jsonPrimitive.content to it.jsonObject }

    private fun text(id: String) = common.getValue(id).getValue("text").jsonPrimitive.content
    private fun lines(id: String) = common.getValue(id).getValue("lines").jsonArray.map { it.jsonPrimitive.content }

    private val extras: Map<String, JsonObject> =
        Json.parseToJsonElement(File(content, "prayers_extra.json").readText()).jsonArray
            .associate { it.jsonObject.getValue("id").jsonPrimitive.content to it.jsonObject }

    private fun english(id: String) =
        extras.getValue(id).getValue("body").jsonObject.getValue("en").jsonArray.joinToString(" ") { it.jsonPrimitive.content }

    private fun traditions(id: String) = extras.getValue(id).getValue("traditions").jsonArray.map { it.jsonPrimitive.content }

    /** What one tradition sees in the prayer list, in English. */
    private fun visibleTo(tradition: String) =
        extras.keys.filter { tradition in traditions(it) }.joinToString("\n") { english(it) }

    private val filesOf = mapOf(
        "EASTERN_ORTHODOX" to listOf("prayers_eo.json", "prayers_extra.json"),
        "ORIENTAL_ORTHODOX" to listOf("prayers_oo.json", "prayers_extra.json"),
        "CATHOLIC" to listOf("prayers_ca.json", "prayers_extra.json"),
    )

    @Test fun everyTextHasItsSource() {
        for ((id, p) in common) {
            assertTrue("$id has no source", p["source"]?.jsonPrimitive?.content.orEmpty().isNotBlank())
            p["lines"]?.let { assertEquals("$id: text and lines differ", lines(id).joinToString(" "), text(id)) }
        }
    }

    @Test fun noOldVariantIsLeft() {
        for ((id, p) in common) {
            val old = p["variants"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty() +
                p["fragments"]?.jsonArray?.map { it.jsonArray[0].jsonPrimitive.content }.orEmpty() +
                // Old lines are only replaced where they are a whole string.
                p["variant_lines"]?.jsonArray.orEmpty().flatMap { v ->
                    v.jsonArray.map { it.jsonPrimitive.content }.filter { it !in lines(id) }.map { "\"$it\"" }
                }
            for (name in filesOf.getValue(p.getValue("tradition").jsonPrimitive.content)) {
                val file = File(content, name).readText()
                for (o in old) assertTrue("$name still has an old English form of $id: $o", !file.contains(o))
            }
        }
    }

    @Test fun rosaryUsesTheCommonTexts() {
        assertEquals(text("ca-our-father"), RosaryPrayer.OUR_FATHER.text.en)
        assertEquals(text("ca-hail-mary"), RosaryPrayer.HAIL_MARY.text.en)
        assertEquals(text("ca-glory-be"), RosaryPrayer.GLORY_BE.text.en)
        assertEquals(text("ca-fatima"), RosaryPrayer.FATIMA.text.en)
        assertEquals(text("ca-apostles-creed"), RosaryPrayer.APOSTLES_CREED.text.en)
        assertEquals(text("jesus-prayer"), PrayerRope.CHOTKI.prayer.en)
    }

    @Test fun prayerListUsesTheCommonTexts() {
        assertEquals(text("ca-our-father"), english("our-father-ca"))
        assertEquals(text("ca-hail-mary"), english("hail-mary"))
        assertEquals(text("ca-glory-be"), english("glory-be"))
        assertEquals(text("ca-sub-tuum"), english("sub-tuum-ca"))
        assertEquals(text("oo-our-father"), english("our-father-oo"))
        assertEquals(text("eo-our-father"), english("our-father"))
        assertTrue(english("jesus-prayer").startsWith(text("jesus-prayer")))
        // The Our Father inside the Byzantine prayers is the same OCA text.
        for (id in listOf("trisagion", "morning-prayer", "evening-prayer", "usual-beginning")) {
            assertTrue(id, english(id).contains(text("eo-our-father-said")))
        }
    }

    /** Each tradition sees exactly one Creed and one Our Father in English, its own, and not another's. */
    @Test fun eachTraditionHasItsOwnEnglishCreedAndOurFather() {
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
                assertEquals(id, listOf(tradition), traditions(id))
                assertEquals("$id is not its source text", source, english(id))
            }
            val seen = visibleTo(tradition)
            for ((other, theirs) in own) if (other != tradition) for ((id, source) in theirs) {
                assertTrue("$tradition sees the English text of $id", !seen.contains(source))
            }
        }
        // Telling marks of each: the Catholic "consubstantial" and Filioque, the Byzantine "of one essence" without
        // it, the Coptic plural and "Pantocrator".
        assertTrue(text("ca-creed").contains("consubstantial with the Father") && text("ca-creed").contains("from the Father and the Son"))
        assertTrue(!text("eo-creed").contains("from the Father and the Son") && text("eo-creed").contains("of one essence with the Father"))
        assertTrue(text("oo-creed").startsWith("We believe in one God, God the Father, the Pantocrator"))
        assertTrue(!visibleTo("EASTERN_ORTHODOX").contains("consubstantial"))
        assertTrue(!visibleTo("ORIENTAL_ORTHODOX").contains("consubstantial"))
    }

    /** The hours say the same English Creed and Our Father as the prayer list of their tradition. */
    @Test fun hoursSayTheirOwnEnglishCreedAndOurFather() {
        val agpeya = File(content, "prayers_oo.json").readText()
        for (line in lines("oo-creed-intro") + lines("oo-creed")) assertTrue("Agpeya lacks: $line", agpeya.contains(line))
        assertTrue(agpeya.contains(text("oo-our-father")))
        val byzantine = File(content, "prayers_eo.json").readText()
        for (line in lines("eo-creed")) assertTrue("Horologion lacks: $line", byzantine.contains(line))
        assertTrue(byzantine.contains(text("eo-our-father-said")))
        for (line in lines("ca-creed") + lines("oo-creed")) assertTrue("Horologion has: $line", !byzantine.contains(line))
        for (line in lines("ca-creed") + lines("eo-creed")) assertTrue("Agpeya has: $line", !agpeya.contains(line))
        assertTrue(!agpeya.contains(text("eo-our-father-said")) && !byzantine.contains(text("oo-our-father")))
    }
}
