#!/usr/bin/env python3
"""Downloads public-domain / CC0 photographs of church interiors from Wikimedia Commons for the Home background.

Only files whose Commons licence is Public domain or CC0 are kept (most photographs of buildings on Commons are
CC BY-SA, so they are skipped). Each photo is saved as a 1080 px wide JPEG in app/src/main/assets/backgrounds/ and
listed with title, artist, licence and source page in backgrounds.json, next to the painted backgrounds from
tools/render_backgrounds.py (which are always kept, so the app never lacks a background).

Look at every downloaded photo before committing it: the search can return the wrong subject.
Run from the repository root (needs Pillow):  python tools/build_backgrounds.py
"""
import json
import os
import re
import time
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "backgrounds")
OUT_JSON = os.path.join(OUT_DIR, "backgrounds.json")
UA = "AGAPE-app-build/1.0 (https://github.com/gabriel360110l/AGAPE-Android3)"
FREE = {"public domain", "cc0", "pd"}
WIDTH = 1080
LIMIT = 8

# Searches tried in order; the first free, large, landscape-or-portrait photo of each is kept, up to LIMIT photos.
SEARCHES = [
    "Hagia Sophia interior",
    "Byzantine church interior mosaic",
    "Orthodox church interior iconostasis",
    "Monreale Cathedral interior",
    "San Vitale Ravenna interior",
    "Basilica di San Marco interior",
    "Chora Church mosaic",
    "Coptic church interior",
    "church interior candles",
    "cathedral interior nave",
]

BAD_TITLE = re.compile(r"exterior|map|plan|coin|seal|stamp|drawing|engraving|\.svg$|\.tif$", re.I)


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


def candidates(q):
    d = api(action="query", generator="search", gsrnamespace=6, gsrlimit=30, gsrsearch=f"{q} filetype:bitmap",
            prop="imageinfo", iiprop="url|extmetadata|size", iiurlwidth=WIDTH)
    for p in sorted((d.get("query", {}).get("pages", {}) or {}).values(), key=lambda p: p.get("index", 0)):
        ii = (p.get("imageinfo") or [{}])[0]
        meta = ii.get("extmetadata", {})
        yield p["title"], ii, meta, clean(meta.get("LicenseShortName", {}).get("value", ""))


def main():
    from PIL import Image  # noqa: PLC0415 (only needed here)
    os.makedirs(OUT_DIR, exist_ok=True)
    current = json.load(open(OUT_JSON, encoding="utf-8")) if os.path.exists(OUT_JSON) else []
    out = [e for e in current if os.path.basename(e["file"]).startswith("art_")]
    seen = set()
    for q in SEARCHES:
        if len(out) - 3 >= LIMIT:
            break
        for title, ii, meta, licence in candidates(q):
            low = licence.lower()
            if title in seen or (low not in FREE and not low.startswith("public domain")):
                continue
            if BAD_TITLE.search(title) or ii.get("width", 0) < 1600:
                continue
            seen.add(title)
            name = "photo_" + re.sub(r"[^a-z0-9]+", "_", title.removeprefix("File:").rsplit(".", 1)[0].lower())[:40].strip("_") + ".jpg"
            path = os.path.join(OUT_DIR, name)
            req = urllib.request.Request(ii["thumburl"], headers={"User-Agent": UA})
            with open(path, "wb") as f:
                f.write(urllib.request.urlopen(req, timeout=60).read())
            im = Image.open(path).convert("RGB")
            # Home shows a tall crop; keep a centred portrait crop of at most 1080 x 1500.
            if im.width > im.height * 0.72:
                w = round(im.height * 0.72)
                im = im.crop(((im.width - w) // 2, 0, (im.width + w) // 2, im.height))
            im = im.resize((WIDTH, round(im.height * WIDTH / im.width)), Image.LANCZOS)
            im.save(path, "JPEG", quality=80, optimize=True, progressive=True)
            out.append({
                "file": f"backgrounds/{name}",
                "title": title.removeprefix("File:").rsplit(".", 1)[0],
                "artist": clean(meta.get("Artist", {}).get("value", ""))[:120],
                "licence": licence,
                "source": ii.get("descriptionurl", ""),
            })
            print(f"  {licence:14s} {title[:80]}")
            time.sleep(0.3)
            break
    with open(OUT_JSON, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print("wrote", len(out), "backgrounds")


if __name__ == "__main__":
    main()
