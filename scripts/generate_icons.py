#!/usr/bin/env python3
"""Regenerate committed launcher icons from the master PNG. Requires Pillow only for this step."""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "src/main/resources/icons/steamstone.png"
DESTINATION = ROOT / "packaging/icons"
WINDOWS_SIZES = (16, 24, 32, 48, 64, 128, 256)


def main():
    with Image.open(SOURCE) as source:
        if source.width != source.height or source.width < 1024:
            raise SystemExit("Use a square master PNG of at least 1024 x 1024 pixels.")
        icon = source.convert("RGBA").resize((1024, 1024), Image.Resampling.LANCZOS)
    DESTINATION.mkdir(parents=True, exist_ok=True)
    icon.save(DESTINATION / "steamstone.ico", sizes=[(size, size) for size in WINDOWS_SIZES])
    icon.save(DESTINATION / "steamstone.icns")
    icon.resize((512, 512), Image.Resampling.LANCZOS).save(DESTINATION / "steamstone.png")
    print(f"Generated Windows, macOS, and Linux launcher icons in {DESTINATION}.")


if __name__ == "__main__":
    main()
