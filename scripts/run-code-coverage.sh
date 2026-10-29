#!/usr/bin/env bash
# Full app coverage: all productionDebug JVM code plus the real device suites.
set -euo pipefail
script_dir="$(cd "$(dirname "$0")" && pwd)"
source "${script_dir}/device-test-common.sh"
usage() {
  cat <<'USAGE'
usage: run-code-coverage.sh [--offline]
Runs every productionDebug unit test and all single-process device suites, merges
unique executable source-line hits, and fails below 90.01%. Use a disposable 4 KB
Android emulator; ANDROID_SERIAL selects it when multiple devices are attached.
Reports: build/reports/coverage/summary.json and each module's HTML report.
SORA_GRADLE_FLAGS accepts additional Gradle flags (see device-test-common.sh).
USAGE
}
while [ "$#" -gt 0 ]; do
  case "$1" in
    --offline) sora_use_offline_gradle ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
  shift
done
sora_read_gradle_environment
cd "${script_dir}/.."
sora_require_emulator
if [ "$(sora_device_page_size)" != 4096 ]; then
  echo "error: full coverage is qualified on a 4 KB emulator; the MockK agent remains unqualified for the full 16 KB suite" >&2
  exit 1
fi
python3 scripts/test-code-coverage.py
mkdir -p build/reports/coverage
manifest="build/reports/coverage/source-manifest.json"
python3 scripts/check-code-coverage.py --write-source-manifest "${manifest}"
sora_gradle --dependency-verification=strict --max-workers=4 \
  testProductionDebugUnitTest :app:lintProductionDebug \
  :app:koverXmlReportProductionDebug :app:koverHtmlReportProductionDebug
sora_gradle --dependency-verification=strict --max-workers=4 --continue \
  -I scripts/device-coverage.gradle \
  :common:createProductionDebugAndroidTestCoverageReport \
  :sorasubstrate:createProductionDebugAndroidTestCoverageReport \
  :core_db:createProductionDebugAndroidTestCoverageReport \
  :feature_account_impl:createProductionDebugAndroidTestCoverageReport \
  :feature_assets_impl:createProductionDebugAndroidTestCoverageReport \
  :feature_blockexplorer_api:createProductionDebugAndroidTestCoverageReport \
  :app:createProductionDebugAndroidTestCoverageReport \
  -Pandroid.testInstrumentationRunnerArguments.notClass=jp.co.soramitsu.feature_account_impl.data.repository.datasource.EncryptedWalletMigrationStorageTest
sora_gradle --dependency-verification=strict --max-workers=4 \
  -I scripts/device-coverage.gradle aggregateProductionDebugDeviceCoverage
# This class requires five ordered, independent processes and has its own gate.
# Its code remains in the denominator; ordinary device/JVM reports supply its hits.
qualification_args=()
if [ "${sora_offline}" -eq 1 ]; then qualification_args+=(--offline); fi
bash scripts/run-encrypted-wallet-upgrade-qualification.sh ${qualification_args[@]+"${qualification_args[@]}"}
bash scripts/run-migration-manager-production-path-qualification.sh ${qualification_args[@]+"${qualification_args[@]}"}
python3 scripts/check-code-coverage.py --source-manifest "${manifest}" \
  --output build/reports/coverage/summary.json app/build/reports/kover/reportProductionDebug.xml build/reports/coverage/device-aggregate.xml
