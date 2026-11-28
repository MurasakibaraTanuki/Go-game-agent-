"""Extract the offline runtime from a trusted Go Masters Android CI artifact."""
import argparse
import hashlib
import shutil
import sys
from pathlib import Path
from tempfile import TemporaryDirectory
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from play import MODELS, digest

ENGINES = [f"lib/{abi}/libkatago.so" for abi in ("arm64-v8a", "x86_64")]


def stage(apk_path, expected):
    if digest(apk_path) != expected.lower():
        raise ValueError("APK SHA-256 does not match the CI artifact")
    target = ROOT / "external" / "android-studio"
    target.parent.mkdir(parents=True, exist_ok=True)
    model_hashes = {f"assets/{url.rsplit('/', 1)[-1]}": sha for url, sha in MODELS}
    with TemporaryDirectory(dir=target.parent) as directory, ZipFile(apk_path) as apk:
        staging = Path(directory)
        notices = [
            name for name in apk.namelist()
            if name.startswith("assets/notices/") and not name.endswith("/")
        ]
        if not notices:
            raise ValueError("The APK is missing license notices")
        for name in [*model_hashes, *ENGINES, *notices]:
            if Path(name).name != name.rsplit("/", 1)[-1] or ".." in Path(name).parts:
                raise ValueError(f"Unexpected path in APK: {name}")
            if name in model_hashes:
                bundled_name = name + ".model"
                if bundled_name not in apk.namelist():
                    bundled_name = name
                model = staging / name
                model.parent.mkdir(parents=True, exist_ok=True)
                with apk.open(bundled_name) as source, model.open("wb") as dest:
                    shutil.copyfileobj(source, dest)
                if digest(model) != model_hashes[name]:
                    raise ValueError(f"Model SHA-256 mismatch: {name}")
            else:
                apk.extract(name, staging)
        shutil.copytree(staging, target, dirs_exist_ok=True)
    print(f"Offline runtime prepared in {target}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("checksums", type=Path, help="SHA256SUMS from the same CI artifact")
    args = parser.parse_args()
    tokens = args.checksums.read_text(encoding="ascii").split()
    if not tokens or len(tokens[0]) != hashlib.sha256().digest_size * 2:
        parser.error("SHA256SUMS must start with the APK SHA-256")
    stage(args.apk, tokens[0])


if __name__ == "__main__":
    main()
