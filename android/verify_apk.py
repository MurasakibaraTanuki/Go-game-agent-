"""Verify that a Go Masters APK contains its complete offline runtime."""
import argparse
import hashlib
import sys
from pathlib import Path
from zipfile import BadZipFile, ZIP_STORED, ZipFile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from play import MODELS


def verify(path, require_x86_64=False):
    with ZipFile(path) as apk:
        names = set(apk.namelist())
        required = {
            "AndroidManifest.xml", "classes.dex", "assets/game.cfg",
            "assets/analysis.cfg", "assets/notices/KataGo-networks.txt",
        }
        engines = {"lib/arm64-v8a/libkatago.so": 183, "lib/x86_64/libkatago.so": 62}
        arm64, x86_64 = engines
        required.add(arm64)
        if require_x86_64:
            required.add(x86_64)
        models = {
            "assets/" + url.rsplit("/", 1)[-1] + ".model": checksum
            for url, checksum in MODELS
        }
        missing = sorted((required | models.keys()) - names)
        if missing:
            raise ValueError("Missing APK entries: " + ", ".join(missing))

        for entry, machine in engines.items():
            if entry in names:
                with apk.open(entry) as engine:
                    header = engine.read(20)
                if header[:6] != b"\x7fELF\x02\x01" or int.from_bytes(header[18:20], "little") != machine:
                    raise ValueError(f"Unexpected native engine architecture: {entry}")

        for entry, expected in models.items():
            if apk.getinfo(entry).compress_type != ZIP_STORED:
                raise ValueError(f"Model must be stored as its original gzip archive: {entry}")
            checksum = hashlib.sha256()
            with apk.open(entry) as model:
                for block in iter(lambda: model.read(1024 * 1024), b""):
                    checksum.update(block)
            if checksum.hexdigest() != expected:
                raise ValueError(f"Model SHA-256 mismatch: {entry}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--require-x86-64", action="store_true")
    args = parser.parse_args()
    try:
        verify(args.apk, args.require_x86_64)
    except (OSError, ValueError, BadZipFile) as error:
        parser.exit(1, f"{args.apk}: {error}\n")
    print(f"Verified offline APK: {args.apk}")


if __name__ == "__main__":
    main()
