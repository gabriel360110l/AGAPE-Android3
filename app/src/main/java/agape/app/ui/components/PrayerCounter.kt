package agape.app.ui.components

import agape.app.data.counter.AtEnd
import agape.app.data.counter.Buzz
import agape.app.data.counter.PaceGate
import agape.app.data.counter.PrayerCount
import agape.app.data.counter.buzzFor
import agape.app.ui.LocalContainer
import agape.app.ui.LocalSettings
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import java.time.LocalDate

/**
 * The one counter behind the prayer rope, the Rosary and the counters of the Agpeya: a tap says one prayer, with the
 * same wait per bead, the same feel (as Settings allow) and the same saving, so all three count alike.
 *
 * [save] is called with every new count; [clock] gives the time for the wait per bead.
 */
@Stable
class CounterState(
    initial: PrayerCount,
    private val atEnd: AtEnd,
    private val save: (PrayerCount) -> Unit = {},
    private val clock: () -> Long = { SystemClock.uptimeMillis() },
) {
    var count: PrayerCount by mutableStateOf(initial)
        private set

    /** Where a marker is felt: a tenth knot, a large bead of the Rosary, the end. */
    var marker: (PrayerCount) -> Boolean = { it.complete }
    var feel: (Buzz) -> Unit = {}
    var onComplete: () -> Unit = {}
    var vibration: Boolean = true
    var quiet: Boolean = false
    var quietMarkers: Boolean = true
    var paceMillis: Long = 0
        set(value) {
            if (value != field) { field = value; gate = PaceGate(value).also { it.restart(clock()) } }
        }
    private var gate = PaceGate(0)

    /** One prayer said. False when nothing changed: the wait on the bead is not over, or the counter is at its end. */
    fun tap(): Boolean {
        val now = clock()
        if (!gate.open(now)) return false
        val next = count.next(atEnd)
        if (next == count) return false
        step(next, now)
        if (next.complete) onComplete()
        return true
    }

    /** One prayer back; undoes [tap] exactly, also over the end of a round. */
    fun back() {
        val prev = count.back()
        if (prev != count) { set(prev); gate.restart(clock()) }
    }

    /** Straight to [to] prayers said, as when a bead of the Rosary is tapped. */
    fun jump(to: Int) {
        val next = count.jump(to)
        if (next != count) step(next, clock())
    }

    /** From nothing again, with [total] prayers (another size of rope, or the Rosary once more). */
    fun reset(total: Int = count.total) {
        set(PrayerCount(total))
        gate.restart(clock())
    }

    private fun step(next: PrayerCount, now: Long) {
        set(next)
        gate.restart(now)
        feel(buzzFor(marker(next), vibration, quiet, quietMarkers))
    }

    private fun set(next: PrayerCount) {
        count = next
        save(next)
    }
}

/**
 * A [CounterState] kept under [key]: across rotation, and when the screen is left and opened again (on the same day
 * only when [daily]). A saved count is taken back when [accept] allows it; otherwise the counter starts at nothing
 * of [total].
 */
@Composable
fun rememberPrayerCounter(
    key: String,
    total: Int,
    atEnd: AtEnd,
    daily: Boolean,
    accept: (PrayerCount) -> Boolean = { it.total == total },
): CounterState {
    val store = LocalContainer.current.prayerProgress
    val settings = LocalSettings.current
    val context = LocalContext.current
    val today = remember(daily) { if (daily) LocalDate.now().toEpochDay() else null }
    fun make(count: PrayerCount?) =
        CounterState(count?.takeIf(accept) ?: PrayerCount(total), atEnd, save = { store.saveCount(key, it, today) })
    val state = rememberSaveable(key, saver = Saver<CounterState, String>(
        save = { it.count.encode() },
        restore = { make(PrayerCount.decode(it)) },
    )) { make(store.count(key, today)) }
    val haptics = remember(context) { CounterHaptics(context) }
    SideEffect {
        state.vibration = settings.prayerVibration
        state.quietMarkers = settings.quietMarkers
        state.feel = haptics::buzz
    }
    return state
}

/** The whole of a counter as one tap target: a tap says one prayer. With [counterSemantics] for TalkBack. */
fun Modifier.counterTapArea(state: CounterState, description: String, tapLabel: String, backLabel: String): Modifier =
    counterTaps(state).counterSemantics(state, description, tapLabel, backLabel)

/**
 * Taps only, for a large area such as a whole screen whose texts TalkBack should still read one by one; the counter
 * itself then carries [counterSemantics]. Buttons inside keep their own taps.
 */
fun Modifier.counterTaps(state: CounterState): Modifier = pointerInput(state) { detectTapGestures { state.tap() } }

/**
 * What TalkBack says of every counter: [description] (the prayer and how far), read again when it changes; a double
 * tap counts ([tapLabel]); and an action to go one back ([backLabel]).
 */
fun Modifier.counterSemantics(state: CounterState, description: String, tapLabel: String, backLabel: String): Modifier =
    semantics(mergeDescendants = true) {
        contentDescription = description
        liveRegion = LiveRegionMode.Polite
        onClick(label = tapLabel) { state.tap(); true }
        customActions = listOf(CustomAccessibilityAction(backLabel) { state.back(); true })
    }

/** The feel of counting: a light tick, or a marked double pulse; silent on phones without a vibrator. */
class CounterHaptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    fun buzz(buzz: Buzz) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = when (buzz) {
            Buzz.NONE -> return
            Buzz.TICK -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                else VibrationEffect.createOneShot(15L, 80)
            Buzz.MARK -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                else VibrationEffect.createWaveform(longArrayOf(0, 40, 80, 40), -1)
        }
        v.vibrate(effect)
    }
}

/** True when animations are turned off in Android (Accessibility, remove animations): then nothing moves. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
