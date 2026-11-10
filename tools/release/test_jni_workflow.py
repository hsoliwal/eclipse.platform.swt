# SPDX-License-Identifier: EPL-2.0
"""Source-backed checks for JNI diagnostics in SWT's actual native test JVM."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def require_jni_test_contract(pom_body: str, workflow_body: str) -> None:
    """Preserve instrumentation and platform flags while enabling JNI checks."""
    pom = ET.fromstring(pom_body)
    plugins = {
        plugin.findtext("m:artifactId", namespaces=NS): plugin
        for plugin in pom.findall("m:build/m:plugins/m:plugin", NS)
    }
    provider = plugins["maven-surefire-plugin"]
    executions = [execution for execution in provider.findall("m:executions/m:execution", NS)
                  if execution.findtext("m:id", namespaces=NS) == "test"]
    if len(executions) != 1:
        raise AssertionError("Exactly one actual Surefire test execution is required")
    execution = executions[0]
    if (execution.findtext("m:phase", namespaces=NS) != "integration-test"
            or execution.findtext("m:goals/m:goal", namespaces=NS) != "test"):
        raise AssertionError("JNI flags must reach the integration-test JVM")
    arguments = execution.findtext("m:configuration/m:argLine", namespaces=NS).split()
    required = ("@{tycho.testArgLine}", "-Xcheck:jni",
                "-Dorg.eclipse.swt.internal.gtk.disablePrinting", "${os-jvm-flags}")
    for flag in required:
        if arguments.count(flag) != 1:
            raise AssertionError("Missing or duplicate test JVM flag: " + flag)
    if plugins["tycho-surefire-plugin"].findtext("m:configuration/m:skipTests", namespaces=NS) != "true":
        raise AssertionError("Do not silently create a second test provider")
    if not re.search(r"(?m)^\s+-DskipNativeTests=false\s*$", workflow_body):
        raise AssertionError("The hosted build must select native tests")
    for extension in ("xml", "txt"):
        path = "${{ github.workspace }}/**/target/surefire-reports/*." + extension
        if not any(line.strip() == path for line in workflow_body.splitlines()):
            raise AssertionError("Missing Surefire diagnostic artifact: " + extension)


class JniWorkflowContract(unittest.TestCase):
    def setUp(self):
        self.pom = (ROOT / "tests/org.eclipse.swt.tests/pom.xml").read_text(encoding="utf-8")
        self.workflow = (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8")

    def test_actual_source_preserves_jni_instrumentation_and_platform_flags(self):
        require_jni_test_contract(self.pom, self.workflow)

    def test_each_required_argument_cannot_be_removed(self):
        for flag in ("@{tycho.testArgLine}", "-Xcheck:jni",
                     "-Dorg.eclipse.swt.internal.gtk.disablePrinting", "${os-jvm-flags}"):
            with self.subTest(flag=flag), self.assertRaisesRegex(AssertionError, "JVM flag"):
                require_jni_test_contract(self.pom.replace(flag, "", 1), self.workflow)

    def test_duplicate_jni_argument_is_rejected(self):
        with self.assertRaisesRegex(AssertionError, "JVM flag"):
            require_jni_test_contract(self.pom.replace("-Xcheck:jni", "-Xcheck:jni -Xcheck:jni", 1), self.workflow)

    def test_wrong_execution_phase_is_rejected(self):
        with self.assertRaisesRegex(AssertionError, "integration-test JVM"):
            require_jni_test_contract(self.pom.replace("<phase>integration-test</phase>", "<phase>package</phase>", 1), self.workflow)

    def test_disabled_native_tests_are_rejected(self):
        with self.assertRaisesRegex(AssertionError, "native tests"):
            require_jni_test_contract(self.pom, self.workflow.replace("-DskipNativeTests=false", "-DskipNativeTests=true"))

    def test_missing_diagnostic_artifacts_are_rejected(self):
        for extension in ("xml", "txt"):
            missing = "${{ github.workspace }}/**/target/surefire-reports/*." + extension
            with self.subTest(extension=extension), self.assertRaisesRegex(AssertionError, "artifact"):
                require_jni_test_contract(self.pom, self.workflow.replace(missing, "omitted"))


if __name__ == "__main__":
    unittest.main()
