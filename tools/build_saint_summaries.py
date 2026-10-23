#!/usr/bin/env python3
"""Adds the hand-written summaries to the Coptic and Catholic saint calendars.

Inputs:   tools/source/saint_summaries_oo.tsv, tools/source/saint_summaries_ca.tsv (title, English, Dutch[, ?])
Outputs:  app/src/main/assets/content/saints_oo.json and saints_ca.json, rewritten in place with a "summary"
          {en, nl} on every saint whose English title has a line in the TSV.

Works offline on the committed JSON, so it can run after build_saints.py or on its own. Summaries are matched on the
exact English title, not on the day, so they survive corrections of the calendars. Titles without a summary are
counted per calendar; the first commemoration of each day is the one the app shows.

Run from the repository root:  python -I -X utf8 tools/build_saint_summaries.py
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "tools", "source")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "content")


def read_tsv(name):
    rows = {}
    with open(os.path.join(SRC, name), encoding="utf-8") as f:
        for n, line in enumerate(f, 1):
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) not in (3, 4) or not parts[1].strip() or not parts[2].strip():
                raise SystemExit(f"{name}:{n}: expected title, English, Dutch[, ?]")
            title = parts[0].strip()
            if title in rows:
                raise SystemExit(f"{name}:{n}: duplicate title {title!r}")
            rows[title] = {"en": parts[1].strip(), "nl": parts[2].strip()}
    return rows


def apply(json_name, tsv_name):
    summaries = read_tsv(tsv_name)
    path = os.path.join(OUT, json_name)
    days = json.load(open(path, encoding="utf-8"))
    used, missing_first, missing_other = set(), [], []
    for day in days:
        for i, saint in enumerate(day["saints"]):
            title = saint["title"]["en"].strip()
            if title in summaries:
                saint["summary"] = summaries[title]
                used.add(title)
            else:
                saint.pop("summary", None)
                (missing_first if i == 0 else missing_other).append(f"{day['month']:02d}-{day['day']:02d} {title}")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(days, f, ensure_ascii=False, indent=1)
    print(f"{json_name}: {len(used)} titles with a summary, first of the day without one: {len(missing_first)}, "
          f"others without one: {len(missing_other)}")
    for line in missing_first:
        print("  missing (shown in app):", line)
    unused = sorted(set(summaries) - used)
    for title in unused:
        print("  unused line in", tsv_name + ":", title)


if __name__ == "__main__":
    apply("saints_oo.json", "saint_summaries_oo.tsv")
    apply("saints_ca.json", "saint_summaries_ca.tsv")
