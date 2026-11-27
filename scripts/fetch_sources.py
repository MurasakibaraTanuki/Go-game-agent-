"""Acquire selected upstream sources, with provenance. Never execute or extract them."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[1] / 'external'
ARCHIVES = {'cwi-sgf':'https://homepages.cwi.nl/~aeb/go/games/games.tgz'}
REPOS = {'katago':'lightvector/KataGo','katrain':'sanderland/katrain',
         'katadroid':'zhzy0077/KataDroid','sabaki':'SabakiHQ/Sabaki',
         'kaya':'kaya-go/kaya','igoki':'cmdrdats/igoki','izis':'wjchen-vlsi/izis_go_board_reng',
         'page':'yifangao112/PAGE'}

def sha256(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda:f.read(1024*1024), b''): h.update(block)
    return h.hexdigest()

def record(item):
    with (ROOT/'manifest.jsonl').open('a',encoding='utf-8') as f:
        f.write(json.dumps(dict(item, retrieved_at=datetime.now(timezone.utc).isoformat()))+'\n')

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--list',action='store_true'); p.add_argument('--get',nargs='+',choices=ARCHIVES,default=[])
    p.add_argument('--clone',nargs='+',choices=REPOS,default=[])
    p.add_argument('--acknowledge-terms',action='store_true')
    a=p.parse_args()
    if a.list:
        print(json.dumps({'archives':ARCHIVES,'repositories':REPOS},indent=2))
    if not a.get and not a.clone: return
    if not a.acknowledge_terms: p.error('Read docs/SOURCES.md and acknowledge source terms first')
    ROOT.mkdir(parents=True,exist_ok=True)
    for name in a.get:
        url=ARCHIVES[name]; dest=ROOT/(name+'.tgz'); partial=dest.with_suffix('.part')
        if not dest.exists():
            try:
                with urllib.request.urlopen(url,timeout=60) as r, partial.open('wb') as f:
                    shutil.copyfileobj(r,f)
                with partial.open('rb') as f:
                    if f.read(2)!=b'\x1f\x8b': raise ValueError('Expected gzip file')
                partial.replace(dest)
            finally:
                partial.unlink(missing_ok=True)
        record({'source':name,'url':url,'path':dest.name,'sha256':sha256(dest),'bytes':dest.stat().st_size})
        print(dest)
    for name in a.clone:
        url='https://github.com/'+REPOS[name]+'.git'; dest=ROOT/name
        if not dest.exists(): subprocess.run(['git','clone',url,str(dest)],check=True)
        actual=subprocess.check_output(['git','-C',str(dest),'remote','get-url','origin'],text=True).strip()
        if actual!=url: raise ValueError('Existing checkout has unexpected origin: '+actual)
        commit=subprocess.check_output(['git','-C',str(dest),'rev-parse','HEAD'],text=True).strip()
        record({'source':name,'url':url,'path':dest.name,'commit':commit})
        print(name,commit)

if __name__=='__main__': main()
