"""Index main lines of a tarred SGF collection without extracting any files."""
import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import tarfile
import tempfile
import unicodedata

from sgfmill import sgf, sgf_grammar


def player(root, prop):
    if not root.has_property(prop):
        return ""
    return " ".join(unicodedata.normalize("NFKC", root.get(prop)).casefold().split())


def record(tree):
    game = sgf.Sgf_game.from_coarse_game_tree(tree)
    root = game.get_root()
    if game.get_size() != 19:
        return "other_size"
    if root.has_property("HA") and root.get("HA") > 0:
        return "handicap"
    moves = bytearray()
    for node in game.get_main_sequence():
        if any(node.has_property(p) for p in ("AB", "AW", "AE", "PL")):
            return "setup_or_player_override"
        if node.has_property("B") and node.has_property("W"):
            return "invalid_move"
        color, point = node.get_move()
        if color is None:
            continue
        if color != ("w" if len(moves) // 2 % 2 else "b"):
            return "out_of_turn"
        index = 361 if point is None else point[0] * 19 + point[1]
        moves.extend(index.to_bytes(2, "big"))
    if not moves:
        return "empty"
    komi = root.get("KM") if root.has_property("KM") else None
    rules = root.get("RU").strip().casefold() if root.has_property("RU") else ""
    meta = json.dumps([19, komi, rules], allow_nan=False).encode()
    key = hashlib.sha256(meta + b"\0" + moves).hexdigest()
    date = root.get_raw("DT").decode("ascii", errors="replace") if root.has_property("DT") else ""
    return (key, player(root, "PB"), player(root, "PW"), date, komi, rules, bytes(moves))


def build(archive, db):
    db.execute("CREATE TABLE games (id TEXT PRIMARY KEY, black TEXT, white TEXT, sgf_date TEXT, komi REAL, rules TEXT, moves BLOB, member TEXT, tree_index INTEGER)")
    counts = Counter()
    errors = []
    with tarfile.open(archive, "r|gz") as stream:
        for item in stream:
            if not item.isfile() or not item.name.lower().endswith(".sgf"):
                continue
            counts["sgf_files"] += 1
            if item.size > 4_000_000:
                counts["oversized_files"] += 1
                continue
            try:
                trees = sgf_grammar.parse_sgf_collection(stream.extractfile(item).read())
            except ValueError as exc:
                counts["malformed_files"] += 1
                if len(errors) < 10:
                    errors.append([item.name, str(exc) or type(exc).__name__])
                continue
            for ordinal, tree in enumerate(trees):
                try:
                    row = record(tree)
                except (ValueError, KeyError, UnicodeError, LookupError) as exc:
                    counts["malformed_games"] += 1
                    if len(errors) < 10:
                        errors.append([item.name, str(exc) or type(exc).__name__])
                    continue
                if isinstance(row, str):
                    counts[row] += 1
                elif db.execute("INSERT OR IGNORE INTO games VALUES (?,?,?,?,?,?,?,?,?)", (*row, item.name, ordinal)).rowcount:
                    counts["unique_games"] += 1
                    counts["moves"] += len(row[-1]) // 2
                else:
                    counts["duplicates"] += 1
    counts["player_labels"] = db.execute(
        "SELECT count(*) FROM (SELECT black AS p FROM games UNION SELECT white FROM games) WHERE p != ''"
    ).fetchone()[0]
    return {"counts": dict(counts), "sample_errors": errors}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("database", type=Path)
    args = parser.parse_args()
    # Publish a complete rebuild atomically; preserve any previous index on failure.
    with tempfile.NamedTemporaryFile(dir=args.database.parent, delete=False) as tmp:
        name = tmp.name
    try:
        db = sqlite3.connect(name)
        try:
            report = build(args.archive, db)
            db.commit()
        finally:
            db.close()
        os.replace(name, args.database)
        print(json.dumps(report, ensure_ascii=False, indent=2))
    finally:
        Path(name).unlink(missing_ok=True)


if __name__ == "__main__":
    main()
