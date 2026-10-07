# Contributing to Leftovers

Thanks for helping. Bug fixes, small improvements and features from the
[issue list](https://github.com/saisaharsh3/Leftovers/issues) are all welcome. Issues labelled
**good first issue** or **help wanted** are a good place to start.

## How changes reach the app

```
your fork ──pull request──▶ dev ──tested, then merged──▶ main ──tag──▶ release
```

- **`dev`** is where contributions land. Open every pull request against `dev`, not `main`.
- **`main`** only gets changes after they've been tried on a phone from `dev`. Releases are tagged from `main`.

## Before you start

- **Small fix?** Go ahead and open a pull request.
- **New feature or bigger change?** Comment on the issue first (or open one) and say how you plan to do it.
  That avoids work that doesn't fit the app.
- If you pick up an issue, comment so others know it's taken.

## Setting up

You need **JDK 17 or newer** and the **Android SDK** ([Android Studio](https://developer.android.com/studio) has both).

```bash
# Fork the repo on GitHub, then:
git clone https://github.com/<you>/Leftovers.git
cd Leftovers
git remote add upstream https://github.com/saisaharsh3/Leftovers.git
git switch -c my-change upstream/dev

./gradlew testDebugUnitTest   # run the tests
./gradlew installDebug        # install on a phone or emulator
```

On Windows use `gradlew.bat`. Keep your branch up to date with `git pull --rebase upstream dev`.

## Testing changes from `dev`

Debug builds install as a separate app called **Leftovers Dev** (amber icon), next to the real Leftovers, with their own
empty data. Restore a backup into it (Settings → Restore) to test with realistic data.

- **On the phone:** every push to `dev` is published as a **Dev build** pre-release on the
  [Releases](https://github.com/saisaharsh3/Leftovers/releases) page. Open it and install `leftovers-dev.apk`.
  Each new dev build installs over the last one.
- **Pull requests:** GitHub Actions builds them too. Open the run under
  [Actions](https://github.com/saisaharsh3/Leftovers/actions), download **leftovers-debug**, unzip it and install
  `app-debug.apk` (it may need the previous Leftovers Dev uninstalled first).
- **With a computer:** `git switch dev && git pull`, then `./gradlew installDebug` with the phone connected.
  To try a pull request before merging it: `gh pr checkout <number>`, then `./gradlew installDebug`.

## What a good pull request looks like

- **One change per pull request**, with a clear title such as *"Add AMOLED theme"*.
- **Tests pass** (`./gradlew testDebugUnitTest`). Add tests for logic you add or change; see `app/src/test`.
- **Tried on a phone or emulator.** Add before/after screenshots for anything you can see.
- **Matches the code around it**: Kotlin and Jetpack Compose, the existing components in `ui/components`,
  colours from the theme (`LocalAppColors`) and icons from `ui/icons/Lucide.kt`. Keep comments short and
  about *why*.
- **Keeps the app calm.** Leftovers aims to stay simple. Prefer putting a feature where it naturally belongs
  over adding new buttons, tabs or cards to Home.

## Rules that protect users

These matter more than any feature:

- **Data stays on the phone.** No analytics, tracking, ads or crash reporters, and no network calls except the
  optional AI assistant the user connects themselves.
- **No new permissions** without discussing it in an issue first.
- **Never commit secrets**: API keys, `keystore.properties`, `*.jks`. Use your own keys locally.
- **Database changes need a migration.** If you change a Room entity:
  1. Bump `version` in `AppDatabase.kt` and add a `Migration`.
  2. Build once so the new schema is exported to `app/schemas/`, and commit that file.
  3. Add the migration to `ALL_MIGRATIONS`. `MigrationTest` then checks the upgrade.
  4. If the data should survive a backup, update `BackupManager` too.

## Reporting bugs and asking for features

Use the [issue forms](https://github.com/saisaharsh3/Leftovers/issues/new/choose). For bugs, include the app
version (bottom of Settings), your phone and Android version, and steps to reproduce. Please don't post real
financial data or screenshots with personal details.

## Code of conduct

Be kind and constructive. See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

By contributing, you agree that your work is released under the [MIT License](LICENSE).
