#!/usr/bin/env python3
"""Builds the articles of the Church section (Bible tab: "Your tradition" and "History").

Source: tools/source/church/NN-<id>.md, one file per article, written by hand in English and Dutch.
Files are read in name order, which is the order the app lists them in.

    id: tradition-eo-sacraments
    section: TRADITION                     (TRADITION or HISTORY)
    traditions: EASTERN_ORTHODOX           (comma separated: EASTERN_ORTHODOX, ORIENTAL_ORTHODOX, CATHOLIC)
    title.en: The Holy Mysteries
    title.nl: De heilige mysteriën
    summary.en: One line for the list.
    summary.nl: Eén regel voor de lijst.

    [en]
    First paragraph.

    Second paragraph.

    [nl]
    Eerste alinea.

    Tweede alinea.

Output: the "articles" list of app/src/main/assets/content/church.json. The Fathers and leaders in that file
are kept as they are.

Run from the repository root:  python -I -X utf8 tools/build_church.py
"""
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "tools", "source", "church")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "content", "church.json")
SECTIONS = {"TRADITION", "HISTORY"}
TRADITIONS = {"EASTERN_ORTHODOX", "ORIENTAL_ORTHODOX", "CATHOLIC"}
HEADER = ["id", "section", "traditions", "title.en", "title.nl", "summary.en", "summary.nl"]


def parse(path):
    head, body, lang, para = {}, {"en": [], "nl": []}, None, []

    def flush():
        if para:
            body[lang].append(" ".join(para))
            para.clear()

    for n, raw in enumerate(open(path, encoding="utf-8"), 1):
        line = raw.strip()
        if line in ("[en]", "[nl]"):
            flush()
            lang = line[1:-1]
        elif lang is None:
            if not line:
                continue
            key, sep, value = line.partition(":")
            if not sep or key.strip() not in HEADER:
                sys.exit(f"{path}:{n}: unexpected header line: {line}")
            head[key.strip()] = value.strip()
        elif not line:
            flush()
        else:
            para.append(line)
    flush()

    missing = [k for k in HEADER if not head.get(k)]
    if missing:
        sys.exit(f"{path}: missing {', '.join(missing)}")
    traditions = [t.strip() for t in head["traditions"].split(",")]
    if head["section"] not in SECTIONS or not traditions or set(traditions) - TRADITIONS:
        sys.exit(f"{path}: bad section or traditions")
    if not body["en"] or len(body["en"]) != len(body["nl"]):
        sys.exit(f"{path}: English and Dutch need the same number of paragraphs ({len(body['en'])} vs {len(body['nl'])})")
    return {
        "id": head["id"], "section": head["section"], "traditions": traditions,
        "title": {"en": head["title.en"], "nl": head["title.nl"]},
        "summary": {"en": head["summary.en"], "nl": head["summary.nl"]},
        "body": body,
    }


def main():
    articles = [parse(p) for p in sorted(glob.glob(os.path.join(SRC, "*.md")))]
    ids = [a["id"] for a in articles]
    if len(ids) != len(set(ids)):
        sys.exit("duplicate article id")
    with open(OUT, encoding="utf-8") as f:
        data = json.load(f)
    data["articles"] = articles
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
        f.write("\n")
    for t in sorted(TRADITIONS):
        n = sum(t in a["traditions"] for a in articles)
        print(f"{t}: {n} articles")
    print(f"{len(articles)} articles -> {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
