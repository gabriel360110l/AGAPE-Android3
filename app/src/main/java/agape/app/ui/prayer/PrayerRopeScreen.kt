package agape.app.ui.prayer

import agape.app.R
import agape.app.core.model.Tradition
import agape.app.data.rosary.PrayerRope
import agape.app.data.rosary.RopeCount
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
import agape.app.ui.components.TraditionCross
import agape.app.ui.components.crossPath
import agape.app.ui.theme.AgapeTheme
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The least time on one knot before Next opens, when waiting on each bead is switched on. */
private const val KNOT_MILLIS = 2_000

/** The rope's names, and the cross that hangs from it. */
val PrayerRope.title: Int get() = R.string.rope_chotki
val PrayerRope.subtitle: Int get() = R.string.rope_chotki_sub
val PrayerRope.cross: Tradition get() = Tradition.EASTERN_ORTHODOX

/** Keeps a [RopeCount] across rotation and other recreations of the screen. */
private val RopeCountSaver = Saver<RopeCount, String>(save = { it.encode() }, restore = { RopeCount.decode(it) })

/**
 * A prayer rope to pray along: Next moves one knot on. With waiting on each bead switched on (Settings), Next opens
 * again only after two seconds, so the prayer is said on every knot. A soft tick on every knot and a firmer one on
 * every tenth, unless vibration is switched off. The count is kept when the screen is left and opened again.
 */
@Composable
fun PrayerRopeScreen(rope: PrayerRope, onBack: () -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val settings = LocalSettings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = AgapeTheme.colors
    var count by rememberSaveable(stateSaver = RopeCountSaver) {
        mutableStateOf(container.prayerProgress.rope(rope) ?: RopeCount(rope.sizes.first()))
    }
    // Asking before the count is cleared: the rope size to start again with, or null when not asking.
    var confirmRestart by remember { mutableStateOf<Int?>(null) }
    val size = count.size
    val title = stringResource(rope.title)
    val prayer = rope.prayer[lang]
    val bead = rope.bead?.get(lang)
    QuietWhilePraying()

    fun update(next: RopeCount) {
        count = next
        container.prayerProgress.saveRope(rope, next)
    }
    fun tap() {
        val next = count.next()
        update(next)
        if (settings.prayerVibration) tick(context, strong = PrayerRope.isBead(next.knot) || next.done)
        if (next.done) scope.launch { container.user.markPrayed("rope:${rope.name.lowercase()}", title) }
    }
    /** Starts again with [newSize] knots; asks first when something has been counted. */
    fun restart(newSize: Int) {
        if (count.knot == 0 && count.rounds == 0) update(RopeCount(newSize)) else confirmRestart = newSize
    }

    confirmRestart?.let { newSize ->
        AlertDialog(
            onDismissRequest = { confirmRestart = null },
            title = { Text(stringResource(R.string.rope_restart_title)) },
            text = { Text(stringResource(R.string.rope_restart_text)) },
            confirmButton = { TextButton({ confirmRestart = null; update(RopeCount(newSize)) }) { Text(stringResource(R.string.rope_restart)) } },
            dismissButton = { TextButton({ confirmRestart = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title, onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Kicker(stringResource(rope.subtitle))
            Spacer(Modifier.height(10.dp))
            if (rope.sizes.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rope.sizes.forEach { n -> Pill(stringResource(R.string.rope_knots, n), size == n, { if (n != size) restart(n) }) }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .background(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.16f), Color.Transparent)))
                    .semantics { contentDescription = "$title, ${count.knot} / $size" },
                contentAlignment = Alignment.Center,
            ) {
                RopeRing(size, count.knot, rope, Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${count.knot}", style = MaterialTheme.typography.displayLarge, color = c.primary)
                    Text(stringResource(R.string.rope_of, size), style = MaterialTheme.typography.labelLarge, color = c.inkMuted)
                    if (count.rounds > 0) Text(stringResource(R.string.rope_rounds, count.rounds), style = MaterialTheme.typography.labelMedium, color = c.accent)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (bead != null && PrayerRope.isBead(count.knot)) bead else prayer,
                style = AgapeTheme.type.scripture, color = c.ink, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(22.dp))
            PaceButton(
                stringResource(R.string.rope_next), waiting = stringResource(R.string.pace_praying),
                minMillis = if (settings.paceWait) KNOT_MILLIS else 0, step = count.knot to count.rounds, onClick = ::tap,
            )
            Spacer(Modifier.height(14.dp))
            AgapeCard(tonal = true) {
                Text(stringResource(R.string.rope_chotki_about), style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
            }
            Row(Modifier.padding(vertical = 8.dp)) {
                QuietButton(stringResource(R.string.rosary_previous), { update(count.back()) })
                QuietButton(stringResource(R.string.rope_restart), { restart(size) })
            }
        }
    }
}

/** The knots around a circle, prayed ones in gold, beads larger, and the cross hanging below. */
@Composable
private fun RopeRing(size: Int, knot: Int, rope: PrayerRope, modifier: Modifier = Modifier) {
    val c = AgapeTheme.colors
    val progress by animateFloatAsState(knot / size.toFloat(), label = "rope")
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
            val isBead = PrayerRope.isBead(i)
            val prayed = i <= knot
            val radius = if (isBead) knotR * 1.35f else knotR
            drawCircle(if (prayed) c.accent else wool.copy(alpha = if (c.isDark) 0.9f else 0.75f), radius, p)
            if (i == knot) drawCircle(c.primary, radius + 3.dp.toPx(), p, style = Stroke(2.dp.toPx()))
        }
        // The tassel and cross below the ring.
        val bottom = Offset(center.x, center.y + r)
        drawLine(wool, bottom, bottom.copy(y = bottom.y + r * 0.16f), 2.dp.toPx())
        val cw = r * 0.22f
        val ch = cw * 1.33f
        translate(center.x - cw / 2, bottom.y + r * 0.16f) { drawPath(crossPath(rope.cross, Size(cw, ch)), c.accent) }
    }
}

/** A short tick, or a firmer one for a bead; silent on phones without a vibrator. */
private fun tick(context: Context, strong: Boolean) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    if (vibrator?.hasVibrator() != true) return
    val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.createPredefined(if (strong) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_TICK)
    else VibrationEffect.createOneShot(if (strong) 60L else 15L, if (strong) 255 else 80)
    vibrator.vibrate(effect)
}

/** A small rope with its cross, as a mark on cards. */
@Composable
fun RopeMark(rope: PrayerRope, modifier: Modifier = Modifier) {
    val c = AgapeTheme.colors
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension * 0.30f
            val center = Offset(this.size.width / 2, this.size.height * 0.38f)
            for (i in 0 until 24) {
                val a = PI.toFloat() / 2 + (i + 0.5f) / 25f * 2 * PI.toFloat()
                drawCircle(c.accent.copy(alpha = 0.85f), this.size.minDimension * if (i % 6 == 5) 0.05f else 0.032f, Offset(center.x + cos(a) * r, center.y + sin(a) * r))
            }
        }
        TraditionCross(rope.cross, Modifier.padding(top = 0.dp).align(Alignment.BottomCenter).size(width = 14.dp, height = 18.dp), color = c.accent)
    }
}
