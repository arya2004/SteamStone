# Native achievement editor

## Use

1. Keep Steam running and signed into the account whose profile you loaded.
2. Select a game. SteamStone combines Web API definitions with native state, unlock time, and descriptions. Hidden achievements show their actual description when Steam provides one. Missing descriptions leave no fabricated subtitle.
3. Tick/untick individual rows, or stage Unlock all / Lock all. A small dot marks changed states; Reset discards staged edits.
4. Choose Commit. The sidebar and editor are temporarily disabled and normal window close is blocked until the worker finishes.
5. Read the result. Confirmed states become the new baseline; unmatched changes remain pending. An unconfirmed save disables further edits until Reload because some changes could still sync.

Changing games, disconnecting, closing, or reloading with pending edits asks whether to discard them. Local edits never call Steam setters until Commit.

## Implementation

The existing single Maven module now includes `steam/nativeapi`:

- `NativeSteam` launches one short-lived process per operation with a private temporary working directory. A small JSON request/result protocol carries App ID, account ID, achievement IDs, expected states and desired changes. It never includes the Web API key. Stderr/stdout from the SDK are not treated as protocol messages.
- `SteamNativeLibraries` extracts the architecture-matching libraries from the pinned steamworks4j 1.10.0 JAR. macOS binaries are universal; Linux/Windows artifacts still need target-host runtime tests.
- `NativeWorker` supplies the game context only to its child environment and temporary `steam_appid.txt`. It verifies `SteamUtils.getAppID()` and `SteamUser.getSteamID()` before reading or setting anything. It neither launches the game executable nor calls RestartAppIfNecessary.
- SDK 1.62 preloads current-user stats during initialization. The worker pumps callbacks while waiting for metadata. A read operation does not call setters or StoreStats.
- `SteamAttributes` adds two documented flat C API reads missing from steamworks4j: GetAchievementDisplayAttribute and GetAchievementAndUnlockTime. Java 25's built-in Foreign Function & Memory API calls the pinned runtime's v013 stats interface. There is no vtable patching, cache parsing, scraping, or extra SDK installation.
- `AchievementCommit` checks **all** requested baselines before applying any setters. Concurrently changed or unavailable states stop the batch. It calls SetAchievement/ClearAchievement only for staged IDs, StoreStats once for accepted setters, waits for the matching UserStatsStored callback, then reads back.
- The worker waits up to 25 seconds for storage; the parent bounds an entire operation to 50 seconds. Temporary files are deleted after process exit. Forced process termination and Steam shutdown can leave outcomes uncertain.
- The macOS app includes a second `SteamStoneWorker` launcher sharing its bundled runtime. Source development launches the worker using the selected JDK; end users do not need Java installed.

## Result semantics

| Result | Meaning |
| --- | --- |
| READ | Read-only native session completed; no setters/storage were requested |
| CONFIRMED | Steam acknowledged storage and every requested state matched native readback |
| PARTIAL | Steam acknowledged storage, but some setters failed or requested states did not match; unmatched edits stay pending |
| CONFLICT | A requested baseline changed before setting; the batch applied no setters and the UI refreshes its baseline |
| REJECTED | Initialization/context/read/set validation failed; no state is claimed saved |
| UNKNOWN | Storage failed, timed out, or the worker exited unexpectedly; accepted changes may still sync, so reload before retrying |

This is not transactional. Steam may automatically flush accepted setters on shutdown. An unsuccessful StoreStats result does not imply rollback. No automatic rollback, ResetAllStats, server-stat editing, or restriction bypass is implemented. The backend's protection metadata is not fully exposed; known read failures block edits and Steam setter/storage rejections are reported. A native readback after acknowledgment confirms the local Steam view; it does not guarantee a game's server or another device will retain that state indefinitely.

## Checks and limits

- 35 automated tests passed on macOS, including header-only Web API authentication, schema/state joins, hidden-description fallback, staged changes, baseline conflicts, unreadable states, setter failures, partial writes, rejected storage, timeout, readback mismatch, and matching storage callbacks.
- A real native read returned known states and descriptions for A Plague Tale: Requiem (App ID 1182900) on the development Apple Silicon Mac.
- The graphical read-only check loaded 62 games and 35 achievements, verified real descriptions and edit-control enablement, and exercised local checkbox/bulk/reset behavior. It deliberately never clicks Commit.
- The user authorized a live Requiem test with restoration. ACH_20 was originally locked; Steam acknowledged unlocking it, and a fresh native session verified it while all other states stayed unchanged. Steam then acknowledged clearing it. A fresh read confirmed all 35 original states and every pre-existing unlocked achievement’s timestamp were unchanged.
- Before/after snapshots and operation results remain in ignored `target/verification/requiem-*.json`. No key appears in them. A clean build removes these local verification artifacts.
- The packaged worker successfully performed native reads with an invalid system JAVA_HOME and rejected a different requested Steam account. These checks performed no setters/storage.
- No other game was live-write tested. Simulated failures do not establish that every title permits editing.
- Owned games can still fail native initialization because of platform availability, local Steam licensing/configuration, or game restrictions. Browsing a game through the Web API does not prove it can be edited here.
- Windows/Linux packaged runtime and write tests are still pending milestone 4. Public signing/notarization/release work is outside this milestone.

Optional source read-only check (after `bash scripts/build.sh`, with JDK 25 selected):

```sh
STEAMSTONE_TEST_PROFILE=YOUR_17_DIGIT_STEAMID \
  "$JAVA_HOME/bin/java" -cp 'target/test-classes:target/classes:target/lib/*' \
  steamstone.steam.NativeReadCheck
```

This development check specifically targets owned App ID 1182900 and ACH_01/ACH_02; use it only for an account that owns that title. It never sends setters or StoreStats. Normal app use selects the App ID from your library.

## Primary references

- [steamworks4j support matrix and included runtimes](https://code-disaster.github.io/steamworks4j/)
- [steamworks4j loading and lifecycle](https://code-disaster.github.io/steamworks4j/getting-started.html)
- [Steamworks initialization and application context](https://partner.steamgames.com/doc/sdk/api)
- [Achievement attributes, setters, StoreStats and callbacks](https://partner.steamgames.com/doc/api/ISteamUserStats)
