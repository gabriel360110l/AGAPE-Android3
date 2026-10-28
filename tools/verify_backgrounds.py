#!/usr/bin/env python3
"""Checks author and licence of Home backgrounds against Wikimedia Commons.

Entries marked "check": true came over from the AI Studio version with metadata that was not verified.
Run on a machine that can reach commons.wikimedia.org:

    python3 tools/verify_backgrounds.py           # show what Commons says
    python3 tools/verify_backgrounds.py --write   # take Commons' author and licence and clear "check"

A file that Commons does not know, or whose licence is not free, is reported and left alone.
"""
import json
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

MANIFEST = Path(__file__).resolve().parent.parent / "app/src/main/assets/backgrounds/backgrounds.json"
API = "https://commons.wikimedia.org/w/api.php"
FREE = ("CC0", "Public domain", "CC BY", "CC BY-SA")


def commons(title: str) -> dict | None:
    q = urllib.parse.urlencode({
        "action": "query", "format": "json", "prop": "imageinfo", "iiprop": "extmetadata", "titles": title,
    })
    req = urllib.request.Request(f"{API}?{q}", headers={"User-Agent": "AGAPE-app/1.0 (background credits)"})
    with urllib.request.urlopen(req, timeout=30) as r:
        pages = json.load(r)["query"]["pages"]
    page = next(iter(pages.values()))
    info = (page.get("imageinfo") or [None])[0]
    if not info:
        return None
    meta = info["extmetadata"]
    strip = lambda v: re.sub(r"<[^>]+>", "", v or "").strip()
    return {
        "artist": strip(meta.get("Artist", {}).get("value")),
        "licence": strip(meta.get("LicenseShortName", {}).get("value")),
    }


def main() -> None:
    write = "--write" in sys.argv
    entries = json.loads(MANIFEST.read_text(encoding="utf-8"))
    for e in entries:
        if not e.get("check"):
            continue
        title = urllib.parse.unquote(e["source"].rsplit("/wiki/", 1)[-1]).replace("_", " ")
        found = commons(title)
        if not found:
            print(f"NOT FOUND  {e['file']}  ({title})")
            continue
        free = any(found["licence"].startswith(f) for f in FREE)
        print(f"{'OK  ' if free else 'NOT FREE'}  {e['file']}: {found['artist']} · {found['licence']}")
        if write and free:
            e["artist"], e["licence"] = found["artist"], found["licence"]
            del e["check"]
    if write:
        MANIFEST.write_text(json.dumps(entries, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
