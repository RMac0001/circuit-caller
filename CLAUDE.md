# Circuit Caller — Android app

Android app (Kotlin, Jetpack Compose, Material 3) that runs interval/circuit workouts and calls
each exercise out loud. A workout is a list of exercises (work and rest seconds) repeated for
a number of rounds. The runner expands it into PREP → WORK → REST segments and plays them in a
foreground service with text-to-speech, beeps and vibration, so it keeps going with the screen
off. Workouts live in Room; settings in DataStore. `README.md` has the user-facing behaviour
and `STATUS.md` the implementation notes and what still needs checking on a phone.

## Commands

- `./gradlew check`: ktlint, Android Lint and unit tests. Run before committing.
- `./gradlew :app:assembleDebug`: debug APK in `app/build/outputs/apk/debug/`.
- `./gradlew ktlintFormat`: auto-fix formatting.
- `./gradlew :app:testDebugUnitTest`: JVM unit tests in `app/src/test/`.

Cloud sessions need `dl.google.com` allowed in the environment's network settings for the
Android SDK and Google's Maven repository (the SessionStart hook installs the SDK). Without
it only ktlint runs locally; push the branch and let GitHub Actions CI, which runs on every
branch, do the build.

## Conventions

- Source lives in `app/src/main/java/com/roger/circuitcaller/`, package `com.roger.circuitcaller`.
- Keep logic that can be tested on the JVM out of Android classes: segment expansion
  (`timer/Segment.kt`) and backup JSON (`data/Backup.kt`) are plain Kotlin and have unit
  tests. Do the same for new logic, rather than burying it in `TimerService` or a composable.
- Unit tests are JUnit 4. `org.json:json` is a test dependency because android.jar's
  `org.json` is a stub on the JVM.
- Formatting: ktlint 1.8.0, `android_studio` style, 4-space indent, max line 120 (settings in
  `.editorconfig`). Composables are PascalCase. Fix lint warnings; don't suppress rules
  without a described reason.
- UI text: sentence case. User-visible strings for the manifest live in `res/values/strings.xml`.
- Never commit `local.properties`, build output, APKs or any keystore except
  `app/debug.keystore`.

## Architecture

- `data/`: Room entities `Workout` and `Exercise` (cascade delete), `WorkoutDao`,
  `WorkoutWithExercises` (`usableExercises` = ordered, blanks dropped), `SettingsRepository`
  (DataStore → `AppSettings`) and `Backup` (versioned JSON export/import).
- `timer/`: `SegmentBuilder` turns a workout into segments; `TimerService` is the foreground
  service that plays them (state in a `StateFlow<RunState>`, wake lock, notification
  actions); `Speaker` wraps TextToSpeech, audio focus and beeps.
- `ui/`: single `MainActivity` with Navigation Compose; `AppViewModel` is shared by the
  screens (`library`, `editor`, `run`, `settings`).
- `CircuitApp` owns the database and settings repository and seeds an example workout on
  first launch.

## Hard rules

- The timer counts from `SystemClock.elapsedRealtime()` deadlines, never by summing delays.
- Blank-named exercises are skipped; the last round has no trailing rest.
- `prepOverrideSeconds = -1` means "use the global prep setting".
- Backup import validates the whole file before writing anything; never half-import.
- Bump `Backup.VERSION` and keep reading old versions when the backup format changes.
- Room schema changes need a migration (bump the `@Database` version); don't wipe user data.

## Versioning

Bump both `versionCode` (+1) and `versionName` in `app/build.gradle.kts`. Pushing a new
`versionName` to `main` makes the release workflow run `check`, build the release APK and
publish a GitHub release (tag without a `v` prefix) with `circuit-caller-<version>.apk`
attached. Tags can't be pushed from Claude Code cloud sessions, so don't rely on pushing tags.

Release APKs are signed with the committed `app/debug.keystore` (local and CI builds share it,
so every APK installs as an update over the last). It is not secret; replace it with a release
key held in GitHub Secrets before distributing the app beyond personal use.
