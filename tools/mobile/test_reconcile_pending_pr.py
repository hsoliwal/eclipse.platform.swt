"""Hermetic, deterministic tests for the pending-PR blob identity gate."""

import json
import tempfile
import unittest
from pathlib import Path

from reconcile_pending_pr import AuditError, audit, git_blob_sha, main, read_manifest


class ReconciliationAuditTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.left = self.root / "candidate"
        self.right = self.root / "baseline"
        self.left.mkdir()
        self.right.mkdir()
        self.manifest = self.root / "inventory.tsv"
        self.rel = "src/a File.java"
        for folder, body in ((self.left, b"class A {}\n"), (self.right, b"class A { int x; }\n")):
            target = folder / self.rel
            target.parent.mkdir()
            target.write_bytes(body)
        self.write_manifest()

    def write_manifest(self):
        self.manifest.write_text(
            "path\tcandidate_git_blob\tbaseline_git_blob\n"
            + self.rel + "\t"
            + git_blob_sha((self.left / self.rel).read_bytes()) + "\t"
            + git_blob_sha((self.right / self.rel).read_bytes()) + "\n",
            encoding="utf-8",
        )

    def test_exact_source_classifies_and_replays_byte_identically(self):
        report = self.root / "audit.json"
        args = ["--candidate", str(self.left), "--baseline", str(self.right),
                "--manifest", str(self.manifest), "--report", str(report)]
        self.assertEqual(0, main(args))
        first = report.read_bytes()
        self.assertEqual(0, main(args))
        self.assertEqual(first, report.read_bytes())
        self.assertEqual({"different": 1, "identical": 0, "inspected": 1}, json.loads(first)["summary"])

    def test_blob_id_matches_git_object_encoding(self):
        self.assertEqual("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391", git_blob_sha(b""))

    def test_stale_candidate_is_refused(self):
        (self.left / self.rel).write_bytes(b"changed")
        with self.assertRaisesRegex(AuditError, "candidate content drift"):
            audit(self.left, self.right, self.manifest)

    def test_stale_baseline_is_refused(self):
        (self.right / self.rel).write_bytes(b"changed")
        with self.assertRaisesRegex(AuditError, "baseline content drift"):
            audit(self.left, self.right, self.manifest)

    def test_duplicate_manifest_paths_are_refused(self):
        self.manifest.write_text(self.manifest.read_text() + self.manifest.read_text().splitlines()[1] + "\n")
        with self.assertRaisesRegex(AuditError, "sorted or duplicated"):
            read_manifest(self.manifest)

    def test_path_escape_is_refused(self):
        self.rel = "../outside.java"
        self.manifest.write_text("path\tcandidate_git_blob\tbaseline_git_blob\n" +
                                 self.rel + "\t" + "0"*40 + "\t" + "1"*40 + "\n")
        with self.assertRaisesRegex(AuditError, "noncanonical"):
            audit(self.left, self.right, self.manifest)

    def test_missing_file_is_refused(self):
        (self.right / self.rel).unlink()
        with self.assertRaisesRegex(AuditError, "missing or escaping"):
            audit(self.left, self.right, self.manifest)

    def test_symlink_is_refused(self):
        (self.right / self.rel).unlink()
        try:
            (self.right / self.rel).symlink_to(self.left / self.rel)
        except (OSError, NotImplementedError):
            self.skipTest("symlink creation unavailable")
        with self.assertRaisesRegex(AuditError, "symlink"):
            audit(self.left, self.right, self.manifest)

    def test_existing_report_cannot_be_silently_overwritten(self):
        report = self.root / "audit.json"
        report.write_text("stale")
        args = ["--candidate", str(self.left), "--baseline", str(self.right),
                "--manifest", str(self.manifest), "--report", str(report)]
        self.assertEqual(1, main(args))
        self.assertEqual("stale", report.read_text())


if __name__ == "__main__":
    unittest.main()
