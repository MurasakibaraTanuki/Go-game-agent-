import unittest
from gomasters.policy import probabilities, sample, move_name
from gomasters.engine import query

class PolicyTests(unittest.TestCase):
    def test_baseline(self):
        for a,b in zip(probabilities([.5,.3,.2,-1]), [.5,.3,.2,0]):
            self.assertAlmostEqual(a,b)
    def test_bias(self):
        p = probabilities([.5,.5,-1],[0,1,100],weight=1)
        self.assertGreater(p[1], p[0]); self.assertEqual(p[2],0)
        self.assertAlmostEqual(sum(p),1)
    def test_seed(self):
        self.assertEqual(sample([.4,.6],seed=42),sample([.4,.6],seed=42))
    def test_invalid(self):
        for args in [([-1,0],), ([1],[0,1]), ([float('nan')],), ([1],None,0,0)]:
            with self.assertRaises(ValueError): probabilities(*args)
    def test_coordinates(self):
        self.assertEqual(move_name(0,19),'A19'); self.assertEqual(move_name(360,19),'T1')
        self.assertEqual(move_name(8,19),'J19'); self.assertEqual(move_name(361,19),'pass')
    def test_query(self):
        self.assertEqual(query('5k')['overrideSettings']['humanSLProfile'],'rank_5k')
        with self.assertRaises(ValueError): query('25d')

if __name__ == '__main__': unittest.main()
