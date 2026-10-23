# Uniform heiligenschema (agape-saints/1)

Eén JSON-vorm voor de heiligen en feesten van de drie tradities in AGAPE: Oosters-Orthodox, Rooms-Katholiek en
Koptisch-Orthodox. Per gekozen datum levert het schema het icoon, de naam, de datum in de Gregoriaanse, Juliaanse
en Koptische kalender, het levensverhaal en de schriftlezingen, en per onderdeel de bron waar het vandaan komt.

De app leest het schema: de converter schrijft een compacte kopie naar `app/src/main/assets/content/saints_unified.json`,
en de kalender, de heilige van de dag op Home en het zoeken halen daar hun heiligen, iconen, levens en lezingen uit
(`data/saints/SaintCalendar.kt`). `saints_eo.json`, `saints_oo.json` en `saints_ca.json` blijven de invoer van de
converter; draai hem opnieuw als die of `icons.json` veranderen.

## Bestanden

| Bestand | Wat |
|---|---|
| `saints_unified.schema.json` | JSON Schema van de dataset: alle vaste gedachtenissen, met de iconen en bronnen |
| `saint_day.schema.json` | JSON Schema van één gekozen dag in één traditie (wat een scherm toont) |
| `saints_unified.json` | De bestaande app-data omgezet naar het schema (gegenereerd; dezelfde inhoud staat compact in de app) |
| `examples/day_*.json` | Drie uitgewerkte dagen, elk voor de drie tradities (gegenereerd) |
| `../build_unified_saints.py` | De converter |

## Twee niveaus

**1. Dataset (`agape-saints/1`).** Elke gedachtenis staat er één keer in, met de datum in de eigen kalender van de
traditie. Een vaste datum hoort bij één kalender, maar valt elk jaar op een andere burgerlijke dag:

| Traditie | `date.calendar` | Betekenis |
|---|---|---|
| `eastern_orthodox` | `church` | Menologion-datum: nieuwe kalender = Gregoriaans, oude kalender = Juliaans (13 dagen later) |
| `catholic` | `gregorian` | Algemene Romeinse Kalender |
| `coptic_orthodox` | `coptic` | Koptische maand (1 Thout ... 13 Nasie) en dag |

**2. Dag (`agape-saint-day/1`).** De converter rekent een gekozen datum om naar alle drie de kalenders en zoekt de
gedachtenissen van die dag op. Het icoon staat er dan volledig in, met titel, maker, licentie en bronpagina.

## Velden van een gedachtenis

| Veld | Inhoud |
|---|---|
| `id` | `traditie/MM-DD/volgorde-slug`, bijv. `coptic_orthodox/02-13/0-the-departure-of-saint-zacharias-the-monk` |
| `tradition` | `eastern_orthodox`, `catholic` of `coptic_orthodox` (in de app: `ORIENTAL_ORTHODOX` voor Koptisch) |
| `kind` | `feast` (Heer, Moeder Gods, Kruis), `event` (concilie, inwijding, overbrenging van relieken, wonder) of `saint`. Afgeleid uit de naam |
| `date` | `{calendar, month, day}` zoals hierboven |
| `order` | Plaats op die dag in de bron; 0 is de hoofdgedachtenis |
| `rank` | `{code, level}`: de rang zoals de bron hem schrijft, en een niveau 1 (hoogfeest) t/m 5 (gedachtenis). Koptisch: `null` |
| `name` | `{en, nl}` |
| `life` | `{text: {en, nl}, scope, url?}`: het levensverhaal of een samenvatting. `scope: "day"` als de tekst over de hele dag gaat (menologion) |
| `icon` | In de dataset een verwijzing `{key, match}` naar de lijst `icons`; in een dag het volledige icoon |
| `readings` | Eigen lezingen van deze gedachtenis, als die er zijn |
| `refs` | Dezelfde gedachtenis in een andere dataset (`romcal`-id, `katameros`-story-id en slug), met `score` als de koppeling automatisch is gemaakt |
| `provenance` | Per onderdeel de bron: `{name, name_nl, life, ...}`, met ids uit de lijst `sources` |

Een icoon heeft `asset` (pad in de app), `url` (directe link via Commons `Special:FilePath`), `page`, `title`,
`artist`, `licence` (alleen `Public domain` of `CC0`) en `fallback` (een algemeen icoon, alleen als er geen eigen is).

Een lezing heeft `role` (`first_reading`, `psalm`, `second_reading`, `gospel`, `epistle`, `apostle`, `old_testament`,
`pauline`, `catholic_epistle`, `acts`, ...), `display` (zoals de bron het schrijft), `ref` in de notatie van de app
(`MRK 12:28-34`) als de verwijzing eenvoudig genoeg is, en `source`. Er wordt nooit bijbeltekst overgenomen.

## Voorbeeld (23 oktober 2026, Koptisch)

```json
{
 "schema": "agape-saint-day/1",
 "tradition": "coptic_orthodox",
 "date": {"gregorian": "2026-10-23", "julian": "2026-10-10",
          "coptic": {"year": 1743, "month": 2, "day": 13, "month_name": "Paopi"}},
 "title": {"en": "The Departure of Saint Zacharias, the Monk", "nl": "Het heengaan van de heilige Zacharias, de monnik"},
 "icon": null,
 "commemorations": [{
   "id": "coptic_orthodox/02-13/0-the-departure-of-saint-zacharias-the-monk",
   "tradition": "coptic_orthodox", "kind": "saint",
   "date": {"calendar": "coptic", "month": 2, "day": 13}, "order": 0, "rank": null,
   "name": {"en": "The Departure of Saint Zacharias, the Monk", "nl": "Het heengaan van de heilige Zacharias, de monnik"},
   "life": null, "icon": null, "readings": [],
   "refs": [{"source": "katameros", "id": "21301", "slug": "departure-of-saint-zacharias-the-monk", "score": 1.0}],
   "provenance": {"name": "coptic_io", "name_nl": "agape"}
 }],
 "readings": []
}
```

Volledige voorbeelden: `examples/day_2026-10-23.json`, `day_2026-12-25.json` en `day_2027-01-07.json`.

## Bronnen en licenties

Gecontroleerd op 23 oktober 2026. Uit een bron wordt alleen overgenomen wat de licentie toestaat; wat niet vrij is,
dient alleen als voorbeeld voor de vorm van het schema.

| Repository | Licentie | Gebruikt | Niet gebruikt |
|---|---|---|---|
| brianglass/orthocal-service | MIT (code) | Vorm van `readings` (bron, boek, weergave) en de Juliaanse/nieuwe kalender als optie | De levens en lezingen komen uit de database van orthocal.info (OCA-teksten, auteursrecht); de API is vanuit de cloud niet bereikbaar |
| dennisleexyz/muzeikon | GPL-3.0 | Alleen het idee: per dag het icoon van de heilige | Code (GPL) en de iconen, die van oca.org komen en niet vrij zijn |
| harmolipi/Orthodox-Calendar-API | Geen licentie | Niets | Alles: zonder licentie mag niets worden overgenomen, en de lezingen zijn RSV-tekst |
| cpbjr/catholic-readings-api | MIT | Schriftverwijzingen per datum (2025-2027, lectionarium VS) voor `readings` | De korte beschrijvingen en de afbeeldingen (licentie per afbeelding onbekend) |
| romcal/romcal | MIT | Stabiele ids van de Algemene Romeinse Kalender in `refs` | Namen en rangen: die heeft de app al, met Nederlands |
| pierresaid/katameros-web-app | MIT | Story-id en slug van het Synaxarium in `refs` (862 van de 913 Koptische en Katholieke gedachtenissen gekoppeld) | De levens staan in de katameros-API, niet in de repo; die API is vanuit de cloud niet getest |
| Will-Langhart/copticfaith | Geen licentie | Niets; alleen het idee van een `kind` per heilige | Alles |
| Wikimedia Commons (bestaande `icons.json`) | Public domain / CC0 | Alle 73 iconen, met maker en bronpagina | Afbeeldingen met een andere licentie |

De titels in `saints_oo.json` (api.coptic.io) en `saints_ca.json` (calapi) staan in de dataset met bron
`coptic_io` en `calapi` en licentie `unknown`: het zijn feitelijke namen en data, geen lopende tekst.

## Hoe de API's op het schema passen

| Schema | orthocal.info | catholic-readings-api | katameros |
|---|---|---|---|
| `name` | `saints[]`, `feasts[]` | `celebration.name` | `Title` (synax-days) |
| `rank` | `feast_level`, `feast_level_description` | `celebration.type` | geen |
| `life.text` | `stories[].story` | (`celebration.description`) | `GET /synaxarium/{slug}` → `html` |
| `readings[]` | `readings[]`: `source` → `role`, `display` → `display` | `readings.firstReading` enz. | `sections[].subSections[].readings[].passages[]` |
| `date` | `year/month/day`, `julian_day_number` | `date` | `Month`, `Day` (Koptisch) |

## Wat er nog ontbreekt

- **Levens.** Samenvattingen, geen volledige levens: elke dag van het menologion (366), de eerste Koptische
  gedachtenis van elke dag (367) en elke Katholieke gedachtenis (217). De overige Koptische gedachtenissen hebben
  alleen een naam.
- **Iconen.** 253 gedachtenissen hebben een eigen icoon (minder dan eerst, sinds verkeerde koppelingen in
  `icons.json` zijn rechtgezet), de rest een algemeen icoon of geen. Meer iconen moeten op
  Gabriels computer worden opgehaald (Wikimedia is vanuit de cloud geblokkeerd).
- **Lezingen.** Alleen Katholiek, en alleen 2025-2027, in `dated_readings` (per datum, want ze volgen het
  lectionarium en niet de heilige). `ref` is de plek waar de lezer opent: bij een lezing in stukken het eerste stuk,
  zonder versletters; `display` houdt de volledige verwijzing. De Byzantijnse lezingen haalt de kalender live op
  (orthocal.info); Koptische (katameros) nog niet. Beide kunnen in dezelfde `readings`-vorm worden opgeslagen.
- **Beweeglijke feesten.** Pascha, Pinksteren, Kerstmis in de Romeinse kalender enz. zitten niet in de dataset;
  die rekent `LiturgicalCalendar.kt` uit en gaan in de app voor op de heilige van de dag.

## Draaien

Vanuit de root van de repository, zonder netwerk:

```
python -I -X utf8 tools/build_unified_saints.py
```

Zonder de referentie-repo's blijven de `refs` en `dated_readings` van de vorige build staan. Met de repo's lokaal
gekloond (om ze te vernieuwen) en uitgewerkte dagen:

```
python -I -X utf8 tools/build_unified_saints.py --romcal ../romcal --katameros ../katameros-web-app \
    --catholic-readings ../catholic-readings-api --day 2026-10-23 --day 2026-12-25 --day 2027-01-07
```

`--old-calendar` zet de Oosters-Orthodoxe dagen op de Juliaanse kalender. Als het pakket `jsonschema` is
geïnstalleerd, controleert de converter de uitvoer tegen beide schema's.
