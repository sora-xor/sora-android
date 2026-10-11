#!/usr/bin/env bash
set -euo pipefail

# MigrationManager through the production start path, one method per process, in the isolated
# jp.co.soramitsu.sora.qualification package (never the production package). Ten phases:
# seven stored-secret shapes, then the three ordered phases that take a pre-multiaccount
# wallet at Room 50 (release 2.3.3 layout) through every migration to the current schema and
# through a restart.

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/device-test-common.sh
source "${script_dir}/device-test-common.sh"

usage() {
  cat <<'USAGE'
usage: run-migration-manager-production-path-qualification.sh [--offline]

  --offline   build with Gradle's --offline (local Gradle cache, no network)

Environment: SORA_GRADLE_FLAGS (extra Gradle arguments), ANDROID_SERIAL (device to use),
ANDROID_HOME (to find apkanalyzer or aapt2).
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --offline) sora_use_offline_gradle ;;
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
sora_read_gradle_environment
cd "${script_dir}/.."
sora_require_one_device

readonly qualification_class="jp.co.soramitsu.sora.splash.domain.MigrationManagerProductionPathQualificationTest"
readonly qualification_target_package="jp.co.soramitsu.sora.qualification"
readonly qualification_test_package="jp.co.soramitsu.sora.qualification.test"
readonly qualification_runner="${qualification_test_package}/androidx.test.runner.AndroidJUnitRunner"
readonly qualification_target_output="app/build/outputs/apk/qualification/debug"
readonly qualification_test_output="app/build/outputs/apk/androidTest/qualification/debug"
qualification_tmp="$(mktemp -d "${TMPDIR:-/tmp}/sora-migration-manager-qualification.XXXXXX")"
readonly qualification_tmp

cleanup_qualification_tmp() {
  rm -rf -- "${qualification_tmp}"
}
trap cleanup_qualification_tmp EXIT

sora_gradle :app:assembleQualificationDebug :app:assembleQualificationDebugAndroidTest

# apkanalyzer comes with the SDK command-line tools, which an offline machine may not have.
# aapt2 from build-tools reads the same two facts (package name, sharedUserId). Each finder
# prints a path, or nothing.
find_apkanalyzer() {
  local candidate=""
  local sdk_root=""

  candidate="$(command -v apkanalyzer 2>/dev/null || true)"
  if [ -n "${candidate}" ] && [ -x "${candidate}" ]; then
    printf '%s\n' "${candidate}"
    return
  fi

  for sdk_root in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}"; do
    if [ -z "${sdk_root}" ]; then
      continue
    fi
    for candidate in \
      "${sdk_root}/cmdline-tools/latest/bin/apkanalyzer" \
      "${sdk_root}/tools/bin/apkanalyzer"; do
      if [ -x "${candidate}" ]; then
        printf '%s\n' "${candidate}"
        return
      fi
    done
    for candidate in "${sdk_root}"/cmdline-tools/*/bin/apkanalyzer; do
      if [ -x "${candidate}" ]; then
        printf '%s\n' "${candidate}"
        return
      fi
    done
  done
}

find_aapt2() {
  local candidate=""
  local sdk_root=""

  candidate="$(command -v aapt2 2>/dev/null || true)"
  if [ -n "${candidate}" ] && [ -x "${candidate}" ]; then
    printf '%s\n' "${candidate}"
    return
  fi
  for sdk_root in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}"; do
    if [ -z "${sdk_root}" ]; then
      continue
    fi
    # Any installed aapt2 can print a manifest, so the order of the versions does not matter.
    for candidate in "${sdk_root}"/build-tools/*/aapt2; do
      if [ -x "${candidate}" ]; then
        printf '%s\n' "${candidate}"
        return
      fi
    done
  done
}

apk_application_id() {
  if [ "${apk_inspector_kind}" = "apkanalyzer" ]; then
    "${apk_inspector}" manifest application-id "$1" | tr -d '\r\n'
  else
    "${apk_inspector}" dump packagename "$1" | tr -d '\r\n'
  fi
}

apk_manifest_text() {
  if [ "${apk_inspector_kind}" = "apkanalyzer" ]; then
    "${apk_inspector}" manifest print "$1" | tr -d '\r'
  else
    "${apk_inspector}" dump xmltree --file AndroidManifest.xml "$1" | tr -d '\r'
  fi
}

resolve_single_apk() {
  local output_directory="$1"
  local description="$2"
  local matches=()
  local candidate=""

  if [ ! -d "${output_directory}" ]; then
    echo "error: ${description} APK output directory is missing" >&2
    exit 1
  fi
  while IFS= read -r -d '' candidate; do
    matches+=("${candidate}")
  done < <(find "${output_directory}" -type f -name '*.apk' -print0)
  if [ "${#matches[@]}" -ne 1 ]; then
    echo "error: expected exactly one ${description} APK, found ${#matches[@]}" >&2
    exit 1
  fi
  printf '%s\n' "${matches[0]}"
}

# SORA_APK_INSPECTOR=aapt2 uses aapt2 even when apkanalyzer is installed.
apk_inspector_kind="apkanalyzer"
apk_inspector=""
if [ "${SORA_APK_INSPECTOR:-}" != "aapt2" ]; then
  apk_inspector="$(find_apkanalyzer)"
fi
if [ -z "${apk_inspector}" ]; then
  apk_inspector_kind="aapt2"
  apk_inspector="$(find_aapt2)"
fi
if [ -z "${apk_inspector}" ]; then
  echo "error: apkanalyzer or aapt2 is required to prove APK package isolation before install" >&2
  exit 1
fi
readonly apk_inspector_kind
readonly apk_inspector
qualification_target_apk="$(
  resolve_single_apk "${qualification_target_output}" "qualification target"
)"
readonly qualification_target_apk
qualification_test_apk="$(
  resolve_single_apk "${qualification_test_output}" "qualification test"
)"
readonly qualification_test_apk
built_target_package="$(apk_application_id "${qualification_target_apk}")"
readonly built_target_package
built_test_package="$(apk_application_id "${qualification_test_apk}")"
readonly built_test_package
built_target_manifest="$(apk_manifest_text "${qualification_target_apk}")"
readonly built_target_manifest
built_test_manifest="$(apk_manifest_text "${qualification_test_apk}")"
readonly built_test_manifest
if [ -z "${built_target_manifest}" ] || [ -z "${built_test_manifest}" ]; then
  echo "error: could not read the manifest of a qualification APK" >&2
  exit 1
fi
if [ "${built_target_package}" != "${qualification_target_package}" ]; then
  echo "error: refusing to install non-isolated MigrationManager target APK" >&2
  exit 1
fi
if [ "${built_test_package}" != "${qualification_test_package}" ]; then
  echo "error: refusing to install non-isolated MigrationManager test APK" >&2
  exit 1
fi
if [ "${built_target_package}" = "jp.co.soramitsu.sora" ] ||
  [ "${built_test_package}" = "jp.co.soramitsu.sora.test" ]; then
  echo "error: built qualification APK aliases production" >&2
  exit 1
fi
# apkanalyzer prints android:sharedUserId="..."; aapt2 prints ...android:sharedUserId(0x0101000b)=...
if grep -Fq 'sharedUserId' <<<"${built_target_manifest}" ||
  grep -Fq 'sharedUserId' <<<"${built_test_manifest}"; then
  echo "error: qualification APK must not declare a shared Android UID" >&2
  exit 1
fi

adb wait-for-device
adb install -r -t "${qualification_target_apk}"
adb install -r -t "${qualification_test_apk}"
instrumentation_listing="$(adb shell pm list instrumentation | tr -d '\r')"
qualification_instrumentation_line="$(
  grep -m 1 -F "instrumentation:${qualification_runner} " \
    <<<"${instrumentation_listing}" || true
)"
if [ -z "${qualification_instrumentation_line}" ]; then
  echo "error: exact isolated MigrationManager qualification runner is not installed" >&2
  exit 1
fi
qualification_target="$(
  sed -n 's/.*(target=\([^)]*\)).*/\1/p' <<<"${qualification_instrumentation_line}"
)"
if [ "${qualification_target}" != "${qualification_target_package}" ]; then
  echo "error: refusing non-isolated MigrationManager target package" >&2
  exit 1
fi
if [ "${qualification_test_package}" = "jp.co.soramitsu.sora.test" ] ||
  [ "${qualification_target_package}" = "jp.co.soramitsu.sora" ]; then
  echo "error: qualification package aliases production" >&2
  exit 1
fi
if ! adb shell pm path "${qualification_target_package}" | tr -d '\r' |
  grep -Eq '^package:'; then
  echo "error: isolated MigrationManager target package is not installed" >&2
  exit 1
fi
if ! adb shell pm path "${qualification_test_package}" | tr -d '\r' |
  grep -Eq '^package:'; then
  echo "error: isolated MigrationManager test package is not installed" >&2
  exit 1
fi

run_qualification_method() {
  local phase_name="$1"
  local method_name="$2"
  local phase_output="${qualification_tmp}/${phase_name}.txt"
  local instrumentation_status=0

  adb shell am instrument -w -r \
    -e class "${qualification_class}#${method_name}" \
    "${qualification_runner}" >"${phase_output}" 2>&1 || instrumentation_status=$?
  adb shell am force-stop "${qualification_target_package}"
  adb shell am force-stop "${qualification_test_package}"
  tr -d '\r' <"${phase_output}" >"${phase_output}.normalized"
  mv "${phase_output}.normalized" "${phase_output}"
  cat "${phase_output}"

  if [ "${instrumentation_status}" -ne 0 ] ||
    ! grep -Eq '^OK \(1 test\)$' "${phase_output}" ||
    grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' "${phase_output}"; then
    echo "error: MigrationManager qualification phase ${phase_name} failed" >&2
    exit 1
  fi
}

# Each invocation gets a fresh target process. Every method independently proves the exact
# qualification package before deleting its own sandboxed state.
run_qualification_method \
  "01-twelve-word-mnemonic" \
  "qualifyTwelveWordMnemonicThroughProductionPath"
run_qualification_method \
  "02-twenty-four-word-mnemonic" \
  "qualifyTwentyFourWordMnemonicThroughProductionPath"
run_qualification_method \
  "03-retained-fifteen-word-mnemonic" \
  "qualifyRetainedFifteenWordMnemonicThroughProductionPath"
run_qualification_method \
  "04-raw-seed" \
  "qualifyRawSeedThroughProductionPath"
run_qualification_method \
  "05-legacy-secret" \
  "qualifyLegacySecretThroughProductionPath"
run_qualification_method \
  "06-watch-only" \
  "qualifyExplicitWatchOnlyThroughProductionPath"
run_qualification_method \
  "07-two-account-selection" \
  "qualifyTwoAccountExactSelectionThroughProductionPath"

# Pre-multiaccount wallet at Room 50 (release 2.3.3 layout). The three phases share state and
# must run in this order: write the old storage, upgrade it through the production database
# builder (15..57 -> 58 and every later migration), then start again as a new process.
run_qualification_method \
  "08-room-50-prepare" \
  "preparePreMultiaccountRoom50NativeFixture"
run_qualification_method \
  "09-room-50-upgrade" \
  "upgradePreMultiaccountRoom50ThroughProductionPath"
run_qualification_method \
  "10-room-50-restart" \
  "restartPreMultiaccountUpgradeThroughProductionPath"

echo "MigrationManager production-path qualification passed: 10 phases"
