import asyncio
import unittest

from gomasters.game import Game, board_rows, human_policy, point_index
from gomasters.policy import move_name


class ParserTests(unittest.TestCase):
    def test_policy_orientation_and_illegality(self):
        grid = ["0"] * 361
        grid[point_index("D4")] = "NAN"
        grid[point_index("T19")] = "0.75"
        reply = "policyPass .25\nwhiteWin .5\npolicy\n" + " ".join(grid)
        parsed = human_policy(reply)
        self.assertEqual(parsed[288], -1)
        self.assertEqual(parsed[18], .75)
        self.assertEqual(parsed[-1], .25)
        for i in range(362):
            self.assertEqual(point_index(move_name(i, 19)), i)

    def test_bad_policy(self):
        for text in ("policy 0.9 policyPass 0.1", "policyPass 1"):
            with self.assertRaises(RuntimeError):
                human_policy(text)

    def test_board_includes_numbered_stones(self):
        rows = [f"{19-i:2} " + " ".join("." * 19) for i in range(19)]
        rows[0] = "19 X1O2" + ". " * 17
        self.assertEqual(board_rows("\n".join(rows))[0], "XO" + "." * 17)
        with self.assertRaises(RuntimeError):
            board_rows("\n".join(rows[1:]))


class FramingTests(unittest.IsolatedAsyncioTestCase):
    async def test_crlf_and_consecutive_replies(self):
        class Pipe:
            stdout = asyncio.StreamReader()
        game = Game(type("Args", (), {"seed": 1})())
        game.process = Pipe()
        game.process.stdout.feed_data(b"=1 \r\n\r\n=2 first\r\nsecond\r\n\r\n")
        self.assertEqual((await game._read_reply()).strip(), b"=1")
        self.assertIn(b"second", await game._read_reply())
