package agape.app.ui.prayer

import agape.app.R
import agape.app.core.model.Tradition
import agape.app.data.counter.AtEnd
import agape.app.data.rosary.PrayerRope
import agape.app.notifications.QuietWhilePraying
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.LocalSettings
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Kicker
import agape.app.ui.components.PaceButton
import agape.app.ui.components.Pill
import agape.app.ui.components.QuietButton
import agape.app.ui.components.counterSemantics
import agape.app.ui.components.counterTaps
import agape.app.ui.components.plainCrossPath
import agape.app.ui.tradition
import androidx.compose.ui.graphics.Path
import agape.app.ui.components.rememberPrayerCounter
import agape.app.ui.components.rememberReduceMotion
import agape.app.ui.components.crossPath
import agape.app.ui.theme.AgapeTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The least time on one knot before a tap counts, when waiting on each bead is switched on. */
private const val KNOT_MILLIS = 2_000L

val PrayerRope.title: Int get() = R.string.rope_chotki

/**
 * The rope as each tradition meets it: the cross hanging from it, the line under the title and the explanation.
 * Eastern Orthodox: the Byzantine chotki with its three-bar cross. Catholic: the Jesus Prayer as the Catechism gives
 * it (CCC 2667-2668), with the Latin cross. Oriental Orthodox: neutral words and a plain cross; PRIEST REVIEW: the
 * Coptic wording (rope_about_oo) and whether the Coptic cross belongs on the rope were not verified with a Coptic
 * source and should be checked by a priest before they are made more specific.
 */
private data class RopeLook(val cross: (Size) -> Path, val subtitle: Int, val about: Int)

private fun ropeLook(tradition: Tradition) = when (tradition) {
    Tradition.EASTERN_ORTHODOX -> RopeLook({ crossPath(Tradition.EASTERN_ORTHODOX, it) }, R.string.rope_chotki_sub, R.string.rope_chotki_about)
    Tradition.CATHOLIC -> RopeLook({ crossPath(Tradition.CATHOLIC, it) }, R.string.rope_sub_jesus_prayer, R.string.rope_about_ca)
    Tradition.ORIENTAL_ORTHODOX -> RopeLook(::plainCrossPath, R.string.rope_sub_jesus_prayer, R.string.rope_about_oo)
}

/** The line under the rope's title for [tradition], as on its card. */
fun ropeSubtitle(tradition: Tradition): Int = ropeLook(tradition).subtitle

/**
 * A prayer rope to pray along. The whole screen is the tap target: a tap anywhere moves one knot on (the buttons and
 * choices keep their own taps). The size (33, 50, 100) and the markers (none, or every 10, 25 or 33 knots) can be
 * chosen; markers are drawn larger and felt as a double pulse. Counter only hides the prayer and the explanation.
 * With waiting on each bead switched on (Settings), a tap counts only two seconds after the last. The count is kept
 * when the screen is left. Quiet mode darkens the screen to pray without looking.
 */
@Composable
fun PrayerRopeScreen(rope: PrayerRope, onBack: () -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val settings = LocalSettings.current
    val tradition = tradition
    val look = ropeLook(tradition)
    val scope = rememberCoroutineScope()
    val c = AgapeTheme.colors
    val title = stringResource(rope.title)
    val every = settings.ropeMarkers
    val counter = rememberPrayerCounter(
        "rope_${rope.name.lowercase()}", rope.sizes.first(), AtEnd.WRAP, daily = false, accept = { it.total in rope.sizes },
    )
    SideEffect {
        counter.marker = { PrayerRope.isMarker(it.done, every) || it.complete }
        counter.onComplete = { scope.launch { container.user.markPrayed("rope:${rope.name.lowercase()}", title) } }
        counter.paceMillis = if (settings.paceWait) KNOT_MILLIS else 0L
    }
    val count = counter.count
    // Asking before the count is cleared: the rope size to start again with, or null when not asking.
    var confirmRestart by remember { mutableStateOf<Int?>(null) }
    var quiet by rememberSaveable { mutableStateOf(false) }
    val size = count.total
    val markerPrayer = rope.markerPrayer(tradition)?.get(lang)
    val line = if (markerPrayer != null && PrayerRope.isMarker(count.done, every)) markerPrayer else rope.prayer[lang]
    val description = stringResource(R.string.counter_a11y, title, count.done, size)
    val tapLabel = stringResource(R.string.counter_tap)
    val backLabel = stringResource(R.string.counter_back)
    QuietWhilePraying()

    /** Starts again with [newSize] knots; asks first when something has been counted. */
    fun restart(newSize: Int) {
        if (count.done == 0 && count.rounds == 0) counter.reset(newSize) else confirmRestart = newSize
    }

    confirmRestart?.let { newSize ->
        AlertDialog(
            onDismissRequest = { confirmRestart = null },
            title = { Text(stringResource(R.string.rope_restart_title)) },
            text = { Text(stringResource(R.string.rope_restart_text)) },
            confirmButton = { TextButton({ confirmRestart = null; counter.reset(newSize) }) { Text(stringResource(R.string.rope_restart)) } },
            dismissButton = { TextButton({ confirmRestart = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (quiet) {
        QuietCounter(
            counter, headline = "${count.done}",
            detail = stringResource(R.string.rope_of, size) + if (count.rounds > 0) " · " + stringResource(R.string.rope_rounds, count.rounds) else "",
            text = if (settings.ropeCounterOnly) null else line, description = description, onExit = { quiet = false },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title, onBack) { QuietModeButton { quiet = true } }
        Column(
            Modifier.fillMaxSize().counterTaps(counter).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Kicker(stringResource(look.subtitle))
            Spacer(Modifier.height(10.dp))
            if (rope.sizes.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rope.sizes.forEach { n -> Pill(stringResource(R.string.rope_knots, n), size == n, { if (n != size) restart(n) }) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.rope_markers), style = MaterialTheme.typography.labelMedium, color = c.inkMuted)
                PrayerRope.MARKER_CHOICES.forEach { n ->
                    Pill(
                        if (n == 0) stringResource(R.string.rope_markers_none) else "$n", every == n,
                        { scope.launch { container.settings.setRopeMarkers(n) } },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth(0.92f).aspectRatio(1f)
                    .background(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.16f), Color.Transparent)))
                    .counterSemantics(counter, description, tapLabel, backLabel),
                contentAlignment = Alignment.Center,
            ) {
                RopeRing(size, count.done, every, look.cross, Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${count.done}", style = MaterialTheme.typography.displayLarge, color = c.primary)
                    Text(stringResource(R.string.rope_of, size), style = MaterialTheme.typography.labelLarge, color = c.inkMuted)
                    if (count.rounds > 0) Text(stringResource(R.string.rope_rounds, count.rounds), style = MaterialTheme.typography.labelMedium, color = c.accent)
                }
            }
            if (!settings.ropeCounterOnly) {
                Text(line, style = AgapeTheme.type.scripture, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
            }
            Text(stringResource(R.string.quiet_hint), style = MaterialTheme.typography.bodySmall, color = c.inkFaint, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))
            PaceButton(
                stringResource(R.string.rope_next), waiting = stringResource(R.string.pace_praying),
                minMillis = if (settings.paceWait) KNOT_MILLIS.toInt() else 0, step = count, onClick = { counter.tap() },
            )
            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
                QuietButton(stringResource(R.string.rosary_previous), { counter.back() })
                QuietButton(stringResource(R.string.rope_restart), { restart(size) })
                QuietButton(
                    stringResource(if (settings.ropeCounterOnly) R.string.rope_show_prayer else R.string.rope_counter_only),
                    { scope.launch { container.settings.setRopeCounterOnly(!settings.ropeCounterOnly) } },
                )
            }
            if (!settings.ropeCounterOnly) AgapeCard(tonal = true) {
                Text(stringResource(look.about), style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The knots around a circle, prayed ones in gold, markers larger, and the cross hanging below. */
@Composable
private fun RopeRing(size: Int, knot: Int, every: Int, cross: (Size) -> Path, modifier: Modifier = Modifier) {
    val c = AgapeTheme.colors
    val still = rememberReduceMotion()
    val progress by animateFloatAsState(knot / size.toFloat(), if (still) snap() else spring(), label = "rope")
    val wool = if (c.isDark) Color(0xFF3A3530) else Color(0xFF2B2622)
    Canvas(modifier.padding(8.dp)) {
        val center = Offset(this.size.width / 2, this.size.height * 0.46f)
        val r = this.size.minDimension * 0.40f
        val knotR = (2 * PI.toFloat() * r / size * 0.36f).coerceIn(3.dp.toPx(), 9.dp.toPx())
        drawCircle(c.line, r, center, style = Stroke(1.dp.toPx()))
        drawArc(c.accent.copy(alpha = 0.35f), -90f, progress * 360f, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(2.dp.toPx()))
        for (i in 1..size) {
            // Knot 1 sits just right of the bottom, where the cross hangs, and the rope runs round anticlockwise.
            val a = PI.toFloat() / 2 - i / (size + 1f) * 2 * PI.toFloat()
            val p = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
            val marker = PrayerRope.isMarker(i, every)
            val prayed = i <= knot
            val radius = if (marker) knotR * 1.45f else knotR
            drawCircle(if (prayed) c.accent else wool.copy(alpha = if (c.isDark) 0.9f else 0.75f), radius, p)
            if (i == knot) drawCircle(c.primary, radius + 3.dp.toPx(), p, style = Stroke(2.dp.toPx()))
        }
        // The tassel and cross below the ring.
        val bottom = Offset(center.x, center.y + r)
        drawLine(wool, bottom, bottom.copy(y = bottom.y + r * 0.16f), 2.dp.toPx())
        val cw = r * 0.22f
        val ch = cw * 1.33f
        translate(center.x - cw / 2, bottom.y + r * 0.16f) { drawPath(cross(Size(cw, ch)), c.accent) }
    }
}

/** A small rope with its cross, as a mark on cards. */
@Composable
fun RopeMark(rope: PrayerRope, modifier: Modifier = Modifier) {
    val c = AgapeTheme.colors
    val cross = ropeLook(tradition).cross
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension * 0.30f
            val center = Offset(this.size.width / 2, this.size.height * 0.38f)
            for (i in 0 until 24) {
                val a = PI.toFloat() / 2 + (i + 0.5f) / 25f * 2 * PI.toFloat()
                drawCircle(c.accent.copy(alpha = 0.85f), this.size.minDimension * if (i % 6 == 5) 0.05f else 0.032f, Offset(center.x + cos(a) * r, center.y + sin(a) * r))
            }
        }
        Canvas(Modifier.align(Alignment.BottomCenter).size(width = 14.dp, height = 18.dp)) { drawPath(cross(this.size), c.accent) }
    }
}
