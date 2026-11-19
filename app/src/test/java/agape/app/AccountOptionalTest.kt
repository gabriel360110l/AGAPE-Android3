package agape.app

import agape.app.core.model.Tradition
import agape.app.core.model.VisualStyle
import agape.app.core.prefs.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app opens without an account once the first-launch steps are done; an account only adds sync. */
class AccountOptionalTest {
    private val onboarded = Settings(welcomeSeen = true, termsAccepted = true, tradition = Tradition.CATHOLIC, style = VisualStyle.NAVY_CREAM)

    @Test fun firstLaunchStopsAtTheAccountStep() = assertFalse(onboarded.opensApp(signedIn = false))

    @Test fun continuingWithoutAnAccountOpensTheApp() = assertTrue(onboarded.copy(accountStepDone = true).opensApp(signedIn = false))

    @Test fun signingInOpensTheApp() = assertTrue(onboarded.opensApp(signedIn = true))

    @Test fun theChurchIsStillChosenFirst() {
        assertFalse(onboarded.copy(tradition = null, accountStepDone = true).opensApp(signedIn = false))
        assertFalse(Settings(accountStepDone = true).opensApp(signedIn = true))
    }
}
