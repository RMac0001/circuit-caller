# Implementation status

Version 1.0: workout library, editor, runner (foreground service with speech, beeps and
vibration), settings and JSON backup/restore are implemented. Segment expansion and the
backup format are covered by JVM unit tests. Nothing has been checked on a phone through
this repository's builds yet; see "To verify" below.

## Build setup (Oct 25, 2026)

- Gradle wrapper 8.9, Android Gradle Plugin 8.5.2, Kotlin 2.0.21, compile/target SDK 34,
  min SDK 26, JDK 17.
- ktlint 1.8.0 (`android_studio` style) runs from its CLI jar through `ktlintCheck` /
  `ktlintFormat`. The existing code was reformatted once to match; that commit only changes
  layout.
- CI (`.github/workflows/ci.yml`) runs `./gradlew check` and builds a debug APK on every
  push and pull request. Cloud sessions can't reach `dl.google.com` under the current network
  policy, so CI is the only place the Android build runs until that host is allowed.
- Release (`.github/workflows/release.yml`) publishes a GitHub release with the APK when
  `versionName` changes on `main`.
- Release builds are signed with the committed `app/debug.keystore`. An install made earlier
  from Android Studio with a different debug key has to be uninstalled once (export a backup
  first) before these APKs will install over it.

## Known issues

- `targetSdk` is 34. Fine for sideloading; Google Play would require a newer target.

## To verify on a phone

- Timer keeps counting and speaking with the screen off and the app in the background.
- Notification pause/skip/stop actions work, and the notification permission prompt appears
  on Android 13+.
- Speech ducks or pauses other audio (music) and restores it afterwards.
- Export a backup, clear app data, import it: workouts and settings come back.
- A release APK from GitHub installs over the previous one without losing data.
