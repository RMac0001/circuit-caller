#!/bin/bash
# Installs the Android SDK and warms the Gradle cache so ./gradlew check and builds work in
# Claude Code on the web sessions. Needs dl.google.com (SDK and Google's Maven repository)
# allowed in the environment's network settings; without it, it warns and lets the session
# start anyway.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
    exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-.}"

SDK="${ANDROID_HOME:-$HOME/android-sdk}"
TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "Installing Android command-line tools into $SDK"
    tmp="$(mktemp -d)"
    if ! curl -fsSL -o "$tmp/tools.zip" "$TOOLS_URL"; then
        echo "WARNING: could not download the Android SDK (is dl.google.com allowed?)." >&2
        echo "ktlint works without it; Android builds, lint and unit tests do not." >&2
        rm -rf "$tmp"
        exit 0
    fi
    mkdir -p "$SDK/cmdline-tools"
    unzip -q "$tmp/tools.zip" -d "$tmp"
    rm -rf "$SDK/cmdline-tools/latest"
    mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
    rm -rf "$tmp"
fi

sdkmanager="$SDK/cmdline-tools/latest/bin/sdkmanager"
yes | "$sdkmanager" --licenses > /dev/null || true
"$sdkmanager" --install "platform-tools" "platforms;android-34" "build-tools;34.0.0" > /dev/null

echo "sdk.dir=$SDK" > local.properties
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
    echo "export ANDROID_HOME=\"$SDK\"" >> "$CLAUDE_ENV_FILE"
fi

# Download Gradle, plugins and dependencies now so the first real build is fast.
./gradlew --no-daemon --quiet :app:compileDebugUnitTestKotlin ktlintCheck || \
    echo "WARNING: Gradle warm-up failed; run ./gradlew check to see why." >&2
