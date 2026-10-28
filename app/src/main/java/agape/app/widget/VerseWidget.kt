package agape.app.widget

import agape.app.MainActivity
import agape.app.R
import agape.app.container
import agape.app.core.model.Tradition
import agape.app.core.model.Passage
import agape.app.ui.passageLabel
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The verse of the day on the home screen; the same verse as on Home, a tap opens it in the reader. */
class VerseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                val views = render(context)
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Redraws every placed widget, for instance after a change of church or language. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, VerseWidget::class.java))
            if (ids.isEmpty()) return
            scope.launch { render(context).let { v -> ids.forEach { manager.updateAppWidget(it, v) } } }
        }

        private suspend fun render(context: Context): RemoteViews {
            val container = context.container
            val tradition = container.settings.current().tradition ?: Tradition.EASTERN_ORTHODOX
            val lang = container.language()
            val ref = Passage.parse(container.content.verseOfDay(tradition, LocalDate.now()))
            val text = ref?.let { runCatching { container.bible.passage(it, lang).joinToString(" ") { v -> v.text } }.getOrNull() }
            val views = RemoteViews(context.packageName, R.layout.widget_verse)
            views.setTextViewText(R.id.widget_kicker, context.getString(R.string.widget_kicker))
            views.setTextViewText(R.id.widget_verse, text?.let { "“${it.trim(' ', '“', '”', '"')}”" } ?: context.getString(R.string.widget_description))
            views.setTextViewText(R.id.widget_reference, ref?.let { container.passageLabel(it, lang) }.orEmpty())
            val uri = ref?.let { Uri.parse("agape://bible/${it.book}/${it.chapter}?verse=${it.startVerse ?: 0}") } ?: Uri.parse("agape://home")
            val open = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            return views
        }
    }
}
