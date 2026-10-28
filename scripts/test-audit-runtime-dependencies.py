#!/usr/bin/env python3
"""Offline regression checks: incomplete OSV responses must never report clean."""

import importlib.util
import pathlib
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("audit", pathlib.Path(__file__).with_name("audit-runtime-dependencies.py"))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class RuntimeDependencyAuditTest(unittest.TestCase):
    def test_runtime_scope_and_deduplication(self):
        self.assertEqual(audit.runtime_coordinates(
            "g:a:1=productionReleaseRuntimeClasspath\n"
            "g:a:1=productionReleaseRuntimeClasspath,productionReleaseCompileClasspath\n"
            "g:test:2=productionReleaseUnitTestRuntimeClasspath\n"
        ), ["g:a:1"])

    def test_empty_inventory_rejected(self):
        with self.assertRaises(ValueError):
            audit.runtime_coordinates("g:a:1=developDebugRuntimeClasspath\n")

    def test_empty_result_is_valid(self):
        self.assertEqual(audit.advisory_ids({}, {}), [])

    def test_error_or_malformed_results_rejected(self):
        for result in [None, [], {"error": "unavailable"}, {"error": None},
                       {"vulns": None}, {"vulns": {}}, {"vulns": [None]},
                       {"vulns": [{}]}, {"vulns": [{"id": 1}]},
                       {"vulns": [{"id": " "}]}, {"next_page_token": None}]:
            with self.subTest(result=result), self.assertRaises(ValueError):
                audit.advisory_ids({}, result)

    def test_pagination_is_deduplicated(self):
        with patch.object(audit, "request_json", return_value={"vulns": [{"id": "B"}, {"id": "A"}]}) as request:
            self.assertEqual(audit.advisory_ids({"version": "1"}, {
                "vulns": [{"id": "A"}], "next_page_token": "next"
            }), ["A", "B"])
            request.assert_called_once_with("query", {"version": "1", "page_token": "next"})

    def test_paginated_error_rejected(self):
        with patch.object(audit, "request_json", return_value={"error": "unavailable"}), self.assertRaises(ValueError):
            audit.advisory_ids({}, {"next_page_token": "next"})

    def test_repeated_token_rejected(self):
        with patch.object(audit, "request_json", return_value={"next_page_token": "next"}) as request:
            with self.assertRaises(ValueError):
                audit.advisory_ids({}, {"next_page_token": "next"})
            self.assertEqual(request.call_count, 1)

    def test_malformed_batch_does_not_write_report(self):
        with tempfile.TemporaryDirectory() as directory:
            lock, output = pathlib.Path(directory) / "lock", pathlib.Path(directory) / "report.json"
            lock.write_text("g:a:1=productionReleaseRuntimeClasspath\n")
            for response in [None, {"error": "unavailable"}, {}, {"results": {}},
                             {"results": []}, {"results": [{"error": "failed"}]}]:
                with self.subTest(response=response), patch.object(audit.sys, "argv", [
                    "audit", "--lock", str(lock), "--output", str(output)
                ]), patch.object(audit, "request_json", return_value=response), self.assertRaises(ValueError):
                    audit.main()
                self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
