package agape.app.ui.components

import androidx.compose.runtime.getValue
import agape.app.data.content.IconEntry
import agape.app.ui.theme.AgapeTheme
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An icon in an arched frame, like an icon in its niche. Without a bundled icon it shows a quiet halo and cross in
 * the colours of the current style, so a missing image never looks like an error.
 */
@Composable
fun SaintIcon(icon: IconEntry?, name: String, modifier: Modifier = Modifier, width: Dp = 104.dp) {
    val context = LocalContext.current
    val c = AgapeTheme.colors
    val bitmap by produceState<ImageBitmap?>(null, icon?.file) {
        value = icon?.let { entry ->
            withContext(Dispatchers.IO) {
                runCatching { context.assets.open(entry.file).use { BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull()
            }
        }
    }
    val shape = RoundedCornerShape(topStart = width / 2, topEnd = width / 2, bottomStart = 10.dp, bottomEnd = 10.dp)
    Box(
        modifier.width(width).aspectRatio(0.76f)
            .shadow(10.dp, shape, spotColor = c.primary.copy(alpha = 0.3f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.22f), c.surfaceSunken)))
            .border(1.5.dp, c.accent.copy(alpha = 0.75f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = name, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
        } else {
            Canvas(Modifier.fillMaxSize().padding(width / 5)) {
                val stroke = 2.dp.toPx()
                val r = size.minDimension / 2.6f
                val centre = Offset(size.width / 2, size.height * 0.36f)
                drawCircle(c.accent.copy(alpha = 0.8f), r, centre, style = Stroke(stroke))
                drawLine(c.accent, Offset(centre.x, centre.y - r * 0.55f), Offset(centre.x, size.height * 0.95f), stroke * 1.4f, StrokeCap.Round)
                drawLine(c.accent, Offset(centre.x - r * 0.5f, centre.y), Offset(centre.x + r * 0.5f, centre.y), stroke * 1.4f, StrokeCap.Round)
            }
        }
    }
}

