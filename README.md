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
  ui/              onboarding, guided tour, 4-tab shell (Home · Bible · Prayer · Me) and all screens
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
- **End of every chapter**: summary, the Fathers on that chapter, cross-references and a 5-question quiz.
  **End of every book**: book overview, the Fathers on the book and a 20-question quiz. Quizzes without hand-written
  questions are generated from the Bible text (`data/quiz/QuizGenerator.kt`).
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
| Menologion, legacy prayers, chapter guides, Father notes | Legacy AGAPE app | Own content |
| Council stories, Father biographies, Liturgy of the Hours hymns and prayers, plans, quizzes, verse notes | `app/src/main/assets/content/*.json` | Own content (hymns: Neale/Newman, public domain) |

Canon per tradition is defined in `tools/build_content.py` (`BOOKS`). Councils per tradition are in
`assets/content/councils.json` (`recognizedBy`); Catholic readers see all 21 councils, but plans stop at 1054
(`afterSchism`).

Rebuild generated content from the repository root (downloads are cached in `tools/.cache/`):

```
python tools/build_content.py    # Bible database, cross-references, Father quotes, books
python tools/build_prayers.py    # Agpeya and Horologion
python tools/build_councils.py   # council texts and canons
```

## Before release

- Replace the placeholder Terms and Privacy Policy (`assets/legal/`), donation URL and support e-mail (`app/build.gradle.kts`).
- Complete the sample content: Horologion and Liturgy of the Hours texts, verse commentary, quizzes, Church articles.
- Add a release signing config and a Play Store listing (screenshots per style).

## Related repositories

- Documentation & guidelines: https://github.com/gabriel360110l/EOC-church
- Previous Android version: https://github.com/gabriel360110l/AGAPE-Android2.0
