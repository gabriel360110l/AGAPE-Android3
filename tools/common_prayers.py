#!/usr/bin/env python3
"""Writes the one text of each shared prayer over its older variants, in Dutch or (--lang en) in English.

The texts and their sources live in tools/source/common_prayers_nl.json and common_prayers_en.json. This
script replaces every listed variant (and fragment) in the source tables the
builders read and in the content assets they wrote, so a rebuild and the
shipped files say the same thing. Run with --check to only report leftovers.

  python3 tools/common_prayers.py [--lang en] [--check]
"""
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = sys.argv[sys.argv.index("--lang") + 1] if "--lang" in sys.argv else "nl"
TABLE = os.path.join(ROOT, "tools", "source", f"common_prayers_{LANG}.json")
ASSETS = os.path.join("app", "src", "main", "assets", "content")
# Where each tradition's texts live; a prayer is only rewritten there, so for instance the Coptic
# "Heilige Sterke" and "Glorie aan de Vader" of the Agpeya stay as the Coptic church in Utrecht prays them.
SCOPE = {
    "EASTERN_ORTHODOX": [
        os.path.join(ASSETS, "prayers_eo.json"), os.path.join(ASSETS, "prayers_extra.json"),
        "tools/source/prayers_eo_nl.json", "tools/source/horologion_seed.json", "tools/source/legacy/prayers.json",
        "tools/build_content.py", "tools/build_prayers.py"],
    "ORIENTAL_ORTHODOX": [
        os.path.join(ASSETS, "prayers_oo.json"), "tools/source/translations_nl.json",
        "tools/source/legacy/agpeya-offices.json", "tools/source/legacy/agpeya-hours.json", "tools/build_prayers.py"],
    "CATHOLIC": [
        os.path.join(ASSETS, "prayers_ca.json"), os.path.join(ASSETS, "prayers_extra.json"),
        "tools/source/liturgy_hours_ca_seed.json", "tools/build_content.py", "tools/build_hours_ca.py"],
}
# The English texts: the content assets, the tables the builders read, and the Rosary's own prayers.
SCOPE_EN = {
    "EASTERN_ORTHODOX": [
        os.path.join(ASSETS, "prayers_eo.json"), os.path.join(ASSETS, "prayers_extra.json"),
        "tools/source/horologion_seed.json", "tools/source/legacy/prayers.json", "tools/build_content.py"],
    "ORIENTAL_ORTHODOX": [
        os.path.join(ASSETS, "prayers_oo.json"), os.path.join(ASSETS, "prayers_extra.json"),
        "tools/source/legacy/agpeya-offices.json", "tools/source/legacy/agpeya-hours.json", "tools/build_content.py"],
    "CATHOLIC": [
        os.path.join(ASSETS, "prayers_ca.json"), os.path.join(ASSETS, "prayers_extra.json"),
        "tools/source/liturgy_hours_ca_seed.json", "tools/build_content.py"],
}
if LANG == "en":
    SCOPE = SCOPE_EN
FILES = sorted({os.path.join(ROOT, f) for fs in SCOPE.values() for f in fs})


def pairs(path):
    """The (old, new) replacements that apply to one file."""
    rel = os.path.relpath(path, ROOT)
    out = []
    for p in json.load(open(TABLE, encoding="utf-8"))["prayers"]:
        if rel in SCOPE[p["tradition"]]:
            out += [(v, p["text"]) for v in p.get("variants", [])]
            out += [tuple(f) for f in p.get("fragments", [])]
            # A prayer kept line by line (the Creeds): each older line is replaced by the line in its place.
            # Only whole lines (a string of their own, between quotes), so a short line such as "Thy kingdom come."
            # is never replaced inside another tradition's prayer in a shared file.
            for old in p.get("variant_lines", []):
                out += [(f'"{o}"', f'"{n}"') for o, n in zip(old, p["lines"]) if o != n]
    # The longest first, so a whole prayer is replaced before any of its lines could break it up.
    return sorted(out, key=lambda pair: -len(pair[0]))


def main(check):
    left = 0
    for path in FILES:
        if not os.path.exists(path):
            continue
        text = open(path, encoding="utf-8").read()
        new = text
        for old, good in pairs(path):
            n = new.count(old)
            if n:
                print(f"{os.path.relpath(path, ROOT)}: {n}x {old[:60]}...")
                left += n
                new = new.replace(old, good)
        if not check and new != text:
            open(path, "w", encoding="utf-8", newline="\n").write(new)
    return left


if __name__ == "__main__":
    check = "--check" in sys.argv
    n = main(check)
    print(("left: " if check else "replaced: ") + str(n))
    sys.exit(1 if check and n else 0)
