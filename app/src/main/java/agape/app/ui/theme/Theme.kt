package agape.app.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.Surface
import androidx.compose.material3.ColorScheme
import agape.app.core.model.VisualStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalColors = staticCompositionLocalOf { Palettes.of(VisualStyle.NAVY_CREAM, false) }
private val LocalType = staticCompositionLocalOf { agapeType(VisualStyle.NAVY_CREAM, 1f) }
private val LocalStyle = staticCompositionLocalOf { VisualStyle.NAVY_CREAM }

object AgapeTheme {
    val colors: AgapeColors @Composable @ReadOnlyComposable get() = LocalColors.current
    val type: AgapeType @Composable @ReadOnlyComposable get() = LocalType.current
    val style: VisualStyle @Composable @ReadOnlyComposable get() = LocalStyle.current
}

@Composable
fun AgapeTheme(style: VisualStyle, dark: Boolean, fontScale: Float = 1f, content: @Composable () -> Unit) {
    val colors = remember(style, dark) { Palettes.of(style, dark) }
    val type = remember(style, fontScale) { agapeType(style, fontScale) }
    val typography = remember(style, fontScale) { materialTypography(style, fontScale) }
    val scheme = remember(colors) { schemeFor(colors) }
    CompositionLocalProvider(LocalColors provides colors, LocalType provides type, LocalStyle provides style) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

private fun schemeFor(colors: AgapeColors): ColorScheme {
    val base = if (colors.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = colors.primary, onPrimary = colors.onPrimary,
        primaryContainer = colors.surfaceSunken, onPrimaryContainer = colors.ink,
        secondary = colors.accent, onSecondary = colors.onAccent,
        secondaryContainer = colors.surfaceSunken, onSecondaryContainer = colors.ink,
        tertiary = colors.accent, onTertiary = colors.onAccent,
        background = colors.background, onBackground = colors.ink,
        surface = colors.surface, onSurface = colors.ink,
        surfaceVariant = colors.surfaceSunken, onSurfaceVariant = colors.inkMuted,
        surfaceContainerLowest = colors.background, surfaceContainerLow = colors.surface,
        surfaceContainer = colors.surface, surfaceContainerHigh = colors.surfaceRaised,
        surfaceContainerHighest = colors.surfaceRaised,
        outline = colors.line, outlineVariant = colors.line,
        error = colors.danger, onError = colors.onPrimary,
        inverseSurface = colors.ink, inverseOnSurface = colors.background,
    )
}

/**
 * Real black under [content] when [on] and the theme is dark: the prayer screens use it so that praying at night the
 * screen gives off as little light as possible. In light mode, or with [on] false, nothing changes.
 */
@Composable
fun TrueBlack(on: Boolean, content: @Composable () -> Unit) {
    val base = LocalColors.current
    if (!on || !base.isDark) return content()
    val colors = remember(base) { base.trueBlack() }
    val scheme = remember(colors) { schemeFor(colors) }
    CompositionLocalProvider(LocalColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography) {
            Surface(color = colors.background, contentColor = colors.ink, modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}
