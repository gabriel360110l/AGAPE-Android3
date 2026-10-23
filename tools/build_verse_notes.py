#!/usr/bin/env python3
"""Builds the per-verse commentary shown in the verse sheet (tap a verse in the reader).

Source: tools/source/verse_notes/<BOOK>.md, one file per book, written by hand in English and Dutch:

    # MAT
    ## 5:3
    E: Note in English.
    N: Toelichting in het Nederlands.
    ## 16:18 CATHOLIC
    E: A note shown only to readers of that tradition (CATHOLIC, EASTERN_ORTHODOX, ORIENTAL_ORTHODOX).
    N: ...
    ## 51:1 | 51:3
    E: ...
    N: ...

References are in WEB numbering. Where the Statenvertaling numbers the verse differently, give its reference after
a "|". For the Psalms that is done automatically when the chapter only differs by the titles (SV counts a title as
its own verse or two); a chapter of any other book whose verse count differs between WEB and SV needs the explicit
NL reference, so a note never lands on the wrong verse.

Output: app/src/main/assets/content/verse_notes.json, keyed 'BOOK.CHAPTER.VERSE'. When the EN and NL references
differ, each key carries only its own language.

Run from the repository root:  python -I -X utf8 tools/build_verse_notes.py
"""
import glob
import json
import os
import re
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "tools", "source", "verse_notes")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "content", "verse_notes.json")
DB = os.path.join(ROOT, "app", "src", "main", "assets", "bible", "bible.db")
TRADITIONS = {"all", "CATHOLIC", "EASTERN_ORTHODOX", "ORIENTAL_ORTHODOX"}
NOTE = ("Per-verse commentary, built by tools/build_verse_notes.py from tools/source/verse_notes. Keys are "
        "'BOOK.CHAPTER.VERSE'. 'all' applies to every tradition; a tradition key adds a tradition-specific note.")


def ref(text):
    m = re.fullmatch(r"(\d+):(\d+)", text.strip())
    if not m:
        raise ValueError(text)
    return int(m.group(1)), int(m.group(2))


def parse(path):
    book, notes, current = None, [], None
    for n, raw in enumerate(open(path, encoding="utf-8"), 1):
        line = raw.strip()
        if not line:
            continue
        try:
            if line.startswith("# "):
                book = line[2:].strip()
            elif line.startswith("## "):
                head = line[3:].split("|")
                first = head[0].split()
                tradition = first[1] if len(first) > 1 else "all"
                if tradition not in TRADITIONS:
                    raise ValueError(tradition)
                current = {"en_ref": ref(first[0]), "nl_ref": ref(head[1]) if len(head) > 1 else None,
                           "tradition": tradition, "line": n}
                notes.append(current)
            else:
                m = re.match(r"^([EN]):\s*(.+)$", line)
                if not m or current is None:
                    raise ValueError(line[:60])
                current["en" if m.group(1) == "E" else "nl"] = m.group(2).strip()
        except ValueError as e:
            sys.exit(f"{path}:{n}: cannot read line ({e}): {line[:60]}")
    for note in notes:
        if "en" not in note or "nl" not in note:
            sys.exit(f"{path}:{note['line']}: note needs both E: and N:")
    return book, notes


def main():
    db = sqlite3.connect(DB)
    counts = {(t, b, c): n for t, b, c, n in db.execute(
        "SELECT translation, book, chapter, COUNT(*) FROM verses GROUP BY translation, book, chapter")}
    out, problems, total = {"_note": NOTE}, [], 0
    for path in sorted(glob.glob(os.path.join(SRC, "*.md"))):
        book, notes = parse(path)
        if book != os.path.splitext(os.path.basename(path))[0]:
            sys.exit(f"{path}: heading '# {book}' does not match the file name")
        for note in notes:
            (ce, ve), where = note["en_ref"], f"{os.path.basename(path)}:{note['line']}"
            web, sv = counts.get(("web", book, ce)), counts.get(("sv", book, ce))
            nl_ref = note["nl_ref"]
            if nl_ref is None:
                if web == sv:
                    nl_ref = (ce, ve)
                elif book == "PSA" and web and sv and 0 < sv - web <= 2:
                    nl_ref = (ce, ve + sv - web)
                else:
                    problems.append(f"{where}: {book} {ce} has {web} verses in WEB and {sv} in SV; give the NL reference")
                    continue
            cn, vn = nl_ref
            if not web or ve > web:
                problems.append(f"{where}: {book} {ce}:{ve} is not in WEB")
            if not counts.get(("sv", book, cn)) or vn > counts[("sv", book, cn)]:
                problems.append(f"{where}: {book} {cn}:{vn} is not in the Statenvertaling")
            en_key, nl_key = f"{book}.{ce}.{ve}", f"{book}.{cn}.{vn}"
            for key, lang in ((en_key, "en"), (nl_key, "nl")):
                slot = out.setdefault(key, {}).setdefault(note["tradition"], {"en": "", "nl": ""})
                if slot[lang]:
                    problems.append(f"{where}: {key} {note['tradition']} already has a {lang} note")
                slot[lang] = note[lang]
            total += 1
    if problems:
        sys.exit("\n".join(problems))
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"{total} notes on {len(out) - 1} verses -> {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
