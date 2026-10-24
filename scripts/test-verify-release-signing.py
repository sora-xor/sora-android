#!/usr/bin/env python3
"""Tests for verify-release-signing.py.

Parser tests always run. The end-to-end tests create a throwaway self-signed key, sign a small
APK and AAB with it, and are skipped when keytool, jarsigner or apksigner are unavailable.
"""

import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

CHECKER = Path(__file__).with_name("verify-release-signing.py")
SPEC = importlib.util.spec_from_file_location("verify_release_signing", CHECKER)
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)

DIGEST = "b35dfe16cb3226da4432607288c6287362c5e623532c428f933d552297e9e3e0"


class ParserTest(unittest.TestCase):
    def test_apksigner_single_signer(self):
        output = f"Verifies\nVerified using v2 scheme (APK Signature Scheme v2): true\n" \
                 f"Signer #1 certificate SHA-256 digest: {DIGEST.upper()}\n"
        self.assertEqual(DIGEST, checker.parse_apksigner_digest(output))

    def test_apksigner_rejects_two_signers(self):
        output = f"Signer #1 certificate SHA-256 digest: {DIGEST}\n" \
                 f"Signer #2 certificate SHA-256 digest: {DIGEST}\n"
        with self.assertRaises(checker.SigningError):
            checker.parse_apksigner_digest(output)

    def test_apksigner_rejects_missing_signer(self):
        with self.assertRaises(checker.SigningError):
            checker.parse_apksigner_digest("DOES NOT VERIFY\n")

    def test_keytool_rejects_two_signers(self):
        output = "Signer #1:\n-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----\n" \
                 "Signer #2:\n-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----\n"
        with self.assertRaises(checker.SigningError):
            checker.parse_keytool_signer_certificate(output)

    def test_keytool_rejects_missing_certificate(self):
        with self.assertRaises(checker.SigningError):
            checker.parse_keytool_signer_certificate("Signer #1:\n\nSignature:\n")

    def test_pin_file_must_hold_lowercase_hex(self):
        with tempfile.TemporaryDirectory() as directory:
            pin_file = Path(directory, "pin.json")
            pin_file.write_text(json.dumps({"uploadCertificateSha256": DIGEST.upper()}))
            with self.assertRaises(checker.SigningError):
                checker.load_pin(pin_file)
            pin_file.write_text(json.dumps({"uploadCertificateSha256": DIGEST}))
            self.assertEqual(DIGEST, checker.load_pin(pin_file))

    def test_repository_pin_is_valid(self):
        self.assertRegex(checker.load_pin(checker.DEFAULT_PIN_FILE), r"\A[0-9a-f]{64}\Z")


def tool_available(name):
    try:
        if name == "apksigner":
            checker.build_tools_apksigner()
        else:
            checker.java_tool(name)
        return True
    except checker.SigningError:
        return False


def android_jar():
    """Newest platforms/android-*/android.jar, used to link a minimal real APK."""
    for variable in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk = os.environ.get(variable)
        if sdk:
            jars = sorted(
                Path(sdk, "platforms").glob("android-*/android.jar"),
                key=lambda jar: int(jar.parent.name.split("-")[-1]) if jar.parent.name.split("-")[-1].isdigit() else -1,
            )
            if jars:
                return jars[-1]
    return None


def aapt2():
    try:
        candidate = Path(checker.build_tools_apksigner()).with_name("aapt2")
    except checker.SigningError:
        return None
    return candidate if candidate.is_file() else None


@unittest.skipUnless(
    all(tool_available(name) for name in ("keytool", "jarsigner", "apksigner"))
    and aapt2() is not None and android_jar() is not None,
    "keytool, jarsigner, apksigner, aapt2 or android.jar is unavailable",
)
class EndToEndTest(unittest.TestCase):
    def setUp(self):
        self.directory = Path(tempfile.mkdtemp(prefix="sora-signing-test-"))
        self.addCleanup(shutil.rmtree, self.directory)
        self.keystore = self.directory / "throwaway.p12"
        self.password = "throwaway-test-only"
        self.run_tool(
            checker.java_tool("keytool"), "-genkeypair", "-alias", "test", "-keyalg", "RSA",
            "-keysize", "2048", "-validity", "2", "-dname", "CN=SORA signing test",
            "-keystore", str(self.keystore), "-storetype", "PKCS12",
            "-storepass", self.password, "-keypass", self.password,
        )
        certificate = self.run_tool(
            checker.java_tool("keytool"), *checker.JAVA_LOCALE, "-exportcert", "-rfc",
            "-alias", "test", "-keystore", str(self.keystore), "-storepass", self.password,
        )
        self.digest, _ = checker.parse_keytool_signer_certificate("Signer #1:\n" + certificate)

    def run_tool(self, *command):
        result = subprocess.run(command, capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr + result.stdout)
        return result.stdout

    def pin_file(self, digest):
        path = self.directory / "pin.json"
        path.write_text(json.dumps({"uploadCertificateSha256": digest}))
        return path

    def unsigned_zip(self, name):
        path = self.directory / name
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("AndroidManifest.xml", b"placeholder")
            archive.writestr("classes.dex", b"placeholder")
        return path

    def unsigned_apk(self, name):
        manifest = self.directory / "AndroidManifest.xml"
        manifest.write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
            'package="jp.co.soramitsu.signingtest"><application /></manifest>'
        )
        path = self.directory / name
        self.run_tool(
            str(aapt2()), "link", "-o", str(path), "-I", str(android_jar()),
            "--manifest", str(manifest), "--min-sdk-version", "26", "--target-sdk-version", "36",
        )
        return path

    def signed_apk(self):
        unsigned = self.unsigned_apk("unsigned.apk")
        signed = self.directory / "signed.apk"
        self.run_tool(
            checker.build_tools_apksigner(), "sign", "--ks", str(self.keystore),
            "--ks-pass", f"pass:{self.password}", "--out", str(signed), str(unsigned),
        )
        return signed

    def signed_aab(self):
        aab = self.unsigned_zip("app.aab")
        self.run_tool(
            checker.java_tool("jarsigner"), "-keystore", str(self.keystore),
            "-storetype", "PKCS12", "-storepass", self.password, str(aab), "test",
        )
        return aab

    def check(self, digest, *artifacts):
        return checker.main(["--pin-file", str(self.pin_file(digest)), *map(str, artifacts)])

    def test_matching_pin_passes(self):
        self.assertEqual(0, self.check(self.digest, self.signed_apk(), self.signed_aab()))

    def test_other_pin_fails(self):
        other = "0" * 64
        self.assertEqual(1, self.check(other, self.signed_apk()))
        self.assertEqual(1, self.check(other, self.signed_aab()))

    def test_unsigned_artifacts_fail(self):
        self.assertEqual(1, self.check(self.digest, self.unsigned_apk("plain.apk")))
        self.assertEqual(1, self.check(self.digest, self.unsigned_zip("plain.aab")))

    def test_tampered_aab_fails(self):
        aab = self.signed_aab()
        tampered = self.directory / "tampered.aab"
        with zipfile.ZipFile(aab) as source, zipfile.ZipFile(tampered, "w") as target:
            for entry in source.infolist():
                data = b"tampered" if entry.filename == "classes.dex" else source.read(entry)
                target.writestr(entry, data)
        self.assertEqual(1, self.check(self.digest, tampered))


if __name__ == "__main__":
    unittest.main(verbosity=2)
