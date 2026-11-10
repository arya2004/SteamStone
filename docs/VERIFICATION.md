# Milestone 1 verification

Verified on an Apple Silicon Mac on 2026-11-11 using JDK 25.0.2 and JavaFX 25.0.5.

- Maven Wrapper clean verify: 12 tests passed, zero failures/errors.
- Local HTTP fixtures cover credential validation, header-only authentication, vanity resolution, private/unavailable and zero libraries, incomplete snapshots, malformed data, HTTP errors, and optional profile failure.
- A user-authorized live request to Valve returned HTTP 200 and 62 games.
- The JavaFX graphical check rendered those 62 games, verified search/clear, and verified that the key input was cleared after connection.
- Connection/library screenshots were inspected for layout. They remain under ignored target/verification, not in published documentation.
- The packaged macOS app launched from outside the repository with an intentionally invalid JAVA_HOME, confirming it uses its bundled runtime. Startup emitted no diagnostics.
- No achievements or statistics were modified.

The key was read from an existing file in the separate original SAM workspace solely for the approved integration check. The application does not auto-read this file. No key was copied into this repository, persisted by the app, or printed.

Windows/Linux source run commands are documented but have not been runtime-tested. Their verification remains a later milestone.

## Sidebar/editor layout revision

- All 62 live library games rendered in the searchable sidebar; the game count appears only there.
- Removed total playtime, games-played cards, playtime rows, and the sorting dashboard.
- Selecting a sidebar game updates the main achievement workspace heading and App ID.
- Achievement data and editing remain unimplemented; the workspace states that explicitly.
- All 12 existing tests passed, the graphical selection/search check passed, and refreshed screenshots were visually inspected.
- The macOS app bundle was rebuilt with the revised layout. Restart an older running instance to see the update.

## Milestone 2: achievements and dummy editor

- Clean Maven verification: 23 tests passed, zero failures/errors.
- New HTTP fixtures verify exact-identifier joins, locked/unlocked states, hidden metadata, private/unavailable player data, malformed/missing states, duplicate identifiers, account mismatch, empty definitions, and validation before requests.
- Preview checks verify toggling back removes pending changes, bulk operations skip unknown states, reset restores the original immutable snapshot, and foreign rows cannot be edited.
- User-authorized graphical live check loaded 62 library games and 35 achievements for a selected game. It switched selection while loading, verified the editor context, and exercised tick/untick, Unlock all, Lock all, dummy Commit, Reset preview, library search, and clearing the credential field.
- The Commit message explicitly confirmed that nothing was sent to Steam. All remote achievement operations are GET requests; the application contains no achievement-write endpoint or native integration.
- Screenshots remain in ignored `target/verification`. The achievement layout was visually inspected on macOS.
- The macOS development bundle is rebuilt for this milestone. Windows/Linux runtime validation remains pending.

## Compact Steam-style layout revision

- Replaced the duplicate workspace masthead, large game heading, banners, and stacked controls with one compact title bar.
- Reduced achievement rows from 112 to 56 pixels, icons from 48 to 32 pixels, and moved API identifiers to tooltips.
- Removed persistent preview warnings and counters. Informational/error text uses space only when present; Commit remains a dummy action.
- Applied charcoal/navy backgrounds and Steam-style blue accents throughout, including unlocked rows and selected sidebar games.
- All 23 tests passed after the layout change. The graphical live check verified a list starting within 90 pixels of the top and rows no taller than 60 pixels, along with the existing controls and search checks.
- At 1180×800, about 13 achievements fit in the main pane, versus about three in the prior layout.

## Milestone 3: native achievement editing

This supersedes earlier notes describing Commit as dummy/read-only.

- Clean Maven verification: 35 tests passed, including native commit engine failure/conflict/timeout/readback cases and callback correlation.
- Read-only native session for App ID 1182900 returned known achievement states and actual descriptions on Apple Silicon.
- Graphical live verification loaded 62 games and 35 achievements, showed actual hidden-achievement descriptions, and checked local staging/reset/Commit enablement without executing a write.
- The compact blue UI screenshot was inspected. The Mac bundle now includes a separate native worker launcher.
- Actual live write verification remains pending a specific authorized achievement/action. Simulated storage acknowledgment is not presented as live Steam write verification.
- Details and behavior are documented in NATIVE_EDITOR.md. Windows/Linux runtime verification remains pending.

### Authorized live write and restoration result

The user selected A Plague Tale: Requiem and requested testing with original-state restoration.

- Saved all 35 native achievement states and unlock times before writing.
- Selected originally locked ACH_20. Steam acknowledged unlocking it; a fresh worker session verified the unlocked state and verified every other achievement stayed unchanged.
- Steam acknowledged clearing ACH_20. A fresh worker session confirmed all 35 states matched the saved baseline and existing unlocked-achievement timestamps were unchanged.
- The final native app worker also passed read-only metadata loading and account-mismatch rejection using its bundled runtime with an invalid system JAVA_HOME.
- The earlier “live write pending” note above is superseded by these results. Other games and Windows/Linux writes remain unverified.
- Snapshot/results are under ignored `target/verification/requiem-*.json`; the optional test harness requires an explicit live-write opt-in and always attempts restoration in its finally block.
