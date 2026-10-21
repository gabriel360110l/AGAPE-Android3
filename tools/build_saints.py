#!/usr/bin/env python3
"""Builds the offline Saint-of-the-Day calendars for the Oriental Orthodox and Catholic traditions.

Outputs (committed):
  app/src/main/assets/content/saints_oo.json   Coptic Synaxarium per Coptic month/day (api.coptic.io)
  app/src/main/assets/content/saints_ca.json   Saints of the General Roman Calendar per month/day (calapi.inadiutorium.cz)

Dutch names come from tools/source/saints_nl.json (hand-checked); names without a Dutch entry fall back to English
and are listed when the script runs. The Eastern Orthodox menologion (saints_eo.json) is built by build_content.py.

Run from the repository root:  python -I tools/build_saints.py
"""
import collections
import concurrent.futures
import datetime
import json
import os
import re
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, "tools", ".cache", "saints")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "content")
NL_PATH = os.path.join(ROOT, "tools", "source", "saints_nl.json")
UA = "AGAPE-app-build/1.0 (https://github.com/gabriel360110l/AGAPE-Android3)"

# Coptic year 1743 runs from 11 September 2026 to 11 September 2027 (a leap year: Nasie has six days).
COPTIC_YEAR_START = datetime.date(2026, 9, 11)
COPTIC_YEAR_DAYS = 366
ROMAN_YEARS = (2027, 2028, 2029)

# Celebrations of the Temporale (moveable or not about a saint) are left to the app's own liturgical calendar.
TEMPORAL = re.compile(
    r"Sunday|week|Advent|Lent|Easter|Christmas|Octave|Ash Wednesday|Holy Week|Pentecost|Trinity|Body and Blood|"
    r"Sacred Heart|Christ the King|Ascension|Epiphany|Baptism of the Lord|Palm|Triduum|Good Friday|Holy Saturday|"
    r"Thursday of|Mother of the Church|Immaculate Heart|Holy Family|Solemnity of Mary|Day within|on Saturday",
    re.I,
)


def fetch_json(url, cache_name):
    path = os.path.join(CACHE, cache_name)
    if os.path.exists(path):
        return json.load(open(path, encoding="utf-8"))
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            data = json.load(urllib.request.urlopen(req, timeout=40))
            break
        except Exception as e:  # network hiccups: back off and retry
            if attempt == 3:
                raise
            print("  retry", url, e)
            time.sleep(3 * (attempt + 1))
    os.makedirs(CACHE, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)
    return data


def clean_coptic(name):
    name = re.sub(r"\s+", " ", name).strip().rstrip(".")
    return name


def build_oo():
    days = []
    for i in range(COPTIC_YEAR_DAYS):
        date = COPTIC_YEAR_START + datetime.timedelta(days=i)
        month, day = i // 30 + 1, i % 30 + 1
        entries = fetch_json(f"https://api.coptic.io/api/synaxarium/{date.isoformat()}", f"oo-{month:02d}-{day:02d}.json")
        names = []
        for e in entries or []:
            n = clean_coptic(e.get("name", ""))
            if n and n not in names:
                names.append(n)
        days.append({"month": month, "day": day, "names": names})
    return days


def build_ca():
    dates = []
    for year in ROMAN_YEARS:
        d = datetime.date(year, 1, 1)
        while d.year == year:
            dates.append(d)
            d += datetime.timedelta(days=1)

    def fetch(d):
        return d, fetch_json(
            f"http://calapi.inadiutorium.cz/api/v0/en/calendars/general-en/{d.year}/{d.month}/{d.day}",
            f"ca-{d.isoformat()}.json",
        )

    # The API answers slowly (seconds per day), so ask for several days at once.
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(fetch, dates))

    where = collections.defaultdict(collections.Counter)  # title -> (month, day) -> years seen there
    ranks = {}
    for d, data in results:
        for c in data.get("celebrations", []):
            title, rank = c.get("title", ""), c.get("rank", "")
            if rank == "ferial" or not title or TEMPORAL.search(title):
                continue
            where[title][(d.month, d.day)] += 1
            ranks[title] = rank
    # A saint can be pushed off its day by a Sunday or moved by a solemnity in some years: keep the date it
    # has most often across the years fetched.
    per_day = collections.defaultdict(list)
    for title, counter in where.items():
        per_day[counter.most_common(1)[0][0]].append(title)
    return [
        {"month": m, "day": d, "names": titles, "ranks": [ranks[t] for t in titles]}
        for (m, d), titles in sorted(per_day.items())
    ]


# Dutch for the Synaxarium titles: the recurring phrases, roles and the usual Dutch forms of biblical and patristic
# names. Proper names without a Dutch form stay as they are. Hand-written entries in saints_nl.json take precedence.
NL_PHRASES = [
    (r"^(The )?Commemoration of the Consecration of the [Cc]hurch of", "Gedachtenis van de inwijding van de kerk van"),
    (r"^(The )?Consecration of the [Cc]hurch of", "Inwijding van de kerk van"),
    (r"^(The )?Commemoration of the Martyrdom of", "Gedachtenis van het martelaarschap van"),
    (r"^(The )?Commemoration of the Departure of", "Gedachtenis van het heengaan van"),
    (r"^(The )?Commemoration of the Relocation of the Relics of", "Gedachtenis van de overbrenging van de relieken van"),
    (r"^(The )?Commemoration of the Appearance of", "Gedachtenis van de verschijning van"),
    (r"^(The )?Commemoration of the Miracle performed by", "Gedachtenis van het wonder gedaan door"),
    (r"^(The )?Commemoration [Oo]f", "Gedachtenis van"),
    (r"^(The )?Martyrdom [Oo]f", "Het martelaarschap van"),
    (r"^(The )?Departure [Oo]f", "Het heengaan van"),
    (r"^(The )?Translocation of the (Relics|Body) of", "De overbrenging van de relieken van"),
    (r"^(The )?Relocation of the (Relics|Body) of", "De overbrenging van de relieken van"),
    (r"^(The )?Appearance of the (Head|Body|Relics) of", "De vinding van de relieken van"),
    (r"^(The )?Assembly of the", "Het bijeenkomen van het"),
    (r"^(The )?Feast of the", "Het feest van de"),
    (r"^(The )?Feast of", "Het feest van"),
]
NL_WORDS = [
    (r"\bSts?\.\s*", "de heilige "), (r"\bSaints\b", "de heiligen"), (r"\bSaint\b", "de heilige"),
    (r"\bthe Theotokos\b", "de Moeder Gods"), (r"\bTheotokos\b", "Moeder Gods"), (r"\bEver-Virgin\b", "altijd-maagd"),
    (r"\bOur Lord Jesus Christ\b", "onze Heer Jezus Christus"), (r"\bOur Lord\b", "onze Heer"),
    (r"\bthe Archangel\b", "de aartsengel"), (r"\bArchangel\b", "aartsengel"),
    (r"\bthe Prophet\b", "de profeet"), (r"\bthe Apostles?\b", "de apostel"), (r"\bthe Evangelist\b", "de evangelist"),
    (r"\bthe Martyrs?\b", "de martelaar"), (r"\bthe Soldier\b", "de soldaat"), (r"\bthe Priest\b", "de priester"),
    (r"\bthe Monk\b", "de monnik"), (r"\bthe Hermit\b", "de kluizenaar"), (r"\bthe Anchorite\b", "de kluizenaar"),
    (r"\bthe Confessor\b", "de belijder"), (r"\bthe Ascetic\b", "de asceet"), (r"\bthe Righteous\b", "de rechtvaardige"),
    (r"\bthe Great\b", "de Grote"), (r"\bthe Virgin\b", "de maagd"), (r"\bthe Bishop\b", "de bisschop"),
    (r"\bthe Deacon\b", "de diaken"), (r"\bthe Hegumen\b", "de hegoumen"), (r"\bthe Abbot\b", "de abt"),
    (r"\bthe Stylite\b", "de styliet"), (r"\bthe Empress\b", "de keizerin"), (r"\bthe Emperor\b", "de keizer"),
    (r"\bthe Short\b", "de Kleine"), (r"\bthe Poor\b", "de Arme"), (r"\bthe Nun\b", "de non"),
    (r"\bPope of Rome\b", "paus van Rome"), (r"\bPope of Alexandria\b", "paus van Alexandrië"),
    (r"\bPatriarch of Alexandr[ia]a\b", "patriarch van Alexandrië"), (r"\bPatriarch of Antioch\b", "patriarch van Antiochië"),
    (r"\bPatriarch of Constantinople\b", "patriarch van Constantinopel"), (r"\bArchbishop of\b", "aartsbisschop van"),
    (r"\bBishop of\b", "bisschop van"), (r"\bPope\b", "paus"), (r"\bPatriarch\b", "patriarch"), (r"\bBishop\b", "bisschop"),
    (r"\bPriest\b", "priester"), (r"\bMonk\b", "monnik"), (r"\bMartyrs\b", "martelaren"), (r"\bMartyr\b", "martelaar"),
    (r"\bVirgins\b", "maagden"), (r"\bthe See of St\.? Mark\b", "de Stoel van Marcus"),
    (r"\bAlexandria\b", "Alexandrië"), (r"\bAntioch\b", "Antiochië"), (r"\bJerusalem\b", "Jeruzalem"),
    (r"\bEgypt\b", "Egypte"), (r"\bPersia\b", "Perzië"), (r"\bCappadocia\b", "Cappadocië"),
    (r"\bmonastery\b", "klooster"), (r"\bMonastery\b", "klooster"), (r"\bchurch\b", "kerk"), (r"\bChurch\b", "Kerk"),
    (r"\b[Hh]is [Cc]ompanions\b", "zijn gezellen"), (r"\b[Hh]er [Cc]ompanions\b", "haar gezellen"),
    (r"\b[Tt]heir [Mm]other\b", "hun moeder"), (r"\b[Hh]is [Tt]wo [Ss]ons\b", "zijn twee zonen"),
    (r"\b[Hh]er [Tt]wo [Ss]ons\b", "haar twee zonen"), (r"\b[Hh]is [Ss]ister\b", "zijn zuster"),
    (r"\b[Hh]is [Dd]isciples?\b", "zijn leerling"), (r"\bthe Disciple of\b", "de leerling van"),
    (r"\bJesus Christ\b", "Jezus Christus"), (r"\bJesus\b", "Jezus"), (r"\bChrist\b", "Christus"),
    (r"\bSavio(u)?r\b", "Verlosser"), (r"\bLord\b", "Heer"), (r"\bHoly\b", "heilige"), (r"\bGlorious\b", "glorierijke"),
    (r"\bHonou?rable\b", "eerbiedwaardige"), (r"\bBlessed\b", "gezegende"), (r"\bBaptism\b", "doop"),
    (r"\bNativity\b", "geboorte"), (r"\bResurrection\b", "verrijzenis"), (r"\bCross\b", "Kruis"),
    (r"\bAscension\b", "hemelvaart"), (r"\bEntry\b", "intocht"), (r"\bCircumcision\b", "besnijdenis"),
    (r"\bCouncil\b", "concilie"), (r"\bEcumenical\b", "oecumenisch"), (r"\bRelics\b", "relieken"), (r"\bBody\b", "lichaam"),
    (r"\bMiracle\b", "wonder"), (r"\bWonder\b", "wonder"), (r"\bFirst\b", "eerste"), (r"\bHermit\b", "kluizenaar"),
    (r"\bBrothers\b", "broers"), (r"\bBrother\b", "broer"), (r"\bSons\b", "zonen"), (r"\bSon\b", "zoon"),
    (r"\bDaughter\b", "dochter"), (r"\bMother\b", "moeder"), (r"\bFather\b", "vader"), (r"\bSister\b", "zuster"),
    (r"\bWife\b", "vrouw"), (r"\bKing\b", "koning"), (r"\bQueen\b", "koningin"), (r"\bEmperor\b", "keizer"),
    (r"\bCompanions\b", "gezellen"), (r"\bDisciple\b", "leerling"), (r"\bAbbot\b", "abt"), (r"\bGreat\b", "Grote"),
    (r"\bBishops\b", "bisschoppen"), (r"\bPriests\b", "priesters"), (r"\bbelievers\b", "gelovigen"),
    (r"\bknown as\b", "bekend als"), (r"\bKnown as\b", "bekend als"), (r"\bwho was\b", "die was"),
    (r"\bOne of the Seven Deacons\b", "een van de zeven diakens"), (r"\bthe Forty\b", "de veertig"),
    (r"\band\b", "en"), (r"\bof\b", "van"), (r"\bthe\b", "de"), (r"\bThe\b", "De"),
    # Biblical and patristic names in their Dutch form
    (r"\bJohn\b", "Johannes"), (r"\bJames\b", "Jakobus"), (r"\bPeter\b", "Petrus"), (r"\bPaul\b", "Paulus"),
    (r"\bMark\b", "Marcus"), (r"\bMatthew\b", "Mattheüs"), (r"\bLuke\b", "Lucas"), (r"\bAndrew\b", "Andreas"),
    (r"\bPhilip\b", "Filippus"), (r"\bBartholomew\b", "Bartolomeüs"), (r"\bMatthias\b", "Mattias"), (r"\bJude\b", "Judas"),
    (r"\bStephen\b", "Stefanus"), (r"\bTimothy\b", "Timoteüs"), (r"\bMichael\b", "Michaël"), (r"\bGabriel\b", "Gabriël"),
    (r"\bRaphael\b", "Rafaël"), (r"\bMary\b", "Maria"), (r"\bElizabeth\b", "Elisabet"), (r"\bAnne\b", "Anna"),
    (r"\bJoseph\b", "Jozef"), (r"\bMoses\b", "Mozes"), (r"\bIsaiah\b", "Jesaja"), (r"\bJeremiah\b", "Jeremia"),
    (r"\bEzekiel\b", "Ezechiël"), (r"\bElijah\b", "Elia"), (r"\bElisha\b", "Elisa"), (r"\bIsaac\b", "Isaak"),
    (r"\bJacob\b", "Jakob"), (r"\bObadiah\b", "Obadja"), (r"\bHosea\b", "Hosea"), (r"\bZacharias\b", "Zacharias"),
    (r"\bGeorge\b", "Georgius"), (r"\bAnthony\b", "Antonius"), (r"\bBasil\b", "Basilius"), (r"\bGregory\b", "Gregorius"),
    (r"\bCyril\b", "Cyrillus"), (r"\bTheodore\b", "Theodorus"), (r"\bThomas\b", "Tomas"), (r"\bHelena\b", "Helena"),
    (r"\bMartin\b", "Martinus"), (r"\bPolycarp\b", "Polycarpus"), (r"\bClement\b", "Clemens"), (r"\bJob\b", "Job"),
]


def dutch_coptic(name):
    s = name
    for pattern, repl in NL_PHRASES:
        s, n = re.subn(pattern, repl, s)
        if n:
            break
    head, sep, rest = s.partition(" van ") if s.startswith(("Gedachtenis", "Het ", "De ", "Inwijding")) else ("", "", s)
    for pattern, repl in NL_WORDS:
        rest = re.sub(pattern, repl, rest)
    rest = re.sub(r"\b(\d+)(st|nd|rd|th)\.?", r"\1e", rest)  # 21st -> 21e
    rest = re.sub(r"\bde de\b", "de", rest)
    rest = re.sub(r"\bde Grote de heilige\b", "de grote heilige", rest)
    rest = re.sub(r"\s+", " ", rest).strip()
    return f"{head}{sep}{rest}" if sep else rest


def localize(days, nl, missing, rules=None):
    out = []
    for d in days:
        saints = []
        for i, en in enumerate(d["names"]):
            dutch = nl.get(en) or (rules(en) if rules else None)
            if not dutch:
                missing.add(en)
            s = {"title": {"en": en, "nl": dutch or en}}
            if "ranks" in d:
                s["rank"] = d["ranks"][i]
            saints.append(s)
        out.append({"month": d["month"], "day": d["day"], "saints": saints})
    return out


def write(name, data):
    path = os.path.join(OUT, name)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    print("wrote", name, os.path.getsize(path) // 1024, "KB")


if __name__ == "__main__":
    nl = {}
    for path in (NL_PATH, NL_PATH.replace(".json", "_oo.json")):
        if os.path.exists(path):
            nl.update(json.load(open(path, encoding="utf-8")))
    missing = set()
    oo, ca = build_oo(), build_ca()
    write("saints_oo.json", localize(oo, nl, missing, rules=dutch_coptic))
    write("saints_ca.json", localize(ca, nl, missing))
    if missing:
        with open(os.path.join(CACHE, "missing_nl.txt"), "w", encoding="utf-8") as f:
            f.write("\n".join(sorted(missing)))
        print(len(missing), "names without Dutch; listed in tools/.cache/saints/missing_nl.txt")
