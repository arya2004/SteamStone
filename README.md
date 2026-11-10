# SteamStone

A focused desktop home for your Steam library. Built with Java 25, JavaFX, and a small CSS theme.

**Milestone 2:** load your library, then click a game in the sidebar to load its achievements and your unlock states. Unlocked rows are green, locked rows are slate, and unavailable states are amber. The sidebar contains the game count; there are no playtime dashboards.

Tick/untick, **Unlock all**, **Lock all**, **Reset preview**, and **Commit** are local preview controls. Commit displays a dummy message and sends nothing to Steam. Preview changes are discarded when you select another game, reload its achievements, disconnect, or close the app. Unavailable states cannot be preview-edited.

## Run on your Mac

```sh
cd ~/Documents/SteamStone
bash scripts/run.sh
```

The script locates your installed JDK 25 on macOS, even if your global `JAVA_HOME` points at an older JDK. It does not change your shell configuration. Maven and JavaFX dependencies download on the first run.

In the window:

1. Paste your **personal Steam Web API key**.
2. Enter your Steam profile URL, vanity name, or 17-digit SteamID64.
3. Choose **Load my library**.

A key authorizes requests; it does not identify the account to load. Examples of accepted profile input:

```text
https://steamcommunity.com/id/your-profile-name
https://steamcommunity.com/profiles/76561198000000000
76561198000000000
```

Use your own profile, not the example number. The app does not guess accounts from Steam's local files. If no games appear, check **Steam → Profile → Edit Profile → Privacy Settings → Game details**. Steam must expose that account's game details to the API.

Create a key at [Steam's registration page](https://steamcommunity.com/dev/apikey). For local use, `localhost` can be used in the domain field. Never send your key in a bug report.

## Requirements

| Requirement | Detail |
| --- | --- |
| Development JDK | **JDK 25**, matching the machine's architecture |
| Maven | Included through the Maven Wrapper; no global Maven installation required |
| JavaFX | 25.0.5, downloaded by Maven for the build host |
| Network | HTTPS access to Maven Central for setup and Steam for library requests |
| Account | Personal API key and profile with game details accessible to the API |

This read-only Web API milestone does **not** require the Steam client to be running, a Steamworks SDK, or native Steam bindings. Native editing will require a local logged-in Steam session later.

Use a current patched JDK 25 distribution. JavaFX is not included in the JDK; Maven handles its native artifacts. Source builds target the current host OS and architecture. A Mac-generated runtime bundle is not a Windows/Linux runtime bundle.

## Build and run on each platform

### macOS

Install JDK 25 if needed (for example, an OpenJDK 25 distribution), then:

```sh
bash scripts/build.sh
bash scripts/run.sh
```

The scripts try a registered JDK 25 and the existing Homebrew OpenJDK installation. For another location, set it explicitly:

```sh
export JAVA_HOME="/path/to/jdk-25/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw clean verify
./mvnw javafx:run
```

### Windows (PowerShell)

Install a Windows JDK 25 matching your architecture and set its directory:

```powershell
cd "$HOME\Documents\SteamStone"
$env:JAVA_HOME = 'C:\path\to\jdk-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd clean verify
.\mvnw.cmd javafx:run
```

Alternatively, after setting `JAVA_HOME`, use `powershell -ExecutionPolicy Bypass -File scripts/run.ps1`. Review the script before running it; the process-only execution policy does not change machine policy.

### Linux (Bash)

Install JDK 25 and the desktop libraries required by JavaFX. On Debian/Ubuntu desktop systems these typically include GTK 3 and the standard X11 libraries; minimal containers need additional display configuration. A graphical desktop session is required.

```sh
cd ~/Documents/SteamStone
export JAVA_HOME="/path/to/jdk-25"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw clean verify
./mvnw javafx:run
```

Run on an OS/architecture supported by JavaFX 25. macOS development/build checks are verified for this milestone. Windows/Linux source commands are provided, but live runtime validation on those platforms remains milestone 4 work. There are no unsupported success claims or prebuilt Windows/Linux releases yet.

## Build outputs

```sh
bash scripts/build.sh
```

This runs the tests and creates:

```text
target/steamstone.jar
target/lib/                 Runtime dependencies for this build host
```

With JDK 25 selected, the built application can also be launched with:

```sh
java --module-path target/lib --add-modules javafx.controls \
  --enable-native-access=javafx.graphics -jar target/steamstone.jar
```

Keep `steamstone.jar` and `lib/` together. The raw JAR bundle needs Java installed. Native app images described below bundle Java for end users.

## Optional macOS application bundle

```sh
bash scripts/package-macos.sh
open target/dist/SteamStone.app
```

The app image includes its own runtime. This is a **local, ad-hoc-signed development bundle**, not a notarized public release. macOS bundle metadata uses `1.0.0` because jpackage requires a positive major version there; the prototype's application version is `0.1.0`. Run the build/tests first; the packaging helper skips repeating tests. Move an existing `target/dist/SteamStone.app` aside before packaging again. Do not run `scripts/build.sh` after packaging if you want to keep that bundle: its `clean` step removes `target/`.

Packaging for public Windows/Linux distribution, CI, signing, and notarization belongs to milestone 4. `jpackage` must build each platform's packages on that platform; sharing source code does not make one packaged app run everywhere.

## Credential handling

- The app does not include or read `key.md`, `.env`, or any developer key.
- The key is entered through a masked field and kept in session memory only.
- It is sent in the `x-webapi-key` header to `https://api.steampowered.com/`, never in a URL.
- Redirects are not followed. User-supplied profile URLs are parsed locally; arbitrary hosts are rejected.
- No key is persisted, exported, or logged. Disconnect clears the session and the earlier library.
- Tests use synthetic credentials and a loopback HTTP server.

Your existing key file stays outside this repository. Enter it manually in the UI. `.gitignore` excludes common local secret filenames as an additional guard.

## Data sources and limitations

SteamStone asks Valve's documented `GetOwnedGames` endpoint for the selected account. It requests names/icons and played free games; it does not fetch a global catalog, use `gib.me`, scrape Community pages, inspect Steam login/cache files, or perform per-title ownership scans.

Vanity links use `ResolveVanityURL`; the optional display name uses `GetPlayerSummaries`. Icons load from Steam's CDN. Games are sorted alphabetically. Selecting one loads `GetSchemaForGame` for achievement definitions and `GetPlayerAchievements` for account states. Results join by the exact achievement API identifier, not list position. Failed/private player data leaves states unavailable instead of assuming locked. Games without definitions show an explicit empty state. Failed requests can be retried with Reload; canceled responses cannot replace another game's view.

Library visibility, temporary/family licenses, free games, and Steam API behavior can affect coverage. A zero response does not prove the account owns no games. Missing/denied data is not silently replaced with an installed-games scan. Failed refresh keeps the prior session snapshot visible and labels it as older data.

## Code structure

```text
src/main/java/steamstone/
  Main.java                  Classpath/package launcher
  SteamStoneApp.java          JavaFX application lifecycle
  model/                     Immutable Steam data and disposable local preview
  steam/                     Profile parsing and documented read-only HTTP API
  ui/LibraryView.java        Connection and sidebar screen/state
  ui/AchievementEditor.java  Async achievement loading and dummy editor
src/main/resources/styles/   Small JavaFX theme
src/test/java/steamstone/    HTTP fixtures, parsing, and data checks
```

One Maven module, no application framework, database, or server. Library and achievement requests each use a background worker. Lists are virtualized; only visible cells request icons, and the in-memory icon cache is bounded.

## Roadmap and review boundary

1. **Library loading** — reviewed; user requested milestone 2.
2. **Achievement listing and dummy editor** — ready for review after verification.
3. Stage achievement edits and commit through native Steam integration.
4. Cross-platform runtime verification and distribution.
5. Statistics/reset workflows and final release preparation.

Stop for review after every milestone. Actual Steam writes and native integration remain milestone 3; this build cannot commit achievements.

## Attribution

SteamStone is inspired by and developed alongside Rick (gibbed)'s [Steam Achievement Manager](https://github.com/gibbed/SteamAchievementManager). The original SAM source remains in its separate repository. This is a new Java implementation; it does not copy SAM's game catalog or native binding code. SteamStone is not affiliated with Valve. Steam names and artwork belong to their respective owners.

## References

- [Owned games API](https://partner.steamgames.com/doc/webapi/IPlayerService#GetOwnedGames)
- [Achievement definitions and player states](https://partner.steamgames.com/doc/webapi/ISteamUserStats)
- [Steam profile API](https://partner.steamgames.com/doc/webapi/ISteamUser)
- [Steam Web API key authentication](https://partner.steamgames.com/doc/webapi_overview/auth)
- [JavaFX setup](https://openjfx.io/openjfx-docs/)
- [Java packaging](https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html)
