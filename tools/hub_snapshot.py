"""One-time snapshot of PokeMMO Hub's Pokedex data into the app's assets.

Source: https://pokemmohub.com/tools/pokedex/ (PokeMMO Hub). The Hub team said in their Discord that the
Pokedex data is free to use (2026-10-01). PokeMMO Hub is credited in the app description.

The Pokedex page ships its data as JSON inside its JavaScript chunks. This script finds those chunks, pulls out the
Pokemon and move tables, keeps only the fields the app needs (Gen 1-5 base forms, battle data), and writes:

    app/src/main/assets/pokemmo/species.json    battle data + Pokedex facts (EV yield, catch rate, evolutions, ...)
    app/src/main/assets/pokemmo/moves.json
    app/src/main/assets/pokemmo/locations.json  wild spawns per species, seasons merged
    app/src/main/assets/pokemmo/berries.json    berry growing data (Berries Helper)
    app/src/main/assets/pokemmo/items.json      item names/descriptions keyed by GTL item id (Items page)
    app/src/main/assets/pokemmo/egg_moves.json  egg move parent chains (Egg Moves Calculator)
    app/src/main/assets/pokemmo/breeding.json   IV breeding patterns (Breeding simulator)

Run from the project root:  python tools/hub_snapshot.py
"""
import json
import os
import re
import urllib.request

SITE = "https://pokemmohub.com"
PAGE = SITE + "/tools/pokedex/"
COMPONENT = "component---src-pages-tools-pokedex-js"
OUT_DIR = os.path.join("app", "src", "main", "assets", "pokemmo")
MAX_DEX = 649
BS = chr(92)  # backslash


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (PokeMMO Companion data snapshot)"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read().decode("utf8")


def js_unescape(raw):
    """Turns the body of a single-quoted JS string literal into the text it represents."""
    out, i, n = [], 0, len(raw)
    simple = {"n": "\n", "t": "\t", "r": "\r", "b": "\b", "f": "\f", "0": "\0"}
    while i < n:
        c = raw[i]
        if c == BS and i + 1 < n:
            d = raw[i + 1]
            if d == "x":
                out.append(chr(int(raw[i + 2:i + 4], 16)))
                i += 4
            elif d == "u":
                out.append(chr(int(raw[i + 2:i + 6], 16)))
                i += 6
            else:
                out.append(simple.get(d, d))
                i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def page_chunks(page=None, component=None):
    """URLs of the JS chunks a Hub page loads (default: the Pokedex)."""
    page = page or PAGE
    component = component or COMPONENT
    html = fetch(page)
    runtime = re.search(r'src="/(webpack-runtime-[^"]+\.js)"', html).group(1)
    app = re.search(r'src="/(app-[^"]+\.js)"', html).group(1)
    rt = fetch(f"{SITE}/{runtime}")
    maps = [dict(re.findall(r'(\d+):"([^"]+)"', m.group(0))) for m in re.finditer(r'\{(\d+:"[^"]+",?){5,}\}', rt)]
    names, hashes = maps[0], maps[1]
    ids = re.search(component + r'":\(\)=>Promise\.all\(\[([^\]]*)\]', fetch(f"{SITE}/{app}")).group(1)
    for cid in re.findall(r"\.e\((\d+)\)", ids):
        if cid in hashes:  # CSS-only chunks have no JS hash
            yield f"{SITE}/{names.get(cid, cid)}-{hashes[cid]}.js"


def json_tables(js):
    for m in re.finditer(r"\d+:function\(e\)\{e\.exports=JSON\.parse\('", js):
        raw = js[m.end():js.find("')}", m.end())]
        try:
            yield json.loads(js_unescape(raw), strict=False)
        except ValueError:
            pass


def dedupe(seq):
    out = []
    for x in seq:
        if x not in out:
            out.append(x)
    return out


SEASONS = ["Spring", "Summer", "Autumn", "Winter"]
GENDER_FEMALE = {0: 0, 31: 12.5, 63: 25, 127: 50, 191: 75, 254: 100}


def evolution_text(e):
    """Short, readable evolution condition, e.g. "Lv 16", "Fire Stone", "Trade holding Metal Coat"."""
    t, val, item = e["type"], e.get("val"), e.get("item_name", "").replace(" (EVO)", "")
    if t == "LEVEL":
        return f"Lv {val}"
    if t == "ITEM":
        return item or "Item"
    if t == "TRADE":
        return "Trade"
    if t == "TRADE_WITH_ITEM":
        return f"Trade holding {item}" if item else "Trade holding an item"
    if t.startswith("HAPPINESS"):
        return "Friendship" + {"HAPPINESS_DAY": " (day)", "HAPPINESS_NIGHT": " (night)"}.get(t, "")
    if t == "LEVEL_WITH_SKILL":
        return "Level up knowing a move"
    return t.replace("_", " ").capitalize() + (f" ({item})" if item else (f" {val}" if val else ""))


def locations_of(p):
    """Spawns merged across seasons: [region, place, method, minLv, maxLv, horde, morning, day, night, seasons]."""
    merged = {}
    for l in p["locations"]:
        horde = 5 if l["is_horde_5x"] else 3 if l["is_horde_3x"] else 0
        key = (l["region_name"], l["location_name_full"] or l["location_name"], l["type"], l["min_level"],
               l["max_level"], horde, l["rarity_morning"], l["rarity_day"], l["rarity_night"])
        merged.setdefault(key, set()).add(l["season"])
    out = []
    for key, seasons in merged.items():
        s = "" if "Any" in seasons or set(SEASONS) <= seasons else "/".join(x[:3] for x in SEASONS if x in seasons)
        out.append(list(key) + [s])
    return sorted(out, key=lambda r: (r[0], r[1], r[2], r[3]))


# Natural Gift type index -> type (game order, 9 is the unused "???" type)
GIFT_TYPES = ["NORMAL", "FIGHTING", "FLYING", "POISON", "GROUND", "ROCK", "BUG", "GHOST", "STEEL", "???",
              "FIRE", "WATER", "GRASS", "ELECTRIC", "PSYCHIC", "ICE", "DRAGON", "DARK"]


def js_string_after(js, marker):
    """Body of the single-quoted JSON.parse('...') string that starts right after [marker]."""
    start = js.index(marker) + len(marker)
    i = start
    while js[i] != "'":
        i += 2 if js[i] == BS else 1
    return json.loads(js_unescape(js[start:i]), strict=False)


def tool_chunks(page, component):
    return [fetch(u) for u in page_chunks(f"{SITE}/{page}/", component)]


def snapshot_tools(items_by_id):
    """Berries, item list, egg move chains and breeding patterns from the Hub tool pages."""
    out = {}
    # Item names/descriptions as the Items (GTL) page shows them; "_id" is the game item id used elsewhere.
    page = json.loads(fetch(f"{SITE}/page-data/items/page-data.json"))
    nodes = page["result"]["data"]["allPokemmo"]["nodes"]
    out["items.json"] = [{
        "id": n["item_id"], "hubId": n["_id"], "name": n["n"]["en"],
        "desc": " ".join((n.get("d") or {}).get("en", "").split()), "category": n["category"],
    } for n in nodes]
    desc_by_hub = {n["_id"]: " ".join((n.get("d") or {}).get("en", "").split()) for n in nodes}

    berries = None
    for js in tool_chunks("tools/berries", "component---src-pages-tools-berries-js"):
        for table in json_tables(js):
            if isinstance(table, list) and table and isinstance(table[0], dict) and "grow_time" in table[0]:
                berries = table
    assert berries, "berry table not found"
    out["berries.json"] = [{
        "id": b["item_id"],
        "name": items_by_id[b["item_id"]]["en_name"],
        "effect": desc_by_hub.get(b["item_id"], ""),
        "growHours": b["grow_time"], "witherHours": b["wither_time"],
        "harvestMin": b["min_harvest"], "harvestMax": b["max_harvest"],
        # Seeds needed per flavor (Plain seed = 1, Very seed = 2)
        "flavors": {k.split("_")[0]: b[k] for k in ("spicy_degree", "dry_degree", "sweet_degree", "bitter_degree", "sour_degree")},
        "giftType": GIFT_TYPES[b["nature_power_type"]], "giftPower": b["nature_power_power"],
    } for b in sorted(berries, key=lambda b: b["item_id"])]

    paths = None
    for js in tool_chunks("tools/egg-moves-calculator", "component---src-pages-tools-egg-moves-calculator-js"):
        if "JSON.parse('[[{" in js:
            k = js.index("JSON.parse('[[{")
            paths = js_string_after(js[k:], "JSON.parse('")
    assert paths, "egg move paths not found"
    # target id -> move id -> chains of [parent id, how] (how: level, or 101 special, 105 egg & item, 106 evolution,
    # 107 pre-evolution, 108 the parent needs it as an egg move too)
    chains = {}
    for p in paths:
        t, mv = p[0]["monster_id"], p[0]["move_id"]
        chains.setdefault(str(t), {}).setdefault(str(mv), []).append([[x["monster_id"], x["level"]] for x in p[1:]])
    out["egg_moves.json"] = chains

    patterns = None
    marker = "JSON.parse('" + '{"I":{"iv5"'
    for js in tool_chunks("tools/breeding", "component---src-pages-tools-breeding-js"):
        if marker in js:
            patterns = js_string_after(js[js.index(marker):], "JSON.parse('")
    assert patterns, "breeding patterns not found"
    # "nature": with an Everstone parent (index 0 = nature), "random": IVs only; rows from parents (1) up to the result.
    out["breeding.json"] = {"nature": patterns["I"], "random": patterns["M"]}
    return out


def main():
    pokemon = moves = catch_rates = items = None
    for url in page_chunks():
        for table in json_tables(fetch(url)):
            if isinstance(table, list) and table and isinstance(table[0], dict):
                if "exp_type" in table[0]:
                    pokemon = table
                elif "skill_damage_type" in table[0]:
                    moves = table
                elif set(table[0]) == {"id", "rate"}:
                    catch_rates = {r["id"]: r["rate"] for r in table}
                elif "en_name" in table[0] and "buy_pokeyen" in table[0]:
                    items = {i["id"]: i for i in table}
    assert pokemon and moves, "Pokemon or move table not found; the Hub page layout may have changed"

    species = []
    locations = {}
    pre = {e["id"]: p["id"] for p in pokemon if p["id"] <= MAX_DEX for e in p["evolutions"]}
    for p in sorted(pokemon, key=lambda p: p["id"]):
        if p["id"] > MAX_DEX:
            continue  # alternate forms (Deoxys, Rotom, ...) have ids above 649
        s = p["stats"]
        y = p["yields"]
        level_moves = sorted(
            ((m["level"], m["name"]) for m in p["moves"] if m["type"] == "level"), key=lambda lm: lm[0]
        )
        species.append({
            "id": p["id"],
            "name": p["name"],
            "types": dedupe(p["types"]),
            # HP, Atk, Def, SpA, SpD, Spe
            "stats": [s["hp"], s["attack"], s["defense"], s["sp_attack"], s["sp_defense"], s["speed"]],
            "abilities": dedupe(a["name"] for a in p["abilities"]),
            "levelMoves": [{"level": lv, "move": name} for lv, name in level_moves],
            "obtainable": p["obtainable"],
            "evYield": [y["ev_hp"], y["ev_attack"], y["ev_defense"], y["ev_sp_attack"], y["ev_sp_defense"], y["ev_speed"]],
            "expYield": y["exp"],
            # The catch calculator's table (PokeMMO values; differs for some legendaries), else the Pokedex's.
            "catchRate": (catch_rates or {}).get(p["id"], p["catch_rate"]),
            # Percent female; -1 = genderless
            "female": GENDER_FEMALE.get(p["gender_ratio"], round(p["gender_ratio"] / 2.56, 1)) if p["gender_ratio"] != 255 else -1,
            "eggGroups": [g.replace("_", " ").title() for g in p["egg_groups"]],
            "growth": p["exp_type_name"],
            "heightDm": p["height"],
            "weightHg": p["weight"],
            "heldItems": [h["name"] for h in p["held_items"]],
            "evolvesFrom": pre.get(p["id"], 0),
            "evolutions": [{"id": e["id"], "how": evolution_text(e)} for e in p["evolutions"] if e["id"] <= MAX_DEX],
            # Other ways to learn moves (move ids): TMs/HMs, tutors, egg moves
            "tmMoves": sorted({m["id"] for m in p["moves"] if m["type"].startswith("TM")}),
            "tutorMoves": sorted({m["id"] for m in p["moves"] if m["type"] == "TUTOR"}),
            "eggMoves": sorted({m["id"] for m in p["moves"] if m["type"].startswith("EGG")}),
        })
        if p["locations"]:
            locations[str(p["id"])] = locations_of(p)

    move_out = [{
        "id": m["id"],
        "name": m["name"],
        "type": m["type"],
        "category": m["skill_damage_type"],
        "power": m["base_power"],
        "accuracy": m["base_accuracy"],
        "pp": m["base_pp"],
        "priority": m["priority"],
        "target": m["target_type"],
    } for m in sorted(moves, key=lambda m: m["id"])]

    os.makedirs(OUT_DIR, exist_ok=True)
    tools = snapshot_tools(items)
    for name, data in [("species.json", species), ("moves.json", move_out), ("locations.json", locations)] + list(tools.items()):
        path = os.path.join(OUT_DIR, name)
        with open(path, "w", encoding="utf8") as f:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
        print(f"wrote {path}: {len(data)} entries, {os.path.getsize(path) // 1024} KB")


if __name__ == "__main__":
    main()
