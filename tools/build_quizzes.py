#!/usr/bin/env python3
"""Builds the hand-written chapter quizzes that follow the last verse of a chapter.

Source: tools/source/quizzes/<BOOK>.md, one file per book, written by hand in English and Dutch:

    # MAT
    ## 1
    ? Question in English? | Vraag in het Nederlands?
    = The right answer | Het goede antwoord
    - A wrong answer | Een fout antwoord
    - Another wrong answer | Nog een fout antwoord

The right answer is written first; the build moves it to a fixed but varied position, so it is not always the
same button. Output: the "chapter:BOOK.N" entries of app/src/main/assets/content/quizzes.json. Other quizzes
(plans) are kept. Chapters without written questions keep the quiz the app generates from the Bible text.

Run from the repository root:  python -I tools/build_quizzes.py
"""
import glob
import json
import os
import sqlite3
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "tools", "source", "quizzes")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "content", "quizzes.json")
DB = os.path.join(ROOT, "app", "src", "main", "assets", "bible", "bible.db")
ORDER = ["MAT", "MRK", "LUK", "JHN", "ACT", "ROM", "1CO", "2CO", "GAL", "EPH", "PHP", "COL", "1TH", "2TH", "1TI",
         "2TI", "TIT", "PHM", "HEB", "JAS", "1PE", "2PE", "1JN", "2JN", "3JN", "JUD", "REV"]


def pair(text, where):
    parts = [p.strip() for p in text.split("|")]
    if len(parts) != 2 or not all(parts):
        sys.exit(f"{where}: needs 'English | Nederlands': {text[:60]}")
    return {"en": parts[0], "nl": parts[1]}


def finish(question, where):
    right, wrong = question.pop("right"), question.pop("wrong")
    if right is None or not 2 <= len(wrong) <= 3:
        sys.exit(f"{where}: a question needs one '=' answer and two or three '-' answers")
    at = zlib.crc32(question["q"]["en"].encode("utf-8")) % (len(wrong) + 1)
    question["options"] = wrong[:at] + [right] + wrong[at:]
    question["answer"] = at


def parse(path):
    book, chapters, questions, current = None, {}, None, None
    for n, raw in enumerate(open(path, encoding="utf-8"), 1):
        line, where = raw.strip(), f"{path}:{n}"
        if not line:
            continue
        if line.startswith("# "):
            book = line[2:].strip()
        elif line.startswith("## "):
            questions = chapters.setdefault(int(line[3:].strip()), [])
        elif line[:2] == "? " and questions is not None:
            if current:
                finish(current, where)
            current = {"q": pair(line[2:], where), "right": None, "wrong": []}
            questions.append(current)
        elif line[:2] == "= " and current:
            current["right"] = pair(line[2:], where)
        elif line[:2] == "- " and current:
            current["wrong"].append(pair(line[2:], where))
        else:
            sys.exit(f"{where}: cannot read line: {line[:60]}")
    if current:
        finish(current, path)
    return book, chapters


def chapter_counts():
    with sqlite3.connect(f"file:{DB}?mode=ro", uri=True) as db:
        return dict(db.execute("SELECT book, MAX(chapter) FROM verses GROUP BY book"))


def main():
    counts = chapter_counts()
    written = {}
    for path in sorted(glob.glob(os.path.join(SRC, "*.md"))):
        book, chapters = parse(path)
        for chapter, questions in chapters.items():
            if not 1 <= chapter <= counts.get(book, 0):
                sys.exit(f"{path}: {book} has no chapter {chapter}")
            if len(questions) < 3:
                sys.exit(f"{path}: {book} {chapter} needs at least three questions")
            if f"{book}.{chapter}" in written:
                sys.exit(f"{path}: {book} {chapter} is written twice")
            written[f"{book}.{chapter}"] = questions

    with open(OUT, encoding="utf-8") as f:
        data = json.load(f)
    books = {key.split(".")[0] for key in written}
    kept = {k: v for k, v in data["quizzes"].items() if not (k.startswith("chapter:") and k[8:].split(".")[0] in books)}

    def order(key):
        book, chapter = key.split(".")
        return (ORDER.index(book) if book in ORDER else len(ORDER), book, int(chapter))

    chapters = {f"chapter:{key}": written[key] for key in sorted(written, key=order)}
    others = {k: v for k, v in kept.items() if not k.startswith("chapter:")}
    older = {k: v for k, v in kept.items() if k.startswith("chapter:")}
    quizzes = {**chapters, **older, **others}

    # One question per line keeps the file readable and its diffs small.
    lines = ["{", f' "passMark": {json.dumps(data["passMark"])},', ' "quizzes": {']
    for i, (key, questions) in enumerate(quizzes.items()):
        lines.append(f"  {json.dumps(key)}: [")
        lines.append(",\n".join("   " + json.dumps(q, ensure_ascii=False) for q in questions))
        lines.append("  ]" + ("," if i < len(quizzes) - 1 else ""))
    lines += [" }", "}", ""]
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))
    for book in sorted(books, key=lambda b: ORDER.index(b) if b in ORDER else len(ORDER)):
        n = sum(1 for key in written if key.startswith(book + "."))
        q = sum(len(v) for key, v in written.items() if key.startswith(book + "."))
        print(f"{book}: {n} chapters, {q} questions")
    print("total", len(written), "chapters with written quizzes")


if __name__ == "__main__":
    main()
