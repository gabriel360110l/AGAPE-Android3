package agape.app.ui.prayer

import agape.app.ui.theme.Spacing
import agape.app.ui.theme.Quiet
import agape.app.R
import agape.app.ui.LocalContainer
import agape.app.ui.LocalSettings
import agape.app.ui.components.CounterState
import agape.app.ui.components.counterTapArea
import agape.app.ui.components.rememberReduceMotion
import agape.app.ui.theme.AgapeTheme
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch

/** The colours of quiet mode: near black, the text dimmed; a little lighter after the light theme, for eyes used to it. */
private data class QuietColors(val background: Color, val ink: Color, val faint: Color, val accent: Color)

private fun quietColors(dark: Boolean) = if (dark) {
    QuietColors(Quiet.darkBackground, Quiet.darkInk.copy(alpha = 0.34f), Quiet.darkInk.copy(alpha = 0.20f), Quiet.darkAccent.copy(alpha = 0.40f))
} else {
    QuietColors(Quiet.lightBackground, Quiet.lightInk.copy(alpha = 0.46f), Quiet.lightInk.copy(alpha = 0.26f), Quiet.lightAccent.copy(alpha = 0.50f))
}

/**
 * Quiet mode for the prayer rope and the Rosary: a near-black screen with dimmed text, the whole screen as the tap
 * area of [state], the screen kept on and the system bars hidden while it is shown, and a small control to leave it
 * (Back leaves it too). The marker pulses can be switched off here. With animations turned off in Android, nothing
 * fades.
 */
@Composable
fun QuietCounter(state: CounterState, headline: String, detail: String, text: String?, description: String, onExit: () -> Unit) {
    val container = LocalContainer.current
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val colors = quietColors(AgapeTheme.colors.isDark)
    val still = rememberReduceMotion()
    val view = LocalView.current
    val shown = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) { if (!still) shown.animateTo(1f, tween(450)) }
    DisposableEffect(state, view) {
        state.quiet = true
        val wasOn = view.keepScreenOn
        view.keepScreenOn = true
        val window = view.context.activity()?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            state.quiet = false
            view.keepScreenOn = wasOn
            bars?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    BackHandler(onBack = onExit)
    val exitLabel = stringResource(R.string.quiet_exit)
    Box(
        Modifier.fillMaxSize().background(colors.background)
            .counterTapArea(state, description, stringResource(R.string.counter_tap), stringResource(R.string.counter_back)),
    ) {
        Column(
            Modifier.fillMaxSize().alpha(shown.value).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // A count is shown large; a longer headline (the Rosary's mystery and bead) a size smaller.
            Text(headline, style = if (headline.length <= 4) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineLarge, color = colors.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.xs))
            Text(detail, style = MaterialTheme.typography.labelLarge, color = colors.faint, textAlign = TextAlign.Center)
            if (!text.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.xl))
                Text(text, style = AgapeTheme.type.scripture, color = colors.ink, textAlign = TextAlign.Center)
            }
        }
        // The small controls, each its own tap target, so a tap on them does not count.
        Text(
            "✕", style = MaterialTheme.typography.titleMedium, color = colors.faint,
            modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(Spacing.s)
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(role = Role.Button, onClickLabel = exitLabel, onClick = onExit)
                .semantics { contentDescription = exitLabel }
                .padding(Spacing.m),
        )
        val markers = stringResource(if (settings.quietMarkers) R.string.quiet_markers_on else R.string.quiet_markers_off)
        Column(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.quiet_hint), style = MaterialTheme.typography.bodySmall, color = colors.faint)
            if (settings.prayerVibration) Text(
                markers, style = MaterialTheme.typography.labelMedium, color = colors.accent,
                modifier = Modifier.sizeIn(minHeight = 48.dp)
                    .clickable(role = Role.Switch) { scope.launch { container.settings.setQuietMarkers(!settings.quietMarkers) } }
                    .padding(horizontal = Spacing.l, vertical = Spacing.m),
            )
        }
    }
}

/** The way into quiet mode, in the top bar of the prayer rope and the Rosary. */
@Composable
fun QuietModeButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Outlined.DarkMode, contentDescription = stringResource(R.string.quiet_mode), tint = AgapeTheme.colors.inkMuted)
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
