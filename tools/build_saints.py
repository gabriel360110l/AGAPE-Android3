#!/usr/bin/env python3
"""Builds the offline Saint-of-the-Day calendars for the Oriental Orthodox and Catholic traditions.

Outputs (committed):
  app/src/main/assets/content/saints_oo.json   Coptic Synaxarium per Coptic month/day (api.coptic.io)
  app/src/main/assets/content/saints_ca.json   Saints of the General Roman Calendar per month/day (calapi.inadiutorium.cz)

Dutch names come from tools/source/saints_nl.json (hand-checked); names without a Dutch entry fall back to English
and are listed when the script runs. The Eastern Orthodox menologion (saints_eo.json) is built by build_content.py.

Run from the repository root:  python -I tools/build_saints.py
(--offline rebuilds from the committed calendars, applying the corrections and the Dutch rules, without the APIs.)
"""
import collections
import concurrent.futures
import datetime
import json
import os
import re
import sys
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


# api.coptic.io answers some days with the commemorations of a neighbouring day. These days are corrected from the
# Synaxarium as published by St-Takla.org (books/en/church/synaxarium), checked month by month on 23 October 2026.
OO_CORRECTIONS = {
    (6, 7): ["The Departure of St. Alexandros II, the 43rd Pope of Alexandria",
             "The Departure of St. Theodorus, the 45th Pope of Alexandria"],
    (6, 9): ["The Departure of St. Barsauma, the Father of the Syrian Monks", "The Martyrdom of St. Paul the Syrian"],
    (7, 25): ["The Departure of St. Onesiphorus, One of the Seventy Disciples",
              "The Departure of Pope Mattheos I, the 100th Pope of Alexandria"],
    (8, 1): ["The Departure of St. Silvanus the Monk", "The Departure of Aaron the Priest"],
    (8, 2): ["The Martyrdom of St. Christopher", "The Departure of Pope Yoannis IX, the 81st Patriarch of Alexandria"],
    (8, 4): ["The Martyrdom of Sts. Victor, Decius and Eirene the Virgin, and Those with Them"],
    (8, 28): ["The Martyrdom of St. Millius the Ascetic"],
    (9, 4): ["The Departure of Pope John I, the 29th Patriarch of Alexandria",
             "The Departure of Pope Yoannis V, the 72nd Patriarch of Alexandria"],
    (9, 11): ["The Martyrdom of St. Theoclia, the Wife of St. Justus", "The Departure of St. Paphnutius the Bishop"],
    (9, 13): ["The Departure of St. Arsenius, the Tutor of the Emperor's Children"],
    (9, 15): ["The Martyrdom of St. Simon the Zealot, the Apostle", "The Martyrdom of the 400 Martyrs of Dendera"],
    (9, 23): ["The Departure of St. Junia, One of the Seventy Disciples",
              "The Martyrdom of St. Julian (Yulianus) and his mother in Alexandria"],
    (9, 24): ["The Commemoration of the Entry of Our Lord Jesus Christ into Egypt", "The Departure of Habakkuk the Prophet"],
    (10, 6): ["The Martyrdom of St. Theodore the Monk"],
    (10, 9): ["The Departure of Samuel the Prophet", "The Martyrdom of St. Lucilianus and Four Others with Him"],
}
OO_SPELLING = {"Patriarch of Alexandra": "Patriarch of Alexandria", "Alexanderia": "Alexandria",
               "Carpentar": "Carpenter", "the 89th. Patriarch": "the 89th Patriarch",
               "the 97th. Patriarch": "the 97th Patriarch", "the 86th. Patriarch": "the 86th Patriarch",
               "St. Mark 11,": "St. Mark II,", "St. Michael 11,": "St. Michael II,", "Twenty- Fourth": "Twenty-Fourth"}

# The Roman calendar is read over three years and each celebration kept on the date it has most often; a solemnity
# moved by Holy Week or Easter in two of those years lands on the wrong day, so these are pinned. Renames and
# additions follow the decrees since 2019 that calapi does not yet know.
CA_PINNED = {"Annunciation of the Lord": (3, 25), "Saint Joseph Husband of the Blessed Virgin Mary": (3, 19)}
CA_RENAMED = {"Saint Martha": "Saints Martha, Mary and Lazarus"}
# In Lent and the last week of Advent a memorial is only a "commemoration"; keep the rank it has in the calendar.
CA_RANKS = {"Seven Holy Founders of the Servite Order": "optional memorial",
            "Saint Polycarp, bishop and martyr": "memorial", "Saints Perpetua and Felicity, martyrs": "memorial",
            "Saint John Baptist de la Salle, priest": "memorial"}
CA_ADDED = [
    (2, 27, "Saint Gregory of Narek, abbot and doctor of the Church", "optional memorial"),
    (4, 2, "Saint Francis of Paola, hermit", "optional memorial"),
    (5, 10, "Saint John of Avila, priest and doctor of the Church", "optional memorial"),
    (5, 29, "Saint Paul VI, pope", "optional memorial"),
    (9, 17, "Saint Hildegard of Bingen, virgin and doctor of the Church", "optional memorial"),
    (10, 5, "Saint Faustina Kowalska, virgin", "optional memorial"),
    (12, 10, "Our Lady of Loreto", "optional memorial"),
]


def correct_oo(days):
    for d in days:
        fixed = OO_CORRECTIONS.get((d["month"], d["day"]))
        names = fixed if fixed else d["names"]
        for wrong, right in OO_SPELLING.items():
            names = [n.replace(wrong, right) for n in names]
        d["names"] = names
    return days


def correct_ca(days):
    per_day = {(d["month"], d["day"]): list(zip(d["names"], d["ranks"])) for d in days}
    for entries in per_day.values():
        entries[:] = [(CA_RENAMED.get(n, n), CA_RANKS.get(n, "optional memorial") if r == "commemoration" else r)
                      for n, r in entries if n not in CA_PINNED]
    pinned_ranks = {n: r for d in days for n, r in zip(d["names"], d["ranks"]) if n in CA_PINNED}
    for name, md in CA_PINNED.items():
        if name in pinned_ranks:
            per_day.setdefault(md, []).insert(0, (name, pinned_ranks[name]))
    for m, d, name, rank in CA_ADDED:
        if all(n != name for n, _ in per_day.setdefault((m, d), [])):
            per_day[(m, d)].append((name, rank))
    return [{"month": m, "day": d, "names": [n for n, _ in e], "ranks": [r for _, r in e]}
            for (m, d), e in sorted(per_day.items()) if e]


def from_output(name, with_ranks):
    """--offline: the names of the committed calendar, for when the APIs cannot be reached."""
    data = json.load(open(os.path.join(OUT, name), encoding="utf-8"))
    out = []
    for d in data:
        day = {"month": d["month"], "day": d["day"], "names": [s["title"]["en"] for s in d["saints"]]}
        if with_ranks:
            day["ranks"] = [s.get("rank", "") for s in d["saints"]]
        out.append(day)
    return out


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
    (r"\b[Tt]he See of St\.? ?Mark\b", "de Stoel van Marcus"), (r"\bSee of St\.? ?Mark\b", "Stoel van Marcus"),
    (r"\bon the Mount of\b", "op de berg van"), (r"\bon the island of\b", "op het eiland"),
    (r"\bthe Four Incorporeal Beasts\b", "de vier onlichamelijke wezens"), (r"\bthe Two Saints\b", "de twee heiligen"),
    (r"\bthe Three Virgins\b", "de drie maagden"), (r"\bthe Three\b", "de drie"), (r"\bnew martyr\b", "nieuwe martelaar"),
    (r"\bthe Wonder Worker\b", "de wonderdoener"), (r"\bEver-Virgin\b", "altijd-maagd"), (r"\bRepose of\b", "Het heengaan van"),
    (r"\bthe Carpenter\b", "de timmerman"), (r"\bProphetess\b", "profetes"), (r"\bthe Syrian Monks\b", "de Syrische monniken"),
    (r"\bthe Syrian\b", "de Syriër"), (r"\bthe Zealot\b", "de IJveraar"), (r"\bthe Announcer of\b", "de boodschapper van"),
    (r"\bthe Land of Egypt\b", "het land Egypte"), (r"\bHabakkuk\b", "Habakuk"), (r"\bYoung Men\b", "jongemannen"),
    (r"\b(?:who (?:was|is) )?[Kk]nown as\b", "bekend als"), (r"\bpope\b", "paus"), (r"\bbishop\b", "bisschop"),
    (r"\bmartyr\b", "martelaar"), (r"\bascetic\b", "asceet"), (r"\bthe Elder\b", "de Oudere"),
    (r"\b[Hh]is [Cc]ompanion\b", "zijn gezel"), (r"\b[Cc]ompanion of\b", "gezel van"), (r"\bthe Friend of\b", "de vriend van"),
    (r"\bFriend of\b", "vriend van"), (r"\b[Hh]er [Cc]hildren\b", "haar kinderen"), (r"\b[Tt]heir [Cc]hildren\b", "hun kinderen"),
    (r"\b[Hh]is [Ww]ife\b", "zijn vrouw"), (r"\b[Hh]is [Mm]other\b", "zijn moeder"), (r"\b[Hh]er [Mm]other\b", "haar moeder"),
    (r"\bthe [Mm]other of\b", "de moeder van"), (r"\b[Bb]rother of\b", "broer van"), (r"\bthe [Ss]on of\b", "de zoon van"),
    (r"\bthe Great Prophet\b", "de grote profeet"), (r"\bthe Prophet\b", "de profeet"), (r"\bProphet\b", "profeet"),
    (r"\bOne of the Seventy (?:Disciples|Apostles)\b", "een van de zeventig leerlingen"),
    (r"\bOne of the Seven Deacons\b", "een van de zeven diakens"), (r"\bthe Seven\b", "de zeven"),
    (r"\bthe Three Hebrew Young Men\b", "de drie Hebreeuwse jongemannen"), (r"\bthe Seven Young Men\b", "de zeven jongemannen"),
    (r"\bthe Forty-Nine\b", "de negenenveertig"), (r"\bthe Twenty-Four\b", "de vierentwintig"), (r"\bElders\b", "oudvaders"),
    (r"\bOur Lady\b", "onze Vrouwe"), (r"\bthe Lady\b", "de Vrouwe"), (r"\bVirgin Lady\b", "maagd en Vrouwe"),
    (r"\bPure Virgin\b", "reine maagd"), (r"\bBlessed Virgin\b", "gezegende maagd"), (r"\bVirgin\b", "maagd"),
    (r"\bthe Apostolic\b", "de Apostolische"), (r"\bthe Tutor of the Emperor's Children\b", "de leermeester van de keizerskinderen"),
    (r"\bEnthronement\b", "troonsbestijging"), (r"\bAppearance\b", "verschijning"), (r"\bTranslocation\b", "overbrenging"),
    (r"\bTransfiguration\b", "gedaanteverandering"), (r"\bAssumption\b", "opneming"), (r"\bDormition\b", "ontslapen"),
    (r"\bEntrance\b", "intrede"), (r"\binto\b", "in"), (r"\bat\b", "te"), (r"\bon Mount\b", "op de berg"),
    (r"\bon the Mountain\b", "op de berg"), (r"\bthe island\b", "het eiland"), (r"\bTemple\b", "tempel"),
    (r"\b[Cc]ommemoration\b", "gedachtenis"), (r"\bnew martyr\b", "nieuwe martelaar"), (r"\bHead\b", "hoofd"),
    (r"\b[Hh]is [Hh]oly\b", "zijn heilige"), (r"\b[Tt]hose [Ww]ith [Tt]hem\b", "wie met hen waren"),
    (r"\b[Ff]our [Oo]thers [Ww]ith [Hh]im\b", "vier anderen met hem"), (r"\bthe Wonder\b", "het wonder"),
    (r"\bthat God [Pp]erformed for\b", "dat God deed voor"), (r"\bthat took place on the hand of\b", "gedaan door"),
    (r"\bthe revealing of the virginity of\b", "de onthulling van de maagdelijkheid van"), (r"\bthe Veiled\b", "de Gesluierde"),
    (r"\bthe Wilderness of\b", "de woestijn van"), (r"\bOthers\b", "anderen"), (r"\bGalilee\b", "Galilea"),
    (r"\bEphesus\b", "Efeze"), (r"\bNicea\b", "Nicea"), (r"\bDaniel\b", "Daniël"), (r"\bJonah\b", "Jona"),
    (r"\bJoshua\b", "Jozua"), (r"\bHannah\b", "Hanna"), (r"\bIsidore\b", "Isidorus"), (r"\bHebrew\b", "Hebreeuwse"),
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


UNITS = "zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen " \
        "eighteen nineteen".split()
TENS = "_ _ twenty thirty forty fifty sixty seventy eighty ninety".split()
ORDINAL_UNITS = {"first": 1, "second": 2, "third": 3, "fourth": 4, "fifth": 5, "sixth": 6, "seventh": 7, "eighth": 8,
                 "ninth": 9, "tenth": 10, "eleventh": 11, "twelfth": 12, "thirteenth": 13, "fourteenth": 14,
                 "fifteenth": 15, "sixteenth": 16, "seventeenth": 17, "eighteenth": 18, "nineteenth": 19}
NUMBER_WORD = r"(?:" + "|".join(sorted(set(UNITS[1:] + TENS[2:] + [t[:-1] + "ieth" for t in TENS[2:]] + list(ORDINAL_UNITS)
                                           + ["hundred", "hundredth", "and"]), key=len, reverse=True)) + r")"
NUMBER_PHRASE = re.compile(r"\b(" + NUMBER_WORD + r"(?:[- ]" + NUMBER_WORD + r")*)\b", re.I)
ROMAN = ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV", "XVI",
         "XVII", "XVIII", "XIX", "XX"]


def number_value(phrase):
    """'Twenty-Eighth', 'Fifty Six', 'Seventy', 'One Hundred and Fourth' -> 28, 56, 70, 104."""
    total = 0
    for word in re.split(r"[- ]+", phrase.lower()):
        if word == "and":
            continue
        if word in ("hundred", "hundredth"):
            total = max(total, 1) * 100
        elif word in ORDINAL_UNITS:
            total += ORDINAL_UNITS[word]
        elif word in UNITS:
            total += UNITS.index(word)
        elif word.rstrip("ieth").rstrip("y") + "y" in TENS or word in TENS:
            total += TENS.index(word if word in TENS else word[:-4] + "y") * 10
    return total


def english_numbers(name):
    """Writes spelled-out numbers as digits before translating: 'the Forty-Sixth Pope' -> 'the 46th Pope' (Dutch
    '46e'), and a regnal 'Yoannis the Eleventh,' -> 'Yoannis XI,'."""
    phrase = NUMBER_PHRASE.pattern[3:-3]
    name = re.sub(r"(\bthe |, )(" + phrase + r")(?![-\w])(?=,?\s+(?:Pope|Patriarch))",
                  lambda m: f"{m.group(1)}{number_value(m.group(2))}th", name, flags=re.I)
    ordinal = "|".join(sorted(ORDINAL_UNITS, key=len, reverse=True))

    def regnal(m):
        n = number_value(m.group(1))
        return f" {ROMAN[n]}" if 0 < n < len(ROMAN) else m.group(0)
    return re.sub(r"(?<=[A-Za-z)]) the (" + ordinal + r")(?![-\w])(?=\s*(?:,|$|\(| the | \d))", regnal, name, flags=re.I)


def dutch_coptic(name):
    s = english_numbers(name)
    s = re.sub(r"(?<=\s)(Of|The|And|De)\b", lambda m: m.group(1).lower() if m.group(1) != "De" else "the", s)
    for pattern, repl in NL_PHRASES:
        s, n = re.subn(pattern, repl, s)
        if n:
            break
    head, sep, rest = s.partition(" van ") if s.startswith(("Gedachtenis", "Het ", "De ", "Inwijding")) else ("", "", s)
    for pattern, repl in NL_WORDS:
        rest = re.sub(pattern, repl, rest)
    rest = re.sub(r"\b(\d+)(st|nd|rd|th)\.?", r"\1e", rest)  # 21st -> 21e
    rest = re.sub(r"\bde de\b", "de", rest)
    rest = re.sub(r"\bde (wonder|lichaam|glorierijke Kruis)\b", r"het \1", rest)
    rest = re.sub(r"\bde Grote de heilige\b", "de grote heilige", rest)
    rest = re.sub(r"\s+", " ", rest).strip()
    return re.sub(r"^Het heengaan van Het heengaan van", "Het heengaan van", f"{head}{sep}{rest}" if sep else rest)


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
    if "--offline" in sys.argv:
        oo, ca = from_output("saints_oo.json", with_ranks=False), from_output("saints_ca.json", with_ranks=True)
    else:
        oo, ca = build_oo(), build_ca()
    oo, ca = correct_oo(oo), correct_ca(ca)
    write("saints_oo.json", localize(oo, nl, missing, rules=dutch_coptic))
    write("saints_ca.json", localize(ca, nl, missing))
    if missing:
        os.makedirs(CACHE, exist_ok=True)
        with open(os.path.join(CACHE, "missing_nl.txt"), "w", encoding="utf-8") as f:
            f.write("\n".join(sorted(missing)))
        print(len(missing), "names without Dutch; listed in tools/.cache/saints/missing_nl.txt")
