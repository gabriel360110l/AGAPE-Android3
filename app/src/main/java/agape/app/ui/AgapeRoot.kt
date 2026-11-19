package agape.app.ui

import agape.app.AppContainer
import agape.app.core.model.ThemeMode
import agape.app.core.model.Tradition
import agape.app.ui.intro.AgapeIntro
import agape.app.core.model.VisualStyle
import agape.app.data.auth.AuthState
import agape.app.ui.main.MainShell
import agape.app.ui.onboarding.Onboarding
import agape.app.ui.theme.AgapeTheme
import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AgapeRoot(container: AppContainer, newIntents: StateFlow<Intent?>, onIntentHandled: () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val auth by container.auth.state.collectAsStateWithLifecycle()
    val s = settings ?: return // DataStore answers within a frame; the splash screen covers it.

    // While picking a style during onboarding, the screen previews the candidate before it is saved.
    var previewStyle by remember { mutableStateOf<VisualStyle?>(null) }
    val dark = when (s.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val style = previewStyle ?: s.style ?: VisualStyle.NAVY_CREAM

    CompositionLocalProvider(
        LocalContainer provides container,
        LocalLanguage provides container.language(),
        LocalSettings provides s,
    ) {
        AgapeTheme(style = style, dark = dark, fontScale = s.fontScale) {
            Box(Modifier.fillMaxSize().background(AgapeTheme.colors.background)) {
                val signedIn = auth is AuthState.SignedIn
                // Someone who signed in before this step existed counts as past it, so signing out later keeps the app open.
                LaunchedEffect(signedIn) { if (signedIn && !s.accountStepDone) container.settings.setAccountStepDone() }
                val inApp = s.opensApp(signedIn)
                Crossfade(targetState = inApp, label = "root") { open ->
                    if (open) MainShell(newIntents = newIntents, onIntentHandled = onIntentHandled)
                    else if (auth !is AuthState.Loading) Onboarding(settings = s, onPreviewStyle = { previewStyle = it })
                }
                // The opening plays on every start of the app and again whenever the church is changed.
                val church = s.tradition
                val context = androidx.compose.ui.platform.LocalContext.current
                LaunchedEffect(church) { agape.app.widget.VerseWidget.refresh(context) }
                var shownFor by rememberSaveable { mutableStateOf<Tradition?>(null) }
                if (inApp && church != null && church != shownFor) {
                    key(church) { AgapeIntro(church) { shownFor = church } }
                }
            }
        }
    }
}
