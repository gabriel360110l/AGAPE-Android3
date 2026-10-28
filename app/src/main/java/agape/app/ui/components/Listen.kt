package agape.app.ui.components

import agape.app.R
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.theme.AgapeTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * Reads [parts] aloud, or stops. Reading stops when the screen closes. [key] names the text, so two screens never
 * mistake each other's reading for their own.
 */
@Composable
fun ListenButton(key: String, parts: suspend () -> List<String>) {
    val speaker = LocalContainer.current.speaker
    val lang = LocalLanguage.current
    val scope = rememberCoroutineScope()
    val state by speaker.state.collectAsStateWithLifecycle()
    val active = state.key == key && state.playing
    DisposableEffect(key) { onDispose { speaker.stopIf(key) } }
    IconButton(onClick = { if (active) speaker.stop() else scope.launch { speaker.play(key, parts(), lang) } }) {
        Icon(
            if (active) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp,
            stringResource(if (active) R.string.listen_stop else R.string.listen),
            tint = if (active) AgapeTheme.colors.accent else AgapeTheme.colors.primary,
        )
    }
}
