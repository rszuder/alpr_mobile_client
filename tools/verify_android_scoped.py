"""Isolated, append-only validation for normal CPU/readiness (A) and identity (B)."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DELIVERIES = {
    "a": "android_normal_cpu_readiness_copy_20261116_v1",
    "b": "android_dynamic_vehicle_identity_20261116_v1",
}


def run():
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=DELIVERIES)
    parser.add_argument("--run", required=True)
    parser.add_argument("--test", action="append", default=[])
    parser.add_argument("--baseline", action="store_true")
    args = parser.parse_args()
    out = ROOT / "app/build" / DELIVERIES[args.phase]
    directory = out / args.run
    directory.mkdir(parents=True, exist_ok=False)
    started_ns = time.time_ns()
    previous = json.loads((ROOT / "app/build/android_options_guidance_20261116_v1/host-final/gradle-arguments.json").read_text(encoding="utf-8-sig"))
    tests = args.test or [previous[i + 1] for i, value in enumerate(previous) if value == "--tests"]
    if not args.test and not args.baseline:
        tests += ["com.example.alpr_v1.autotune.NormalExecutionSettingsTest", "com.example.alpr_v1.ui.NormalCpuReadinessUiTest"]
        if args.phase == "b":
            tests += ["com.example.alpr_v1.tracking.VehicleTrackManagerTest", "com.example.alpr_v1.tracking.VehicleTrackingCoordinatorTest",
                      "com.example.alpr_v1.ui.DynamicVehicleIdentityUiTest", "com.example.alpr_v1.pipeline.DynamicIdentityBoundaryTest",
                      "com.example.alpr_v1.continuity.SceneTransitionCoordinatorTest", "com.example.alpr_v1.pipeline.SoftReacquireReportTest",
                      "com.example.alpr_v1.capture.RecognitionHistoryStoreTest", "com.example.alpr_v1.pipeline.VehicleOverlayIdentityTest",
                      "com.example.alpr_v1.acquisition.StaticSceneCycleTest", "com.example.alpr_v1.continuity.StaticSceneWatcherTest",
                      "com.example.alpr_v1.pipeline.GenerationStampedResultsTest", "com.example.alpr_v1.acquisition.ScanAcquisitionControllerTest",
                      "com.example.alpr_v1.pipeline.TrackedVehicleRoiSelectorTest"]
    hashes = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
              for p in (ROOT / "app/src").rglob("*") if p.is_file()}
    (directory / "source-sha256.json").write_text(json.dumps(hashes, indent=2), encoding="utf-8")
    init = directory / "validation.init.gradle"
    temporary = directory / "tmp"
    temporary.mkdir()
    init.write_text("allprojects { tasks.withType(Test).configureEach { maxHeapSize = '2048m'; maxParallelForks = 1; forkEvery = 2; "
                    + "systemProperty 'java.io.tmpdir', '" + temporary.as_posix() + "'; "
                    + "reports.junitXml.outputLocation = file('" + (directory / 'xml').as_posix() + "'); "
                    + "binaryResultsDirectory = file('" + (directory / 'binary').as_posix() + "') } }\n", encoding="ascii")
    env = os.environ.copy()
    env["JAVA_HOME"] = r"C:\Program Files\Android\Android Studio\jbr"
    for index, name in enumerate(("READINESS_UI_ARTIFACTS", "READINESS_AUDIT_ARTIFACTS", "F6_OPTIONS_UI_ARTIFACTS", "MAIN_VIEWFINDER_UI_ARTIFACTS", "OPTIONS_GUIDANCE_UI_ARTIFACTS", "NORMAL_CPU_UI_ARTIFACTS", "IDENTITY_UI_ARTIFACTS")):
        env[name] = str(directory / "ui" / str(index))
    arguments = ["--offline", "-I", str(init), ":app:testDebugUnitTest"]
    for test in dict.fromkeys(tests):
        arguments += ["--tests", test]
    arguments += [":app:assembleDebug", ":app:compileDebugAndroidTestJavaWithJavac", "--console=plain"]
    (directory / "gradle-arguments.json").write_text(json.dumps(arguments, indent=2), encoding="utf-8")
    with (directory / "gradle.log").open("wb") as log:
        process = subprocess.run([str(ROOT / "gradlew.bat"), *arguments], cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
    (directory / "exit-code.txt").write_text(str(process.returncode), encoding="ascii")
    results = directory / "xml"
    if not list(results.glob("TEST-*.xml")):
        # AGP may restore its variant output location after the init hook.
        # Never accept the stale XML left by a failed earlier Gradle invocation.
        results = ROOT / "app/build/test-results/testDebugUnitTest"
    if results.is_dir():
        destination = directory / "test-results"
        destination.mkdir()
        for source in results.glob("TEST-*.xml"):
            if source.stat().st_mtime_ns >= started_ns:
                shutil.copy2(source, destination / source.name)
    suites = []
    for p in (directory / "test-results").glob("TEST-*.xml"):
        suite = ET.parse(p).getroot()
        suites.append({"name": suite.attrib["name"], **{k: int(suite.attrib[k]) for k in ("tests", "failures", "errors", "skipped")}})
    summary = {"run": args.run, "exit_code": process.returncode, "suites": suites,
               **{k: sum(s[k] for s in suites) for k in ("tests", "failures", "errors", "skipped")}}
    (directory / "tests-summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps({k: v for k, v in summary.items() if k != "suites"}))
    if process.returncode == 0 and not summary["tests"]:
        raise SystemExit("No fresh test results were collected")
    raise SystemExit(process.returncode)


if __name__ == "__main__":
    run()
