"""Cross-platform checks for Windows downloads; no Windows tools or Steam session needed."""
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

PACKAGE_SCRIPT = Path(__file__).resolve().parents[1] / "package.py"
SPEC = importlib.util.spec_from_file_location("steamstone_package", PACKAGE_SCRIPT)
package = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(package)

VERSION = "0.1.2"
IMAGE_FILES = {
    "SteamStone.exe": b"MZfixture app launcher",
    "SteamStoneWorker.exe": b"MZfixture worker launcher",
    "runtime/bin/java.exe": b"MZfixture bundled runtime",
    "app/steamstone.jar": b"fixture application",
    "app/native/steam_api64.dll": b"fixture Steam API",
    "app/native/steamworks4j64.dll": b"fixture Steam wrapper",
    "app/bin/glass.dll": b"fixture JavaFX native library",
    "LICENSE.md": b"fixture license",
}


class WindowsDownloadsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="steamstone-package-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / "target").mkdir()
        self.image = self.root / "dist with spaces" / "SteamStone"
        for name, contents in IMAGE_FILES.items():
            destination = self.image / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(contents)
        self.releases = self.root / "releases"
        self.commands = []

    def build_fixture_installer(self, command):
        self.commands.append(command)
        destination = Path(command[command.index("--dest") + 1])
        (destination / f"SteamStone-{VERSION}.exe").write_bytes(b"MZfixture installer")

    def test_builds_installer_and_complete_portable_folder_with_matching_checksums(self):
        with patch.object(package, "ROOT", self.root), patch.object(
                package, "run", side_effect=self.build_fixture_installer):
            installer, portable = package.windows_downloads(
                "jpackage.exe", self.image, VERSION, self.releases)
        self.assertEqual(f"SteamStone-{VERSION}-windows-x64-setup.exe", installer.name)
        self.assertEqual(f"SteamStone-{VERSION}-windows-x64.zip", portable.name)
        self.assertEqual(b"MZfixture installer", installer.read_bytes())
        command = self.commands[0]
        self.assertEqual(self.image, command[command.index("--app-image") + 1])
        self.assertEqual("exe", command[command.index("--type") + 1])
        self.assertEqual(package.WINDOWS_UPGRADE_UUID, command[command.index("--win-upgrade-uuid") + 1])
        self.assertIn("--win-per-user-install", command)
        with zipfile.ZipFile(portable) as contents:
            files = {item.filename for item in contents.infolist() if not item.is_dir()}
            self.assertEqual({f"SteamStone/{name}" for name in IMAGE_FILES}, files)
            for name, expected in IMAGE_FILES.items():
                self.assertEqual(expected, contents.read(f"SteamStone/{name}"))
        for download in (installer, portable):
            package.write_checksum(download)
            checksum = Path(str(download) + ".sha256").read_text(encoding="utf-8")
            self.assertEqual(f"{hashlib.sha256(download.read_bytes()).hexdigest()}  {download.name}\n", checksum)

    def test_rebuild_replaces_portable_contents_instead_of_retaining_stale_files(self):
        with patch.object(package, "ROOT", self.root), patch.object(
                package, "run", side_effect=self.build_fixture_installer):
            _, portable = package.windows_downloads("jpackage.exe", self.image, VERSION, self.releases)
            (self.image / "app/steamstone.jar").write_bytes(b"updated fixture application")
            (self.image / "LICENSE.md").unlink()
            package.windows_downloads("jpackage.exe", self.image, VERSION, self.releases)
        with zipfile.ZipFile(portable) as contents:
            self.assertEqual(b"updated fixture application", contents.read("SteamStone/app/steamstone.jar"))
            self.assertNotIn("SteamStone/LICENSE.md", contents.namelist())

    def test_invalid_installer_does_not_create_a_portable_download(self):
        def build_invalid_installer(command):
            destination = Path(command[command.index("--dest") + 1])
            (destination / "invalid.exe").write_bytes(b"not an executable")

        with patch.object(package, "ROOT", self.root), patch.object(
                package, "run", side_effect=build_invalid_installer):
            with self.assertRaisesRegex(SystemExit, "invalid Windows executable"):
                package.windows_downloads("jpackage.exe", self.image, VERSION, self.releases)
        self.assertEqual([], list(self.releases.iterdir()))


if __name__ == "__main__":
    unittest.main()
