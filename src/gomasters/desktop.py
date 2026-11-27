"""Play a local game against KataGo Human SL."""
import asyncio
import math
import random
import time
import tkinter as tk
from tkinter import ttk

from .game import Game, RejectedMove, SIZE, options, point_index
from .policy import move_name

DARK = "#24251f"
WOOD = "#dcb477"
INK = "#483722"


class Table:
    def __init__(self, root, args):
        self.root = root
        self.args = args
        self.game = None
        self.task = None
        self.sessions = set()
        self.inputs = None
        self.focus = False
        self.closed = False
        self.active = None
        self.since = time.monotonic()
        self.elapsed = {"B": 0., "W": 0.}
        self.ready = False
        self.last = None
        self.status = tk.StringVar(value="Загрузка KataGo…")
        self.delay = tk.BooleanVar(value=args.delay > 0)
        root.title("Go Masters AI")
        root.geometry("780x840")
        root.minsize(360, 420)
        root.configure(bg=DARK)
        self.clocks = tk.Label(root, bg=DARK, fg="#f5efdd", font=("TkDefaultFont", 16))
        self.clocks.pack(fill="x", pady=6)
        self.clocks.bind("<Button-1>", lambda _: self.toggle_focus())
        self.controls = ttk.Frame(root, padding=5)
        self.controls.pack(side="bottom", fill="x")
        self.pass_button = ttk.Button(self.controls, text="Пас", command=lambda: self.submit("pass"))
        self.pass_button.pack(side="left")
        ttk.Button(self.controls, text="Новая партия", command=self.restart).pack(side="left")
        ttk.Button(self.controls, text="Фокус · F11", command=self.toggle_focus).pack(side="left")
        ttk.Checkbutton(self.controls, text="Пауза бота", variable=self.delay).pack(side="right")
        self.info = tk.Label(root, textvariable=self.status, bg=DARK, fg="#ede5d7")
        self.info.pack(side="bottom", fill="x", pady=3)
        self.canvas = tk.Canvas(root, bg=DARK, highlightthickness=0)
        self.canvas.pack(fill="both", expand=True)
        self.canvas.bind("<Configure>", lambda _: self.draw())
        self.canvas.bind("<Button-1>", self.click)
        root.bind("<F11>", lambda _: self.toggle_focus())
        root.bind("<Escape>", lambda _: self.focus and self.toggle_focus())
        root.protocol("WM_DELETE_WINDOW", self.close)
        self.restart()

    def clock(self, color):
        now = time.monotonic()
        if self.active:
            self.elapsed[self.active] += now - self.since
        self.active = color
        self.since = now

    def tick(self):
        now = time.monotonic()
        times = []
        for side, label in (("B", "●"), ("W", "○")):
            sec = int(self.elapsed[side] + (now - self.since if self.active == side else 0))
            times.append(f"{label}  {sec // 60:02d}:{sec % 60:02d}")
        self.clocks.configure(text="       ".join(times))

    def toggle_focus(self):
        self.focus = not self.focus
        self.root.attributes("-fullscreen", self.focus)
        if self.focus:
            self.controls.pack_forget()
            self.info.pack_forget()
        else:
            self.controls.pack(side="bottom", fill="x", before=self.canvas)
            self.info.pack(side="bottom", fill="x", before=self.canvas)

    def geometry(self):
        w, h = self.canvas.winfo_width(), self.canvas.winfo_height()
        dim = min(w, h)
        step = dim / (SIZE - 1 + 1.2)
        return (w - dim) / 2 + .6 * step, (h - dim) / 2 + .6 * step, step

    def draw(self):
        c = self.canvas
        c.delete("all")
        x, y, step = self.geometry()
        end = (SIZE - 1) * step
        pad = .6 * step
        c.create_rectangle(x-pad, y-pad, x+end+pad, y+end+pad, fill=WOOD, outline="")
        for i in range(SIZE):
            v = i * step
            c.create_line(x+v, y, x+v, y+end, fill=INK)
            c.create_line(x, y+v, x+end, y+v, fill=INK)
        def circle(cx, cy, r, **kw):
            return c.create_oval(cx-r, cy-r, cx+r, cy+r, **kw)
        for row in (3, 9, 15):
            for col in (3, 9, 15):
                circle(x+col*step, y+row*step, .085*step, fill=INK, outline="")
        if not self.game:
            return
        for row, cells in enumerate(self.game.board):
            for col, stone in enumerate(cells):
                if stone == ".":
                    continue
                cx, cy = x+col*step, y+row*step
                radius = .47*step
                circle(cx+1, cy+2, radius, fill="#997647", outline="")
                fill, edge = ("#282927", "#111210") if stone == "X" else ("#f5f1e5", "#b4afa0")
                circle(cx, cy, radius, fill=fill, outline=edge)
                if self.last == row*SIZE+col:
                    circle(cx, cy, .19*step, outline="#e3dece" if stone == "X" else "#49483e", width=2)

    def click(self, event):
        x, y, step = self.geometry()
        col, row = round((event.x-x)/step), round((event.y-y)/step)
        if 0 <= row < SIZE and 0 <= col < SIZE:
            self.submit(move_name(row*SIZE+col, SIZE))

    def stop_task(self):
        if self.task and not self.task.done():
            self.task.cancel()

    def close(self):
        self.closed = True
        self.stop_task()

    def restart(self):
        self.stop_task()
        self.game = Game(self.args)
        self.ready = False
        self.last = None
        self.active = None
        self.elapsed = {"B": 0., "W": 0.}
        self.pass_button.state(["disabled"])
        self.status.set("Загрузка KataGo…")
        self.draw()
        self.inputs = asyncio.Queue(maxsize=1)
        self.task = asyncio.create_task(self.session(self.game, self.inputs))
        self.sessions.add(self.task)
        self.task.add_done_callback(self.sessions.discard)

    def submit(self, move):
        if self.ready:
            self.ready = False
            self.pass_button.state(["disabled"])
            self.inputs.put_nowait(move)

    async def session(self, game, inputs):
        try:
            await game.start()
            while game.result is None:
                self.clock(game.turn)
                if game.turn == self.args.color:
                    passed = game.moves and game.moves[-1][1] == "pass"
                    self.status.set("Бот пасовал. Ваш ход." if passed else "Ваш ход")
                    while True:
                        self.ready = True
                        self.pass_button.state(["!disabled"])
                        move = await inputs.get()
                        try:
                            await game.play(move)
                            break
                        except RejectedMove:
                            self.status.set("Недопустимый ход. Выберите другой пункт.")
                else:
                    self.status.set("Ход бота")
                    before = time.monotonic()
                    move = await game.choose()
                    if self.delay.get() and self.args.delay:
                        wait = random.uniform(.5 * self.args.delay, self.args.delay)
                        await asyncio.sleep(max(0, wait - (time.monotonic() - before)))
                    await game.play(move)
                self.last = point_index(move)
                self.draw()
            self.clock(None)
            if self.focus:
                self.toggle_focus()
            self.status.set(f"Партия окончена. Оценка KataGo: {game.result}")
        except (OSError, RuntimeError, ValueError) as exc:
            self.failed(str(exc))
        finally:
            await game.close()

    def failed(self, reason):
        self.clock(None)
        if self.focus:
            self.toggle_focus()
        self.status.set(f"Ошибка: {reason}")


async def run(args):
    root = tk.Tk()
    table = Table(root, args)
    try:
        while not table.closed:
            table.tick()
            root.update()
            await asyncio.sleep(.03)
    finally:
        table.stop_task()
        if table.sessions:
            await asyncio.gather(*table.sessions, return_exceptions=True)
        root.destroy()


def main():
    parser = options(__doc__)
    parser.add_argument("--color", choices=["B", "W"], default="B", help="human stone color")
    parser.add_argument("--delay", type=float, default=1.2, help="maximum pacing delay in seconds; 0 = immediate")
    args = parser.parse_args()
    if not math.isfinite(args.delay) or args.delay < 0 or not math.isfinite(args.komi):
        parser.error("Invalid delay or komi")
    asyncio.run(run(args))


if __name__ == "__main__":
    main()
