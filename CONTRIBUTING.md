# Contributing to SteamStone

Bug reports, small fixes, and improvements are welcome. For larger changes, open an issue first so we can agree on the scope. Follow the [code of conduct](CODE_OF_CONDUCT.md).

## Build and run

Install **JDK 25** for your computer's architecture. Maven is included in the repository. Packaging also uses **Python 3.10+**, with no extra Python packages.

On macOS:

```sh
bash scripts/build.sh
bash scripts/run.sh
```

The Mac scripts locate JDK 25 without changing your shell configuration. On Linux, select JDK 25 using `JAVA_HOME` and run:

```sh
./mvnw clean verify
./mvnw javafx:run
```

On Windows, set `JAVA_HOME` to JDK 25 and add its `bin` directory to your PATH, then use PowerShell:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd javafx:run
```

Steam is needed for native achievement reads and editing. Library browsing uses a personal Web API key. Keep keys outside the repository and enter them in the app.

## Making changes

Keep changes focused and simple. One Maven module is intentional; avoid new frameworks or abstractions unless they solve a concrete problem. Include a short explanation and relevant checks with your pull request.

- `model/` holds Steam snapshots and staged edits.
- `steam/` handles documented read-only Web API calls.
- `steam/nativeapi/` owns the isolated native worker and commit logic.
- `ui/` contains the JavaFX views; `styles/app.css` controls appearance.
- `inject/` contains the Guice composition root and application bindings. Lombok generates injection
  constructors for final dependencies; `@Inject` initialization methods configure JavaFX views on
  the application thread. Cell providers create fresh rows while sharing their owning view.

Native workers must verify the game and account, check expected states before editing, wait for the matching storage callback, and read results back. Keep partial failures and uncertain outcomes explicit. Never add an automatic write or reset when browsing a game.

`./mvnw verify` runs fixture-based tests without Steam or credentials. CI also packages the app and checks that its bundled native worker can load its libraries without signing into Steam. Optional graphical/live checks in `src/test` require a local desktop and explicit credentials; they do not run in CI. Never use a contributor's account or key in an automated workflow. Live writes require a specifically authorized game and achievement, a saved baseline, and verified restoration.

## Package a download

Build on the target OS with its matching JDK 25. Select that JDK using `JAVA_HOME`, then run:

```sh
python3 scripts/package.py
```

On Windows use `python scripts/package.py` and install **WiX Toolset 3.14.x** on the build machine. The script finds its `candle.exe` and `light.exe` through PATH, `WIX`, or the standard Program Files location. The Windows 2025 GitHub runner already includes WiX. End users do not need WiX.

The script runs the tests, creates an app image in `target/dist/`, and checks the bundled worker. Windows produces both **`SteamStone-<version>-windows-x64-setup.exe`** and **`SteamStone-<version>-windows-x64.zip`** in `target/releases/`, each with a SHA-256 checksum. The installer uses the standard `jpackage --type exe` wizard with per-user installation, a directory chooser, optional shortcuts, and uninstall support. The portable ZIP contains the same checked app image and bundled runtime; extract the complete folder and run `SteamStone/SteamStone.exe`. Keep `WINDOWS_UPGRADE_UUID` unchanged so later installers recognize the same app. Mac/Linux produce their existing archives and checksums. Maven prepares Steam native libraries under `target/native`; the packager installs the matching files and JavaFX native libraries in their normal on-disk locations. Packaged apps load these installed files rather than extracting them from JARs at runtime. Java is bundled, so users need no JDK. A graphical Linux desktop needs GTK 3. The helper `bash scripts/package-macos.sh` selects your Mac JDK before running the same packager.

If `target/dist/SteamStone.app` or `target/dist/SteamStone` already exists, move it aside or choose another destination with `--output target/another-build`. After a successful build, `--skip-build` avoids repeating Maven checks. Never run `clean` if you want to keep existing files under `target/`.

The Apple app bundle needs a positive major version in its metadata. For a project version starting with 0, only that metadata uses major 1; the project version and download filename remain unchanged. Signing/notarization and public Windows/Linux Steam verification are not configured yet.

## Releases (maintainers)

1. Update the project version in `pom.xml` to a plain `major.minor.patch` version and merge the intended changes.
2. Create and push a matching tag, such as `v0.1.0` for project version `0.1.0`.
3. The **Release** workflow tests and packages on Apple Silicon macOS, Intel macOS, Windows x64, and Linux x64. Each job checks its bundled native libraries without account access. If any job fails, no release is created.
4. After all jobs pass, the workflow creates a **draft GitHub release** containing five downloads (two Mac archives, the Windows installer and portable ZIP, and the Linux archive), their checksums, and generated notes. The Windows job also extracts the portable ZIP and checks its bundled worker without signing into Steam.
5. Review the notes and test the downloads with Steam on the platforms you can verify. Label remaining limitations, then publish the draft when ready.

The workflow uses GitHub's built-in token; no personal token, Steam key, or signing secret is required. Running Release manually builds downloadable workflow artifacts without creating a release. Workflows must be pushed to GitHub before they can run; creating these files locally does not publish anything.

By contributing, you agree that your contributions are available under the project's [MIT license](LICENSE.md). Third-party libraries retain their existing terms.
