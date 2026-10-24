#!/usr/bin/env python3
"""Tests for the device-test scripts, without a device.

The real scripts are copied into a temporary project whose ./gradlew, adb, apkanalyzer and
aapt2 are small recorders. That checks the parts a release depends on and an emulator run
does not show when they are missing: --offline reaches every Gradle call, every phase is
called in order (the three Room-50 phases included), every group runs even after a failure,
a group without tests fails, and a phone or an ambiguous device is refused.
"""

from __future__ import annotations

import json
import os
import shutil
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent
COPIED = (
    "device-test-common.sh",
    "run-production-connected-device-qualification.sh",
    "run-encrypted-wallet-upgrade-qualification.sh",
    "run-migration-manager-production-path-qualification.sh",
)

GRADLEW = r"""#!/bin/bash
# Records its arguments and leaves the files the real tasks would leave.
printf '%s\n' "$*" >> "$STUB_LOG/gradle.log"
for argument in "$@"; do
  case "$argument" in
    :*:connectedProductionDebugAndroidTest)
      module="${argument#:}"; module="${module%%:*}"
      tests_variable="STUB_TESTS_${module}"; failures_variable="STUB_FAILURES_${module}"
      directory="$module/build/outputs/androidTest-results/connected/debug/flavors/production"
      mkdir -p "$directory"
      printf '<testsuite tests="%s" failures="%s" errors="0" skipped="0"/>\n' \
        "${!tests_variable:-3}" "${!failures_variable:-0}" > "$directory/TEST-stub.xml"
      if [ "${!failures_variable:-0}" != "0" ]; then exit 1; fi
      ;;
    :app:assembleQualificationDebug)
      mkdir -p app/build/outputs/apk/qualification/debug
      : > app/build/outputs/apk/qualification/debug/target.apk
      ;;
    :app:assembleQualificationDebugAndroidTest)
      mkdir -p app/build/outputs/apk/androidTest/qualification/debug
      : > app/build/outputs/apk/androidTest/qualification/debug/test.apk
      ;;
  esac
done
"""

ADB = r"""#!/bin/bash
case "$1" in
  devices)
    echo "List of devices attached"
    printf '%b' "${STUB_DEVICES-emulator-5554\tdevice\n}"
    ;;
  get-serialno) echo "emulator-5554" ;;
  wait-for-device) ;;
  install) echo "Success" ;;
  shell)
    shift
    case "$*" in
      "getprop ro.boot.qemu; getprop ro.kernel.qemu") echo "${STUB_QEMU-1}"; echo ;;
      "getprop ro.build.version.release") echo "14" ;;
      "getprop ro.build.version.sdk") echo "34" ;;
      "getprop ro.product.cpu.abi") echo "x86_64" ;;
      "getconf PAGE_SIZE") echo "${STUB_PAGE_SIZE:-4096}" ;;
      "pm list instrumentation")
        echo "instrumentation:jp.co.soramitsu.feature_account_impl.test/androidx.test.runner.AndroidJUnitRunner (target=jp.co.soramitsu.feature_account_impl.test)"
        echo "instrumentation:jp.co.soramitsu.sora.qualification.test/androidx.test.runner.AndroidJUnitRunner (target=jp.co.soramitsu.sora.qualification)"
        ;;
      "pm path "*) echo "package:/data/app/base.apk" ;;
      "am force-stop "*) ;;
      "am instrument "*)
        method="$(sed -n 's/.*-e class [^ #]*#\([A-Za-z0-9_]*\) .*/\1/p' <<<"$*")"
        echo "$method" >> "$STUB_LOG/instrument.log"
        if [ -n "${STUB_FAILING_METHOD:-}" ] && [ "$method" = "$STUB_FAILING_METHOD" ]; then
          echo "FAILURES!!!"
          echo "Tests run: 1,  Failures: 1"
        else
          echo
          echo "OK (1 test)"
        fi
        ;;
      *) echo "unexpected adb shell $*" >&2; exit 1 ;;
    esac
    ;;
  *) echo "unexpected adb $*" >&2; exit 1 ;;
esac
"""

# Both inspectors answer from the file name: target.apk is the app, test.apk its test APK.
APKANALYZER = r"""#!/bin/bash
echo "apkanalyzer" >> "$STUB_LOG/inspector.log"
package="${STUB_TARGET_PACKAGE:-jp.co.soramitsu.sora.qualification}"
case "$3" in *test.apk) package="${package}.test" ;; esac
case "$1 $2" in
  "manifest application-id") echo "$package" ;;
  "manifest print") echo "<manifest package=\"$package\" ${STUB_MANIFEST_EXTRA:-}>" ;;
  *) exit 1 ;;
esac
"""

AAPT2 = r"""#!/bin/bash
echo "aapt2" >> "$STUB_LOG/inspector.log"
package="${STUB_TARGET_PACKAGE:-jp.co.soramitsu.sora.qualification}"
apk="${!#}"
case "$apk" in *test.apk) package="${package}.test" ;; esac
case "$1 $2" in
  "dump packagename") echo "$package" ;;
  "dump xmltree") echo "E: manifest"; echo "  A: package=\"$package\" ${STUB_MANIFEST_EXTRA:-}" ;;
  *) exit 1 ;;
esac
"""

ENCRYPTED_PHASES = [
    "phase01SeedRetainedSharedPreferencesAndVerifiedBackup",
    "phase02ImportLegacyCiphertextAfterProcessRestart",
    "phase03ReadRetainedDataStoreAfterSecondProcessRestart",
    "phase04AuthenticatedEnvelopeAndWrappedKeyCorruptionFailClosed",
    "phase05MissingKeystoreAliasNeverCreatesReplacement",
]
MIGRATION_PHASES = [
    "qualifyTwelveWordMnemonicThroughProductionPath",
    "qualifyTwentyFourWordMnemonicThroughProductionPath",
    "qualifyRetainedFifteenWordMnemonicThroughProductionPath",
    "qualifyRawSeedThroughProductionPath",
    "qualifyLegacySecretThroughProductionPath",
    "qualifyExplicitWatchOnlyThroughProductionPath",
    "qualifyTwoAccountExactSelectionThroughProductionPath",
    "preparePreMultiaccountRoom50NativeFixture",
    "upgradePreMultiaccountRoom50ThroughProductionPath",
    "restartPreMultiaccountUpgradeThroughProductionPath",
]
GRADLE_CALLS = 7  # five connected groups, the phased-storage install, the qualification APKs


def executable(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)
    path.chmod(path.stat().st_mode | stat.S_IXUSR)


class DeviceScriptsTest(unittest.TestCase):
    def setUp(self):
        self._directory = tempfile.TemporaryDirectory(prefix="sora-device-scripts-")
        self.addCleanup(self._directory.cleanup)
        self.root = Path(self._directory.name).resolve()
        (self.root / "scripts").mkdir()
        for name in COPIED:
            shutil.copy(SCRIPTS / name, self.root / "scripts" / name)
        self.log = self.root / "log"
        self.log.mkdir()
        self.bin = self.root / "bin"
        executable(self.root / "gradlew", GRADLEW)
        executable(self.bin / "adb", ADB)
        executable(self.bin / "apkanalyzer", APKANALYZER)
        self.report = self.root / "report"

    def run_script(self, name="run-production-connected-device-qualification.sh", *arguments, **environment):
        base = {
            "PATH": f"{self.bin}:/usr/bin:/bin",
            "HOME": str(self.root),
            "TMPDIR": str(self.root),
            "STUB_LOG": str(self.log),
        }
        base.update(environment)
        if name.startswith("run-production") and "--help" not in arguments:
            arguments = (*arguments, "--report-dir", str(self.report))
        return subprocess.run(
            ["bash", str(self.root / "scripts" / name), *arguments],
            cwd=tempfile.gettempdir(),  # the scripts must not depend on the caller's directory
            capture_output=True,
            text=True,
            check=False,
            env=base,
        )

    def lines(self, name: str) -> list[str]:
        path = self.log / name
        return path.read_text().splitlines() if path.is_file() else []

    def summary(self) -> str:
        return (self.report / "summary.txt").read_text()

    def test_offline_reaches_every_gradle_call(self):
        result = self.run_script("run-production-connected-device-qualification.sh", "--offline")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        calls = self.lines("gradle.log")
        self.assertEqual(GRADLE_CALLS, len(calls), calls)
        for call in calls:
            self.assertEqual(1, call.split().count("--offline"), call)
            self.assertIn("--no-daemon", call)
        self.assertIn("Gradle: offline", self.summary())

    def test_online_by_default_and_flags_from_the_environment(self):
        result = self.run_script()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertTrue(all("--offline" not in call for call in self.lines("gradle.log")))
        self.assertIn("Gradle: online", self.summary())

        (self.log / "gradle.log").unlink()
        result = self.run_script(SORA_GRADLE_FLAGS="--dependency-verification=strict --offline")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        calls = self.lines("gradle.log")
        self.assertEqual(GRADLE_CALLS, len(calls))
        for call in calls:
            self.assertEqual(1, call.split().count("--offline"), call)
            self.assertIn("--dependency-verification=strict", call)

    def test_every_group_and_phase_runs_in_order(self):
        result = self.run_script()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(ENCRYPTED_PHASES + MIGRATION_PHASES, self.lines("instrument.log"))
        calls = " ".join(self.lines("gradle.log"))
        for task in (
            ":common:connectedProductionDebugAndroidTest",
            ":sorasubstrate:connectedProductionDebugAndroidTest",
            ":core_db:connectedProductionDebugAndroidTest",
            ":feature_account_impl:connectedProductionDebugAndroidTest",
            ":app:connectedProductionDebugAndroidTest",
            ":feature_assets_impl:connectedProductionDebugAndroidTest",
            ":feature_blockexplorer_api:connectedProductionDebugAndroidTest",
        ):
            self.assertIn(task, calls)
        # The phased class is left to its own script; nothing else is filtered out.
        self.assertIn(
            "-Pandroid.testInstrumentationRunnerArguments.notClass="
            "jp.co.soramitsu.feature_account_impl.data.repository.datasource.EncryptedWalletMigrationStorageTest",
            calls,
        )
        self.assertNotIn("testInstrumentationRunnerArguments.class=", calls)
        summary = self.summary()
        self.assertIn("Result: PASS", summary)
        self.assertIn("PASS (5 of 5 phases)", summary)
        self.assertIn("PASS (10 of 10 phases)", summary)
        self.assertIn("Android 14 (API 34), x86_64, page size 4096", summary)
        self.assertEqual(7, summary.count("PASS ("))

    def test_a_failing_phase_fails_the_run_but_the_other_groups_still_run(self):
        result = self.run_script(STUB_FAILING_METHOD="phase02ImportLegacyCiphertextAfterProcessRestart")
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertEqual(GRADLE_CALLS, len(self.lines("gradle.log")))
        summary = self.summary()
        self.assertIn("06-encrypted-wallet-storage-phases", summary)
        self.assertIn("FAIL (1 of 5 phases passed", summary)
        self.assertIn("PASS (10 of 10 phases)", summary)
        self.assertIn("Result: FAIL (1 of 7 groups)", summary)

    def test_a_failing_room_50_phase_fails_the_run(self):
        result = self.run_script(STUB_FAILING_METHOD="restartPreMultiaccountUpgradeThroughProductionPath")
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("FAIL (9 of 10 phases passed", self.summary())

    def test_failed_and_empty_connected_groups_fail(self):
        result = self.run_script(STUB_FAILURES_core_db="2")
        self.assertEqual(1, result.returncode)
        self.assertIn("02-core-db", self.summary())
        self.assertIn("FAIL (3 tests, 2 failed", self.summary())
        self.assertEqual(GRADLE_CALLS, len(self.lines("gradle.log")))

        result = self.run_script(STUB_TESTS_app="0")
        self.assertEqual(1, result.returncode)
        self.assertRegex(self.summary(), r"04-app\s+FAIL \(no test ran\)")

    def test_refuses_a_phone(self):
        result = self.run_script(STUB_QEMU="")
        self.assertEqual(1, result.returncode)
        self.assertIn("runs only on an Android emulator", result.stderr)
        self.assertEqual([], self.lines("gradle.log"))

    def test_needs_a_serial_when_two_devices_are_attached(self):
        two = "emulator-5554\\tdevice\\nemulator-5556\\tdevice\\n"
        result = self.run_script(STUB_DEVICES=two)
        self.assertEqual(1, result.returncode)
        self.assertIn("Set ANDROID_SERIAL", result.stderr)
        self.assertEqual([], self.lines("gradle.log"))

        result = self.run_script(STUB_DEVICES=two, ANDROID_SERIAL="emulator-9999")
        self.assertEqual(1, result.returncode)
        self.assertIn("is not an attached device", result.stderr)

        result = self.run_script(STUB_DEVICES=two, ANDROID_SERIAL="emulator-5556")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Device: emulator-5556", self.summary())

        result = self.run_script(STUB_DEVICES="")
        self.assertEqual(1, result.returncode)
        self.assertIn("no device is attached", result.stderr)

    def test_sixteen_kb_device_runs_the_subset_and_says_so(self):
        result = self.run_script(STUB_PAGE_SIZE="16384")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        calls = self.lines("gradle.log")
        # No build of the qualification APKs: their phases cannot run here.
        self.assertEqual(GRADLE_CALLS - 1, len(calls), calls)
        app_call = next(call for call in calls if ":app:connectedProductionDebugAndroidTest" in call)
        self.assertIn(
            "-Pandroid.testInstrumentationRunnerArguments.notClass=jp.co.soramitsu.sora.ux.WalletUxAcceptanceTest",
            app_call,
        )
        self.assertEqual(ENCRYPTED_PHASES, self.lines("instrument.log"))
        summary = self.summary()
        self.assertIn("page size 16384", summary)
        self.assertRegex(summary, r"07-migration-manager-production-path-phases\s+NOT RUN \(needs 4 KB pages\)")
        self.assertIn("  WalletUxAcceptanceTest in 04-app", summary)
        self.assertIn("  07-migration-manager-production-path-phases", summary)
        self.assertIn("Result: PASS for the 16 KB subset. This is not the release gate", summary)
        self.assertNotIn("Result: PASS\n", summary)

        # A failure on a 16 KB device is still a failure.
        result = self.run_script(STUB_PAGE_SIZE="16384", STUB_FAILURES_core_db="1")
        self.assertEqual(1, result.returncode)
        self.assertIn("Result: FAIL (1 of 7 groups)", self.summary())

    def test_four_kb_device_leaves_nothing_out(self):
        result = self.run_script()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        summary = self.summary()
        self.assertIn("Result: PASS\n", summary)
        self.assertNotIn("NOT RUN", summary)
        self.assertNotIn("Not run on this device", summary)
        self.assertNotIn("WalletUxAcceptanceTest", " ".join(self.lines("gradle.log")))

    def test_migration_manager_phases_refuse_a_sixteen_kb_device(self):
        result = self.run_script(
            "run-migration-manager-production-path-qualification.sh", STUB_PAGE_SIZE="16384"
        )
        self.assertEqual(3, result.returncode, result.stdout + result.stderr)
        self.assertIn("cannot run on a device with 16384-byte pages", result.stderr)
        self.assertEqual([], self.lines("gradle.log"))
        self.assertEqual([], self.lines("instrument.log"))

    def test_unknown_option_and_help(self):
        for name in COPIED[1:]:
            with self.subTest(script=name):
                result = self.run_script(name, "--bogus")
                self.assertEqual(2, result.returncode)
                self.assertIn("unknown option: --bogus", result.stderr)
                result = self.run_script(name, "--help")
                self.assertEqual(0, result.returncode)
                self.assertIn("--offline", result.stdout)
        self.assertEqual([], self.lines("gradle.log"))

    def test_package_isolation_is_proved_with_aapt2_when_apkanalyzer_is_missing(self):
        script = "run-migration-manager-production-path-qualification.sh"
        (self.bin / "apkanalyzer").unlink()
        result = self.run_script(script)
        self.assertEqual(1, result.returncode)
        self.assertIn("apkanalyzer or aapt2 is required", result.stderr)
        self.assertEqual([], self.lines("instrument.log"))

        sdk = self.root / "sdk"
        executable(sdk / "build-tools" / "36.0.0" / "aapt2", AAPT2)
        result = self.run_script(script, "--offline", ANDROID_HOME=str(sdk))
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual({"aapt2"}, set(self.lines("inspector.log")))
        self.assertEqual(MIGRATION_PHASES, self.lines("instrument.log"))
        self.assertIn("--offline", self.lines("gradle.log")[-1])

    def test_a_production_or_shared_uid_package_is_never_installed(self):
        script = "run-migration-manager-production-path-qualification.sh"
        sdk = self.root / "sdk"
        executable(sdk / "build-tools" / "36.0.0" / "aapt2", AAPT2)
        for inspector in ("apkanalyzer", "aapt2"):
            for environment, message in (
                ({"STUB_TARGET_PACKAGE": "jp.co.soramitsu.sora"}, "refusing to install non-isolated"),
                ({"STUB_MANIFEST_EXTRA": "android:sharedUserId(0x0101000b)=\"x\""}, "must not declare a shared Android UID"),
            ):
                with self.subTest(inspector=inspector, environment=environment):
                    result = self.run_script(
                        script, ANDROID_HOME=str(sdk), SORA_APK_INSPECTOR=inspector, **environment
                    )
                    self.assertEqual(1, result.returncode, result.stdout)
                    self.assertIn(message, result.stderr)
                    self.assertEqual([], self.lines("instrument.log"))
                    self.assertIn(inspector, self.lines("inspector.log"))
                    (self.log / "inspector.log").unlink()


if __name__ == "__main__":
    unittest.main()
