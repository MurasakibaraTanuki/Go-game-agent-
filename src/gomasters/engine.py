"""Single-position KataGo JSON analysis smoke probe; not a complete game."""
import argparse
import json
import re
import subprocess
from .policy import sample, move_name

def query(rank, size=19):
    if not re.fullmatch(r'(?:[1-9]|1[0-9]|20)k|[1-9]d', rank):
        raise ValueError('Use a rank from 20k to 9d')
    if size not in (9, 13, 19):
        raise ValueError('Unsupported size')
    return {'id':'probe','moves':[], 'rules':'chinese','komi':7.5,
            'boardXSize':size,'boardYSize':size,'maxVisits':1,'includePolicy':True,
            'overrideSettings':{'humanSLProfile':f'rank_{rank}','ignorePreRootHistory':False}}

def main():
    p = argparse.ArgumentParser(description=__doc__)
    for field in ('exe','config','model','human-model'):
        p.add_argument('--'+field, required=True)
    p.add_argument('--rank', default='5k')
    p.add_argument('--size', type=int, choices=[9,13,19], default=19)
    p.add_argument('--seed', type=int, default=7)
    p.add_argument('--timeout', type=float, default=180)
    a = p.parse_args()
    q = query(a.rank, a.size)
    cmd = [a.exe,'analysis','-config',a.config,'-model',a.model,'-human-model',a.human_model]
    result = subprocess.run(cmd, input=json.dumps(q)+'\n', text=True,
                            capture_output=True, timeout=a.timeout, check=True)
    for line in result.stdout.splitlines():
        try:
            response = json.loads(line)
        except json.JSONDecodeError:
            continue
        if response.get('id') != 'probe':
            continue
        if 'error' in response:
            raise RuntimeError(response['error'])
        policy = response.get('humanPolicy')
        if not isinstance(policy, list) or len(policy) != a.size**2+1:
            raise RuntimeError('No compatible humanPolicy; check engine and network')
        print(move_name(sample(policy, seed=a.seed), a.size))
        return
    raise RuntimeError('No matching analysis response; check config and engine logs: '+result.stderr[-1000:])

if __name__ == '__main__':
    main()
