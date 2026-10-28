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
tools/schema/            uniform saints/feasts schema for the three traditions, with converted data and examples
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
- **Calendar** (its own tab, and a week strip on Home): feasts per tradition and the days you read or prayed. A
  selected day shows its date in the Gregorian, Julian (old style) and Coptic calendars, every commemoration of the
  tradition with its icon and life (longer lives fold open), and the readings: the Roman lectionary from the bundle
  (2025–2027), the Byzantine readings from orthocal.info when online. A reading opens in the Bible reader.
- **Bible reader**: the text stays clean. The **Info** button opens the chapter's explanation, how the Fathers read
  it, Father quotes and cross-references, in English and Dutch. Only the quizzes follow the last verse: every
  chapter of all 82 books, Old and New Testament with the deuterocanon (Psalms, Sirach, Wisdom, the Maccabees,
  1 Esdras, the Prayer of Manasseh, Psalm 151 and the additions to Esther and Daniel included), has hand-written
  questions (4 per chapter, the Esther quiz keeps to the text both Bibles share); the book quiz of 20 questions
  is still generated from the Bible text (`data/quiz/QuizGenerator.kt`).
- **Saint of the Day** (Home, further down, and the calendar), offline for all three Churches from one uniform
  dataset (`assets/content/saints_unified.json`, schema in `tools/schema/`): the Byzantine
  menologion (an English and Dutch text for every day), the Coptic Synaxarium by Coptic date, and the General Roman
  Calendar (a short English and Dutch summary for the first saint of every Coptic day and for every Roman saint),
  with a Byzantine icon where one is bundled and a quiet placeholder otherwise.
- **Prayers**: the Kyrie stays in Greek, Κύριε ἐλέησον, with its meaning shown once beside the 41× counter.
- **Opening** (`ui/intro/Intro.kt`): on every start of the app and after a change of church, golden rays break
  out behind the church's own cross (Latin, Byzantine three-bar, Coptic cross bottony, all drawn in
  `ui/components/Crosses.kt`), A G A P E is spelled letter by letter and "<CHURCH> · GOD IS LOVE" follows. A tap
  skips it; with Android animations off it shows still.
- **Fast or feast** (`data/calendar/Fasting.kt`, tested): every day on Home, in the week strip and on each calendar
  day says whether it is a fast day, a strict fast, a day without meat, fast-free or an ordinary day, and why
  (Great Lent, Wednesday, Bright Week, Apostles' Fast, Paramoun, Ash Wednesday …), with a short explanation per
  church. It is a general guideline: the Byzantine fixed fasts follow the new calendar like the feasts, the Coptic
  rules follow the Coptic seasons, the Catholic rules follow canons 1250–1253.
- **Prayer ropes** (Prayer tab, open to everyone, own church first): the Rosary, the chotki (33, 50 or 100 knots,
  Jesus Prayer, a bead after every ten) and the rope of 41 Kyrie eleison of the Agpeya. Each knot gives a light
  tick, every tenth a firmer one; the prayer can be said aloud at each knot.
- **My day of prayer** (Prayer tab, and Settings → notifications): own time per hour, or hours that follow the
  sun (sunrise and sunset computed on the phone from a chosen city or the coarse location, nothing is sent out);
  own prayer moments; a church bell for prayer notifications (synthesized by `tools/build_bell.py`, CC0, instead of the bell of unknown origin in the AI Studio version); quiet
  mode (screen stays on, Do Not Disturb with priority only while praying, if allowed).
- **Remembered in prayer**: names of the living and the departed and thanksgivings, read at the end of every hour.
- **Read aloud** (`data/audio/Speaker.kt`): chapters (with the verse being read highlighted and an audio bar),
  hours, prayers and saints, with the phone's own voices.
- **Bible extras**: quick book and chapter picker from the reader's title, share a verse as an image (three
  card styles), mark a chapter as unread; search ignores accents and puts the Gospels first.
- **Widget**: the verse of the day on the home screen; a tap opens it in the reader.
- **Church** (Bible tab): the councils (story, decisions, canons and full texts), Church Fathers and current and
  historical patriarchs and popes.

Deep links: `agape://bible/JHN/3?verse=16`, `agape://prayer`, `agape://prayer/vespers`, `agape://home`.

Features from the AI Studio version (`google-studio-bijbel2.0`) were rebuilt here instead of copied: no
destructive database migration (bookmarks and notes stay), the phone's own time zone, sun times computed offline
instead of a weather service, and no build script that downloads or deletes files.

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
| Key decrees of councils 9–21 (Lateran I to Vatican II), EN + NL | `tools/source/council_texts_later/` | Own provisional renderings from the Latin, marked as such in the app — check against the originals (Waterworth 1848 for Trent; vatican.va) |
| Church Father commentary per verse (31k quotes, pre-1100) | HistoricalChristianFaith/Commentaries-Database | Mostly public-domain translations; licence unasserted — check |
| Chapter commentary (EN + NL): New Testament, Genesis to Esther, Tobit, Judith, 1–2 Maccabees, the wisdom books (Job to Sirach), Isaiah, Jeremiah, Lamentations, Baruch, Ezekiel, Daniel, the twelve minor prophets, and the other deuterocanonical books (Prayer of Manasseh, Psalm 151, 1 Esdras, 3–4 Maccabees, and the Greek additions to Esther and Daniel as Dutch separate books) | `tools/source/commentary/*.md` | Own content — review |
| Chapter quizzes, all 82 books (EN + NL) | `tools/source/quizzes/*.md` | Own content — review |
| Coptic Synaxarium (names per day) | api.coptic.io | Check terms before release; Dutch: rules + `tools/source/saints_nl_oo.json` |
| General Roman Calendar (saints per day) | calapi.inadiutorium.cz | Calendar facts; Dutch names in `tools/source/saints_nl.json` |
| Home backgrounds | Painted for AGAPE by `tools/render_backgrounds.py`; real photos can be added with `tools/build_backgrounds.py`, listed in `assets/backgrounds/backgrounds.json` | CC0 (own artwork); photos public domain / CC0 only |
| Rosary prayers (EN + NL) | `data/rosary/Rosary.kt`: traditional texts; the Dutch closing prayer is our own translation | Review |
| Catholic Mass readings per date, 2025–2027 (citations only, no Bible text) | github.com/cpbjr/catholic-readings-api (lectionary of the United States), in `saints_unified.json` | MIT |
| Saint and feast icons (73) | Wikimedia Commons, listed with title and licence in `assets/content/icons.json` | Public domain / CC0 only |
| Byzantine menologion: English texts | `tools/source/menologion_en.json` | Own content — review |
| Coptic and Roman saint summaries (EN + NL) | `tools/source/saint_summaries_oo.tsv`, `saint_summaries_ca.tsv` | Own content — review; Coptic lines marked `?` are provisional |
| Menologion names and Dutch texts, legacy prayers, provisional chapter guides (OT), Father notes | Legacy AGAPE app | Own content |
| Verse notes (EN + NL, per tradition where it differs): best-known verses of every book of the Bible, including the deuterocanon and the books only one translation carries | `tools/source/verse_notes/*.md` | Own content — review |
| Council stories, Father biographies, Liturgy of the Hours hymns and prayers, plans, quizzes | `app/src/main/assets/content/*.json` | Own content (hymns: Neale/Newman, public domain) |

Canon per tradition is defined in `tools/build_content.py` (`BOOKS`). Councils per tradition are in
`assets/content/councils.json` (`recognizedBy`); the council reading plan follows them: 3 (Oriental Orthodox),
7 (Eastern Orthodox) or all 21 (Catholic).

Rebuild generated content from the repository root (downloads are cached in `tools/.cache/`):

```
python tools/build_content.py     # Bible database, cross-references, Father quotes, books; --saints: menologion only
python tools/build_prayers.py     # Agpeya and Horologion (Κύριε ἐλέησον via tools/kyrie.py)
python tools/build_prayers.py --nl-only   # only fill in the Horologion Dutch, no downloads
python tools/build_hours_ca.py    # Liturgy of the Hours, a full week (offline)
python tools/build_councils.py    # council texts and canons; --later merges only councils 9–21 (offline)
python tools/build_commentary.py  # chapter commentary from tools/source/commentary/
python tools/build_quizzes.py     # hand-written chapter quizzes from tools/source/quizzes/
python tools/build_verse_notes.py # verse notes (verse sheet) from tools/source/verse_notes/
python tools/build_church.py      # Church articles (history and per tradition) from tools/source/church/
python tools/build_saints.py      # Coptic Synaxarium and General Roman Calendar
python tools/build_saint_summaries.py  # adds the saint summaries to saints_oo.json and saints_ca.json (offline)
python tools/build_icons.py       # icons from Wikimedia Commons (needs Pillow); --new adds missing icons, --patterns updates matching only
python tools/render_backgrounds.py   # the painted Home backgrounds (needs Pillow and numpy)
python tools/build_backgrounds.py    # adds public-domain / CC0 church photos from Commons to the Home backgrounds
python tools/build_unified_saints.py  # all saints in one schema, for the app and tools/schema/; rerun after saints_*.json or icons.json change
```

Chapter commentary is written book by book in `tools/source/commentary/<BOOK>.md`. Written so far: the New
Testament; the Law and the historical books from Genesis to Esther, with Tobit, Judith and 1–2 Maccabees; the
wisdom books (Job, Psalms, Proverbs, Ecclesiastes, Song of Songs, Wisdom, Sirach); and Isaiah, Jeremiah,
Lamentations, Baruch, Ezekiel and Daniel; the twelve minor prophets, Hosea to Malachi; and the other
deuterocanonical books: the Prayer of Manasseh, Psalm 151, 1 Esdras, 3 and 4 Maccabees, and the Greek additions to
Esther and Daniel, which the Dutch Bible keeps as separate books (Ester (Griekse toevoegingen), Gebed van Azarja,
Susanna, Bel en de draak). Every book of the Bible now has written chapter commentary. The Psalms follow the Hebrew numbering of the Bible text; the Greek number
is noted where the liturgy uses it. The English Esther and Daniel carry the Greek additions in place (Daniel 3, 13
and 14) and their commentary mentions them; the Dutch Bible keeps them as separate books. Where the Dutch chapter
breaks differ (Job 38–41, Ecclesiastes 4–5, Hosea 1–2, 11–14, Micah 4–5), the Dutch summary follows the Dutch text.

The Church articles are written in `tools/source/church/NN-<id>.md` (EN + NL, same number of paragraphs): seven
history articles shared by all three Churches, and for each tradition an overview plus the sacraments, the
liturgy, fasting, the feasts, icons and saints, and daily prayer (28 articles).

Verse notes, shown when a verse is tapped, are written in `tools/source/verse_notes/<BOOK>.md` and cover the
best-known verses, not every verse. Written so far: every book, including the deuterocanon, Psalm 151, the Prayer of Manasseh, 1 Esdras, 3–4 Maccabees and the Greek additions to Esther and Daniel (1106 notes). References use
the English (WEB) numbering; the build moves Psalm notes to the Statenvertaling numbering (where the titles are
counted as verses) and requires an explicit Dutch reference in any other chapter whose numbering differs, so a
note never lands on the wrong verse. Each language only shows its own note.

## Before release

- Terms and Privacy Policy (`assets/legal/`, EN + NL) are drafts that match what the app does: fill in the parts in
  [square brackets] (publisher name and address, Supabase region, backup period, age limit) and have them
  reviewed by a lawyer. Set the real donation URL in `app/build.gradle.kts`.
- Complete the sample content: extend the verse notes to the remaining Old Testament books. Have the Church articles
  reviewed by someone from each tradition. (The Horologion now has Dutch throughout and the Liturgy of the Hours has a
  full week; both carry provisional own texts that need review.)
- Have the written commentary, the quizzes, the menologion texts, the council translations, the Dutch saint names
  and the saint summaries reviewed: the 145 Coptic summaries marked `?` first, which say only what was safe without the full Synaxarium
  text and should be checked and filled out against it.
- Download the 99 extra saint icons listed in `tools/build_icons.py` with `python tools/build_icons.py --new` (needs
  access to commons.wikimedia.org), then look at each new picture and pin a better file in `PINS` where the search
  chose the wrong saint.
- The calendar reads `saints_unified.json`: rerun `python tools/build_unified_saints.py` after any change to
  `saints_*.json` or `icons.json` (it keeps the readings and references of the last build). The Catholic readings
  end in 2027 and follow the American versification, so a few Old Testament references open a verse off in WEB and
  the Statenvertaling (Exodus 22:20-26 is WEB 22:21-27); extend them with `--catholic-readings` from a newer checkout.
- Check author and licence of the seven Home photos taken over from the AI Studio version (marked `"check": true`
  in `assets/backgrounds/backgrounds.json`): `python tools/verify_backgrounds.py --write` on a machine that can reach
  Commons. Remove any photo it reports as not found or not free.
- Try the new parts on a phone: the opening, the widget, the church bell channel (Android keeps the sound of a
  channel once created; reinstall to hear a changed bell), quiet mode and the sun-following hours.
- Have the fasting rules checked by someone from each tradition (`data/calendar/Fasting.kt`).
- Add a release signing config and a Play Store listing (screenshots per style).

## Related repositories

- Documentation & guidelines: https://github.com/gabriel360110l/EOC-church
- Previous Android version: https://github.com/gabriel360110l/AGAPE-Android2.0
