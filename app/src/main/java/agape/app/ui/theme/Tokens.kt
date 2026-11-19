package agape.app.ui.theme

import agape.app.core.model.VisualStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The one spacing scale of the app. Paddings, gaps and spacers use these steps rather than numbers of their own, so
 * screens line up with each other.
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Corner radii. Cards keep the corner of their style (see [cardRadius]); the rest use these steps. */
object Radius {
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 28.dp
}

/** The card corner that belongs to each style: soft for Navy, crisp for Classic Sacred, round for Terracotta. */
fun cardRadius(style: VisualStyle) = when (style) {
    VisualStyle.NAVY_CREAM -> Radius.xl
    VisualStyle.CLASSIC_SACRED -> Radius.m
    VisualStyle.WARM_TERRACOTTA -> Radius.xxl
}

/** The shape of a card in [style]. */
fun styleCardShape(style: VisualStyle) = RoundedCornerShape(cardRadius(style))

/**
 * Colours that are the same in every style, because they belong to a picture rather than to the page: the dark
 * photograph behind Home, the gold of a cross or candle, the night of the intro and of quiet mode.
 */
object Fixed {
    /** Cream on the dark photographs and plan covers. */
    val onImage = Color(0xFFF4EEE3)
    /** Dark text on a cream button laid over a photograph. */
    val inkOnCream = Color(0xFF1A1512)
    /** The dark veil over photographs and behind round buttons on them. */
    val scrim = Color(0xFF000000)
    val backdropInk = Color(0xFF0E0B09)
    /** Fasting days on the dark photograph: a soft violet. */
    val fastOnImage = Color(0xFFD9B8E8)

    val paleGold = Color(0xFFF3D98B)
    val gold = Color(0xFFD4AF37)
    val deepGold = Color(0xFFA67C2E)
    /** The bead being prayed: a lit gold. */
    val litGold = Color(0xFFFFE6A8)

    val flame = Color(0xFFF2B655)
    val flameCore = Color(0xFFFFF3C4)
    val flameEdge = Color(0xFFD9772E)
    val flameLight = Color(0xFFFFFBEA)

    /** The wool of a prayer rope, on light and on dark pages. */
    val woolOnLight = Color(0xFF2B2622)
    val woolOnDark = Color(0xFF3A3530)

    /** The night of the intro. */
    val night = Color(0xFF050403)
    /** Real black, for prayer screens in dark mode and for quiet mode. */
    val black = Color(0xFF000000)
    val white = Color(0xFFFFFFFF)
}

/** The sky behind Home at each time of day: top and bottom of the gradient, and the ink and muted ink on it. */
object Sky {
    val dawn = listOf(Color(0xFFF4C9A8), Color(0xFFE8B8C0), Color(0xFF3B2430), Color(0xFF6E4E58))
    val day = listOf(Color(0xFFF6E7C1), Color(0xFFE9D3A6), Color(0xFF3A2C14), Color(0xFF6E5A36))
    val evening = listOf(Color(0xFFE7A272), Color(0xFFB4566A), Color(0xFFFFF4EA), Color(0xFFF6DCCB))
    val night = listOf(Color(0xFF1E2A4A), Color(0xFF0E1426), Color(0xFFEDE6D6), Color(0xFFB5BBCB))
}

/** Quiet mode: near black with dimmed text; a little lighter after the light theme, for eyes used to it. */
object Quiet {
    val darkBackground = Color(0xFF040404)
    val darkInk = Color(0xFFE9E1D2)
    val darkAccent = Color(0xFFC9A55A)
    val lightBackground = Color(0xFF14110E)
    val lightInk = Color(0xFFF1E9DA)
    val lightAccent = Color(0xFFD4B26A)
}

/** The three looks of a shared verse picture: top and bottom of the gradient, text, and ornament. */
object VerseCard {
    val night = listOf(Color(0xFF0E1726), Color(0xFF1C2D48), Color(0xFFF9F5EB), Color(0xFFD4AF37))
    val parchment = listOf(Color(0xFFF7F0E2), Color(0xFFE6D9BF), Color(0xFF281E15), Color(0xFF8C5E32))
    val olive = listOf(Color(0xFF1A2922), Color(0xFF2B4136), Color(0xFFF4F1EA), Color(0xFFC8A96A))
}
