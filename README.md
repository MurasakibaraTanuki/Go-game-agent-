# Go Masters AI

Offline-first Go training and play with independently configurable opponent style and strength. Initial project scaffold, not a finished application. See [Russian project brief](docs/PLAN.ru.md), [architecture](docs/ARCHITECTURE.md), [data and research](docs/SOURCES.md) and [validation](docs/VALIDATION.md).

## Quick start (Python 3.10+)

```sh
python -m pip install -e .
python -m unittest discover -s tests -v
python -m gomasters.demo
```

The demo samples synthetic policy probabilities only. It does not play a full game. No dependencies are required for these tests. For real positions, install KataGo, a standard model, Human SL model and the matching upstream analysis config; then run:

```sh
python -m gomasters.engine --exe /path/to/katago --config /path/to/analysis_example.cfg --model /path/to/main.bin.gz --human-model /path/to/b18c384nbt-humanv0.bin.gz --rank 5k
```

The command requests the empty 19x19 board and samples a legal move from humanPolicy. Full integration has not been run in the preparation environment. Use `--help` for more options.

## Material acquisition (online once)

```sh
python scripts/fetch_sources.py --list
python scripts/fetch_sources.py --get cwi-sgf --acknowledge-terms
python scripts/fetch_sources.py --clone katago katrain katadroid sabaki kaya igoki izis --acknowledge-terms
```

Read the source register first. Downloads are cached under external/ (ignored by Git); upstream repositories retain their history and licenses. The script records SHA-256 for archives and exact Git commit hashes for clones. It does not automatically execute third-party code or extract archives. PAGE and model weights require manual selection/download from their official pages. Check each source's terms before redistributing. Never add restricted data or multi-GB weights to normal Git history.

## Repository

https://github.com/MurasakibaraTanuki/Go-game-agent-

Clone this repository for local development. The repository is public; do not commit private game histories or materials without redistribution permission.

## Status

Implemented: reference move-policy blending, deterministic synthetic demo, one-shot local engine probe, source retrieval script, unit tests, documentation. Planned: SGF ingestion, empirical style training, calibrated difficulty, playable desktop UI, native Android inference, device-specific board adapters. No real game records or model weights are bundled; network retrieval was unavailable during preparation.
