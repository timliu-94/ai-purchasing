#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
source "$PROJECT_DIR/scripts/env.sh"
cd "$PROJECT_DIR"
case "${1:-help}" in
    doctor)
        java -version
        adb version
        emulator -list-avds
        ./gradlew --version
        ;;
    build) ./gradlew assembleDebug ;;
    test) ./gradlew testDebugUnitTest lintDebug ;;
    ui-test) ./gradlew connectedDebugAndroidTest ;;
    emulator) exec emulator -avd "${2:-Medium_Phone_API_35}" -no-snapshot-save ;;
    run)
        ./gradlew installDebug
        adb ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell am start -W -n com.aipurchase.app/.MainActivity
        ;;
    devices) adb devices -l ;;
    logs) exec adb logcat ;;
    studio) open -a 'Android Studio' "$PROJECT_DIR" ;;
    *) echo 'Usage: ./scripts/android.sh {doctor|build|test|emulator [AVD]|run|ui-test|devices|logs|studio}' ;;
esac
