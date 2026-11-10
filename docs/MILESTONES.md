# SteamStone milestones

Implement one milestone at a time. At each checkpoint, provide run commands, checks, and limitations; stop for the user's review before proceeding. Approval is never automatic.

| # | Milestone | Status |
| --- | --- | --- |
| 1 | Enter a personal API key/profile and load the account library in the UI | Reviewed; milestone 2 requested |
| 2 | Open a game, list achievements, and preview dummy edits | Ready for review |
| 3 | Stage achievement edits and commit through native Steam | Pending |
| 4 | Verify cross-platform runtime behavior and release packaging | Pending |
| 5 | Statistics editing and reset workflows | Pending |
| 6 | Final polish and release preparation | Pending |

## Current scope

The JavaFX app performs documented read-only Web API requests. It supports profile URLs/SteamID64, a searchable alphabetical sidebar, refresh, disconnect, and achievement loading when selecting a game. The dummy editor supports checkbox/bulk previews and reset. Commit only displays a message; no achievement writes or native integration are implemented. Unknown states stay unavailable and cannot be edited. Switching games or reloading discards the disposable preview.

The macOS development app image is a local convenience for reviewing the current milestone. It is not a public release or a claim of Windows/Linux runtime verification.

## Keep it simple

- One Maven module, plain records, a concrete HTTP client, and small screen owners.
- No global candidate catalog, internal Steam cache parsing, or scraping.
- No database, backend server, DI framework, plugin system, or generic infrastructure.
- Add native session ownership behind the UI boundary when milestone 3 requires it.
- Preserve explicit source/account context, pending edits, and truthful save outcomes as later features are introduced.
- Keep all credentials outside source control and distributed artifacts.
