package agape.app.ui.components

import agape.app.R
import agape.app.core.model.Tradition
import agape.app.data.calendar.DayStatus
import agape.app.data.calendar.FastLevel
import agape.app.data.calendar.FeastRank
import agape.app.ui.LocalLanguage
import agape.app.ui.theme.AgapeTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** The mark of a day's fast: a ring for a fast day, a full dot for a strict fast, nothing otherwise. */
@Composable
fun FastMark(level: FastLevel, color: Color, modifier: Modifier = Modifier) {
    when (level) {
        FastLevel.FAST, FastLevel.NO_MEAT -> Box(modifier.size(6.dp).border(1.2.dp, color, CircleShape))
        FastLevel.STRICT -> Box(modifier.size(6.dp).clip(CircleShape).background(color))
        else -> Unit
    }
}

/**
 * The day's fast or feast as a small pill; a tap explains it. [onDark] is for text over the picture on Home.
 */
@Composable
fun DayStatusPill(status: DayStatus, tradition: Tradition, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val c = AgapeTheme.colors
    val lang = LocalLanguage.current
    var open by rememberSaveable { mutableStateOf(false) }
    val feast = status.feast?.takeIf { it.rank != FeastRank.SEASON }
    val ink = if (onDark) Color(0xFFF4EEE3) else c.ink
    val tint = when {
        feast != null && !status.isFast -> c.accent
        status.level == FastLevel.STRICT -> if (onDark) Color(0xFFD9B8E8) else c.primary
        status.isFast -> if (onDark) Color(0xFFD9B8E8) else c.primary
        else -> if (onDark) ink.copy(alpha = 0.7f) else c.inkMuted
    }
    Row(
        modifier.clip(CircleShape)
            .background(if (onDark) Color.Black.copy(alpha = 0.28f) else c.surfaceSunken)
            .border(1.dp, tint.copy(alpha = 0.55f), CircleShape)
            .clickable { open = true }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (status.isFast) FastMark(status.level, tint) else Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
        Text(
            listOfNotNull(feast?.name?.get(lang), status.label[lang]).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 2,
        )
    }
    if (open) DayStatusDialog(status, tradition) { open = false }
}

@Composable
fun DayStatusDialog(status: DayStatus, tradition: Tradition, onDismiss: () -> Unit) {
    val c = AgapeTheme.colors
    val lang = LocalLanguage.current
    val body = when (status.level) {
        FastLevel.NONE -> R.string.fast_info_none
        FastLevel.FREE -> R.string.fast_info_free
        FastLevel.NO_MEAT -> if (tradition == Tradition.CATHOLIC) R.string.fast_info_abstinence else R.string.fast_info_cheesefare
        FastLevel.FAST -> when (tradition) {
            Tradition.EASTERN_ORTHODOX -> R.string.fast_info_fast_eo
            Tradition.ORIENTAL_ORTHODOX -> R.string.fast_info_fast_oo
            Tradition.CATHOLIC -> R.string.fast_info_none
        }
        FastLevel.STRICT -> if (tradition == Tradition.CATHOLIC) R.string.fast_info_strict_rc else R.string.fast_info_strict
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(status.feast?.takeIf { it.rank != FeastRank.SEASON }?.name?.get(lang) ?: status.label[lang], style = MaterialTheme.typography.headlineSmall, color = c.ink) },
        text = {
            Column {
                if (status.feast != null && status.feast.rank != FeastRank.SEASON) {
                    Text(status.label[lang], style = MaterialTheme.typography.titleMedium, color = c.primary)
                    Spacer(Modifier.height(8.dp))
                }
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = c.ink)
                if (status.fish) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.fast_info_fish), style = MaterialTheme.typography.bodyMedium, color = c.ink)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(if (tradition == Tradition.CATHOLIC) R.string.fast_guideline_rc else R.string.fast_guideline),
                    style = MaterialTheme.typography.bodySmall, color = c.inkMuted,
                )
                if (tradition == Tradition.EASTERN_ORTHODOX) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.fast_new_calendar), style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), color = c.primary) } },
    )
}
