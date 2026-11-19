package agape.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sign-in session must stay out of the Google backup and device transfers, under whatever package name. */
class BackupRulesTest {
    private fun rules(name: String) = listOf(File("src/main/res/xml/$name"), File("app/src/main/res/xml/$name")).first { it.exists() }.readText()

    @Test
    fun sessionIsExcluded() {
        val session = "${BuildConfig.APPLICATION_ID}_preferences.xml"
        for (file in listOf("data_extraction_rules.xml", "backup_rules.xml")) {
            val text = rules(file)
            assertTrue("$file must exclude $session", text.contains("path=\"$session\""))
            assertTrue("$file must exclude the development account", text.contains("path=\"agape_dev_auth.xml\""))
        }
        // Cloud backup and device transfer both.
        assertTrue(Regex("<exclude domain=\"sharedpref\" path=\"${Regex.escape(session)}\"").findAll(rules("data_extraction_rules.xml")).count() == 2)
    }
}
