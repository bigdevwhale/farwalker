"""
Fetches one photo for each sight in assets/world/sights.tsv from Wikimedia Commons (via the English
Wikipedia article's lead image), crops it square and writes it small:

  assets/sights/<key>.jpg       420x420, JPEG
  assets/sights/CREDITS.txt     file, author and licence of each

Only free licences are kept (public domain, CC0, CC BY, CC BY-SA, Free Art). A sight whose lead image is missing
or not free is listed at the end; give it a file by hand in PICK. Run: python tools/sight_photos.py
"""
import io
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SIGHTS = os.path.join(ROOT, "app", "src", "main", "assets", "world", "sights.tsv")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "sights")
UA = "FarwalkerSightPhotos/1.0 (https://github.com/bigdevwhale/farwalker)"
SIZE = 420

# Article titles where the sight's own name finds the wrong page, and files chosen by hand.
TITLE = {"Blue Lagoon": "Blue Lagoon (geothermal spa)", "Hermitage": "Hermitage Museum"}
PICK = {
    "Blue Lagoon": "Blue Lagoon Geothermal pool 03.jpg",
    "Panama Canal": "Container Ship Enters Miraflores Locks - Panama Canal.jpg",
    "Serengeti": "015 Wildebeest in the Serengeti National Park Photo by Giles Laurent.jpg",
    "Lake Baikal": "Берег острова Ольхон.jpg",
    "Little Mermaid": "Expo 2010 Denmark Pavilion- Little Mermaid Statue from Copenhagen Harbor 01.jpg",
    "Great Barrier Reef": "Heart Reef and Lagoon Great Barrier Reef.jpg",
}

FREE = re.compile(r"^(public domain|pd|cc0|fal|cc by(-sa)?( \d\.\d)?.*)$", re.I)


def key(en):
    return re.sub(r"[^a-z0-9]+", "_", en.lower()).strip("_")


def get(url, raw=False):
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=30) as r:
                data = r.read()
            return data if raw else json.loads(data)
        except Exception as e:  # noqa: BLE001 - retried, then reported
            if attempt == 3:
                raise
            time.sleep(2 + attempt * 3)


def lead_file(title):
    q = urllib.parse.urlencode({"action": "query", "format": "json", "redirects": 1, "titles": title,
                                "prop": "pageimages", "piprop": "name"})
    pages = get("https://en.wikipedia.org/w/api.php?" + q)["query"]["pages"]
    for p in pages.values():
        if "pageimage" in p:
            return p["pageimage"]
    # Not an exact title: the best search hit.
    q = urllib.parse.urlencode({"action": "query", "format": "json", "generator": "search", "gsrsearch": title,
                                "gsrlimit": 1, "prop": "pageimages", "piprop": "name"})
    pages = get("https://en.wikipedia.org/w/api.php?" + q).get("query", {}).get("pages", {})
    for p in pages.values():
        if "pageimage" in p:
            return p["pageimage"]
    return None


def info(file):
    q = urllib.parse.urlencode({"action": "query", "format": "json", "titles": "File:" + file, "prop": "imageinfo",
                                "iiprop": "url|extmetadata", "iiurlwidth": 900})
    pages = get("https://commons.wikimedia.org/w/api.php?" + q)["query"]["pages"]
    for p in pages.values():
        ii = p.get("imageinfo")
        if ii:
            return ii[0]
    return None


def plain(html):
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", html or "")).strip()


def main():
    os.makedirs(OUT, exist_ok=True)
    rows = [l.rstrip("\n").split("\t") for l in open(SIGHTS, encoding="utf8") if l.strip()]
    credits, missing = [], []
    for r in rows:
        en = r[0]
        k = key(en)
        try:
            file = PICK.get(en) or lead_file(TITLE.get(en, en))
            ii = info(file) if file else None
            meta = (ii or {}).get("extmetadata", {})
            lic = plain(meta.get("LicenseShortName", {}).get("value"))
            if not ii or not FREE.match(lic):
                missing.append(f"{en}: {file} ({lic or 'no licence'})")
                continue
            img = Image.open(io.BytesIO(get(ii.get("thumburl") or ii["url"], raw=True))).convert("RGB")
            s = min(img.size)
            left, top = (img.width - s) // 2, (img.height - s) // 3
            img = img.crop((left, top, left + s, top + s)).resize((SIZE, SIZE), Image.LANCZOS)
            img.save(os.path.join(OUT, k + ".jpg"), quality=74, optimize=True, progressive=True)
            artist = plain(meta.get("Artist", {}).get("value")) or "unknown"
            credits.append(f"{k}.jpg\t{en}\t{file}\t{artist}\t{lic}\thttps://commons.wikimedia.org/wiki/File:{urllib.parse.quote(file)}")
            print("ok", en, "|", lic)
        except Exception as e:  # noqa: BLE001
            missing.append(f"{en}: {e}")
        time.sleep(0.3)
    with open(os.path.join(OUT, "CREDITS.txt"), "w", encoding="utf8", newline="\n") as f:
        f.write("# Photos of the sights, from Wikimedia Commons. file, sight, Commons file, author, licence, source\n")
        f.write("\n".join(credits) + "\n")
    total = sum(os.path.getsize(os.path.join(OUT, n)) for n in os.listdir(OUT))
    print(len(credits), "photos,", total // 1024, "KB")
    if missing:
        print("missing:\n  " + "\n  ".join(missing))


if __name__ == "__main__":
    sys.exit(main())
