# Implementation status

Version 1.2 shows a pause icon (not refresh) on the running timer's pause button. Version 1.1
added the in-app updater (below). Version 1.0: workout library, editor, runner (foreground
service with speech, beeps and vibration), settings and JSON backup/restore. Segment
expansion, the backup format and release parsing are covered by JVM unit tests. See
"Verified on a phone" and "To verify" below.

## Build setup (Oct 25, 2026)

- Gradle wrapper 8.9, Android Gradle Plugin 8.5.2, Kotlin 2.0.21, compile/target SDK 34,
  min SDK 26, JDK 17.
- ktlint 1.8.0 (`android_studio` style) runs from its CLI jar through `ktlintCheck` /
  `ktlintFormat`. The existing code was reformatted once to match; that commit only changes
  layout.
- CI (`.github/workflows/ci.yml`) runs `./gradlew check` and builds a debug APK on every
  push and pull request. Cloud sessions can also run the full build once `dl.google.com` is
  allowed in the environment's network settings (it is as of Oct 25, 2026).
- Release (`.github/workflows/release.yml`) publishes a GitHub release with the APK when
  `versionName` changes on `main`.
- Release builds are signed with the committed `app/debug.keystore`. An install made earlier
  from Android Studio with a different debug key has to be uninstalled once (export a backup
  first) before these APKs will install over it.

## In-app updater (1.1)

- Checks `https://api.github.com/repos/RMac0001/circuit-caller/releases/latest` (unauthenticated,
  so GitHub allows 60 requests an hour per IP) with HttpURLConnection; no new dependencies.
  Release tags have no `v` prefix (see `release.yml`); a leading `v` is stripped anyway.
- Automatic check: when the library opens, at most once per 24 hours (`update_last_check`),
  silent on any failure, and quiet for a version the user dismissed with "Not now"
  (`update_dismissed_version`) until a newer one appears. The manual check always reports
  back and shows a dismissed version again. Neither key is part of backups.
- The download streams to `cacheDir/updates/circuit-caller-<version>.apk`, which is emptied
  on every app start. It is checked against the asset's `digest` (`sha256:<hex>`) when
  GitHub provides one; without one, Android's signature check still applies.
- Verified on the JVM against the live 1.0 release (Oct 25, 2026): the API response parses,
  the download follows GitHub's 302 to `release-assets.githubusercontent.com`, all
  11,705,896 bytes arrive, the SHA-256 matches, and a wrong digest aborts and deletes the file.
- Install uses a PackageInstaller session (`MODE_FULL_INSTALL`, `setAppPackageName`) committed
  with a `FLAG_MUTABLE` broadcast `PendingIntent` to the non-exported `InstallResultReceiver`.
  It starts Android's confirmation on `STATUS_PENDING_USER_ACTION` and reports failures as a
  snackbar.
- `release.yml` now fails when `versionName` changes but `versionCode` isn't higher than the
  previous release's.

## Known issues

- `targetSdk` is 34. Fine for sideloading; Google Play would require a newer target.

## Verified on a phone

Reported by Roger, Oct 25, 2026:

- **In-app update 1.1 → 1.2:** the update was found, downloaded and installed through
  Android's update prompt, and workouts and settings survived it.
- **Pause icon (1.2):** the running timer's center button shows a pause icon.

## To verify on a phone

- Timer keeps counting and speaking with the screen off and the app in the background.
- Notification pause/skip/stop actions work, and the notification permission prompt appears
  on Android 13+.
- Speech ducks or pauses other audio (music) and restores it afterwards.
- Export a backup, clear app data, import it: workouts and settings come back.
- A release APK from GitHub installs over the previous one without losing data.
- "Not now" hides the card until a newer version; "Check for updates" brings it back.
- Whether granting "Install unknown apps" restarts the app process on some Android
  versions. If it does, tap Update again; the download starts over.
