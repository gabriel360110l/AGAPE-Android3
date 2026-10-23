#!/usr/bin/env python3
"""Builds the full prayer books.

  prayers_oo.json  The Coptic Agpeya, every hour in full, from api.coptic.io (English). Dutch comes from
                   tools/source/translations_nl.json; psalms and Gospels fall back to the Statenvertaling via `ref`.
  prayers_eo.json  The Horologion for home use: the Hours composed from the Usual Beginning, psalms and troparia,
                   plus the morning rule, Compline and the evening rule set from Orthodox Daily Prayers (St Tikhon's,
                   1982, public domain via Project Gutenberg) as published by mdws-org/orthodoxprayers.

Dutch for the Horologion texts that have no Dutch in the sources lives in tools/source/prayers_eo_nl.json (English
text → Dutch, the editors' own provisional translation) and is filled in when the file is written.

Downloads are cached in tools/.cache/src. Run from the repository root:  python tools/build_prayers.py
To fill in Dutch from prayers_eo_nl.json without downloading anything:  python tools/build_prayers.py --nl-only
"""
import html
import json
import os
import re
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "tools", ".cache", "src")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "content")
NL = json.load(open(os.path.join(ROOT, "tools", "source", "translations_nl.json"), encoding="utf-8"))
BOOKS = json.load(open(os.path.join(OUT, "books.json"), encoding="utf-8"))
COPTIC = re.compile("[Ⲁ-⳿Ϣ-ϯ]")

WEEKDAYS = {"monday": 1, "tuesday": 2, "wednesday": 3, "thursday": 4, "friday": 5, "saturday": 6, "sunday": 7}


def fetch(url, name):
    path = os.path.join(SRC, name)
    if not os.path.exists(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        print("downloading", url)
        with urllib.request.urlopen(url) as r, open(path, "wb") as f:
            f.write(r.read())
    return open(path, encoding="utf-8").read()


def tr(en, nl=None):
    en = (en or "").strip()
    return {"en": en, "nl": nl if nl is not None else NL.get(en, "")}


def lines(en_list, nl_list=None):
    en_list = [s.strip() for s in en_list if s and s.strip()]
    if nl_list is None:
        # Coptic lines are prayed as they are in every language.
        nl_list = [s if COPTIC.search(s) else NL.get(s, "") for s in en_list]
        if not all(nl_list):
            nl_list = []
    return {"en": en_list, "nl": nl_list}


def lxx_to_hebrew(n):
    """Septuagint psalm number → Hebrew/Protestant chapter(s) used by WEB and the Statenvertaling."""
    if n <= 8 or n >= 148:
        return f"PSA {n}"
    if n == 9:
        return "PSA 9-10"
    if n <= 112:
        return f"PSA {n + 1}"
    if n == 113:
        return "PSA 114-115"
    if n == 114:
        return "PSA 116:1-9"
    if n == 115:
        return "PSA 116:10-19"
    if n <= 145:
        return f"PSA {n + 1}"
    return "PSA 147:1-11" if n == 146 else "PSA 147:12-20"


BOOK_BY_NAME = {b["name"]["en"].lower(): b["id"] for b in BOOKS}
BOOK_BY_NAME.update({"psalm": "PSA", "song of solomon": "SNG"})


def english_ref(reference):
    """'John 1:1-17' / 'Psalm 50' (Septuagint numbering) → app passage notation."""
    m = re.match(r"^\s*(\d?\s?[A-Za-z ]+?)\s+(\d+)(?::(\d+)(?:-(\d+))?)?\s*$", reference or "")
    if not m:
        return None
    name, ch, v1, v2 = m.groups()
    book = BOOK_BY_NAME.get(name.strip().lower())
    if book == "PSA" and not v1:
        return lxx_to_hebrew(int(ch))
    if not book:
        return None
    if v1 and v2:
        return f"{book} {ch}:{v1}-{v2}"
    return f"{book} {ch}:{v1}" if v1 else f"{book} {ch}"


def text(title, body, **extra):
    s = {"kind": "text", "title": tr(title) if isinstance(title, str) else title, "body": body}
    s.update(extra)
    return s


def rubric(en, nl=None):
    return {"kind": "rubric", "body": lines([en], [nl] if nl else None)}


def heading(title, subtitle=None):
    s = {"kind": "heading", "title": tr(title) if isinstance(title, str) else title}
    if subtitle:
        s["body"] = lines([subtitle])
    return s


def counter(count, en="Lord, have mercy.", nl="Heer, ontferm U.", title=None):
    return {
        "kind": "counter", "count": count,
        "title": title or tr(f"Lord, have mercy ({count} times)", f"Heer, ontferm U ({count} maal)"),
        "body": {"en": [en], "nl": [nl]},
    }


def scripture(title, ref, verses_en=None):
    s = {"kind": "scripture", "title": tr(title) if isinstance(title, str) else title, "ref": ref}
    if verses_en:
        s["body"] = {"en": verses_en, "nl": []}
    return s


def prayer(pid):
    return {"kind": "prayer", "prayerId": pid}


# ---------------------------------------------------------------- Agpeya

AGPEYA_TIMES = {"prime": "06:00", "terce": "09:00", "sext": "12:00", "none": "15:00",
                "vespers": "17:00", "compline": "18:00", "midnight": "00:00"}


def agpeya_block(b):
    if not b:
        return []
    if "verses" in b:
        ref = english_ref(b.get("reference") or b.get("title"))
        verses = [v["text"].strip() for v in b["verses"]]
        out = []
        if b.get("rubric"):
            out.append(rubric(b["rubric"]))
        title = b.get("title") or b.get("reference") or ""
        is_gospel = title == "" and b.get("reference")
        out.append(scripture(
            tr(f"Gospel · {b['reference']}", f"Evangelie · {b['reference']}") if is_gospel else tr(title, NL.get(title) or title.replace("Psalm", "Psalm")),
            ref, verses))
        return out
    if b.get("id") == "kyrie41":
        return [counter(41, "Lord, have mercy.", "Heer, ontferm U.", tr("Lord have mercy (41 times)", "Heer, ontferm U (41 maal)"))]
    return [text(b.get("title", ""), lines(b.get("content", [])))]


def agpeya_hour(d):
    sections = []
    if d.get("introduction"):
        sections.append(rubric(d["introduction"]))
    for key in ("opening", "hourIntro", "comeLetUsWorship", "thanksgiving", "introductoryPsalm"):
        sections += agpeya_block(d.get(key))

    def body(part):
        out = []
        if part.get("psalmsIntro"):
            out.append(rubric(part["psalmsIntro"]))
        for p in part.get("psalms", []):
            out += agpeya_block(p)
        out += agpeya_block(part.get("gospel"))
        for key in ("gospelConclusion", "litanies"):
            out += agpeya_block(part.get(key))
        return out

    if "watches" in d:
        for w in d["watches"]:
            sections.append(heading(w["name"], w.get("theme")))
            sections += body(w)
            for c in w.get("conclusion", []):
                sections += agpeya_block(c)
        sections.append(heading("Conclusion", None) | {"title": tr("Conclusion", "Slot")})
        for c in d.get("conclusion", []):
            sections += agpeya_block(c)
    else:
        sections += body(d)
        for c in d.get("conclusion", []):
            sections += agpeya_block(c)
    return {
        "id": d["id"],
        "name": tr(d["name"]),
        "subtitle": tr(d.get("englishName", "")),
        "time": AGPEYA_TIMES[d["id"]],
        "theme": tr(d.get("introduction", "")),
        "sections": sections,
    }


def build_agpeya():
    hours = []
    for h in ["prime", "terce", "sext", "none", "vespers", "compline", "midnight"]:
        d = json.loads(fetch(f"https://api.coptic.io/api/agpeya/{h}", f"agpeya_{h}.json"))
        hours.append(agpeya_hour(d))
    # The Veil is not in the API; keep the version from the legacy app.
    legacy = json.load(open(os.path.join(ROOT, "tools", "source", "legacy", "agpeya-offices.json"), encoding="utf-8"))
    hours.insert(6, {
        "id": "veil", "time": "21:00",
        "name": tr("The Veil", "Het Voorhangsel"),
        "subtitle": tr("Monastic prayer after Compline", "Monastiek gebed na de Completen"),
        "theme": tr("Search the heart before sleep.", "Doorzoek het hart voor het slapengaan."),
        "sections": [text(tr(s.get("kickerEn"), s.get("kicker")), {"en": s.get("linesEn") or [], "nl": s.get("lines") or []})
                     for s in legacy["voorhangsel"]],
    })
    missing = sum(1 for h in hours for s in h["sections"] if s.get("body") and s["kind"] in ("text", "rubric") and not s["body"]["nl"])
    write("prayers_oo.json", {
        "tradition": "ORIENTAL_ORTHODOX",
        "book": tr("Agpeya", "Agpeya"),
        "intro": tr(
            "The Agpeya is the Coptic Orthodox Book of Hours. Seven hours of prayer, with the Veil and the three "
            "midnight watches, sanctify the day by remembering Christ's resurrection, the descent of the Spirit, "
            "the Cross, His death, burial and second coming. Each hour has its psalms, a Gospel, litanies, "
            "Lord have mercy forty-one times and an absolution. Text: api.coptic.io.",
            "De Agpeya is het Koptisch-Orthodoxe getijdenboek. Zeven gebedsuren, met het Voorhangsel en de drie "
            "nachtwaken, heiligen de dag door de verrijzenis van Christus, de neerdaling van de Geest, het Kruis, "
            "Zijn dood, begrafenis en wederkomst te gedenken. Elk uur heeft zijn psalmen, een Evangelie, "
            "smeekgebeden, eenenveertig maal Heer, ontferm U en een absolutie. Psalmen en Evangelie: Statenvertaling."),
        "hours": hours,
        "extras": [],
    })
    print("agpeya: untranslated text sections", missing)


# ---------------------------------------------------------------- Horologion

OP = "https://raw.githubusercontent.com/mdws-org/orthodoxprayers/main/"

# Dutch for the prayers that recur throughout the Horologion, keyed by the start of the English text.
EO_NL = [
    ("o heavenly king", "Hemelse Koning, Trooster, Geest der waarheid, die overal aanwezig bent en alles vervult, Schatkamer van het goede en Gever van het leven: kom en woon in ons, reinig ons van alle smet en red, o Goede, onze zielen."),
    ("holy god holy mighty", "Heilige God, heilige Sterke, heilige Onsterfelijke, ontferm U over ons. (3 maal)"),
    ("glory to the father and to the son and to the holy spirit now and ever", "Eer aan de Vader en de Zoon en de Heilige Geest, nu en altijd en tot in de eeuwen der eeuwen. Amen."),
    ("glory to the father and to the son and to the holy spirit", "Eer aan de Vader en de Zoon en de Heilige Geest,"),
    ("now and ever and unto ages of ages amen", "nu en altijd en tot in de eeuwen der eeuwen. Amen."),
    ("all holy trinity", "Allerheiligste Drie-eenheid, ontferm U over ons. Heer, reinig ons van onze zonden. Meester, vergeef ons onze overtredingen. Heilige, zie neer en genees onze zwakheden, omwille van Uw Naam."),
    ("o most holy trinity", "Allerheiligste Drie-eenheid, ontferm U over ons. Heer, reinig ons van onze zonden. Meester, vergeef ons onze overtredingen. Heilige, bezoek en genees onze zwakheden, omwille van Uw Naam."),
    ("lord have mercy 3 times", "Heer, ontferm U. (3 maal)"),
    ("lord have mercy thrice", "Heer, ontferm U. (3 maal)"),
    ("our father", "Onze Vader, die in de hemelen zijt, Uw Naam worde geheiligd. Uw Koninkrijk kome. Uw wil geschiede op aarde zoals in de hemel. Geef ons heden ons dagelijks brood, en vergeef ons onze schulden, zoals ook wij vergeven aan onze schuldenaren. En leid ons niet in bekoring, maar verlos ons van de boze."),
    ("for thine is the kingdom", "Want van U is het Koninkrijk en de kracht en de heerlijkheid, van de Vader en de Zoon en de Heilige Geest, nu en altijd en tot in de eeuwen der eeuwen."),
    ("come let us worship god our king", "Komt, laat ons aanbidden God, onze Koning."),
    ("come let us worship and fall down before christ our king", "Komt, laat ons aanbidden en neervallen voor Christus, onze Koning en onze God."),
    ("come let us worship and fall down before christ himself", "Komt, laat ons aanbidden en neervallen voor Christus Zelf, onze Koning en onze God."),
    ("it is truly meet", "Het is waarlijk passend u zalig te prijzen, Moeder Gods, altijd zalig en geheel onbevlekt, en Moeder van onze God. Eerbiedwaardiger dan de cherubijnen en onvergelijkelijk heerlijker dan de serafijnen, die ongeschonden God het Woord hebt gebaard: waarlijk Moeder Gods, u maken wij groot."),
    ("more honorable than the cherubim", "Eerbiedwaardiger dan de cherubijnen en onvergelijkelijk heerlijker dan de serafijnen, die ongeschonden God het Woord hebt gebaard: waarlijk Moeder Gods, u maken wij groot."),
    ("amen glory to thee our god glory to thee", "Amen. Eer zij U, onze God, eer zij U."),
    ("glory to thee our god glory to thee", "Eer zij U, onze God, eer zij U."),
    ("through the prayers of our holy fathers", "Door de gebeden van onze heilige vaders, Heer Jezus Christus, onze God, ontferm U over ons. Amen."),
    ("in the name of the father and of the son", "In de Naam van de Vader en de Zoon en de Heilige Geest. Amen."),
    ("lord jesus christ son of god have mercy on me a sinner", "Heer Jezus Christus, Zoon van God, ontferm U over mij, zondaar."),
    ("i believe in one god", "Ik geloof in één God, de almachtige Vader, Schepper van hemel en aarde, van al wat zichtbaar en onzichtbaar is."),
    ("and in one lord jesus christ", "En in één Heer, Jezus Christus, de eniggeboren Zoon van God, uit de Vader geboren vóór alle eeuwen; Licht uit Licht, ware God uit ware God; geboren, niet geschapen, één van wezen met de Vader, door wie alles geworden is; die voor ons, mensen, en omwille van ons heil uit de hemel is neergedaald, vlees is geworden uit de Heilige Geest en de Maagd Maria en mens is geworden; die ook voor ons gekruisigd is onder Pontius Pilatus, geleden heeft en begraven is; die op de derde dag verrezen is volgens de Schriften, opgevaren is ten hemel en zit aan de rechterhand van de Vader; die zal wederkomen in heerlijkheid om te oordelen levenden en doden, en aan wiens Rijk geen einde komt."),
    ("and in the holy spirit the lord the giver of life", "En in de Heilige Geest, de Heer, de Levendmaker, die uitgaat van de Vader, die met de Vader en de Zoon wordt aanbeden en verheerlijkt, die gesproken heeft door de profeten. In de ene, heilige, katholieke en apostolische Kerk. Ik belijd één doop tot vergeving van de zonden. Ik verwacht de opstanding van de doden en het leven van de komende eeuw. Amen."),
    ("rejoice o virgin theotokos", "Verheug u, Maagd en Moeder Gods, Maria, vol van genade, de Heer is met u. Gezegend zijt gij onder de vrouwen en gezegend is de vrucht van uw schoot, want gij hebt de Verlosser van onze zielen gebaard."),
    ("o lord save thy people", "Heer, red Uw volk en zegen Uw erfdeel; schenk Uw Kerk overwinning op de tegenstander en bewaar door Uw Kruis Uw gemeenschap."),
]


def key(s):
    return re.sub(r"[^a-z0-9 ]", "", re.sub(r"\s+", " ", s.lower().replace("!", " ").replace(",", " "))).strip()


def eo_nl(en):
    k = key(en)
    for prefix, nl in EO_NL:
        if k.startswith(prefix):
            return nl
    return ""


def eo_lines(en_list):
    nl = [eo_nl(s) for s in en_list]
    return {"en": en_list, "nl": nl if all(nl) else []}


COUNTER_RE = re.compile(r"^(?:Amen!?\s*)?Lord,? have mercy!?\.?\s*\((\d+|twelve|forty) times\)", re.I)
PSALM_RE = re.compile(r"^Psalm (\d+)$")


def html_sections(page):
    """Turns a page of orthodoxprayers.net into prayer sections."""
    raw = fetch(OP + page, "eo_" + page.replace("/", "_"))
    main = re.search(r"<main.*?</main>", raw, re.S).group(0)
    main = re.sub(r"<button[^>]*>(.*?)</button>", r"\1", main, flags=re.S)
    main = re.sub(r'<div class="(?:gloss-pop|popover)[^"]*".*?</div>', "", main, flags=re.S)
    out, current, weekday = [], None, None

    def flush():
        nonlocal current
        if current and current["en"]:
            title, en = current["title"], current["en"]
            m = PSALM_RE.match(title or "")
            if m:
                s = scripture(tr(f"Psalm {m.group(1)}", f"Psalm {m.group(1)}"), lxx_to_hebrew(int(m.group(1))), en)
            else:
                s = text(tr(title or "", eo_nl(title or "") or ""), eo_lines(en))
            if weekday:
                s["weekday"] = weekday
            out.append(s)
        current = None

    for tag in re.finditer(r"<(h1|h2|h3|p|div)([^>]*)>(.*?)</\1>", main, re.S):
        name, attrs, inner = tag.groups()
        cls = (re.search(r'class="([^"]*)"', attrs) or [None, ""])[1]
        t = html.unescape(re.sub(r"<[^>]+>", "", inner)).strip()
        t = re.sub(r"\s+", " ", t)
        if not t or name == "h1" or cls in ("section-label", "attribution", "head-cross", "inscription tone-jump"):
            continue
        if name == "div" and cls not in ("rubric-line", "inscription"):
            continue
        if name == "h2":
            flush()
            day = WEEKDAYS.get(t.lower().replace(" evening", "").strip())
            weekday = day
            if day:
                s = heading(t)
                s["weekday"] = day
                out.append(s)
            else:
                current = {"title": t, "en": []}
            continue
        if name == "h3":
            flush()
            s = heading(t)
            if weekday:
                s["weekday"] = weekday
            out.append(s)
            continue
        if name == "div":
            flush()
            r = rubric(t, "")
            if weekday:
                r["weekday"] = weekday
            out.append(r)
            continue
        m = COUNTER_RE.match(t)
        if m:
            flush()
            n = {"twelve": 12, "forty": 40}.get(m.group(1).lower()) or int(m.group(1))
            if n >= 12:
                c = counter(n)
                if weekday:
                    c["weekday"] = weekday
                out.append(c)
                continue
        if current is None:
            current = {"title": None, "en": []}
        current["en"].append(t)
    flush()
    return out


USUAL_BEGINNING = [prayer("usual-beginning")]
COME_WORSHIP = [prayer("come-let-us-worship")]


def small_hour(hid, time, name, subtitle, theme, psalms, troparion, hour_prayer):
    hours_page = html_sections("hours/index.html")
    prayer_of_hours = next(s for s in hours_page if s["kind"] == "text" and "at all times and at every hour" in " ".join(s["body"]["en"]))
    return {
        "id": hid, "time": time, "name": name, "subtitle": subtitle, "theme": theme,
        "sections": USUAL_BEGINNING + [counter(12)] + COME_WORSHIP
        + [scripture(tr(f"Psalm {n}", f"Psalm {n}"), lxx_to_hebrew(n)) for n in psalms]
        + [text(tr("Glory…", "Eer…"), lines(
            ["Glory to the Father, and to the Son, and to the Holy Spirit, now and ever, and unto ages of ages. Amen.",
             "Alleluia, alleluia, alleluia. Glory to Thee, O God. (3 times)"],
            ["Eer aan de Vader en de Zoon en de Heilige Geest, nu en altijd en tot in de eeuwen der eeuwen. Amen.",
             "Alleluia, alleluia, alleluia. Eer zij U, o God. (3 maal)"])),
            text(tr("Troparion of the hour", "Troparion van het uur"), troparion),
            counter(40),
            text(tr("Prayer of the Hours", "Gebed van de uren"), {"en": prayer_of_hours["body"]["en"], "nl": [
                "U die te allen tijde en op ieder uur, in de hemel en op aarde, wordt aanbeden en verheerlijkt, Christus God, lankmoedig, rijk aan barmhartigheid, vol mededogen, die de rechtvaardigen liefhebt en Zich over de zondaars ontfermt, die allen roept tot het heil om de belofte van de komende goederen: aanvaard, Heer, ook onze gebeden op dit uur, richt ons leven naar Uw geboden, heilig onze zielen, reinig onze lichamen, leid onze gedachten, zuiver onze bedoelingen en verlos ons van alle droefheid, kwaad en smart. Omring ons met Uw heilige engelen, opdat wij, door hun schaar bewaard en geleid, komen tot de eenheid van het geloof en de kennis van Uw ontoegankelijke heerlijkheid. Want U bent gezegend in de eeuwen der eeuwen. Amen.",
                "Heer, ontferm U. (3 maal)",
                "Eer aan de Vader en de Zoon en de Heilige Geest, nu en altijd en tot in de eeuwen der eeuwen. Amen.",
                "Eerbiedwaardiger dan de cherubijnen en onvergelijkelijk heerlijker dan de serafijnen, die ongeschonden God het Woord hebt gebaard: waarlijk Moeder Gods, u maken wij groot.",
                "Heer Jezus Christus, Zoon van God, op voorspraak van Uw allerreinste Moeder, van onze heilige en godvruchtige vaders en van alle heiligen, ontferm U over ons. Amen.",
            ] if len(prayer_of_hours["body"]["en"]) == 5 else []}),
            text(tr("Prayer of the hour", "Gebed van het uur"), hour_prayer)],
    }


def build_horologion():
    # Hand-written troparia and prayers (with Dutch) from the first version of the Horologion.
    old = {h["id"]: h for h in json.load(open(os.path.join(ROOT, "tools", "source", "horologion_seed.json"), encoding="utf-8"))["hours"]}

    def old_text(hid, title_start):
        for s in old.get(hid, {}).get("sections", []):
            if s.get("kind") == "text" and (s.get("title") or {}).get("en", "").startswith(title_start):
                return s["body"]
        raise KeyError((hid, title_start))

    hours = [
        {
            "id": "midnight", "time": "00:00",
            "name": tr("Midnight Office", "Middernachtsdienst"),
            "subtitle": tr("Waiting for the Bridegroom", "Wachten op de Bruidegom"),
            "theme": tr("Behold, the Bridegroom comes at midnight. Keep watch with the wise virgins.",
                        "Zie, de Bruidegom komt te middernacht. Waak met de wijze maagden."),
            "sections": USUAL_BEGINNING + [counter(12)] + COME_WORSHIP + [
                scripture(tr("Psalm 50", "Psalm 50"), "PSA 51"),
                scripture(tr("Psalm 118 (the Seventeenth Kathisma)", "Psalm 118 (de zeventiende kathisma)"), "PSA 119"),
                prayer("creed-nicene"),
                text(tr("Troparion", "Troparion"), old_text("midnight", "Troparion")),
                prayer("trisagion"),
                counter(40),
            ],
        },
        {
            "id": "morning", "time": "06:00",
            "name": tr("Morning Prayers", "Morgengebeden"),
            "subtitle": tr("The rule on rising", "Het gebed bij het opstaan"),
            "theme": tr("Before anything else, turn to God: the Trinity, Psalm 50, the Creed and the prayers of the Fathers.",
                        "Keer je vóór al het andere tot God: de Drie-eenheid, Psalm 50, de Geloofsbelijdenis en de gebeden van de Vaders."),
            "sections": html_sections("morning/rule/index.html"),
        },
        small_hour("first", "07:00", tr("First Hour", "Eerste uur"), tr("Christ the true Light", "Christus, het ware Licht"),
                   tr("Christ is led before Pilate. Ask that the day's work be directed by His light.",
                      "Christus wordt voor Pilatus geleid. Vraag dat het werk van de dag door Zijn licht wordt geleid."),
                   [5, 89, 100], lines(
                       ["In the morning hear my voice, O my King and my God.",
                        "Glory to the Father, and to the Son, and to the Holy Spirit, now and ever, and unto ages of ages. Amen.",
                        "What shall we call thee, O full of grace? Heaven, for from thee hath shone forth the Sun of righteousness."],
                       ["In de morgen hoor mijn stem, mijn Koning en mijn God.",
                        "Eer aan de Vader en de Zoon en de Heilige Geest, nu en altijd en tot in de eeuwen der eeuwen. Amen.",
                        "Hoe zullen wij u noemen, o vol van genade? Hemel, want uit u is de Zon der gerechtigheid opgegaan."]),
                   old_text("first", "Prayer of the First Hour")),
        small_hour("third", "09:00", tr("Third Hour", "Derde uur"), tr("The descent of the Spirit", "De neerdaling van de Geest"),
                   tr("At the third hour the Holy Spirit descended on the apostles. Ask for a clean heart.",
                      "Op het derde uur daalde de Heilige Geest neer op de apostelen. Vraag om een rein hart."),
                   [16, 24, 50], old_text("third", "Troparion"), lines(
                       ["O Master, God the Father Almighty, O Lord the Only-begotten Son, Jesus Christ, and O Holy Spirit, one Godhead, one Power: have mercy on me a sinner, and by the judgments which Thou knowest save me, Thine unworthy servant, for blessed art Thou unto ages of ages. Amen."],
                       ["Meester, God, almachtige Vader, Heer, eniggeboren Zoon, Jezus Christus, en Heilige Geest, één Godheid, één Kracht: ontferm U over mij, zondaar, en red mij, Uw onwaardige dienaar, naar de oordelen die U kent, want U bent gezegend in de eeuwen der eeuwen. Amen."])),
        small_hour("sixth", "12:00", tr("Sixth Hour", "Zesde uur"), tr("The Cross at noon", "Het Kruis op het middaguur"),
                   tr("At noon Christ was nailed to the Cross. Bring the middle of the day to Him.",
                      "Op het middaguur werd Christus aan het Kruis genageld. Breng het midden van de dag bij Hem."),
                   [53, 54, 90], old_text("sixth", "Troparion"), lines(
                       ["O God and Lord of hosts, Creator of all creation, who through the tender compassion of Thy mercy didst send down Thine Only-begotten Son, our Lord Jesus Christ, for the salvation of our race, and through His precious Cross didst tear asunder the handwriting of our sins: nail down our flesh with the fear of Thee, and incline not our hearts to words or thoughts of evil, but wound our souls with Thy love. Amen."],
                       ["God en Heer der heerscharen, Schepper van heel de schepping, die door de tedere ontferming van Uw barmhartigheid Uw eniggeboren Zoon, onze Heer Jezus Christus, hebt gezonden tot heil van ons geslacht en door Zijn kostbaar Kruis het schuldbewijs van onze zonden hebt verscheurd: nagel ons vlees vast aan Uw vreze, laat ons hart niet neigen tot woorden of gedachten van het kwaad, maar verwond onze zielen met Uw liefde. Amen."])),
        small_hour("ninth", "15:00", tr("Ninth Hour", "Negende uur"), tr("The death of the Lord", "De dood van de Heer"),
                   tr("At the ninth hour Christ gave up His spirit. Put to death what is earthly in us.",
                      "Op het negende uur gaf Christus de geest. Doe sterven wat aards in ons is."),
                   [83, 84, 85], old_text("ninth", "Troparion"), lines(
                       ["O Master, Lord Jesus Christ our God, who art long-suffering in the face of our sins, and who hast brought us even unto this present hour, wherein, hanging upon the life-giving Tree, Thou didst make a way into Paradise for the wise thief: remember us, Thy sinful and unworthy servants, and deliver us from every evil, for we are Thine. Amen."],
                       ["Meester, Heer Jezus Christus, onze God, lankmoedig tegenover onze zonden, die ons tot dit uur hebt gebracht, waarop U, hangend aan het levendmakend Hout, voor de wijze rover de weg naar het Paradijs hebt geopend: gedenk ons, Uw zondige en onwaardige dienaren, en verlos ons van alle kwaad, want wij zijn de Uwen. Amen."])),
        {
            "id": "vespers", "time": "18:00",
            "name": tr("Vespers", "Vespers"),
            "subtitle": tr("O Gladsome Light", "Blijde Licht"),
            "theme": tr("The liturgical day begins at evening. Thank God for the day that has passed.",
                        "De liturgische dag begint 's avonds. Dank God voor de dag die voorbij is."),
            "sections": USUAL_BEGINNING + [counter(12)] + COME_WORSHIP + [
                scripture(tr("Psalm 103 · The Psalm of Creation", "Psalm 103 · De psalm van de schepping"), "PSA 104"),
                rubric("Lord, I have cried: the evening psalms, over which the incense rises.",
                       "Heer, ik roep tot U: de avondpsalmen, waarbij de wierook opstijgt."),
                scripture(tr("Psalm 140", "Psalm 140"), "PSA 141"),
                scripture(tr("Psalm 141", "Psalm 141"), "PSA 142"),
                scripture(tr("Psalm 129", "Psalm 129"), "PSA 130"),
                scripture(tr("Psalm 116", "Psalm 116"), "PSA 117"),
                text(tr("O Gladsome Light", "Blijde Licht"), old_text("vespers", "O Gladsome Light")),
                text(tr("Vouchsafe, O Lord", "Verwaardig U, Heer"), lines(
                    ["Vouchsafe, O Lord, to keep us this evening without sin. Blessed art Thou, O Lord, the God of our fathers, and praised and glorified is Thy name unto the ages. Amen.",
                     "Let Thy mercy, O Lord, be upon us, according as we have hoped in Thee.",
                     "Blessed art Thou, O Lord: teach me Thy statutes. Blessed art Thou, O Master: make me to understand Thy statutes. Blessed art Thou, O Holy One: enlighten me with Thy statutes.",
                     "Thy mercy, O Lord, endureth for ever: O despise not the works of Thy hands. To Thee is due praise, to Thee is due song, to Thee is due glory: to the Father, and to the Son, and to the Holy Spirit, now and ever, and unto ages of ages. Amen."],
                    ["Verwaardig U, Heer, ons deze avond zonder zonde te bewaren. Gezegend bent U, Heer, God van onze vaderen, en geloofd en verheerlijkt is Uw Naam in eeuwigheid. Amen.",
                     "Laat Uw barmhartigheid, Heer, over ons zijn, zoals wij op U hebben gehoopt.",
                     "Gezegend bent U, Heer: leer mij Uw inzettingen. Gezegend bent U, Meester: doe mij Uw inzettingen verstaan. Gezegend bent U, Heilige: verlicht mij door Uw inzettingen.",
                     "Heer, Uw barmhartigheid is in eeuwigheid; veracht de werken van Uw handen niet. U komt lof toe, U komt het lied toe, U komt heerlijkheid toe: de Vader en de Zoon en de Heilige Geest, nu en altijd en tot in de eeuwen der eeuwen. Amen."])),
                scripture(tr("The Song of Simeon", "De lofzang van Simeon"), "LUK 2:29-32"),
                prayer("trisagion"),
                text(tr("Rejoice, O Virgin Theotokos", "Verheug u, Maagd en Moeder Gods"), lines(
                    ["Rejoice, O Virgin Theotokos, Mary full of grace, the Lord is with thee. Blessed art thou among women, and blessed is the fruit of thy womb, for thou hast borne the Saviour of our souls. (3 times)"],
                    ["Verheug u, Maagd en Moeder Gods, Maria, vol van genade, de Heer is met u. Gezegend zijt gij onder de vrouwen en gezegend is de vrucht van uw schoot, want gij hebt de Verlosser van onze zielen gebaard. (3 maal)"])),
            ],
        },
        {
            "id": "compline", "time": "21:00",
            "name": tr("Small Compline", "Kleine Completen"),
            "subtitle": tr("Before sleep", "Voor het slapengaan"),
            "theme": tr("Commit the night to God: Psalm 50, the Creed, the Theotokos and the troparia of this evening.",
                        "Vertrouw de nacht aan God toe: Psalm 50, de Geloofsbelijdenis, de Moeder Gods en de troparia van deze avond."),
            "sections": html_sections("compline/index.html"),
        },
        {
            "id": "evening", "time": "22:00",
            "name": tr("Evening Prayers", "Avondgebeden"),
            "subtitle": tr("The rule before sleep", "Het gebed voor de nacht"),
            "theme": tr("The prayers of St Macarius, St Antiochus and St John of Damascus, and the examination of the day.",
                        "De gebeden van de heilige Macarius, Antiochus en Johannes van Damascus, en het overzien van de dag."),
            "sections": html_sections("evening/rule/index.html"),
        },
    ]
    extras = []
    for eid, page, en, nl, sub_en, sub_nl in [
        ("table", "table/index.html", "Prayers at the Table", "Gebeden aan tafel", "Before and after meals", "Voor en na de maaltijd"),
        ("commemorations", "commemorations/index.html", "Commemoration of the Living and the Dead", "Gedachtenis van levenden en doden", "Read at the end of the morning rule", "Gelezen aan het einde van het morgengebed"),
        ("canons", "canons/index.html", "The Three Canons", "De drie canons", "Repentance, the Theotokos and the Guardian Angel", "Berouw, de Moeder Gods en de Beschermengel"),
        ("communion", "communion/index.html", "Before Holy Communion", "Voor de Heilige Communie", "Prayers in preparation", "Gebeden ter voorbereiding"),
        ("thanksgiving", "communion/thanksgiving/index.html", "Thanksgiving after Communion", "Dankzegging na de Communie", "After receiving the Holy Mysteries", "Na het ontvangen van de Heilige Mysteriën"),
        ("typika", "typika/index.html", "The Typika", "De Typika", "Reader's service when there is no Liturgy", "Lezersdienst wanneer er geen Liturgie is"),
    ]:
        extras.append({"id": eid, "time": "", "name": tr(en, nl), "subtitle": tr(sub_en, sub_nl), "theme": tr("", ""),
                       "sections": html_sections(page)})
    write("prayers_eo.json", {
        "tradition": "EASTERN_ORTHODOX",
        "book": tr("Horologion", "Horologion"),
        "intro": tr(
            "The Horologion is the Book of Hours of the Orthodox Church. At home the day is kept with the morning "
            "rule, the Hours, Vespers, Compline and the prayers before sleep. Texts: Orthodox Daily Prayers "
            "(St Tikhon's, 1982) via orthodoxprayers.net; psalms from the World English Bible.",
            "Het Horologion is het getijdenboek van de Orthodoxe Kerk. Thuis wordt de dag geheiligd met het morgengebed, "
            "de Uren, de Vespers, de Completen en de gebeden voor de nacht. Psalmen: Statenvertaling. De Nederlandse "
            "vertaling van de gebeden is een eigen, voorlopige vertaling."),
        "hours": hours,
        "extras": extras,
    })


def load_kyrie():
    """tools/kyrie.py, loaded by path because `python -I` leaves the script directory off sys.path."""
    import importlib.util
    spec = importlib.util.spec_from_file_location("kyrie", os.path.join(os.path.dirname(os.path.abspath(__file__)), "kyrie.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


EO_NL_TABLE = os.path.join(ROOT, "tools", "source", "prayers_eo_nl.json")


def fill_dutch(data, table):
    """Fills empty Dutch titles and bodies from an English → Dutch table; a body only when every line is in it."""
    for entry in data["hours"] + data["extras"]:
        for s in entry["sections"]:
            t = s.get("title")
            if t and t.get("en") and not t.get("nl") and t["en"] in table:
                t["nl"] = table[t["en"]]
            b = s.get("body")
            if s["kind"] != "scripture" and b and b.get("en") and not b.get("nl") and all(line in table for line in b["en"]):
                b["nl"] = [table[line] for line in b["en"]]
    return data


def untranslated(data):
    return sum(1 for e in data["hours"] + data["extras"] for s in e["sections"]
               if s["kind"] != "scripture" and s.get("body") and s["body"].get("en") and not s["body"].get("nl"))


def write(name, data):
    data = load_kyrie().apply(data)  # Κύριε ἐλέησον stays in Greek
    if name == "prayers_eo.json":
        data = fill_dutch(data, json.load(open(EO_NL_TABLE, encoding="utf-8")))
        print("horologion: untranslated sections", untranslated(data))
    with open(os.path.join(OUT, name), "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    print("wrote", name, os.path.getsize(os.path.join(OUT, name)) // 1024, "KB")


if __name__ == "__main__":
    import sys
    if "--nl-only" in sys.argv:
        write("prayers_eo.json", json.load(open(os.path.join(OUT, "prayers_eo.json"), encoding="utf-8")))
        sys.exit()
    build_agpeya()
    build_horologion()
