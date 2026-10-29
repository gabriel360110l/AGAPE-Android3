package agape.app.ui.plans

import agape.app.R
import agape.app.core.model.Passage
import agape.app.data.content.PlanCategory
import agape.app.data.content.PlanDefinition
import agape.app.data.plans.PlanDay
import agape.app.data.plans.PlanEngine
import agape.app.data.plans.planDays
import agape.app.data.plans.quizKeyFor
import agape.app.data.user.PlanProgress
import agape.app.data.user.planNoteBook
import agape.app.ui.LocalContainer
import agape.app.ui.LocalLanguage
import agape.app.ui.church.CouncilBody
import agape.app.ui.components.AgapeCard
import agape.app.ui.components.DetailTopBar
import agape.app.ui.components.Kicker
import agape.app.ui.components.PrimaryButton
import agape.app.ui.components.ProgressLine
import agape.app.ui.components.QuietButton
import agape.app.ui.components.SecondaryButton
import agape.app.ui.components.cardShape
import agape.app.ui.passageLabel
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tradition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun categoryName(c: PlanCategory) = when (c) {
    PlanCategory.BEGINNER -> R.string.plans_beginner
    PlanCategory.OT_NT -> R.string.plans_ot_nt
    PlanCategory.FULL -> R.string.plans_full
    PlanCategory.COUNCILS -> R.string.plans_councils
}

/** The plans the reader follows on top, then every plan as a cover in a row per kind, as on a bookshelf. */
@Composable
fun PlansScreen(onBack: () -> Unit, openPlan: (String) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val progress by container.user.plans.collectAsStateWithLifecycle(emptyList())
    val grouped = remember { container.content.plans.groupBy { it.category } }
    val mine = progress.mapNotNull { p -> container.content.plan(p.planId)?.let { it to p } }.sortedByDescending { it.second.active }
    Column(Modifier.fillMaxSize()) {
        DetailTopBar(stringResource(R.string.plans_title), onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            if (mine.isNotEmpty()) {
                item { Kicker(stringResource(R.string.plans_mine), Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp)) }
                items(mine, key = { "mine:" + it.first.id }) { (plan, p) ->
                    val days = remember(plan, tradition, lang) { container.content.planDays(plan, tradition, lang) }
                    MyPlanCard(plan, p, days.size, Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) { openPlan(plan.id) }
                }
            }
            PlanCategory.entries.forEach { cat ->
                val list = grouped[cat].orEmpty()
                if (list.isEmpty()) return@forEach
                item(key = "cat:$cat") {
                    Kicker(stringResource(categoryName(cat)), Modifier.padding(start = 20.dp, top = 22.dp, bottom = 10.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(list, key = { it.id }) { plan ->
                            PlanTile(plan, progress.firstOrNull { it.planId == plan.id }) { openPlan(plan.id) }
                        }
                    }
                }
            }
        }
    }
}

/** A plan as a cover: picture, title on it, and below a line on what it reads. */
@Composable
private fun PlanTile(plan: PlanDefinition, progress: PlanProgress?, onClick: () -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val c = AgapeTheme.colors
    val cover = remember(plan.id) { container.backgrounds.forPlan(plan.id) }
    val length = remember(plan, tradition, lang) { container.content.planDays(plan, tradition, lang).size }
    Column(Modifier.width(232.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        PlanCover(cover, Modifier.fillMaxWidth().aspectRatio(1.35f).clip(RoundedCornerShape(18.dp)), sample = 2) {
            Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                when {
                    progress?.active == true -> Badge(stringResource(R.string.plans_active))
                    progress != null -> Icon(Icons.Outlined.CheckCircle, stringResource(R.string.plan_finished), tint = OnCover)
                    plan.locked -> Icon(Icons.Outlined.Lock, stringResource(R.string.plans_with_quizzes), tint = OnCover.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text(plan.title[lang], style = MaterialTheme.typography.titleLarge, color = OnCover, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.plan_length, length), style = MaterialTheme.typography.labelMedium, color = OnCover.copy(alpha = 0.8f))
            }
        }
        Text(
            plan.description[lang], style = MaterialTheme.typography.bodySmall, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = Color(0xFF1A1512),
        modifier = Modifier.clip(CircleShape).background(OnCover).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** A plan the reader has started: a small cover, where they are, and how far they have come. */
@Composable
fun MyPlanCard(plan: PlanDefinition, progress: PlanProgress, totalDays: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    val cover = remember(plan.id) { container.backgrounds.forPlan(plan.id) }
    val next = PlanEngine.nextDay(totalDays, progress.completedDays)
    AgapeCard(modifier, onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlanCover(cover, Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)), sample = 4)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(plan.title[lang], style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (next != null) stringResource(R.string.plan_day_of, next, totalDays) else stringResource(R.string.plan_finished),
                    style = MaterialTheme.typography.bodySmall, color = c.inkMuted,
                )
                Spacer(Modifier.height(8.dp))
                ProgressLine(progress.completedDays.size / totalDays.coerceAtLeast(1).toFloat())
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.inkFaint)
        }
    }
}

@Composable
fun PlanScreen(planId: String, onBack: () -> Unit, openDay: (String, Int) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val scope = rememberCoroutineScope()
    val plan = remember(planId) { container.content.plan(planId) } ?: return
    val days = remember(plan, tradition, lang) { container.content.planDays(plan, tradition, lang) }
    val all by container.user.plans.collectAsStateWithLifecycle(emptyList())
    val notes by container.user.notes.collectAsStateWithLifecycle(emptyList())
    val dayNotes = remember(notes, planId) { notes.filter { it.book == planNoteBook(planId) }.associate { it.chapter to it.text } }
    val progress = all.firstOrNull { it.planId == planId }
    val done = progress?.completedDays.orEmpty()
    val next = if (progress != null) PlanEngine.nextDay(days.size, done) else null
    val cover = remember(planId) { container.backgrounds.forPlan(planId) }
    var confirmStop by remember { mutableStateOf(false) }
    val c = AgapeTheme.colors
    LightStatusBarIcons()

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.plan_stop_title)) },
            text = { Text(stringResource(R.string.plan_stop_text)) },
            confirmButton = { TextButton({ confirmStop = false; scope.launch { container.user.stopPlan(planId) } }) { Text(stringResource(R.string.plan_stop), color = c.danger) } },
            dismissButton = { TextButton({ confirmStop = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        // The cover, with the title on it.
        item {
            PlanCover(cover, Modifier.fillMaxWidth().height(320.dp)) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 6.dp, top = 4.dp)) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), tint = OnCover)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Kicker(stringResource(categoryName(plan.category)), color = OnCover.copy(alpha = 0.8f))
                        Spacer(Modifier.height(6.dp))
                        Text(plan.title[lang], style = MaterialTheme.typography.displaySmall, color = OnCover, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.plan_length, days.size) + if (plan.locked) " · " + stringResource(R.string.plans_with_quizzes) else "",
                            style = MaterialTheme.typography.labelLarge, color = OnCover.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(plan.description[lang], style = MaterialTheme.typography.bodyLarge, color = c.inkMuted)
                if (progress != null) {
                    val share = done.size / days.size.coerceAtLeast(1).toFloat()
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(R.string.plan_progress, done.size, days.size), style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.plan_percent, (share * 100).roundToInt()), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                    }
                    ProgressLine(share)
                    if (next != null) PrimaryButton(stringResource(R.string.plan_continue_day, next), { openDay(planId, next) }, Modifier.fillMaxWidth())
                    else Text(stringResource(R.string.plan_finished), style = MaterialTheme.typography.titleMedium, color = c.primary)
                } else {
                    PrimaryButton(stringResource(R.string.plan_start), {
                        scope.launch { container.user.startPlan(planId) }
                        openDay(planId, 1)
                    }, Modifier.fillMaxWidth())
                }
            }
        }
        item { Kicker(stringResource(R.string.plan_days_title), Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)) }
        items(days, key = { it.number }) { day ->
            val unlocked = progress != null && PlanEngine.isUnlocked(plan, day.number, done)
            DayRow(day, done = day.number in done, isNext = day.number == next, unlocked = unlocked, note = dayNotes[day.number]) { openDay(planId, day.number) }
        }
        if (progress != null) item { QuietButton(stringResource(R.string.plan_stop), { confirmStop = true }, Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) }
        cover?.let { item { CoverCredit(it.title, it.licence) } }
    }
}

/** One day of a plan: its number in a ring that fills when the day is done, the readings, and the reader's note. */
@Composable
private fun DayRow(day: PlanDay, done: Boolean, isNext: Boolean, unlocked: Boolean, note: String?, onClick: () -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val c = AgapeTheme.colors
    Row(
        Modifier.fillMaxWidth()
            .then(if (unlocked) Modifier.clickable(onClick = onClick) else Modifier)
            .background(if (isNext) c.accent.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(if (done) c.accent else Color.Transparent)
                .border(1.5.dp, when { done -> c.accent; isNext -> c.primary; else -> c.line }, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                done -> Icon(Icons.Outlined.Check, stringResource(R.string.plan_finished), tint = c.onAccent, modifier = Modifier.size(20.dp))
                !unlocked && !isNext && day.number > 1 -> Icon(Icons.Outlined.Lock, null, tint = c.inkFaint, modifier = Modifier.size(16.dp))
                else -> Text(day.number.toString(), style = MaterialTheme.typography.titleSmall, color = if (isNext) c.primary else c.inkMuted)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    day.title?.get(lang) ?: stringResource(R.string.plan_day, day.number),
                    style = MaterialTheme.typography.titleMedium, color = if (unlocked || done) c.ink else c.inkMuted,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isNext) {
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.plan_next_badge), style = MaterialTheme.typography.labelSmall, color = c.primary)
                }
            }
            Text(
                day.readings.joinToString(" · ") { container.passageLabel(it, lang) },
                style = MaterialTheme.typography.bodySmall, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (!note.isNullOrBlank()) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.EditNote, stringResource(R.string.plan_note_has), tint = c.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(note, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (unlocked) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.inkFaint)
    }
}

@Composable
private fun CoverCredit(title: String, licence: String) {
    Text(
        stringResource(R.string.plan_cover_credit, title, licence),
        style = MaterialTheme.typography.labelSmall, color = AgapeTheme.colors.inkFaint, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
fun PlanDayScreen(planId: String, day: Int, onBack: () -> Unit, openPassage: (Passage) -> Unit, openQuiz: (String, String) -> Unit) {
    val container = LocalContainer.current
    val lang = LocalLanguage.current
    val tradition = tradition
    val scope = rememberCoroutineScope()
    val plan: PlanDefinition = remember(planId) { container.content.plan(planId) } ?: return
    val days = remember(plan, tradition, lang) { container.content.planDays(plan, tradition, lang) }
    val planDay = days.firstOrNull { it.number == day } ?: return
    val all by container.user.plans.collectAsStateWithLifecycle(emptyList())
    val progress = all.firstOrNull { it.planId == planId }
    val quizKey = remember(planDay) { container.content.quizKeyFor(plan, planDay) }
    val quizzes by container.user.quizResults.collectAsStateWithLifecycle(emptyList())
    val quizPassed = quizKey != null && quizzes.any { it.key == quizKey && it.passed }
    val needsQuiz = plan.locked && quizKey != null && !quizPassed
    val done = progress?.completedDays?.contains(day) == true
    val title = planDay.title?.get(lang) ?: stringResource(R.string.plan_day, day)
    val council = planDay.councilId?.let { container.content.council(it) }
    val notes by container.user.notes.collectAsStateWithLifecycle(emptyList())
    val savedNote = notes.firstOrNull { it.book == planNoteBook(planId) && it.chapter == day }?.text.orEmpty()
    var noteText by rememberSaveable(planId, day) { mutableStateOf<String?>(null) }
    // The field starts from the stored note once it has loaded, and keeps what the reader types from then on.
    LaunchedEffect(savedNote) { if (noteText == null) noteText = savedNote }
    val noteLabel = "${plan.title[lang]} · $title"
    suspend fun saveNote() { noteText?.let { t -> if (t.trim() != savedNote) container.user.savePlanNote(planId, day, t, noteLabel) } }
    // Save first and leave after, so the note is written before this screen and its scope go away.
    fun saveThen(next: () -> Unit) { scope.launch { saveNote(); next() } }
    val c = AgapeTheme.colors

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(stringResource(R.string.plan_day_of, day, days.size), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(plan.title[lang], style = MaterialTheme.typography.labelLarge, color = c.accent)
            if (council == null) Text(title, style = MaterialTheme.typography.headlineMedium, color = c.ink)
            ProgressLine(day / days.size.coerceAtLeast(1).toFloat())
            if (council != null) CouncilBody(council, openPassage)
            else AgapeCard(contentPadding = PaddingValues(0.dp)) {
                Kicker(stringResource(R.string.plan_readings), Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 4.dp))
                planDay.readings.forEachIndexed { i, p ->
                    if (i > 0) Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(0.5.dp).background(c.line))
                    Row(
                        Modifier.fillMaxWidth().clickable { openPassage(p) }.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(container.passageLabel(p, lang), style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.inkFaint)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // The reader's own reflection on the day, as in a journal.
            AgapeCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.EditNote, null, tint = c.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Kicker(stringResource(R.string.plan_note_title))
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.plan_note_hint), style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = noteText.orEmpty(), onValueChange = { noteText = it },
                    placeholder = { Text(stringResource(R.string.plan_note_placeholder)) },
                    modifier = Modifier.fillMaxWidth(), shape = cardShape, minLines = 4,
                )
                Spacer(Modifier.height(8.dp))
                val current = noteText.orEmpty().trim()
                if (current != savedNote) SecondaryButton(stringResource(R.string.save), { scope.launch { saveNote() } })
                else if (savedNote.isNotEmpty()) Text("✓ " + stringResource(R.string.plan_note_saved), style = MaterialTheme.typography.labelMedium, color = c.primary)
            }

            Spacer(Modifier.height(4.dp))
            when {
                done -> Text(stringResource(R.string.plan_day_done), style = MaterialTheme.typography.titleMedium, color = c.primary)
                progress == null -> Text(stringResource(R.string.plan_not_started), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
                needsQuiz -> {
                    Text(stringResource(R.string.plan_quiz_required), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
                    PrimaryButton(stringResource(R.string.reader_take_quiz), { saveThen { openQuiz(quizKey!!, title) } })
                }
                else -> {
                    PrimaryButton(stringResource(R.string.plan_complete_day), {
                        scope.launch {
                            saveNote()
                            container.user.completePlanDay(progress, day, days.size, "${plan.title[lang]} · $title")
                            onBack()
                        }
                    })
                    if (quizKey != null && !quizPassed) SecondaryButton(stringResource(R.string.plan_optional_quiz), { saveThen { openQuiz(quizKey, title) } })
                }
            }
        }
    }
}
