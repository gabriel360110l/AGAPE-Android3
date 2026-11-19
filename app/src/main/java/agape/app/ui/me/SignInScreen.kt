package agape.app.ui.me

import agape.app.R
import agape.app.data.auth.AuthState
import agape.app.ui.LocalContainer
import agape.app.ui.components.DetailTopBar
import agape.app.ui.onboarding.AccountForm
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Signing in after starting without an account. Progress made on this device stays and is merged into the
 * account by SyncRepository; the screen closes itself once the sign-in succeeds.
 */
@Composable
fun SignInScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val auth by container.auth.state.collectAsStateWithLifecycle()
    LaunchedEffect(auth) { if (auth is AuthState.SignedIn) onBack() }
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(stringResource(R.string.me_sign_in), onBack)
        AccountForm(Modifier.weight(1f), showStep = false)
    }
}
