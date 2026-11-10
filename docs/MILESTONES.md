# SteamStone milestones

Implement one milestone at a time. At each checkpoint, provide run commands, checks, and limitations; stop for the user's review before proceeding. Approval is never automatic.

| # | Milestone | Status |
| --- | --- | --- |
| 1 | Enter a personal API key/profile and load the account library in the UI | Reviewed; milestone 2 requested |
| 2 | Open a game, list achievements, and preview dummy edits | Reviewed; actual editing requested |
| 3 | Stage achievement edits and commit through native Steam | Implemented and Mac live write/restore verified; ready for review |
| 4 | Verify cross-platform runtime behavior and release packaging | Pending |
| 5 | Statistics editing and reset workflows | Pending |
| 6 | Final polish and release preparation | Pending |

## Current scope

The JavaFX app loads the library through documented Web API reads and obtains achievement state/descriptions through a native per-game Steam worker. Checkbox and bulk controls stage edits; Commit applies setters, waits for the matching storage callback, and reads back states. Unknown, failed, partial, or conflicting results do not become unconditional success. A matching local account/game is required. Navigation, disconnect and normal close are blocked during commits. Pending edits require an explicit discard when leaving or reloading.

Native read-only sessions and descriptions are verified on the development Mac. A user-authorized Requiem unlock/lock round trip was acknowledged by Steam and verified through fresh sessions; all 35 original states and existing unlock timestamps were restored. Simulated tests cover failure behavior. See [native editor details](NATIVE_EDITOR.md).

The macOS development app image is a local convenience for reviewing the current milestone. It is not a public release or a claim of Windows/Linux runtime verification.

## Keep it simple

- One Maven module, plain records, a concrete HTTP client, and small screen owners.
- No global candidate catalog, internal Steam cache parsing, or scraping.
- No database, backend server, DI framework, plugin system, or generic infrastructure.
- Keep native session ownership in the isolated worker; never initialize Steam in JavaFX.
- Preserve explicit source/account context, pending edits, and truthful save outcomes as later features are introduced.
- Keep all credentials outside source control and distributed artifacts.
