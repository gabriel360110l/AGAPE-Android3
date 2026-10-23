#!/usr/bin/env python3
"""Converts the app's three saint calendars and its icons into one uniform dataset (schema agape-saints/1).

Inputs (committed, read offline):
  app/src/main/assets/content/saints_eo.json   Byzantine menologion per civil month/day, with summaries
  app/src/main/assets/content/saints_oo.json   Coptic Synaxarium per Coptic month/day
  app/src/main/assets/content/saints_ca.json   General Roman Calendar per Gregorian month/day
  app/src/main/assets/content/icons.json       Bundled Public domain / CC0 icons and the names they match
  app/src/main/assets/content/books.json       Book ids, to write readings in the app's `BOOK C:V-V` notation

Optional reference checkouts (only identifiers and citations are taken from them, see tools/schema/README.md):
  --romcal DIR              github.com/romcal/romcal (MIT): stable ids of the General Roman Calendar
  --katameros DIR           github.com/pierresaid/katameros-web-app (MIT): Synaxarium story ids and slugs
  --catholic-readings DIR   github.com/cpbjr/catholic-readings-api (MIT): Mass reading citations per date

Outputs:
  tools/schema/saints_unified.json             every commemoration of the three traditions (schema saints_unified)
  app/src/main/assets/content/saints_unified.json   the same, compact, read by the app's calendar and saint screens
  tools/schema/examples/day_<date>.json        with --day: the resolved view of one date (schema saint_day)

Run from the repository root:
  python -I -X utf8 tools/build_unified_saints.py [--romcal DIR] [--katameros DIR] [--catholic-readings DIR]
                                                 [--day 2026-10-23 ...] [--old-calendar]
Validates against the schemas when the jsonschema package is installed.
"""
import argparse
import datetime
import json
import os
import re
import urllib.parse

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTENT = os.path.join(ROOT, "app", "src", "main", "assets", "content")
SCHEMA_DIR = os.path.join(ROOT, "tools", "schema")
OUT = os.path.join(SCHEMA_DIR, "saints_unified.json")
APP_OUT = os.path.join(CONTENT, "saints_unified.json")

TRADITIONS = ("eastern_orthodox", "catholic", "coptic_orthodox")
COPTIC_MONTHS = ["Thout", "Paopi", "Hathor", "Koiak", "Tobi", "Meshir", "Paremhat", "Parmouti", "Pashons", "Paoni",
                 "Epip", "Mesori", "Nasie"]
COPTIC_EPOCH = 1825030  # Julian Day Number of 1 Thout, year 1 of the Martyrs, as in LiturgicalCalendar.kt
JDN_OFFSET = 1721425  # date.toordinal() + JDN_OFFSET = Julian Day Number

# Rank as written by each source -> 1 (highest) .. 5. The Coptic Synaxarium has no ranks.
RANK_LEVEL = {
    "great": 1, "solemnity": 1,
    "major": 2, "feast": 2,
    "saint": 3, "memorial": 3,
    "optional memorial": 4,
    "commemoration": 5,
}

# What a commemoration is about. Checked in order on the English name; anything else is a saint.
FEAST = re.compile(
    r"\b(Nativity|Theophany|Epiphany|Baptism of (Christ|the Lord|our Lord)|Annunciation|Transfiguration|Dormition|"
    r"Assumption|Presentation of (Christ|the Lord|our Lord)|Meeting of (Christ|the Lord|our Lord)|Circumcision|"
    r"Exaltation|Elevation of the|Holy Cross|Precious Cross|Entr(y|ance) of (the )?(Most Holy )?Theotokos|"
    r"Entr(y|ance) of our Lord|Entry of Our Lord|Protection of the|Immaculate Conception|Conception of|"
    r"Holy Name|Christmas|Pentecost|Ascension|Resurrection|El-Nayrouz|Nayrouz|Wedding at Cana|Most Holy Name|"
    r"Mother of God|Our Lady|Theotokos|Blessed Virgin Mary|Virgin Mary|St\.? Mary)\b",
    re.I,
)
EVENT = re.compile(
    r"\b(Council|Synod|Synaxis|Consecration|Translation|Transfer|Relocation|Translocation|Appearance of the (Head|Body|"
    r"Relics)|Dedication|Finding|Beginning of|New Year|Indiction|Miracle|Assembly of|Commemoration of the (Consecration|"
    r"Miracle|Relocation|Appearance|Entry))\b",
    re.I,
)
# A saint's name inside a feast-like title ("Departure of St. Mary of Egypt") makes it a saint again.
SAINT_OF = re.compile(r"\b(Mary of Egypt|Mary Magdalene|Mary the|Martyrdom|Departure of (?!(the )?(St\.? )?(Mary|Virgin|"
                      r"Theotokos|Lady)))", re.I)

SOURCES = [
    {"id": "agape", "name": "AGAPE (eigen redactie)", "url": "https://github.com/gabriel360110l/AGAPE-Android3",
     "licence": "AGAPE", "used_for": ["name_nl", "life"],
     "note": "Nederlandse namen en levensbeschrijvingen die voor de app zijn geschreven of nagekeken."},
    {"id": "agape_menologion", "name": "AGAPE menologion (legacy)", "url": "https://github.com/gabriel360110l/AGAPE-Android3",
     "licence": "AGAPE", "used_for": ["name", "date", "rank"],
     "note": "Byzantijns menologion van vaste feesten, per burgerlijke datum (nieuwe kalender)."},
    {"id": "coptic_io", "name": "api.coptic.io Synaxarium", "url": "https://api.coptic.io",
     "licence": "unknown", "used_for": ["name", "date"],
     "note": "Alleen de titels per Koptische dag, gecorrigeerd met de Synaxarium-uitgave van St-Takla.org."},
    {"id": "calapi", "name": "Church Calendar API (calapi.inadiutorium.cz)", "url": "http://calapi.inadiutorium.cz",
     "licence": "unknown", "used_for": ["name", "date", "rank"],
     "note": "Alleen titels en rangen van de Algemene Romeinse Kalender."},
    {"id": "wikimedia_commons", "name": "Wikimedia Commons", "url": "https://commons.wikimedia.org",
     "licence": "Public domain / CC0 (per icoon vastgelegd)", "used_for": ["icon"],
     "note": "Alleen iconen met licentie Public domain of CC0; titel, maker en bronpagina staan per icoon."},
    {"id": "romcal", "name": "romcal", "url": "https://github.com/romcal/romcal", "licence": "MIT",
     "used_for": ["ref"], "note": "Alleen de stabiele ids van de Algemene Romeinse Kalender."},
    {"id": "katameros", "name": "Katameros", "url": "https://github.com/pierresaid/katameros-web-app", "licence": "MIT",
     "used_for": ["ref"], "note": "Alleen story-id en slug van het Synaxarium, als verwijzing."},
    {"id": "catholic_readings_api", "name": "Catholic Readings API", "url": "https://github.com/cpbjr/catholic-readings-api",
     "licence": "MIT", "used_for": ["readings"],
     "note": "Alleen schriftverwijzingen (geen bijbeltekst) van het lectionarium van de VS, per datum 2025-2027."},
]


def read_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def slug(text):
    text = re.sub(r"[^a-z0-9]+", "-", text.lower().replace("st.", "saint")).strip("-")
    return text[:60].rstrip("-") or "x"


def kind_of(name, rank=None):
    if rank == "great":
        return "feast"
    if FEAST.search(name) and not SAINT_OF.search(name):
        return "feast"
    if EVENT.search(name):
        return "event"
    return "saint"


def rank_of(code):
    if not code:
        return None
    return {"code": code, "level": RANK_LEVEL.get(code, 5)}


def l10n(value):
    if not value or not (value.get("en") or value.get("nl")):
        return None
    return {"en": value.get("en", ""), "nl": value.get("nl", "")}


# --- Icons -----------------------------------------------------------------------------------------------------------

def commons_file_url(page):
    """The Commons page of a file -> a stable link that redirects to the image itself."""
    name = page.rsplit("/File:", 1)[-1] if "/File:" in page else None
    if not name:
        return None
    return "https://commons.wikimedia.org/wiki/Special:FilePath/" + urllib.parse.quote(urllib.parse.unquote(name))


def load_icons():
    raw = read_json(os.path.join(CONTENT, "icons.json"))
    icons, patterns = [], []
    for i in raw:
        icons.append({
            "key": i["key"],
            "asset": i["file"],
            "url": commons_file_url(i["source"]),
            "page": i["source"],
            "title": i["title"],
            "artist": i["artist"],
            "licence": i["licence"],
            "fallback": bool(i.get("fallback")),
            "source": "wikimedia_commons",
        })
        patterns.append((i["key"], bool(i.get("fallback")), [re.compile(p, re.I) for p in i["match"]]))
    return icons, patterns


def icon_for(name, patterns):
    """Same rule as ContentRepository.iconFor for one name: a specific icon first, else a fallback (generic) one."""
    for fallback in (False, True):
        for key, fb, pats in patterns:
            if fb == fallback and any(p.search(name) for p in pats):
                return {"key": key, "match": "fallback" if fb else "name"}
    return None


# --- Reference checkouts (optional) -----------------------------------------------------------------------------------

STOP = set("""the of saint saints st sts and a an his her their who was with in by for to from one our holy great
father mother son brother sister pope patriarch alexandria see mark bishop bishops priest priests deacon monk virgin
virgins martyr martyrs martyrdom departure repose commemoration apostle apostles disciples seventy twelve anba abba
church doctor doctors known as relics""".split())


def words(text):
    return {w for w in re.findall(r"[a-z]+|\d+", text.lower()) if (len(w) > 2 or w.isdigit()) and w not in STOP}


def best_match(name, candidates, key=lambda c: c, threshold=0.2):
    """(score, candidate) for the candidate that shares most distinctive words with `name`. Both naming a different number (46th, 116th
    pope) rules a candidate out; below `threshold` (Jaccard) there is no match."""
    mine = words(name)
    best = None
    for c in candidates:
        theirs = words(key(c))
        nums, their_nums = {w for w in mine if w.isdigit()}, {w for w in theirs if w.isdigit()}
        if nums and their_nums and not nums & their_nums:
            continue
        score = len(mine & theirs) / (len(mine | theirs) or 1)
        if score >= threshold and (best is None or score > best[0]):
            best = (score, c)
    return best


ROMCAL_DATE_FNS = {"presentationOfTheLord": (2, 2), "annunciation": (3, 25), "nativityOfJohnTheBaptist": (6, 24),
                   "peterAndPaulApostles": (6, 29), "transfiguration": (8, 6), "assumption": (8, 15),
                   "exaltationOfTheHolyCross": (9, 14), "allSaints": (11, 1)}


def load_romcal(path):
    src = os.path.join(path, "rites", "roman1969", "src")
    cal = open(os.path.join(src, "calendars", "general-roman", "index.ts"), encoding="utf-8").read()
    names_ts = open(os.path.join(src, "locales", "en.ts"), encoding="utf-8").read()
    names = dict(re.findall(r"^\s{4}([a-z0-9_]+):\s*\n?\s*'((?:[^'\\]|\\.)*)'", names_ts, re.M))
    by_day = {}
    for m in re.finditer(r"^\s{4}([a-z0-9_]+): \{\n(.*?)^\s{4}\},", cal, re.M | re.S):
        rid, body = m.group(1), m.group(2)
        d = re.search(r"dateDef: \{ month: (\d+), date: (\d+) \}", body)
        f = re.search(r"dateDef: \{ dateFn: '(\w+)' \}", body)
        md = (int(d.group(1)), int(d.group(2))) if d else ROMCAL_DATE_FNS.get(f.group(1)) if f else None
        if md:
            by_day.setdefault(md, []).append((rid, names.get(rid, rid.replace("_", " "))))
    return by_day


def load_katameros(path):
    by_day = {}
    for e in read_json(os.path.join(path, "assets", "synax-days-en.json")):
        by_day.setdefault((e["Month"], e["Day"]), []).append(e)
    return by_day


# --- Readings ---------------------------------------------------------------------------------------------------------

def book_index():
    index = {}
    for b in read_json(os.path.join(CONTENT, "books.json")):
        for n in (b["name"]["en"], b["name"]["nl"]):
            index[n.lower()] = b["id"]
    index.update({"psalm": "PSA", "psalms": "PSA", "song of songs": index.get("song of solomon", "SNG")})
    return index


def to_ref(citation, books):
    """'Mark 12:28-34' -> 'MRK 12:28-34'. Of a citation in several pieces only the first is kept ('Psalm 18:2-3, 3-4, 47'
    -> 'PSA 18:2-3'), and verse letters are dropped ('1 Thessalonians 1:5c-10' -> '1TH 1:5-10'): the ref is where the
    reader opens, the display keeps the full citation. Anything more complex keeps only its display."""
    text = re.sub(r":\s+", ":", re.sub(r"[\u2013\u2014]", "-", citation.replace("\u00a0", " ")))
    first = re.split(r"[,;]| and | or ", text)[0]
    one = re.fullmatch(r"\s*(.+?)\s+(\d+)[a-eA-E]*(?:-(\d+)[a-eA-E]*)?\s*", first)  # Philemon 9-10, Jude 17
    if one and books.get(one.group(1).lower()) in ("OBA", "PHM", "2JN", "3JN", "JUD"):
        first = f"{one.group(1)} 1:{one.group(2)}" + (f"-{one.group(3)}" if one.group(3) else "")
    m = re.fullmatch(r"\s*(.+?)\s+(\d+):(\d+)[a-eA-E]*(?:-(?:(\d+):)?(\d+)[a-eA-E]*)?\s*", first)
    if not m:
        return None
    book = books.get(m.group(1).lower())
    if not book:
        return None
    end = f"-{m.group(4)}:{m.group(5)}" if m.group(4) else f"-{m.group(5)}" if m.group(5) else ""
    return f"{book} {m.group(2)}:{m.group(3)}{end}"


CA_ROLES = {"firstReading": "first_reading", "psalm": "psalm", "secondReading": "second_reading",
            "gospel": "gospel", "alleluia": "acclamation"}


def all_catholic_readings(path):
    """Every date the checkout has: {"2026-10-25": [reading, ...]}."""
    books, out = book_index(), {}
    for year in sorted(os.listdir(os.path.join(path, "readings"))):
        if not year.isdigit():
            continue
        for name in sorted(os.listdir(os.path.join(path, "readings", year))):
            m = re.fullmatch(r"(\d\d)-(\d\d)\.json", name)
            if m:
                date = datetime.date(int(year), int(m.group(1)), int(m.group(2)))
                readings = catholic_readings(path, date, books)
                if readings:
                    out[date.isoformat()] = readings
    return out


def catholic_readings(path, date, books):
    f = os.path.join(path, "readings", str(date.year), f"{date.month:02d}-{date.day:02d}.json")
    if not os.path.exists(f):
        return []
    out = []
    for role, citation in read_json(f).get("readings", {}).items():
        if not isinstance(citation, str) or not citation.strip():
            continue
        r = {"role": CA_ROLES.get(role, "other"), "display": citation.strip(), "source": "catholic_readings_api"}
        ref = to_ref(citation, books)
        if ref:
            r["ref"] = ref
        out.append(r)
    return out


# --- Conversion -------------------------------------------------------------------------------------------------------

def build(romcal=None, katameros=None, dated=None, previous=None):
    """`previous` is the last saints_unified.json: without the reference checkouts its refs and dated readings are
    kept, so a rebuild offline does not lose them."""
    icons, patterns = load_icons()
    items = []

    def add(tradition, calendar, month, day, order, name, rank=None, life=None, provenance=None, refs=None, kind_rank=None):
        c = {
            "id": f"{tradition}/{month:02d}-{day:02d}/{order}-{slug(name['en'])}",
            "tradition": tradition,
            "kind": kind_of(name["en"], kind_rank),
            "date": {"calendar": calendar, "month": month, "day": day},
            "order": order,
            "rank": rank_of(rank),
            "name": name,
            "life": life,
            "icon": icon_for(name["en"], patterns),
            "readings": [],
            "refs": refs or [],
            "provenance": provenance,
        }
        items.append(c)

    # Eastern Orthodox: one entry per day, names joined with " · "; the summary speaks of the whole day.
    for e in read_json(os.path.join(CONTENT, "saints_eo.json")):
        en, nl = e["title"]["en"].split(" · "), e["title"]["nl"].split(" · ")
        summary = l10n(e.get("summary"))
        if summary and summary["en"].startswith("The Church commemorates"):  # placeholder sentence, see SaintOfDay.kt
            summary["en"] = ""
        for i, n in enumerate(en):
            prov = {"name": "agape_menologion", "name_nl": "agape"}
            life = None
            if i == 0 and summary:
                life = {"text": summary, "scope": "day"}
                prov["life"] = "agape"
            add("eastern_orthodox", "church", e["month"], e["day"], i,
                {"en": n.strip(), "nl": (nl[i] if i < len(nl) else n).strip()},
                rank=e.get("rank") if i == 0 else None, kind_rank=e.get("rank") if i == 0 else None,
                life=life, provenance=prov)

    for file, tradition, calendar, source in (("saints_oo.json", "coptic_orthodox", "coptic", "coptic_io"),
                                              ("saints_ca.json", "catholic", "gregorian", "calapi")):
        for d in read_json(os.path.join(CONTENT, file)):
            m, dd = d["month"], d["day"]
            for i, s in enumerate(d["saints"]):
                prov = {"name": source, "name_nl": "agape"}
                summary = l10n(s.get("summary"))  # present once the hand-written summaries are merged
                life = {"text": summary, "scope": "commemoration"} if summary else None
                if life:
                    prov["life"] = "agape"
                refs = []
                if romcal and tradition == "catholic":
                    hit = best_match(s["title"]["en"], romcal.get((m, dd), []), key=lambda c: c[1])
                    if hit:
                        refs.append({"source": "romcal", "id": hit[1][0], "score": round(hit[0], 2)})
                if katameros and tradition == "coptic_orthodox":
                    hit = best_match(s["title"]["en"], katameros.get((m, dd), []), key=lambda c: c["Title"])
                    if hit:
                        story = hit[1]
                        refs.append({"source": "katameros", "id": str(story["StoryId"]), "slug": story["Slug"],
                                     "score": round(hit[0], 2)})
                add(tradition, calendar, m, dd, i, l10n(s["title"]), rank=s.get("rank"), kind_rank=s.get("rank"),
                    life=life, provenance=prov, refs=refs)

    previous = previous or {}
    old_refs = {c["id"]: c["refs"] for c in previous.get("commemorations", [])}
    for c in items:
        kept = [r for r in old_refs.get(c["id"], []) if (r["source"] == "romcal" and not romcal)
                or (r["source"] == "katameros" and not katameros)]
        c["refs"] = c["refs"] + kept
    if dated is None:
        dated = previous.get("dated_readings", {}).get("catholic", {})
    used = {r["source"] for c in items for r in c["refs"]} | ({"catholic_readings_api"} if dated else set())
    sources = [s for s in SOURCES if s["id"] not in ("romcal", "katameros", "catholic_readings_api") or s["id"] in used]
    data = {
        "schema": "agape-saints/1",
        "generated_by": "tools/build_unified_saints.py",
        "sources": sources,
        "icons": icons,
        "commemorations": items,
    }
    if dated:
        data["dated_readings"] = {"catholic": dated}
    return data


# --- Resolving one date -----------------------------------------------------------------------------------------------

def julian_of(date):
    """The Julian calendar date of a Gregorian date, from its Julian Day Number."""
    jdn = date.toordinal() + JDN_OFFSET
    c = jdn + 32082
    d = (4 * c + 3) // 1461
    e = c - 1461 * d // 4
    m = (5 * e + 2) // 153
    return d - 4800 + m // 10, m + 3 - 12 * (m // 10), e - (153 * m + 2) // 5 + 1


def coptic_of(date):
    jdn = date.toordinal() + JDN_OFFSET
    year = (4 * (jdn - COPTIC_EPOCH) + 1463) // 1461
    start = COPTIC_EPOCH + 365 * (year - 1) + year // 4
    doy = jdn - start
    return year, doy // 30 + 1, doy % 30 + 1


def resolve_day(data, tradition, date, old_calendar=False):
    jy, jm, jd = julian_of(date)
    cy, cm, cd = coptic_of(date)
    if tradition == "coptic_orthodox":
        key = ("coptic", cm, cd)
    elif tradition == "eastern_orthodox":
        key = ("church", jm, jd) if old_calendar else ("church", date.month, date.day)
    else:
        key = ("gregorian", date.month, date.day)
    icons = {i["key"]: i for i in data["icons"]}
    items = [dict(c) for c in data["commemorations"] if c["tradition"] == tradition
             and (c["date"]["calendar"], c["date"]["month"], c["date"]["day"]) == key]
    items.sort(key=lambda c: ((c["rank"] or {}).get("level", 9), c["order"]) if tradition == "catholic" else c["order"])
    for c in items:
        c["icon"] = dict(icons[c["icon"]["key"]], match=c["icon"]["match"]) if c["icon"] else None
    # Like the app: the principal commemoration's icon, else the first commemoration of the day that has one.
    icon = next((c["icon"] for c in items if c["icon"] and c["icon"]["match"] == "name"), None) or \
        next((c["icon"] for c in items if c["icon"]), None)
    readings = data.get("dated_readings", {}).get(tradition, {}).get(date.isoformat(), [])
    return {
        "schema": "agape-saint-day/1",
        "tradition": tradition,
        "calendar_style": ("old" if old_calendar else "new") if tradition == "eastern_orthodox" else None,
        "date": {
            "gregorian": date.isoformat(),
            "julian": f"{jy:04d}-{jm:02d}-{jd:02d}",
            "coptic": {"year": cy, "month": cm, "day": cd, "month_name": COPTIC_MONTHS[cm - 1]},
        },
        "title": items[0]["name"] if items else None,
        "icon": icon,
        "commemorations": items,
        "readings": readings,
        "moveable_feasts_note": "Beweeglijke feesten (Pascha, Pinksteren, ...) komen uit LiturgicalCalendar.kt en "
                                "gaan in de app voor op de heilige van de dag.",
    }


def validate(doc, schema_name):
    try:
        import jsonschema
        import referencing
    except ImportError:
        print("  (jsonschema not installed: not validated)")
        return
    schemas = {n: read_json(os.path.join(SCHEMA_DIR, n)) for n in ("saints_unified.schema.json", "saint_day.schema.json")}
    registry = referencing.Registry().with_resources(
        [(s["$id"], referencing.Resource.from_contents(s)) for s in schemas.values()])
    jsonschema.Draft202012Validator(schemas[schema_name], registry=registry).validate(doc)
    print("  valid against", schema_name)


def write(path, data, compact=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        if compact:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
        else:
            json.dump(data, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("wrote", os.path.relpath(path, ROOT), os.path.getsize(path) // 1024, "KB")


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--romcal")
    ap.add_argument("--katameros")
    ap.add_argument("--catholic-readings")
    ap.add_argument("--day", action="append", default=[], help="YYYY-MM-DD; may be repeated")
    ap.add_argument("--old-calendar", action="store_true", help="Eastern Orthodox on the Julian calendar")
    args = ap.parse_args()

    data = build(romcal=load_romcal(args.romcal) if args.romcal else None,
                 katameros=load_katameros(args.katameros) if args.katameros else None,
                 dated=all_catholic_readings(args.catholic_readings) if args.catholic_readings else None,
                 previous=read_json(OUT) if os.path.exists(OUT) else None)
    write(OUT, data)
    write(APP_OUT, data, compact=True)
    validate(data, "saints_unified.schema.json")
    per = {t: sum(1 for c in data["commemorations"] if c["tradition"] == t) for t in TRADITIONS}
    with_icon = sum(1 for c in data["commemorations"] if c["icon"] and c["icon"]["match"] == "name")
    with_life = sum(1 for c in data["commemorations"] if c["life"])
    refs = sum(1 for c in data["commemorations"] if c["refs"])
    dated = len(data.get("dated_readings", {}).get("catholic", {}))
    print(f"  {per}; own icon {with_icon}, life {with_life}, external ref {refs}, dates with readings {dated}")

    for day in args.day:
        date = datetime.date.fromisoformat(day)
        view = {"date": day, "traditions": [resolve_day(data, t, date, args.old_calendar)
                                            for t in TRADITIONS]}
        for v in view["traditions"]:
            validate(v, "saint_day.schema.json")
        write(os.path.join(SCHEMA_DIR, "examples", f"day_{day}.json"), view)
