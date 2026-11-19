"""Package the scoped readiness change against the preserved dirty Opcje v2 baseline.

No Git index mutation, cleanup, installation, research session or model execution.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app/build/android_ux_readiness_20261115_v1"
AUDIT = OUT / "audit"
REPORT = "docs/android_ux_readiness_20261115_v1.md"
JAVA = "app/src/main/java/com/example/alpr_v1/"
TEST = "app/src/test/java/com/example/alpr_v1/"
ALLOWED = {
    JAVA + p for p in (
        "MainActivity.java", "SettingsActivity.java", "autotune/AutoTuneManager.java",
        "experiment/ConfigurationRequirements.java", "experiment/ResearchExecutionConfig.java",
        "experiment/ResearchStageExecutionConfig.java", "experiment/ResearchVariantSelection.java",
        "inference/NcnnBackend.java", "inference/RuntimeBackendFactory.java",
        "model/ModelConfigurationRequirements.java", "pipeline/MobileAlprEngine.java",
        "setup/TvSetupCalibration.java", "ui/ModelStatusFormatter.java",
        "ui/OptionsSectionsController.java", "ui/ResearchConfigurationText.java", "ui/TvSetupController.java",
    )
} | {
    "app/src/main/res/layout/activity_settings.xml", "app/src/main/res/values/f6_strings.xml",
    "app/src/main/res/values/options_layout_strings.xml", "app/src/main/res/values/readiness_strings.xml",
    TEST + "ui/F6ConfigurationUiTest.java", TEST + "ui/ReadinessTestModels.java",
    TEST + "ui/ReadinessUiTest.java", TEST + "ui/readiness/ReadinessEvaluatorTest.java",
    TEST + "autotune/AutoTunePreferenceContractTest.java", REPORT,
    "docs/android_ux_readiness_sources_20261115.md", "tools/verify_android_readiness.ps1",
    "tools/package_android_readiness.py",
}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def read_log(path: Path) -> str:
    data = path.read_bytes()
    return data.decode("utf-16" if data.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8-sig")


def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def git(*args: str, cwd=ROOT) -> subprocess.CompletedProcess:
    environment = os.environ.copy()
    if Path(cwd) != ROOT:
        for key in ("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE"):
            environment.pop(key, None)
    return subprocess.run(["git", *args], cwd=cwd, capture_output=True, env=environment)


def allowed(rel: str) -> bool:
    return rel in ALLOWED or rel.startswith(JAVA + "ui/readiness/") and rel.endswith(".java")


def audit(run: str):
    base = read_json(AUDIT / "baseline.json")
    changed, unrelated, protected_errors = [], [], []
    current = git("ls-files", "-z", "--cached", "--others", "--exclude-standard").stdout.decode().split("\0")
    paths = set(p for p in current if p) | set(base["files"])
    for rel in sorted(paths):
        path = ROOT / rel
        previous = base["files"].get(rel)
        sha = digest(path) if path.is_file() else None
        if sha != (previous or {}).get("sha256"):
            row = {"path": rel, "before": (previous or {}).get("sha256"), "after": sha}
            (changed if allowed(rel) else unrelated).append(row)
    for rel, previous in base["protected"].items():
        path = ROOT / rel
        if not path.is_file() or digest(path) != previous["sha256"] or path.stat().st_mtime_ns != previous["mtime_ns"]:
            protected_errors.append(rel)
    run_dir = OUT / run
    tested = read_json(run_dir / "source-sha256.json")
    source_now = {p.relative_to(ROOT).as_posix(): digest(p) for p in (ROOT / "app/src").rglob("*") if p.is_file()}
    source_errors = sorted(rel for rel in set(tested) | set(source_now) if tested.get(rel) != source_now.get(rel))
    head = git("rev-parse", "HEAD").stdout.decode().strip()
    staged = git("diff", "--cached", "--name-only").stdout.decode().strip()
    result = {
        "baseline_head": base["head"], "current_head": head, "staged_paths": staged,
        "baseline_files": len(base["files"]), "changed": changed, "unrelated_changes": unrelated,
        "protected_files": len(base["protected"]), "protected_errors": protected_errors,
        "tested_source_files": len(tested), "tested_source_errors": source_errors,
    }
    before = zipfile.ZipFile(AUDIT / "baseline-sources.zip")
    source_checks = {}
    for name in ("experiment/ResearchExecutionConfig.java", "experiment/ResearchStageExecutionConfig.java"):
        rel = JAVA + name
        def serializer(text):
            match = re.search(r"public JSONObject toJson\([^)]*\)[^{]*\{", text)
            assert match, rel
            start, level = match.start(), 1
            for i in range(match.end(), len(text)):
                if text[i] == "{": level += 1
                elif text[i] == "}": level -= 1
                if level == 0: return text[start:i + 1].replace("\r\n", "\n")
            raise AssertionError(rel)
        source_checks[rel] = serializer(before.read(rel).decode("utf-8-sig")) == serializer((ROOT / rel).read_text(encoding="utf-8-sig"))
    rel = "app/src/main/res/layout/activity_settings.xml"
    def ids(data): return set(re.findall(rb'android:id="@\+id/([^"]+)"', data))
    result["removed_settings_ids"] = sorted(x.decode() for x in ids(before.read(rel)) - ids((ROOT / rel).read_bytes()))
    result["main_layout_unchanged"] = before.read("app/src/main/res/layout/activity_main.xml") == (ROOT / "app/src/main/res/layout/activity_main.xml").read_bytes()
    result["serializers_unchanged"] = source_checks
    write_json(AUDIT / "source-integrity.json", result)
    assert not unrelated, unrelated
    assert not protected_errors, protected_errors
    assert not source_errors, source_errors
    assert head == base["head"] and not staged
    assert not result["removed_settings_ids"] and result["main_layout_unchanged"] and all(source_checks.values())
    return result


def test_summary(run: str):
    directory = OUT / run
    assert int((directory / "exit-code.txt").read_text(encoding="utf-8-sig").strip()) == 0
    assert "BUILD SUCCESSFUL" in read_log(directory / "gradle.log")
    suites = []
    for path in sorted((directory / "test-results").glob("TEST-*.xml")):
        root = ET.parse(path).getroot()
        suites.append({"name": root.attrib["name"], **{key: int(root.attrib[key]) for key in ("tests", "failures", "errors", "skipped")}})
    result = {"run": run, "suites": suites, **{key: sum(s[key] for s in suites) for key in ("tests", "failures", "errors", "skipped")}}
    assert result["tests"] >= 77 and result["failures"] == result["errors"] == result["skipped"] == 0
    expected = {"F6ConfigurationUiTest": 6, "OptionsMotionUiTest": 10, "MainUiSessionLabelsTest": 9, "F6PostStopSessionCreationTest": 15, "TvSetupUiTest": 10}
    for name, count in expected.items():
        assert next(s["tests"] for s in suites if s["name"].endswith("." + name)) == count, name
    write_json(OUT / "tests-summary.json", result)
    a, b = AUDIT / "protocol-before-readiness.json", AUDIT / "protocol-after-readiness.json"
    assert read_json(a) == read_json(b)
    write_json(AUDIT / "protocol-comparison.json", {"synthetic": True, "equal": True, "excluded_fields": [], "before_sha256": digest(a), "after_sha256": digest(b)})
    return result


def package(run: str):
    integrity = audit(run)
    tests = test_summary(run)
    assert (ROOT / REPORT).is_file()
    files = [row["path"] for row in integrity["changed"]]
    with zipfile.ZipFile(OUT / "readiness-sources.zip", "w", zipfile.ZIP_DEFLATED) as archive:
        for rel in files: archive.write(ROOT / rel, rel)
    work = Path(tempfile.mkdtemp(prefix="patch-roundtrip-", dir=AUDIT))
    before, after = work / "before", work / "after"
    before.mkdir(); after.mkdir()
    with zipfile.ZipFile(AUDIT / "baseline-sources.zip") as baseline:
        for rel in files:
            destination = after / rel; destination.parent.mkdir(parents=True, exist_ok=True);shutil.copy2(ROOT / rel, destination)
            if rel in baseline.namelist():
                destination = before / rel; destination.parent.mkdir(parents=True, exist_ok=True); destination.write_bytes(baseline.read(rel))
    result = git("-c", "core.autocrlf=false", "diff", "--no-index", "--binary", "--no-renames", "before", "after", cwd=work)
    assert result.returncode in (0, 1), result.stderr
    lines = []
    for line in result.stdout.splitlines(keepends=True):
        if line.startswith((b"diff --git ", b"--- ", b"+++ ")):
            line = line.replace(b"a/before/", b"a/").replace(b"b/before/", b"b/").replace(b"a/after/", b"a/").replace(b"b/after/", b"b/")
        lines.append(line)
    patch = b"".join(lines)
    patch_path = OUT / "readiness.patch";patch_path.write_bytes(patch)
    check = work / "applied"; shutil.copytree(before, check)
    template = work / "empty-template"; template.mkdir()
    result = git("init", "--quiet", "--template=" + str(template), str(check), cwd=work)
    assert result.returncode == 0 and (check / ".git").is_dir(), result.stderr
    assert Path(git("rev-parse", "--show-toplevel", cwd=check).stdout.decode().strip()).resolve() == check.resolve()
    result = git("-c", "core.autocrlf=false", "-c", "core.whitespace=cr-at-eol", "apply", str(patch_path), cwd=check)
    assert result.returncode == 0, result.stderr.decode(errors="replace")
    assert all(digest(check / rel) == digest(ROOT / rel) for rel in files)
    write_json(AUDIT / "patch-roundtrip.json", {"files": len(files), "byte_identical": True, "baseline": "dirty Opcje v2 working tree, not Git HEAD"})
    apk = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    installed = OUT / "alpr-ux-readiness-v1-debug.apk"; shutil.copy2(apk, installed)
    shutil.copy2(ROOT / REPORT, OUT / "report.md")
    device = read_json(OUT / "device/acceptance.json") if (OUT / "device/acceptance.json").exists() else {"status": "NOT_VERIFIED"}
    ready = {"packaging_complete": True, "host": "PASS_SCOPED", "tests": tests["tests"], "device": device,
             "apk": installed.name, "apk_sha256": digest(installed), "source_files_in_patch": len(files),
             "experiment_176": "NOT_RUN", "git_commit": integrity["current_head"], "git_dirty": True}
    write_json(OUT / "READY.json", ready)
    sums = []
    for path in sorted(OUT.rglob("*")):
        if path.is_file() and path.name != "SHA256SUMS.txt": sums.append(f"{digest(path)}  {path.relative_to(OUT).as_posix()}")
    (OUT / "SHA256SUMS.txt").write_text("\n".join(sums) + "\n", encoding="utf-8")
    print(json.dumps({"apk": str(installed), "sha256": ready["apk_sha256"], "tests": tests["tests"], "files": len(files), "checksums": len(sums)}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser();parser.add_argument("command", choices=("audit", "package"));parser.add_argument("--run", required=True)
    args = parser.parse_args()
    if args.command == "audit":
        result = audit(args.run);tests = test_summary(args.run)
        print(json.dumps({"changed_files": len(result["changed"]), "protected_files": result["protected_files"], "tests": tests["tests"]}))
    else: package(args.run)
