#!/usr/bin/env python3
"""Extracts the creeds, definitions, anathemas and full canons of the seven Ecumenical Councils from the
Nicene and Post-Nicene Fathers, Second Series, vol. 14 (H. R. Percival, 1900; public domain) as published on
newadvent.org, into app/src/main/assets/content/council_texts.json.

Run from the repository root:  python tools/build_councils.py
"""
import html
import json
import os
import re
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, "tools", ".cache", "src", "councils")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "content", "council_texts.json")

# council id → (New Advent page, documents to keep: (match on the h2/h3 title, display title))
COUNCILS = {
    "nicaea-1": ("3801", [("The Nicene Creed", "The Creed of Nicaea"), ("The Synodal Letter", "Synodal letter to the Church of Alexandria")]),
    "constantinople-1": ("3808", [("Creed", "The Creed"), ("Letter of the Same Holy Synod", "Letter to Emperor Theodosius")]),
    "ephesus": ("3810", [("Cum salvator noster", "Third Letter of St Cyril to Nestorius"), ("The Twelve Anathemas", "The Twelve Anathemas of St Cyril")]),
    # (match, title, first paragraph starts with, stop before paragraph starting with)
    "chalcedon": ("3811", [("Session V", "The Definition of Faith", "The Definition of Faith of the Council", "After the reading")]),
    "constantinople-2": ("3812", [("The Sentence of the Synod", "The Sentence of the Synod"), ("The Capitula of the Council", "The Fourteen Anathemas")]),
    "constantinople-3": ("3813", [("The Definition of Faith", "The Definition of Faith")]),
    # NPNF's "The Definition" here is the iconoclast decree of 754 that the council refuted; "The Decree" is Nicaea II's own.
    "nicaea-2": ("3819", [("The Decree", "The Definition on the Holy Icons", "The holy, great, and Ecumenical Synod", "[After all had signed")]),
}
# NPNF prints editorial notes (Hefele, Bright, Van Espen…) inside some canons; the canon itself ends before them.
EDITORIAL = re.compile(r"\s(?:Hefele[.:]|Bright\.|According to Van Espen|It is supposed by Hefele|Here a new institution|Before the holding of the Council|From the Fourth Session|And Anatolius, the most reverend)")
# New Advent's Constantinople I page omits canon 3 and numbers the rest from 3; restore the NPNF numbering.
CANON_FIXES = {
    "constantinople-1": {
        "renumber": {3: 4, 4: 5, 5: 7},
        "insert": {3: "The Bishop of Constantinople, however, shall have the prerogative of honour after the Bishop of Rome; because Constantinople is New Rome."},
    },
}
NL_DIR = os.path.join(ROOT, "tools", "source", "council_texts_nl")
SKIP = re.compile(r"^(Please help support|About this page|Copyright|Contact information|Source\.)", re.I)


def page(nid):
    path = os.path.join(CACHE, f"{nid}.htm")
    if not os.path.exists(path):
        os.makedirs(CACHE, exist_ok=True)
        req = urllib.request.Request(f"https://www.newadvent.org/fathers/{nid}.htm", headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req) as r, open(path, "wb") as f:
            f.write(r.read())
    return open(path, encoding="utf-8", errors="ignore").read()


def clean(fragment):
    t = re.sub(r"<sup.*?</sup>", "", fragment, flags=re.S)
    t = html.unescape(re.sub(r"<[^>]+>", "", t))
    t = re.sub(r"\}\}-->|<!--.*?-->", "", t)
    return re.sub(r"\s+", " ", t).strip()


def blocks(nid):
    """Yields (h2, h3, paragraph) in document order."""
    body = re.search(r'<div id="springfield2">(.*?)<div class="pub">', page(nid), re.S).group(1)
    h2 = h3 = ""
    for tag in re.finditer(r"<(h2|h3|p|blockquote)[^>]*>(.*?)</\1>", body, re.S):
        name, inner = tag.groups()
        t = clean(inner)
        if not t or SKIP.match(t):
            continue
        if name == "h2":
            h2, h3 = t, ""
        elif name == "h3":
            h3 = t
        else:
            yield h2, h3, t


def build():
    out = {}
    for cid, (nid, wanted) in COUNCILS.items():
        all_blocks = list(blocks(nid))
        canons = {}
        for h2, h3, p in all_blocks:
            m = re.match(r"^Canon (\d+)$", h3)
            if m and ("Canon" in h2 or "Session VII" in h2 or "Session XV" in h2 or h2 == "The Canons" or "Canons of" in h2):
                canons.setdefault(int(m.group(1)), []).append(p)
        canons = {n: [EDITORIAL.split(" ".join(ps), maxsplit=1)[0].strip()] for n, ps in canons.items()}
        fix = CANON_FIXES.get(cid)
        if fix:
            canons = {fix["renumber"].get(n, n): ps for n, ps in canons.items()}
            canons.update({n: [t] for n, t in fix["insert"].items()})
        documents = []
        for match, title, *bounds in wanted:
            paras = [p for h2, h3, p in all_blocks if match in h2 or match in h3]
            if bounds:
                first, stop = bounds
                start = next((i for i, p in enumerate(paras) if p.startswith(first)), 0)
                end = next((i for i, p in enumerate(paras) if i > start and p.startswith(stop)), len(paras))
                paras = paras[start:end]
            if paras:
                documents.append({"title": title, "paragraphs": paras[:40]})
        # Dutch translations, where they exist (tools/source/council_texts_nl/<id>.json).
        nl_path = os.path.join(NL_DIR, f"{cid}.json")
        nl = json.load(open(nl_path, encoding="utf-8")) if os.path.exists(nl_path) else {"documents": [], "canons": {}}
        for i, doc in enumerate(documents):
            t = nl["documents"][i] if i < len(nl["documents"]) else None
            if t and len(t["paragraphs"]) == len(doc["paragraphs"]):
                doc["titleNl"], doc["paragraphsNl"] = t["title"], t["paragraphs"]
            elif t:
                print("  WARNING", cid, doc["title"], "paragraph count differs", len(t["paragraphs"]), len(doc["paragraphs"]))
        out[cid] = {
            "documents": documents,
            "canons": [{"number": n, "text": " ".join(ps), "textNl": nl["canons"].get(str(n), "")} for n, ps in sorted(canons.items())],
            "source": "NPNF II/14, The Seven Ecumenical Councils (H. R. Percival, 1900), via newadvent.org",
        }
        print(cid, "documents", [(d["title"], len(d["paragraphs"])) for d in documents], "canons", len(canons))
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print("wrote", os.path.relpath(OUT, ROOT), os.path.getsize(OUT) // 1024, "KB")


if __name__ == "__main__":
    build()
