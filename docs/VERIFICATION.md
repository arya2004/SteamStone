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
