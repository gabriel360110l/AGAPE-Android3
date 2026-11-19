package agape.app.ui.components

import agape.app.ui.theme.AgapeTheme
import agape.app.ui.theme.Spacing
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Text

/**
 * A group under a small uppercase title, as in a printed book. [modifier] places the group (padding around it);
 * [titleColor] is the accent by default, muted where the group is one of many on a page.
 */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color = AgapeTheme.colors.accentInk,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Kicker(title, color = titleColor)
        Spacer(Modifier.height(Spacing.s))
        content()
    }
}

/** A paragraph of running text, with room under it before the next. [style] defaults to body text. */
@Composable
fun Paragraph(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    italic: Boolean = false,
) {
    Text(
        text,
        style = if (italic) style.copy(fontStyle = FontStyle.Italic) else style,
        color = AgapeTheme.colors.ink,
        modifier = modifier.padding(bottom = Spacing.s),
    )
}

/**
 * A number with its label. In a row ([stacked] false) the label sits after the number, as on Home; stacked, it sits
 * under it, centred, as on the profile.
 */
@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, stacked: Boolean = false) {
    val c = AgapeTheme.colors
    if (stacked) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = c.ink)
            Text(label, style = MaterialTheme.typography.labelSmall, color = c.inkMuted, textAlign = TextAlign.Center)
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
            Spacer(Modifier.width(Spacing.s))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.padding(bottom = Spacing.xs))
        }
    }
}
