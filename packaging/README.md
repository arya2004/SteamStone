# SteamStone

Browse your Steam library and stage achievement changes before explicitly saving them through Steam.

Java is included. Keep this application bundle or folder together. Windows portable: run SteamStone.exe. Linux: run bin/SteamStone. macOS: open SteamStone.app. Linux needs a graphical desktop and GTK 3.

Open Steam under your account, then enter your personal Steam Web API key and profile in SteamStone. Saving credentials is optional and uses the OS credential store. Checkbox changes remain local until **Save changes**; **Reset** discards the preview. If Steam does not confirm a save, reload before retrying. Some games restrict achievement changes.

See [USER-GUIDE.md](USER-GUIDE.md) for setup, privacy, keyboard controls and troubleshooting. The [online guide](https://arya2004.github.io/SteamStone/guide) and [bug tracker](https://github.com/arya2004/SteamStone/issues/new/choose) provide further help. Never attach API keys to reports.

These builds have no trusted publisher signature and are not notarized. Do not disable OS-wide security protections to run them. Version, source revision and dependency hashes are in `build-info.json`; a source-snapshot build is for local review, not a verified public release.

SteamStone is [MIT licensed](LICENSE.md). Dependency notices included by their publishers are preserved in `licenses/` and the bundled runtime's legal directory. SteamStone is not affiliated with or endorsed by Valve.
