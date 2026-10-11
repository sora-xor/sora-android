# shellcheck shell=bash
# Shared by the device-test scripts. Source it; do not run it.
#
# Options every device-test script accepts:
#   --offline    pass --offline to Gradle: build from the local Gradle cache, no network
#   -h, --help   print the script's usage
#
# Environment:
#   SORA_GRADLE_FLAGS  extra Gradle arguments, split on spaces, for example
#                      "--dependency-verification=strict" or
#                      "-Dorg.gradle.java.installations.paths=/path/to/jdk17,/path/to/jdk21"
#   ANDROID_SERIAL     the emulator to use; required when more than one device is attached
#   ANDROID_HOME       used to find adb when it is not on PATH

sora_gradle_flags=()
sora_offline=0

sora_use_offline_gradle() {
  if [ "${sora_offline}" -eq 0 ]; then
    sora_offline=1
    sora_gradle_flags+=("--offline")
  fi
}

# Reads SORA_GRADLE_FLAGS once. Call after the script has parsed its own arguments.
sora_read_gradle_environment() {
  local flag=""
  # Word splitting is intended: the variable holds a list of plain Gradle arguments.
  # shellcheck disable=SC2086
  for flag in ${SORA_GRADLE_FLAGS:-}; do
    if [ "${flag}" = "--offline" ]; then
      sora_use_offline_gradle
    else
      sora_gradle_flags+=("${flag}")
    fi
  done
}

# The option to hand to another device-test script so that it builds the same way.
sora_offline_option() {
  if [ "${sora_offline}" -eq 1 ]; then
    printf '%s\n' "--offline"
  fi
}

sora_gradle() {
  # "${array[@]+...}" keeps bash 3.2 (macOS) from failing on an empty array under set -u.
  ./gradlew "$@" ${sora_gradle_flags[@]+"${sora_gradle_flags[@]}"} \
    --stacktrace --no-daemon --no-parallel
}

# Puts adb on PATH from the Android SDK when the shell does not have it.
sora_require_adb() {
  local sdk_root=""
  if command -v adb >/dev/null 2>&1; then
    return
  fi
  for sdk_root in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
    if [ -n "${sdk_root}" ] && [ -x "${sdk_root}/platform-tools/adb" ]; then
      PATH="${sdk_root}/platform-tools:${PATH}"
      export PATH
      return
    fi
  done
  echo "error: adb not found. Put it on PATH or set ANDROID_HOME." >&2
  exit 1
}

# Stops unless exactly one device is selected. adb and Gradle both honour ANDROID_SERIAL.
sora_require_one_device() {
  local attached=""
  sora_require_adb
  attached="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
  if [ -z "${attached}" ]; then
    echo "error: no device is attached. Start an emulator first." >&2
    exit 1
  fi
  if [ -n "${ANDROID_SERIAL:-}" ]; then
    if ! grep -Fxq -- "${ANDROID_SERIAL}" <<<"${attached}"; then
      echo "error: ANDROID_SERIAL=${ANDROID_SERIAL} is not an attached device." >&2
      exit 1
    fi
  elif [ "$(wc -l <<<"${attached}" | tr -d ' ')" -ne 1 ]; then
    echo "error: more than one device is attached. Set ANDROID_SERIAL to the emulator to use." >&2
    exit 1
  fi
}

# Stops unless the selected device is an emulator.
sora_require_emulator() {
  local emulator_flags=""
  sora_require_one_device
  emulator_flags="$(adb shell 'getprop ro.boot.qemu; getprop ro.kernel.qemu' | tr -d '\r')"
  if [[ "${emulator_flags}" != *1* ]]; then
    echo "error: this script runs only on an Android emulator. Start an emulator and set ANDROID_SERIAL to it." >&2
    exit 1
  fi
}
