"""Numerically stable reference sampler. KataGo uses -1 for illegal moves."""
import math
import random
from typing import Sequence

COLS = 'ABCDEFGHJKLMNOPQRST'

def move_name(index: int, size: int) -> str:
    if not 1 <= size <= len(COLS) or not 0 <= index <= size * size:
        raise ValueError('Invalid board size or policy index')
    if index == size * size:
        return 'pass'
    row, col = divmod(index, size)
    return f'{COLS[col]}{size-row}'

def probabilities(base: Sequence[float], residual: Sequence[float] | None = None,
                  weight: float = 0., temperature: float = 1., cap: float = 2.) -> list[float]:
    if residual is None:
        residual = [0.] * len(base)
    if len(base) != len(residual) or not base:
        raise ValueError('Policy and residual lengths must match and be nonempty')
    if not all(math.isfinite(v) for v in (weight, temperature, cap)) or weight < 0 or temperature <= 0 or cap < 0:
        raise ValueError('Invalid sampler parameters')
    if any(not math.isfinite(x) for x in (*base, *residual)):
        raise ValueError('Nonfinite policy or residual')
    logits = [(math.log(p) + weight * max(-cap, min(cap, d))) / temperature
              if p > 0 else -math.inf for p, d in zip(base, residual)]
    peak = max(logits)
    if peak == -math.inf:
        raise ValueError('No legal move with positive probability')
    weights = [math.exp(x-peak) for x in logits]
    total = sum(weights)
    return [x/total for x in weights]

def sample(base, residual=None, *, weight=0., temperature=1., cap=2., seed=None):
    p = probabilities(base, residual, weight, temperature, cap)
    return random.Random(seed).choices(range(len(p)), weights=p, k=1)[0]
