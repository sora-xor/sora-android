#!/usr/bin/env bash
set -euo pipefail

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

./gradlew \
  :app:assembleQualificationDebug \
  :app:assembleQualificationDebugAndroidTest \
  --stacktrace --no-daemon --no-parallel

resolve_apkanalyzer() {
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

  echo "error: apkanalyzer is required to prove APK package isolation before install" >&2
  exit 1
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

apkanalyzer="$(resolve_apkanalyzer)"
readonly apkanalyzer
qualification_target_apk="$(
  resolve_single_apk "${qualification_target_output}" "qualification target"
)"
readonly qualification_target_apk
qualification_test_apk="$(
  resolve_single_apk "${qualification_test_output}" "qualification test"
)"
readonly qualification_test_apk
built_target_package="$(
  "${apkanalyzer}" manifest application-id "${qualification_target_apk}" | tr -d '\r\n'
)"
readonly built_target_package
built_test_package="$(
  "${apkanalyzer}" manifest application-id "${qualification_test_apk}" | tr -d '\r\n'
)"
readonly built_test_package
built_target_manifest="$(
  "${apkanalyzer}" manifest print "${qualification_target_apk}" | tr -d '\r'
)"
readonly built_target_manifest
built_test_manifest="$(
  "${apkanalyzer}" manifest print "${qualification_test_apk}" | tr -d '\r'
)"
readonly built_test_manifest
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
if grep -Fq 'android:sharedUserId' <<<"${built_target_manifest}" ||
  grep -Fq 'android:sharedUserId' <<<"${built_test_manifest}"; then
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
