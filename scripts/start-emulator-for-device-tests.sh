#!/usr/bin/env bash
set -euo pipefail

# Starts a headless Android emulator for the device-test scripts and waits until Android has
# booted. Run it on a developer or release machine.

usage() {
  cat <<'USAGE'
usage: start-emulator-for-device-tests.sh --avd NAME [--port PORT] [--read-only]
                                          [--create SYSTEM_IMAGE [--install-image]]
                                          [--log FILE] [--boot-timeout SECONDS]

  --avd NAME             the AVD to start
  --port PORT            console port, an even number from 5554 to 5682 (default 5554);
                         the device serial is emulator-PORT
  --read-only            start with -read-only: nothing is written back to the AVD
  --create SYSTEM_IMAGE  create the AVD first from this SDK package, for example
                         "system-images;android-34;google_atd;x86_64". An AVD of the same
                         name is replaced.
  --install-image        with --create: install the system image and the emulator with
                         sdkmanager first (needs network access)
  --log FILE             emulator output (default build/device-qualification/emulator.log)
  --boot-timeout SECONDS how long to wait for Android to boot (default 600)

Refuses to start when emulator-PORT is already attached, so it never takes over an emulator
somebody else started. Prints ANDROID_SERIAL=emulator-PORT for the caller to export.
Stop the emulator with: adb -s emulator-PORT emu kill
USAGE
}

avd_name=""
port="5554"
read_only=0
system_image=""
install_image=0
log_file=""
boot_timeout="600"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --avd | --port | --create | --log | --boot-timeout)
      if [ "$#" -lt 2 ] || [ -z "$2" ]; then
        echo "error: $1 needs a value" >&2
        exit 2
      fi
      case "$1" in
        --avd) avd_name="$2" ;;
        --port) port="$2" ;;
        --create) system_image="$2" ;;
        --log) log_file="$2" ;;
        --boot-timeout) boot_timeout="$2" ;;
      esac
      shift
      ;;
    --read-only) read_only=1 ;;
    --install-image) install_image=1 ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      echo "error: unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
  shift
done

if [[ ! "${avd_name}" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "error: --avd NAME is required (letters, digits, dot, underscore, hyphen)" >&2
  exit 2
fi
if [[ ! "${port}" =~ ^[0-9]+$ ]] || [ "${port}" -lt 5554 ] || [ "${port}" -gt 5682 ] ||
  [ $((port % 2)) -ne 0 ]; then
  echo "error: --port must be an even number from 5554 to 5682" >&2
  exit 2
fi
if [[ ! "${boot_timeout}" =~ ^[0-9]+$ ]]; then
  echo "error: --boot-timeout must be a number of seconds" >&2
  exit 2
fi
if [ "${install_image}" -eq 1 ] && [ -z "${system_image}" ]; then
  echo "error: --install-image needs --create SYSTEM_IMAGE" >&2
  exit 2
fi

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "${sdk_root}" ] || [ ! -d "${sdk_root}" ]; then
  echo "error: set ANDROID_HOME to the Android SDK" >&2
  exit 1
fi
readonly serial="emulator-${port}"

# Prints the path of an SDK tool: from the SDK itself first, then from PATH.
find_sdk_tool() {
  local name="$1"
  local candidate=""
  for candidate in \
    "${sdk_root}/${name}" \
    "${sdk_root}/platform-tools/${name}" \
    "${sdk_root}/emulator/${name}" \
    "${sdk_root}/cmdline-tools/latest/bin/${name}" \
    "${sdk_root}"/cmdline-tools/*/bin/"${name}"; do
    if [ -f "${candidate}" ] && [ -x "${candidate}" ]; then
      printf '%s\n' "${candidate}"
      return
    fi
  done
  candidate="$(command -v "${name}" 2>/dev/null || true)"
  if [ -n "${candidate}" ]; then
    printf '%s\n' "${candidate}"
    return
  fi
  echo "error: ${name} not found in ${sdk_root} or on PATH" >&2
  exit 1
}

if [ "${install_image}" -eq 1 ]; then
  sdkmanager="$(find_sdk_tool sdkmanager)"
  # "yes" ends with a broken pipe once sdkmanager stops reading; that is not a failure.
  (yes 2>/dev/null || true) | "${sdkmanager}" --sdk_root="${sdk_root}" --install \
    "platform-tools" "emulator" "${system_image}" >/dev/null
fi

adb="$(find_sdk_tool adb)"
emulator="$(find_sdk_tool emulator)"
readonly adb emulator

if "${adb}" devices | awk 'NR > 1 { print $1 }' | grep -Fxq -- "${serial}"; then
  echo "error: ${serial} is already attached. Choose another --port; this script never reuses or stops an emulator it did not start." >&2
  exit 1
fi

if [ -n "${system_image}" ]; then
  image_dir="${sdk_root}/$(tr ';' '/' <<<"${system_image}")"
  if [ ! -d "${image_dir}" ]; then
    echo "error: ${system_image} is not installed in ${sdk_root}. Install it, or pass --install-image." >&2
    exit 1
  fi
  avdmanager="$(find_sdk_tool avdmanager)"
  # "no": do not create a custom hardware profile.
  echo "no" | ANDROID_SDK_ROOT="${sdk_root}" ANDROID_HOME="${sdk_root}" \
    "${avdmanager}" create avd --force --name "${avd_name}" --package "${system_image}" \
    --device "pixel_6" >/dev/null
  avd_config="${ANDROID_AVD_HOME:-${HOME}/.android/avd}/${avd_name}.avd/config.ini"
  if [ ! -f "${avd_config}" ]; then
    echo "error: avdmanager did not create ${avd_config}" >&2
    exit 1
  fi
  # Room for the test APKs, the app under test and the databases the tests create.
  grep -v -E '^(disk\.dataPartition\.size|hw\.ramSize|hw\.keyboard)=' "${avd_config}" >"${avd_config}.new"
  printf '%s\n' "disk.dataPartition.size=6G" "hw.ramSize=3072" "hw.keyboard=yes" >>"${avd_config}.new"
  mv "${avd_config}.new" "${avd_config}"
fi

if ! "${emulator}" -list-avds | grep -Fxq -- "${avd_name}"; then
  echo "error: there is no AVD named ${avd_name}. Create it, or pass --create SYSTEM_IMAGE." >&2
  exit 1
fi

log_file="${log_file:-build/device-qualification/emulator.log}"
mkdir -p "$(dirname "${log_file}")"

emulator_arguments=(
  -avd "${avd_name}" -port "${port}"
  -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect
)
if [ "${read_only}" -eq 1 ]; then
  emulator_arguments+=(-read-only)
fi
nohup "${emulator}" "${emulator_arguments[@]}" >"${log_file}" 2>&1 &
readonly emulator_pid=$!

fail_boot() {
  echo "error: $1" >&2
  echo "--- last lines of ${log_file} ---" >&2
  tail -n 40 "${log_file}" >&2 || true
  kill "${emulator_pid}" 2>/dev/null || true
  exit 1
}

waited=0
booted=""
while [ "${waited}" -lt "${boot_timeout}" ]; do
  if ! kill -0 "${emulator_pid}" 2>/dev/null; then
    fail_boot "the emulator process ended before Android booted"
  fi
  booted="$("${adb}" -s "${serial}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [ "${booted}" = "1" ]; then
    break
  fi
  sleep 5
  waited=$((waited + 5))
done
if [ "${booted}" != "1" ]; then
  fail_boot "Android did not finish booting on ${serial} within ${boot_timeout} seconds"
fi

# UI tests are steadier without animations; a read-only AVD forgets these settings on exit.
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  "${adb}" -s "${serial}" shell settings put global "${setting}" 0 >/dev/null 2>&1 || true
done
"${adb}" -s "${serial}" shell input keyevent 82 >/dev/null 2>&1 || true

echo "Android $("${adb}" -s "${serial}" shell getprop ro.build.version.release | tr -d '\r')" \
  "(API $("${adb}" -s "${serial}" shell getprop ro.build.version.sdk | tr -d '\r'))," \
  "$("${adb}" -s "${serial}" shell getprop ro.product.cpu.abi | tr -d '\r')," \
  "page size $("${adb}" -s "${serial}" shell getconf PAGE_SIZE | tr -d '\r')," \
  "booted after about ${waited} seconds"
echo "ANDROID_SERIAL=${serial}"
