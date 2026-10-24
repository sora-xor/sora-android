#!/usr/bin/env python3
"""Tests for verify-release-manifest.py.

Every case builds a small APK (Android binary XML) and a small AAB (aapt2 protobuf XML) from
the same tree, runs the real command line on both, and checks the exit code and the message.
The baseline tree mirrors the merged manifest of the 3.9.0 productionRelease build; each
failing case changes one thing in it.
"""

from __future__ import annotations

import copy
import importlib.util
import json
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

CHECKER = Path(__file__).with_name("verify-release-manifest.py")
SPEC = importlib.util.spec_from_file_location("verify_release_manifest", CHECKER)
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)

ANDROID_NS = checker.ANDROID_NS
ATTRIBUTE_ID = {name: number for number, name in checker.ANDROID_ATTRIBUTE_IDS.items()}
NO_INDEX = 0xFFFFFFFF
SPLASH = "jp.co.soramitsu.sora.splash.presentation.SplashActivity"
IROHA = "jp.co.soramitsu.sora.irohaconnect.IrohaConnectActivity"


def el(name, attributes=None, *children):
    return {"name": name, "attributes": dict(attributes or {}), "children": list(children)}


def launcher_filter():
    return el(
        "intent-filter",
        None,
        el("action", {"name": "android.intent.action.MAIN"}),
        el("category", {"name": "android.intent.category.LAUNCHER"}),
        el("category", {"name": "android.intent.category.DEFAULT"}),
    )


def deep_link_filter(scheme):
    return el(
        "intent-filter",
        None,
        el("action", {"name": "android.intent.action.VIEW"}),
        el("category", {"name": "android.intent.category.DEFAULT"}),
        el("category", {"name": "android.intent.category.BROWSABLE"}),
        el("data", {"host": "connect", "scheme": scheme}),
    )


def production_manifest():
    policy = json.loads(checker.DEFAULT_POLICY_FILE.read_text(encoding="utf-8"))
    permissions = [el("uses-permission", {"name": name}) for name in policy["usesPermissions"]]
    application = el(
        "application",
        {
            "name": "jp.co.soramitsu.sora.SoraApp",
            "allowBackup": False,
            "fullBackupContent": False,
            "dataExtractionRules": "@0x7f170000",
            "supportsRtl": True,
        },
        el("activity", {"name": SPLASH, "exported": True}, launcher_filter()),
        el("activity", {"name": "jp.co.soramitsu.feature_main_impl.presentation.MainActivity", "exported": False}),
        el(
            "service",
            {
                "name": "com.google.android.gms.auth.api.signin.RevocationBoundService",
                "exported": True,
                "permission": "com.google.android.gms.auth.api.signin.permission.REVOCATION_NOTIFICATION",
            },
        ),
        el(
            "service",
            {
                "name": "androidx.work.impl.background.systemjob.SystemJobService",
                "exported": True,
                "permission": "android.permission.BIND_JOB_SERVICE",
            },
        ),
        el(
            "receiver",
            {
                "name": "androidx.work.impl.diagnostics.DiagnosticsReceiver",
                "exported": True,
                "permission": "android.permission.DUMP",
            },
            el("intent-filter", None, el("action", {"name": "androidx.work.diagnostics.REQUEST_DIAGNOSTICS"})),
        ),
        el(
            "receiver",
            {
                "name": "androidx.work.impl.background.systemalarm.RescheduleReceiver",
                "exported": False,
            },
            el("intent-filter", None, el("action", {"name": "android.intent.action.BOOT_COMPLETED"})),
        ),
        el(
            "receiver",
            {
                "name": "androidx.profileinstaller.ProfileInstallReceiver",
                "exported": True,
                "permission": "android.permission.DUMP",
            },
            el("intent-filter", None, el("action", {"name": "androidx.profileinstaller.action.INSTALL_PROFILE"})),
        ),
        el(
            "provider",
            {"name": "androidx.core.content.FileProvider", "exported": False},
        ),
    )
    application["children"].extend(
        el("meta-data", {"name": name, "value": value})
        for name, value in policy["requiredApplicationMetadata"].items()
    )
    return el(
        "manifest",
        {"package": policy["applicationId"], "versionCode": 3090001, "versionName": "3.9.0"},
        el("uses-sdk", {"minSdkVersion": policy["minSdkVersion"], "targetSdkVersion": policy["targetSdkVersion"]}),
        *permissions,
        application,
    )


def application_of(manifest):
    return next(child for child in manifest["children"] if child["name"] == "application")


def component(manifest, name):
    return next(child for child in application_of(manifest)["children"] if child["attributes"].get("name") == name)


# --- Android binary XML encoder --------------------------------------------------------------


def encode_binary(root, *, utf8=True, strip_attribute_names=False) -> bytes:
    """Encode the tree the way aapt2 does: framework attribute names first, mapped to ids."""
    mapped = []

    def collect(node):
        for key in node["attributes"]:
            if key in ATTRIBUTE_ID and key not in mapped:
                mapped.append(key)
        for child in node["children"]:
            collect(child)

    collect(root)
    strings = ["" if strip_attribute_names else name for name in mapped]
    index_of_mapped = {name: position for position, name in enumerate(mapped)}

    def string(value: str) -> int:
        for position in range(len(mapped), len(strings)):
            if strings[position] == value:
                return position
        strings.append(value)
        return len(strings) - 1

    namespace_index = string(ANDROID_NS)
    body = bytearray()

    def emit(node):
        attributes = bytearray()
        for key, value in node["attributes"].items():
            if key == "package":
                ns_index, name_index = NO_INDEX, string(key)
            elif key in index_of_mapped:
                ns_index, name_index = namespace_index, index_of_mapped[key]
            else:
                ns_index, name_index = namespace_index, string(key)
            if isinstance(value, bool):
                raw, data_type, data = NO_INDEX, 0x12, 0xFFFFFFFF if value else 0
            elif isinstance(value, int):
                raw, data_type, data = NO_INDEX, 0x10, value
            elif value.startswith("@0x"):
                raw, data_type, data = NO_INDEX, 0x01, int(value[1:], 16)
            else:
                raw = data = string(value)
                data_type = 0x03
            attributes += struct.pack("<IIIHBBI", ns_index, name_index, raw, 8, 0, data_type, data)
        count = len(node["attributes"])
        name_index = string(node["name"])
        body.extend(struct.pack("<HHIII", 0x0102, 16, 36 + len(attributes), 1, NO_INDEX))
        body.extend(struct.pack("<IIHHHHHH", NO_INDEX, name_index, 20, 20, count, 0, 0, 0))
        body.extend(attributes)
        for child in node["children"]:
            emit(child)
        body.extend(struct.pack("<HHIIIII", 0x0103, 16, 24, 1, NO_INDEX, NO_INDEX, name_index))

    emit(root)

    pool = bytearray()
    offsets = []
    for value in strings:
        offsets.append(len(pool))
        if utf8:
            raw = value.encode("utf-8")
            for length in (len(value), len(raw)):
                pool += bytes([0x80 | (length >> 8), length & 0xFF]) if length > 0x7F else bytes([length])
            pool += raw + b"\x00"
        else:
            raw = value.encode("utf-16-le")
            pool += struct.pack("<H", len(raw) // 2) + raw + b"\x00\x00"
    pool += bytes(-len(pool) % 4)
    header_size = 28
    strings_start = header_size + 4 * len(strings)
    string_pool = struct.pack(
        "<HHIIIIII",
        0x0001,
        header_size,
        strings_start + len(pool),
        len(strings),
        0,
        0x100 if utf8 else 0,
        strings_start,
        0,
    )
    string_pool += b"".join(struct.pack("<I", offset) for offset in offsets) + bytes(pool)
    resource_map = struct.pack("<HHI", 0x0180, 8, 8 + 4 * len(mapped))
    resource_map += b"".join(struct.pack("<I", ATTRIBUTE_ID[name]) for name in mapped)
    payload = string_pool + resource_map + bytes(body)
    return struct.pack("<HHI", 0x0003, 8, 8 + len(payload)) + payload


# --- aapt2 protobuf XML encoder --------------------------------------------------------------


def varint(value: int) -> bytes:
    out = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        out.append(byte | (0x80 if value else 0))
        if not value:
            return bytes(out)


def field(number: int, payload) -> bytes:
    if isinstance(payload, int):
        return varint(number << 3) + varint(payload)
    if isinstance(payload, str):
        payload = payload.encode("utf-8")
    return varint(number << 3 | 2) + varint(len(payload)) + payload


def encode_proto(root, *, typed=True) -> bytes:
    def element(node) -> bytes:
        out = field(3, node["name"])
        for key, value in node["attributes"].items():
            attribute = b"" if key == "package" else field(1, ANDROID_NS)
            attribute += field(2, key)
            if isinstance(value, bool):
                attribute += field(3, "true" if value else "false")
                if typed:
                    attribute += field(6, field(7, field(8, int(value))))
            elif isinstance(value, int):
                attribute += field(3, str(value))
                if typed:
                    attribute += field(6, field(7, field(6, value)))
            else:
                attribute += field(3, "@xml/rules" if value.startswith("@0x") else value)
            if key in ATTRIBUTE_ID:
                attribute += field(5, ATTRIBUTE_ID[key])
            out += field(4, attribute)
        for child in node["children"]:
            out += field(5, field(2, "\n    ")) + field(5, field(1, element(child)))
        return out

    return field(1, element(root))


def write_apk(path: Path, manifest, **options) -> Path:
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("AndroidManifest.xml", encode_binary(manifest, **options))
        archive.writestr("classes.dex", b"dex\n")
    return path


def write_aab(path: Path, manifest, *, extra_module=False, **options) -> Path:
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("BundleConfig.pb", b"")
        archive.writestr("base/manifest/AndroidManifest.xml", encode_proto(manifest, **options))
        if extra_module:
            archive.writestr("feature/manifest/AndroidManifest.xml", encode_proto(manifest))
    return path


def run(*arguments) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, str(CHECKER), *map(str, arguments)], capture_output=True, text=True, check=False
    )


class ReleaseManifestTest(unittest.TestCase):
    def setUp(self):
        self._directory = tempfile.TemporaryDirectory(prefix="sora-release-manifest-")
        self.addCleanup(self._directory.cleanup)
        self.directory = Path(self._directory.name)

    def artifacts(self, manifest):
        return [
            ("apk", write_apk(self.directory / "app.apk", manifest)),
            ("apk utf-16", write_apk(self.directory / "app16.apk", manifest, utf8=False)),
            ("aab", write_aab(self.directory / "app.aab", manifest)),
            ("aab untyped", write_aab(self.directory / "plain.aab", manifest, typed=False)),
        ]

    def assert_rejected(self, manifest, *messages):
        for kind, artifact in self.artifacts(manifest):
            with self.subTest(kind=kind):
                result = run(artifact)
                self.assertEqual(1, result.returncode, result.stdout + result.stderr)
                for message in messages:
                    self.assertIn(message, result.stderr)

    def test_production_manifest_passes(self):
        for kind, artifact in self.artifacts(production_manifest()):
            with self.subTest(kind=kind):
                result = run("--expect-version-code", "3090001", "--expect-version-name", "3.9.0", artifact)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                self.assertIn("targetSdk 36", result.stdout)
                self.assertIn("merged manifest matches", result.stdout)

    def test_packaged_firebase_defaults_keep_metrics_and_deny_advertising(self):
        policy = json.loads(checker.DEFAULT_POLICY_FILE.read_text())
        for name, expected in policy["requiredApplicationMetadata"].items():
            with self.subTest(name=name):
                manifest = production_manifest()
                metadata = component(manifest, name)
                metadata["attributes"]["value"] = not expected
                self.assert_rejected(manifest, name)
                application_of(manifest)["children"].remove(metadata)
                self.assert_rejected(manifest, name)

    def test_duplicate_defaults_and_permanent_analytics_deactivation_are_rejected(self):
        manifest = production_manifest()
        application_of(manifest)["children"].append(
            el("meta-data", {"name": "google_analytics_adid_collection_enabled", "value": True})
        )
        self.assert_rejected(manifest, "google_analytics_adid_collection_enabled")
        manifest = production_manifest()
        application_of(manifest)["children"].append(
            el("meta-data", {"name": "firebase_analytics_collection_deactivated", "value": True})
        )
        self.assert_rejected(manifest, "must not be permanently deactivated")

    def test_backup_must_be_off(self):
        manifest = production_manifest()
        application_of(manifest)["attributes"]["allowBackup"] = True
        self.assert_rejected(manifest, "android:allowBackup is true; it must be false")

        manifest = production_manifest()
        del application_of(manifest)["attributes"]["allowBackup"]
        self.assert_rejected(manifest, "android:allowBackup is absent; it must be false")

        manifest = production_manifest()
        application_of(manifest)["attributes"]["fullBackupContent"] = True
        self.assert_rejected(manifest, "android:fullBackupContent is true; it must be false")

        manifest = production_manifest()
        del application_of(manifest)["attributes"]["dataExtractionRules"]
        self.assert_rejected(manifest, "android:dataExtractionRules is missing")

    def test_debug_switches_must_be_off(self):
        for attribute in ("debuggable", "testOnly", "usesCleartextTraffic"):
            manifest = production_manifest()
            application_of(manifest)["attributes"][attribute] = True
            self.assert_rejected(manifest, f"android:{attribute} is true; it must be false or absent")
            application_of(manifest)["attributes"][attribute] = False
            for kind, artifact in self.artifacts(manifest):
                with self.subTest(kind=kind, attribute=attribute):
                    self.assertEqual(0, run(artifact).returncode)

    def test_attribute_is_recognised_by_resource_id_when_its_name_is_stripped(self):
        manifest = production_manifest()
        application_of(manifest)["attributes"]["debuggable"] = True
        artifact = write_apk(self.directory / "stripped.apk", manifest, strip_attribute_names=True)
        result = run(artifact)
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("android:debuggable is true", result.stderr)
        self.assertNotIn("allowBackup", result.stderr)

    def test_sdk_levels_and_package(self):
        manifest = production_manifest()
        manifest["children"][0]["attributes"]["targetSdkVersion"] = 35
        self.assert_rejected(manifest, "targetSdkVersion is 35, expected 36")

        manifest = production_manifest()
        manifest["children"][0]["attributes"]["minSdkVersion"] = 24
        self.assert_rejected(manifest, "minSdkVersion is 24, expected 26")

        manifest = production_manifest()
        manifest["attributes"]["package"] = "jp.co.soramitsu.sora.develop"
        self.assert_rejected(manifest, "package is jp.co.soramitsu.sora.develop, expected jp.co.soramitsu.sora")

        manifest = production_manifest()
        manifest["attributes"]["sharedUserId"] = "jp.co.soramitsu.shared"
        self.assert_rejected(manifest, "android:sharedUserId is set")

    def test_expected_version(self):
        for kind, artifact in self.artifacts(production_manifest()):
            with self.subTest(kind=kind):
                result = run("--expect-version-code", "3090002", artifact)
                self.assertEqual(1, result.returncode)
                self.assertIn("versionCode is 3090001, expected 3090002", result.stderr)
                result = run("--expect-version-name", "3.9.1", artifact)
                self.assertEqual(1, result.returncode)
                self.assertIn("versionName is 3.9.0, expected 3.9.1", result.stderr)

    def test_iroha_connect_without_the_production_overlay_is_rejected(self):
        # What app/src/main/AndroidManifest.xml declares when app/src/production does not
        # remove it.
        manifest = production_manifest()
        application_of(manifest)["children"].append(
            el(
                "activity",
                {"name": IROHA, "exported": True},
                deep_link_filter("iroha"),
                deep_link_filter("irohaconnect"),
            )
        )
        self.assert_rejected(
            manifest,
            f"forbidden component is packaged: <activity> {IROHA}",
            "deep link: <data host=connect, scheme=iroha> is declared",
            "deep link: <data host=connect, scheme=irohaconnect> is declared",
            "deep link: an intent filter accepts android.intent.action.VIEW",
            "deep link: an intent filter has the BROWSABLE category",
            f"exported component is not in the reviewed list: <activity> {IROHA}",
        )

    def test_any_deep_link_is_rejected(self):
        manifest = production_manifest()
        component(manifest, SPLASH)["children"].append(deep_link_filter("https"))
        self.assert_rejected(
            manifest,
            "deep link: <data host=connect, scheme=https> is declared",
            f"{SPLASH} must only be the launcher entry",
        )

    def test_debug_preview_and_alias_are_rejected(self):
        manifest = production_manifest()
        application_of(manifest)["children"].append(
            el("activity", {"name": "jp.co.soramitsu.sora.debug.SafeModePreviewActivity", "exported": False})
        )
        self.assert_rejected(manifest, "forbidden component is packaged")

        manifest = production_manifest()
        application_of(manifest)["children"].append(
            el("activity-alias", {"name": "jp.co.soramitsu.sora.Link", "targetActivity": IROHA, "exported": False})
        )
        self.assert_rejected(manifest, f"forbidden component is packaged: <activity-alias> {IROHA}")

    def test_permissions_must_match_the_reviewed_list(self):
        manifest = production_manifest()
        manifest["children"].insert(1, el("uses-permission", {"name": "android.permission.ACCESS_FINE_LOCATION"}))
        self.assert_rejected(
            manifest, "permission is not in the reviewed list: android.permission.ACCESS_FINE_LOCATION"
        )

        manifest = production_manifest()
        manifest["children"].insert(1, el("uses-permission-sdk-23", {"name": "android.permission.NFC"}))
        self.assert_rejected(manifest, "permission is not in the reviewed list: android.permission.NFC")

        manifest = production_manifest()
        manifest["children"] = [
            child for child in manifest["children"] if child["attributes"].get("name") != "android.permission.INTERNET"
        ]
        self.assert_rejected(manifest, "reviewed permission is no longer requested: android.permission.INTERNET")

    def test_exported_components_must_match_the_reviewed_list(self):
        manifest = production_manifest()
        component(manifest, "jp.co.soramitsu.feature_main_impl.presentation.MainActivity")["attributes"]["exported"] = True
        self.assert_rejected(manifest, "exported component is not in the reviewed list: <activity> jp.co.soramitsu.feature_main_impl")

        # No android:exported, but an intent filter: exported by default.
        manifest = production_manifest()
        del component(manifest, "androidx.work.impl.background.systemalarm.RescheduleReceiver")["attributes"]["exported"]
        self.assert_rejected(manifest, "exported component is not in the reviewed list: <receiver> androidx.work.impl.background.systemalarm.RescheduleReceiver")

        manifest = production_manifest()
        del component(manifest, "androidx.work.impl.diagnostics.DiagnosticsReceiver")["attributes"]["permission"]
        self.assert_rejected(manifest, "androidx.work.impl.diagnostics.DiagnosticsReceiver is guarded by permission absent, expected android.permission.DUMP")

        manifest = production_manifest()
        component(manifest, "androidx.core.content.FileProvider")["attributes"]["exported"] = True
        self.assert_rejected(manifest, "exported component is not in the reviewed list: <provider> androidx.core.content.FileProvider")

        # android:exported given as a resource reference cannot be judged, so it counts as exported.
        manifest = production_manifest()
        component(manifest, "androidx.core.content.FileProvider")["attributes"]["exported"] = "@0x7f050001"
        for artifact in (
            write_apk(self.directory / "reference.apk", manifest),
            write_aab(self.directory / "reference.aab", manifest),
        ):
            result = run(artifact)
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertIn(
                "exported component is not in the reviewed list: <provider> androidx.core.content.FileProvider",
                result.stderr,
            )

        manifest = production_manifest()
        component(manifest, SPLASH)["attributes"]["exported"] = False
        self.assert_rejected(manifest, f"reviewed exported component is missing or no longer exported: {SPLASH}")

    def test_queries_must_match_the_reviewed_list(self):
        manifest = production_manifest()
        manifest["children"].append(el("queries", None, el("package", {"name": "com.soracard.wallet.iban"})))
        self.assert_rejected(manifest, "<queries> is ['package com.soracard.wallet.iban'], expected []")

    def test_bundle_with_a_second_module_is_not_accepted(self):
        artifact = write_aab(self.directory / "two.aab", production_manifest(), extra_module=True)
        result = run(artifact)
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertIn("expected only base/manifest/AndroidManifest.xml", result.stderr)

    def test_unreadable_inputs_fail_closed(self):
        empty = self.directory / "empty.apk"
        empty.write_bytes(b"")
        self.assertEqual(2, run(empty).returncode)

        not_zip = self.directory / "text.apk"
        not_zip.write_text("not a zip")
        self.assertEqual(2, run(not_zip).returncode)

        with zipfile.ZipFile(self.directory / "plain-xml.apk", "w") as archive:
            archive.writestr("AndroidManifest.xml", "<manifest/>")
        result = run(self.directory / "plain-xml.apk")
        self.assertEqual(2, result.returncode)
        self.assertIn("not Android binary XML", result.stderr)

        truncated = encode_binary(production_manifest())[:-40]
        with zipfile.ZipFile(self.directory / "cut.apk", "w") as archive:
            archive.writestr("AndroidManifest.xml", truncated)
        self.assertEqual(2, run(self.directory / "cut.apk").returncode)

        other = self.directory / "app.zip"
        other.write_bytes(write_apk(self.directory / "x.apk", production_manifest()).read_bytes())
        self.assertEqual(2, run(other).returncode)

    def test_policy_file_is_validated(self):
        artifact = write_apk(self.directory / "app.apk", production_manifest())
        policy = json.loads(checker.DEFAULT_POLICY_FILE.read_text(encoding="utf-8"))

        broken = copy.deepcopy(policy)
        del broken["usesPermissions"]
        path = self.directory / "policy.json"
        path.write_text(json.dumps(broken))
        result = run("--policy", path, artifact)
        self.assertEqual(2, result.returncode)
        self.assertIn("usesPermissions must be a list", result.stderr)

        broken = copy.deepcopy(policy)
        broken["exportedComponents"]["x.Y"] = {}
        path.write_text(json.dumps(broken))
        result = run("--policy", path, artifact)
        self.assertEqual(2, result.returncode)
        self.assertIn("exportedComponents[x.Y] needs launcherOnly or permission", result.stderr)

        self.assertEqual(2, run("--policy", self.directory / "missing.json", artifact).returncode)

    def test_repository_policy_matches_what_the_app_declares(self):
        policy = checker.load_policy(checker.DEFAULT_POLICY_FILE)
        self.assertEqual("jp.co.soramitsu.sora", policy["applicationId"])
        self.assertEqual([], policy["queries"])
        self.assertIn(SPLASH, policy["exportedComponents"])
        build_script = (CHECKER.parent.parent / "app" / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn(f"targetSdk = {policy['targetSdkVersion']}\n", build_script)
        self.assertIn(f"minSdk = {policy['minSdkVersion']}\n", build_script)


if __name__ == "__main__":
    unittest.main()
