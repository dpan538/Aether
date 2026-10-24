"""Package the user-selected provisional icon without changing its artwork."""
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parent
MASTER = ROOT / "app-icon.png"
EXPECTED_SHA256 = "00ed21739c8243aecc8b4c7d7b5d91361b34edc448464b8b2978bb4a7d53a48a"
ARCHIVED = ROOT.parent / "2026-10-24-spectral-globes/E1-chromatic-sphere-white-horizon.png"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def describe(path):
    with Image.open(path) as image:
        image.verify()
    with Image.open(path) as image:
        return {"file": path.relative_to(ROOT).as_posix(), "width": image.width,
                "height": image.height, "mode": image.mode,
                "bytes": path.stat().st_size, "sha256": sha256(path)}


def main():
    if sha256(MASTER) != EXPECTED_SHA256:
        raise ValueError("Selected original changed; review the selection before exporting")
    with Image.open(MASTER) as source, Image.open(ARCHIVED) as archived:
        if source.size != (1254, 1254) or source.mode != "RGB":
            raise ValueError("Unexpected selected image dimensions or color mode")
        same_pixels = (source.size == archived.size and
                       ImageChops.difference(source, archived.convert("RGB")).getbbox() is None)
        paths = [MASTER]
        for density, size in DENSITIES.items():
            target = ROOT / "android-legacy" / f"mipmap-{density}" / "ic_launcher.png"
            target.parent.mkdir(parents=True, exist_ok=True)
            source.resize((size, size), Image.Resampling.LANCZOS).save(target, optimize=True)
            paths.append(target)
        preview = ROOT / "preview-512.png"
        source.resize((512, 512), Image.Resampling.LANCZOS).save(preview, optimize=True)
        paths.append(preview)
    manifest = {
        "selectedDate": "2026-10-25", "status": "User-selected provisional icon; not final branding",
        "master": "app-icon.png", "origin": "Previously authorized imagegen study E1, reattached by user",
        "archivedE1": {"file": "../2026-10-24-spectral-globes/E1-chromatic-sphere-white-horizon.png",
                       "sha256": sha256(ARCHIVED), "identicalRGBPixels": same_pixels},
        "transform": "Full square image resized with Pillow Lanczos; no crop, redrawing or added effects",
        "files": [describe(path) for path in paths],
        "launcherValidation": "Pending Android task integration and device verification"
    }
    (ROOT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    print(f"Packaged {len(paths)} PNGs; original SHA-256 verified; archived E1 pixel match: {same_pixels}")


if __name__ == "__main__":
    main()
