#!/usr/bin/env python3
"""Builds the chapter commentary shown behind the reader's Info button.

Source: tools/source/commentary/<BOOK>.md, one file per book, written by hand in English and Dutch:

    # MAT
    ## 1 | English title | Nederlandse titel
    S: Summary of the chapter (English).
    SN: Samenvatting (Nederlands).
    M: Why it matters.
    MN: Waarom het ertoe doet.
    F: How the Fathers read it.
    FN: Hoe de Vaders het lazen.
    T: theme; theme; theme
    TN: thema; thema; thema

Optional lines, which a chapter may add (both languages together, in any order within the chapter):
    SS: / SSL:   short summary, one or two sentences (EN / NL)      -> "shortSummary"
    LS: / LSL:   longer summary (EN / NL)                           -> "longSummary"
    P: / PN:     patterns, hidden names, typology, connections      -> "patterns"
    LE: / LEN:   liturgical use in the Eastern Orthodox Church (Byzantine rite)       -> "liturgy": {"eo": ...}
    LO: / LON:   liturgical use in the Oriental Orthodox Churches (Coptic, ...)       -> "liturgy": {"oo": ...}
    LC: / LCN:   liturgical use in the Catholic Church (Roman rite)                   -> "liturgy": {"ca": ...}
Chapters without these lines get no such keys in the JSON. The app shows only the liturgy of the reader's
chosen tradition.

Output: app/src/main/assets/content/commentary/<BOOK>.json. Chapters without written commentary keep the
provisional guide from the Bible database, which the app marks as provisional.

Run from the repository root:  python -I tools/build_commentary.py
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "tools", "source", "commentary")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "content", "commentary")
FIELDS = {"S": ("summary", "en"), "SN": ("summary", "nl"), "M": ("meaning", "en"), "MN": ("meaning", "nl"),
          "F": ("fathers", "en"), "FN": ("fathers", "nl"), "T": ("themes", "en"), "TN": ("themes", "nl"),
          "SS": ("shortSummary", "en"), "SSL": ("shortSummary", "nl"), "LS": ("longSummary", "en"),
          "LSL": ("longSummary", "nl"), "P": ("patterns", "en"), "PN": ("patterns", "nl")}
# Liturgy per tradition: key -> (tradition, language), emitted as "liturgy": {"eo"|"oo"|"ca": {"en", "nl"}}.
LITURGY = {"LE": ("eo", "en"), "LEN": ("eo", "nl"), "LO": ("oo", "en"), "LON": ("oo", "nl"),
           "LC": ("ca", "en"), "LCN": ("ca", "nl")}


def parse(path):
    book, chapters, current = None, [], None
    for n, raw in enumerate(open(path, encoding="utf-8"), 1):
        line = raw.strip()
        if not line:
            continue
        if line.startswith("# "):
            book = line[2:].strip()
        elif line.startswith("## "):
            num, en, nl = (p.strip() for p in line[3:].split("|"))
            current = {"chapter": int(num), "title": {"en": en, "nl": nl}, "summary": {}, "meaning": {}, "fathers": {}, "themes": {}}
            chapters.append(current)
        else:
            m = re.match(r"^([A-Z]{1,3}):\s*(.*)$", line)
            if not m or current is None or (m.group(1) not in FIELDS and m.group(1) not in LITURGY):
                sys.exit(f"{path}:{n}: cannot read line: {line[:60]}")
            if m.group(1) in LITURGY:
                tradition, lang = LITURGY[m.group(1)]
                value = m.group(2).strip()
                if value:
                    current.setdefault("liturgy", {}).setdefault(tradition, {})[lang] = value
                continue
            field, lang = FIELDS[m.group(1)]
            value = m.group(2).strip()
            current.setdefault(field, {})[lang] = [t.strip() for t in value.split(";") if t.strip()] if field == "themes" else value
    for c in chapters:
        for field in ("summary", "meaning", "fathers", "shortSummary", "longSummary", "patterns"):
            if set(c.get(field, {})) not in (set(), {"en", "nl"}):
                sys.exit(f"{path}: chapter {c['chapter']} has {field} in only one language")
        for tradition, text in c.get("liturgy", {}).items():
            if set(text) != {"en", "nl"}:
                sys.exit(f"{path}: chapter {c['chapter']} has {tradition} liturgy in only one language")
    return book, chapters


def main():
    os.makedirs(OUT, exist_ok=True)
    books = {}
    # A long book may be split over several files (MAT.md, MAT.2.md, ...); the "# BOOK" header decides.
    for path in sorted(glob.glob(os.path.join(SRC, "*.md"))):
        book, chapters = parse(path)
        books.setdefault(book, []).extend(chapters)
    total = 0
    for book, chapters in sorted(books.items()):
        chapters.sort(key=lambda c: c["chapter"])
        numbers = [c["chapter"] for c in chapters]
        if len(numbers) != len(set(numbers)):
            sys.exit(f"{book}: a chapter is written twice")
        with open(os.path.join(OUT, f"{book}.json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump({"book": book, "chapters": chapters}, f, ensure_ascii=False, indent=1)
        total += len(chapters)
        print(f"{book}: {len(chapters)} chapters ({numbers[0]}–{numbers[-1]})")
    print("total", total, "chapters with written commentary")


if __name__ == "__main__":
    main()
