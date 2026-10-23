#!/usr/bin/env python3
"""Downloads the bundled saint and feast icons from Wikimedia Commons.

Only images whose Commons licence is Public domain or CC0 are used (photographs of historic icons, mostly
Byzantine). Each icon is saved as a 360 px wide thumbnail with its title, artist, licence and source page in
app/src/main/assets/content/icons.json, which the app shows as credits and uses to match saints by name.

ICONS lists, per icon, the Commons searches to try and the patterns (matched against English saint and feast
names) that select it; the first pattern list that matches a saint wins, so specific entries come first.
`file` pins a Commons file when the search does not find a good one.

Run from the repository root (needs Pillow to recompress the thumbnails):  python tools/build_icons.py
Add --new to download only the icons that icons.json does not have yet (the others are kept as they are), or
--patterns to rewrite only the match patterns.
"""
import json
import os
import re
import time
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "icons")
OUT_JSON = os.path.join(ROOT, "app", "src", "main", "assets", "content", "icons.json")
UA = "AGAPE-app-build/1.0 (https://github.com/gabriel360110l/AGAPE-Android3)"
FREE = {"public domain", "cc0", "pd"}
WIDTH = 360

BYZ = 'deepcat:"Byzantine icons"'

# key, searches, match patterns (case-insensitive), optional pinned file
ICONS = [
    # Feasts
    ("nativity", [f"{BYZ} Nativity of Christ", "Nativity of Christ icon"], [r"Nativity of (Christ|the Lord|our Lord)", r"\bChristmas\b", r"Birth of (Christ|our Lord)"], None),
    ("theophany", [f"{BYZ} Baptism of Christ", "Theophany icon"], [r"Theophany", r"Epiphany", r"Baptism of (Christ|the Lord|our Lord)"], None),
    ("annunciation", [f"{BYZ} Annunciation", "Annunciation icon Ohrid"], [r"Annunciation"], None),
    ("transfiguration", [f"{BYZ} Transfiguration", "Transfiguration icon Sinai"], [r"Transfiguration"], None),
    ("presentation", [f"{BYZ} Presentation of Jesus at the Temple", "Hypapante icon"], [r"Meeting of (Christ|the Lord|our Lord)", r"Presentation of (Christ|the Lord|our Lord)", r"Entrance of (Christ|the Lord|our Lord) into the Temple", r"Candlemas"], None),
    ("dormition", [f"{BYZ} Dormition", "Dormition of the Theotokos icon"], [r"Dormition of (the )?(Most Holy )?(Theotokos|Virgin|Mother of God|Lady)", r"(Forefeast|leave-taking) of the Dormition", r"Assumption", r"Departure of (the )?(St\.? )?(Mary|Virgin|Theotokos|Lady)"], None),
    ("nativity_theotokos", [f"{BYZ} Nativity of the Theotokos", "Nativity of the Virgin icon"], [r"Nativity of (the )?(Most Holy |Blessed )?(Theotokos|Virgin|Mary|Lady)"], None),
    ("entry_theotokos", [f"{BYZ} Presentation of Mary", "Entry of the Theotokos into the Temple icon"], [r"Entr(y|ance) of (the )?(Most Holy )?(Theotokos|Virgin|Lady)", r"Presentation of (the )?(Blessed )?(Virgin|Mary|Theotokos)"], None),
    ("cross", [f"{BYZ} Exaltation of the Cross", "Exaltation of the Holy Cross icon"], [r"\bCross\b"], None),
    ("archangels", [f"{BYZ} Archangel Michael", "Archangel Michael icon"], [r"Michael", r"Micah[eë]l", r"Archangels?", r"Holy Angels", r"Bodiless Powers"], None),
    ("gabriel", [f"{BYZ} Archangel Gabriel", "Archangel Gabriel icon"], [r"Gabriel"], None),
    # Saints
    ("john_baptist", [f"{BYZ} John the Baptist", "John the Forerunner icon"], [r"John the Baptist", r"Forerunner", r"\bBaptist\b"], None),
    ("peter_paul", [f"{BYZ} Peter and Paul", "Saints Peter and Paul icon"], [r"Peter and Paul", r"Peter & Paul"], None),
    ("peter", [f"{BYZ} Saint Peter", "Apostle Peter icon"], [r"Apostle Peter", r"(St\.?|Saint) Peter\b(?! of)", r"Chair of (St\.? |Saint )?Peter", r"Peter's Chains", r"Chains of (St\.? |the Apostle )?Peter"], None),
    ("paul", [f"{BYZ} Saint Paul", "Apostle Paul icon"], [r"Apostle Paul", r"(St\.?|Saint) Paul\b(?! of| the)", r"Conversion of .*Paul"], None),
    ("andrew", [f"{BYZ} Saint Andrew", "Apostle Andrew icon"], [r"Andrew the First-called", r"Apostle Andrew", r"Andrew,? (the )?Apostle", r"(St\.?|Saint) Andrew\b(?! of)"], None),
    ("john_theologian", [f"{BYZ} John the Theologian", "John the Evangelist icon"], [r"John the (Theologian|Evangelist|Apostle|Divine)", r"Apostle (and Evangelist )?John", r"John,? Apostle"], None),
    ("james", [f"{BYZ} Apostle James", "Saint James the Great icon"], [r"Apostle James", r"James,? (the )?(Apostle|son of|brother of)", r"James the (Great|Greater|Less|Just)"], None),
    ("thomas", [f"{BYZ} Apostle Thomas", "Saint Thomas apostle icon"], [r"Apostle Thomas", r"Thomas,? (the )?Apostle"], None),
    ("matthew", [f"{BYZ} Matthew", "Saint Matthew evangelist icon"], [r"\bMatthew\b"], None),
    ("mark", [f"{BYZ} Mark the Evangelist", "Saint Mark evangelist icon"], [r"\bMark\b.*(Apostle|Evangelist)", r"Apostle Mark", r"(St\.?|Saint) Mark\b(?! of)"], None),
    ("luke", [f"{BYZ} Luke the Evangelist", "Saint Luke evangelist icon"], [r"\bLuke\b"], None),
    ("philip", [f"{BYZ} Apostle Philip", "Saint Philip apostle icon"], [r"Apostle Philip", r"Philip,? (the )?Apostle", r"Philip and James"], None),
    ("bartholomew", [f"{BYZ} Bartholomew", "Saint Bartholomew apostle icon"], [r"Bartholomew"], None),
    ("stephen", [f"{BYZ} Saint Stephen", "Saint Stephen protomartyr icon"], [r"Protomartyr", r"Stephen,? (the )?(First Martyr|Archdeacon|Deacon)", r"(St\.?|Saint) Stephen\b(?! of)", r"Translation of (the relics of )?(St\.? )?Stephen"], None),
    ("george", [f"{BYZ} Saint George", "Saint George icon"], [r"George the (Trophy-bearer|Great|Victorious)", r"Great[- ]?martyr George", r"(St\.?|Saint) George\b(?! of)", r"^George$"], None),
    ("demetrius", [f"{BYZ} Demetrius", "Saint Demetrius icon"], [r"Demetri"], None),
    ("theodore", [f"{BYZ} Theodore Stratelates", "Saint Theodore icon"], [r"Theodore (the )?(Stratelates|Tyro|Tiro|Recruit|General|Oriental|Anatoli)"], None),
    ("nicholas", [f"{BYZ} Nicholas", "Saint Nicholas icon"], [r"Nicholas"], None),
    ("basil", [f"{BYZ} Basil the Great", "Basil of Caesarea icon"], [r"Basil (the Great|of Caesarea)", r"(St\.?|Saint) Basil\b(?! of)", r"^Basil$"], None),
    ("three_hierarchs", [f"{BYZ} Three Hierarchs", "Three Holy Hierarchs icon"], [r"Three (Holy )?Hierarchs"], None),
    ("gregory_theologian", [f"{BYZ} Gregory the Theologian", "Gregory of Nazianzus icon"], [r"Gregory (the Theologian|of Nazianz|Nazianzen)"], None),
    ("chrysostom", [f"{BYZ} John Chrysostom", "John Chrysostom icon"], [r"Chrysostom"], None),
    ("athanasius", [f"{BYZ} Athanasius", "Athanasius of Alexandria icon"], [r"Athanasius (the Great|of Alexandria|the Apostolic)", r"Athanasius and Cyril", r"(St\.?|Saint) Athanasius\b(?! of Athos)"], None),
    ("cyril", [f"{BYZ} Cyril of Alexandria", "Cyril of Alexandria icon"], [r"Cyril (of Alexandria|the Great|I\b)", r"Cyril,? (the )?(24th )?Pope"], None),
    ("anthony", [f"{BYZ} Anthony the Great", "Anthony the Great icon"], [r"Ant(h)?ony (the Great|of Egypt)", r"(St\.?|Saint|Abba|Abba) Ant(h)?ony\b(?! of Padua)"], None),
    ("mary_egypt", [f"{BYZ} Mary of Egypt", "Mary of Egypt icon"], [r"Mary of Egypt"], None),
    ("mary_magdalene", [f"{BYZ} Mary Magdalene", "Mary Magdalene icon myrrhbearer"], [r"Magdalen"], None),
    ("constantine_helen", [f"{BYZ} Constantine and Helena", "Constantine and Helen icon"], [r"Constantine and Hel", r"\bHelen(a)?\b"], None),
    ("catherine", [f"{BYZ} Catherine of Alexandria", "Catherine of Alexandria icon Sinai"], [r"Catherine(?! of Siena)", r"Katherine"], None),
    ("panteleimon", [f"{BYZ} Panteleimon", "Saint Panteleimon icon"], [r"Panteleimon", r"Pantaleon"], None),
    ("spyridon", [f"{BYZ} Spyridon", "Saint Spyridon icon"], [r"Spyridon", r"Spiridon"], None),
    ("elijah", [f"{BYZ} Elijah", "Prophet Elijah icon"], [r"Elijah", r"Prophet Elias"], None),
    ("moses", [f"{BYZ} Moses", "Prophet Moses icon"], [r"Prophet Moses", r"Moses the (Prophet|God-seer|Lawgiver)"], None),
    ("joachim_anna", [f"{BYZ} Joachim and Anna", "Joachim and Anna icon"], [r"Joachim", r"\bAnne\b", r"Righteous Anna\b", r"Dormition of (St\.? |Saint )?Ann[ae]"], None),
    ("ephrem", ["Ephrem the Syrian icon", f"{BYZ} Ephrem"], [r"Ephr(e|ai)m"], None),
    ("menas", ["Saint Menas icon", f"{BYZ} Menas"], [r"\bMena(s)?\b"], None),
    ("mercurius", ["Saint Mercurius icon", f"{BYZ} Mercurius"], [r"Mercurius", r"Philopater", r"Abu Sefein"], None),
    ("macarius", ["Macarius the Great icon"], [r"Macarius the (Great|Egyptian|Elder)", r"Body of St\. Macarius to His Monastery"], None),
    ("ignatius", ["Ignatius of Antioch icon"], [r"Ignatius (of Antioch|the God-bearer|Theophorus)", r"Ignatius,? (Bishop|Patriarch) of Antioch"], None),
    ("polycarp", ["Polycarp icon"], [r"Polycarp"], None),
    ("maximus", ["Maximus the Confessor icon"], [r"Maximus the Confessor"], None),
    ("sergius_bacchus", ["Sergius and Bacchus icon"], [r"Sergius and (St\.? )?Bacchus", r"\bBacchus\b"], None),
    ("sergius_radonezh", ["Sergius of Radonezh icon"], [r"Sergius of Radonezh"], None),
    ("seraphim", ["Seraphim of Sarov icon"], [r"Seraphim of Sarov"], None),
    ("simeon_stylite", ["Simeon the Stylite icon"], [r"Sim(e)?on( \(Simeon\))? the (Elder )?Stylite", r"Simeon Stylites"], None),
    ("thecla", ["Thecla icon"], [r"(St\.?|Saint) Thecla\b(?! and)", r"Thecla,? equal to the apostles", r"^Thecla$"], None),
    ("euphemia", ["Euphemia icon"], [r"Euphemia"], None),
    ("marina", ["Saint Marina icon"], [r"\bMarina\b"], None),
    ("paraskevi", ["Paraskevi icon"], [r"Paraskev[ai] of Rome", r"^Paraskev[ai]$"], None),
    ("barbara", ["Barbara icon"], [r"\bBarbara\b"], None),
    ("pachomius", ["Pachomius icon"], [r"(?<!disciple of St\. )Pachomius"], None),
    ("cosmas_damian", ["Cosmas and Damian icon"], [r"Cosmas and (St\.? |Saint )?Damian", r"Cosmas, Damian"], None),
    ("john_damascus", ["John of Damascus icon"], [r"John (of Damascus|Damascene)"], None),
    ("moses_black", ["Moses the Black icon"], [r"Moses the (Black|Ethiopian|Strong)"], None),
    ("sabbas", ["Sabbas the Sanctified icon"], [r"Sabbas the Sanctified", r"Lavra of Sabbas", r"(St\.?|Saint) Sabbas\b(?! the Stratelates)"], None),
    ("cyril_methodius", ["Cyril and Methodius icon"], [r"Cyril and (St\.? )?Methodius", r"Cyril, teacher of the Slavs", r"Cyril, monk, and Methodius"], None),
    ("boris_gleb", ["Boris and Gleb icon"], [r"Boris and Gleb"], None),
    ("climacus", ["John Climacus icon"], [r"Climacus"], None),
    ("isaiah", ["Prophet Isaiah icon"], [r"Prophet Isaiah", r"Isaiah,? the Prophet"], None),
    # Added in v3/icons-more: saints of the three calendars that had no icon yet. Searches only, so the first
    # free portrait result is used; files that turned out wrong are pinned in PINS.
    ("gregory_nyssa", ["Gregory of Nyssa icon", f"{BYZ} Gregory of Nyssa"], [r"Gregory,? (of|Bishop of) Nyssa"], None),
    ("paul_thebes", ["Paul of Thebes icon", "Paul the First Hermit icon"], [r"Paul of Thebes", r"Paul,? the First Hermit", r"Anba Paul,? the First Hermit"], None),
    ("euthymius", ["Euthymius the Great icon"], [r"Euthymius the Great"], None),
    ("timothy", ["Apostle Timothy icon", f"{BYZ} Timothy apostle"], [r"Apostle Timothy", r"Timothy,? the Apostle", r"Timothy and Titus"], None),
    ("tryphon", ["Saint Tryphon icon"], [r"Tryphon"], None),
    ("agatha", ["Saint Agatha icon", "Agatha of Sicily icon"], [r"\bAgatha\b"], None),
    ("haralambos", ["Saint Charalampos icon", "Haralambos icon"], [r"Haralamb", r"Charalamp"], None),
    ("blaise", ["Blaise of Sebaste icon", "Saint Blaise icon"], [r"Blas(ius|e) of Sebaste", r"(St\.?|Saint) Blase\b"], None),
    ("leo_great", ["Pope Leo the Great icon", "Leo I icon"], [r"Leo (of Rome|the Great)"], None),
    ("tarasius", ["Tarasius of Constantinople icon"], [r"Tarasius"], None),
    ("porphyrius_gaza", ["Porphyry of Gaza icon"], [r"Porphyrius of Gaza"], None),
    ("john_cassian", ["John Cassian icon"], [r"John Cassian"], None),
    ("gerasimus", ["Gerasimus of the Jordan icon"], [r"Gerasimus of the Jordan"], None),
    ("forty_martyrs", ["Forty Martyrs of Sebaste icon", f"{BYZ} Forty Martyrs"], [r"Forty Martyrs of Sebaste"], None),
    ("sophronius", ["Sophronius of Jerusalem icon"], [r"Sophronius of Jerusalem"], None),
    ("gregory_dialogos", ["Gregory the Great icon", "Gregory Dialogos icon"], [r"Gregory (Dialogos|the Great|the Dialogist)"], None),
    ("benedict", ["Benedict of Nursia icon", "Saint Benedict medieval"], [r"Benedict of Nursia", r"(St\.?|Saint) Benedict,? abbot"], None),
    ("alexius", ["Alexius man of God icon"], [r"Alexius,? the man of God", r"Alexis,? the man of God"], None),
    ("cyril_jerusalem", ["Cyril of Jerusalem icon"], [r"Cyril of Jerusalem"], None),
    ("antipas", ["Antipas of Pergamum icon"], [r"Antipas"], None),
    ("jeremiah", ["Prophet Jeremiah icon", "Prophet Jeremiah Kizhi"], [r"Prophet Jeremiah", r"Jeremiah,? the Prophet"], None),
    ("job", ["Job the Long-suffering icon", "Righteous Job icon"], [r"Job the Long-suffering", r"Righteous Job\b"], None),
    ("simon_zealot", ["Simon the Zealot icon", "Apostle Simon icon"], [r"Simon the (Zealot|Canaanite)", r"Simon and (Saint )?Jude"], None),
    ("jude", ["Apostle Jude icon", "Jude Thaddeus icon"], [r"Apostle Jude", r"Jude,? (the )?(Apostle|brother of)"], None),
    ("epiphanius", ["Epiphanius of Salamis icon", "Epiphanius of Cyprus icon"], [r"Epiphanius of (Cyprus|Salamis)"], None),
    ("justin", ["Justin Martyr icon"], [r"Justin (the Philosopher|Martyr)"], None),
    ("onuphrius", ["Onuphrius icon", f"{BYZ} Onuphrius"], [r"Onuphrius", r"Abu-Nofer"], None),
    ("elisha", ["Prophet Elisha icon"], [r"Prophet Elisha", r"Elisha,? the Prophet"], None),
    ("amos", ["Prophet Amos icon"], [r"Prophet Amos"], None),
    ("andrew_crete", ["Andrew of Crete icon"], [r"Andrew of Crete"], None),
    ("athanasius_athos", ["Athanasius the Athonite icon"], [r"Athanasius (of Athos|the Athonite)"], None),
    ("sisoes", ["Sisoes the Great icon", "Saint Sisoes icon"], [r"Sisoes"], None),
    ("procopius", ["Saint Procopius icon", "Procopius of Scythopolis icon"], [r"Procopius of Caesarea", r"Great[- ]?martyr Procopius"], None),
    ("vladimir", ["Saint Vladimir icon", "Vladimir the Great icon"], [r"Vladimir,? equal to the apostles", r"Vladimir the Great"], None),
    ("seven_sleepers", ["Seven Sleepers of Ephesus icon"], [r"Seven Sleepers"], None),
    ("matthias", ["Apostle Matthias icon"], [r"Matthias"], None),
    ("lawrence", ["Saint Lawrence icon", "Lawrence of Rome icon"], [r"Laurentius of Rome", r"(St\.?|Saint) Lawrence,? deacon", r"Lawrence of Rome"], None),
    ("mandylion", ["Mandylion icon", "Image of Edessa icon"], [r"Image Not Made by Hands", r"Mandylion"], None),
    ("samuel", ["Prophet Samuel icon"], [r"Prophet Samuel\b"], None),
    ("poemen", ["Poemen the Great icon", "Abba Poemen icon"], [r"Poemen", r"Poemin"], None),
    ("mamas", ["Saint Mamas icon"], [r"Mamas of Caesarea", r"(St\.?|Saint) Mamas\b"], None),
    ("zechariah", ["Prophet Zechariah icon"], [r"Prophet Zechariah"], None),
    ("sophia_daughters", ["Sophia Faith Hope Love icon", "Saints Faith Hope Charity Sophia icon"], [r"Sophia,? Faith,? Hope", r"Pistis,? Helpis,? Agape"], None),
    ("gregory_illuminator", ["Gregory the Illuminator icon"], [r"Gregory the (Enlightener|Illuminator)", r"Gregory,? Patriarch of the Armenians"], None),
    ("cyprian_justina", ["Cyprian and Justina icon"], [r"Cyprian(us)? and Justina"], None),
    ("dionysius_areopagite", ["Dionysius the Areopagite icon"], [r"Dionysius the Areopagite"], None),
    ("longinus", ["Longinus the Centurion icon"], [r"Longinus,? the (Centurion|Soldier)", r"Head of St\.? Longinus"], None),
    ("hosea", ["Prophet Hosea icon"], [r"Prophet Hosea"], None),
    ("joel", ["Prophet Joel icon"], [r"Prophet Joel"], None),
    ("obadiah", ["Prophet Obadiah icon"], [r"Prophet Obadiah", r"Obadiah,? the Prophet"], None),
    ("jonah", ["Prophet Jonah icon"], [r"Prophet Jonah", r"Jonah,? the (Great )?Prophet"], None),
    ("nahum", ["Prophet Nahum icon"], [r"Prophet Nahum", r"Nahum,? the Prophet"], None),
    ("habakkuk", ["Prophet Habakkuk icon"], [r"Prophet Habakkuk"], None),
    ("zephaniah", ["Prophet Zephaniah icon"], [r"Prophet Zephaniah"], None),
    ("haggai", ["Prophet Haggai icon"], [r"Prophet Haggai", r"Haggai,? the Prophet"], None),
    ("malachi", ["Prophet Malachi icon"], [r"Prophet Malachi"], None),
    ("daniel", ["Prophet Daniel icon", "Three Holy Youths icon"], [r"Prophet Daniel", r"Three (Holy )?Youths"], None),
    ("david", ["King David icon", "Prophet David icon"], [r"David,? the Prophet and King", r"Prophet(-| )King David", r"King David\b"], None),
    ("john_rila", ["John of Rila icon"], [r"John of Rila"], None),
    ("artemius", ["Saint Artemius icon"], [r"Artemius of Antioch", r"Great[- ]?martyr Artemius"], None),
    ("hilarion", ["Hilarion the Great icon"], [r"Hilarion the Great", r"Abba Hilarion"], None),
    ("nestor", ["Nestor of Thessalonica icon"], [r"Nestor of Thessalonica"], None),
    ("nectarios", ["Nectarios of Aegina icon"], [r"Nectarios of Aegina"], None),
    ("john_merciful", ["John the Merciful icon"], [r"John the Merciful", r"John the Almsgiver"], None),
    ("gregory_wonderworker", ["Gregory Thaumaturgus icon"], [r"Gregory the (Wonderworker|Thaumaturg)", r"Gregory Thaumaturgus"], None),
    ("clement_rome", ["Clement of Rome icon", "Pope Clement I icon"], [r"Clement (of Rome|I\b)", r"Clemens of Rome"], None),
    ("peter_alexandria", ["Peter of Alexandria icon"], [r"Peter of Alexandria", r"Peter,? (the )?Seal of the Martyrs"], None),
    ("ambrose", ["Ambrose of Milan icon", "Saint Ambrose mosaic"], [r"Ambrose(,? (of Milan|bishop))?\b"], None),
    ("sebastian", ["Saint Sebastian icon", "Saint Sebastian mosaic"], [r"Sebastian(,? martyr| of Rome)"], None),
    ("juliana", ["Juliana of Nicomedia icon"], [r"Juliana (of Nicomedia|the Martyr)"], None),
    ("anastasia", ["Anastasia Pharmakolytria icon", "Saint Anastasia icon"], [r"Anastasia,? (deliverer|of Rome|the Great)"], None),
    ("holy_innocents", ["Massacre of the Innocents icon", "Holy Innocents icon"], [r"Holy Innocents", r"children of Bethlehem", r"Slain Children of Bethlehem"], None),
    ("xenia", ["Xenia of Saint Petersburg icon"], [r"Xenia of (St\.? |Saint )?Petersburg"], None),
    ("tatiana", ["Tatiana of Rome icon"], [r"Tatiana"], None),
    ("sylvester", ["Pope Sylvester I icon", "Saint Sylvester fresco"], [r"Sylvester"], None),
    ("irenaeus", ["Irenaeus of Lyon icon"], [r"Irenaeus"], None),
    ("joseph", ["Saint Joseph icon", "Joseph the Betrothed icon"], [r"Joseph the (Worker|Betrothed)", r"(St\.?|Saint) Joseph,? (Spouse|husband)", r"^Saint Joseph$"], None),
    ("thomas_aquinas", ["Thomas Aquinas painting medieval", "Thomas Aquinas Crivelli"], [r"Aquinas"], None),
    ("francis_assisi", ["Francis of Assisi Cimabue", "Francis of Assisi Bonaventura Berlinghieri"], [r"Francis of Assisi"], None),
    ("dominic", ["Saint Dominic Fra Angelico", "Saint Dominic painting"], [r"(St\.?|Saint) Dominic,? priest", r"Dominic de Guzm"], None),
    ("clare", ["Clare of Assisi painting", "Saint Clare Simone Martini"], [r"(St\.?|Saint) Clare,? virgin", r"Clare of Assisi"], None),
    ("augustine", ["Augustine of Hippo painting", "Saint Augustine fresco Lateran"], [r"Augustine of Hippo", r"Augustine,? bishop and doctor"], None),
    ("jerome", ["Saint Jerome icon", "Saint Jerome painting medieval"], [r"Jerome(?! Emiliani)"], None),
    ("scholastica", ["Saint Scholastica painting"], [r"Scholastica"], None),
    ("agnes", ["Saint Agnes mosaic", "Saint Agnes painting"], [r"\bAgnes\b"], None),
    ("cecilia", ["Saint Cecilia painting", "Saint Cecilia mosaic"], [r"Cecilia"], None),
    ("lucy", ["Saint Lucy painting", "Lucy of Syracuse"], [r"\bLucy\b"], None),
    ("anthony_padua", ["Anthony of Padua painting"], [r"Anthony of Padua"], None),
    ("bernard", ["Bernard of Clairvaux painting"], [r"Bernard of Clairvaux"], None),
    ("catherine_siena", ["Catherine of Siena painting"], [r"Catherine of Siena"], None),
    ("martin_tours", ["Martin of Tours icon", "Saint Martin of Tours painting"], [r"Martin(us)?( \(Martin\))?,? (of|Bishop of) Tours"], None),
    ("patrick", ["Saint Patrick icon", "Saint Patrick stained glass"], [r"Patrick,? bishop", r"Patrick of Ireland", r"Patrick, Enlightener"], None),
    ("teresa_avila", ["Teresa of Avila painting"], [r"Teresa of (Jesus|Avila)"], None),
    ("ignatius_loyola", ["Ignatius of Loyola painting"], [r"Ignatius of Loyola"], None),
    ("francis_xavier", ["Francis Xavier painting"], [r"Francis Xavier"], None),
    ("barnabas", ["Apostle Barnabas icon"], [r"Barnabas"], None),
    ("martha", ["Saint Martha painting", "Martha of Bethany icon"], [r"(St\.?|Saint) Martha\b(?! of Egypt)", r"Martha (of Bethany|and Mary)"], None),
    ("monica", ["Saint Monica painting"], [r"\bMonica\b"], None),
    ("all_saints", ["All Saints icon", f"{BYZ} All Saints"], [r"^All Saints", r"Sunday of All Saints"], None),
    # After the single apostles, so "Peter and Paul" keeps its own icon.
    ("apostles", [f"{BYZ} Twelve Apostles", "Synaxis of the Twelve Apostles icon"], [r"Twelve Apostles", r"Holy Apostles", r"Seventy Apostles"], None),
    # The Mother of God and Christ last: they match many feast titles that have their own icons above.
    ("theotokos", [f"{BYZ} Hodegetria", "Theotokos Hodegetria icon"], [r"Theotokos", r"Virgin Mary", r"Blessed Virgin", r"Our Lady", r"Mother of God", r"(St\.?|Saint) Mary\b(?! of| Magdal)", r"Holy Virgin"], None),
    ("pantocrator", [f"{BYZ} Pantocrator Sinai", "Christ Pantocrator Sinai"], [r"\b(Christ|Saviour|Savior)\b", r"\bthe Lord\b", r"Holy Name"], None),
]

# Files chosen by hand where the search's first free result was a building, a book scan or the wrong saint.
PINS = {
    "annunciation": "File:Annunciation ystuj.jpg",
    "theophany": "File:Epiphany last XIII early XIVth c.jpg",
    "presentation": "File:Sretenie (Russian museum, 15 c).jpg",
    "nativity_theotokos": "File:Nikola-Obrazopisov-Belyova-church-Nativity-of-the-Theotokos-icon-1863.jpg",
    "entry_theotokos": "File:Presentation of Mary, II Half of XIV Century, St Mary Perivleptos Church, Ohrid Icon Gallery.jpg",
    "cross": "File:Exaltation of the Cross - Palekh icon (19 c, priv.coll).jpg",
    "gabriel": "File:Annunciation Gabriel, Early XIΙ Century, St Mary Perivleptos Church, Ohrid Icon Gallery.jpg",
    "stephen": "File:Stephan (Lebanon).png",
    "peter_paul": "File:Peter and Paul icon Belozersk.jpg",
    "paul": "File:Apostle Paul (Ubisi icon).jpg",
    "andrew": "File:Fyodor Zubov.Andrew the Apostle.1669.jpg",
    "john_theologian": "File:Holy Apostle and Evangelist John the Theologian. Icon. Greece, 16th century.jpg",
    "james": "File:Apostle James, son of Alphaeus.jpeg",
    "thomas": "File:Saint Thomas K. Tzanes.png",
    "mark": "File:087 Mark the Evangelist Icon from Saint Paraskevi Church in Langadas.jpg",
    "luke": "File:Saint Luke the Evangelist - icon.jpeg",
    "george": "File:Novgorod George.jpg",
    "basil": "File:Semen Spiridonov Kholmogorets. Basil the Great. 1674.jpg",
    "gregory_theologian": "File:Gregory of Nazianzus (1420s, Sergiev Posad).jpg",
    "chrysostom": "File:John Chrysostom (1420s, Sergiev Posad).jpg",
    "athanasius": "File:Saint-Athanasius-of-Alexandria-icon-Sozopol-Bulgaria-17century.jpg",
    "cyril": "File:Fresco Icon of Saint Kyrillos of Alexandria 3.jpg",
    "anthony": "File:Cretan Icon Saint Anthony the Great.jpg",
    "mary_egypt": "File:Icon of Mary of Egypt (Mstera, 19th c.).jpg",
    "mary_magdalene": "File:Mary Magdalene by Constantin Tzanes (17th c.).jpg",
    "constantine_helen": "File:Saint Constantin and Helena, mother of Constantine I icon, Syria (14th Century).jpg",
    "panteleimon": "File:Byzantine - Saint Panteleimon - Walters 4820864.jpg",
    "spyridon": "File:Saint Spyridon Icon from Gorno Statitsa 1720.jpg",
    "moses": "File:Moses & Bush Icon Sinai c12th century.jpg",
    "ephrem": "File:Mor Ephrem icon.jpg",
    "menas": "File:Kristus a svatý Menas.jpg",
    "mercurius": "File:Byzantine icon St-Mercurius 1295.jpg",
    "macarius": "File:Macarius the Elder Crop.jpg",
    "ignatius": "File:Byzantine - Saint Ignatius of Antioch - Walters 4820867.jpg",
    "polycarp": "File:Polycarp.jpg",
    "maximus": "File:Athonite Fresco Icon of Saint Maximos the Confessor.jpg",
    "sergius_bacchus": "File:ܡܪܝ ܣܪܓܝܣ ܘܡܪܝ ܒܟܘܣ.jpg",
    "sergius_radonezh": "File:Sergius of Radonezh begin of 16c SPMZ.jpg",
    "seraphim": "File:Seraphim of Sarov (after 1903, priv.coll).jpg",
    "simeon_stylite": "File:Simeon Stylites icon 1465.jpg",
    "thecla": "File:Saint Thecla, Lebanon (14 Century).jpg",
    "euphemia": "File:St Euphemia.jpg",
    "marina": "File:Cretan Icon of Saint Marina in Thiseio.jpg",
    "paraskevi": "File:002 Saint Paraskevi Icon from Saint Paraskevi Church in Langadas.jpg",
    "barbara": "File:Saint Barbara (ca. 1680-1690).jpg",
    "pachomius": "File:StPakhom.jpg",
    "cosmas_damian": "File:Saints Cosmas and Damian icon, Syria (1778).jpg",
    "john_damascus": "File:Icon of Saint John of Damascus - Emmanouel Tzanes.jpg",
    "moses_black": "File:Saints Poimen, Elijah, and Moses the Black.jpg",
    "sabbas": "File:SabbastheSanctified.jpg",
    "cyril_methodius": "File:Sts Cyril and Methodius Icon in Tresonche.png",
    "boris_gleb": "File:BorisGleb.jpg",
    "climacus": "File:John Climacus.jpg",
    "isaiah": "File:Isaiah Prophet.jpg",
}

FALLBACK = {"theotokos", "pantocrator"}

BAD_TITLE = re.compile(r"church|exterior|interior|building|iconostas|monastery view|map|coin|seal|stamp|\.tif$|\.svg$", re.I)


def api(**params):
    params.setdefault("format", "json")
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(3):
        try:
            return json.load(urllib.request.urlopen(req, timeout=40))
        except Exception:
            time.sleep(3 * (attempt + 1))
    raise RuntimeError(url)


def clean(html):
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", html or "")).strip()


def info(pages):
    for p in sorted(pages, key=lambda p: p.get("index", 0)):
        ii = (p.get("imageinfo") or [{}])[0]
        meta = ii.get("extmetadata", {})
        licence = clean(meta.get("LicenseShortName", {}).get("value", ""))
        yield p["title"], ii, meta, licence


def candidates(searches, pinned):
    if pinned:
        d = api(action="query", titles=pinned, prop="imageinfo", iiprop="url|extmetadata|size", iiurlwidth=WIDTH)
        yield from info(d.get("query", {}).get("pages", {}).values())
        return
    for q in searches:
        d = api(action="query", generator="search", gsrnamespace=6, gsrlimit=20, gsrsearch=f"{q} filetype:bitmap",
                prop="imageinfo", iiprop="url|extmetadata|size", iiurlwidth=WIDTH)
        yield from info((d.get("query", {}).get("pages", {}) or {}).values())


def pick(searches, pinned):
    for title, ii, meta, licence in candidates(searches, pinned):
        if licence.lower() not in FREE and not licence.lower().startswith("public domain"):
            continue
        w, h = ii.get("width", 0), ii.get("height", 0)
        if not pinned and (BAD_TITLE.search(title) or h < w * 0.95 or w < 300):
            continue  # want a portrait icon, not a building or a wide scene
        return title, ii, meta, licence
    return None


def main(only_new=False):
    """Downloads every icon, or with only_new just the keys that icons.json does not have yet."""
    os.makedirs(OUT_DIR, exist_ok=True)
    current = {i["key"]: i for i in json.load(open(OUT_JSON, encoding="utf-8"))} if only_new else {}
    out, fresh = [], []
    for key, searches, patterns, pinned in ICONS:
        if key in current:
            out.append({**current[key], "match": patterns, "fallback": key in FALLBACK})
            continue
        chosen = pick(searches, PINS.get(key, pinned))
        if not chosen:
            print("  no free icon for", key)
            continue
        title, ii, meta, licence = chosen
        file = f"{key}.jpg"
        req = urllib.request.Request(ii["thumburl"], headers={"User-Agent": UA})
        with open(os.path.join(OUT_DIR, file), "wb") as f:
            f.write(urllib.request.urlopen(req, timeout=60).read())
        fresh.append(file)
        out.append({
            "key": key,
            "file": f"icons/{file}",
            "match": patterns,
            "title": title.removeprefix("File:").rsplit(".", 1)[0],
            "artist": clean(meta.get("Artist", {}).get("value", ""))[:120],
            "licence": licence,
            "source": ii.get("descriptionurl", ""),
            # Used only when no other name of the day has its own icon (e.g. Basil before "the Lord" on 1 January).
            "fallback": key in FALLBACK,
        })
        print(f"  {key:20s} {licence:14s} {title[:70]}")
        time.sleep(0.3)
    with open(OUT_JSON, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print("wrote", len(out), "icons,", len(fresh), "downloaded")
    compress(files=fresh if only_new else None)


def compress(width=320, files=None):
    """Commons thumbnails can be PNG or very tall; store them as small JPEGs cropped to the upper part (the face)."""
    from PIL import Image  # noqa: PLC0415 (only needed here)
    for f in os.listdir(OUT_DIR) if files is None else files:
        path = os.path.join(OUT_DIR, f)
        im = Image.open(path).convert("RGB")
        if im.width != width:
            im = im.resize((width, round(im.height * width / im.width)), Image.LANCZOS)
        if im.height > width * 1.7:
            im = im.crop((0, 0, width, round(width * 1.7)))
        im.save(path, "JPEG", quality=80, optimize=True, progressive=True)
    print("compressed to", sum(os.path.getsize(os.path.join(OUT_DIR, f)) for f in os.listdir(OUT_DIR)) // 1024, "KB")


def update_patterns():
    """Rewrites only the match patterns, order and fallback flags in icons.json, without downloading again."""
    current = {i["key"]: i for i in json.load(open(OUT_JSON, encoding="utf-8"))}
    out = []
    for key, _, patterns, _ in ICONS:
        if key in current:
            out.append({**current[key], "match": patterns, "fallback": key in FALLBACK})
    with open(OUT_JSON, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print("updated patterns of", len(out), "icons")


if __name__ == "__main__":
    import sys
    if "--patterns" in sys.argv:
        update_patterns()
    else:
        main(only_new="--new" in sys.argv)
