#!/usr/bin/env python3
"""Builds the hours of prayers_ca.json: the Liturgy of the Hours for a whole week.

The psalms, canticles and short readings follow Week I of the four-week Psalter of the Liturgia Horarum, one set
per weekday (sections carry `weekday`, 1 = Monday; Saturday evening is Evening Prayer I of Sunday). Psalm and
canticle texts come from bible.db: WEB in English, the Statenvertaling in Dutch. Psalm verses are given in Hebrew
numbering as in the breviary; partial psalms and non-contiguous canticles are written out inline per language,
because WEB does not number the psalm superscriptions and the Statenvertaling does.

Hymns, Te Deum, Salve Regina and the other fixed texts come from tools/source/liturgy_hours_ca_seed.json. Antiphons,
responsories, intercessions and concluding prayers are the editors' own provisional texts (EN + NL); the second
readings are public-domain translations of the Fathers (Lightfoot, ANF, NPNF, Pusey) with a Dutch translation of our own.
The extras (Angelus, Rosary, …) are left as they are.

Run from the repository root:  python -I -X utf8 tools/build_hours_ca.py
"""
import json
import os
import re
import sqlite3

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "content", "prayers_ca.json")
DB = sqlite3.connect(os.path.join(ROOT, "app", "src", "main", "assets", "bible", "bible.db"))
SEED = {h["id"]: h for h in json.load(open(os.path.join(ROOT, "tools", "source", "liturgy_hours_ca_seed.json"), encoding="utf-8"))["hours"]}

MON, TUE, WED, THU, FRI, SAT, SUN = range(1, 8)
DAYS = [SUN, MON, TUE, WED, THU, FRI, SAT]


def tr(en, nl):
    return {"en": en, "nl": nl}


def verse_list(spec):
    out = []
    for part in spec.split(","):
        a, _, b = part.partition("-")
        out += list(range(int(a), int(b or a) + 1))
    return out


def text_of(trans, book, chapter, verses):
    rows = dict(DB.execute(
        f"select verse, text from verses where translation=? and book=? and chapter=? and verse in ({','.join('?' * len(verses))})",
        (trans, book, chapter, *verses)).fetchall())
    missing = [v for v in verses if v not in rows]
    assert not missing, (trans, book, chapter, missing)
    return [rows[v].strip() for v in verses]


def psalm_offset(n):
    count = dict(DB.execute("select translation, count(*) from verses where book='PSA' and chapter=? group by translation", (n,)).fetchall())
    return count["sv"] - count["web"]


def lxx(n):
    if n <= 8 or n >= 148:
        return None
    if n in (9, 10):
        return "9"
    if n <= 113:
        return str(n - 1)
    if n in (114, 115):
        return "113"
    if n == 116:
        return "114–115"
    if n <= 146:
        return str(n - 1)
    return "146–147"


def psalm(n, spec=None, part=None):
    """A psalm, whole or in part (Hebrew/Statenvertaling verse numbers)."""
    g = lxx(n)
    title = f"Psalm {n}" + (f" ({g})" if g else "") + (f" · {part}" if part else "")
    s = {"kind": "scripture", "title": tr(title, title), "ref": f"PSA {n}"}
    if spec:
        sv = verse_list(spec)
        off = psalm_offset(n)
        web = [v - off for v in sv if v - off >= 1]
        s["ref"] = f"PSA {n}:{web[0]}-{web[-1]}"
        s["body"] = {"en": text_of("web", "PSA", n, web), "nl": text_of("sv", "PSA", n, sv)}
    return s


def passage(title_en, title_nl, ref, en=None, nl=None):
    """A canticle or reading. `en`/`nl` are (book, chapter, verses) when the text is written out inline."""
    s = {"kind": "scripture", "title": tr(title_en, title_nl), "ref": ref}
    if en or nl:
        s["body"] = {"en": [x for b, c, v in en for x in text_of("web", b, c, verse_list(v))],
                     "nl": [x for b, c, v in nl for x in text_of("sv", b, c, verse_list(v))]}
    return s


def reading(ref, en=None, nl=None):
    return passage("Short reading · " + nice(ref), "Korte lezing · " + nice(ref, True), ref, en, nl)


BOOK_NAMES = {b["id"]: b["name"] for b in json.load(open(os.path.join(ROOT, "app", "src", "main", "assets", "content", "books.json"), encoding="utf-8"))}


def nice(ref, nl=False):
    book, rest = ref.split(" ", 1)
    return f"{BOOK_NAMES[book]['nl' if nl else 'en']} {rest}"


def ant(en, nl):
    return {"kind": "rubric", "body": {"en": [f"Antiphon: {en}"], "nl": [f"Antifoon: {nl}"]}}


def rubric(en, nl):
    return {"kind": "rubric", "body": {"en": [en], "nl": [nl]}}


def text(title_en, title_nl, en, nl):
    assert len(en) == len(nl), title_en
    return {"kind": "text", "title": tr(title_en, title_nl), "body": {"en": en, "nl": nl}}


def resp(en, nl):
    return text("Responsory", "Responsorium", [en], [nl])


def prayer(pid):
    return {"kind": "prayer", "prayerId": pid}


def seed(hid, title_start):
    for s in SEED[hid]["sections"]:
        if s.get("title", {}).get("en", "").startswith(title_start):
            return json.loads(json.dumps(s))
    raise KeyError((hid, title_start))


def on(day, sections):
    for s in sections:
        s["weekday"] = day
    return sections


THROUGH = (" Through our Lord Jesus Christ, your Son, who lives and reigns with you in the unity of the Holy Spirit, God, for ever and ever. Amen.",
           " Door onze Heer Jezus Christus, uw Zoon, die met U leeft en heerst in de eenheid van de Heilige Geest, God, door alle eeuwen der eeuwen. Amen.")


def collect(en, nl, ending=True):
    return (en + THROUGH[0], nl + THROUGH[1]) if ending else (en, nl)


def intercessions(intro, petitions):
    """intro = (en, nl, response_en, response_nl); petitions = [(en, nl)]."""
    ie, inl, re_, rnl = intro
    en = [f"{ie} — {re_}"] + [f"{p} — {re_}" for p, _ in petitions]
    nl = [f"{inl} — {rnl}"] + [f"{p} — {rnl}" for _, p in petitions]
    return text("Intercessions", "Voorbeden", en, nl)


# ---------------------------------------------------------------- the prayer of each day (Lauds, Office of Readings)

DAY_PRAYER = {
    SUN: collect("Almighty, everlasting God, who on the first day of the week raised your Son from the dead: grant that we, who were buried with Him in baptism, may rise with Him and walk in newness of life.",
                 "Almachtige, eeuwige God, die op de eerste dag van de week uw Zoon uit de doden hebt opgewekt: geef dat wij, die met Hem begraven zijn in de doop, met Hem opstaan en wandelen in een nieuw leven."),
    MON: collect("Lord God, in the morning we come before you: bless the work of our hands, and let all that we begin today be done for your glory and for the good of our neighbour.",
                 "Heer God, in de morgen komen wij voor U: zegen het werk van onze handen, en laat alles wat wij vandaag beginnen gebeuren tot uw eer en tot welzijn van onze naaste."),
    TUE: collect("Almighty God, the night is far spent and the day is at hand: help us to cast off the works of darkness, to put on the armour of light, and to walk honestly as in the day.",
                 "Almachtige God, de nacht is ver gevorderd en de dag is nabij: help ons de werken van de duisternis af te leggen, de wapenrusting van het licht aan te doen en eerbaar te wandelen als bij dag."),
    WED: collect("Lord God, whose word is a lamp for our feet: give us today an obedient heart, that we may hear your commandments, keep them and walk in your ways.",
                 "Heer God, wiens woord een lamp is voor onze voet: geef ons vandaag een gehoorzaam hart, opdat wij uw geboden horen, bewaren en op uw wegen gaan."),
    THU: collect("Lord God, heaven is your throne and the earth your footstool: look upon us who are humble and contrite in spirit and tremble at your word, and make us a dwelling for your glory.",
                 "Heer God, de hemel is uw troon en de aarde de voetbank van uw voeten: zie neer op ons, nederig en verslagen van geest, die beven voor uw woord, en maak ons tot een woning van uw heerlijkheid."),
    FRI: ("Lord Jesus Christ, who stretched out your arms on the Cross to draw all people to yourself: let our lives today be marked by your love, that we may be kind to one another and forgiving, as God in you has forgiven us; who live and reign for ever and ever. Amen.",
          "Heer Jezus Christus, die uw armen aan het Kruis hebt uitgestrekt om alle mensen naar U toe te trekken: laat ons leven vandaag getekend zijn door uw liefde, opdat wij goed zijn voor elkaar en vergevingsgezind, zoals God ons in U heeft vergeven; die leeft en heerst in de eeuwen der eeuwen. Amen."),
    SAT: collect("Lord God, source of salvation, who gave us the Virgin Mary as a model of faith: grant that through her prayers we may hold fast to your word and come to the joy of your eternal kingdom.",
                 "Heer God, bron van heil, die ons de Maagd Maria hebt gegeven als voorbeeld van geloof: geef dat wij op haar voorspraak vasthouden aan uw woord en komen tot de vreugde van uw eeuwig koninkrijk."),
}

BLESSING = ("May the Lord bless us, keep us from all evil, and bring us to everlasting life. Amen.",
            "De Heer zegene ons, beware ons voor alle kwaad en geleide ons naar het eeuwig leven. Amen.")


def concluding(en_nl, blessing=BLESSING):
    return text("Concluding prayer", "Slotgebed", [en_nl[0], blessing[0]], [en_nl[1], blessing[1]])


# ---------------------------------------------------------------- Office of Readings

SECOND_READINGS = {
    SUN: None,  # St Augustine, Confessions I.1, from the seed
    MON: ("Second reading · Letter to Diognetus 5", "Tweede lezing · Brief aan Diognetus 5", [
        "Christians are not distinguished from the rest of mankind either in locality or in speech or in customs. For they dwell not somewhere in cities of their own, neither do they use some different language, nor practise an extraordinary kind of life.",
        "They dwell in their own countries, but only as sojourners; they bear their share in all things as citizens, and they endure all hardships as strangers. Every foreign country is a fatherland to them, and every fatherland is foreign.",
        "They find themselves in the flesh, and yet they live not after the flesh. Their existence is on earth, but their citizenship is in heaven. They obey the established laws, and they surpass the laws in their own lives. They love all men, and they are persecuted by all."], [
        "Christenen onderscheiden zich van de andere mensen niet door land, taal of gewoonten. Zij wonen niet ergens in eigen steden, zij spreken geen afwijkende taal en leiden geen opvallend leven.",
        "Zij wonen in hun eigen vaderland, maar als vreemdelingen; zij nemen als burgers aan alles deel, en verdragen alles als vreemden. Elk vreemd land is hun een vaderland, en elk vaderland een vreemd land.",
        "Zij zijn in het vlees, maar leven niet naar het vlees. Zij verblijven op aarde, maar hun burgerschap is in de hemel. Zij gehoorzamen de bestaande wetten, en overtreffen die wetten door hun eigen leven. Zij hebben allen lief, en worden door allen vervolgd."]),
    TUE: ("Second reading · St Cyprian, On the Lord's Prayer 8", "Tweede lezing · H. Cyprianus, Over het Onze Vader 8", [
        "Before all things, the Teacher of peace and the Master of unity would not have prayer to be made singly and individually, as for one who prays to pray for himself alone.",
        "For we say not, My Father, who art in heaven, nor, Give me this day my daily bread; nor does each one ask that only his own debt should be forgiven him, nor does he request for himself alone that he may not be led into temptation and delivered from evil.",
        "Our prayer is public and common; and when we pray, we pray not for one, but for the whole people, because we the whole people are one."], [
        "Vóór alles wilde de Leraar van de vrede en de Meester van de eenheid niet dat het gebed afzonderlijk en individueel zou zijn, zodat wie bidt alleen voor zichzelf bidt.",
        "Want wij zeggen niet: Mijn Vader, die in de hemel zijt, of: Geef mij heden mijn dagelijks brood; ook vraagt niemand dat alleen zijn eigen schuld wordt vergeven, of alleen voor zichzelf dat hij niet in bekoring wordt geleid en van het kwaad wordt verlost.",
        "Ons gebed is openbaar en gemeenschappelijk; en wanneer wij bidden, bidden wij niet voor één, maar voor heel het volk, want wij, heel het volk, zijn één."]),
    WED: ("Second reading · St Ignatius of Antioch, Letter to the Romans 4", "Tweede lezing · H. Ignatius van Antiochië, Brief aan de Romeinen 4", [
        "I write to all the churches, and I bid all men know, that of my own free will I die for God, unless ye should hinder me. I exhort you, be ye not an unseasonable kindness to me.",
        "Let me be given to the wild beasts, for through them I can attain unto God. I am God's wheat, and I am ground by the teeth of wild beasts that I may be found pure bread of Christ."], [
        "Ik schrijf aan alle kerken en laat iedereen weten dat ik uit vrije wil voor God sterf, als u mij niet tegenhoudt. Ik smeek u: bewijs mij geen ontijdige goedheid.",
        "Laat mij een prooi van de wilde dieren worden; door hen kan ik God bereiken. Ik ben Gods tarwe, en ik word gemalen door de tanden van de wilde dieren, opdat ik zuiver brood van Christus word bevonden."]),
    THU: ("Second reading · The Didache 9", "Tweede lezing · De Didache 9", [
        "We give Thee thanks, O our Father, for the holy vine of Thy son David, which Thou madest known unto us through Thy Son Jesus; Thine is the glory for ever.",
        "We give Thee thanks, O our Father, for the life and knowledge which Thou didst make known unto us through Thy Son Jesus; Thine is the glory for ever.",
        "As this broken bread was scattered upon the mountains and being gathered together became one, so may Thy Church be gathered together from the ends of the earth into Thy kingdom; for Thine is the glory and the power through Jesus Christ for ever and ever."], [
        "Wij danken U, onze Vader, voor de heilige wijnstok van David, uw dienaar, die U ons hebt bekendgemaakt door Jezus, uw Dienaar; U zij de heerlijkheid in eeuwigheid.",
        "Wij danken U, onze Vader, voor het leven en de kennis die U ons hebt bekendgemaakt door Jezus, uw Dienaar; U zij de heerlijkheid in eeuwigheid.",
        "Zoals dit gebroken brood verstrooid was over de bergen en, bijeengebracht, één is geworden, zo moge uw Kerk van de einden der aarde worden bijeengebracht in uw koninkrijk; want van U is de heerlijkheid en de kracht door Jezus Christus in eeuwigheid."]),
    FRI: ("Second reading · St Leo the Great, Sermon 21", "Tweede lezing · H. Leo de Grote, Preek 21", [
        "Christian, acknowledge thy dignity, and becoming a partner in the Divine nature, refuse to return to the old baseness by degenerate conduct. Remember the Head and the Body of which thou art a member.",
        "Recollect that thou wert rescued from the power of darkness and brought out into God's light and kingdom. By the mystery of Baptism thou wert made the temple of the Holy Ghost: do not put such a denizen to flight from thee by base acts, and subject thyself once more to the devil's thraldom; because thy purchase money is the blood of Christ."], [
        "Christen, erken je waardigheid. Nu je deel hebt gekregen aan de goddelijke natuur, keer dan niet door een ontaard leven terug tot de oude laagheid. Gedenk van welk Hoofd en van welk Lichaam je lid bent.",
        "Bedenk dat je bent ontrukt aan de macht van de duisternis en overgebracht in het licht en het koninkrijk van God. Door het mysterie van de doop ben je een tempel van de Heilige Geest geworden: verdrijf zo'n Gast niet door slechte daden en onderwerp je niet opnieuw aan de slavernij van de duivel, want je losprijs is het bloed van Christus."]),
    SAT: ("Second reading · St Augustine, Confessions X.27", "Tweede lezing · H. Augustinus, Belijdenissen X.27", [
        "Too late loved I Thee, O Thou Beauty of ancient days, yet ever new! too late I loved Thee! And behold, Thou wert within, and I abroad, and there I searched for Thee; deformed I, plunging amid those fair forms which Thou hadst made.",
        "Thou wert with me, but I was not with Thee. Things held me far from Thee, which, unless they were in Thee, were not at all.",
        "Thou calledst, and shoutedst, and burstest my deafness. Thou flashedst, shonest, and scatteredst my blindness. Thou breathedst odours, and I drew in breath and pant for Thee. I tasted, and hunger and thirst. Thou touchedst me, and I burned for Thy peace."], [
        "Laat heb ik U liefgehad, Schoonheid, zo oud en zo nieuw, laat heb ik U liefgehad! En zie, U was binnen en ik was buiten, en daar zocht ik U; misvormd als ik was, stortte ik mij op de schone dingen die U had gemaakt.",
        "U was bij mij, maar ik was niet bij U. Die dingen hielden mij ver van U, die niet zouden bestaan als zij niet in U waren.",
        "U hebt geroepen en geschreeuwd en mijn doofheid doorbroken. U hebt geflitst en gestraald en mijn blindheid verdreven. U hebt geurig gewaaid, en ik haalde adem en snak naar U. Ik heb geproefd, en nu honger en dorst ik. U hebt mij aangeraakt, en ik brand van verlangen naar uw vrede."]),
}

OOR = {
    SUN: ("Happy the one who delights in the law of the Lord.", "Gelukkig wie vreugde vindt in de wet van de Heer.",
          [psalm(1), psalm(2), psalm(3)], "ROM 6:1-14"),
    MON: ("Turn to me, Lord, and save my life; I will praise you with all my heart.", "Keer terug, Heer, en red mijn ziel; ik zal U loven met heel mijn hart.",
          [psalm(6), psalm(9, "2-11", "I"), psalm(9, "12-21", "II")], "1JN 1:5-2:6"),
    TUE: ("You see the trouble and the grief, Lord; you are the helper of the orphan.", "U ziet de moeite en het verdriet, Heer; U bent de helper van de wees.",
          [psalm(10, "1-11", "I"), psalm(10, "12-18", "II"), psalm(12)], "COL 3:1-17"),
    WED: ("I love you, Lord, my strength, my rock and my deliverer.", "Ik heb U lief, Heer, mijn kracht, mijn rots en mijn bevrijder.",
          [psalm(18, "2-7", "I"), psalm(18, "8-20", "II"), psalm(18, "21-30", "III")], "ISA 55"),
    THU: ("The way of God is perfect; the word of the Lord is tried in the fire.", "Gods weg is volmaakt; het woord van de Heer is gelouterd.",
          [psalm(18, "31-37", "IV"), psalm(18, "38-46", "V"), psalm(18, "47-51", "VI")], "JHN 15:1-17"),
    FRI: ("Rise up, Lord, to help me; say to my soul: I am your salvation.", "Sta op, Heer, mij te hulp; zeg tot mijn ziel: Ik ben uw heil.",
          [psalm(35, "1-3,9-12", "I"), psalm(35, "13-16", "II"), psalm(35, "17-19,22-23,27-28", "III")], "ISA 52:13-53:12"),
    SAT: ("Remember the wonders the Lord has done.", "Gedenkt de wonderen die de Heer heeft gedaan.",
          [psalm(105, "1-11", "I"), psalm(105, "12-22", "II"), psalm(105, "23-45", "III")], "ROM 8:18-39"),
}


def office_of_readings():
    sections = [prayer("deus-in-adiutorium"), seed("readings", "Hymn")]
    for day in DAYS:
        a_en, a_nl, psalms, first = OOR[day]
        second = seed("readings", "Second reading") if SECOND_READINGS[day] is None else text(*SECOND_READINGS[day][:2], *SECOND_READINGS[day][2:])
        sections += on(day, [ant(a_en, a_nl)] + psalms + [
            text("Verse", "Vers", ["Open my eyes, O Lord. — That I may see the wonders of your law."],
                 ["Open mijn ogen, Heer. — Opdat ik de wonderen van uw wet aanschouw."]),
            passage("First reading · " + nice(first), "Eerste lezing · " + nice(first, True), first),
            second])
    te_deum = seed("readings", "Te Deum")
    sections += on(SUN, [rubric("On Sundays outside Lent, on solemnities and feasts:", "Op zondagen buiten de Veertigdagentijd, op hoogfeesten en feesten:"), te_deum])
    for day in DAYS:
        sections += on(day, [concluding(DAY_PRAYER[day], ("Let us praise the Lord. — And give Him thanks.", "Laten wij de Heer loven. — En Hem dank zeggen."))])
    return sections


# ---------------------------------------------------------------- Morning Prayer

LAUDS = {
    SUN: dict(
        psalmody=[("My soul clings to you, O God; your right hand upholds me.", "Mijn ziel klampt zich aan U vast, o God; uw rechterhand ondersteunt mij.", psalm(63, "2-9")),
                  ("All you works of the Lord, bless the Lord; praise and exalt Him for ever.", "Alle werken van de Heer, zegent de Heer; looft en verheft Hem in eeuwigheid.",
                   passage("Canticle · Daniel 3:57-88, 56", "Lofzang · Daniël 3:57-88, 56", "DAN 3:57-88", [("DAN", 3, "58-88,57")], [("S3Y", 1, "34-65,33")])),
                  ("Let the faithful exult in glory; let the praise of God be in their mouths.", "Laat de getrouwen juichen in heerlijkheid; Gods lof zij in hun mond.", psalm(149))],
        reading=reading("REV 7:10-12"),
        resp=("Christ, Son of the living God, have mercy on us. — You are seated at the right hand of the Father: have mercy on us.",
              "Christus, Zoon van de levende God, ontferm U over ons. — U die zit aan de rechterhand van de Vader, ontferm U over ons."),
        bened=("Very early on the first day of the week they came to the tomb, as the sun was rising.", "Zeer vroeg op de eerste dag van de week kwamen zij bij het graf, toen de zon opging."),
        prayers=(("Let us praise Christ, the Sun who rises upon us, and pray:", "Laten wij Christus loven, de Zon die over ons opgaat, en bidden:", "Lord, you are our life and our salvation.", "Heer, U bent ons leven en ons heil."), [
            ("We thank you for the gift of this new day and for the memory of your resurrection.", "Wij danken U voor het geschenk van deze nieuwe dag en voor de gedachtenis van uw verrijzenis."),
            ("Let your Holy Spirit teach us today to do your will.", "Laat uw Heilige Geest ons vandaag leren uw wil te doen."),
            ("As we gather on the Lord's day, nourish us at the table of your word and of your Body.", "Voed ons, nu wij samenkomen op de dag van de Heer, aan de tafel van uw woord en van uw Lichaam."),
            ("Let the poor and the lonely receive your joy through us.", "Laat de armen en de eenzamen door ons uw vreugde ontvangen.")])),
    MON: dict(
        psalmody=[("To you I pray, O Lord; in the morning you hear my voice.", "Tot U bid ik, Heer; in de morgen hoort U mijn stem.", psalm(5, "2-10,12-13")),
                  ("We praise your glorious name, O Lord our God.", "Wij loven uw heerlijke Naam, Heer onze God.", passage("Canticle · 1 Chronicles 29:10-13", "Lofzang · 1 Kronieken 29:10-13", "1CH 29:10-13")),
                  ("Worship the Lord in the splendour of holiness.", "Aanbidt de Heer in heilige luister.", psalm(29))],
        reading=reading("2TH 3:10-13"),
        resp=("Blessed be the Lord from age to age. — He alone does wondrous deeds.", "Gezegend de Heer van eeuwigheid tot eeuwigheid. — Hij alleen doet wonderen."),
        bened=("Blessed be the Lord, for He has come to His people and set them free.", "Gezegend de Heer, want Hij is naar zijn volk gekomen en heeft het bevrijd."),
        prayers=(("Let us bless Christ, who gives us this day, and pray:", "Laten wij Christus zegenen, die ons deze dag geeft, en bidden:", "Bless and sanctify us, Lord.", "Zegen en heilig ons, Heer."), [
            ("You give us work to do: let us do it faithfully and for your glory.", "U geeft ons werk te doen: laat ons het trouw doen en tot uw eer."),
            ("Make us patient with one another and quick to forgive.", "Maak ons geduldig met elkaar en snel bereid tot vergeven."),
            ("Be with all who are looking for work and with those who are weary of their labour.", "Wees bij allen die werk zoeken en bij hen die moe zijn van hun arbeid."),
            ("Keep us this day from every sin.", "Bewaar ons deze dag voor iedere zonde.")])),
    TUE: dict(
        psalmody=[("The one with clean hands and a pure heart shall climb the mountain of the Lord.", "Wie reine handen heeft en een zuiver hart, zal de berg van de Heer bestijgen.", psalm(24)),
                  ("Blessed be God who lives for ever; His kingdom endures through all ages.", "Gezegend zij God die eeuwig leeft; zijn koninkrijk duurt door alle eeuwen.",
                   passage("Canticle · Tobit 13:1-8", "Lofzang · Tobit 13:1-8", "TOB 13:1-8", [("TOB", 13, "1-8")], [("TOB", 13, "2-9")])),
                  ("The earth is full of the steadfast love of the Lord.", "De aarde is vol van de goedertierenheid van de Heer.", psalm(33))],
        reading=reading("ROM 13:11-13"),
        resp=("My God, my help, in Him I put my trust. — He will free me from the snare of the fowler.", "Mijn God, mijn hulp, op Hem vertrouw ik. — Hij bevrijdt mij uit de strik van de vogelvanger."),
        bened=("Save us, Lord, from the hands of all who hate us.", "Red ons, Heer, uit de hand van allen die ons haten."),
        prayers=(("Let us pray to Christ, who was raised to new life:", "Laten wij bidden tot Christus, die is opgewekt tot nieuw leven:", "Save us, Lord, by your resurrection.", "Red ons, Heer, door uw verrijzenis."), [
            ("Light of the world, let the night of sin pass from us, and clothe us with the armour of light.", "Licht van de wereld, laat de nacht van de zonde van ons wijken en bekleed ons met de wapenrusting van het licht."),
            ("Make our words today kind and true.", "Maak onze woorden vandaag vriendelijk en waar."),
            ("Strengthen all who suffer persecution for your name.", "Sterk allen die om uw Naam vervolging lijden."),
            ("Give wisdom and peace to all who govern the nations.", "Geef wijsheid en vrede aan allen die de volken besturen.")])),
    WED: dict(
        psalmody=[("In your light, O Lord, we see light.", "In uw licht, Heer, zien wij het licht.", psalm(36)),
                  ("O Lord, you are great and glorious, wonderful in strength and invincible.", "Heer, U bent groot en heerlijk, wonderbaar in kracht en onoverwinnelijk.",
                   passage("Canticle · Judith 16:2-3, 13-15", "Lofzang · Judit 16:2-3, 13-15", "JDT 16:2-15", [("JDT", 16, "2-3,13-15")], [("JDT", 16, "2-3,15-18")])),
                  ("Shout to God with cries of gladness; God is King of all the earth.", "Juicht voor God met jubelklank; God is Koning over heel de aarde.", psalm(47))],
        reading=reading("TOB 4:15-19", [("TOB", 4, "15-16,18-19")], [("TOB", 4, "16-17,19-20")]),
        resp=("Incline my heart to your decrees, O Lord. — Give me life in your ways.", "Neig mijn hart tot uw getuigenissen, Heer. — Maak mij levend op uw wegen."),
        bened=("Show us your mercy, Lord, and remember your holy covenant.", "Toon ons uw barmhartigheid, Heer, en gedenk uw heilig verbond."),
        prayers=(("Let us give thanks to Christ, who feeds us with His word, and pray:", "Laten wij Christus danken, die ons voedt met zijn woord, en bidden:", "Lord, guide us in your truth.", "Heer, leid ons in uw waarheid."), [
            ("Teach us to do to others as we would have them do to us.", "Leer ons anderen te doen wat wij willen dat zij ons doen."),
            ("Make us generous to the hungry and to all in need.", "Maak ons vrijgevig voor de hongerigen en voor allen die in nood zijn."),
            ("Help us to seek good counsel and to accept it with humility.", "Help ons goede raad te zoeken en die nederig te aanvaarden."),
            ("Bless the Church in her teachers and in all who hand on the faith.", "Zegen de Kerk in haar leraren en in allen die het geloof doorgeven.")])),
    THU: dict(
        psalmody=[("Awake, my soul; I will awaken the dawn.", "Ontwaak, mijn ziel; ik wil de dageraad wekken.", psalm(57, "2-12")),
                  ("My people shall be filled with my good things, says the Lord.", "Mijn volk zal verzadigd worden met mijn goederen, spreekt de Heer.", passage("Canticle · Jeremiah 31:10-14", "Lofzang · Jeremia 31:10-14", "JER 31:10-14")),
                  ("Great is the Lord and greatly to be praised in the city of our God.", "Groot is de Heer en zeer te prijzen in de stad van onze God.", psalm(48))],
        reading=reading("ISA 66:1-2"),
        resp=("I call with all my heart; answer me, O Lord. — I will keep your statutes.", "Met heel mijn hart roep ik; antwoord mij, Heer. — Ik zal uw inzettingen bewaren."),
        bened=("Let us serve the Lord in holiness, and He will free us from the hands of our enemies.", "Laten wij de Heer dienen in heiligheid, en Hij zal ons verlossen uit de hand van onze vijanden."),
        prayers=(("Let us pray to Christ, the Bread of life:", "Laten wij bidden tot Christus, het Brood des levens:", "Lord, give us this bread always.", "Heer, geef ons altijd dit brood."), [
            ("You gave yourself to us at the Last Supper: make us grateful for your gift.", "U hebt Uzelf aan ons gegeven bij het Laatste Avondmaal: maak ons dankbaar voor uw gave."),
            ("Gather your scattered people into one.", "Breng uw verstrooide volk samen tot één."),
            ("Give daily bread to the hungry, and the bread of your word to all who seek you.", "Geef de hongerigen hun dagelijks brood, en het brood van uw woord aan allen die U zoeken."),
            ("Make us humble and contrite, and attentive to your word.", "Maak ons nederig en berouwvol, en aandachtig voor uw woord.")])),
    FRI: dict(
        psalmody=[("Create a clean heart in me, O God; put a steadfast spirit within me.", "Schep mij een rein hart, o God, en vernieuw in mij een standvastige geest.", psalm(51)),
                  ("Truly you are a hidden God, the God of Israel, the Saviour.", "Waarlijk, U bent een verborgen God, de God van Israël, de Heiland.", passage("Canticle · Isaiah 45:15-25", "Lofzang · Jesaja 45:15-25", "ISA 45:15-25")),
                  ("Come into His presence with songs of joy.", "Komt voor zijn aangezicht met gejuich.", psalm(100))],
        reading=reading("EPH 4:29-32"),
        resp=("At daybreak let me hear of your love, O Lord. — For in you I trust.", "Laat mij in de morgen uw goedertierenheid horen, Heer. — Want op U vertrouw ik."),
        bened=("Through the tender mercy of our God, the dawn from on high has visited us.", "Door de innige barmhartigheid van onze God heeft de Opgang uit de hoge ons bezocht."),
        prayers=(("Let us adore Christ, who by His Cross brought salvation to the world, and pray:", "Laten wij Christus aanbidden, die door zijn Kruis de wereld het heil heeft gebracht, en bidden:", "Lord, by your Cross, save us.", "Heer, red ons door uw Kruis."), [
            ("You took our sins upon yourself: let us die to sin and live for you.", "U hebt onze zonden op U genomen: laat ons sterven aan de zonde en leven voor U."),
            ("Be near to the sick and the dying, and to all who carry a heavy cross.", "Wees nabij aan de zieken en de stervenden, en aan allen die een zwaar kruis dragen."),
            ("Let no bitter or harsh word leave our mouths today.", "Laat vandaag geen bitter of hard woord over onze lippen komen."),
            ("Teach us to forgive one another as God has forgiven us in you.", "Leer ons elkaar te vergeven, zoals God ons in U heeft vergeven.")])),
    SAT: dict(
        psalmody=[("You are near, O Lord, and all your commands are truth.", "U bent nabij, Heer, en al uw geboden zijn waarheid.", psalm(119, "145-152", "XIX (Qoph)")),
                  ("The Lord is my strength and my song; He has become my salvation.", "De Heer is mijn kracht en mijn lied; Hij is mij tot heil geworden.",
                   passage("Canticle · Exodus 15:1-4, 8-13, 17-18", "Lofzang · Exodus 15:1-4, 8-13, 17-18", "EXO 15:1-18", [("EXO", 15, "1-4,8-13,17-18")], [("EXO", 15, "1-4,8-13,17-18")])),
                  ("Great is His love for us; the faithfulness of the Lord endures for ever.", "Groot is zijn goedertierenheid over ons; de trouw van de Heer duurt eeuwig.", psalm(117))],
        reading=reading("2PE 1:10-11"),
        resp=("I call to you, Lord; you are my refuge. — My portion in the land of the living.", "Ik roep tot U, Heer; U bent mijn toevlucht. — Mijn deel in het land van de levenden."),
        bened=("Guide our feet, Lord, into the way of peace.", "Richt onze voeten, Heer, op de weg van de vrede."),
        prayers=(("Let us praise Christ, born of the Virgin Mary, and pray:", "Laten wij Christus loven, geboren uit de Maagd Maria, en bidden:", "May your holy Mother pray for us, Lord.", "Laat uw heilige Moeder voor ons bidden, Heer."), [
            ("Sun of justice, keep us faithful as we wait for your coming.", "Zon der gerechtigheid, houd ons trouw terwijl wij uw komst verwachten."),
            ("You made your Mother the first of believers: give us faith to say yes to your word.", "U hebt uw Moeder de eerste van de gelovigen gemaakt: geef ons geloof om ja te zeggen op uw woord."),
            ("Bring rest to the weary and comfort to all who mourn.", "Geef rust aan de vermoeiden en troost aan allen die rouwen."),
            ("Prepare our hearts to celebrate the day of your resurrection.", "Bereid ons hart voor om de dag van uw verrijzenis te vieren.")])),
}


def lauds():
    sections = [
        seed("lauds", "Invitatory"),
        ant("Come, let us worship the Lord, for He is our God.", "Komt, laten wij de Heer aanbidden, want Hij is onze God."),
        psalm(95),
        seed("lauds", "Hymn"),
    ]
    for day in DAYS:
        d = LAUDS[day]
        part = []
        for a_en, a_nl, s in d["psalmody"]:
            part += [ant(a_en, a_nl), s]
        part += [d["reading"], resp(*d["resp"]), ant(*d["bened"])]
        sections += on(day, part)
    sections.append({"kind": "scripture", "title": tr("Benedictus", "Benedictus"), "ref": "LUK 1:68-79"})
    for day in DAYS:
        sections += on(day, [intercessions(*LAUDS[day]["prayers"])])
    sections.append(prayer("our-father"))
    for day in DAYS:
        sections += on(day, [concluding(DAY_PRAYER[day])])
    return sections


# ---------------------------------------------------------------- Daytime Prayer

DAYTIME = {
    SUN: ("This is the day the Lord has made; let us rejoice and be glad.", "Dit is de dag die de Heer gemaakt heeft; laten wij juichen en ons verheugen.",
          [psalm(118, "1-9", "I"), psalm(118, "10-18", "II"), psalm(118, "19-29", "III")]),
    MON: ("The law of the Lord is perfect; it revives the soul.", "De wet van de Heer is volmaakt; zij bekeert de ziel.",
          [psalm(19, "8-15"), psalm(7, "2-10", "I"), psalm(7, "11-18", "II")]),
    TUE: ("Happy those whose way is blameless, who walk in the law of the Lord.", "Gelukkig wie oprecht van wandel zijn, die gaan in de wet van de Heer.",
          [psalm(119, "1-8", "I (Aleph)"), psalm(13), psalm(14)]),
    WED: ("Guard me as the apple of your eye; hide me in the shadow of your wings.", "Behoed mij als uw oogappel; verberg mij in de schaduw van uw vleugels.",
          [psalm(119, "9-16", "II (Beth)"), psalm(17, "1-7", "I"), psalm(17, "8-15", "II")]),
    THU: ("Make me know your ways, O Lord; teach me your paths.", "Maak mij uw wegen bekend, Heer; leer mij uw paden.",
          [psalm(119, "17-24", "III (Gimel)"), psalm(25, "1-11", "I"), psalm(25, "12-22", "II")]),
    FRI: ("Lead me, Lord, on a level path.", "Leid mij, Heer, op een effen pad.",
          [psalm(119, "25-32", "IV (Daleth)"), psalm(26), psalm(28, "1-3,6-9")]),
    SAT: ("Taste and see that the Lord is good.", "Proeft en ziet dat de Heer goed is.",
          [psalm(119, "33-40", "V (He)"), psalm(34, "2-11", "I"), psalm(34, "12-23", "II")]),
}

COMPLEMENTARY = rubric(
    "Complementary psalmody, for those who pray more than one daytime hour. If this is the only daytime hour you pray, use the psalms of the day from Midday Prayer.",
    "Aanvullende psalmen, voor wie meer dan één klein uur bidt. Bid je alleen dit uur, neem dan de psalmen van de dag uit de Sext.")


def small_hour(hid, psalms, reading_ref, response, own_psalmody=False):
    sections = [prayer("deus-in-adiutorium"), seed(hid, "Hymn")]
    if own_psalmody:
        for day in DAYS:
            a_en, a_nl, ps = DAYTIME[day]
            sections += on(day, [ant(a_en, a_nl)] + ps)
    else:
        sections += [COMPLEMENTARY] + [psalm(n) for n in psalms]
    sections += [reading(reading_ref), resp(*response), seed(hid, "Concluding prayer")]
    return sections


# ---------------------------------------------------------------- Evening Prayer

VESPERS = {
    SAT: dict(  # Evening Prayer I of Sunday
        psalmody=[("Let my prayer rise before you like incense, O Lord.", "Laat mijn gebed als reukwerk voor U opstijgen, Heer.", psalm(141, "1-9")),
                  ("You are my refuge, Lord, my portion in the land of the living.", "U bent mijn toevlucht, Heer, mijn deel in het land van de levenden.", psalm(142)),
                  ("At the name of Jesus every knee shall bend, in heaven and on earth.", "In de naam van Jezus zal elke knie zich buigen, in de hemel en op aarde.",
                   passage("Canticle · Philippians 2:6-11", "Lofzang · Filippenzen 2:6-11", "PHP 2:6-11"))],
        reading=reading("ROM 11:33-36"),
        resp=("How great are your works, O Lord. — In wisdom you have made them all.", "Hoe groot zijn uw werken, Heer. — Met wijsheid hebt U ze alle gemaakt."),
        magn=("The Almighty has done great things for me, and holy is His name.", "De Machtige heeft grote dingen aan mij gedaan, en heilig is zijn Naam."),
        prayers=(("Let us pray to the one God, Father, Son and Holy Spirit:", "Laten wij bidden tot de ene God, Vader, Zoon en Heilige Geest:", "Lord, be with your people.", "Heer, wees met uw volk."), [
            ("As this week comes to an end, we thank you for all its gifts.", "Nu deze week ten einde loopt, danken wij U voor al haar gaven."),
            ("Make your Church a sign of your peace among the nations.", "Maak uw Kerk tot een teken van uw vrede onder de volken."),
            ("Give all who will celebrate the Eucharist tomorrow a pure and joyful heart.", "Geef allen die morgen de Eucharistie vieren een rein en blij hart."),
            ("Grant eternal rest to the departed, and let perpetual light shine upon them.", "Geef de overledenen de eeuwige rust, en het eeuwige licht verlichte hen.")]),
        prayer=collect("Lord God, as evening falls we give you thanks for this week: forgive what we have done wrong, accept what was good, and prepare us to celebrate with joy the day of your Son's resurrection.",
                       "Heer God, nu het avond wordt danken wij U voor deze week: vergeef wat wij verkeerd hebben gedaan, aanvaard wat goed was, en bereid ons voor om met vreugde de dag van de verrijzenis van uw Zoon te vieren.")),
    SUN: dict(  # Evening Prayer II
        psalmody=[("The Lord said to my Lord: Sit at my right hand.", "De Heer sprak tot mijn Heer: Zit aan mijn rechterhand.", psalm(110, "1-5,7")),
                  ("Tremble, O earth, before the face of the Lord.", "Beef, aarde, voor het aangezicht van de Heer.", psalm(114)),
                  ("Alleluia. Salvation, glory and power belong to our God, alleluia.", "Alleluia. Het heil, de heerlijkheid en de kracht zijn van onze God, alleluia.",
                   passage("Canticle · Revelation 19:1-7", "Lofzang · Openbaring 19:1-7", "REV 19:1-7"))],
        reading=reading("2CO 1:3-4"),
        resp=("Blessed are you, O Lord, in the vault of heaven. — You are worthy of praise and glory for ever.", "Gezegend bent U, Heer, in het uitspansel van de hemel. — U komt lof en eer toe in eeuwigheid."),
        magn=("My soul magnifies the Lord, for He has looked upon His lowly servant.", "Mijn ziel maakt groot de Heer, want Hij heeft neergezien op zijn nederige dienstmaagd."),
        prayers=None,  # from the seed
        prayer=None),
    MON: dict(
        psalmody=[("The Lord is in His holy temple; His eyes watch over the children of men.", "De Heer is in zijn heilige tempel; zijn ogen slaan de mensenkinderen gade.", psalm(11)),
                  ("Blessed are the pure in heart, for they shall see God.", "Zalig de zuiveren van hart, want zij zullen God zien.", psalm(15)),
                  ("God chose us in Christ before the foundation of the world.", "God heeft ons in Christus uitverkoren vóór de grondlegging van de wereld.",
                   passage("Canticle · Ephesians 1:3-10", "Lofzang · Efeziërs 1:3-10", "EPH 1:3-10"))],
        reading=reading("COL 1:9-11"),
        resp=("Heal my soul, for I have sinned against you. — I said: Lord, be gracious to me.", "Genees mijn ziel, want ik heb tegen U gezondigd. — Ik zei: Heer, wees mij genadig."),
        magn=("My spirit rejoices in God my Saviour.", "Mijn geest juicht in God, mijn Redder."),
        prayers=(("Let us pray to Christ, who wills that all should be saved:", "Laten wij bidden tot Christus, die wil dat allen gered worden:", "Lord, draw all people to yourself.", "Heer, trek alle mensen naar U toe."), [
            ("We thank you for all you have given us today; forgive what we have done amiss.", "Wij danken U voor alles wat U ons vandaag hebt gegeven; vergeef wat wij verkeerd hebben gedaan."),
            ("Be with all who work through the night and keep watch over others.", "Wees bij allen die 's nachts werken en over anderen waken."),
            ("Strengthen the sick and give peace to the dying.", "Sterk de zieken en geef vrede aan de stervenden."),
            ("Bring our departed brothers and sisters into your light.", "Breng onze overleden broeders en zusters in uw licht.")]),
        prayer=collect("Lord God, light that never fades: as evening falls, let your light shine in our hearts, that we may know your will and walk in it all our days.",
                       "Heer God, licht dat nooit dooft: laat nu het avond wordt uw licht stralen in ons hart, opdat wij uw wil kennen en die al onze dagen volgen.")),
    TUE: dict(
        psalmody=[("May the Lord answer you in the day of trouble.", "De Heer verhore u op de dag van de benauwdheid.", psalm(20)),
                  ("We will sing and praise your might, O Lord.", "Wij zullen uw macht bezingen en loven, Heer.", psalm(21, "2-8,14")),
                  ("Worthy are you, O Lord, to receive glory and honour and power.", "U bent waardig, Heer, de heerlijkheid, de eer en de kracht te ontvangen.",
                   passage("Canticle · Revelation 4:11; 5:9-10, 12", "Lofzang · Openbaring 4:11; 5:9-10, 12", "REV 4:11-5:12", [("REV", 4, "11"), ("REV", 5, "9-10,12")], [("REV", 4, "11"), ("REV", 5, "9-10,12")]))],
        reading=reading("1JN 3:1-2"),
        resp=("Your love, O Lord, fills us with joy. — We will sing for joy all our days.", "Uw goedertierenheid, Heer, vervult ons met vreugde. — Wij zullen juichen al onze dagen."),
        magn=("He has cast down the mighty from their thrones and lifted up the lowly.", "Hij heeft machtigen van hun troon gestoten en nederigen verheven."),
        prayers=(("Let us pray to Christ, the shepherd and guardian of our souls:", "Laten wij bidden tot Christus, de herder en hoeder van onze zielen:", "Lord, keep us in your love.", "Heer, bewaar ons in uw liefde."), [
            ("Keep the bishops and priests of your Church faithful to the Gospel.", "Houd de bisschoppen en priesters van uw Kerk trouw aan het Evangelie."),
            ("Give young people courage to find their way in life.", "Geef jonge mensen moed om hun weg in het leven te vinden."),
            ("Comfort the lonely and those who have no one to pray for them.", "Troost de eenzamen en hen voor wie niemand bidt."),
            ("Receive the departed into your kingdom.", "Neem de overledenen op in uw koninkrijk.")]),
        prayer=collect("Lord, we are your children now, though what we shall be has not yet been revealed: keep us in your love tonight, and bring us at last to see you as you are.",
                       "Heer, wij zijn nu uw kinderen, al is nog niet geopenbaard wat wij zullen zijn: bewaar ons deze nacht in uw liefde en breng ons eens zover dat wij U zien zoals U bent.")),
    WED: dict(
        psalmody=[("The Lord is my light and my salvation; whom shall I fear?", "De Heer is mijn licht en mijn heil; voor wie zou ik vrezen?", psalm(27, "1-6", "I")),
                  ("Your face, O Lord, I seek; hide not your face from me.", "Uw aangezicht, Heer, zoek ik; verberg uw aangezicht niet voor mij.", psalm(27, "7-14", "II")),
                  ("He is the firstborn of all creation; in Him all things hold together.", "Hij is de eerstgeborene van heel de schepping; alles heeft in Hem zijn bestaan.",
                   passage("Canticle · Colossians 1:12-20", "Lofzang · Kolossenzen 1:12-20", "COL 1:12-20"))],
        reading=reading("JAS 1:22-25"),
        resp=("Keep us, Lord, as the apple of your eye. — Hide us in the shadow of your wings.", "Behoed ons, Heer, als uw oogappel. — Verberg ons in de schaduw van uw vleugels."),
        magn=("His mercy is from age to age on those who fear Him.", "Zijn barmhartigheid is van geslacht tot geslacht over hen die Hem vrezen."),
        prayers=(("Let us pray to God our Father, who calls us to hear and do His word:", "Laten wij bidden tot God onze Vader, die ons roept zijn woord te horen en te doen:", "Lord, make us doers of your word.", "Heer, maak ons tot daders van uw woord."), [
            ("Let the seed of your word sown today bear fruit in us.", "Laat het zaad van uw woord dat vandaag is gezaaid in ons vrucht dragen."),
            ("Give strength to the persecuted and courage to those who speak the truth.", "Geef kracht aan de vervolgden en moed aan hen die de waarheid spreken."),
            ("Bless families and all who care for children and the elderly.", "Zegen de gezinnen en allen die zorgen voor kinderen en ouderen."),
            ("Give the departed a share in the inheritance of the saints in light.", "Geef de overledenen deel aan de erfenis van de heiligen in het licht.")]),
        prayer=collect("Father, who rescued us from the power of darkness and brought us into the kingdom of your beloved Son: keep us this night in His peace, and make us doers of your word, not hearers only.",
                       "Vader, die ons hebt verlost uit de macht van de duisternis en overgebracht in het koninkrijk van uw geliefde Zoon: bewaar ons deze nacht in zijn vrede en maak ons tot daders van uw woord, niet alleen tot hoorders.")),
    THU: dict(
        psalmody=[("I will extol you, Lord, for you have lifted me up.", "Ik zal U verheffen, Heer, want U hebt mij opgetrokken.", psalm(30)),
                  ("Happy the one whose sin is forgiven.", "Gelukkig wie van zijn overtreding is vergeven.", psalm(32)),
                  ("Now the salvation and the power and the kingdom of our God have come.", "Nu is gekomen het heil en de kracht en het koninkrijk van onze God.",
                   passage("Canticle · Revelation 11:17-18; 12:10-12", "Lofzang · Openbaring 11:17-18; 12:10-12", "REV 11:17-12:12", [("REV", 11, "17-18"), ("REV", 12, "10-12")], [("REV", 11, "17-18"), ("REV", 12, "10-12")]))],
        reading=reading("1PE 1:6-9"),
        resp=("The Lord fed us with the finest wheat. — He satisfied us with honey from the rock.", "De Heer voedde ons met het fijnste koren. — Hij verzadigde ons met honing uit de rots."),
        magn=("The Lord has filled the hungry with good things.", "Hongerigen heeft de Heer met goederen vervuld."),
        prayers=(("Let us pray to Christ, who gave Himself to us in the Eucharist:", "Laten wij bidden tot Christus, die Zich aan ons heeft gegeven in de Eucharistie:", "Lord, gather us into one.", "Heer, breng ons samen tot één."), [
            ("Let all who share one bread become one body.", "Laat allen die één brood delen één lichaam worden."),
            ("Call many to serve your people as priests and religious.", "Roep velen om uw volk te dienen als priester of religieus."),
            ("Feed the hungry, and teach us to share what we have.", "Voed de hongerigen en leer ons te delen wat wij hebben."),
            ("Raise up the departed on the last day.", "Wek de overledenen op op de laatste dag.")]),
        prayer=("Lord Jesus Christ, who left us the memorial of your passion in the sacrament of the altar: grant that we may so honour the mystery of your Body and Blood that we always know in ourselves the fruit of your redemption; who live and reign for ever and ever. Amen.",
                "Heer Jezus Christus, die ons in het sacrament van het altaar de gedachtenis van uw lijden hebt nagelaten: geef dat wij het mysterie van uw Lichaam en Bloed zo vereren, dat wij altijd de vrucht van uw verlossing in ons ervaren; die leeft en heerst in de eeuwen der eeuwen. Amen.")),
    FRI: dict(
        psalmody=[("Heal my soul, O Lord, for I have sinned against you.", "Genees mijn ziel, Heer, want ik heb tegen U gezondigd.", psalm(41)),
                  ("The Lord of hosts is with us; the God of Jacob is our stronghold.", "De Heer der heerscharen is met ons; de God van Jakob is ons een vesting.", psalm(46)),
                  ("All nations shall come and worship before you, O Lord.", "Alle volken zullen komen en voor U aanbidden, Heer.",
                   passage("Canticle · Revelation 15:3-4", "Lofzang · Openbaring 15:3-4", "REV 15:3-4"))],
        reading=reading("ROM 15:1-3"),
        resp=("Christ loved us and washed away our sins in His blood. — He made us a kingdom and priests to God.", "Christus heeft ons liefgehad en ons van onze zonden gewassen in zijn bloed. — Hij heeft ons gemaakt tot een koninkrijk en priesters voor God."),
        magn=("The Lord has come to the help of His servant, remembering His mercy.", "Hij heeft Zich ontfermd over zijn dienaar en zijn barmhartigheid gedacht."),
        prayers=(("Let us pray to Christ, who by His death destroyed death:", "Laten wij bidden tot Christus, die door zijn dood de dood heeft vernietigd:", "Lord, by your Cross, give us life.", "Heer, geef ons leven door uw Kruis."), [
            ("You suffered under Pontius Pilate: be with all who are unjustly condemned.", "U hebt geleden onder Pontius Pilatus: wees bij allen die onrechtvaardig worden veroordeeld."),
            ("Give strength to those who are tempted to despair.", "Geef kracht aan hen die in de verleiding zijn te wanhopen."),
            ("Teach us to bear one another's burdens.", "Leer ons elkaars lasten te dragen."),
            ("Open paradise to the dying, as you opened it to the penitent thief.", "Open het paradijs voor de stervenden, zoals U het opende voor de berouwvolle rover.")]),
        prayer=collect("Lord God, who willed that your Son should bear the Cross for our salvation: grant that we who have known His mystery on earth may receive the reward of His redemption in heaven.",
                       "Heer God, die hebt gewild dat uw Zoon het Kruis zou dragen tot ons heil: geef dat wij, die zijn mysterie op aarde hebben leren kennen, het loon van zijn verlossing in de hemel ontvangen.")),
}


def vespers():
    sections = [prayer("deus-in-adiutorium"), seed("vespers", "Hymn")]
    for day in DAYS:
        d = VESPERS[day]
        part = []
        for a_en, a_nl, s in d["psalmody"]:
            part += [ant(a_en, a_nl), s]
        part += [d["reading"], resp(*d["resp"]), ant(*d["magn"])]
        sections += on(day, part)
    sections.append({"kind": "scripture", "title": tr("Magnificat", "Magnificat"), "ref": "LUK 1:46-55"})
    for day in DAYS:
        d = VESPERS[day]
        if d["prayers"] is None:
            sunday = seed("vespers", "Intercessions")
            sunday["title"] = tr("Intercessions", "Voorbeden")
            for lang in ("en", "nl"):
                *petitions, closing = sunday["body"][lang]
                sunday["body"][lang] = petitions
            sections += on(day, [sunday])
        else:
            sections += on(day, [intercessions(*d["prayers"])])
    sections.append(prayer("our-father"))
    for day in DAYS:
        d = VESPERS[day]
        if d["prayer"] is None:
            body = seed("vespers", "Intercessions")["body"]
            sections += on(day, [concluding((body["en"][-1], body["nl"][-1]))])
        else:
            sections += on(day, [concluding(d["prayer"])])
    return sections


# ---------------------------------------------------------------- Night Prayer

COMPLINE = {
    SAT: ([("Have mercy on me, Lord, and hear my prayer.", "Wees mij genadig, Heer, en hoor mijn gebed.", psalm(4)),
           ("In the night lift up your hands and bless the Lord.", "Heft in de nacht uw handen op en zegent de Heer.", psalm(134))], "DEU 6:4-7"),
    SUN: ([("He will cover you with His wings; you will not fear the terror of the night.", "Hij zal u dekken met zijn vlerken; u zult niet vrezen voor de verschrikking van de nacht.", psalm(91))], "REV 22:4-5"),
    MON: ([("Turn to me, Lord, and have mercy; save your servant who trusts in you.", "Wend U tot mij, Heer, en wees mij genadig; red uw dienaar die op U vertrouwt.", psalm(86))], "1TH 5:9-10"),
    TUE: ([("Do not hide your face from me, for in you I put my trust.", "Verberg uw aangezicht niet voor mij, want op U vertrouw ik.", psalm(143, "1-11"))], "1PE 5:8-9"),
    WED: ([("Be a rock of refuge for me, a mighty stronghold to save me.", "Wees mij een rots van toevlucht, een burcht om mij te redden.", psalm(31, "2-6")),
           ("Out of the depths I cry to you, O Lord.", "Uit de diepten roep ik tot U, Heer.", psalm(130))], "EPH 4:26-27"),
    THU: ([("Keep me, O God, for in you I take refuge.", "Bewaar mij, o God, want bij U schuil ik.", psalm(16))], "1TH 5:23"),
    FRI: ([("Day and night I cry to you, O Lord.", "Dag en nacht roep ik tot U, Heer.", psalm(88))], "JER 14:9"),
}


def compline():
    seeded = {s.get("title", {}).get("en", ""): s for s in SEED["compline"]["sections"]}
    nunc_ant = next(s for s in SEED["compline"]["sections"] if s["kind"] == "rubric")
    sections = [prayer("deus-in-adiutorium"), seed("compline", "Hymn"), seed("compline", "Examination")]
    for day in DAYS:
        psalms, ref = COMPLINE[day]
        part = []
        for a_en, a_nl, s in psalms:
            part += [ant(a_en, a_nl), s]
        sections += on(day, part + [reading(ref)])
    sections += [
        resp("Into your hands, Lord, I commend my spirit. — You have redeemed us, Lord God of truth.",
             "In uw handen, Heer, beveel ik mijn geest. — U hebt ons verlost, Heer, God van trouw."),
        json.loads(json.dumps(nunc_ant)),
        {"kind": "scripture", "title": tr("Nunc Dimittis", "Nunc Dimittis"), "ref": "LUK 2:29-32"},
        seeded["Concluding prayer and blessing"],
        seeded["Salve Regina"],
        seeded["Regina Caeli (in Eastertide)"],
    ]
    return sections


# ---------------------------------------------------------------- the book

def build():
    data = json.load(open(OUT, encoding="utf-8"))
    meta = {h["id"]: h for h in SEED.values()}

    def hour(hid, sections):
        h = {k: v for k, v in meta[hid].items() if k != "sections"}
        h["sections"] = sections
        return h

    data["intro"] = tr(
        "The Liturgy of the Hours is the public prayer of the Church. Morning and Evening Prayer are its two hinges; "
        "the Daytime hours and Night Prayer keep the whole day turned toward God. Each hour follows Week I of the "
        "four-week Psalter, with its own psalms, canticle, reading, intercessions and prayer for every day of the week; "
        "the app shows the texts of today. Hymns are classic public-domain translations (J. M. Neale, J. H. Newman); "
        "psalms and readings are from the World English Bible. Antiphons, intercessions and prayers are provisional "
        "texts of this app, not the official translation.",
        "Het Getijdengebed is het openbare gebed van de Kerk. Lauden en Vespers zijn de twee scharnieren; de kleine "
        "uren en de Completen houden de hele dag naar God gekeerd. Elk uur volgt week I van het psalter, met eigen "
        "psalmen, lofzang, lezing, voorbeden en gebed voor iedere dag van de week; de app toont de teksten van vandaag. "
        "Psalmen en lezingen: Statenvertaling. Antifonen, voorbeden en gebeden zijn voorlopige teksten van deze app, "
        "niet de officiële vertaling.")
    data["hours"] = [
        hour("readings", office_of_readings()),
        hour("lauds", lauds()),
        hour("terce", small_hour("terce", [120, 121, 122], "ROM 5:5",
                                 ("Create a clean heart in me, O God. — Renew a steadfast spirit within me.",
                                  "Schep mij een rein hart, o God. — Vernieuw in mij een standvastige geest."))),
        hour("sext", small_hour("sext", None, "ROM 13:8-10",
                                ("The Lord is my shepherd; I shall not want. — He makes me lie down in green pastures.",
                                 "De Heer is mijn herder; mij zal niets ontbreken. — Hij doet mij neerliggen in grazige weiden."), own_psalmody=True)),
        hour("none", small_hour("none", [126, 127, 128], "COL 3:17",
                                ("The eyes of the Lord are on those who fear Him. — On those who hope in His steadfast love.",
                                 "Het oog van de Heer is op hen die Hem vrezen. — Op hen die hopen op zijn goedertierenheid."))),
        hour("vespers", vespers()),
        hour("compline", compline()),
    ]
    import importlib.util
    spec = importlib.util.spec_from_file_location("kyrie", os.path.join(os.path.dirname(os.path.abspath(__file__)), "kyrie.py"))
    kyrie = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(kyrie)
    data = kyrie.apply(data)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    for h in data["hours"]:
        per_day = {d: sum(1 for s in h["sections"] if s.get("weekday") in (None, d)) for d in DAYS}
        print(h["id"], len(h["sections"]), "sections;", "per day", min(per_day.values()), "-", max(per_day.values()))


if __name__ == "__main__":
    build()
