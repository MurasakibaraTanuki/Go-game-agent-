"""Prepare verified CPU files once, then launch the local desktop client."""
import argparse
import hashlib
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tempfile
from urllib.request import Request, urlopen
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "external" / "desktop"
MEDIA = "https://media.katagotraining.org/uploaded/networks/"
MAIN = "kata1-b10c128-s1141046784-d204142634.txt.gz"
HUMAN = "b18c384nbt-humanv0.bin.gz"
MODELS = [
    (MEDIA + "models/kata1/" + MAIN, "3d8a24697ba25fe4da39af4c2b6bd405907b0ad8295322f5a550fa2d8fe4a2f4"),
    (MEDIA + "models_extra/" + HUMAN, "637746e44f0efe00ad1245a50aa9bbf0716efe364c43965ead97bd6835d84ab5"),
]
WINDOWS = (
    "https://github.com/lightvector/KataGo/releases/download/v1.18.1/katago-v1.18.1-eigenavx2-windows-x64.zip",
    "0d62ffa41ee04dd89dd1b80fe45e306c837231cb1e2dccb4f5780d0ca7c313db",
)
LINUX = (
    "https://github.com/Slymaster/masame/releases/download/v0.8.1/Masame_0.8.1_amd64.deb",
    "99ee22b1faa8a61e2f757a82659599056662a5311d7ff3355b7ce06bc8cc431f",
)


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def fetch(url, expected):
    target = CACHE / url.rsplit("/", 1)[1]
    if target.exists() and digest(target) == expected:
        return target
    print(f"Downloading {target.name}", flush=True)
    with tempfile.TemporaryDirectory(dir=CACHE) as tmp:
        part = Path(tmp) / target.name
        request = Request(url, headers={"User-Agent": "GoMastersAI/0.1 (local desktop setup)"})
        with urlopen(request, timeout=90) as response, part.open("wb") as f:
            shutil.copyfileobj(response, f)
        if digest(part) != expected:
            raise ValueError(f"SHA-256 mismatch: {target.name}")
        os.replace(part, target)
    return target


def main():
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument("--setup", action="store_true", help="download and verify local assets, then exit")
    args, extra = parser.parse_known_args()
    system = platform.system()
    if system not in ("Windows", "Linux") or platform.machine().lower() not in ("amd64", "x86_64"):
        parser.error("This CPU kit requires Windows or Linux x64 with AVX2/FMA.")
    win = system == "Windows"
    engine = CACHE / ("windows/katago.exe" if win else "linux/usr/lib/Masame/resources/katago-linux/eigenavx2/katago")
    if args.setup:
        CACHE.mkdir(parents=True, exist_ok=True)
        package = fetch(*(WINDOWS if win else LINUX))
        for model in MODELS:
            fetch(*model)
        if win:
            # The digest above pins the entire upstream archive, including DLLs.
            with ZipFile(package) as z:
                z.extractall(engine.parent)
        else:
            subprocess.run(["dpkg-deb", "-x", package, CACHE / "linux"], check=True)
        subprocess.run([engine, "version"], check=True)
        return
    if not all(p.is_file() for p in (engine, CACHE / MAIN, CACHE / HUMAN)):
        parser.error("Run scripts/play.py --setup first")
    sys.path.insert(0, str(ROOT / "src"))
    from gomasters.desktop import main as desktop
    sys.argv[1:] = ["--exe", str(engine), "--config", str(ROOT / "configs/gtp-cpu.cfg"),
                    "--model", str(CACHE / MAIN), "--human-model", str(CACHE / HUMAN), *extra]
    desktop()


if __name__ == "__main__":
    main()
