# SteamStone

SteamStone is a desktop app for viewing and editing your Steam achievements. Browse your games, tick achievements to unlock them, untick them to lock them, and click **Commit** to save your changes to Steam.

## Download

Get SteamStone from the [Releases page](https://github.com/arya2004/SteamStone/releases/latest). Open **Assets** and choose the download for your computer:

| Your computer | Download | How to open it |
| --- | --- | --- |
| Mac with Apple Silicon (M1 or newer) | `SteamStone-<version>-macos-arm64.zip` | Unzip, drag SteamStone into Applications, and open it |
| Intel Mac | `SteamStone-<version>-macos-x64.zip` | Unzip, drag SteamStone into Applications, and open it |
| Windows 64-bit — installer | `SteamStone-<version>-windows-x64-setup.exe` | Run the setup wizard, then open SteamStone from the Start menu |
| Windows 64-bit — portable | `SteamStone-<version>-windows-x64.zip` | Extract the ZIP, keep the folder together, and run `SteamStone/SteamStone.exe` |
| Linux 64-bit (x86_64) | `SteamStone-<version>-linux-x64.tar.gz` | Extract it, then run `SteamStone/bin/SteamStone` |

**You do not need to install Java or any developer tools.** Both Windows downloads include Java. The setup wizard installs SteamStone for your account and lets you choose its location and shortcuts; you can remove it through Windows Settings → Apps. The portable ZIP needs no installation: extract it once, run `SteamStone.exe` inside its folder, and keep the runtime and other files beside it. Keep the extracted Linux app folder together; Linux needs a graphical desktop with GTK 3.

If there are no release downloads yet, a public build has not been published. The repository's **Code → Download ZIP** button downloads source code, not a runnable app.

Builds are currently unsigned and not notarized. Your operating system may ask you to allow the app. macOS with Apple Silicon has been tested with Steam; Intel Mac, Windows, and Linux still need live Steam testing.

## First-time setup

1. Open Steam and sign in.
2. Get your personal key from [Steam's API key page](https://steamcommunity.com/dev/apikey). If asked for a domain, enter `localhost`.
3. Open SteamStone and paste that key into **Steam Web API key**.
4. Enter your Steam profile URL or SteamID64. Use the account currently signed into Steam.
5. Click **Load my library**, then choose a game from the sidebar.

Your key stays in memory for the current session. SteamStone does not save it or include a shared key in the download.

## Edit achievements

- **Tick / untick** an achievement to stage an unlock or lock.
- **Unlock all / Lock all** stages changes for every available achievement in the selected game.
- **Reset** discards changes you have not committed.
- **Commit** saves your staged changes through Steam and shows the result.
- **↻** reloads the game's achievements.

Blue rows are unlocked; dark slate rows are locked. Unavailable achievements cannot be edited. Keep Steam running while using the editor. Some games restrict achievement changes; SteamStone reports failed or partially saved changes.

## Having trouble?

**My games are missing.** Check that you entered the correct profile and that Steam can access its Game details. Check your profile's privacy settings, then refresh your library.

**I can browse but cannot edit.** Keep Steam open, check that both apps are using the same account, and reload the game. Owning a game does not guarantee Steam will let it be edited on your current platform.

**A save was not confirmed.** Reload before trying again; Steam may still sync accepted changes. Check the result instead of assuming every change was saved.

Found a bug? [Open an issue](https://github.com/arya2004/SteamStone/issues) with your operating system, app version, and what happened. Keep API keys and personal information out of reports.

## Contribute

See [CONTRIBUTING.md](CONTRIBUTING.md) for building, testing, and creating releases, and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for community guidelines.

## License and credits

SteamStone is available under the [MIT license](LICENSE.md). It is inspired by [Steam Achievement Manager](https://github.com/gibbed/SteamAchievementManager) and uses JavaFX and [steamworks4j](https://github.com/code-disaster/steamworks4j). Third-party components keep their own licenses; the steamworks4j notice is included in `licenses/` and packaged downloads.

SteamStone is not affiliated with Valve. Steam names and game artwork belong to their owners.
