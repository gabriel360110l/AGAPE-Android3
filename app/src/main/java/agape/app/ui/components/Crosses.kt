package agape.app.ui.components

import agape.app.core.model.Tradition
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/**
 * The cross of each tradition, drawn as one filled shape in a box of [size]:
 * the Latin cross (Catholic), the three-bar cross with the slanted footrest (Eastern Orthodox; raised on the
 * viewer's left, towards the good thief) and the Coptic cross whose arms end in three knobs, for the Trinity
 * (Oriental Orthodox).
 */
fun crossPath(tradition: Tradition, size: Size): Path {
    val w = size.width
    val h = size.height
    fun rect(x0: Float, y0: Float, x1: Float, y1: Float) = Rect(x0 * w, y0 * h, x1 * w, y1 * h)
    return Path().apply {
        when (tradition) {
            Tradition.CATHOLIC -> {
                addRect(rect(0.43f, 0f, 0.57f, 1f))
                addRect(rect(0.14f, 0.24f, 0.86f, 0.35f))
            }
            Tradition.EASTERN_ORTHODOX -> {
                addRect(rect(0.445f, 0f, 0.555f, 1f))
                addRect(rect(0.32f, 0.11f, 0.68f, 0.18f))
                addRect(rect(0.12f, 0.29f, 0.88f, 0.38f))
                // The footrest: a slanted bar, higher on the viewer's left.
                moveTo(0.24f * w, 0.70f * h)
                lineTo(0.76f * w, 0.79f * h)
                lineTo(0.76f * w, 0.865f * h)
                lineTo(0.24f * w, 0.775f * h)
                close()
            }
            Tradition.ORIENTAL_ORTHODOX -> {
                // An equal-armed cross in a square centred in the box.
                val s = minOf(w, h)
                val ox = (w - s) / 2f
                val oy = (h - s) / 2f
                fun r(x0: Float, y0: Float, x1: Float, y1: Float) = Rect(ox + x0 * s, oy + y0 * s, ox + x1 * s, oy + y1 * s)
                fun dot(cx: Float, cy: Float, radius: Float) = addOval(Rect(Offset(ox + cx * s, oy + cy * s), radius * s))
                addRect(r(0.43f, 0.10f, 0.57f, 0.90f))
                addRect(r(0.10f, 0.43f, 0.90f, 0.57f))
                // Each arm ends in three knobs (a trefoil): tip at (x, y), pointing inwards along (dx, dy).
                listOf(Triple(0.5f, 0.08f, 0 to 1), Triple(0.5f, 0.92f, 0 to -1), Triple(0.08f, 0.5f, 1 to 0), Triple(0.92f, 0.5f, -1 to 0)).forEach { (x, y, d) ->
                    val (dx, dy) = d
                    dot(x, y, 0.075f)
                    dot(x + dx * 0.085f + dy * 0.095f, y + dy * 0.085f + dx * 0.095f, 0.065f)
                    dot(x + dx * 0.085f - dy * 0.095f, y + dy * 0.085f - dx * 0.095f, 0.065f)
                }
            }
        }
    }
}

/** The tradition's cross in gold (or [color]); keep the box about 3:4 for the Latin and Byzantine crosses. */
@Composable
fun TraditionCross(tradition: Tradition, modifier: Modifier = Modifier, color: Color? = null) {
    Canvas(modifier) {
        val path = crossPath(tradition, size)
        if (color != null) drawPath(path, color)
        else drawPath(path, Brush.verticalGradient(listOf(Color(0xFFF3D98B), Color(0xFFD4AF37), Color(0xFFA67C2E))))
    }
}
