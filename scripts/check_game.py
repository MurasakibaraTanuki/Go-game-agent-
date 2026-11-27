"""Exercise the real local engine, then measure a full Human SL self-play game."""
import asyncio
import json
import statistics
import time

from gomasters.game import Game, RejectedMove, options


async def verify(args):
    game = Game(args)
    await game.start()
    try:
        for move in ("B1", "A1", "A2"):
            await game.play(move)
        assert game.board[-1][:2] == ".X" and game.board[-2][0] == "X"
        for move in ("A1", "B1"):  # suicide and occupied intersection
            try:
                await game.play(move)
            except RejectedMove:
                continue
            raise AssertionError(f"Accepted illegal move {move}")
        assert len(game.moves) == 3
    finally:
        await game.close()

    game = Game(args)
    await game.start()
    try:
        for move in ("B2", "C2", "D2", "B3", "C1", "D3", "T19", "C4", "C3"):
            await game.play(move)
        try:
            await game.play("C2")
        except RejectedMove:
            pass
        else:
            raise AssertionError("Accepted immediate ko recapture")
    finally:
        await game.close()

    game = Game(args)
    start = time.monotonic()
    await game.start()
    startup = time.monotonic() - start
    timings = []
    try:
        for _ in range(800):
            start = time.monotonic()
            await game.play(await game.choose())
            timings.append(time.monotonic() - start)
            if game.result is not None:
                break
        if not game.result:
            raise RuntimeError("Game did not end within 800 moves")
        print(json.dumps({
            "seed": args.seed, "rank": args.rank, "moves": len(timings),
            "last_moves": game.moves[-2:], "estimate": game.result,
            "startup_s": startup, "median_move_s": statistics.median(timings),
            "p95_move_s": statistics.quantiles(timings, n=100)[94],
        }, indent=2))
    finally:
        await game.close()


if __name__ == "__main__":
    asyncio.run(verify(options(__doc__).parse_args()))
