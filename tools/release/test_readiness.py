# SPDX-License-Identifier: EPL-2.0
"""Synthetic parser/source guard tests. Never interpret fixture success as SWT CI."""
import importlib.util
import copy
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

PATH = Path(__file__).with_name("readiness.py")
spec = importlib.util.spec_from_file_location("swt_release_readiness", PATH)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class ReadinessTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for item in gate.REQUIRED:
            target = self.root / item
            if item.startswith("tests/"):
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text("fixture\n", encoding="utf-8")
        (self.root / "bundles/org.eclipse.swt/META-INF/MANIFEST.MF").write_text(
            "Bundle-SymbolicName: org.eclipse.swt; singleton:=true\n"
            "Automatic-Module-Name: org.eclipse.swt\n"
            "Bundle-RequiredExecutionEnvironment: JavaSE-21\n"
            "Export-Package: org.eclipse.swt.widgets,\n"
            " org.eclipse.swt.graphics\n", encoding="utf-8")

    def write(self, name, content, root=None):
        path = (self.root if root is None else root) / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def junit(self, name, failure=0, root=None):
        detail = '<failure message="fixture"/>' if failure else ''
        return self.write(name, f'<testsuite tests="3" failures="{failure}" '
                          'errors="0" skipped="1">'
                          '<testcase name="one" />'
                          '<testcase name="two"><skipped/></testcase>'
                          f'<testcase name="three">{detail}</testcase>'
                          '</testsuite>', root=root)

    def jacoco(self, line_covered=99, branch_covered=99, root=None):
        return self.write("jacoco.xml", '<report name="fixture">'
                          f'<counter type="LINE" covered="{line_covered}" missed="1"/>'
                          f'<counter type="BRANCH" covered="{branch_covered}" missed="1"/>'
                          '</report>', root=root)

    def git(self, *args):
        return subprocess.run(["git", "-C", str(self.root), *args], check=True,
                              capture_output=True, text=True).stdout.strip()

    def bound_reports(self):
        """Synthetic content-binding fixture; never a producer attestation."""
        self.git("init", "--quiet")
        self.git("add", ".")
        self.git("-c", "user.name=SWT parser fixture", "-c", "user.email=fixture@example.invalid",
                 "commit", "--quiet", "-m", "Synthetic source anchors")
        source = self.git("rev-parse", "HEAD")
        evidence_temp = tempfile.TemporaryDirectory()
        self.addCleanup(evidence_temp.cleanup)
        self.evidence = Path(evidence_temp.name)
        files = {}
        for backend in gate.BACKENDS:
            report = self.junit(backend + "/TEST-fixture.xml", root=self.evidence)
            report.write_text(report.read_text().replace('name="one"', f'name="{backend}"'))
            files[backend] = report.parent
        coverage = self.jacoco(root=self.evidence)
        identity = {"source_sha": source, "run_id": 12345, "run_attempt": 2}
        payload = {"schema": gate.BINDING_SCHEMA, "repository": gate.REPOSITORY,
                   **identity, "run_url": f"https://github.com/{gate.REPOSITORY}/actions/runs/12345/attempts/2",
                   "junit": {backend: {**identity, "backend": backend,
                                       "files": gate.junit_report(path)["files"]}
                             for backend, path in files.items()},
                   "jacoco": {**identity, "backends": list(gate.BACKENDS),
                              "files": [{"path": coverage.name, "sha256": gate.jacoco_report(coverage)["sha256"]}]}}
        manifest = self.write("binding.json", json.dumps(payload), root=self.evidence)
        return files, coverage, source, manifest, payload

    def evaluate_bound(self, fixture):
        files, coverage, source, manifest, _ = fixture
        return gate.evaluate(self.root, files, coverage, True, .9, .9, source, manifest)

    def assert_binding_refused(self, fixture, message):
        result = self.evaluate_bound(fixture)
        self.assertFalse(result["surface_ready"])
        self.assertFalse(result["release_admitted"])
        self.assertIsNone(result["report_binding"])
        self.assertTrue(any(message in issue for issue in result["source_issues"]), result["source_issues"])

    def test_manifest_naming_and_source_anchors(self):
        self.assertEqual([], gate.inspect_source(self.root))
        manifest = self.root / "bundles/org.eclipse.swt/META-INF/MANIFEST.MF"
        manifest.write_text("Bundle-SymbolicName: wrong.toolkit\n")
        self.assertTrue(gate.inspect_source(self.root))

    def test_junit_counts_separate_skips_and_failures(self):
        counts = gate.junit_report(self.junit("TEST-demo.xml"))
        self.assertEqual((3, 1, 0), (counts["tests"], counts["skipped"], counts["failures"]))
        self.assertEqual(1, gate.junit_report(self.junit("TEST-bad.xml", 1))["failures"])

    def test_inconsistent_reported_counts_are_refused(self):
        fraudulent = self.write("TEST-claimed.xml", '<testsuite tests="99" '
                                'failures="0"><testcase name="only"/></testsuite>')
        with self.assertRaisesRegex(ValueError, "counter mismatch"):
            gate.junit_report(fraudulent)
        no_cases = self.write("TEST-no-cases.xml", '<testsuite tests="99"/>')
        with self.assertRaisesRegex(ValueError, "no executed testcases"):
            gate.junit_report(no_cases)

    def test_nested_aggregate_suite_does_not_double_count(self):
        path = self.write("TEST-aggregate.xml",
                          '<testsuites tests="2" failures="0" skipped="1">'
                          '<testsuite tests="2" failures="0" skipped="1">'
                          '<testcase name="one"/>'
                          '<testcase name="two"><skipped/></testcase>'
                          '</testsuite></testsuites>')
        report = gate.junit_report(path)
        self.assertEqual((2, 1), (report["tests"], report["skipped"]))
        self.assertEqual(64, len(report["files"][0]["sha256"]))

    def test_directory_requires_real_test_report_names(self):
        self.write("evidence/random.xml", '<testsuite tests="1"><testcase name="one"/></testsuite>')
        with self.assertRaisesRegex(ValueError, "no TEST-"):
            gate.junit_report(self.root / "evidence")

    def test_invalid_or_empty_junit_fails_closed(self):
        with self.assertRaises(ValueError):
            gate.junit_report(self.write("TEST-empty.xml", '<testsuite tests="0"/>'))
        with self.assertRaises(ValueError):
            gate.junit_report(self.write("TEST-invalid.xml", '<other/>'))

    def test_all_skipped_backend_is_not_executed(self):
        with self.assertRaisesRegex(ValueError, "all skipped"):
            gate.junit_report(self.write("TEST-skipped.xml",
                              '<testsuite tests="3" failures="0" errors="0" skipped="3">'
                              '<testcase name="one"><skipped/></testcase>'
                              '<testcase name="two"><skipped/></testcase>'
                              '<testcase name="three"><skipped/></testcase></testsuite>'))

    def test_all_skipped_backend_blocks_release_surface(self):
        files = {backend: self.junit("TEST-" + backend + ".xml") for backend in gate.BACKENDS}
        files["win32"] = self.write("TEST-win32-skipped.xml",
                                    '<testsuite tests="3" failures="0" errors="0" skipped="3">'
                              '<testcase name="one"><skipped/></testcase>'
                              '<testcase name="two"><skipped/></testcase>'
                              '<testcase name="three"><skipped/></testcase></testsuite>')
        result = gate.evaluate(self.root, files, self.jacoco(), True, .9, .9)
        self.assertFalse(result["surface_ready"])
        self.assertIn("win32", result["pending_backends"])
        self.assertTrue(any("all skipped" in issue for issue in result["source_issues"]))

    def test_jacoco_counts_and_missing_branch(self):
        metrics = gate.jacoco_report(self.jacoco())
        self.assertEqual(.99, metrics["line"]["ratio"])
        self.assertEqual(.99, metrics["branch"]["ratio"])
        with self.assertRaises(ValueError):
            gate.jacoco_report(self.write("nocounters.xml", '<report><counter type="LINE" covered="1" missed="1"/></report>'))

    def test_duplicate_jacoco_counter_types_are_refused(self):
        for kind in ("LINE", "BRANCH", "INSTRUCTION"):
            with self.subTest(kind=kind):
                report = ('<report><counter type="LINE" covered="99" missed="1"/>'
                          '<counter type="BRANCH" covered="99" missed="1"/>'
                          '<counter type="INSTRUCTION" covered="99" missed="1"/>'
                          f'<counter type="{kind}" covered="1" missed="99"/></report>')
                with self.assertRaisesRegex(ValueError, "duplicate JaCoCo counter type"):
                    gate.jacoco_report(self.write("duplicate-counter.xml", report))

    def test_source_only_never_becomes_release_ready(self):
        result = gate.evaluate(self.root, {}, None, False, .9, .9)
        self.assertFalse(result["surface_ready"])
        self.assertFalse(result["source_issues"])
        self.assertEqual(len(gate.BACKENDS), len(result["pending_backends"]))

    def test_missing_backend_and_coverage_block_release(self):
        result = gate.evaluate(self.root, {"win32": self.junit("TEST-win32.xml")}, None, True, .9, .9)
        self.assertFalse(result["surface_ready"])
        self.assertTrue(any("missing platform JUnit" in x for x in result["source_issues"]))

    def test_failed_junit_and_insufficient_branch_coverage_block(self):
        files = {backend: self.junit("TEST-" + backend + ".xml") for backend in gate.BACKENDS}
        files["win32"] = self.junit("TEST-win32-fail.xml", 1)
        result = gate.evaluate(self.root, files, self.jacoco(branch_covered=8), True, .9, .9)
        self.assertFalse(result["surface_ready"])
        self.assertTrue(any("failed JUnit: win32" in x for x in result["source_issues"]))
        self.assertTrue(any("branch coverage" in x for x in result["source_issues"]))

    def test_duplicate_backend_paths_are_refused(self):
        path = self.junit("TEST-one.xml")
        result = gate.evaluate(self.root, {"win32": path, "gtk3-x11": path},
                               self.jacoco(), True, .9, .9)
        self.assertFalse(result["surface_ready"])
        self.assertTrue(any("duplicate backend JUnit" in x
                            for x in result["source_issues"]))

    def test_release_cannot_disable_coverage_thresholds(self):
        with self.assertRaises(SystemExit) as caught:
            gate.main(["--root", str(self.root), "--release",
                       "--min-line", "0", "--min-branch", "0"])
        self.assertEqual(2, caught.exception.code)
        with self.assertRaises(SystemExit) as caught:
            gate.main(["--root", str(self.root), "--min-line", "nan"])
        self.assertEqual(2, caught.exception.code)

    def test_complete_synthetic_surface_is_not_product_evidence(self):
        files = {backend: self.junit("TEST-" + backend + ".xml") for backend in gate.BACKENDS}
        result = gate.evaluate(self.root, files, self.jacoco(), True, .9, .9)
        self.assertTrue(any("source-bound reports require" in issue for issue in result["source_issues"]))
        self.assertFalse(result["surface_ready"])
        self.assertFalse(result["release_admitted"])
        self.assertIsNone(result["report_binding"])
        self.assertIn("not a native/UI", result["scope"])

    def test_bound_synthetic_surface_still_cannot_admit_product_release(self):
        fixture = self.bound_reports()
        result = self.evaluate_bound(fixture)
        self.assertEqual([], result["source_issues"])
        self.assertTrue(result["surface_ready"])
        self.assertFalse(result["release_admitted"])
        self.assertEqual(fixture[2], result["report_binding"]["source_sha"])
        self.assertTrue(result["report_binding"]["source_clean"])
        self.assertEqual("not-verified", result["report_binding"]["producer_authenticity"])
        self.assertIn("producer authenticity", result["remaining_release_gates"][0])
        self.assertEqual(64, len(result["report_binding"]["manifest_sha256"]))

    def test_bound_report_flags_are_paired_even_in_source_mode(self):
        fixture = self.bound_reports()
        for source, manifest in ((fixture[2], None), (None, fixture[3])):
            result = gate.evaluate(self.root, {}, None, False, .9, .9, source, manifest)
            self.assertTrue(any("source-bound reports require" in issue for issue in result["source_issues"]))
            self.assertFalse(result["surface_ready"])

    def test_binding_refuses_wrong_head_non_git_source_and_subdirectory(self):
        with self.assertRaisesRegex(ValueError, "inspectable Git"):
            gate.verify_source(self.root, "a" * 40)
        fixture = self.bound_reports()
        with self.assertRaisesRegex(ValueError, "worktree root"):
            gate.verify_source(self.root / "tests", fixture[2])
        self.git("-c", "user.name=SWT parser fixture", "-c", "user.email=fixture@example.invalid",
                 "commit", "--quiet", "--allow-empty", "-m", "Different source revision")
        self.assert_binding_refused(fixture, "HEAD does not match")

    def test_binding_requires_full_lowercase_expected_sha(self):
        fixture = self.bound_reports()
        for source in ("main", fixture[2][:12], "A" * 40, 42):
            altered = (*fixture[:2], source, *fixture[3:])
            with self.subTest(source=source):
                self.assert_binding_refused(altered, "full lowercase Git")

    def test_binding_refuses_staged_unstaged_and_deleted_tracked_source(self):
        fixture = self.bound_reports()
        for state in ("unstaged", "staged", "deleted"):
            with self.subTest(state=state):
                target = self.root / "README.md"
                if state == "deleted":
                    target.unlink()
                else:
                    target.write_text("Changed release source\n")
                    if state == "staged":
                        self.git("add", "README.md")
                self.assert_binding_refused(fixture, "tracked changes")
                self.git("reset", "--hard", "HEAD")

    def test_binding_refuses_untracked_source_but_allows_ignored_build_outputs(self):
        self.write(".gitignore", "target/\n")
        fixture = self.bound_reports()
        self.write("target/build-output.txt", "ignored generated output\n")
        self.assertTrue(self.evaluate_bound(fixture)["surface_ready"])
        self.write("Unexpected.java", "class Unexpected {}\n")
        self.assert_binding_refused(fixture, "untracked changes")

    def test_binding_refuses_report_substitution_extra_and_missing_files(self):
        fixture = self.bound_reports()
        files, _, _, _, _ = fixture
        original = files["win32"] / "TEST-fixture.xml"
        content = original.read_text()
        original.write_text(content.replace('name="win32"', 'name="substituted"'))
        self.assert_binding_refused(fixture, "win32 report inventory or SHA-256 mismatch")
        original.write_text(content)
        extra = self.junit("win32/TEST-extra.xml", root=self.evidence)
        self.assert_binding_refused(fixture, "win32 report inventory or SHA-256 mismatch")
        extra.unlink()
        original.unlink()
        self.assert_binding_refused(fixture, "all six parsed backend reports")

    def test_binding_refuses_substituted_jacoco(self):
        fixture = self.bound_reports()
        fixture[1].write_text(fixture[1].read_text().replace('name="fixture"', 'name="other-source"'))
        self.assert_binding_refused(fixture, "JaCoCo report inventory or SHA-256 mismatch")

    def test_binding_refuses_hardlinks_and_copied_backend_reports(self):
        fixture = self.bound_reports()
        files, _, _, manifest, payload = fixture
        original = files["gtk3-x11"] / "TEST-fixture.xml"
        reused = files["win32"] / "TEST-fixture.xml"
        reused.unlink()
        os.link(original, reused)
        self.assert_binding_refused(fixture, "all six parsed backend reports")
        result = self.evaluate_bound(fixture)
        self.assertTrue(any("duplicate/aliased JUnit" in issue for issue in result["source_issues"]))
        reused.unlink()
        reused.write_bytes(original.read_bytes())
        payload["junit"]["win32"]["files"] = gate.junit_report(files["win32"])["files"]
        manifest.write_text(json.dumps(payload))
        result = self.evaluate_bound(fixture)
        self.assertFalse(result["surface_ready"])
        self.assertTrue(any("reused JUnit report bytes" in issue for issue in result["source_issues"]))

    def test_junit_refuses_symlink_inputs_and_nonregular_inventory(self):
        fixture = self.bound_reports()
        files = fixture[0]
        alias = self.evidence / "alias"
        alias.symlink_to(files["win32"], target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "symlink JUnit report input"):
            gate.junit_report(alias)
        linked = files["win32"] / "TEST-link.xml"
        linked.symlink_to(files["win32"] / "TEST-fixture.xml")
        with self.assertRaisesRegex(ValueError, "regular files"):
            gate.junit_report(files["win32"])
        linked.unlink()
        linked.mkdir()
        with self.assertRaisesRegex(ValueError, "regular files"):
            gate.junit_report(files["win32"])

    def test_junit_and_jacoco_hash_the_same_snapshot_they_parse(self):
        from unittest.mock import patch
        for kind in ("junit", "jacoco"):
            with self.subTest(kind=kind):
                path = self.junit("TEST-changing.xml") if kind == "junit" else self.jacoco()
                original = path.read_bytes()
                replacement = original.replace(b"fixture", b"changed-after-parse")
                if kind == "junit":
                    replacement = replacement.replace(b'name="one"', b'name="changed-after-parse"')
                def rewrite_after(parser):
                    def wrapped(*args, **kwargs):
                        result = parser(*args, **kwargs)
                        path.write_bytes(replacement)
                        return result
                    return wrapped
                with patch.object(gate.ET, "parse", rewrite_after(gate.ET.parse)):
                    with patch.object(gate.ET, "fromstring", rewrite_after(gate.ET.fromstring)):
                        result = gate.junit_report(path) if kind == "junit" else gate.jacoco_report(path)
                digest = result["files"][0]["sha256"] if kind == "junit" else result["sha256"]
                self.assertEqual(hashlib.sha256(original).hexdigest(), digest)
                self.assertEqual(replacement, path.read_bytes())
                self.assertNotEqual(hashlib.sha256(replacement).hexdigest(), digest)

    def test_binding_refuses_manifest_identity_and_run_inconsistency(self):
        fixture = self.bound_reports()
        mutations = (
            ((), "schema", "other", "unsupported binding"),
            ((), "repository", "other/repository", "unsupported binding"),
            ((), "source_sha", "f" * 40, "manifest source SHA"),
            ((), "run_id", 0, "positive integer"),
            ((), "run_attempt", True, "positive integer"),
            ((), "run_url", "https://github.com/other/repository/actions/runs/12345/attempts/2", "run URL"),
            ((), "run_url", f"https://github.com/{gate.REPOSITORY}/actions/runs/12345/attempts/1", "run URL"),
            (("junit", "win32"), "backend", "gtk3-x11", "source/backend/run/attempt mismatch"),
            (("junit", "win32"), "source_sha", "f" * 40, "source/backend/run/attempt mismatch"),
            (("junit", "win32"), "run_id", 12346, "source/backend/run/attempt mismatch"),
            (("junit", "win32"), "run_attempt", 1, "source/backend/run/attempt mismatch"),
            (("junit", "win32"), "run_attempt", 2.0, "source/backend/run/attempt mismatch"),
            (("jacoco",), "source_sha", "f" * 40, "JaCoCo source/backends/run/attempt mismatch"),
            (("jacoco",), "run_id", 12346, "JaCoCo source/backends/run/attempt mismatch"),
            (("jacoco",), "run_attempt", 1, "JaCoCo source/backends/run/attempt mismatch"),
            (("jacoco",), "backends", ["win32"], "JaCoCo source/backends/run/attempt mismatch"),
        )
        for location, key, value, message in mutations:
            with self.subTest(location=location, key=key, value=value):
                payload = copy.deepcopy(fixture[4])
                target = payload
                for part in location:
                    target = target[part]
                target[key] = value
                fixture[3].write_text(json.dumps(payload))
                self.assert_binding_refused(fixture, message)

    def test_binding_refuses_incomplete_unknown_or_duplicate_manifest_fields(self):
        fixture = self.bound_reports()
        payload = fixture[4]
        missing = copy.deepcopy(payload)
        del missing["junit"]["gtk4-wayland"]
        unknown = copy.deepcopy(payload)
        unknown["junit"]["unknown-backend"] = unknown["junit"]["win32"]
        for content, message in (("[]", "binding manifest must contain exactly"),
                                 ("{", "report binding:"),
                                 (json.dumps({**payload, "extra": True}), "must contain exactly"),
                                 (json.dumps(missing), "binding JUnit backends"),
                                 (json.dumps(unknown), "binding JUnit backends"),
                                 (json.dumps(payload)[:-1] + ',"source_sha":"duplicate"}', "duplicate binding manifest key")):
            with self.subTest(message=message):
                fixture[3].write_text(content)
                self.assert_binding_refused(fixture, message)
        fixture[3].unlink()
        self.assert_binding_refused(fixture, "report binding:")

    def test_binding_refuses_invalid_duplicate_and_incomplete_report_inventory(self):
        fixture = self.bound_reports()
        original = fixture[4]["junit"]["win32"]["files"][0]
        inventories = (None, [], {}, [original, original],
                       [{**original, "path": None}], [{**original, "path": ""}],
                       [{**original, "sha256": 42}], [{**original, "sha256": "not-a-hash"}],
                       [{**original, "sha256": "A" * 64}], [{**original, "extra": True}],
                       [{**original, "path": "../TEST-fixture.xml"}],
                       [{**original, "path": "/TEST-fixture.xml"}],
                       [{**original, "path": "./TEST-fixture.xml"}],
                       [{**original, "path": "nested\\TEST-fixture.xml"}],
                       [{**original, "path": "TEST-absent.xml"}],
                       [original, {**original, "path": "TEST-extra.xml"}])
        for files in inventories:
            with self.subTest(files=files):
                payload = copy.deepcopy(fixture[4])
                payload["junit"]["win32"]["files"] = files
                fixture[3].write_text(json.dumps(payload))
                self.assert_binding_refused(fixture, "report binding:")

    def test_binding_cannot_replace_junit_failures_or_coverage_floor(self):
        fixture = self.bound_reports()
        files, coverage, _, manifest, payload = fixture
        self.junit("win32/TEST-fixture.xml", failure=1, root=self.evidence)
        self.jacoco(line_covered=8, root=self.evidence)
        payload["junit"]["win32"]["files"] = gate.junit_report(files["win32"])["files"]
        payload["jacoco"]["files"][0]["sha256"] = gate.jacoco_report(coverage)["sha256"]
        manifest.write_text(json.dumps(payload))
        result = self.evaluate_bound(fixture)
        self.assertIsNotNone(result["report_binding"])
        self.assertFalse(result["surface_ready"])
        self.assertFalse(result["release_admitted"])
        self.assertTrue(any("failed JUnit: win32" in issue for issue in result["source_issues"]))
        self.assertTrue(any("line coverage below" in issue for issue in result["source_issues"]))

    def test_binding_cli_outputs_content_binding_without_release_admission(self):
        import contextlib
        import io
        fixture = self.bound_reports()
        files, coverage, source, manifest, _ = fixture
        output = self.evidence / "results" / "surface.json"
        arguments = ["--root", str(self.root), "--release", "--jacoco", str(coverage),
                     "--source-sha", source, "--evidence-manifest", str(manifest), "--json-out", str(output)]
        for backend, path in files.items():
            arguments.extend(("--junit", f"{backend}={path}"))
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, gate.main(arguments))
        result = json.loads(output.read_text())
        self.assertTrue(result["surface_ready"])
        self.assertFalse(result["release_admitted"])
        self.assertEqual(source, result["report_binding"]["source_sha"])


    def test_missing_manifest_is_reported(self):
        manifest = self.root / "bundles/org.eclipse.swt/META-INF/MANIFEST.MF"
        manifest.unlink()
        self.assertTrue(any("MANIFEST.MF" in x for x in gate.inspect_source(self.root)))

    def test_junit_missing_names_errors_and_bad_aggregation(self):
        for name, data, message in (
            ("TEST-empty-suites.xml", '<testsuites/>', "empty JUnit XML"),
            ("TEST-no-name.xml", '<testsuite tests="1"><testcase/></testsuite>', "no name"),
        ):
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, message):
                gate.junit_report(self.write(name, data))
        parsed = gate.junit_report(self.write("TEST-errors.xml",
            '<testsuite tests="1" errors="1"><testcase name="bad"><error/></testcase></testsuite>'))
        self.assertEqual(1, parsed["errors"])
        aggregate = self.write("TEST-no-root-count.xml",
            '<testsuites><testsuite tests="2" skipped="1" errors="0" failures="0">'
            '<testcase name="a"/><testcase name="b"><skipped/></testcase>'
            '</testsuite></testsuites>')
        self.assertEqual((2, 1), (gate.junit_report(aggregate)["tests"], gate.junit_report(aggregate)["skipped"]))

    def test_jacoco_invalid_root_and_counts_refused(self):
        for name, xml, message in (
            ("root.xml", '<other/>', "not a JaCoCo"),
            ("negative.xml", '<report><counter type="LINE" covered="-1" missed="1"/>'
             '<counter type="BRANCH" covered="2" missed="1"/></report>', "invalid/empty JaCoCo"),
            ("zero.xml", '<report><counter type="LINE" covered="0" missed="0"/>'
             '<counter type="BRANCH" covered="2" missed="0"/></report>', "invalid/empty JaCoCo"),
        ):
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, message):
                gate.jacoco_report(self.write(name, xml))

    def test_evaluate_reports_unknown_backend_and_bad_inputs(self):
        root_issue = gate.evaluate(self.root, {"invalid-backend": self.junit("TEST-unknown.xml")},
                                   None, False, .90, .90)
        self.assertTrue(any("unknown backend" in e for e in root_issue["source_issues"]))
        malformed = self.write("TEST-broken.xml", "<testsuite")
        result = gate.evaluate(self.root, {"win32": malformed},
                               self.write("bad-jacoco.xml", "<report"), True, .90, .90)
        self.assertFalse(result["surface_ready"])
        self.assertTrue(any("JUnit win32" in x for x in result["source_issues"]))
        self.assertTrue(any("JaCoCo" in x for x in result["source_issues"]))

    def test_cli_argument_validation_and_json_output(self):
        import contextlib
        import io
        import json
        for token in ("", "not-a-backend=xyz", "win32="):
            with self.subTest(token=token), self.assertRaises(Exception):
                gate.parse_label_path(token)
        self.assertEqual(("win32", Path("out.xml")), gate.parse_label_path("win32=out.xml"))
        output = self.root / "summary.json"
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(0, gate.main(["--root", str(self.root), "--json-out", str(output)]))
        self.assertFalse(json.loads(output.read_text())["surface_ready"])
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(1, gate.main(["--root", str(self.root), "--release"]))
        with contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as err:
                gate.main(["--root", str(self.root), "--junit", "win32=x", "--junit", "win32=y"])
        self.assertEqual(2, err.exception.code)

    def test_main_script_entrypoint_is_read_only(self):
        import contextlib
        import io
        import runpy
        import sys
        from unittest.mock import patch
        with patch.object(sys, "argv", [str(PATH), "--root", str(self.root)]):
            with contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaises(SystemExit) as exit_info:
                    runpy.run_path(str(PATH), run_name="__main__")
        self.assertEqual(0, exit_info.exception.code)


    def test_unattributed_aggregate_suite_uses_testcase_counts(self):
        report = self.write("TEST-aggregate-no-counters.xml",
                            '<testsuites><testsuite><testcase name="only"/></testsuite></testsuites>')
        self.assertEqual(1, gate.junit_report(report)["tests"])


if __name__ == "__main__":
    unittest.main()
