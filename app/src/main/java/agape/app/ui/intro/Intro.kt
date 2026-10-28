package agape.app.ui.intro

import agape.app.R
import agape.app.core.model.Tradition
import agape.app.ui.components.TraditionCross
import agape.app.ui.onboarding.traditionName
import agape.app.ui.theme.Cinzel
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val Night = Color(0xFF050403)
private val Gold = Color(0xFFD4AF37)
private val PaleGold = Color(0xFFF3D98B)
private val DeepGold = Color(0xFFA67C2E)
private const val LETTERS = "AGAPE"

/**
 * The opening of the app, on every start and after a change of church: golden light breaks out behind the
 * church's own cross, A G A P E is spelled letter by letter, and the church's name with "God is love" follows.
 * A tap skips it; with animations turned off in Android it shows still for a moment.
 */
@Composable
fun AgapeIntro(tradition: Tradition, onDone: () -> Unit) {
    val context = LocalContext.current
    val still = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val light = remember { Animatable(if (still) 1f else 0f) }
    val cross = remember { Animatable(if (still) 1f else 0f) }
    val letters = remember { List(LETTERS.length) { Animatable(if (still) 1f else 0f) } }
    val words = remember { Animatable(if (still) 1f else 0f) }
    val leave = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        if (!still) coroutineScope {
            listOf(
                async { light.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) },
                async { delay(250); cross.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) },
                async { letters.forEachIndexed { i, a -> delay(if (i == 0) 900L else 150L); async { a.animateTo(1f, tween(520)) } } },
                async { delay(1900); words.animateTo(1f, tween(700)) },
            ).awaitAll()
            delay(900)
        } else delay(1200)
        leave.animateTo(0f, tween(450))
        onDone()
    }

    val spin = rememberInfiniteTransition(label = "rays")
    val turn by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "turn")
    val breathe by spin.animateFloat(0.94f, 1.04f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")

    val church = stringResource(traditionName(tradition)).uppercase()
    val subtitle = stringResource(R.string.intro_subtitle, church)
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = leave.value }.background(Night)
            .clickable(remember { MutableInteractionSource() }, indication = null) { onDone() }
            .semantics { contentDescription = "AGAPE · $subtitle" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.40f)
            val reach = maxOf(size.width, size.height) * 0.75f
            val l = light.value
            // Warm glow behind everything.
            drawCircle(
                Brush.radialGradient(listOf(Gold.copy(alpha = 0.34f * l), DeepGold.copy(alpha = 0.10f * l), Color.Transparent), center, reach * 0.62f * breathe),
                reach, center,
            )
            // Rays: long and short in turn, slowly turning.
            rotate(turn, center) {
                val count = 24
                for (i in 0 until count) {
                    val a = i * 2 * PI / count
                    val len = reach * (if (i % 2 == 0) 1f else 0.62f) * (0.35f + 0.65f * l)
                    val half = if (i % 2 == 0) 0.045 else 0.028
                    val ray = Path().apply {
                        moveTo(center.x, center.y)
                        lineTo(center.x + (cos(a - half) * len).toFloat(), center.y + (sin(a - half) * len).toFloat())
                        lineTo(center.x + (cos(a + half) * len).toFloat(), center.y + (sin(a + half) * len).toFloat())
                        close()
                    }
                    drawPath(ray, Brush.radialGradient(listOf(PaleGold.copy(alpha = 0.30f * l), Gold.copy(alpha = 0.06f * l), Color.Transparent), center, len))
                }
            }
            // The halo.
            val r = min(size.width, size.height) * 0.20f
            drawCircle(Brush.radialGradient(listOf(PaleGold.copy(alpha = 0.22f * l), Color.Transparent), center, r * 1.3f), r * 1.3f, center)
            drawCircle(Gold.copy(alpha = 0.75f * cross.value), r, center, style = Stroke(1.6.dp.toPx()))
            drawCircle(Gold.copy(alpha = 0.35f * cross.value), r * 1.12f, center, style = Stroke(0.8.dp.toPx()))
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Same geometry as the canvas: the cross sits on the glow's centre, the name below the halo.
            val crossH = if (tradition == Tradition.ORIENTAL_ORTHODOX) 84.dp else 112.dp
            val middle = maxHeight * 0.40f
            val halo = min(maxWidth.value, maxHeight.value).dp * 0.20f
            TraditionCross(
                tradition,
                Modifier.align(Alignment.TopCenter).offset(y = middle - crossH / 2).size(width = 84.dp, height = crossH)
                    .graphicsLayer {
                        alpha = cross.value
                        val k = 0.82f + 0.18f * cross.value
                        scaleX = k; scaleY = k
                    },
            )
            Column(
                Modifier.align(Alignment.TopCenter).offset(y = middle + halo * 1.12f + 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LETTERS.forEachIndexed { i, ch ->
                        val a = letters[i].value
                        Text(
                            ch.toString(),
                            style = TextStyle(
                                fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 44.sp,
                                brush = Brush.verticalGradient(listOf(PaleGold, Gold, DeepGold)),
                            ),
                            modifier = Modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 18.dp.toPx() },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    subtitle,
                    style = TextStyle(fontFamily = Cinzel, fontSize = 12.sp, letterSpacing = 0.28.em, color = PaleGold.copy(alpha = 0.85f)),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp).graphicsLayer { alpha = words.value },
                )
            }
        }
    }
}
