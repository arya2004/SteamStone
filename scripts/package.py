#!/usr/bin/env python3
"""Build a runtime-inclusive app image and a platform download. Python standard library only."""
import argparse
import hashlib
import os
import platform
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent


def run(command, **kwargs):
    subprocess.run([str(value) for value in command], check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", help="Release version; must match pom.xml")
    parser.add_argument("--output", type=Path, default=ROOT / "target/dist", help="App image destination")
    parser.add_argument("--skip-build", action="store_true", help="Use an already verified target/ build")
    args = parser.parse_args()
    os.chdir(ROOT)
    system = platform.system()
    suffix = ".exe" if system == "Windows" else ""
    home = os.environ.get("JAVA_HOME")
    java = Path(home) / "bin" / ("java" + suffix) if home else Path(shutil.which("java") or "java").resolve()
    version_output = subprocess.run([str(java), "-XshowSettings:properties", "-version"], capture_output=True, text=True, check=True)
    if not re.search(r'version "25(?:[.\"-])', version_output.stdout + version_output.stderr):
        raise SystemExit("Select JDK 25 with JAVA_HOME before packaging.")
    jpackage = java.parent / ("jpackage" + suffix)
    if not jpackage.is_file():
        raise SystemExit("jpackage is missing. Use a full JDK 25 installation.")
    project = ET.parse(ROOT / "pom.xml").getroot()
    version = project.findtext("{http://maven.apache.org/POM/4.0.0}version")
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version or ""):
        raise SystemExit("pom.xml must use a plain major.minor.patch version for packaging.")
    if args.version and args.version != version:
        raise SystemExit("Release tag/version must match pom.xml.")
    java_arch = re.search(r"^\s*os\.arch\s*=\s*(\S+)", version_output.stderr + version_output.stdout, re.MULTILINE)
    if java_arch is None:
        raise SystemExit("Could not determine the selected JDK architecture.")
    machine = java_arch.group(1).lower()
    arch = {"arm64": "arm64", "aarch64": "arm64", "amd64": "x64", "x86_64": "x64"}.get(machine)
    if system not in ("Darwin", "Windows", "Linux") or arch is None or (system != "Darwin" and arch != "x64"):
        raise SystemExit("Supported builds: macOS arm64/x64, Windows x64, Linux x64.")
    dist = args.output.resolve()
    image = dist / ("SteamStone.app" if system == "Darwin" else "SteamStone")
    if image.exists():
        raise SystemExit(f"{image} exists. Move it aside or choose --output before packaging again.")
    if not args.skip_build:
        wrapper = ["cmd.exe", "/c", "mvnw.cmd"] if system == "Windows" else ["./mvnw"]
        run([*wrapper, "-B", "-ntp", "verify"])
    jar = ROOT / "target/steamstone.jar"
    if not jar.is_file() or not (ROOT / "target/lib").is_dir():
        raise SystemExit("Build outputs are missing. Run the build before --skip-build.")
    stage = ROOT / "target/package-input"
    if stage.exists():
        shutil.rmtree(stage)
    (stage / "lib").mkdir(parents=True)
    shutil.copy2(jar, stage)
    for dependency in (ROOT / "target/lib").glob("*.jar"):
        shutil.copy2(dependency, stage / "lib")
    for name in ("LICENSE.md", "README.md"):
        shutil.copy2(ROOT / name, stage)
    shutil.copytree(ROOT / "licenses", stage / "licenses")
    worker_properties = ROOT / "target/worker-launcher.properties"
    worker_properties.write_text("main-class=steamstone.steam.nativeapi.NativeWorker\n"
                                 "java-options=--enable-native-access=ALL-UNNAMED\n", encoding="utf-8")
    bundle_version = version
    if system == "Darwin" and version.split(".")[0] == "0":
        bundle_version = "1." + version.split(".", 1)[1]
    command = [jpackage, "--type", "app-image", "--name", "SteamStone", "--app-version", bundle_version,
               "--vendor", "SteamStone", "--input", stage, "--main-jar", "steamstone.jar",
               "--main-class", "steamstone.Main", "--dest", dist,
               "--add-launcher", f"SteamStoneWorker={worker_properties}",
               "--java-options", "--module-path=$APPDIR/lib", "--java-options", "--add-modules=javafx.controls",
               "--java-options", "--enable-native-access=javafx.graphics",
               "--add-modules", "java.se,jdk.unsupported,jdk.crypto.ec"]
    if system == "Darwin":
        command += ["--mac-package-identifier", "app.steamstone.desktop"]
    run(command)
    # Exercise the main app's real worker-launch path, without a UI, credentials or Steam initialization.
    launcher = image / ({"Darwin": "Contents/MacOS/SteamStone", "Windows": "SteamStone.exe",
                          "Linux": "bin/SteamStone"}[system])
    with tempfile.TemporaryDirectory(prefix="steamstone-package-check-") as temporary:
        run([launcher, "--check-runtime"], cwd=temporary, timeout=40)
    releases = ROOT / "target/releases"
    releases.mkdir(exist_ok=True)
    os_name = {"Darwin": "macos", "Windows": "windows", "Linux": "linux"}[system]
    base = releases / f"SteamStone-{version}-{os_name}-{arch}"
    if system == "Darwin":
        archive = Path(str(base) + ".zip")
        run(["ditto", "-c", "-k", "--sequesterRsrc", "--keepParent", image, archive])
    elif system == "Windows":
        archive = Path(shutil.make_archive(str(base), "zip", root_dir=dist, base_dir=image.name))
    else:
        archive = Path(str(base) + ".tar.gz")
        with tarfile.open(archive, "w:gz") as output:
            output.add(image, arcname=image.name)
    digest = hashlib.sha256()
    with archive.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    Path(str(archive) + ".sha256").write_text(f"{digest.hexdigest()}  {archive.name}\n", encoding="utf-8", newline="\n")
    print(f"Created {archive} and its SHA-256 checksum.")


if __name__ == "__main__":
    main()
