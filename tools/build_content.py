#!/usr/bin/env python3
"""Builds AGAPE's bundled offline content.

Outputs (committed, so the app builds without Python):
  app/src/main/assets/bible/bible.db       Bible texts, cross-references, chapter guides, Father notes
  app/src/main/assets/content/books.json   Book list with canon membership per tradition
  app/src/main/assets/content/prayers_extra.json  Additional prayers
  app/src/main/assets/content/saints_eo.json      Byzantine menologion (fixed feasts)

Inputs:
  tools/source/statenvertaling.tsv         Statenvertaling 1637 + Apocrypha (public domain)
  tools/source/chapter_guides.jsonl        Chapter guides from the legacy app
  tools/source/father_notes.jsonl          Church Father notes from the legacy app
  tools/source/legacy/*.json               Legacy prayer / calendar / canon content
  tools/.cache/eng-web_vpl.txt             World English Bible Classic, downloaded from ebible.org
  tools/.cache/cross_references.txt        OpenBible.info cross-references (CC-BY), downloaded

Run from the repository root:  python tools/build_content.py
"""
import io
import json
import os
import re
import sqlite3
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "tools", "source")
CACHE = os.path.join(ROOT, "tools", ".cache")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
DB_VERSION = 3
MIN_CROSS_REF_VOTES = 10

EO, OO, CA = "EASTERN_ORTHODOX", "ORIENTAL_ORTHODOX", "CATHOLIC"
ALL = [EO, OO, CA]
# Deuterocanon shared by all three churches. The Coptic list is a starting point and is easy to extend here.
DC = [EO, OO, CA]

# usfm, ebible WEB code, legacy/Statenvertaling slug, English name, Dutch name, testament, traditions
BOOKS = [
    ("GEN", "GEN", "genesis", "Genesis", "Genesis", "OT", ALL),
    ("EXO", "EXO", "exodus", "Exodus", "Exodus", "OT", ALL),
    ("LEV", "LEV", "leviticus", "Leviticus", "Leviticus", "OT", ALL),
    ("NUM", "NUM", "numeri", "Numbers", "Numeri", "OT", ALL),
    ("DEU", "DEU", "deuteronomium", "Deuteronomy", "Deuteronomium", "OT", ALL),
    ("JOS", "JOS", "jozua", "Joshua", "Jozua", "OT", ALL),
    ("JDG", "JDG", "richteren", "Judges", "Richteren", "OT", ALL),
    ("RUT", "RUT", "ruth", "Ruth", "Ruth", "OT", ALL),
    ("1SA", "1SA", "1samuel", "1 Samuel", "1 Samuël", "OT", ALL),
    ("2SA", "2SA", "2samuel", "2 Samuel", "2 Samuël", "OT", ALL),
    ("1KI", "1KI", "1koningen", "1 Kings", "1 Koningen", "OT", ALL),
    ("2KI", "2KI", "2koningen", "2 Kings", "2 Koningen", "OT", ALL),
    ("1CH", "1CH", "1kronieken", "1 Chronicles", "1 Kronieken", "OT", ALL),
    ("2CH", "2CH", "2kronieken", "2 Chronicles", "2 Kronieken", "OT", ALL),
    ("1ES", "1ES", "1esdras", "1 Esdras", "1 Esdras", "OT", [EO]),
    ("EZR", "EZR", "ezra", "Ezra", "Ezra", "OT", ALL),
    ("NEH", "NEH", "nehemia", "Nehemiah", "Nehemia", "OT", ALL),
    ("TOB", "TOB", "tobit", "Tobit", "Tobit", "OT", DC),
    ("JDT", "JDT", "judith", "Judith", "Judit", "OT", DC),
    # English uses the complete Greek Esther from WEB; Dutch has Hebrew Esther plus the Greek additions.
    ("EST", "ESG", "esther", "Esther", "Ester", "OT", ALL),
    ("ESG", None, "ester-grieks", "Esther (Greek additions)", "Ester (Griekse toevoegingen)", "OT", DC),
    ("1MA", "1MA", "1maccabeen", "1 Maccabees", "1 Makkabeeën", "OT", DC),
    ("2MA", "2MA", "2maccabeen", "2 Maccabees", "2 Makkabeeën", "OT", DC),
    ("3MA", "3MA", "3maccabeen", "3 Maccabees", "3 Makkabeeën", "OT", [EO]),
    ("PSA", "PSA", "psalmen", "Psalms", "Psalmen", "OT", ALL),
    ("PS2", "PSX", "psalm151", "Psalm 151", "Psalm 151", "OT", [EO, OO]),
    ("MAN", "PRM", "gebed-manasse", "Prayer of Manasseh", "Gebed van Manasse", "OT", [EO]),
    ("JOB", "JOB", "job", "Job", "Job", "OT", ALL),
    ("PRO", "PRO", "spreuken", "Proverbs", "Spreuken", "OT", ALL),
    ("ECC", "ECC", "prediker", "Ecclesiastes", "Prediker", "OT", ALL),
    ("SNG", "SOL", "hooglied", "Song of Songs", "Hooglied", "OT", ALL),
    ("WIS", "WIS", "wijsheid", "Wisdom", "Wijsheid", "OT", DC),
    ("SIR", "SIR", "sirach", "Sirach", "Sirach", "OT", DC),
    ("ISA", "ISA", "jesaja", "Isaiah", "Jesaja", "OT", ALL),
    ("JER", "JER", "jeremia", "Jeremiah", "Jeremia", "OT", ALL),
    ("LAM", "LAM", "klaagliederen", "Lamentations", "Klaagliederen", "OT", ALL),
    ("BAR", "BAR", "baruch", "Baruch", "Baruch", "OT", DC),
    ("EZK", "EZE", "ezechiel", "Ezekiel", "Ezechiël", "OT", ALL),
    # English uses Greek Daniel (14 chapters, with the additions); Dutch keeps the additions as separate books.
    ("DAN", "DNG", "daniel", "Daniel", "Daniël", "OT", ALL),
    ("S3Y", None, "gebed-azaria", "Prayer of Azariah", "Gebed van Azarja", "OT", DC),
    ("SUS", None, "susanna", "Susanna", "Susanna", "OT", DC),
    ("BEL", None, "bel", "Bel and the Dragon", "Bel en de draak", "OT", DC),
    ("HOS", "HOS", "hosea", "Hosea", "Hosea", "OT", ALL),
    ("JOL", "JOE", "joel", "Joel", "Joël", "OT", ALL),
    ("AMO", "AMO", "amos", "Amos", "Amos", "OT", ALL),
    ("OBA", "OBA", "obadja", "Obadiah", "Obadja", "OT", ALL),
    ("JON", "JON", "jona", "Jonah", "Jona", "OT", ALL),
    ("MIC", "MIC", "micha", "Micah", "Micha", "OT", ALL),
    ("NAM", "NAH", "nahum", "Nahum", "Nahum", "OT", ALL),
    ("HAB", "HAB", "habakuk", "Habakkuk", "Habakuk", "OT", ALL),
    ("ZEP", "ZEP", "sefania", "Zephaniah", "Sefanja", "OT", ALL),
    ("HAG", "HAG", "haggai", "Haggai", "Haggai", "OT", ALL),
    ("ZEC", "ZEC", "zacharia", "Zechariah", "Zacharia", "OT", ALL),
    ("MAL", "MAL", "maleachi", "Malachi", "Maleachi", "OT", ALL),
    ("4MA", "4MA", None, "4 Maccabees (appendix)", "4 Makkabeeën (appendix)", "OT", [EO]),
    ("MAT", "MAT", "matteus", "Matthew", "Matteüs", "NT", ALL),
    ("MRK", "MAR", "marcus", "Mark", "Marcus", "NT", ALL),
    ("LUK", "LUK", "lucas", "Luke", "Lucas", "NT", ALL),
    ("JHN", "JOH", "johannes", "John", "Johannes", "NT", ALL),
    ("ACT", "ACT", "handelingen", "Acts", "Handelingen", "NT", ALL),
    ("ROM", "ROM", "romeinen", "Romans", "Romeinen", "NT", ALL),
    ("1CO", "1CO", "1korintiers", "1 Corinthians", "1 Korintiërs", "NT", ALL),
    ("2CO", "2CO", "2korintiers", "2 Corinthians", "2 Korintiërs", "NT", ALL),
    ("GAL", "GAL", "galaten", "Galatians", "Galaten", "NT", ALL),
    ("EPH", "EPH", "efeziers", "Ephesians", "Efeziërs", "NT", ALL),
    ("PHP", "PHI", "filippenzen", "Philippians", "Filippenzen", "NT", ALL),
    ("COL", "COL", "kolossenzen", "Colossians", "Kolossenzen", "NT", ALL),
    ("1TH", "1TH", "1thessalonicenzen", "1 Thessalonians", "1 Tessalonicenzen", "NT", ALL),
    ("2TH", "2TH", "2thessalonicenzen", "2 Thessalonians", "2 Tessalonicenzen", "NT", ALL),
    ("1TI", "1TI", "1timotheus", "1 Timothy", "1 Timoteüs", "NT", ALL),
    ("2TI", "2TI", "2timotheus", "2 Timothy", "2 Timoteüs", "NT", ALL),
    ("TIT", "TIT", "titus", "Titus", "Titus", "NT", ALL),
    ("PHM", "PHM", "filemon", "Philemon", "Filemon", "NT", ALL),
    ("HEB", "HEB", "hebreen", "Hebrews", "Hebreeën", "NT", ALL),
    ("JAS", "JAM", "jakobus", "James", "Jakobus", "NT", ALL),
    ("1PE", "1PE", "1petrus", "1 Peter", "1 Petrus", "NT", ALL),
    ("2PE", "2PE", "2petrus", "2 Peter", "2 Petrus", "NT", ALL),
    ("1JN", "1JO", "1johannes", "1 John", "1 Johannes", "NT", ALL),
    ("2JN", "2JO", "2johannes", "2 John", "2 Johannes", "NT", ALL),
    ("3JN", "3JO", "3johannes", "3 John", "3 Johannes", "NT", ALL),
    ("JUD", "JUD", "judas", "Jude", "Judas", "NT", ALL),
    ("REV", "REV", "openbaring", "Revelation", "Openbaring", "NT", ALL),
]
DEUTERO = {"1ES", "TOB", "JDT", "ESG", "1MA", "2MA", "3MA", "PS2", "MAN", "WIS", "SIR", "BAR", "S3Y", "SUS", "BEL", "4MA"}

SLUG_TO_USFM = {b[2]: b[0] for b in BOOKS if b[2]}
SLUG_TO_USFM["hebreeen"] = "HEB"  # spelling used in some legacy cross-links
WEB_TO_USFM = {b[1]: b[0] for b in BOOKS if b[1]}

OSIS_TO_USFM = {
    "Gen": "GEN", "Exod": "EXO", "Lev": "LEV", "Num": "NUM", "Deut": "DEU", "Josh": "JOS", "Judg": "JDG",
    "Ruth": "RUT", "1Sam": "1SA", "2Sam": "2SA", "1Kgs": "1KI", "2Kgs": "2KI", "1Chr": "1CH", "2Chr": "2CH",
    "Ezra": "EZR", "Neh": "NEH", "Esth": "EST", "Job": "JOB", "Ps": "PSA", "Prov": "PRO", "Eccl": "ECC",
    "Song": "SNG", "Isa": "ISA", "Jer": "JER", "Lam": "LAM", "Ezek": "EZK", "Dan": "DAN", "Hos": "HOS",
    "Joel": "JOL", "Amos": "AMO", "Obad": "OBA", "Jonah": "JON", "Mic": "MIC", "Nah": "NAM", "Hab": "HAB",
    "Zeph": "ZEP", "Hag": "HAG", "Zech": "ZEC", "Mal": "MAL", "Matt": "MAT", "Mark": "MRK", "Luke": "LUK",
    "John": "JHN", "Acts": "ACT", "Rom": "ROM", "1Cor": "1CO", "2Cor": "2CO", "Gal": "GAL", "Eph": "EPH",
    "Phil": "PHP", "Col": "COL", "1Thess": "1TH", "2Thess": "2TH", "1Tim": "1TI", "2Tim": "2TI",
    "Titus": "TIT", "Phlm": "PHM", "Heb": "HEB", "Jas": "JAS", "1Pet": "1PE", "2Pet": "2PE", "1John": "1JN",
    "2John": "2JN", "3John": "3JN", "Jude": "JUD", "Rev": "REV",
}


def download(url, path):
    if os.path.exists(path):
        return
    print("downloading", url)
    with urllib.request.urlopen(url) as r:
        data = r.read()
    if url.endswith(".zip"):
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            name = next(n for n in z.namelist() if n.endswith(os.path.basename(path)))
            data = z.read(name)
    with open(path, "wb") as f:
        f.write(data)


def read_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def load_kyrie():
    """tools/kyrie.py, loaded by path because `python -I` leaves the script directory off sys.path."""
    import importlib.util
    spec = importlib.util.spec_from_file_location("kyrie", os.path.join(ROOT, "tools", "kyrie.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def write_json(name, data):
    if name.startswith("prayers"):
        data = load_kyrie().apply(data)  # Κύριε ἐλέησον stays in Greek
    path = os.path.join(ASSETS, "content", name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    print("wrote", os.path.relpath(path, ROOT))


def tr(en, nl):
    return {"en": en or nl or "", "nl": nl or en or ""}


# Dutch names as in books.json; the legacy Dutch texts mix in older spellings.
NL_NAMES = [(r"\bTimotheüs\b", "Timoteüs"), (r"\bMarkus\b", "Marcus"), (r"\bMattheüs\b", "Matteüs"), (r"\bSamuel\b", "Samuël")]


def nl_names(text):
    for pattern, repl in NL_NAMES:
        text = re.sub(pattern, repl, text) if text else text
    return text


# WEB marks some words with footnote-free brackets and pilcrows; keep the text clean for reading.
WEB_CLEAN = re.compile(r"\s*¶\s*")


def load_web():
    path = os.path.join(CACHE, "eng-web_vpl.txt")
    download("https://ebible.org/Scriptures/eng-web_vpl.zip", path)
    rows = []
    line_re = re.compile(r"^(\S+) (\d+):(\d+) (.*)$")
    with open(path, encoding="utf-8-sig") as f:
        for line in f:
            m = line_re.match(line.rstrip("\n"))
            if not m:
                continue
            code, ch, v, text = m.groups()
            usfm = WEB_TO_USFM.get(code)
            if not usfm:
                continue  # Hebrew Daniel and 2 Esdras are not part of any of the three canons we show
            rows.append(("web", usfm, int(ch), int(v), WEB_CLEAN.sub(" ", text).strip()))
    return rows


def load_sv():
    rows = []
    with open(os.path.join(SRC, "statenvertaling.tsv"), encoding="utf-8") as f:
        for line in f:
            if line.startswith("#"):
                continue
            slug, ch, v, text = line.rstrip("\n").split("\t", 3)
            usfm = SLUG_TO_USFM.get(slug)
            if usfm:
                rows.append(("sv", usfm, int(ch), int(v), text.strip()))
    return rows


def load_cross_refs():
    path = os.path.join(CACHE, "cross_references.txt")
    download("https://a.openbible.info/data/cross-references.zip", path)

    def parse(ref):
        book, ch, v = ref.split(".")
        return OSIS_TO_USFM.get(book), int(ch), int(v)

    rows = []
    with open(path, encoding="utf-8") as f:
        next(f)
        for line in f:
            frm, to, votes = line.rstrip("\n").split("\t")[:3]
            if int(votes) < MIN_CROSS_REF_VOTES:
                continue
            fb, fc, fv = parse(frm)
            start, _, end = to.partition("-")
            tb, tc, tv = parse(start)
            te = parse(end)[2] if end and parse(end)[1] == tc else tv
            if fb and tb:
                rows.append((fb, fc, fv, tb, tc, tv, te, int(votes)))
    return rows


# Fathers shown first when a verse has many commentators.
FATHER_PRIORITY = [
    "John Chrysostom", "Cyril of Alexandria", "Athanasius of Alexandria", "Basil of Caesarea", "Gregory of Nazianzus",
    "Gregory of Nyssa", "Ephrem the Syrian", "Augustine of Hippo", "Ambrose of Milan", "Irenaeus", "Ignatius of Antioch",
    "Maximus the Confessor", "John of Damascus", "Leo the Great", "Gregory the Dialogist", "Cyprian", "Hilary of Poitiers",
    "Isaac of Nineveh", "Severus of Antioch", "Philoxenus of Mabbug", "Jacob of Serugh", "Clement of Alexandria",
    "Theophylact of Ohrid", "Jerome", "Bede", "Didymus the Blind", "Cyril of Jerusalem", "Theodoret of Cyrus",
]
EXCLUDED_CATEGORIES = ("Reformation & Modern", "Apocrypha, Pseudepigrapha & Early Documents", "Pseudonymous Works",
                       "Second Temple Judaism", "Canonical Scriptures")
QUOTES_PER_VERSE = 2
QUOTE_MAX_CHARS = 520


def load_father_quotes():
    """Pre-1100 patristic commentary per verse from HistoricalChristianFaith/Commentaries-Database."""
    path = os.path.join(CACHE, "commentaries.sqlite")
    download("https://github.com/HistoricalChristianFaith/Commentaries-Database/releases/download/latest/commentaries.sqlite", path)
    by_name = {re.sub(r"[^a-z0-9]", "", b[3].lower()): b[0] for b in BOOKS}
    by_name.update({"songofsolomon": "SNG", "prayerofazariah": "S3Y"})
    rank = {n: i for i, n in enumerate(FATHER_PRIORITY)}
    src = sqlite3.connect(path)
    rows = src.execute(
        f"""SELECT c.father_name, c.ts, c.book, c.location_start, c.location_end, c.txt, c.source_title
            FROM commentary c JOIN father_meta m ON m.name = c.father_name
            WHERE c.ts <= 1100 AND COALESCE(m.condemned_by_council, 0) = 0
              AND m.father_category NOT IN ({",".join("?" * len(EXCLUDED_CATEGORIES))})
              AND c.location_end - c.location_start <= 3""", EXCLUDED_CATEGORIES).fetchall()
    per_verse = {}
    for father, year, book, start, end, txt, source in rows:
        usfm = by_name.get(book)
        if not usfm:
            continue
        per_verse.setdefault((usfm, start), []).append((rank.get(father, 100), len(txt), father, year, end, txt, source))
    out = []
    for (usfm, start), quotes in per_verse.items():
        seen = set()
        for _, _, father, year, end, txt, source in sorted(quotes):
            if father in seen:
                continue
            seen.add(father)
            txt = re.sub(r"\s+", " ", txt).strip().strip('"')
            if len(txt) > QUOTE_MAX_CHARS:
                txt = txt[:QUOTE_MAX_CHARS].rsplit(" ", 1)[0] + " …"
            out.append((usfm, start // 1000000, start % 1000000, end % 1000000, father, int(year or 0), source or "", txt))
            if len(seen) == QUOTES_PER_VERSE:
                break
    return out


def related_json(raw):
    out = []
    for r in json.loads(raw or "[]"):
        book = SLUG_TO_USFM.get(r.get("bookId"))
        if book:
            out.append({"book": book, "chapter": r.get("chapter"), "verse": r.get("verse")})
    return json.dumps(out)


def build_db():
    out = os.path.join(ASSETS, "bible", "bible.db")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    if os.path.exists(out):
        os.remove(out)
    db = sqlite3.connect(out)
    db.executescript(
        """
        CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE verses (
            translation TEXT NOT NULL, book TEXT NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL,
            text TEXT NOT NULL, PRIMARY KEY (translation, book, chapter, verse)) WITHOUT ROWID;
        CREATE TABLE cross_refs (
            book TEXT NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL,
            to_book TEXT NOT NULL, to_chapter INTEGER NOT NULL, to_verse INTEGER NOT NULL,
            to_verse_end INTEGER NOT NULL, votes INTEGER NOT NULL);
        CREATE INDEX cross_refs_from ON cross_refs (book, chapter, verse);
        CREATE TABLE chapter_guides (
            book TEXT NOT NULL, chapter INTEGER NOT NULL, lang TEXT NOT NULL,
            title TEXT NOT NULL, summary TEXT NOT NULL, context TEXT NOT NULL, themes TEXT NOT NULL,
            related TEXT NOT NULL, PRIMARY KEY (book, chapter, lang)) WITHOUT ROWID;
        CREATE TABLE father_notes (
            book TEXT NOT NULL, chapter INTEGER NOT NULL, father TEXT NOT NULL, work TEXT NOT NULL,
            date TEXT NOT NULL, verse_from INTEGER NOT NULL, verse_to INTEGER NOT NULL,
            note_en TEXT NOT NULL, note_nl TEXT NOT NULL, source TEXT NOT NULL, sort INTEGER NOT NULL);
        CREATE INDEX father_notes_chapter ON father_notes (book, chapter);
        CREATE TABLE father_quotes (
            book TEXT NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL, verse_end INTEGER NOT NULL,
            father TEXT NOT NULL, year INTEGER NOT NULL, source TEXT NOT NULL, text TEXT NOT NULL);
        CREATE INDEX father_quotes_verse ON father_quotes (book, chapter, verse);
        """
    )
    verses = load_web() + load_sv()
    db.executemany("INSERT OR REPLACE INTO verses VALUES (?,?,?,?,?)", verses)
    refs = load_cross_refs()
    db.executemany("INSERT INTO cross_refs VALUES (?,?,?,?,?,?,?,?)", refs)

    guides = 0
    with open(os.path.join(SRC, "chapter_guides.jsonl"), encoding="utf-8") as f:
        for line in f:
            g = json.loads(line)
            book = SLUG_TO_USFM.get(g["book_id"])
            if not book:
                continue
            for lang, sfx in (("en", ""), ("nl", "_nl")):
                title = g.get("title" + sfx) or ""
                if not title:
                    continue
                db.execute(
                    "INSERT OR REPLACE INTO chapter_guides VALUES (?,?,?,?,?,?,?,?)",
                    (book, g["chapter"], lang, title, g.get("summary" + sfx) or "",
                     g.get("context" + sfx) or g.get("purpose" + sfx) or "",
                     g.get("themes" + sfx) or "[]", related_json(g.get("related" + sfx))),
                )
                guides += 1

    notes = 0
    with open(os.path.join(SRC, "father_notes.jsonl"), encoding="utf-8") as f:
        for line in f:
            n = json.loads(line)
            book = SLUG_TO_USFM.get(n["book_id"])
            if not book:
                continue
            m = re.match(r"(\d+)(?:-(\d+))?", n.get("verses") or "")
            vf = int(m.group(1)) if m else 1
            vt = int(m.group(2) or m.group(1)) if m else 999
            db.execute(
                "INSERT INTO father_notes VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                (book, n["chapter"], n["father"], n["work"], n["date"], vf, vt,
                 n["commentary"], n.get("commentary_nl") or "", n["source"], n["sort"]),
            )
            notes += 1

    quotes = load_father_quotes()
    db.executemany("INSERT INTO father_quotes VALUES (?,?,?,?,?,?,?,?)", quotes)
    print("father quotes", len(quotes))

    db.executemany("INSERT INTO meta VALUES (?,?)", [
        ("version", str(DB_VERSION)),
        ("web", "World English Bible Classic (public domain), ebible.org"),
        ("sv", "Statenvertaling 1637 met apocriefen (publiek domein)"),
        ("cross_refs", "OpenBible.info cross-references, CC-BY"),
        ("father_quotes", "HistoricalChristianFaith/Commentaries-Database (historicalchristian.faith)"),
    ])
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(f"bible.db: {len(verses)} verses, {len(refs)} cross-refs, {guides} guides, {notes} father notes,"
          f" {os.path.getsize(out) // 1024} KB")
    return verses


def build_books(verses):
    chapters = {}
    for translation, book, ch, _v, _t in verses:
        key = (translation, book)
        chapters[key] = max(chapters.get(key, 0), ch)
    legacy = {b["id"]: b for b in read_json(os.path.join(SRC, "legacy", "canon.json"))["books"]}
    books = []
    for order, (usfm, _web, slug, en, nl, testament, traditions) in enumerate(BOOKS):
        lb = legacy.get(slug or "", {})
        books.append({
            "id": usfm,
            "order": order,
            "testament": testament,
            "deuterocanonical": usfm in DEUTERO,
            "traditions": traditions,
            "name": tr(en, nl),
            "intro": tr(lb.get("introEn"), nl_names(lb.get("intro"))) if lb else None,
            "chapters": {"en": chapters.get(("web", usfm), 0), "nl": chapters.get(("sv", usfm), 0)},
        })
    write_json("books.json", books)


AGPEYA_NAMES = {
    "lauden": ("prime", "Prime", "Priem", "The First Hour", "Het eerste uur"),
    "terts": ("terce", "Terce", "Terts", "The Third Hour", "Het derde uur"),
    "sext": ("sext", "Sext", "Sext", "The Sixth Hour", "Het zesde uur"),
    "none": ("none", "None", "None", "The Ninth Hour", "Het negende uur"),
    "vespers": ("vespers", "Vespers", "Vespers", "The Eleventh Hour", "Het elfde uur"),
    "completen": ("compline", "Compline", "Completen", "The Twelfth Hour", "Het twaalfde uur"),
    "voorhangsel": ("veil", "The Veil", "Het voorhangsel", "Monastic prayer after Compline", "Monastiek gebed na de Completen"),
    "middernacht": ("midnight", "Midnight", "Middernacht", "The three watches of the night", "De drie nachtwaken"),
}


def build_agpeya():
    hours = read_json(os.path.join(SRC, "legacy", "agpeya-hours.json"))
    offices = read_json(os.path.join(SRC, "legacy", "agpeya-offices.json"))
    out = []
    for h in hours:
        hid, en, nl, sub_en, sub_nl = AGPEYA_NAMES[h["id"]]
        sections = [{
            "kind": "text",
            "title": tr(s.get("kickerEn"), s.get("kicker")),
            "body": {"en": s.get("linesEn") or s.get("lines"), "nl": s.get("lines") or s.get("linesEn")},
        } for s in offices.get(h["id"], [])]
        out.append({
            "id": hid,
            "name": tr(en, nl),
            "subtitle": tr(sub_en, sub_nl),
            "time": "%02d:%02d" % (int(h["hour"]), int(h["minute"])),
            "theme": tr(h.get("teaserEn"), h.get("teaser")),
            "sections": sections,
        })
    write_json("prayers_oo.json", {
        "tradition": OO,
        "book": tr("Agpeya", "Agpeya"),
        "intro": tr(
            "The Agpeya is the Coptic Orthodox Book of Hours. Seven hours of prayer, plus the Veil and the "
            "midnight watches, sanctify the day by remembering Christ's life, death and resurrection.",
            "De Agpeya is het Koptisch-Orthodoxe getijdenboek. Zeven gebedsuren, met het Voorhangsel en de "
            "nachtwaken, heiligen de dag door het leven, de dood en de opstanding van Christus te gedenken."),
        "hours": out,
    })


EXTRA_PRAYER_TRADITIONS = {
    "jezusgebed": [EO, OO], "onze-vader": ALL, "geloof": [EO, OO], "trisagion": [EO, OO],
    "theotokos": ALL, "efrem": [EO, OO], "lezen": ALL, "psalm50": ALL, "morgen": [EO, OO],
    "avond": [EO, OO], "pascha": [EO, OO], "optel": ALL,
}
EXTRA_PRAYER_IDS = {
    "jezusgebed": "jesus-prayer", "onze-vader": "our-father", "geloof": "creed-nicene", "trisagion": "trisagion",
    "theotokos": "sub-tuum", "efrem": "prayer-of-ephrem", "lezen": "before-scripture", "psalm50": "psalm-51",
    "morgen": "morning-prayer", "avond": "evening-prayer", "pascha": "paschal-troparion", "optel": "after-scripture",
}


def build_extra_prayers():
    legacy = read_json(os.path.join(SRC, "legacy", "prayers.json"))
    out = [{
        "id": EXTRA_PRAYER_IDS[p["id"]],
        "traditions": EXTRA_PRAYER_TRADITIONS[p["id"]],
        "title": tr(p.get("titleEn"), p.get("title")),
        "subtitle": tr(p.get("kickerEn"), p.get("kicker")),
        "body": {"en": p.get("bodyEn") or p.get("body"), "nl": p.get("body") or p.get("bodyEn")},
    } for p in legacy]
    # The Catholic form of the Creed (with the Filioque) and common Catholic prayers.
    out += [
        {
            "id": "creed-catholic", "traditions": [CA],
            "title": tr("The Nicene Creed", "De geloofsbelijdenis van Nicea"),
            "subtitle": tr("As prayed in the Roman Rite", "Zoals gebeden in de Romeinse ritus"),
            "body": {
                "en": [
                    "I believe in one God, the Father almighty, maker of heaven and earth, of all things visible and invisible.",
                    "I believe in one Lord Jesus Christ, the Only Begotten Son of God, born of the Father before all ages. God from God, Light from Light, true God from true God, begotten, not made, consubstantial with the Father; through him all things were made.",
                    "For us men and for our salvation he came down from heaven, and by the Holy Spirit was incarnate of the Virgin Mary, and became man.",
                    "For our sake he was crucified under Pontius Pilate, he suffered death and was buried, and rose again on the third day in accordance with the Scriptures. He ascended into heaven and is seated at the right hand of the Father. He will come again in glory to judge the living and the dead and his kingdom will have no end.",
                    "I believe in the Holy Spirit, the Lord, the giver of life, who proceeds from the Father and the Son, who with the Father and the Son is adored and glorified, who has spoken through the prophets.",
                    "I believe in one, holy, catholic and apostolic Church. I confess one Baptism for the forgiveness of sins and I look forward to the resurrection of the dead and the life of the world to come. Amen.",
                ],
                "nl": [
                    "Ik geloof in één God, de almachtige Vader, Schepper van hemel en aarde, van al wat zichtbaar en onzichtbaar is.",
                    "En in één Heer, Jezus Christus, eniggeboren Zoon van God, vóór alle tijden geboren uit de Vader. God uit God, Licht uit Licht, ware God uit de ware God. Geboren, niet geschapen, één in wezen met de Vader, en door wie alles geschapen is.",
                    "Hij is voor ons, mensen, en omwille van ons heil uit de hemel neergedaald. Hij heeft het vlees aangenomen door de heilige Geest uit de Maagd Maria, en is mens geworden.",
                    "Hij werd voor ons gekruisigd, Hij heeft geleden onder Pontius Pilatus en is begraven. Hij is verrezen op de derde dag, volgens de Schriften. Hij is opgevaren naar de hemel en zit aan de rechterhand van de Vader. Hij zal wederkomen in heerlijkheid om te oordelen levenden en doden, en aan zijn rijk komt geen einde.",
                    "Ik geloof in de heilige Geest, die Heer is en het leven geeft, die voortkomt uit de Vader en de Zoon; die met de Vader en de Zoon wordt aanbeden en verheerlijkt; Hij heeft gesproken door de profeten.",
                    "Ik geloof in de éne, heilige, katholieke en apostolische Kerk. Ik belijd één doopsel tot vergeving van de zonden. Ik verwacht de opstanding van de doden en het leven van het komend rijk. Amen.",
                ],
            },
        },
        {
            "id": "hail-mary", "traditions": [CA],
            "title": tr("Hail Mary", "Weesgegroet"),
            "subtitle": tr("The Angelic Salutation", "De engelse groet"),
            "body": {
                "en": ["Hail Mary, full of grace, the Lord is with thee. Blessed art thou among women, and blessed is the fruit of thy womb, Jesus.",
                       "Holy Mary, Mother of God, pray for us sinners, now and at the hour of our death. Amen."],
                "nl": ["Wees gegroet, Maria, vol van genade, de Heer is met U. Gezegend zijt Gij boven alle vrouwen, en gezegend is Jezus, de vrucht van uw schoot.",
                       "Heilige Maria, Moeder van God, bid voor ons, zondaars, nu en in het uur van onze dood. Amen."],
            },
        },
        {
            "id": "glory-be", "traditions": [CA],
            "title": tr("Glory Be", "Eer aan de Vader"),
            "subtitle": tr("The lesser doxology", "De kleine lofprijzing"),
            "body": {
                "en": ["Glory be to the Father, and to the Son, and to the Holy Spirit, as it was in the beginning, is now, and ever shall be, world without end. Amen."],
                "nl": ["Eer aan de Vader en de Zoon en de heilige Geest, zoals het was in het begin en nu en altijd en in de eeuwen der eeuwen. Amen."],
            },
        },
    ]
    # Short fixed openings used inside the hours; not listed on their own.
    out += [
        {
            "id": "usual-beginning", "traditions": [EO], "listed": False,
            "title": tr("The Usual Beginning", "Het gebruikelijke begin"), "subtitle": tr("", ""),
            "body": {
                "en": ["Glory to Thee, our God, glory to Thee.",
                       "O Heavenly King, Comforter, Spirit of Truth, who art everywhere present and fillest all things, Treasury of good things and Giver of life: come and abide in us, and cleanse us from every stain, and save our souls, O Good One.",
                       "Holy God, Holy Mighty, Holy Immortal, have mercy on us. (3 times)",
                       "Glory to the Father, and to the Son, and to the Holy Spirit, now and ever, and unto ages of ages. Amen.",
                       "All-holy Trinity, have mercy on us. Lord, cleanse us from our sins. Master, pardon our transgressions. Holy One, visit and heal our infirmities, for Thy name's sake.",
                       "Lord, have mercy. (3 times)",
                       "Glory to the Father, and to the Son, and to the Holy Spirit, now and ever, and unto ages of ages. Amen.",
                       "Our Father, who art in heaven, hallowed be Thy name. Thy kingdom come, Thy will be done, on earth as it is in heaven. Give us this day our daily bread, and forgive us our trespasses, as we forgive those who trespass against us. And lead us not into temptation, but deliver us from the evil one.",
                       "Through the prayers of our holy fathers, Lord Jesus Christ our God, have mercy on us. Amen."],
                "nl": ["Eer zij U, onze God, eer zij U.",
                       "Hemelse Koning, Trooster, Geest der waarheid, die overal aanwezig bent en alles vervult, Schatkamer van het goede en Gever van het leven: kom en woon in ons, reinig ons van alle smet en red, o Goede, onze zielen.",
                       "Heilige God, heilige Sterke, heilige Onsterfelijke, ontferm U over ons. (3 maal)",
                       "Eer aan de Vader en de Zoon en de Heilige Geest, nu en altijd en tot in de eeuwen der eeuwen. Amen.",
                       "Allerheiligste Drie-eenheid, ontferm U over ons. Heer, reinig ons van onze zonden. Meester, vergeef ons onze overtredingen. Heilige, bezoek en genees onze zwakheden, omwille van Uw Naam.",
                       "Heer, ontferm U. (3 maal)",
                       "Eer aan de Vader en de Zoon en de Heilige Geest, nu en altijd en tot in de eeuwen der eeuwen. Amen.",
                       "Onze Vader, die in de hemelen zijt, Uw Naam worde geheiligd. Uw Koninkrijk kome. Uw wil geschiede op aarde zoals in de hemel. Geef ons heden ons dagelijks brood, en vergeef ons onze schulden, zoals ook wij vergeven aan onze schuldenaren. En leid ons niet in bekoring, maar verlos ons van de boze.",
                       "Door de gebeden van onze heilige vaders, Heer Jezus Christus, onze God, ontferm U over ons. Amen."],
            },
        },
        {
            "id": "come-let-us-worship", "traditions": [EO], "listed": False,
            "title": tr("Come, let us worship", "Komt, laat ons aanbidden"), "subtitle": tr("", ""),
            "body": {
                "en": ["O come, let us worship God our King.", "O come, let us worship and fall down before Christ, our King and our God.", "O come, let us worship and fall down before Christ Himself, our King and our God."],
                "nl": ["Komt, laat ons aanbidden God, onze Koning.", "Komt, laat ons aanbidden en neervallen voor Christus, onze Koning en onze God.", "Komt, laat ons aanbidden en neervallen voor Christus Zelf, onze Koning en onze God."],
            },
        },
        {
            "id": "deus-in-adiutorium", "traditions": [CA], "listed": False,
            "title": tr("Opening", "Opening"), "subtitle": tr("", ""),
            "body": {
                "en": ["O God, come to my assistance. — O Lord, make haste to help me.", "Glory be to the Father, and to the Son, and to the Holy Spirit, as it was in the beginning, is now, and ever shall be, world without end. Amen."],
                "nl": ["God, kom mij te hulp. — Heer, haast U mij te helpen.", "Eer aan de Vader en de Zoon en de heilige Geest, zoals het was in het begin en nu en altijd en in de eeuwen der eeuwen. Amen."],
            },
        },
    ]
    write_json("prayers_extra.json", out)


# The legacy English titles are partly Dutch ("Polyeuctus van Melitene", "of the zeventig"); these put them right.
EN_TITLE_FIXES = [
    (r"\bof the zeventig\b", "of the Seventy"), (r"\bMartelaren\b", "Martyrs"), (r"\bleerling van Gregorius\b", "disciple of Gregory"),
    (r"\bOude Grotten\b", "Old Caves"), (r"\bde Pers\b", "the Persian"), (r"\bde martelaar\b", "the Martyr"),
    (r"\bde Wonderdoener\b", "the Wonderworker"), (r"\bde Hymnograaf\b", "the Hymnographer"), (r"\bde Stratelates\b", "the Stratelates"),
    (r"\bde Goth\b", "the Goth"), (r"\ben\b", "and"), (r"\bvan\b", "of"),
    (r"Jordaan", "Jordan"), (r"Isaurië", "Isauria"), (r"Nicomedië", "Nicomedia"), (r"Korinthe", "Corinth"), (r"Illyrië", "Illyria"),
    (r"Azië", "Asia"), (r"Hiërapolis", "Hierapolis"), (r"Sinaï", "Sinai"), (r"\bThebe\b", "Thebes"), (r"Micah[eë]l", "Michael"),
    (r"Isaäc", "Isaac"), (r"Dorotheüs", "Dorotheus"), (r"Hermolaüs", "Hermolaus"), (r"Sebastiaan", "Sebastian"),
    (r"Filemon", "Philemon"), (r"Ismaël", "Ismael"), (r"Georgius", "George"), (r"Markus", "Mark"), (r"Nicétas", "Nicetas"),
    (r"\bDamianus\b", "Damian"),
]


def build_saints_eo():
    legacy = read_json(os.path.join(SRC, "legacy", "menologion.json"))
    # Hand-written English summaries (the legacy English ones were a placeholder sentence) and title corrections.
    en = read_json(os.path.join(SRC, "menologion_en.json"))
    out = []
    for e in legacy["entries"]:
        key = f"{int(e['month']):02d}-{int(e['day']):02d}"
        title_en = e.get("titleEn") or ""
        for pattern, repl in EN_TITLE_FIXES:
            title_en = re.sub(pattern, repl, title_en)
        title_en = en["titles"].get(key, title_en)
        title_nl = nl_names(en.get("titles_nl", {}).get(key, e.get("title")))
        out.append({
            "month": int(e["month"]), "day": int(e["day"]), "rank": e.get("rank", "saint"),
            "title": tr(title_en, title_nl),
            "summary": tr(en["summaries"].get(key) or e.get("explanationEn") or e.get("teaserEn"),
                          nl_names(e.get("explanation") or e.get("teaser"))),
        })
    write_json("saints_eo.json", out)


if __name__ == "__main__":
    import sys
    if "--saints" in sys.argv:  # only the menologion, without downloading the Bibles again
        build_saints_eo()
        sys.exit()
    os.makedirs(CACHE, exist_ok=True)
    rows = build_db()
    build_books(rows)
    build_extra_prayers()
    build_saints_eo()
