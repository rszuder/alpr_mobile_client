param([string]$Run = 'host-final', [string[]]$TestFilters = @())

# Scoped host validation only. No ADB, installation, experiment, cleanup or Git mutation.
$ErrorActionPreference = 'Stop'
$repo = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $repo
$delivery = Join-Path $repo 'app/build/android_main_viewfinder_fix_20261115_v1'
$runDirectory = Join-Path $delivery $Run
if (Test-Path -LiteralPath $runDirectory) { throw "Run already exists: $runDirectory" }
New-Item -ItemType Directory -Path $runDirectory | Out-Null
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:READINESS_UI_ARTIFACTS = Join-Path $delivery 'host-ui'
$env:READINESS_AUDIT_ARTIFACTS = Join-Path $delivery 'audit'
$env:F6_OPTIONS_UI_ARTIFACTS = Join-Path $delivery 'host-ui/legacy'
$env:MAIN_VIEWFINDER_UI_ARTIFACTS = Join-Path $delivery 'host-ui/main'
$tests = @(
    'com.example.alpr_v1.ui.MainViewfinderInsetsTest',
    'com.example.alpr_v1.ui.CameraOperatorPresentationTest',
    'com.example.alpr_v1.ui.MainHeaderOperationUiTest',
    'com.example.alpr_v1.ui.CalibrationOperatorTextTest',
    'com.example.alpr_v1.ui.MainUiTimerStatusTest',
    'com.example.alpr_v1.ui.F5a26TimerUiTest',
    'com.example.alpr_v1.ui.F6ConfigurationUiTest',
    'com.example.alpr_v1.ui.OptionsMotionUiTest',
    'com.example.alpr_v1.ui.MainUiSessionLabelsTest',
    'com.example.alpr_v1.ui.F6PostStopSessionCreationTest',
    'com.example.alpr_v1.ui.TvSetupUiTest.realOverflowFocusAndTransientBarsDoNotResizePreviewOrRebindCamera',
    'com.example.alpr_v1.ui.TvSetupUiTest.keyboardKeepsNumericFieldAndLabelVisibleAndRestoresPreview*',
    'com.example.alpr_v1.ui.TvSetupUiTest.alreadyResizedWindowDoesNotSubtractTheKeyboardTwice',
    'com.example.alpr_v1.ui.TvSetupUiTest.visibleSystemBarsLeaveCornersAndPrimaryActionsUsableAtSmallAndLargeFonts',
    'com.example.alpr_v1.ui.TvSetupUiTest.photoCopyRestoreAndSingleCancelDecisionKeepBothApprovedRecords',
    'com.example.alpr_v1.ui.TvSetupUiTest.cornerChoiceDragAndGestureCancellationRetainSourcePoints',
    'com.example.alpr_v1.ui.TvSetupUiTest.calculatorRetainsImePriorityAfterLeavingCalibration*',
    'com.example.alpr_v1.ui.TvSetupUiTest.heldArrowStopsOnCornerStepFocusAndCancelWithoutMovingAnotherPoint',
    'com.example.alpr_v1.ui.ReadinessUiTest',
    'com.example.alpr_v1.ui.readiness.ReadinessEvaluatorTest',
    'com.example.alpr_v1.autotune.AutoTunePreferenceContractTest',
    'com.example.alpr_v1.autotune.AutoTuneSelectionTest',
    'com.example.alpr_v1.experiment.ResearchExecutionChoiceTest',
    'com.example.alpr_v1.experiment.ResearchExecutionEvidenceContractTest',
    'com.example.alpr_v1.model.ModelVariantContractTest',
    'com.example.alpr_v1.experiment.TvBboxAdmissionTest',
    'com.example.alpr_v1.experiment.Final180PhotoRoiApprovalTest',
    'com.example.alpr_v1.camera.CameraSetupControlTest',
    'com.example.alpr_v1.ui.ExperimentStartRequestTest',
    'com.example.alpr_v1.ui.StartPresentationConsistencyTest',
    'com.example.alpr_v1.inference.RuntimeExecutionEvidenceTest',
    'com.example.alpr_v1.pipeline.RuntimeContractFailureGateTest'
)
if ($TestFilters.Count -gt 0) { $tests = $TestFilters }
$sourceHashes = [ordered]@{}
Get-ChildItem -LiteralPath (Join-Path $repo 'app/src') -Recurse -File | Sort-Object FullName | ForEach-Object {
    $relative = $_.FullName.Substring($repo.Length + 1).Replace('\','/')
    $sourceHashes[$relative] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
}
$sourceHashes | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $runDirectory 'source-sha256.json') -Encoding UTF8
$initScript = Join-Path $delivery 'audit/host-validation.init.gradle'
@'
allprojects {
    tasks.withType(Test).configureEach {
        maxHeapSize = '2048m'
        maxParallelForks = 1
        forkEvery = 4
    }
}
'@ | Set-Content -LiteralPath $initScript -Encoding ASCII
$arguments = @('--offline', '-I', $initScript, ':app:testDebugUnitTest')
foreach ($test in $tests) { $arguments += @('--tests', $test) }
$arguments += @(':app:assembleDebug', ':app:compileDebugAndroidTestJavaWithJavac', '--console=plain')
$arguments | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runDirectory 'gradle-arguments.json') -Encoding UTF8
$ErrorActionPreference = 'Continue'
& (Join-Path $repo 'gradlew.bat') @arguments *> (Join-Path $runDirectory 'gradle.log')
$validationExit = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
Copy-Item -LiteralPath (Join-Path $repo 'app/build/test-results/testDebugUnitTest') -Destination (Join-Path $runDirectory 'test-results') -Recurse
Set-Content -LiteralPath (Join-Path $runDirectory 'exit-code.txt') -Value $validationExit -Encoding UTF8
Get-Content -LiteralPath (Join-Path $runDirectory 'gradle.log') -Tail 40
if ($validationExit -ne 0) { exit $validationExit }
