package agape.app.ui.theme

import agape.app.core.model.VisualStyle
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The colour tokens every screen draws from. Material's ColorScheme is derived from these. */
@Immutable
data class AgapeColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val onAccent: Color,
    val line: Color,
    val danger: Color,
    /** Verse highlight colours, in a fixed order so a stored index means the same hue in every style. */
    val highlights: List<Color>,
    val isDark: Boolean,
)

object Palettes {
    // Style 1 — Modern Navy + Cream
    private val navyLight = AgapeColors(
        background = Color(0xFFF7F2E8), surface = Color(0xFFFFFCF6), surfaceRaised = Color(0xFFFFFFFF),
        surfaceSunken = Color(0xFFEFE8DA), ink = Color(0xFF16213B), inkMuted = Color(0xFF4F5A70),
        inkFaint = Color(0xFF8A90A0), primary = Color(0xFF1B2A4A), onPrimary = Color(0xFFF7F2E8),
        accent = Color(0xFFB08A45), onAccent = Color(0xFFFFFCF6), line = Color(0xFFE2D9C6), danger = Color(0xFFA23B3B),
        highlights = listOf(Color(0x66E8C77A), Color(0x5591B4D9), Color(0x55A9C9A0), Color(0x55E3A6A0)), isDark = false,
    )
    private val navyDark = AgapeColors(
        background = Color(0xFF0D1424), surface = Color(0xFF141D31), surfaceRaised = Color(0xFF1B263D),
        surfaceSunken = Color(0xFF0A101D), ink = Color(0xFFEDE6D6), inkMuted = Color(0xFFAEB3C2),
        inkFaint = Color(0xFF6E768A), primary = Color(0xFFD7B56C), onPrimary = Color(0xFF111A2D),
        accent = Color(0xFFD7B56C), onAccent = Color(0xFF111A2D), line = Color(0xFF26324C), danger = Color(0xFFE08A84),
        highlights = listOf(Color(0x55B8963F), Color(0x554F78A8), Color(0x55628A5C), Color(0x55A35E58)), isDark = true,
    )

    // Style 2 — Classic Sacred
    private val sacredLight = AgapeColors(
        background = Color(0xFFF2E8D3), surface = Color(0xFFFAF3E3), surfaceRaised = Color(0xFFFFF9EC),
        surfaceSunken = Color(0xFFE9DCC0), ink = Color(0xFF2B1712), inkMuted = Color(0xFF6A4F42),
        inkFaint = Color(0xFF9C8573), primary = Color(0xFF7A1C2A), onPrimary = Color(0xFFFAF3E3),
        accent = Color(0xFFA9802F), onAccent = Color(0xFFFFF9EC), line = Color(0xFFD9C6A0), danger = Color(0xFF9E2B2B),
        highlights = listOf(Color(0x66E2BE6A), Color(0x558FA9C9), Color(0x559DBB8E), Color(0x55D99A93)), isDark = false,
    )
    private val sacredDark = AgapeColors(
        background = Color(0xFF170D0E), surface = Color(0xFF221416), surfaceRaised = Color(0xFF2D1B1C),
        surfaceSunken = Color(0xFF110909), ink = Color(0xFFF1E5CC), inkMuted = Color(0xFFC1AB8D),
        inkFaint = Color(0xFF7F6A58), primary = Color(0xFFC8A257), onPrimary = Color(0xFF231315),
        accent = Color(0xFFB8454F), onAccent = Color(0xFFF1E5CC), line = Color(0xFF3E2826), danger = Color(0xFFE28A80),
        highlights = listOf(Color(0x55B08A3A), Color(0x55506E92), Color(0x55607F52), Color(0x559A4B4B)), isDark = true,
    )

    // Style 3 — Warm Terracotta
    private val terracottaLight = AgapeColors(
        background = Color(0xFFF6EEE4), surface = Color(0xFFFFF9F2), surfaceRaised = Color(0xFFFFFDF9),
        surfaceSunken = Color(0xFFEFE2D2), ink = Color(0xFF35251D), inkMuted = Color(0xFF725F53),
        inkFaint = Color(0xFFA6938A), primary = Color(0xFFB0553A), onPrimary = Color(0xFFFFF8F1),
        accent = Color(0xFFC59B55), onAccent = Color(0xFF35251D), line = Color(0xFFE6D6C3), danger = Color(0xFFA63A2E),
        highlights = listOf(Color(0x66EBC982), Color(0x5598B6CF), Color(0x55AFC79A), Color(0x55E6A592)), isDark = false,
    )
    private val terracottaDark = AgapeColors(
        background = Color(0xFF1B1613), surface = Color(0xFF241E1A), surfaceRaised = Color(0xFF2E2621),
        surfaceSunken = Color(0xFF15110E), ink = Color(0xFFF2E6D8), inkMuted = Color(0xFFBCA999),
        inkFaint = Color(0xFF7D6E62), primary = Color(0xFFE08B6B), onPrimary = Color(0xFF241A15),
        accent = Color(0xFFD8B26C), onAccent = Color(0xFF241A15), line = Color(0xFF3B312A), danger = Color(0xFFE38F80),
        highlights = listOf(Color(0x55B9944A), Color(0x55577A96), Color(0x55688758), Color(0x55A2594A)), isDark = true,
    )

    fun of(style: VisualStyle, dark: Boolean): AgapeColors = when (style) {
        VisualStyle.NAVY_CREAM -> if (dark) navyDark else navyLight
        VisualStyle.CLASSIC_SACRED -> if (dark) sacredDark else sacredLight
        VisualStyle.WARM_TERRACOTTA -> if (dark) terracottaDark else terracottaLight
    }
}
