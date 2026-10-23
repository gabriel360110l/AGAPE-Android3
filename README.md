# AGAPE — Android

A tradition-aware Bible and prayer app for **Eastern Orthodox**, **Oriental Orthodox** and **Catholic** Christians.
Native Android, Kotlin + Jetpack Compose. English by default, Dutch available.

## Build and run

1. Open the repository root in Android Studio (JDK 17+; the Gradle wrapper uses Gradle 8.14).
2. Optional — real accounts: add your Supabase project to `local.properties` (never committed):
   ```properties
   supabase.url=https://YOUR-PROJECT.supabase.co
   supabase.anonKey=YOUR-ANON-KEY
   google.webClientId=YOUR-WEB-CLIENT-ID.apps.googleusercontent.com
   ```
   Then apply `supabase/migrations/*.sql` to the project and enable the Email and Google providers.
   Without these keys the app uses a **local development account**, so every screen still works.
3. Run the `app` configuration, or `./gradlew :app:assembleDebug`. Unit tests: `./gradlew :app:testDebugUnitTest`.

## Structure

```
app/src/main/java/agape/app/
  core/            models (Tradition, VisualStyle, Passage…), DataStore settings
  data/
    bible/         read-only Bible database (WEB + Statenvertaling, cross-refs, chapter guides, Father notes)
    content/       bundled JSON content (books per canon, prayer books, councils, plans, quizzes, Church section)
    plans/         plan generation, quiz gating, current prayer hour (pure, unit-tested)
    user/          Room database: bookmarks, highlights, notes, progress, streaks, achievements
    auth/ sync/    Supabase auth (email + Google) and progress sync; local dev account fallback
    daily/         saint and readings of the day (orthocal.info, api.coptic.io, calapi), cached offline
    calendar/      Pascha (East and West)
  notifications/   prayer-hour alarms, boot rescheduling, inactivity reminder worker
  ui/              onboarding, guided tour, 5-tab shell (Home · Bible · Prayer · Search · Me) and all screens
  ui/theme/        3 styles × light/dark: Navy + Cream, Classic Sacred, Warm Terracotta
tools/build_content.py   rebuilds assets/bible/bible.db and generated JSON from tools/source/
supabase/migrations/     database schema (user snapshot table, RLS, account deletion)
```

## App flow

Welcome → Terms → Choose Church → Choose style → Sign in / Create account → Guided tour → Home · Bible · Prayer · Search · Me.
The first visit to Prayer explains the hours and asks to enable notifications (prayer times and inactivity reminders).

- **Search** looks through everything offline: references ("John 3:16"), books, verses, hours and prayers, councils,
  Fathers, articles, Father quotes, saints and plans.
- **Calendar** (week strip on Home, full month behind it): feasts per tradition, old-style or Coptic date, saints of the
  day and the days you read or prayed.
- **Bible reader**: the text stays clean. The **Info** button opens the chapter's explanation, how the Fathers read
  it, Father quotes and cross-references, in English and Dutch. Only the quizzes follow the last verse: the four
  Gospels have hand-written questions (4 per chapter); other chapters get 5 questions and every book 20, generated
  from the Bible text (`data/quiz/QuizGenerator.kt`).
- **Saint of the Day** (Home, further down, and the calendar), offline for all three Churches: the Byzantine
  menologion (an English and Dutch text for every day), the Coptic Synaxarium by Coptic date, and the General Roman
  Calendar, with a Byzantine icon where one is bundled and a quiet placeholder otherwise.
- **Prayers**: the Kyrie stays in Greek, Κύριε ἐλέησον, with its meaning shown once beside the 41× counter.
- **Church** (Bible tab): the councils (story, decisions, canons and full texts), Church Fathers and current and
  historical patriarchs and popes.

Deep links: `agape://bible/JHN/3?verse=16`, `agape://prayer/vespers`, `agape://home`.

## Content

| | Source | Licence |
|---|---|---|
| English Bible | World English Bible Classic (with deuterocanon), ebible.org | Public domain |
| Dutch Bible | Statenvertaling 1637 with Apocrypha | Public domain |
| Cross-references | OpenBible.info (votes ≥ 10) | CC BY |
| Agpeya (all hours, English) | api.coptic.io | Check terms before release |
| Agpeya (Dutch fixed prayers) | `tools/source/translations_nl.json` | Own translation — review |
| Horologion: morning/evening rule, Compline, Canons, Communion, Typika | *Orthodox Daily Prayers* (St Tikhon's 1982, Project Gutenberg) via mdws-org/orthodoxprayers | Public domain |
| Council creeds, definitions, anathemas and canons | NPNF II/14 (Percival, 1900) via newadvent.org | Public domain |
| Dutch council texts and canons | `tools/source/council_texts_nl/` | Own translation — review |
| Church Father commentary per verse (31k quotes, pre-1100) | HistoricalChristianFaith/Commentaries-Database | Mostly public-domain translations; licence unasserted — check |
| Chapter commentary (EN + NL): New Testament, Genesis to Esther, Tobit, Judith, 1–2 Maccabees, the wisdom books (Job to Sirach), Isaiah | `tools/source/commentary/*.md` | Own content — review |
| Chapter quizzes, four Gospels (EN + NL) | `tools/source/quizzes/*.md` | Own content — review |
| Coptic Synaxarium (names per day) | api.coptic.io | Check terms before release; Dutch: rules + `tools/source/saints_nl_oo.json` |
| General Roman Calendar (saints per day) | calapi.inadiutorium.cz | Calendar facts; Dutch names in `tools/source/saints_nl.json` |
| Saint and feast icons (73) | Wikimedia Commons, listed with title and licence in `assets/content/icons.json` | Public domain / CC0 only |
| Byzantine menologion: English texts | `tools/source/menologion_en.json` | Own content — review |
| Menologion names and Dutch texts, legacy prayers, provisional chapter guides (OT), Father notes | Legacy AGAPE app | Own content |
| Council stories, Father biographies, Liturgy of the Hours hymns and prayers, plans, quizzes, verse notes | `app/src/main/assets/content/*.json` | Own content (hymns: Neale/Newman, public domain) |

Canon per tradition is defined in `tools/build_content.py` (`BOOKS`). Councils per tradition are in
`assets/content/councils.json` (`recognizedBy`); the council reading plan follows them: 3 (Oriental Orthodox),
7 (Eastern Orthodox) or all 21 (Catholic).

Rebuild generated content from the repository root (downloads are cached in `tools/.cache/`):

```
python tools/build_content.py     # Bible database, cross-references, Father quotes, books; --saints: menologion only
python tools/build_prayers.py     # Agpeya and Horologion (Κύριε ἐλέησον via tools/kyrie.py)
python tools/build_councils.py    # council texts and canons
python tools/build_commentary.py  # chapter commentary from tools/source/commentary/
python tools/build_quizzes.py     # hand-written chapter quizzes from tools/source/quizzes/
python tools/build_saints.py      # Coptic Synaxarium and General Roman Calendar
python tools/build_icons.py       # icons from Wikimedia Commons (needs Pillow); --patterns updates matching only
```

Chapter commentary is written book by book in `tools/source/commentary/<BOOK>.md`. Written so far: the New
Testament; the Law and the historical books from Genesis to Esther, with Tobit, Judith and 1–2 Maccabees; the
wisdom books (Job, Psalms, Proverbs, Ecclesiastes, Song of Songs, Wisdom, Sirach); and Isaiah. The Psalms follow
the Hebrew numbering of the Bible text; the Greek number is noted where the liturgy uses it. The English Esther
carries the Greek additions in place and its commentary mentions them; the Dutch Bible keeps them as a separate
book. Where the Dutch chapter breaks differ (Job 38–41, Ecclesiastes 4–5), the Dutch summary follows the Dutch
text. The other Old Testament books (the Prophets after Isaiah and the remaining deuterocanonical books) still
show the legacy guide, marked in the app as provisional (English only).

## Before release

- Terms and Privacy Policy (`assets/legal/`, EN + NL) are drafts that match what the app does: fill in the parts in
  [square brackets] (publisher name and address, Supabase region, backup period, age limit) and have them
  reviewed by a lawyer. Set the real donation URL in `app/build.gradle.kts`.
- Complete the sample content: Horologion and Liturgy of the Hours texts, verse commentary, Church articles, and
  hand-written quizzes for Acts to Revelation.
- Write the remaining Old Testament chapter commentary (EN + NL): the Prophets after Isaiah (Jeremiah to Malachi,
  with Lamentations, Baruch and Daniel) and the remaining deuterocanonical books. Then have the written commentary,
  the quizzes, the menologion texts, the council translations and the Dutch saint names reviewed.
- Add icons for more saints (each checked for a public-domain or CC0 licence).
- Add a release signing config and a Play Store listing (screenshots per style).

## Related repositories

- Documentation & guidelines: https://github.com/gabriel360110l/EOC-church
- Previous Android version: https://github.com/gabriel360110l/AGAPE-Android2.0
