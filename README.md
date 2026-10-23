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
  ui/              onboarding, guided tour, 5-tab shell (Home · Bible · Calendar · Prayer · Search) and all screens
  ui/theme/        3 styles × light/dark: Navy + Cream, Classic Sacred, Warm Terracotta
tools/build_content.py   rebuilds assets/bible/bible.db and generated JSON from tools/source/
supabase/migrations/     database schema (user snapshot table, RLS, account deletion)
```

## App flow

Welcome → Terms → Choose Church → Choose style → Sign in / Create account → Guided tour → Home · Bible · Calendar · Prayer · Search.
Profile and settings open from the gear on Home, help from the question mark beside it.
The first visit to Prayer explains the hours and asks to enable notifications (prayer times and inactivity reminders).

- **Home**: the verse of the day over the picture of the day (Read · Share), then where you left off, your progress
  (streak, chapters read of the canon, hours prayed), the readings of the day labelled Law and Prophets, Psalm, Gospel and
  Apostle, the Rosary for Catholics, the reading plan, the saint of the day and the week. The tab bar floats over the page.
- **Rosary** (Catholic; Prayer tab, Home, and the Rosary in the prayer book): a rosary of 59 beads, medal and crucifix
  that you pray by tapping the glowing bead. Each bead shows its prayer (Creed, Our Father, Hail Mary, Glory Be,
  O my Jesus, Hail Holy Queen, closing prayer) and each decade its mystery with the passage to read, in English and
  Dutch. The mysteries of the day follow Rosarium Virginis Mariae; any set can be chosen (`data/rosary/Rosary.kt`).
- **Search** looks through everything offline: references ("John 3:16"), books, verses, hours and prayers, councils,
  Fathers, articles, Father quotes, saints and plans.
- **Calendar** (its own tab, and a week strip on Home): feasts per tradition, old-style or Coptic date, saints of the
  day and the days you read or prayed.
- **Bible reader**: the text stays clean. The **Info** button opens the chapter's explanation, how the Fathers read
  it, Father quotes and cross-references, in English and Dutch. Only the quizzes follow the last verse: the whole
  New Testament and the major Old Testament books (Genesis, Exodus, Ruth, Jonah, Jeremiah, Lamentations, Baruch,
  Ezekiel, Daniel) have hand-written questions (4 per chapter); other chapters get 5 questions and every book 20, generated
  from the Bible text (`data/quiz/QuizGenerator.kt`).
- **Saint of the Day** (Home, further down, and the calendar), offline for all three Churches: the Byzantine
  menologion (an English and Dutch text for every day), the Coptic Synaxarium by Coptic date, and the General Roman
  Calendar (a short English and Dutch summary for the first saint of every Coptic day and for every Roman saint),
  with a Byzantine icon where one is bundled and a quiet placeholder otherwise.
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
| Horologion (Dutch for the texts above) | `tools/source/prayers_eo_nl.json` | Own translation — review |
| Horologion: Matins (reader's form) and the Vespers prokeimena | `tools/build_prayers.py` (traditional texts, psalms from the Bible) | Own arrangement and Dutch — review |
| Liturgy of the Hours: Week I psalter, readings, Fathers' second readings | `tools/build_hours_ca.py` (texts from bible.db; Fathers: Lightfoot, ANF, NPNF, Pusey) | Public domain; antiphons, intercessions, prayers and Dutch of the Fathers are own texts — review |
| Dutch council texts and canons | `tools/source/council_texts_nl/` | Own translation — review |
| Church Father commentary per verse (31k quotes, pre-1100) | HistoricalChristianFaith/Commentaries-Database | Mostly public-domain translations; licence unasserted — check |
| Chapter commentary (EN + NL): New Testament, Genesis to Esther, Tobit, Judith, 1–2 Maccabees, the wisdom books (Job to Sirach), Isaiah, Ezekiel | `tools/source/commentary/*.md` | Own content — review |
| Chapter quizzes, New Testament and major Old Testament books (EN + NL) | `tools/source/quizzes/*.md` | Own content — review |
| Coptic Synaxarium (names per day) | api.coptic.io | Check terms before release; Dutch: rules + `tools/source/saints_nl_oo.json` |
| General Roman Calendar (saints per day) | calapi.inadiutorium.cz | Calendar facts; Dutch names in `tools/source/saints_nl.json` |
| Home backgrounds | Painted for AGAPE by `tools/render_backgrounds.py`; real photos can be added with `tools/build_backgrounds.py`, listed in `assets/backgrounds/backgrounds.json` | CC0 (own artwork); photos public domain / CC0 only |
| Rosary prayers (EN + NL) | `data/rosary/Rosary.kt`: traditional texts; the Dutch closing prayer is our own translation | Review |
| Saint and feast icons (73) | Wikimedia Commons, listed with title and licence in `assets/content/icons.json` | Public domain / CC0 only |
| Byzantine menologion: English texts | `tools/source/menologion_en.json` | Own content — review |
| Coptic and Roman saint summaries (EN + NL) | `tools/source/saint_summaries_oo.tsv`, `saint_summaries_ca.tsv` | Own content — review; Coptic lines marked `?` are provisional |
| Menologion names and Dutch texts, legacy prayers, provisional chapter guides (OT), Father notes | Legacy AGAPE app | Own content |
| Council stories, Father biographies, Liturgy of the Hours hymns and prayers, plans, quizzes, verse notes | `app/src/main/assets/content/*.json` | Own content (hymns: Neale/Newman, public domain) |

Canon per tradition is defined in `tools/build_content.py` (`BOOKS`). Councils per tradition are in
`assets/content/councils.json` (`recognizedBy`); the council reading plan follows them: 3 (Oriental Orthodox),
7 (Eastern Orthodox) or all 21 (Catholic).

Rebuild generated content from the repository root (downloads are cached in `tools/.cache/`):

```
python tools/build_content.py     # Bible database, cross-references, Father quotes, books; --saints: menologion only
python tools/build_prayers.py     # Agpeya and Horologion (Κύριε ἐλέησον via tools/kyrie.py)
python tools/build_prayers.py --nl-only   # only fill in the Horologion Dutch, no downloads
python tools/build_hours_ca.py    # Liturgy of the Hours, a full week (offline)
python tools/build_councils.py    # council texts and canons
python tools/build_commentary.py  # chapter commentary from tools/source/commentary/
python tools/build_quizzes.py     # hand-written chapter quizzes from tools/source/quizzes/
python tools/build_saints.py      # Coptic Synaxarium and General Roman Calendar
python tools/build_saint_summaries.py  # adds the saint summaries to saints_oo.json and saints_ca.json (offline)
python tools/build_icons.py       # icons from Wikimedia Commons (needs Pillow); --new adds missing icons, --patterns updates matching only
python tools/render_backgrounds.py   # the painted Home backgrounds (needs Pillow and numpy)
python tools/build_backgrounds.py    # adds public-domain / CC0 church photos from Commons to the Home backgrounds
```

Chapter commentary is written book by book in `tools/source/commentary/<BOOK>.md`. Written so far: the New
Testament; the Law and the historical books from Genesis to Esther, with Tobit, Judith and 1–2 Maccabees; the
wisdom books (Job, Psalms, Proverbs, Ecclesiastes, Song of Songs, Wisdom, Sirach); and Isaiah and Ezekiel. The Psalms follow
the Hebrew numbering of the Bible text; the Greek number is noted where the liturgy uses it. The English Esther
carries the Greek additions in place and its commentary mentions them; the Dutch Bible keeps them as a separate
book. Where the Dutch chapter breaks differ (Job 38–41, Ecclesiastes 4–5), the Dutch summary follows the Dutch
text. The other Old Testament books (Jeremiah, Lamentations, Baruch, Daniel, the twelve minor prophets and the
remaining deuterocanonical books) still show the legacy guide, marked in the app as provisional (English only).

## Before release

- Replace the placeholder Terms and Privacy Policy (`assets/legal/`), donation URL and support e-mail (`app/build.gradle.kts`).
- Complete the sample content: verse commentary and Church articles. (The Horologion now has Dutch throughout and the
  Liturgy of the Hours has a full week; both carry provisional own texts that need review.)
- Write the remaining Old Testament chapter commentary (EN + NL): Jeremiah, Lamentations, Baruch, Daniel, the twelve
  minor prophets (Hosea to Malachi) and the remaining deuterocanonical books. Then have the written commentary,
  the quizzes, the menologion texts, the council translations, the Dutch saint names and the saint summaries
  reviewed: the 145 Coptic summaries marked `?` first, which say only what was safe without the full Synaxarium
  text and should be checked and filled out against it.
- Download the 99 extra saint icons listed in `tools/build_icons.py` with `python tools/build_icons.py --new` (needs
  access to commons.wikimedia.org), then look at each new picture and pin a better file in `PINS` where the search
  chose the wrong saint.
- Add a release signing config and a Play Store listing (screenshots per style).

## Related repositories

- Documentation & guidelines: https://github.com/gabriel360110l/EOC-church
- Previous Android version: https://github.com/gabriel360110l/AGAPE-Android2.0
