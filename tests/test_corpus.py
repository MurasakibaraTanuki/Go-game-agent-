import importlib.util
import unittest

if importlib.util.find_spec("sgfmill"):
    from sgfmill import sgf_grammar
    from scripts.index_sgf import record


@unittest.skipUnless(importlib.util.find_spec("sgfmill"), "install the corpus extra")
class CorpusTests(unittest.TestCase):
    def parse(self, data):
        return record(sgf_grammar.parse_sgf_collection(data)[0])

    def test_identity_mainline_pass_and_metadata(self):
        first = self.parse(b"(;PB[ Player  A ]KM[7.5]DT[2001];B[aa](;W[])(;W[ab]))")
        second = self.parse(b"(;PB[Another]KM[7.5];B[aa]C[note];W[])")
        self.assertEqual(first[0], second[0])
        self.assertEqual(first[1:4], ("player a", "", "2001"))
        self.assertEqual(first[-1][-2:], (361).to_bytes(2, "big"))
        self.assertNotEqual(first[0], self.parse(b"(;KM[6.5];B[aa];W[])")[0])

    def test_excludes_setup_and_nonstandard_turns(self):
        for src, expected in [(b"(;SZ[9];B[aa])", "other_size"),
                              (b"(;AB[aa];W[bb])", "setup_or_player_override"),
                              (b"(;B[aa];B[bb])", "out_of_turn")]:
            self.assertEqual(self.parse(src), expected)
