#!/usr/bin/env bash
set -euo pipefail

# Encrypted wallet storage across real process restarts: five ordered phases, each in its own
# instrumentation process, in the isolated feature_account_impl test package.

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/device-test-common.sh
source "${script_dir}/device-test-common.sh"

usage() {
  cat <<'USAGE'
usage: run-encrypted-wallet-upgrade-qualification.sh [--offline]

  --offline   build with Gradle's --offline (local Gradle cache, no network)

Environment: SORA_GRADLE_FLAGS (extra Gradle arguments), ANDROID_SERIAL (device to use).
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

readonly qualification_class="jp.co.soramitsu.feature_account_impl.data.repository.datasource.EncryptedWalletMigrationStorageTest"
readonly qualification_package="jp.co.soramitsu.feature_account_impl.test"
readonly qualification_runner="${qualification_package}/androidx.test.runner.AndroidJUnitRunner"
readonly qualification_tmp="$(mktemp -d "${TMPDIR:-/tmp}/sora-wallet-upgrade-qualification.XXXXXX")"

cleanup_qualification_tmp() {
  rm -rf -- "${qualification_tmp}"
}
trap cleanup_qualification_tmp EXIT

sora_gradle :feature_account_impl:installProductionDebugAndroidTest

adb wait-for-device
instrumentation_listing="$(adb shell pm list instrumentation | tr -d '\r')"
qualification_instrumentation_line="$(
  grep -m 1 -F "instrumentation:${qualification_runner} " \
    <<<"${instrumentation_listing}" || true
)"
if [ -z "${qualification_instrumentation_line}" ]; then
  echo "error: exact encrypted-wallet qualification runner is not installed" >&2
  exit 1
fi
qualification_target="$(
  sed -n 's/.*(target=\([^)]*\)).*/\1/p' <<<"${qualification_instrumentation_line}"
)"
if [ -z "${qualification_target}" ]; then
  echo "error: encrypted-wallet qualification target package is unresolved" >&2
  exit 1
fi

run_qualification_phase() {
  local phase_name="$1"
  local method_name="$2"
  local phase_output="${qualification_tmp}/${phase_name}.txt"
  local instrumentation_status=0

  adb shell am instrument -w -r \
    -e class "${qualification_class}#${method_name}" \
    "${qualification_runner}" >"${phase_output}" 2>&1 || instrumentation_status=$?
  adb shell am force-stop "${qualification_target}"
  adb shell am force-stop "${qualification_package}"
  cat "${phase_output}"

  if [ "${instrumentation_status}" -ne 0 ] ||
    ! grep -Eq '^OK \(1 test\)$' "${phase_output}" ||
    grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' "${phase_output}"; then
    echo "error: encrypted-wallet qualification phase ${phase_name} failed" >&2
    exit 1
  fi
}

run_qualification_phase \
  "01-seed-retained-shared-preferences" \
  "phase01SeedRetainedSharedPreferencesAndVerifiedBackup"
run_qualification_phase \
  "02-import-after-process-restart" \
  "phase02ImportLegacyCiphertextAfterProcessRestart"
run_qualification_phase \
  "03-read-retained-datastore-after-second-restart" \
  "phase03ReadRetainedDataStoreAfterSecondProcessRestart"
run_qualification_phase \
  "04-corruption-fails-closed" \
  "phase04AuthenticatedEnvelopeAndWrappedKeyCorruptionFailClosed"
run_qualification_phase \
  "05-missing-alias-fails-closed" \
  "phase05MissingKeystoreAliasNeverCreatesReplacement"
