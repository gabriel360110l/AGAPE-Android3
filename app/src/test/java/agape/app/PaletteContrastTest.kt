package agape.app

import agape.app.core.model.VisualStyle
import agape.app.ui.theme.AgapeColors
import agape.app.ui.theme.Palettes
import agape.app.ui.theme.trueBlack
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** WCAG AA for every style, light and dark, and dark on real black: 4.5:1 for text, 3:1 for icons and ornaments. */
class PaletteContrastTest {
    private fun channel(v: Float) = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    private fun lum(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    private fun ratio(a: Color, b: Color): Double {
        val (x, y) = lum(a) to lum(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    private fun palettes(): List<Pair<String, AgapeColors>> = VisualStyle.entries.flatMap { s ->
        listOf(
            "$s light" to Palettes.of(s, false),
            "$s dark" to Palettes.of(s, true),
            "$s dark, real black" to Palettes.of(s, true).trueBlack(),
        )
    }

    private fun check(name: String, role: String, fg: Color, bg: Color, min: Double, failures: MutableList<String>) {
        val r = ratio(fg, bg)
        if (r < min) failures += "$name: $role %.2f < %.1f".format(r, min)
    }

    @Test
    fun everyStyleMeetsAa() {
        val failures = mutableListOf<String>()
        for ((name, p) in palettes()) {
            val grounds = mapOf("background" to p.background, "surface" to p.surface, "surfaceRaised" to p.surfaceRaised, "surfaceSunken" to p.surfaceSunken)
            for ((g, bg) in grounds) {
                check(name, "ink/$g", p.ink, bg, 4.5, failures)
                check(name, "inkMuted/$g", p.inkMuted, bg, 4.5, failures)
                check(name, "inkFaint/$g", p.inkFaint, bg, 4.5, failures)
                check(name, "accentInk/$g", p.accentInk, bg, 4.5, failures)
                check(name, "primary/$g", p.primary, bg, 4.5, failures)
                check(name, "accent/$g", p.accent, bg, 3.0, failures)
            }
            check(name, "danger/background", p.danger, p.background, 4.5, failures)
            check(name, "danger/surface", p.danger, p.surface, 4.5, failures)
            check(name, "onPrimary/primary", p.onPrimary, p.primary, 4.5, failures)
            check(name, "onAccent/accent", p.onAccent, p.accent, 3.0, failures)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun realBlackIsBlackOnlyInDark() {
        for (s in VisualStyle.entries) {
            assertTrue(Palettes.of(s, true).trueBlack().background == Color.Black)
            assertTrue(Palettes.of(s, false).trueBlack() == Palettes.of(s, false))
        }
    }
}
