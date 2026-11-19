package agape.app

import agape.app.ui.me.donationConfigured
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The donation row stays hidden until a real page is configured. */
class DonationLinkTest {
    @Test
    fun placeholderOrEmptyIsHidden() {
        assertFalse(donationConfigured(""))
        assertFalse(donationConfigured("https://example.org/agape/donate"))
        assertFalse(donationConfigured("http://insecure.test/donate"))
    }

    @Test
    fun realPageIsShown() = assertTrue(donationConfigured("https://donate.test/agape"))
}
