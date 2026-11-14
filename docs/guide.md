---
layout: default
title: SteamStone user guide
---

# SteamStone user guide

[Website](https://arya2004.github.io/SteamStone/) · [Downloads](https://github.com/arya2004/SteamStone/releases/latest) · [Report a problem](https://github.com/arya2004/SteamStone/issues/new/choose)

## Installation

Downloads contain Java. Select the architecture shown in your computer's system information, not just its operating-system name. Never run the app from inside an archive.

- **macOS Apple Silicon:** extract the `macos-arm64.zip` archive and move SteamStone.app to Applications. Intel Macs require `macos-x64.zip`.
- **Windows x64:** run `windows-x64-setup.exe` for a per-user installation and Start Menu shortcut. Alternatively extract the portable ZIP and run `SteamStone/SteamStone.exe`; retain the entire folder. Uninstall the setup version through Settings → Apps; remove the portable version by deleting its extracted folder after closing it.
- **Linux x64:** extract the TAR.GZ and run `SteamStone/bin/SteamStone`. Keep its runtime and library directories together. A graphical desktop, GTK 3 and compatible system libraries are required; the build target is Ubuntu 24.04. Remove the extracted folder to uninstall. SteamStone does not install a system service.

Close the app before replacing an existing installation. Saved credentials belong to the OS credential store, so removing the app does not necessarily erase them. Disconnect and uncheck the save option first if you want to forget the account.

### Verify a download

Download its matching `.sha256` file from the same release. On macOS:

```sh
shasum -a 256 -c SteamStone-1.0.0-macos-arm64.zip.sha256
```

On Linux:

```sh
sha256sum -c SteamStone-1.0.0-linux-x64.tar.gz.sha256
```

On Windows, compare the following result with the checksum file:

```powershell
Get-FileHash .\SteamStone-1.0.0-windows-x64-setup.exe -Algorithm SHA256
```

These v1.0.0 filenames become available only after publication; substitute the downloaded version. For releases built with the attestation workflow, GitHub CLI can also verify build provenance:

```sh
gh attestation verify SteamStone-1.0.0-macos-arm64.zip --repo arya2004/SteamStone
```

A checksum detects altered files; an attestation ties the artifact to a workflow. Neither is a trusted publisher signature or a malware guarantee.

### OS security warnings

SteamStone has no trusted publisher certificate and is not notarized. macOS may apply an ad-hoc signature internally; that is not Developer ID signing. Follow [Apple's per-app guidance](https://support.apple.com/en-us/102445) only if you have verified the source and choose to trust the app. Do not disable Gatekeeper globally or remove quarantine recursively.

Windows may warn or block an unsigned download, especially on managed machines or with Smart App Control. See [Microsoft's explanation](https://learn.microsoft.com/windows/apps/package-and-deploy/smartscreen-reputation). Do not disable Defender, SmartScreen, or organizational policies to run SteamStone. If policy blocks it, stop and report the limitation.

## Setup and privacy

Open Steam and sign in. Visit [Steam's API key page](https://steamcommunity.com/dev/apikey), also linked in SteamStone, and follow Steam's current requirements. Enter your own key plus a Steam Community profile URL, vanity name, or SteamID64. Editing requires the profile to match the account signed into Steam.

The key is sent to Steam over HTTPS in a request header, not a query string. Profile and game queries go to Steam; artwork loads from Steam's image hosts. SteamStone does not add analytics or telemetry.

**Save API key and profile on this device** is optional. It stores one connection in macOS Keychain, Windows Credential Manager, or a supported Linux GNOME/KWallet keyring. A remembered connection loads automatically at startup. No plaintext-file fallback exists. On Linux without a supported keyring, manual session-only login remains available. Credential-store failures show an error; they do not silently save to disk.

To change accounts, disconnect and enter the new account's key/profile. To erase saved credentials, disconnect and uncheck the save option, then wait for it to finish. Disconnect alone preserves a remembered account. SteamStone does not discover or switch among profiles associated with a key.

## Managing achievements

Selecting a game loads its metadata and current states. Unknown or unavailable states cannot be edited. The native Steam session verifies the account and game before any write.

| Action | Effect |
| --- | --- |
| Tick / untick | Stage an unlock / lock locally |
| Unlock all / Lock all | Stage that state for every available achievement in the selected game |
| Reset | Discard the pending preview; does not reset Steam's saved achievements |
| Save changes | Send only pending changes, after checking their expected original state |
| Reload | Fetch current achievements; asks before discarding pending edits |
| Refresh library | Refresh game navigation; preserve the open achievement preview |

Game switching, disconnecting, and closing ask before discarding pending edits. While saving, duplicate saves and navigation are blocked. Do not force-close during a save: Steam may have accepted some changes even if confirmation has not arrived.

A confirmed result requires a successful Steam storage callback and matching readback. A partial result leaves unresolved edits staged. A conflict means Steam's state changed since loading, so no setters were sent; review the refreshed preview. An uncertain result does not prove failure: accepted changes may still synchronize. Reload and inspect before retrying. SteamStone does not promise rollback, restored unlock timestamps, or support for every game.

## Troubleshooting and FAQ

**No games / private profile:** check the entered profile and Steam's Game details visibility. Refresh after correcting it. An unavailable response is not treated as proof of an empty library.

**No achievements:** a game may not define any, or Steam may not expose its metadata. Do not assume unavailable achievements are locked.

**Can browse but cannot edit:** keep Steam running under the same account, then reload. Native initialization, game ownership, platform support, or game restrictions can prevent editing. Browsing does not require a native Steam write session.

**API error / rate limiting:** verify your key and profile, check connectivity, and retry later. Requests have timeouts; there is no automatic retry loop that repeatedly submits writes.

**Save not confirmed:** reload before retrying. Check the resulting state in Steam as well. Never interpret a generic error as proof that nothing changed.

**Credential-store error:** unlock the OS keyring if necessary. You can enter credentials manually without saving. Remove a saved entry through the OS credential manager if the app cannot erase it; its service is `app.steamstone.desktop`, account `saved-connection`.

**Will this affect game restrictions or anti-cheat?** SteamStone cannot guarantee how a game or service will treat edited achievements. Understand the game's rules before using it.

**Startup failure:** verify architecture, extract the complete archive, and check required Linux libraries. Report the app version, OS, architecture, downloaded filename, and visible error. Redact keys, account data and local paths from attached logs.

## Keyboard navigation

Use Tab / Shift+Tab to move focus, Space to toggle a focused checkbox, and the menus for platform-specific shortcuts. Common shortcuts include Ctrl/Cmd+F (find a game), Ctrl/Cmd+R (refresh library), Ctrl/Cmd+Shift+R (reload achievements), Ctrl/Cmd+S (save), and Ctrl/Cmd+W (close). On macOS, menus appear in the system menu bar; other platforms use the window menu bar.

## Development and verification

[Contribution guide](https://github.com/arya2004/SteamStone/blob/main/CONTRIBUTING.md)
