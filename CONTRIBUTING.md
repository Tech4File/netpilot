# Contributing to NetPilot

## Ground rules
1. **First-party code only** for app logic — no new third-party runtime dependencies
   (Google's AndroidX/Material excepted). Justify any exception in the PR.
2. **TV first**: every UI change must be D-pad operable and verified in both themes.
3. **Tests before merge**: `./gradlew :app:testDebugUnitTest :app:lintDebug` must pass;
   new logic needs new tests.
4. **No secrets, no telemetry, no new permissions** without a security-review discussion.

## Workflow
1. Fork / branch from `main`
2. Make the change + tests
3. Run the local gate:
   ```bash
   ./gradlew :app:testDebugUnitTest :app:lintDebug
   ```
4. Open a PR using the template (CI runs the full pipeline incl. emulator tests)

## Code style
- Kotlin official style (`kotlin.code.style=official`)
- User-facing strings in `resources/values/strings.xml` — never hard-coded
- Colors/typography via the design tokens (docs/DESIGN_SYSTEM.md)
- Prefer explicit state; UI reflects system state, it never guesses it
