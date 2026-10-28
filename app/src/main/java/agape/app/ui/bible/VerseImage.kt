package agape.app.ui.bible

import agape.app.R
import agape.app.core.model.Tradition
import agape.app.ui.components.Kicker
import agape.app.ui.components.Pill
import agape.app.ui.components.PrimaryButton
import agape.app.ui.components.TraditionCross
import agape.app.ui.components.crossPath
import agape.app.ui.theme.AgapeTheme
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import java.io.File

/** The looks a verse card can have: background gradient, text and gold. */
private enum class CardStyle(val label: Int, val top: Color, val bottom: Color, val text: Color, val gold: Color) {
    NIGHT(R.string.card_style_night, Color(0xFF0E1726), Color(0xFF1C2D48), Color(0xFFF9F5EB), Color(0xFFD4AF37)),
    PARCHMENT(R.string.card_style_parchment, Color(0xFFF7F0E2), Color(0xFFE6D9BF), Color(0xFF281E15), Color(0xFF8C5E32)),
    OLIVE(R.string.card_style_olive, Color(0xFF1A2922), Color(0xFF2B4136), Color(0xFFF4F1EA), Color(0xFFC8A96A)),
}

/** One verse as a picture to share: a preview, three looks, and the share button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerseImageSheet(text: String, label: String, tradition: Tradition, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = AgapeTheme.colors
    var style by rememberSaveable { mutableStateOf(CardStyle.NIGHT) }
    val quoted = remember(text) { "“${text.trim()}”" }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 22.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Kicker(stringResource(R.string.verse_image_title))
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Brush.verticalGradient(listOf(style.top, style.bottom)))
                    .border(1.5.dp, style.gold.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 26.dp, vertical = 30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TraditionCross(tradition, Modifier.size(width = 24.dp, height = 32.dp), color = style.gold)
                    Text(
                        quoted,
                        style = AgapeTheme.type.scripture.copy(fontSize = if (quoted.length > 260) 17.sp else 20.sp, lineHeight = if (quoted.length > 260) 26.sp else 30.sp, fontStyle = FontStyle.Italic),
                        color = style.text, textAlign = TextAlign.Center,
                    )
                    Box(Modifier.width(42.dp).height(1.dp).background(style.gold.copy(alpha = 0.7f)))
                    Text(label.uppercase(), style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.8.sp), color = style.gold)
                    Text("A G A P E", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp), color = style.text.copy(alpha = 0.55f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardStyle.entries.forEach { s -> Pill(stringResource(s.label), style == s, { style = s }) }
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton(stringResource(R.string.verse_image_share), {
                shareCard(context, quoted, label, tradition, style)
                onDismiss()
            })
        }
    }
}

/** Draws the card at 1080 px wide and hands it to the share sheet, with the verse as text for apps without images. */
private fun shareCard(context: Context, quoted: String, label: String, tradition: Tradition, style: CardStyle) {
    val width = 1080
    val side = 120
    val serif = runCatching { ResourcesCompat.getFont(context, R.font.eb_garamond) }.getOrNull() ?: Typeface.SERIF
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.text.toArgb()
        textSize = if (quoted.length > 280) 44f else 54f
        typeface = Typeface.create(serif, Typeface.ITALIC)
    }
    val layout = StaticLayout.Builder.obtain(quoted, 0, quoted.length, textPaint, width - side * 2)
        .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(12f, 1.12f).build()
    val crossW = 72f
    val crossH = 96f
    val block = crossH + 56f + layout.height + 80f + 40f
    val height = (block + 360f).toInt().coerceAtLeast(1080)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, height.toFloat(), style.top.toArgb(), style.bottom.toArgb(), Shader.TileMode.CLAMP)
    })
    canvas.drawRoundRect(RectF(48f, 48f, width - 48f, height - 48f), 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.gold.copy(alpha = 0.45f).toArgb()
        this.style = Paint.Style.STROKE
        strokeWidth = 3f
    })

    var y = (height - block) / 2f
    val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.gold.toArgb() }
    canvas.save()
    canvas.translate((width - crossW) / 2f, y)
    canvas.drawPath(crossPath(tradition, Size(crossW, crossH)).asAndroidPath(), gold)
    canvas.restore()
    y += crossH + 56f

    canvas.save()
    canvas.translate(side.toFloat(), y)
    layout.draw(canvas)
    canvas.restore()
    y += layout.height + 56f

    canvas.drawRect(width / 2f - 40f, y, width / 2f + 40f, y + 2f, gold)
    y += 64f
    canvas.drawText(label.uppercase(), width / 2f, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.gold.toArgb()
        textSize = 34f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        letterSpacing = 0.14f
    })
    canvas.drawText("A G A P E", width / 2f, height - 96f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.text.copy(alpha = 0.55f).toArgb()
        textSize = 26f
        textAlign = Paint.Align.CENTER
        typeface = serif
        letterSpacing = 0.35f
    })

    runCatching {
        val file = File(File(context.cacheDir, "shared").apply { mkdirs() }, "agape_verse.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "$quoted\n— $label")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, label))
    }
    bitmap.recycle()
}
