"""A local 19x19 game backed by one KataGo GTP process."""
import argparse
import asyncio
import math
import random
import re
from contextlib import suppress

from .engine import query
from .policy import COLS, move_name, probabilities

SIZE = 19


class RejectedMove(ValueError):
    """KataGo rejected the requested placement."""


def point_index(vertex):
    if vertex == "pass":
        return SIZE * SIZE
    match = re.fullmatch(r"([A-HJ-T])([1-9]|1[0-9])", vertex)
    if not match:
        raise ValueError(f"Invalid coordinate: {vertex}")
    return (SIZE - int(match[2])) * SIZE + COLS.index(match[1])


def human_policy(reply):
    fields = reply.split()
    try:
        start = fields.index("policy") + 1
        values = [float(x) for x in fields[start:start + SIZE * SIZE]]
        if len(values) != SIZE * SIZE:
            raise ValueError("Incomplete grid")
        values.append(float(fields[fields.index("policyPass") + 1]))
    except (ValueError, IndexError) as exc:
        raise RuntimeError("Invalid KataGo human policy") from exc
    # GTP uses NAN for illegal intersections. Keep legal zeroes distinct.
    return [-1.0 if math.isnan(p) else p for p in values]


def board_rows(reply):
    rows = []
    for line in reply.splitlines():
        match = re.fullmatch(r"\s*(\d+)\s+([.XO\d\s]+)", line)
        if match:
            cells = "".join(re.findall("[.XO]", match[2]))
            if int(match[1]) != SIZE - len(rows) or len(cells) != SIZE:
                raise RuntimeError("Unexpected KataGo board row")
            rows.append(cells)
    if len(rows) != SIZE:
        raise RuntimeError("Missing KataGo board rows")
    return tuple(rows)


class Game:
    def __init__(self, options):
        self.options = options
        self.rng = random.Random(options.seed)
        self.process = None
        self.serial = 0
        self.moves = []
        self.board = tuple("." * SIZE for _ in range(SIZE))
        self.policy = None
        self.result = None

    @property
    def turn(self):
        return "W" if len(self.moves) % 2 else "B"

    async def start(self):
        o = self.options
        profile = query(o.rank)["overrideSettings"]["humanSLProfile"]
        self.process = await asyncio.create_subprocess_exec(
            o.exe, "gtp", "-config", o.config, "-model", o.model,
            "-human-model", o.human_model, stdin=asyncio.subprocess.PIPE,
            stdout=asyncio.subprocess.PIPE)
        try:
            await self.command("boardsize 19")
            await self.command("kata-set-rules chinese")
            await self.command(f"komi {o.komi}")
            await self.command(f"kata-set-param humanSLProfile {profile}")
        except BaseException:
            await self.close()
            raise

    async def close(self):
        if self.process is not None:
            if self.process.returncode is None:
                with suppress(ProcessLookupError):
                    self.process.kill()
            await self.process.wait()

    async def _read_reply(self):
        lines = []
        size = 0
        while True:
            line = await self.process.stdout.readline()
            if not line:
                raise RuntimeError("KataGo closed its output. See engine output.")
            size += len(line)
            if size > 65536:
                raise RuntimeError("Oversized KataGo reply")
            if not line.strip():
                if lines:
                    return b"".join(lines)
            else:
                lines.append(line)

    async def command(self, cmd):
        self.serial += 1
        marker = str(self.serial)
        self.process.stdin.write(f"{marker} {cmd}\n".encode("ascii"))
        try:
            packet = await asyncio.wait_for(self._read_reply(), 120)
        except (asyncio.IncompleteReadError, asyncio.LimitOverrunError, asyncio.TimeoutError) as exc:
            raise RuntimeError("KataGo stopped or timed out. See engine output.") from exc
        reply = packet.decode("utf-8").strip()
        head, _, body = reply.partition(" ")
        if head == "?" + marker and cmd.startswith("play "):
            raise RejectedMove(body)
        if head != "=" + marker:
            raise RuntimeError(f"KataGo {cmd.split()[0]}: {reply[:200]}")
        return body.strip()

    async def distribution(self):
        if self.policy is None:
            reply = await self.command("kata-raw-human-nn 0")
            self.policy = human_policy(reply)
        return self.policy

    async def choose(self):
        weights = probabilities(await self.distribution())
        return move_name(self.rng.choices(range(len(weights)), weights=weights)[0], SIZE)

    async def play(self, move):
        if self.result is not None:
            raise RejectedMove("Game over")
        index = point_index(move)
        # The raw policy marks rule-illegal moves even if GTP play is tolerant.
        if move != "pass" and (await self.distribution())[index] < 0:
            raise RejectedMove(move)
        color = self.turn
        await self.command(f"play {color} {move}")
        self.board = board_rows(await self.command("showboard"))
        self.moves.append((color, move))
        self.policy = None
        if len(self.moves) >= 2 and self.moves[-2][1] == move == "pass":
            self.result = await self.command("final_score")


def options(description):
    parser = argparse.ArgumentParser(description=description)
    for field in ("exe", "model", "human-model", "config"):
        parser.add_argument("--" + field, required=True)
    parser.add_argument("--rank", default="5k")
    parser.add_argument("--seed", type=int)
    parser.add_argument("--komi", type=float, default=7.5)
    return parser
