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
