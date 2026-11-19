#!/usr/bin/env python3
"""Writes the one Dutch text of each shared prayer over its older variants.

The texts and their sources live in tools/source/common_prayers_nl.json. This
script replaces every listed variant (and fragment) in the source tables the
builders read and in the content assets they wrote, so a rebuild and the
shipped files say the same thing. Run with --check to only report leftovers.

  python3 tools/common_prayers.py [--check]
"""
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TABLE = os.path.join(ROOT, "tools", "source", "common_prayers_nl.json")
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
FILES = sorted({os.path.join(ROOT, f) for fs in SCOPE.values() for f in fs})


def pairs(path):
    """The (old, new) replacements that apply to one file."""
    rel = os.path.relpath(path, ROOT)
    out = []
    for p in json.load(open(TABLE, encoding="utf-8"))["prayers"]:
        if rel in SCOPE[p["tradition"]]:
            out += [(v, p["text"]) for v in p.get("variants", [])]
            out += [tuple(f) for f in p.get("fragments", [])]
    return out


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
