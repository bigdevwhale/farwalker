"""
Builds the world the traveller walks through, from Natural Earth (public domain):

  assets/world/countries.tsv  iso, English and Russian names, continent, kind (S UN state, T other land), label point, area
  assets/world/shapes.bin     country outlines for the map, simplified, in hundredths of a degree
  assets/world/towns.tsv      towns and capes: id, names, iso, lat, lon, population, time zone, wikidata, flags
  assets/world/edges.tsv      roads and sea lanes between towns, with the borders crossed along each road

Roads are straight lines between neighbouring towns that stay on land, counted 1.25x longer than the
straight line (roads wind). Sea lanes join ports of different landmasses, plus a hand-written list of
the great crossings. Run: python tools/world.py   (downloads the data into tools/.cache on first run)
"""
import heapq
import json
import math
import os
import struct
import sys
import urllib.request
from collections import defaultdict

import numpy as np
import shapely
from shapely.geometry import shape
from shapely.strtree import STRtree

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, "tools", ".cache")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "world")
NE = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/"

ROAD = 1.25          # a road is this much longer than the straight line
NEIGHBOURS = 6       # roads from each town to its nearest towns...
ROAD_MAX = 420       # ...no further than this many km
SAMPLE = 4.0         # km between checks along a road
WET_MAX = 3.0        # a road may cross this many km of sea (estuaries, outline slop); straits take a ferry

# Ends of the earth: the capes a traveller goes out of their way for. (en, ru, lat, lon, iso)
CAPES = [
    ("Cabo da Roca", "Мыс Рока", 38.7804, -9.4989, "PRT"),
    ("Nordkapp", "Нордкап", 71.1700, 25.7837, "NOR"),
    ("Cape Agulhas", "Мыс Игольный", -34.8330, 20.0033, "ZAF"),
    ("Cape of Good Hope", "Мыс Доброй Надежды", -34.3568, 18.4740, "ZAF"),
    ("Cape Horn", "Мыс Горн", -55.9800, -67.2700, "CHL"),
    ("Cape Froward", "Мыс Фроуард", -53.8970, -71.2970, "CHL"),
    ("Punta Gallinas", "Пунта-Гальинас", 12.4590, -71.6680, "COL"),
    ("Ponta do Seixas", "Понта-ду-Сейшас", -7.1550, -34.7930, "BRA"),
    ("Cape York", "Мыс Йорк", -10.6870, 142.5310, "AUS"),
    ("Steep Point", "Стип-Пойнт", -26.1530, 113.1550, "AUS"),
    ("Cape Byron", "Мыс Байрон", -28.6380, 153.6380, "AUS"),
    ("Cape Dezhnev", "Мыс Дежнёва", 66.0750, -169.6600, "RUS"),
    ("Cape Chelyuskin", "Мыс Челюскин", 77.7200, 104.2600, "RUS"),
    ("Point Barrow", "Мыс Барроу", 71.3870, -156.4810, "USA"),
    ("Cape Prince of Wales", "Мыс Принца Уэльского", 65.6100, -168.0870, "USA"),
    ("Ras ben Sakka", "Рас-эль-Абьяд", 37.3450, 9.7480, "TUN"),
    ("Ras Hafun", "Рас-Хафун", 10.4380, 51.4120, "SOM"),
    ("Cap-Vert", "Зелёный Мыс", 14.7270, -17.5300, "SEN"),
    ("Tanjung Piai", "Танджунг-Пиай", 1.2670, 103.5110, "MYS"),
    ("Cape Comorin", "Мыс Коморин", 8.0780, 77.5410, "IND"),
    ("Cape Finisterre", "Мыс Финистерре", 42.8820, -9.2720, "ESP"),
    ("South Pole", "Южный полюс", -89.9990, 0.0, "ATA"),
]

# Roads the nearest-town rule can't find: the trek across the ice to the Pole, and the great bridges
# and tunnels over water a road couldn't otherwise cross. (English name, iso) pairs.
TREKS = [
    (("South Pole", "ATA"), ("McMurdo Station", "ATA")),
    (("Copenhagen", "DNK"), ("Malmö", "SWE")),       # the Øresund Bridge
    (("Copenhagen", "DNK"), ("Odense", "DNK")),      # the Great Belt Bridge
    (("Calais", "FRA"), ("Dover", "GBR")),           # the Channel Tunnel
    (("Dammam", "SAU"), ("Manama", "BHR")),          # the King Fahd Causeway
    (("Zhuhai", "CHN"), ("Macau", "MAC")),
    # Roads that go round a bay a straight line would cross.
    (("Doha", "QAT"), ("Hofuf", "SAU")),
    (("Halifax", "CAN"), ("Moncton", "CAN")),
]

# The great sea crossings, which nearest-port links alone would miss: (from, to, iso of each end).
LANES = [
    ("Lisbon", "PRT", "New York", "USA"),
    ("Liverpool", "GBR", "Halifax", "CAN"),
    ("Southampton", "GBR", "New York", "USA"),
    ("Dakar", "SEN", "Recife", "BRA"),
    ("Cape Town", "ZAF", "Buenos Aires", "ARG"),
    ("Cape Town", "ZAF", "Perth", "AUS"),
    ("Durban", "ZAF", "Mumbai", "IND"),
    ("Mombasa", "KEN", "Mumbai", "IND"),
    ("Colombo", "LKA", "Singapore", "SGP"),
    ("Yokohama", "JPN", "San Francisco", "USA"),
    ("Yokohama", "JPN", "Honolulu", "USA"),
    ("Honolulu", "USA", "San Francisco", "USA"),
    ("Honolulu", "USA", "Papeete", "PYF"),
    ("Papeete", "PYF", "Auckland", "NZL"),
    ("Valparaíso", "CHL", "Papeete", "PYF"),
    ("Sydney", "AUS", "Auckland", "NZL"),
    ("Sydney", "AUS", "Suva", "FJI"),
    ("Suva", "FJI", "Honolulu", "USA"),
    ("Panama City", "PAN", "Guayaquil", "ECU"),
    ("Ushuaia", "ARG", "Esperanza Base", "ATA"),
    ("Hobart", "AUS", "McMurdo Station", "ATA"),
    ("Christchurch", "NZL", "McMurdo Station", "ATA"),
    ("Cape Dezhnev", "RUS", "Cape Prince of Wales", "USA"),
    ("Reykjavík", "ISL", "Tórshavn", "FRO"),
    ("Tórshavn", "FRO", "Bergen", "NOR"),
    ("Reykjavík", "ISL", "Nuuk", "GRL"),
    ("Nuuk", "GRL", "St. John's", "CAN"),
    ("Vladivostok", "RUS", "Busan", "KOR"),
    ("Manila", "PHL", "Hong Kong", "HKG"),
    ("Darwin", "AUS", "Dili", "TLS"),
    ("Port Louis", "MUS", "Durban", "ZAF"),
    ("Victoria", "SYC", "Mombasa", "KEN"),
    ("Malé", "MDV", "Colombo", "LKA"),
    ("Praia", "CPV", "Dakar", "SEN"),
    ("Santa Cruz de Tenerife", "ESP", "Cádiz", "ESP"),
    ("Funchal", "PRT", "Lisbon", "PRT"),
    ("Ponta Delgada", "PRT", "Lisbon", "PRT"),
    ("Hamilton", "BMU", "New York", "USA"),
]


# Outlines Natural Earth draws as their own sovereign but which aren't UN members or observers.
NOT_UN = {"TWN", "KOS", "CYN", "SOL", "SAH", "KAS", "ATA", "FLK", "IOT"}


def fetch(name):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, name + ".geojson")
    if not os.path.exists(path):
        print("downloading", name)
        urllib.request.urlretrieve(NE + name + ".geojson", path)
    with open(path, encoding="utf8") as f:
        return json.load(f)["features"]


def km(lat1, lon1, lat2, lon2):
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 6371.0 * 2 * math.asin(min(1.0, math.sqrt(a)))


def along(a, b, n):
    """n+1 points from a to b (lat, lon), straight in degrees, the way the map draws a road."""
    lat1, lon1 = a
    lat2, lon2 = b
    if lon2 - lon1 > 180:
        lon2 -= 360
    elif lon1 - lon2 > 180:
        lon2 += 360
    t = np.linspace(0, 1, n + 1)
    lon = lon1 + (lon2 - lon1) * t
    lon = (lon + 180) % 360 - 180
    return lat1 + (lat2 - lat1) * t, lon


def main():
    countries = fetch("ne_50m_admin_0_countries")
    places = fetch("ne_10m_populated_places")
    land = shapely.union_all([shape(f["geometry"]) for f in fetch("ne_50m_land")])
    shapely.prepare(land)

    # Countries.
    rows, geoms, isos = [], [], []
    for f in countries:
        p = f["properties"]
        iso = p["ADM0_A3"]
        # A state is a member or an observer of the UN (195 of them); everything else with its own outline
        # (dependencies, partly recognised lands, Antarctica) is a land: it stamps the passport all the same.
        kind = "S" if p["SOVEREIGNT"] == p["ADMIN"] and iso not in NOT_UN or iso in ("PSX", "ISR") else "T"
        g = shape(f["geometry"])
        # Where the name goes on the map: inside the biggest piece.
        big = max(g.geoms, key=lambda q: q.area) if g.geom_type == "MultiPolygon" else g
        label = big.representative_point()
        rows.append((iso, p["NAME"], p["NAME_RU"] or p["NAME"], p["CONTINENT"], kind,
                     f"{label.y:.2f}", f"{label.x:.2f}", f"{big.area:.1f}"))
        geoms.append(g)
        isos.append(iso)
    if "ATA" not in isos:
        sys.exit("no Antarctica")
    print("states", sum(1 for r in rows if r[4] == "S"))
    tree = STRtree(geoms)
    iso_index = {iso: i for i, iso in enumerate(isos)}

    def country_of(lats, lons):
        """The country under each point, or '' at sea."""
        pts = shapely.points(lons, lats)
        out = np.full(len(lats), "", dtype=object)
        hit_pt, hit_g = tree.query(pts, predicate="within")
        out[hit_pt] = np.array(isos, dtype=object)[hit_g]
        return out

    # Towns: big enough to sleep in, every capital, the stations of Antarctica, and the capes.
    towns = []
    for f in places:
        p = f["properties"]
        cls = p["FEATURECLA"]
        capital = cls.startswith("Admin-0 capital")
        station = cls == "Scientific station"
        if not (p["POP_MAX"] >= 30000 or capital or cls.startswith("Admin-1") or cls.startswith("Admin-0") or station):
            continue
        lat, lon = p["LATITUDE"], p["LONGITUDE"]
        flags = ("C" if capital else "") + ("S" if station else "")
        # English names as an English reader knows them (Copenhagen, not København); NAME stays the key
        # the hand-written lanes are matched on.
        en = " ".join((p["NAME_EN"] or p["NAME"]).split())
        towns.append(dict(key=p["NAME"], en=en, ru=" ".join((p["NAME_RU"] or en).split()), iso=p["ADM0_A3"], lat=lat, lon=lon,
                          pop=max(0, int(p["POP_MAX"])), tz=p["TIMEZONE"] or "", wd=p["WIKIDATAID"] or "",
                          flags=flags))
    for en, ru, lat, lon, iso in CAPES:
        towns.append(dict(key=en, en=en, ru=ru, iso=iso, lat=lat, lon=lon, pop=0, tz="", wd="", flags="X"))

    # The country is taken from the map itself, so a town's stamp matches the outline it stands in.
    lat = np.array([t["lat"] for t in towns])
    lon = np.array([t["lon"] for t in towns])
    where = country_of(lat, lon)
    near_land = shapely.contains_xy(land.buffer(0.08), lon, lat)
    keep = []
    for t, c, ok in zip(towns, where, near_land):
        if not ok and "X" not in t["flags"]:
            continue
        # A microstate's capital may be smaller than its outline's precision: trust the data there.
        if c and not ("C" in t["flags"] and t["iso"] in iso_index):
            t["iso"] = c
        if t["iso"] not in iso_index:
            continue
        keep.append(t)
    # Two towns on the same spot (twin cities in the data) would be a road of zero length.
    seen, towns = set(), []
    for t in sorted(keep, key=lambda t: -t["pop"] - (10**9 if t["flags"] else 0)):
        k = (round(t["lat"], 2), round(t["lon"], 2))
        if k in seen:
            continue
        seen.add(k)
        towns.append(t)
    # Every outline gets somewhere to land, even an island with no town in the data.
    have = {t["iso"] for t in towns}
    for r in rows:
        if r[0] not in have:
            towns.append(dict(key=r[1], en=r[1], ru=r[2], iso=r[0], lat=float(r[5]), lon=float(r[6]), pop=0, tz="", wd="",
                              flags="L"))
    towns.sort(key=lambda t: (t["iso"], -t["pop"], t["en"]))
    # A time zone for the capes and anyone missing one: the nearest town's.
    for t in towns:
        if not t["tz"]:
            near = min((o for o in towns if o["tz"]), key=lambda o: km(t["lat"], t["lon"], o["lat"], o["lon"]))
            t["tz"] = near["tz"]
    n = len(towns)
    print("towns", n)

    # Ports: towns by the sea. The coast is the land's edge; lakes don't count, near enough.
    coast = land.boundary
    shapely.prepare(coast)
    lat = np.array([t["lat"] for t in towns])
    lon = np.array([t["lon"] for t in towns])
    port = shapely.dwithin(shapely.points(lon, lat), coast, 0.15)
    for t, p in zip(towns, port):
        if p:
            t["flags"] += "P"

    # Neighbours on a unit sphere.
    xyz = np.stack([np.cos(np.radians(lat)) * np.cos(np.radians(lon)),
                    np.cos(np.radians(lat)) * np.sin(np.radians(lon)),
                    np.sin(np.radians(lat))], axis=1)

    def nearest(i, k, mask=None):
        d = 6371.0 * np.arccos(np.clip(xyz @ xyz[i], -1, 1))
        d[i] = np.inf
        if mask is not None:
            d = np.where(mask, d, np.inf)
        idx = np.argpartition(d, min(k, n - 1))[:k]
        return sorted(((float(d[j]), int(j)) for j in idx if np.isfinite(d[j])))

    edges = {}

    def road(i, j, limit_wet=WET_MAX):
        a, b = towns[i], towns[j]
        d = km(a["lat"], a["lon"], b["lat"], b["lon"])
        steps = max(2, int(d / SAMPLE))
        la, lo = along((a["lat"], a["lon"]), (b["lat"], b["lon"]), steps)
        dry = shapely.contains_xy(land, lo, la)
        # A harbour town's point often sits on the water's edge: the first and last few km don't count.
        ends = int(8 / (d / steps)) + 1
        wet = (~dry[ends:len(dry) - ends]).sum() * d / steps if len(dry) > 2 * ends else 0.0
        if wet > limit_wet:
            return None
        return d, la, lo

    # Roads: nearest towns that a straight road on land can reach.
    candidates = []
    for i in range(n):
        for d, j in nearest(i, NEIGHBOURS):
            if d > ROAD_MAX:
                break
            key = (min(i, j), max(i, j))
            if key not in edges:
                edges[key] = None
                candidates.append(key)
    print("road candidates", len(candidates))
    roads = {}
    for key in candidates:
        r = road(*key)
        if r:
            roads[key] = r
    print("roads", len(roads))

    def components(es):
        parent = list(range(n))

        def find(x):
            while parent[x] != x:
                parent[x] = parent[parent[x]]
                x = parent[x]
            return x
        for i, j in es:
            parent[find(i)] = find(j)
        return [find(i) for i in range(n)]

    by_name = defaultdict(list)
    for i, t in enumerate(towns):
        by_name[t["key"]].append(i)
    by_en = {(t["en"], t["iso"]): i for i, t in enumerate(towns)}
    for a, b in TREKS:
        i, j = by_en[a], by_en[b]
        roads[(min(i, j), max(i, j))] = road(i, j, limit_wet=1e9)

    # Join pieces of one landmass that the nearest-neighbour roads left apart.
    for _ in range(6):
        comp = components(roads)
        groups = defaultdict(list)
        for i, c in enumerate(comp):
            groups[c].append(i)
        added = 0
        for c, members in groups.items():
            best = None
            # A lone town across a bay or a short bridge (Doha, Abu Dhabi, Xiamen) may cross a little more water.
            wet = WET_MAX if len(members) > 3 else 10.0
            for i in members:
                for d, j in nearest(i, 30, np.array(comp) != c):
                    if d > 900:
                        break
                    if best and d >= best[0]:
                        break
                    r = road(i, j, limit_wet=wet)
                    if r:
                        best = (d, i, j, r)
                        break
            if best:
                d, i, j, r = best
                roads[(min(i, j), max(i, j))] = r
                added += 1
        print("joined", added)
        if not added:
            break

    # Sea lanes: every port to the nearest ports of other landmasses, over open water.
    comp = components(roads)
    ports = np.array(["P" in t["flags"] for t in towns])
    lanes = {}

    def sea(i, j, open_water=0.85):
        a, b = towns[i], towns[j]
        d = km(a["lat"], a["lon"], b["lat"], b["lon"])
        steps = max(4, int(d / 10))
        la, lo = along((a["lat"], a["lon"]), (b["lat"], b["lon"]), steps)
        dry = shapely.contains_xy(land, lo[1:-1], la[1:-1])
        if len(dry) and (~dry).mean() < open_water:
            return None
        return d

    for i in range(n):
        if not ports[i]:
            continue
        found = 0
        for d, j in nearest(i, 40, ports & (np.array(comp) != comp[i])):
            if d > 1200 or found >= 2:
                break
            s = sea(i, j)
            if s:
                lanes[(min(i, j), max(i, j))] = s
                found += 1
    print("sea lanes", len(lanes))

    for a, ia, b, ib in LANES:
        try:
            i = next(k for k in by_name[a] if towns[k]["iso"] == ia)
            j = next(k for k in by_name[b] if towns[k]["iso"] == ib)
        except StopIteration:
            sys.exit(f"lane end not found: {a} {ia} / {b} {ib}: "
                     f"{[towns[k]['iso'] for k in by_name[a]]} {[towns[k]['iso'] for k in by_name[b]]}")
        lanes[(min(i, j), max(i, j))] = km(towns[i]["lat"], towns[i]["lon"], towns[j]["lat"], towns[j]["lon"])

    # Whatever is still cut off (a lone island town) gets a boat to the nearest port elsewhere.
    for _ in range(10):
        comp = components(list(roads) + list(lanes))
        main_c = max(set(comp), key=comp.count)
        loose = defaultdict(list)
        for i, c in enumerate(comp):
            if c != main_c:
                loose[c].append(i)
        if not loose:
            break
        for c, members in loose.items():
            best = None
            for i in members:
                for d, j in nearest(i, 5, np.array(comp) != c):
                    if not best or d < best[0]:
                        best = (d, i, j)
                    break
            d, i, j = best
            lanes[(min(i, j), max(i, j))] = d
            towns[i]["flags"] += "" if "P" in towns[i]["flags"] else "P"
            towns[j]["flags"] += "" if "P" in towns[j]["flags"] else "P"
        print("loose pieces boated", len(loose))
    comp = components(list(roads) + list(lanes))
    assert len(set(comp)) == 1, "the world is not one piece"

    # The borders along each road: where the country underfoot changes, as a fraction of the road.
    keys = list(roads)
    all_la = np.concatenate([roads[k][1] for k in keys])
    all_lo = np.concatenate([roads[k][2] for k in keys])
    under = country_of(all_la, all_lo)
    pos = 0
    out_edges = []
    for k in keys:
        d, la, lo = roads[k]
        seq = under[pos:pos + len(la)]
        pos += len(la)
        i, j = k
        last = towns[i]["iso"]
        cross = []
        for s in range(1, len(seq)):
            c = seq[s]
            if c and c != last:
                cross.append(f"{s / (len(seq) - 1):.3f}:{c}")
                last = c
        if last != towns[j]["iso"]:
            cross.append(f"1.000:{towns[j]['iso']}")
        out_edges.append((i, j, round(d * ROAD), "L", ",".join(cross)))
    for (i, j), d in lanes.items():
        if (i, j) in roads:
            continue
        out_edges.append((i, j, round(d * 1.1), "S", ""))
    out_edges.sort()

    # Write.
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "countries.tsv"), "w", encoding="utf8", newline="\n") as f:
        for r in sorted(rows):
            f.write("\t".join(r) + "\n")
    with open(os.path.join(OUT, "towns.tsv"), "w", encoding="utf8", newline="\n") as f:
        for t in towns:
            f.write("\t".join([t["en"], t["ru"], t["iso"], f"{t['lat']:.4f}", f"{t['lon']:.4f}", str(t["pop"]),
                               t["tz"], t["wd"], t["flags"]]) + "\n")
    with open(os.path.join(OUT, "edges.tsv"), "w", encoding="utf8", newline="\n") as f:
        for i, j, d, mode, cross in out_edges:
            f.write(f"{i}\t{j}\t{d}\t{mode}\t{cross}\n")

    # Outlines: per country, its rings as hundredths of a degree (big-endian, for DataInputStream).
    with open(os.path.join(OUT, "shapes.bin"), "wb") as f:
        order = sorted(range(len(isos)), key=lambda i: isos[i])
        f.write(struct.pack(">H", len(order)))
        for ci in order:
            g = geoms[ci].simplify(0.03, preserve_topology=True)
            polys = list(g.geoms) if g.geom_type == "MultiPolygon" else [g]
            rings = []
            for p in polys:
                if p.area < 0.004:
                    continue
                rings.append(np.array(p.exterior.coords))
            f.write(isos[ci].encode("ascii"))
            f.write(struct.pack(">H", len(rings)))
            for r in rings:
                q = np.round(r * 100).astype(int)
                f.write(struct.pack(">H", len(q)))
                for x, y in q:
                    f.write(struct.pack(">hh", int(x), int(y)))
    sizes = {name: os.path.getsize(os.path.join(OUT, name)) for name in os.listdir(OUT)}
    print("edges", len(out_edges), sizes)


if __name__ == "__main__":
    main()
