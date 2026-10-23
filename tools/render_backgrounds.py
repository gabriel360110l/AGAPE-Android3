#!/usr/bin/env python3
"""Paints the bundled Home backgrounds: dark, candle-lit church interiors in a Byzantine manner.

These are our own artwork (no photograph, no third-party material), released under CC0 with the app, so they can
ship before real photographs are chosen. tools/build_backgrounds.py adds public-domain / CC0 photographs from
Wikimedia Commons on top of these when it is run on a machine with internet access.

Output: app/src/main/assets/backgrounds/art_*.jpg (1080 x 1500, dark at the bottom so text stays readable).
Run from the repository root (needs Pillow and numpy):  python tools/render_backgrounds.py
"""
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "backgrounds")
W, H = 1080, 1500

GOLD = np.array([212, 168, 92], dtype=np.float32)
DEEP = np.array([14, 10, 8], dtype=np.float32)


def vertical(top, bottom):
    t = np.linspace(0, 1, H, dtype=np.float32)[:, None, None]
    return np.broadcast_to(np.array(top, np.float32) * (1 - t) + np.array(bottom, np.float32) * t, (H, W, 3)).copy()


def glow(img, cx, cy, radius, color, strength):
    """Adds a soft round light (additive) to a float image."""
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    d2 = ((xx - cx) ** 2 + (yy - cy) ** 2) / (radius ** 2)
    img += np.exp(-d2)[..., None] * np.array(color, np.float32) / 255 * strength


def mosaic(mask, rng, base=GOLD, tile=9, jitter=0.22):
    """Gold tesserae: a grid of small squares of slightly different brightness, only where mask is set."""
    tiles = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(tiles)
    for y in range(0, H, tile):
        off = (y // tile % 2) * tile // 2
        for x in range(-tile, W, tile):
            k = 1 + rng.uniform(-jitter, jitter)
            col = tuple(int(min(255, v * k)) for v in base)
            d.rectangle([x + off, y, x + off + tile - 2, y + tile - 2], fill=col)
    arr = np.asarray(tiles, np.float32)
    return arr * mask[..., None]


def lamp(draw_img, cx, top, cy, rng):
    """A hanging oil lamp: a thin chain from the vault and a small cup of light."""
    d = ImageDraw.Draw(draw_img)
    d.line([(cx, top), (cx, cy - 18)], fill=(70, 55, 35), width=2)
    d.polygon([(cx - 22, cy - 18), (cx + 22, cy - 18), (cx + 12, cy + 6), (cx - 12, cy + 6)], fill=(96, 72, 38))
    d.ellipse([cx - 5, cy - 30, cx + 5, cy - 16], fill=(255, 226, 160))


def finish(arr, name, vignette=0.75, floor=0.55, soft=5):
    """Soft focus and bloom, a vignette, a dark lower half for the text, film grain, and save."""
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    blurred = np.asarray(img.filter(ImageFilter.GaussianBlur(soft)), np.float32)
    bright = Image.fromarray(np.clip((arr - 150) * 1.6, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(28))
    arr = arr * 0.45 + blurred * 0.55 + np.asarray(bright, np.float32) * 0.55
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    r = np.sqrt(((xx - W / 2) / (W * 0.75)) ** 2 + ((yy - H * 0.38) / (H * 0.7)) ** 2)
    arr *= (1 - vignette * np.clip(r - 0.35, 0, 1))[..., None]
    t = np.clip((yy - H * 0.45) / (H * 0.55), 0, 1)
    arr *= (1 - floor * t)[..., None]
    arr += np.random.default_rng(7).normal(0, 3.2, arr.shape).astype(np.float32)
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, f"{name}.jpg")
    img.save(path, "JPEG", quality=82, optimize=True, progressive=True)
    print("wrote", path, os.path.getsize(path) // 1024, "KB")


def sanctuary():
    """Looking down the nave towards the apse: golden half-dome, columns, lamps and candles before the icons."""
    rng = random.Random(1)
    arr = vertical((26, 19, 14), (10, 8, 7))
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)

    # The apse arch with its golden half-dome.
    cx, spring, half = W / 2, 520, 300
    inside_arch = ((np.abs(xx - cx) < half) & (yy > spring)) | (((xx - cx) ** 2 + (yy - spring) ** 2) < half ** 2)
    dome = (((xx - cx) ** 2 + (yy - spring) ** 2) < (half - 24) ** 2) & (yy < spring + 40)
    wall = inside_arch & ~dome
    gold = mosaic(dome.astype(np.float32), rng, tile=10)
    light = np.clip(1 - np.sqrt((xx - cx) ** 2 + (yy - spring + 120) ** 2) / 420, 0.15, 1)[..., None]
    arr = np.where(dome[..., None], gold * (0.35 + 0.75 * light), arr)
    arr = np.where(wall[..., None], arr * 0.6 + np.array([46, 34, 22], np.float32) * 0.55, arr)

    # A ring of mosaic medallions round the dome and a dark figure of light at the centre (no likeness).
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    for i in range(11):
        a = math.pi * (i + 0.5) / 11
        x, y = cx - math.cos(a) * (half - 56), spring - math.sin(a) * (half - 56)
        d.ellipse([x - 17, y - 17, x + 17, y + 17], outline=(120, 84, 40), width=3)
    d.arc([cx - half + 4, spring - half + 4, cx + half - 4, spring + half - 4], 180, 360, fill=(150, 110, 52), width=6)
    d.arc([cx - 120, spring - 250, cx + 120, spring - 10], 180, 360, fill=(240, 205, 130), width=3)
    # The iconostasis: a row of tall dark panels with gold frames.
    base = 1040
    for i in range(-3, 4):
        x0 = cx + i * 92 - 40
        d.rectangle([x0, base - 300, x0 + 80, base], fill=(40, 28, 18), outline=(150, 112, 56), width=3)
        d.ellipse([x0 + 22, base - 270, x0 + 58, base - 234], fill=(120, 90, 46))
    d.rectangle([cx - 46, base - 330, cx + 46, base], fill=(28, 20, 14), outline=(190, 145, 70), width=4)
    d.arc([cx - 46, base - 376, cx + 46, base - 284], 180, 360, fill=(190, 145, 70), width=4)

    # Columns either side with simple capitals.
    for side in (-1, 1):
        for k, (x, w) in enumerate([(cx + side * 380, 70), (cx + side * 500, 86)]):
            shade = 34 - k * 10
            d.rectangle([x - w / 2, 300 - k * 120, x + w / 2, H], fill=(shade, shade - 8, shade - 14))
            d.rectangle([x - w / 2 - 14, 290 - k * 120, x + w / 2 + 14, 330 - k * 120], fill=(shade + 18, shade + 6, shade - 6))
            d.line([(x - w / 2 + 8, 340 - k * 120), (x - w / 2 + 8, H)], fill=(shade + 22, shade + 10, shade), width=3)
    # Upper arcade arches in the side walls.
    for side in (-1, 1):
        for j in range(3):
            x = cx + side * (380 + j * 0) + side * 0
            top = 120 + j * 60
            d.arc([cx + side * 440 - 120, top, cx + side * 440 + 120, top + 240], 180, 360, fill=(60, 44, 28), width=8)

    for x, y in [(cx, 610), (cx - 210, 660), (cx + 210, 660), (cx - 330, 560), (cx + 330, 560)]:
        lamp(img, x, 0 if abs(x - cx) > 250 else 260, y, rng)
    arr = np.asarray(img, np.float32).copy()

    for x, y in [(cx, 590), (cx - 210, 640), (cx + 210, 640), (cx - 330, 540), (cx + 330, 540)]:
        glow(arr, x, y, 70, (255, 190, 110), 60)
        glow(arr, x, y, 220, (255, 170, 90), 18)
    # Candle stand before the icons.
    for i in range(22):
        x = cx - 230 + i * 22 + rng.uniform(-6, 6)
        y = 1070 + rng.uniform(-14, 14)
        glow(arr, x, y, 9, (255, 220, 150), 120)
        glow(arr, x, y, 46, (255, 160, 80), 14)
    glow(arr, cx, 1060, 300, (255, 150, 70), 22)
    # Haze from the windows of the dome.
    glow(arr, cx, 240, 380, (255, 214, 150), 14)
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.6))
    finish(np.asarray(img, np.float32).copy(), "art_sanctuary", soft=6)


def dome():
    """Looking up into a dome: windows in a ring, gold ribs, and a medallion of light at the crown."""
    rng = random.Random(2)
    arr = vertical((20, 15, 11), (9, 7, 6))
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    cx, cy, R = W / 2, 520, 470
    r = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    inside = r < R
    gold = mosaic(inside.astype(np.float32), rng, tile=8, jitter=0.18)
    shade = np.clip(1.15 - r / R, 0.12, 1)[..., None]
    arr = np.where(inside[..., None], gold * shade * 0.9 + arr * 0.2, arr)
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    n = 16
    for i in range(n):
        a = 2 * math.pi * i / n
        d.line([(cx + math.cos(a) * 90, cy + math.sin(a) * 90), (cx + math.cos(a) * R, cy + math.sin(a) * R)], fill=(70, 50, 28), width=7)
        b = a + math.pi / n
        wx, wy = cx + math.cos(b) * (R - 50), cy + math.sin(b) * (R - 50)
        d.ellipse([wx - 22, wy - 34, wx + 22, wy + 34], fill=(250, 232, 190))
    d.ellipse([cx - R, cy - R, cx + R, cy + R], outline=(110, 80, 40), width=14)
    d.ellipse([cx - 96, cy - 96, cx + 96, cy + 96], outline=(220, 180, 100), width=6)
    d.line([(cx, cy - 60), (cx, cy + 60)], fill=(235, 200, 120), width=8)
    d.line([(cx - 44, cy - 14), (cx + 44, cy - 14)], fill=(235, 200, 120), width=8)
    arr = np.asarray(img, np.float32).copy()
    for i in range(n):
        b = 2 * math.pi * i / n + math.pi / n
        glow(arr, cx + math.cos(b) * (R - 50), cy + math.sin(b) * (R - 50), 80, (255, 220, 160), 30)
    glow(arr, cx, cy, 160, (255, 210, 140), 26)
    # Pendentives and the dark drum below.
    glow(arr, cx, H * 0.95, 380, (255, 150, 70), 16)
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.4))
    finish(np.asarray(img, np.float32).copy(), "art_dome", vignette=0.9)


def candles():
    """A stand of beeswax candles in the dark, as before an icon in the evening."""
    rng = random.Random(3)
    arr = vertical((18, 13, 10), (8, 6, 5))
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    # An icon in its frame, in shadow, above the candles.
    d.rectangle([W / 2 - 230, 150, W / 2 + 230, 720], fill=(40, 28, 18), outline=(140, 104, 52), width=8)
    d.ellipse([W / 2 - 110, 220, W / 2 + 110, 440], fill=(150, 112, 54))
    d.ellipse([W / 2 - 92, 238, W / 2 + 92, 422], fill=(52, 36, 24))
    d.line([(W / 2, 270), (W / 2, 640)], fill=(190, 146, 72), width=10)
    d.line([(W / 2 - 80, 360), (W / 2 + 80, 360)], fill=(190, 146, 72), width=10)
    d.line([(W / 2 - 52, 470), (W / 2 + 52, 450)], fill=(190, 146, 72), width=8)
    flames = []
    for row, (y0, n, h) in enumerate([(1000, 9, 300), (1090, 12, 240), (1180, 15, 180)]):
        for i in range(n):
            x = W / 2 + (i - (n - 1) / 2) * (W * 0.72 / n) + rng.uniform(-10, 10)
            top = y0 - h * rng.uniform(0.6, 1.0)
            d.rectangle([x - 6, top, x + 6, y0 + 120], fill=(196, 150, 84))
            d.line([(x - 4, top), (x - 4, y0 + 120)], fill=(226, 186, 118), width=2)
            flames.append((x, top - 16))
    d.rectangle([W * 0.1, 1290, W * 0.9, 1330], fill=(70, 52, 30))
    arr = np.asarray(img, np.float32).copy()
    for x, y in flames:
        glow(arr, x, y, 8, (255, 236, 180), 150)
        glow(arr, x, y - 4, 40, (255, 170, 80), 26)
    glow(arr, W / 2, 1050, 420, (255, 150, 70), 30)
    glow(arr, W / 2, 430, 260, (255, 190, 110), 14)
    img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.8))
    finish(np.asarray(img, np.float32).copy(), "art_candles", floor=0.35, soft=8)


if __name__ == "__main__":
    sanctuary()
    dome()
    candles()
