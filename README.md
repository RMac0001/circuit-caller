# Circuit Caller

An Android app for circuit and interval workouts that calls out each exercise so you never
have to look at the screen. Build a workout from exercises with their own work and rest
times, choose how many rounds, and press start.

## What it does

- **Calls the workout out loud:** speaks each exercise name as it starts, "Rest" between
  exercises, "Round 2" and so on at the start of each round, with an optional 3-2-1
  countdown, beeps and vibration.
- **Keeps running with the screen off:** the timer runs in a foreground service with a
  notification you can pause, skip or stop from.
- **Prep time:** a short get-ready segment before each exercise (global setting, can be
  overridden per workout). The final round ends without a pointless last rest.
- **Optional halfway callout** for exercises of a minute or longer.
- **Voice settings:** choose the text-to-speech voice and speech rate.
- **Backup and restore:** export all workouts and settings to a plain JSON file and import
  it again, for example on a new phone.

## Install

1. On the phone, open the [latest release](https://github.com/RMac0001/circuit-caller/releases/latest)
   and download `circuit-caller-<version>.apk`.
2. Open it and allow installing from your browser or file manager when Android asks.
3. To update, install the newer APK over the old one; your workouts are kept.

Requires Android 8.0 (API 26) or later.

## Updates

From 1.1 on, the app updates itself from this repository's GitHub releases. It checks at most
once a day when you open your workouts, or whenever you tap **Check for updates** at the
bottom of the screen. When a newer version exists, a card at the top of the list offers
**Update** or **Not now**. "Not now" hides that version until a newer one comes out. It never
checks or updates during a workout.

The first time you update, Android asks you to allow Circuit Caller to install apps
("Install unknown apps", then turn on **Allow from this source**). This is a one-time
permission; come back to the app and the update continues. Android then shows its own
"Do you want to update this app?" prompt. Your workouts and settings are kept.

## Building

Open the project in Android Studio, or from the command line with JDK 17 and the Android SDK:

```
./gradlew check                # ktlint, Android Lint, unit tests
./gradlew :app:assembleDebug   # APK in app/build/outputs/apk/debug/
```

Every push runs CI on GitHub Actions; the debug APK is attached to each run as an artifact.

## License

[GPL-3.0](LICENSE)
