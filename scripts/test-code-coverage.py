#!/usr/bin/env python3
"""Contract checks for the coverage gate: scope, union, and fail-closed behavior."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("check-code-coverage.py")
spec = importlib.util.spec_from_file_location("coverage_gate", SCRIPT)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class CoverageGateTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        source = self.root / "app/src/main/kotlin/wallet/Flow.kt"
        source.parent.mkdir(parents=True)
        source.write_text("package wallet\nfun first() = 1\nfun second() = 2\n")

    def report(self, name, first, second, source="Flow.kt"):
        path = self.root / name
        path.write_text(f'<report><package name="wallet"><sourcefile name="{source}">'
                        f'<line nr="2" ci="{first}" mi="1"/>'
                        f'<line nr="3" ci="{second}" mi="1"/>'
                        '</sourcefile></package></report>')
        return path

    def test_combines_disjoint_hits_without_counting_lines_twice(self):
        a = self.report("unit.xml", 1, 0)
        b = self.report("device.xml", 0, 1)
        result = gate.summarize(self.root, [a, b, a])
        self.assertEqual((2, 2, 100), (result["coveredLines"], result["executableLines"], result["lineCoveragePercent"]))

    def test_never_excludes_an_uncovered_production_file(self):
        report = self.report("unit.xml", 0, 0)
        result = gate.summarize(self.root, [report])
        self.assertEqual(0, result["lineCoveragePercent"])
        self.assertEqual([2, 3], result["files"][0]["uncoveredLines"])

    def test_generated_sources_do_not_change_production_denominator(self):
        reports = [self.report("unit.xml", 1, 1), self.report("generated.xml", 0, 0, "DaggerFlow.java")]
        result = gate.summarize(self.root, reports)
        self.assertEqual(2, result["executableLines"])
        self.assertEqual(["wallet/DaggerFlow.java"], result["excludedNonProductionSources"])

    def test_empty_or_unmatched_reports_cannot_pass(self):
        with self.assertRaisesRegex(ValueError, "no app-owned"):
            gate.summarize(self.root, [self.report("generated.xml", 1, 1, "DaggerFlow.java")])

    def test_test_sources_cannot_inflate_coverage(self):
        source = self.root / "app/src/test/kotlin/wallet/FlowTest.kt"
        source.parent.mkdir(parents=True)
        source.write_text("package wallet\nclass FlowTest\n")
        self.assertNotIn("wallet/FlowTest.kt", gate.source_files(self.root))

    def test_test_only_module_is_not_application_code(self):
        source = self.root / "test_data/src/main/kotlin/wallet/Fixture.kt"
        source.parent.mkdir(parents=True)
        source.write_text("package wallet\nfun fixture() = 1\n")
        self.assertNotIn("wallet/Fixture.kt", gate.source_files(self.root))

    def test_cli_fails_below_eighty_percent(self):
        report = self.report("unit.xml", 1, 0)
        result = subprocess.run([sys.executable, str(SCRIPT), "--root", str(self.root), str(report)], capture_output=True, text=True)
        self.assertEqual(1, result.returncode)
        self.assertIn("50.0000%", result.stdout)

    def test_below_threshold_percentage_does_not_display_as_eighty(self):
        # 16000/20001 rounds to 80.00% at two decimals, but must still fail.
        report = self.root / "near-threshold.xml"
        lines = ''.join(f'<line nr="{number}" ci="{int(number <= 16000)}" mi="1"/>'
                        for number in range(1, 20002))
        report.write_text('<report><package name="wallet"><sourcefile name="Flow.kt">' +
                          lines + '</sourcefile></package></report>')
        result = subprocess.run([sys.executable, str(SCRIPT), "--root", str(self.root), str(report)], capture_output=True, text=True)
        self.assertEqual(1, result.returncode)
        self.assertIn("79.9960%", result.stdout)
        self.assertNotIn("coverage: 80", result.stdout)

    def test_source_manifest_rejects_edited_production_source(self):
        manifest = self.root / "manifest.json"
        manifest.write_text(json.dumps(gate.source_manifest(self.root)))
        gate.require_source_manifest(self.root, manifest)
        source = self.root / "app/src/main/kotlin/wallet/Flow.kt"
        source.write_text(source.read_text().replace("first() = 1", "first() = 42"))
        with self.assertRaisesRegex(ValueError, "sources changed"):
            gate.require_source_manifest(self.root, manifest)

    def test_source_manifest_rejects_new_untested_production_file(self):
        manifest = self.root / "manifest.json"
        manifest.write_text(json.dumps(gate.source_manifest(self.root)))
        (self.root / "app/src/main/kotlin/wallet/NewFlow.kt").write_text("package wallet\nfun untested() = 0\n")
        with self.assertRaisesRegex(ValueError, "sources changed"):
            gate.require_source_manifest(self.root, manifest)

    def test_source_manifest_rejects_deleted_production_file(self):
        manifest = self.root / "manifest.json"
        manifest.write_text(json.dumps(gate.source_manifest(self.root)))
        (self.root / "app/src/main/kotlin/wallet/Flow.kt").unlink()
        with self.assertRaisesRegex(ValueError, "sources changed"):
            gate.require_source_manifest(self.root, manifest)

    def test_tests_and_generated_files_do_not_invalidate_source_manifest(self):
        manifest = self.root / "manifest.json"
        manifest.write_text(json.dumps(gate.source_manifest(self.root)))
        for relative in ("app/src/test/kotlin/wallet/FlowTest.kt", "app/build/generated/FlowFactory.kt"):
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("package wallet\nclass Generated\n")
        gate.require_source_manifest(self.root, manifest)


if __name__ == "__main__":
    unittest.main()
