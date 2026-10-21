"""Keeps the Kyrie in its original Greek: Κύριε ἐλέησον.

The response "Lord, have mercy" / "Heer, ontferm U" and the transliteration "Kyrie eleison" become Κύριε ἐλέησον
wherever they stand as the liturgical response. Sentences that merely contain the words ("O Lord, have mercy on me",
"Heer, ontferm U over ons") are Psalm verses and prayers, not the Kyrie, and are left alone. Rubrics that explain the
response keep a translation in parentheses.

Used by build_prayers.py and build_content.py; can also be run on its own to update the bundled prayer files:
    python tools/kyrie.py
"""
import json
import os
import re

KYRIE = "Κύριε ἐλέησον"

# Explanations of the response keep the translation once, in parentheses.
RUBRICS = [
    ("Kyrie eleison. Lord, have mercy. Repeat 41 times", f"{KYRIE} (Lord, have mercy). Repeat 41 times"),
    ("Kyrië eleison. Heer, ontferm U. Herhaal dit 41 keer", f"{KYRIE} (Heer, ontferm U). Herhaal dit 41 keer"),
    ("Lord have mercy forty-one times", f"{KYRIE} (Lord, have mercy) forty-one times"),
    ("eenenveertig maal Heer, ontferm U", f"eenenveertig maal {KYRIE} (Heer, ontferm U)"),
]

TRANSLIT = re.compile(r"\bKyri[eë],? eleison\b")
# The response itself: not followed by "on/upon/over/me/us/mij/ons", which would make it part of a sentence.
RESPONSE = re.compile(
    r"(?<![Oo] )(?<!compassionate )(?<!\()\b(?:Lord,? have mercy|Heere?,? ontferm U)\b(?!\s+(?:on|upon|over|me|us|mij|ons)\b)"
)


def greek(text: str) -> str:
    for a, b in RUBRICS:
        text = text.replace(a, b)
    text = TRANSLIT.sub(KYRIE, text)
    return RESPONSE.sub(KYRIE, text)


def apply(obj):
    if isinstance(obj, str):
        return greek(obj)
    if isinstance(obj, list):
        return [apply(x) for x in obj]
    if isinstance(obj, dict):
        return {k: (v if k in ("id", "ref", "kind") else apply(v)) for k, v in obj.items()}
    return obj


if __name__ == "__main__":
    out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "assets", "content")
    for name in ("prayers_eo.json", "prayers_oo.json", "prayers_ca.json", "prayers_extra.json"):
        path = os.path.join(out, name)
        data = json.load(open(path, encoding="utf-8"))
        new = apply(data)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(new, f, ensure_ascii=False, indent=1)
        print(name, json.dumps(new, ensure_ascii=False).count(KYRIE), "×", KYRIE)
