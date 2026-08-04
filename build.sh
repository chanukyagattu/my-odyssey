#!/usr/bin/env bash
#
# The fast feedback loop.
#
#   ./build.sh          engine tests, then link the iOS framework
#   ./build.sh test     engine tests only  (~30s, no Xcode involved)
#   ./build.sh clean    throw away every build artefact and start over
#
# Xcode runs Gradle itself during ⌘R, so this script is not strictly required —
# it exists because a Kotlin error is far easier to read here than buried in an
# Xcode build log.
set -euo pipefail
cd "$(dirname "$0")"

# Kotlin/Native needs a full Xcode, not the standalone command line tools.
export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"

green() { printf '\033[32m%s\033[0m\n' "$1"; }
blue()  { printf '\033[34m%s\033[0m\n' "$1"; }

case "${1:-all}" in
  clean)
    blue "==> Cleaning"
    ./gradlew --stop >/dev/null 2>&1 || true
    rm -rf .gradle build engine/build composeApp/build
    rm -rf ~/Library/Developer/Xcode/DerivedData/iosApp-*
    green "Clean. Run ./build.sh next."
    ;;

  test)
    blue "==> Engine tests (pure JVM, no Xcode)"
    ./gradlew :engine:jvmTest
    green "Engine green."
    ;;

  all)
    blue "==> 1/2  Engine tests"
    ./gradlew :engine:jvmTest

    blue "==> 2/2  Linking the iOS framework"
    ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64

    green ""
    green "Both green. Now in Xcode: ⌘R"
    green "  open iosApp/iosApp.xcodeproj"
    ;;

  *)
    echo "usage: ./build.sh [all|test|clean]" >&2
    exit 2
    ;;
esac
