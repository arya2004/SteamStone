#!/usr/bin/env python3
"""Build a runtime-inclusive app image and platform downloads. Python standard library only."""
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
import zipfile
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parent.parent
# Keep this stable so later Windows installers recognize the existing installation.
WINDOWS_UPGRADE_UUID = "b7b58994-b292-571d-af9f-ec047c0e95af"


def run(command, **kwargs):
    subprocess.run([str(value) for value in command], check=True, **kwargs)


def ensure_windows_wix():
    """jpackage's standard Windows installer backend needs WiX 3.x on the build host."""
    if shutil.which("candle.exe") and shutil.which("light.exe"):
        return
    candidates = []
    if os.environ.get("WIX"):
        candidates.append(Path(os.environ["WIX"]) / "bin")
    for variable in ("ProgramFiles(x86)", "ProgramFiles"):
        if os.environ.get(variable):
            candidates.extend(Path(os.environ[variable]).glob("WiX Toolset v3.*/bin"))
    for directory in candidates:
        if (directory / "candle.exe").is_file() and (directory / "light.exe").is_file():
            os.environ["PATH"] = str(directory) + os.pathsep + os.environ.get("PATH", "")
            return
    raise SystemExit("Windows installer builds require WiX Toolset 3.14.x (candle.exe and light.exe). "
                     "Install it on the build machine and add its bin directory to PATH.")


def install_native_files(stage, system, arch):
    """Prepare native files at build time, using the libraries' standard on-disk layouts."""
    names = {"Windows": ("steam_api64.dll", "steamworks4j64.dll"),
             "Darwin": ("libsteam_api.dylib", "libsteamworks4j.dylib"),
             "Linux": ("libsteam_api.so", "libsteamworks4j.so")}[system]
    (stage / "native").mkdir()
    for name in names:
        source = ROOT / "target/native" / name
        if not source.is_file():
            raise SystemExit("Steam native files are missing. Run Maven verify before packaging.")
        shutil.copy2(source, stage / "native" / name)
    # Install JNA's bridge beside the Steam natives so saving a key never extracts executable code.
    jna_platform = {("Darwin", "arm64"): "darwin-aarch64", ("Darwin", "x64"): "darwin-x86-64",
                    ("Windows", "x64"): "win32-x86-64", ("Linux", "x64"): "linux-x86-64"}[(system, arch)]
    jna_name = {"Darwin": "libjnidispatch.jnilib", "Windows": "jnidispatch.dll", "Linux": "libjnidispatch.so"}[system]
    jna_jars = list((stage / "lib").glob("jna-[0-9]*.jar"))
    if len(jna_jars) != 1:
        raise SystemExit("Expected one resolved JNA runtime dependency.")
    with zipfile.ZipFile(jna_jars[0]) as contents:
        (stage / "native" / jna_name).write_bytes(contents.read(f"com/sun/jna/{jna_platform}/{jna_name}"))
    # This is JavaFX's standard native-library layout next to its modular JARs.
    # Installing these files prevents JavaFX from extracting resources into a user cache.
    javafx_directory = stage / ("bin" if system == "Windows" else "lib")
    javafx_directory.mkdir(exist_ok=True)
    suffix = {"Windows": ".dll", "Darwin": ".dylib", "Linux": ".so"}[system]
    count = 0
    for jar in (stage / "lib").glob("javafx-*.jar"):
        with zipfile.ZipFile(jar) as contents:
            for entry in contents.infolist():
                if not entry.is_dir() and entry.filename.endswith(suffix):
                    (javafx_directory / Path(entry.filename).name).write_bytes(contents.read(entry))
                    count += 1
    if count == 0:
        raise SystemExit("JavaFX native files are missing from this platform's dependencies.")


def runtime_dependencies(jar):
    """Use Maven's resolved manifest rather than stale JARs left by an earlier build."""
    with zipfile.ZipFile(jar) as contents:
        manifest = contents.read("META-INF/MANIFEST.MF").decode("utf-8")
    manifest = manifest.replace("\r\n ", "").replace("\n ", "")
    classpath = next((line.removeprefix("Class-Path: ") for line in manifest.splitlines()
                      if line.startswith("Class-Path: ")), "")
    if not classpath:
        raise SystemExit("The application JAR has no runtime classpath. Run Maven verify before packaging.")
    dependencies = []
    for entry in classpath.split():
        relative = Path(unquote(entry))
        dependency = jar.parent / relative
        if relative.parent != Path("lib") or relative.suffix != ".jar" or not dependency.is_file():
            raise SystemExit(f"Missing or invalid runtime dependency: {entry}. Run Maven verify before packaging.")
        dependencies.append(dependency)
    return dependencies


def packaging_icon(system):
    extension = {"Windows": ".ico", "Darwin": ".icns", "Linux": ".png"}[system]
    icon = ROOT / "packaging/icons" / f"steamstone{extension}"
    if not icon.is_file():
        raise SystemExit(f"Missing launcher icon: {icon}. Run scripts/generate_icons.py after updating the master PNG.")
    return icon


def windows_installer(jpackage, image, version, destination):
    """Create a normal per-user setup wizard from the already checked app image."""
    destination.mkdir(parents=True, exist_ok=True)
    installer = destination / f"SteamStone-{version}-windows-x64-setup.exe"
    # Use a fresh build directory so an older installer cannot be mistaken for this build's output.
    with tempfile.TemporaryDirectory(prefix="steamstone-installer-", dir=ROOT / "target") as temporary:
        run([jpackage, "--type", "exe", "--name", "SteamStone", "--app-version", version,
             "--vendor", "SteamStone", "--description", "Steam achievement editor",
             "--app-image", image, "--dest", temporary,
             "--icon", packaging_icon("Windows"),
             "--license-file", ROOT / "LICENSE.md",
             "--win-per-user-install", "--win-dir-chooser", "--win-menu",
             "--win-menu-group", "SteamStone", "--win-shortcut", "--win-shortcut-prompt",
             "--win-upgrade-uuid", WINDOWS_UPGRADE_UUID,
             "--about-url", "https://github.com/arya2004/SteamStone"])
        built = list(Path(temporary).glob("*.exe"))
        if len(built) != 1:
            raise SystemExit("jpackage did not produce exactly one Windows setup executable.")
        with built[0].open("rb") as source:
            if source.read(2) != b"MZ":
                raise SystemExit("jpackage returned an invalid Windows executable.")
        shutil.copy2(built[0], installer)
    return installer


def windows_downloads(jpackage, image, version, destination):
    """Offer setup and a portable copy of the same checked app image."""
    installer = windows_installer(jpackage, image, version, destination)
    portable = Path(shutil.make_archive(
        str(destination / f"SteamStone-{version}-windows-x64"), "zip",
        root_dir=image.parent, base_dir=image.name))
    return installer, portable


def write_checksum(download):
    digest = hashlib.sha256()
    with download.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    Path(str(download) + ".sha256").write_text(
        f"{digest.hexdigest()}  {download.name}\n", encoding="utf-8", newline="\n")
    print(f"Created {download} and its SHA-256 checksum.")


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
    if system == "Windows":
        ensure_windows_wix()
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
    for dependency in runtime_dependencies(jar):
        shutil.copy2(dependency, stage / "lib")
    install_native_files(stage, system, arch)
    for name in ("LICENSE.md", "README.md"):
        shutil.copy2(ROOT / name, stage)
    shutil.copytree(ROOT / "licenses", stage / "licenses")
    bundle_version = version
    if system == "Darwin" and version.split(".")[0] == "0":
        bundle_version = "1." + version.split(".", 1)[1]
    command = [jpackage, "--type", "app-image", "--name", "SteamStone", "--app-version", bundle_version,
               "--vendor", "SteamStone", "--input", stage, "--main-jar", "steamstone.jar",
               "--icon", packaging_icon(system),
               "--main-class", "steamstone.Main", "--dest", dist,
               "--java-options", "--module-path=$APPDIR/lib", "--java-options", "--add-modules=javafx.controls",
               "--java-options", "--enable-native-access=javafx.graphics,ALL-UNNAMED",
               "--java-options", "-Djna.boot.library.path=$APPDIR/native",
               "--java-options", "-Djna.nounpack=true",
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
        downloads = (archive,)
    elif system == "Windows":
        downloads = windows_downloads(jpackage, image, version, releases)
    else:
        archive = Path(str(base) + ".tar.gz")
        with tarfile.open(archive, "w:gz") as output:
            output.add(image, arcname=image.name)
        downloads = (archive,)
    for download in downloads:
        write_checksum(download)


if __name__ == "__main__":
    main()
