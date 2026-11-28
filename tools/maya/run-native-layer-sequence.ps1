# SPDX-License-Identifier: EPL-2.0
# Preserve exact Maya native three-test readback evidence on a Windows SWT rig.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $Runner,
    [Parameter(Mandatory = $true)][string] $Tree,
    [string] $ExtraClasspath = $env:EXTRA_CP,
    [string] $OutRoot = 'maya-native-sequence',
    [ValidateRange(1, 20)][int] $Iterations = 3,
    [string] $Bash = 'bash'
)
$ErrorActionPreference = 'Stop'
$testClass = 'org.eclipse.swt.tests.junit.maya.Test_org_eclipse_swt_maya_NativeLayers'
$original = @(
    "$testClass#nativeImageLayersChangeWithoutRepaintingTheViewport",
    "$testClass#aPanAndZoomPreserveNativeOrPaintedLayerPixels"
)
$pinned = "$testClass#aPinnedNativeHeaderStaysWhileTheRowsPanUnderIt"
$cases = @(
    @{ Name = 'original-two'; Methods = $original; Expected = 2; Class = $false },
    @{ Name = 'pinned-only'; Methods = @($pinned); Expected = 1; Class = $false },
    @{ Name = 'combined-three'; Methods = @($original[0], $original[1], $pinned); Expected = 3; Class = $false },
    @{ Name = 'whole-class'; Methods = @(); Expected = 0; Class = $true }
)
$priorExtra = $env:EXTRA_CP
$priorOut = $env:RIG_OUT
$failures = [System.Collections.Generic.List[string]]::new()
try {
    $env:EXTRA_CP = $ExtraClasspath
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetFullPath($OutRoot)) | Out-Null
    for ($iteration = 1; $iteration -le $Iterations; $iteration++) {
        foreach ($case in $cases) {
            $runName = '{0:D2}-{1}' -f $iteration, $case.Name
            $out = Join-Path $OutRoot $runName
            [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetFullPath($out)) | Out-Null
            $env:RIG_OUT = (Resolve-Path -LiteralPath $out).Path
            $argsForRig = @($Tree)
            if ($case.Class) {
                $argsForRig += '--select-class'
                $argsForRig += $testClass
            } else {
                foreach ($method in $case.Methods) {
                    $argsForRig += '--select-method'
                    $argsForRig += $method
                }
            }
            Write-Host "MAYA_SEQUENCE $runName"
            $output = (& $Bash $Runner @argsForRig 2>&1 | Out-String)
            $exitCode = $LASTEXITCODE
            Set-Content -LiteralPath (Join-Path $out 'runner.stdout.log') -Encoding UTF8 -Value $output
            $junitPath = Join-Path $out 'junit.log'
            $junit = if (Test-Path -LiteralPath $junitPath) {
                Get-Content -LiteralPath $junitPath -Raw
            } else { '' }
            $report = $output + [Environment]::NewLine + $junit
            $ok = $exitCode -eq 0 -and $junit.Length -gt 0 -and
                  $report -match '\[\s*0 tests failed\s*\]' -and
                  $report -match '\[\s*0 tests aborted\s*\]' -and
                  $report -notmatch 'jni warnings:\s*[1-9][0-9]*'
            if ($case.Expected -gt 0) {
                $pattern = '\[\s*' + $case.Expected + ' tests found\s*\]'
                $ok = $ok -and [regex]::IsMatch($report, $pattern)
            } else {
                $ok = $ok -and $report -match '\[\s*[1-9][0-9]* tests found\s*\]'
            }
            if ($ok) {
                Write-Host "MAYA_SEQUENCE PASS $runName"
            } else {
                $failures.Add($runName + ': exit=' + $exitCode +
                    '; inspect runner.stdout.log/junit.log for MAYA_PIXEL_* and JNI diagnostics')
                Write-Warning "MAYA_SEQUENCE FAIL $runName (logs retained)"
            }
        }
    }
} finally {
    $env:EXTRA_CP = $priorExtra
    $env:RIG_OUT = $priorOut
}
if ($failures.Count -ne 0) {
    throw ('Maya native sequence matrix failed: ' + ($failures -join '; '))
}
Write-Host 'MAYA_SEQUENCE ALL PASS — check the JUnit log for actual execution order'
