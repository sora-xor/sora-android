#!/usr/bin/env python3
"""Tests for the Google identity pins.

Three layers, each through the code that runs in a release:

1. scripts/google-identity-pin.py through its command line (the workflow's early check and the
   helper the person who fills in the pin uses).
2. scripts/verify-production-google-identity.gradle, applied by a real Gradle run to a small
   fixture project (the same file app/build.gradle.kts applies). Skipped when Java is missing.
3. The real :app:verifyProductionReleaseSigning task of this repository. It needs the Android
   SDK and the project's plugins, so it runs only when SORA_TEST_RELEASE_GATE=1; extra Gradle
   arguments come from SORA_GRADLE_FLAGS (for example "--offline").

No real identifier is used anywhere: the IDs below are made up.
"""

from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
HELPER = ROOT / "scripts" / "google-identity-pin.py"
GRADLE_SCRIPT = ROOT / "scripts" / "verify-production-google-identity.gradle"
PIN_FILE = ROOT / "config" / "android-production-google-identity.json"
SECRET = "SORA_GOOGLE_TOKEN_PROD"

CLIENT = "123456789012-abcdefghijklmnopqrstuvwxyz012345.apps.googleusercontent.com"
OTHER_CLIENT = "987654321098-zyxwvutsrqponmlkjihgfedcba543210.apps.googleusercontent.com"
APP_ID = "1:123456789012:android:0123456789abcdef"
OTHER_APP_ID = "1:210987654321:android:fedcba9876543210"
PACKAGE = "jp.co.soramitsu.sora"


def sha256(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def write_pins(path: Path, drive="", firebase="") -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(
            {
                "schemaVersion": 1,
                "applicationId": PACKAGE,
                "googleDriveClientIdSha256": drive,
                "firebaseAppIdSha256": firebase,
            }
        ),
        encoding="utf-8",
    )
    return path


def write_google_services(path: Path, app_id=APP_ID, package=PACKAGE) -> Path:
    path.write_text(
        json.dumps(
            {
                "project_info": {"project_number": "123456789012", "project_id": "fixture"},
                "client": [
                    {
                        "client_info": {
                            "mobilesdk_app_id": "1:123456789012:android:aaaaaaaaaaaaaaaa",
                            "android_client_info": {"package_name": PACKAGE + ".develop"},
                        }
                    },
                    {
                        "client_info": {
                            "mobilesdk_app_id": app_id,
                            "android_client_info": {"package_name": package},
                        }
                    },
                ],
            }
        ),
        encoding="utf-8",
    )
    return path


def clean_environment(client=None) -> dict:
    environment = {key: value for key, value in os.environ.items() if key != SECRET}
    if client is not None:
        environment[SECRET] = client
    return environment


def helper(*arguments, client=None, stdin=None) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, str(HELPER), *map(str, arguments)],
        capture_output=True,
        text=True,
        check=False,
        env=clean_environment(client),
        input=stdin,
    )


class HelperTest(unittest.TestCase):
    def setUp(self):
        self._directory = tempfile.TemporaryDirectory(prefix="sora-google-identity-")
        self.addCleanup(self._directory.cleanup)
        self.directory = Path(self._directory.name)

    def test_hash_prints_the_sha256_and_never_the_value(self):
        result = helper("hash", client=CLIENT)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(f"googleDriveClientIdSha256: {sha256(CLIENT)}\n", result.stdout)
        self.assertNotIn(CLIENT, result.stdout + result.stderr)

        result = helper("hash", "--stdin", stdin=CLIENT + "\n")
        self.assertEqual(f"googleDriveClientIdSha256: {sha256(CLIENT)}\n", result.stdout)

        # A value stored as a quoted literal compiles to the same client ID.
        result = helper("hash", client=f'"{CLIENT}"')
        self.assertEqual(f"googleDriveClientIdSha256: {sha256(CLIENT)}\n", result.stdout)

    def test_hash_refuses_a_value_the_build_would_compile_differently(self):
        for value in ("", "   ", CLIENT + " ", " " + CLIENT, CLIENT + "\n", f'"{CLIENT}', 'a"b', "a b"):
            with self.subTest(value=value):
                result = helper("hash", client=value)
                self.assertEqual(2, result.returncode, result.stdout)
                self.assertNotIn("googleDriveClientIdSha256", result.stdout)

    def test_hash_warns_when_the_value_is_not_a_client_id(self):
        result = helper("hash", client="GOCSPX-not-a-client-id")
        self.assertEqual(0, result.returncode)
        self.assertIn("does not look like a Google OAuth client ID", result.stderr)

    def test_hash_of_the_firebase_app(self):
        services = write_google_services(self.directory / "google-services.json")
        pins = write_pins(self.directory / "pins.json")
        result = helper("hash", "--pin-file", pins, "--google-services", services)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(f"firebaseAppIdSha256: {sha256(APP_ID)}\n", result.stdout)

    def test_check_fails_while_the_drive_pin_is_empty(self):
        pins = write_pins(self.directory / "pins.json")
        result = helper("check", "--pin-file", pins, client=CLIENT)
        self.assertEqual(1, result.returncode)
        self.assertIn("Google Drive backup client: no pin yet", result.stderr)
        self.assertIn("scripts/google-identity-pin.py", result.stderr)

    def test_check_accepts_only_the_pinned_client(self):
        pins = write_pins(self.directory / "pins.json", drive=sha256(CLIENT))
        result = helper("check", "--pin-file", pins, client=CLIENT)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("is the pinned Google Drive backup client", result.stdout)

        result = helper("check", "--pin-file", pins, client=OTHER_CLIENT)
        self.assertEqual(1, result.returncode)
        self.assertIn(f"(SHA-256 {sha256(OTHER_CLIENT)}) is not the pinned client (SHA-256 {sha256(CLIENT)})", result.stderr)
        self.assertNotIn(OTHER_CLIENT, result.stdout + result.stderr)

        result = helper("check", "--pin-file", pins)
        self.assertEqual(1, result.returncode)
        self.assertIn("SORA_GOOGLE_TOKEN_PROD is missing", result.stderr)

        result = helper("check", "--pin-file", pins, client=CLIENT + "\n")
        self.assertEqual(1, result.returncode)
        self.assertIn("must be the bare Google Drive backup client ID", result.stderr)

    def test_check_of_the_firebase_app(self):
        services = write_google_services(self.directory / "google-services.json")
        pins = write_pins(self.directory / "pins.json", drive=sha256(CLIENT))
        result = helper("check", "--pin-file", pins, "--google-services", services, client=CLIENT)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("note: Firebase app: not pinned", result.stdout)

        write_pins(pins, drive=sha256(CLIENT), firebase=sha256(APP_ID))
        result = helper("check", "--pin-file", pins, "--google-services", services, client=CLIENT)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotIn("not pinned", result.stdout)

        write_google_services(services, app_id=OTHER_APP_ID)
        result = helper("check", "--pin-file", pins, "--google-services", services, client=CLIENT)
        self.assertEqual(1, result.returncode)
        self.assertIn("is for another Firebase app", result.stderr)

        write_google_services(services, app_id="1:000000000000:android:0000000000000000")
        result = helper("check", "--pin-file", pins, "--google-services", services, client=CLIENT)
        self.assertEqual(1, result.returncode)
        self.assertIn("placeholder or malformed", result.stderr)

        write_google_services(services, package="com.example.other")
        result = helper("check", "--pin-file", pins, "--google-services", services, client=CLIENT)
        self.assertEqual(1, result.returncode)
        self.assertIn(f"must have exactly one client for {PACKAGE}, found 0", result.stderr)

    def test_firebase_web_client_does_not_authorize_a_different_drive_client(self):
        services = write_google_services(self.directory / "google-services.json")
        data = json.loads(services.read_text())
        data["client"][1]["oauth_client"] = [{"client_type": 3, "client_id": OTHER_CLIENT}]
        services.write_text(json.dumps(data))
        pins = write_pins(self.directory / "pins.json", drive=sha256(CLIENT), firebase=sha256(APP_ID))
        self.assertEqual(0, helper("check", "--pin-file", pins, "--google-services", services, client=CLIENT).returncode)
        result = helper("check", "--pin-file", pins, "--google-services", services, client=OTHER_CLIENT)
        self.assertEqual(1, result.returncode)
        self.assertIn("is not the pinned client", result.stderr)
        self.assertNotIn("is for another Firebase app", result.stderr)

    def test_check_rejects_a_malformed_pin_file(self):
        pins = write_pins(self.directory / "pins.json", drive=sha256(CLIENT).upper())
        result = helper("check", "--pin-file", pins, client=CLIENT)
        self.assertEqual(2, result.returncode)
        self.assertIn("googleDriveClientIdSha256 must be empty or 64 lowercase hex characters", result.stderr)
        self.assertEqual(2, helper("check", "--pin-file", self.directory / "none.json", client=CLIENT).returncode)

    def test_from_apk_finds_the_ids_a_release_carries(self):
        apk = self.directory / "released.apk"
        with zipfile.ZipFile(apk, "w") as archive:
            # A dex stores each string as a length byte, the text and a zero byte.
            dex = b"dex\n035\x00" + bytes([len(CLIENT)]) + CLIENT.encode() + b"\x00" + b"\x05other\x00"
            archive.writestr("classes.dex", b"dex\n035\x00\x03abc\x00")
            archive.writestr("classes2.dex", dex)
            # resources.arsc may store strings as UTF-16, at any offset.
            arsc = b"\x02\x00\x0c" + OTHER_CLIENT.encode("utf-16-le") + b"\x00\x00" + APP_ID.encode("utf-16-le")
            archive.writestr("resources.arsc", arsc)
            archive.writestr("assets/notes.txt", "999999-zzzzzzzzzzzzzzzzzzzz.apps.googleusercontent.com")
        result = helper("from-apk", apk)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn(f"googleDriveClientIdSha256: {sha256(CLIENT)}", result.stdout)
        self.assertIn(f"firebaseAppIdSha256: {sha256(APP_ID)}", result.stdout)
        self.assertIn(f"sha256 {sha256(OTHER_CLIENT)}", result.stdout)
        self.assertIn("classes2.dex", result.stdout)
        self.assertNotIn("notes.txt", result.stdout)
        self.assertNotIn(CLIENT, result.stdout)
        self.assertIn("1234...2345.apps.googleusercontent.com", result.stdout)

        result = helper("from-apk", "--show-values", apk)
        self.assertIn(CLIENT, result.stdout)

    def test_from_apk_does_not_guess_between_two_clients(self):
        apk = self.directory / "two.apk"
        with zipfile.ZipFile(apk, "w") as archive:
            archive.writestr("classes.dex", CLIENT.encode() + b"\x00" + OTHER_CLIENT.encode() + b"\x00")
        result = helper("from-apk", apk)
        self.assertEqual(1, result.returncode)
        self.assertIn("2 OAuth client IDs in the dex files", result.stdout)
        self.assertNotIn("googleDriveClientIdSha256:", result.stdout)

        empty = self.directory / "empty.apk"
        with zipfile.ZipFile(empty, "w") as archive:
            archive.writestr("classes.dex", b"dex\n")
        result = helper("from-apk", empty)
        self.assertEqual(1, result.returncode)
        self.assertIn("no Google OAuth client ID or Firebase app ID found", result.stdout)

    def test_repository_pin_file_is_well_formed(self):
        pins = json.loads(PIN_FILE.read_text(encoding="utf-8"))
        self.assertEqual(PACKAGE, pins["applicationId"])
        for key in ("googleDriveClientIdSha256", "firebaseAppIdSha256"):
            self.assertRegex(pins[key], r"\A([0-9a-f]{64})?\Z")
        result = helper("check", client=CLIENT)
        # Nobody may pin the made-up client of this test.
        self.assertEqual(1, result.returncode, result.stdout)


def java_available() -> bool:
    java_home = os.environ.get("JAVA_HOME")
    return bool(java_home and Path(java_home, "bin", "java").is_file()) or shutil.which("java") is not None


@unittest.skipUnless(java_available(), "Java is needed to run Gradle")
class GradleScriptTest(unittest.TestCase):
    """The Gradle script on a fixture project: what a productionRelease build would decide."""

    def setUp(self):
        self._directory = tempfile.TemporaryDirectory(prefix="sora-google-identity-gradle-")
        self.addCleanup(self._directory.cleanup)
        self.fixture = Path(self._directory.name)
        (self.fixture / "settings.gradle").write_text("rootProject.name = 'google-identity-fixture'\n")
        script = str(GRADLE_SCRIPT).replace("\\", "\\\\").replace("'", "\\'")
        (self.fixture / "build.gradle").write_text(f"apply from: '{script}'\n")
        self.pins = self.fixture / "config" / "android-production-google-identity.json"

    def gradle(self, client=None) -> subprocess.CompletedProcess:
        return subprocess.run(
            [
                "./gradlew", "-p", str(self.fixture), "verifyProductionGoogleIdentity",
                "--offline", "--no-daemon", "--no-parallel", "--console=plain",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
            timeout=300,
            env=clean_environment(client),
        )

    def assert_refused(self, result, *messages):
        output = result.stdout + result.stderr
        self.assertNotEqual(0, result.returncode, output)
        self.assertIn("productionRelease cannot be built:", output)
        for message in messages:
            self.assertIn(message, output)
        for identifier in (CLIENT, OTHER_CLIENT, APP_ID, OTHER_APP_ID):
            self.assertNotIn(identifier, output)

    def test_empty_pin_refuses_the_build_with_instructions(self):
        write_pins(self.pins)
        self.assert_refused(
            self.gradle(CLIENT),
            "Google Drive backup client: no pin yet",
            "googleDriveClientIdSha256 in config/android-production-google-identity.json is empty",
            "scripts/google-identity-pin.py",
        )

    def test_pin_from_the_helper_accepts_that_client_and_no_other(self):
        printed = helper("hash", client=CLIENT).stdout.strip()
        self.assertTrue(printed.startswith("googleDriveClientIdSha256: "), printed)
        write_pins(self.pins, drive=printed.split(": ")[1])

        result = self.gradle(CLIENT)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("is the pinned Google Drive backup client", result.stdout)
        self.assertIn("Firebase app: not pinned", result.stdout + result.stderr)

        # Stored as a quoted literal, the build compiles the same client ID.
        self.assertEqual(0, self.gradle(f'"{CLIENT}"').returncode)

        self.assert_refused(
            self.gradle(OTHER_CLIENT),
            f"SORA_GOOGLE_TOKEN_PROD (SHA-256 {sha256(OTHER_CLIENT)}) is not the pinned client "
            f"(SHA-256 {sha256(CLIENT)})",
            "cannot see the Google Drive backups",
        )
        self.assert_refused(self.gradle(None), "SORA_GOOGLE_TOKEN_PROD is missing (Google Drive backup)")
        self.assert_refused(self.gradle(CLIENT + " "), "must be the bare Google Drive backup client ID")

    def test_firebase_web_client_cannot_substitute_for_historical_drive_client(self):
        write_pins(self.pins, drive=sha256(CLIENT), firebase=sha256(APP_ID))
        services = write_google_services(self.fixture / "google-services.json")
        data = json.loads(services.read_text())
        data["client"][1]["oauth_client"] = [{"client_type": 3, "client_id": OTHER_CLIENT}]
        services.write_text(json.dumps(data))
        result = self.gradle(CLIENT)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assert_refused(self.gradle(OTHER_CLIENT), "is not the pinned client")

    def test_local_properties_wins_over_the_environment_like_the_build(self):
        write_pins(self.pins, drive=sha256(CLIENT))
        (self.fixture / "local.properties").write_text(f"{SECRET}={OTHER_CLIENT}\n")
        self.assert_refused(self.gradle(CLIENT), "is not the pinned client")
        (self.fixture / "local.properties").write_text(f"{SECRET}={CLIENT}\n")
        self.assertEqual(0, self.gradle(OTHER_CLIENT).returncode)

    def test_firebase_app(self):
        write_pins(self.pins, drive=sha256(CLIENT), firebase=sha256(APP_ID))
        write_google_services(self.fixture / "google-services.json")
        result = self.gradle(CLIENT)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertNotIn("not pinned", result.stdout + result.stderr)

        # A copy in a variant directory is the one the google-services plugin would use.
        variant = self.fixture / "src" / "production"
        variant.mkdir(parents=True)
        write_google_services(variant / "google-services.json", app_id=OTHER_APP_ID)
        self.assert_refused(
            self.gradle(CLIENT),
            "src/production/google-services.json is for another Firebase app",
            f"(SHA-256 {sha256(OTHER_APP_ID)})",
        )
        shutil.rmtree(self.fixture / "src")

        # Even without a Firebase pin, a zeroed or foreign file is not accepted.
        write_pins(self.pins, drive=sha256(CLIENT))
        write_google_services(self.fixture / "google-services.json", app_id="1:000000000000:android:0000000000000000")
        self.assert_refused(self.gradle(CLIENT), "placeholder or malformed")
        write_google_services(self.fixture / "google-services.json", package="com.example.other")
        self.assert_refused(self.gradle(CLIENT), f"must have exactly one client for {PACKAGE}, found 0")

    def test_malformed_pin_file(self):
        write_pins(self.pins, drive="not-a-hash")
        self.assert_refused(self.gradle(CLIENT), "googleDriveClientIdSha256 must be 64 lowercase hex characters")
        self.pins.write_text("{", encoding="utf-8")
        self.assert_refused(self.gradle(CLIENT), "config/android-production-google-identity.json cannot be read")
        self.pins.unlink()
        self.assert_refused(self.gradle(CLIENT), "config/android-production-google-identity.json cannot be read")


@unittest.skipUnless(os.environ.get("SORA_TEST_RELEASE_GATE") == "1", "set SORA_TEST_RELEASE_GATE=1 to run")
class ReleaseGateTest(unittest.TestCase):
    """The real :app gate: every productionRelease task depends on it."""

    def gate(self, client=None) -> str:
        environment = clean_environment(client)
        for name in ("CI_KEYSTORE_PATH", "CI_KEYSTORE_PASS", "CI_KEYSTORE_KEY_ALIAS", "CI_KEYSTORE_KEY_PASS", "SORA_VERSION_CODE"):
            environment.pop(name, None)
        result = subprocess.run(
            ["./gradlew", ":app:verifyProductionReleaseSigning", "--console=plain", *os.environ.get("SORA_GRADLE_FLAGS", "").split()],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
            timeout=1800,
            env=environment,
        )
        output = result.stdout + result.stderr
        self.assertNotEqual(0, result.returncode, output)
        self.assertIn("productionRelease cannot be built:", output)
        return output

    def test_gate_lists_every_missing_input(self):
        if (ROOT / "local.properties").is_file() and SECRET in (ROOT / "local.properties").read_text():
            self.skipTest("local.properties sets the Drive client; the environment cannot override it")
        output = self.gate()
        self.assertIn("- signing: set CI_KEYSTORE_PATH", output)
        self.assertIn("- versionCode: set SORA_VERSION_CODE", output)
        self.assertIn("- SORA_GOOGLE_TOKEN_PROD is missing (Google Drive backup)", output)

    def test_gate_refuses_a_client_that_is_not_the_pinned_one(self):
        if (ROOT / "local.properties").is_file() and SECRET in (ROOT / "local.properties").read_text():
            self.skipTest("local.properties sets the Drive client; the environment cannot override it")
        output = self.gate(CLIENT)
        pinned = json.loads(PIN_FILE.read_text(encoding="utf-8"))["googleDriveClientIdSha256"]
        if pinned:
            self.assertIn(f"- Google Drive backup client: SORA_GOOGLE_TOKEN_PROD (SHA-256 {sha256(CLIENT)}) is not the pinned client", output)
        else:
            self.assertIn("- Google Drive backup client: no pin yet", output)
        self.assertNotIn("SORA_GOOGLE_TOKEN_PROD is missing", output)
        self.assertNotIn(CLIENT, output)


if __name__ == "__main__":
    unittest.main()
