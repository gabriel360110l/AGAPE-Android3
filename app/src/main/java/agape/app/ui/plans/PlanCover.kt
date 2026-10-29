package agape.app.ui.plans

import agape.app.data.content.BackgroundEntry
import agape.app.ui.components.BackdropInk
import agape.app.ui.theme.AgapeTheme
import android.app.Activity
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Cream text on a cover: the covers are darkened, whatever the style. */
val OnCover = Color(0xFFF4EEE3)

/**
 * A reading plan's cover picture, darkened towards the bottom so its title can stand on it, as on the cover of a book.
 * [sample] scales the picture down for small tiles, so a row of covers stays light on memory.
 */
@Composable
fun PlanCover(entry: BackgroundEntry?, modifier: Modifier = Modifier, sample: Int = 1, content: @Composable BoxScope.() -> Unit = {}) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, entry?.file, sample) {
        value = entry?.let { e ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    context.assets.open(e.file).use { BitmapFactory.decodeStream(it, null, options)!!.asImageBitmap() }
                }.getOrNull()
            }
        }
    }
    Box(
        modifier.background(
            Brush.linearGradient(listOf(AgapeTheme.colors.accent.copy(alpha = 0.55f), BackdropInk)),
        ),
    ) {
        Crossfade(bitmap, animationSpec = tween(500), label = "cover", modifier = Modifier.matchParentSize()) { image ->
            if (image != null) Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to BackdropInk.copy(alpha = 0.30f),
                    0.35f to BackdropInk.copy(alpha = 0.05f),
                    0.70f to BackdropInk.copy(alpha = 0.55f),
                    1f to BackdropInk.copy(alpha = 0.90f),
                ),
            ),
        )
        content()
    }
}

/** Light status bar icons while a dark cover lies under them; the style's own setting comes back on leaving. */
@Composable
fun LightStatusBarIcons() {
    val view = LocalView.current
    val dark = AgapeTheme.colors.isDark
    DisposableEffect(view, dark) {
        val window = (view.context as? Activity)?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.isAppearanceLightStatusBars = false
        onDispose { insets?.isAppearanceLightStatusBars = !dark }
    }
}
