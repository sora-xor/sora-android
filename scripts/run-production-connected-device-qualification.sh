#!/usr/bin/env bash
set -euo pipefail

# Wallet-upgrade device tests for a release candidate. Emulator only; never on a phone with
# wallets: :app:connectedProductionDebugAndroidTest installs the production package ID
# (jp.co.soramitsu.sora) and uninstalls it after the tests, which deletes that app's data,
# wallets included. With more than one device attached, set ANDROID_SERIAL to the emulator
# first (adb and Gradle both honour it).
#
# The "Android Upgrade Device Tests" workflow runs this script, and "Android Release" does not
# build a signed artifact unless it passed on the same commit. Run it by hand, with --offline,
# on a machine without network access.

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/device-test-common.sh
source "${script_dir}/device-test-common.sh"

usage() {
  cat <<'USAGE'
usage: run-production-connected-device-qualification.sh [--offline] [--report-dir DIR]

  --offline         build with Gradle's --offline (local Gradle cache, no network)
  --report-dir DIR  where the logs and summary.txt go
                    (default: build/device-qualification)

Runs every group even when one fails, prints a summary with the commit and the device, and
exits with 1 when any group failed or ran no test.

Environment: SORA_GRADLE_FLAGS (extra Gradle arguments), ANDROID_SERIAL (emulator to use),
ANDROID_HOME (to find adb, apkanalyzer or aapt2).
USAGE
}

report_dir=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    --offline) sora_use_offline_gradle ;;
    --report-dir)
      if [ "$#" -lt 2 ] || [ -z "$2" ]; then
        echo "error: --report-dir needs a directory" >&2
        exit 2
      fi
      report_dir="$2"
      shift
      ;;
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
sora_require_emulator

if ! command -v python3 >/dev/null 2>&1; then
  echo "error: python3 is required to count the test results." >&2
  exit 1
fi

report_dir="${report_dir:-build/device-qualification}"
mkdir -p "${report_dir}"
report_dir="$(cd "${report_dir}" && pwd)"
readonly report_dir
readonly summary_file="${report_dir}/summary.txt"
rm -f "${report_dir}"/[0-9][0-9]-*.log "${summary_file}"

# Its five phases need a new process each; scripts/run-encrypted-wallet-upgrade-qualification.sh
# runs them. Everything else in :feature_account_impl runs in the ordinary connected task.
readonly phased_storage_class="jp.co.soramitsu.feature_account_impl.data.repository.datasource.EncryptedWalletMigrationStorageTest"

group_names=()
group_results=()
failed_groups=0

# Prints "tests failures skipped" summed over the JUnit XML files Gradle wrote for a module.
count_connected_results() {
  python3 - "$@" <<'PY'
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

tests = failures = skipped = 0
for module in sys.argv[1:]:
    for path in Path(module, "build", "outputs", "androidTest-results", "connected").rglob("TEST-*.xml"):
        root = ElementTree.parse(path).getroot()
        tests += int(root.get("tests", "0"))
        failures += int(root.get("failures", "0")) + int(root.get("errors", "0"))
        skipped += int(root.get("skipped", "0"))
print(tests, failures, skipped)
PY
}

record_group() {
  group_names+=("$1")
  group_results+=("$2")
  if [[ "$2" != PASS* ]]; then
    failed_groups=$((failed_groups + 1))
  fi
}

# run_connected_group NAME "MODULE..." GRADLE_ARGUMENT...
run_connected_group() {
  local name="$1"
  local modules="$2"
  shift 2
  local log_file="${report_dir}/${name}.log"
  local status=0
  local module=""
  local counts=""
  local tests=0
  local failures=0
  local skipped=0

  echo "==> ${name}"
  for module in ${modules}; do
    rm -rf "${module}/build/outputs/androidTest-results/connected"
  done
  sora_gradle "$@" >"${log_file}" 2>&1 || status=$?
  tail -n 25 "${log_file}"
  # shellcheck disable=SC2086
  counts="$(count_connected_results ${modules})"
  read -r tests failures skipped <<<"${counts}"
  if [ "${status}" -ne 0 ] || [ "${failures}" -ne 0 ]; then
    record_group "${name}" "FAIL (${tests} tests, ${failures} failed, ${skipped} skipped; Gradle exit ${status})"
  elif [ "${tests}" -eq 0 ] || [ "${tests}" -eq "${skipped}" ]; then
    # A class filter that matches nothing must not look like a pass.
    record_group "${name}" "FAIL (no test ran)"
  else
    record_group "${name}" "PASS (${tests} tests, 0 failed, ${skipped} skipped)"
  fi
}

# run_script_group NAME EXPECTED_PHASES SCRIPT
run_script_group() {
  local name="$1"
  local expected_phases="$2"
  local script="$3"
  local log_file="${report_dir}/${name}.log"
  local status=0
  local passed_phases=0

  echo "==> ${name}"
  # shellcheck disable=SC2046
  bash "${script}" $(sora_offline_option) >"${log_file}" 2>&1 || status=$?
  tail -n 25 "${log_file}"
  passed_phases="$(grep -c '^OK (1 test)' "${log_file}" || true)"
  if [ "${status}" -ne 0 ]; then
    record_group "${name}" "FAIL (${passed_phases} of ${expected_phases} phases passed; exit ${status})"
  elif [ "${passed_phases}" -ne "${expected_phases}" ]; then
    record_group "${name}" "FAIL (${passed_phases} phases passed, expected ${expected_phases})"
  else
    record_group "${name}" "PASS (${passed_phases} of ${expected_phases} phases)"
  fi
}

run_connected_group "01-common-sorasubstrate" "common sorasubstrate" \
  :common:connectedProductionDebugAndroidTest \
  :sorasubstrate:connectedProductionDebugAndroidTest

# Room migrations, the pre-upgrade backup and the card-hub rows.
run_connected_group "02-core-db" "core_db" \
  :core_db:connectedProductionDebugAndroidTest

# Upgrade scenarios written with the storage writers of the released apps (abandoned creates,
# dangling selection, partial key sets, an undecryptable account, low storage, interrupted
# migration, busy Keystore), and every other device test of the module except the phased one.
run_connected_group "03-feature-account-upgrade-scenarios" "feature_account_impl" \
  :feature_account_impl:connectedProductionDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.notClass=${phased_storage_class}"

# Every device test of the app module: the recovery (Safe Mode) screens, the sr25519
# known-answer test, the PIN lock and the wallet acceptance tests. No class filter, so a test
# class added later is part of the gate without a change here.
run_connected_group "04-app" "app" \
  :app:connectedProductionDebugAndroidTest

# Screens that depend on the state of an account: Send for a read-only account, locked XOR.
run_connected_group "05-feature-screens" "feature_assets_impl feature_blockexplorer_api" \
  :feature_assets_impl:connectedProductionDebugAndroidTest \
  :feature_blockexplorer_api:connectedProductionDebugAndroidTest

run_script_group "06-encrypted-wallet-storage-phases" 5 \
  scripts/run-encrypted-wallet-upgrade-qualification.sh
run_script_group "07-migration-manager-production-path-phases" 10 \
  scripts/run-migration-manager-production-path-qualification.sh

commit="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
if [ -n "$(git status --porcelain 2>/dev/null || true)" ]; then
  commit="${commit} (with uncommitted changes)"
fi
device_property() {
  adb shell getprop "$1" | tr -d '\r'
}
{
  echo "SORA Wallet Android: wallet-upgrade device tests"
  echo "Date (UTC): $(date -u '+%Y-%m-%d %H:%M:%S')"
  echo "Commit: ${commit}"
  echo "Device: ${ANDROID_SERIAL:-$(adb get-serialno | tr -d '\r')}," \
    "Android $(device_property ro.build.version.release) (API $(device_property ro.build.version.sdk))," \
    "$(device_property ro.product.cpu.abi), page size $(adb shell getconf PAGE_SIZE | tr -d '\r')"
  if [ "${sora_offline}" -eq 1 ]; then
    echo "Gradle: offline"
  else
    echo "Gradle: online"
  fi
  echo
  index=0
  while [ "${index}" -lt "${#group_names[@]}" ]; do
    printf '%-48s %s\n' "${group_names[${index}]}" "${group_results[${index}]}"
    index=$((index + 1))
  done
  echo
  if [ "${failed_groups}" -eq 0 ]; then
    echo "Result: PASS"
  else
    echo "Result: FAIL (${failed_groups} of ${#group_names[@]} groups)"
  fi
} | tee "${summary_file}"
echo "Logs and summary: ${report_dir}"

if [ "${failed_groups}" -ne 0 ]; then
  exit 1
fi
