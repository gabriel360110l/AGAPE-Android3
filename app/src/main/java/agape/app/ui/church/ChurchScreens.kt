package agape.app.ui.church

import agape.app.R
import agape.app.core.model.Passage
import agape.app.core.model.Tradition
import agape.app.data.content.Council
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Kicker
import agape.app.ui.components.Ornament
import agape.app.ui.components.QuietButton
import agape.app.ui.onboarding.traditionName
import agape.app.ui.passageLabel
import agape.app.ui.theme.AgapeTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun ArticleScreen(id: String, onBack: () -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val article = remember(id) { container.content.article(id) } ?: return
    val c = AgapeTheme.colors
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(article.title[lang], onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp)) {
            Text(article.title[lang], style = MaterialTheme.typography.headlineLarge, color = c.ink)
            Spacer(Modifier.height(6.dp))
            Text(article.summary[lang], style = MaterialTheme.typography.titleMedium, color = c.inkMuted)
            Spacer(Modifier.height(14.dp))
            Ornament()
            Spacer(Modifier.height(14.dp))
            article.body[lang].forEach {
                Text(it, style = AgapeTheme.type.scripture.copy(fontSize = AgapeTheme.type.scripture.fontSize * 0.92f), color = c.ink, modifier = Modifier.padding(bottom = 14.dp))
            }
        }
    }
}

@Composable
fun CouncilScreen(id: String, onBack: () -> Unit, openPassage: (Passage) -> Unit) {
    val container = LocalContainer.current
    val council = remember(id) { container.content.council(id) } ?: return
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(council.name[LocalLanguage.current], onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            CouncilBody(council, openPassage)
        }
    }
}

/** The council summary, shared by the Church section and the council reading plan. */
@Composable
fun CouncilBody(council: Council, openPassage: (Passage) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    Column {
        Kicker(stringResource(R.string.council_number, council.number, council.year))
        Spacer(Modifier.height(4.dp))
        Text(council.name[lang], style = MaterialTheme.typography.headlineMedium, color = c.ink)
        Text(stringResource(R.string.council_convoked, council.convokedBy[lang]), style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
        Spacer(Modifier.height(16.dp))

        AgapeCard {
            Kicker(stringResource(R.string.council_scripture))
            Spacer(Modifier.height(4.dp))
            council.passages.mapNotNull { Passage.parse(it) }.forEach { p ->
                QuietButton(container.passageLabel(p, lang), { openPassage(p) })
            }
        }
        Spacer(Modifier.height(12.dp))
        AgapeCard {
            Kicker(stringResource(R.string.council_why))
            Spacer(Modifier.height(6.dp))
            Text(council.reason[lang], style = MaterialTheme.typography.bodyLarge, color = c.ink)
        }
        Spacer(Modifier.height(12.dp))
        AgapeCard {
            Kicker(stringResource(R.string.council_canons))
            Spacer(Modifier.height(6.dp))
            council.canons.forEach {
                Text("•  " + it[lang], style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        AgapeCard(tonal = true) {
            Kicker(stringResource(R.string.council_outcome))
            Spacer(Modifier.height(6.dp))
            Text(council.outcome[lang], style = MaterialTheme.typography.bodyLarge, color = c.ink)
        }
        Spacer(Modifier.height(12.dp))
        val names = council.recognizedBy.map { stringResource(traditionName(it)) }
        Text(
            stringResource(R.string.council_recognized_by, names.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall, color = c.inkFaint,
        )
        if (Tradition.ORIENTAL_ORTHODOX !in council.recognizedBy && council.number == 4) {
            Text(stringResource(R.string.council_chalcedon_note), style = MaterialTheme.typography.bodySmall, color = c.inkFaint, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(16.dp))
        Ornament(Modifier.align(Alignment.CenterHorizontally))
    }
}
