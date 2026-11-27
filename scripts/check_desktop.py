"""Exercise the actual desktop controls with a display and local model files."""
import asyncio
import tkinter as tk

from gomasters.desktop import Table
from gomasters.game import options, point_index


async def check(args):
    args.color, args.delay = "B", 4
    root = tk.Tk()
    table = Table(root, args)

    async def wait_until(predicate):
        async def pump():
            while not predicate():
                root.update()
                await asyncio.sleep(.025)
        await asyncio.wait_for(pump(), 90)
        root.update()

    def click(vertex):
        row, col = divmod(point_index(vertex), 19)
        x, y, step = table.geometry()
        table.canvas.event_generate("<Button-1>", x=round(x + col*step), y=round(y + row*step))

    try:
        await wait_until(lambda: table.ready)
        click("D4")
        await wait_until(lambda: table.status.get() == "Ход бота")
        old = table.game
        table.restart()
        await wait_until(lambda: table.ready and old.process.returncode is not None)
        assert not table.game.moves
        table.delay.set(False)
        click("D4")
        await wait_until(lambda: table.ready)
        before = tuple(table.game.moves)
        assert len(before) == 2 and before[0] == ("B", "D4")
        click("D4")
        await wait_until(lambda: table.ready)
        assert tuple(table.game.moves) == before
        table.toggle_focus()
        root.update()
        assert not table.controls.winfo_ismapped() and not table.info.winfo_ismapped()
        table.clocks.event_generate("<Button-1>")
        root.update()
        assert table.controls.winfo_ismapped()
        table.pass_button.invoke()
        await wait_until(lambda: table.ready or table.game.result)
        assert ("B", "pass") in table.game.moves
    finally:
        table.close()
        await asyncio.gather(*table.sessions, return_exceptions=True)
        root.destroy()


if __name__ == "__main__":
    asyncio.run(check(options(__doc__).parse_args()))
