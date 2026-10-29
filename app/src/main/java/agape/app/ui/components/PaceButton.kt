package agape.app.ui.components

import agape.app.ui.theme.AgapeTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The one way forward while praying on beads or knots: after each step the button fills slowly over [minMillis],
 * and only once it is full can it be pressed. So every bead gets its time and none can be skipped. [step] restarts
 * the filling; [waiting] is shown while it fills.
 */
@Composable
fun PaceButton(text: String, waiting: String, minMillis: Int, step: Any, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = AgapeTheme.colors
    val fill = remember { Animatable(1f) }
    LaunchedEffect(step) {
        fill.snapTo(0f)
        fill.animateTo(1f, tween(minMillis, easing = LinearEasing))
    }
    val ready = enabled && fill.value >= 1f
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.fillMaxWidth().height(58.dp).clip(shape)
            .background(c.primary.copy(alpha = 0.16f))
            .border(1.dp, c.primary.copy(alpha = if (ready) 0.9f else 0.35f), shape)
            .clickable(enabled = ready, onClick = onClick)
            .semantics { if (!ready) disabled() },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fill.value).background(c.primary.copy(alpha = if (ready) 1f else 0.55f)))
        Text(
            if (ready) text else waiting,
            style = MaterialTheme.typography.titleMedium,
            color = if (ready || fill.value > 0.5f) c.onPrimary else c.primary,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
