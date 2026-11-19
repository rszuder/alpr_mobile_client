"""Publish one immutable synthetic Android delivery after producer-side contract tests.

No APK build, ZIP repair, Desktop import, device operation, or modification of the prior delivery.
Run from the Android checkout. Generation uses the existing JVM producer tests first; this
script requires their SHA-bound validation receipts and a separately captured final test run.
"""
from pathlib import Path
import datetime
import hashlib
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = "f6_mp_schema_compatibility_fix_20261112_v1"
STAGE = ROOT / "app/build" / (VERSION + ".staging")
FINAL = ROOT / "app/build" / VERSION
PUBLISH = ROOT / "app/build" / (VERSION + ".delivery-staging")
REPORT = ROOT / "docs/f6_android_mp_schema_compatibility_fix_20261112_v1.md"
OLD_BAD_SHA = "0323d3e5d1f79fe32e99c193a6ce71ee558b31ac0d0ed8aca57e0b7c48212f5f"
SOURCES = [
    "app/src/test/java/com/example/alpr_v1/experiment/Final180EvidenceTest.java",
    "app/src/test/java/com/example/alpr_v1/experiment/F6ProducerContractVerifier.java",
    "app/src/test/java/com/example/alpr_v1/experiment/F6ProducerContractVerifierTest.java",
    "app/src/test/java/com/example/alpr_v1/pipeline/TvBboxPipelineTest.java",
    "tools/package_f6_mp_schema_fix.py",
]
PRODUCERS = [
    "app/src/main/java/com/example/alpr_v1/pipeline/MobileAlprEngine.java",
    "app/src/main/java/com/example/alpr_v1/experiment/F5aEvidence.java",
    "app/src/main/java/com/example/alpr_v1/experiment/Final180Evidence.java",
    "app/src/main/java/com/example/alpr_v1/experiment/Final180ArchiveValidator.java",
    "app/src/main/java/com/example/alpr_v1/experiment/ExternalJsonSort.java",
    "app/src/main/java/com/example/alpr_v1/experiment/ResearchSessionStore.java",
    "app/src/main/java/com/example/alpr_v1/experiment/TvBboxEvidence.java",
    "app/src/main/java/com/example/alpr_v1/metrics/ResearchArchive.java",
    "app/src/test/java/com/example/alpr_v1/experiment/TvBboxProducerFixture.java",
]
CASES = {
    "r0-positive": "PASS", "r0-no-detection": "PASS", "r0-two": "PASS", "r0-no-read": "PASS",
    "r0-no-mz": "PASS", "r0-extra-final": "PASS", "r0-duplicate-final": "PASS",
    "r0-mt-error": "PARTIAL", "r0-mz-error": "PARTIAL", "r0-mt-fatal": "PARTIAL",
    "r0-mz-fatal": "PARTIAL", "r0-cancelled": "PARTIAL", "r0-delayed-old-frame": "PARTIAL",
    "not-run": "PASS", "not-run-source-unavailable": "PARTIAL", "not-run-image-write-error": "PARTIAL",
    "queue-full": "PARTIAL", "interrupted-start": "PARTIAL", "interrupted-broken-tail": "PARTIAL",
    "long-session-4100-frames": "PASS",
}
SUITES = {
    "com.example.alpr_v1.experiment.F6ProducerContractVerifierTest",
    "com.example.alpr_v1.experiment.Final180EvidenceTest",
    "com.example.alpr_v1.experiment.F5aEvidenceTest",
    "com.example.alpr_v1.experiment.TvBboxEvidenceTest",
    "com.example.alpr_v1.experiment.ResearchExecutionEvidenceContractTest",
    "com.example.alpr_v1.pipeline.TvBboxPipelineTest",
}


def sha(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def copy(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)
    assert sha(source) == sha(target)


def archive_integrity(path):
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None, path
        names = archive.namelist()
        assert len(names) == len(set(names)), path
        manifest = json.loads(archive.read("manifest.json"))
        assert set(manifest["entry_sha256"]) == set(names) - {"manifest.json"}, path
        for entry, expected in manifest["entry_sha256"].items():
            digest = hashlib.sha256()
            with archive.open(entry) as stream:
                for block in iter(lambda: stream.read(65536), b""):
                    digest.update(block)
            assert digest.hexdigest() == expected, (path, entry)
        return json.loads(archive.read("session.json"))


def sums(directory):
    # The manifest excludes itself; outer SHA256SUMS also covers the ZIP and READY marker.
    files = sorted(p for p in directory.rglob("*") if p.is_file() and p != directory / "SHA256SUMS.txt")
    (directory / "SHA256SUMS.txt").write_text(
        "".join(f"{sha(p)}  {p.relative_to(directory).as_posix()}\n" for p in files), encoding="utf-8")


def main():
    assert not FINAL.exists() and not PUBLISH.exists(), "Immutable version already exists; select a new version"
    baseline = json.loads((STAGE / "audit/before.json").read_text(encoding="utf-8"))
    assert subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip() == baseline["head"]
    assert subprocess.check_output(["git", "branch", "--show-current"], cwd=ROOT, text=True).strip() == baseline["branch"]
    allowed = set(SOURCES) | {REPORT.relative_to(ROOT).as_posix()}
    changed = [name for name, expected in baseline["files"].items() if not (ROOT / name).is_file() or sha(ROOT / name) != expected]
    assert set(changed) <= allowed, changed
    for group in ("previous_delivery", "apk", "desktop_read_only"):
        for name, expected in baseline[group].items():
            path = Path(name) if Path(name).is_absolute() else ROOT / name
            assert path.is_file() and sha(path) == expected, ("Protected file changed", name)
    assert REPORT.is_file()
    test_dir = STAGE / "audit/final_run"
    checks = []
    for path in sorted(test_dir.glob("TEST-*.xml")):
        suite = ET.parse(path).getroot()
        checks.append({"suite": suite.attrib["name"], **{key: int(suite.attrib.get(key, 0)) for key in ("tests", "failures", "errors", "skipped")}, "xml_sha256": sha(path)})
    assert {s["suite"] for s in checks} == SUITES, checks
    totals = {key: sum(s[key] for s in checks) for key in ("tests", "failures", "errors", "skipped")}
    assert totals["tests"] > 0 and totals["failures"] == totals["errors"] == totals["skipped"] == 0, totals
    final_log = STAGE / "audit/final_run.log"
    assert "BUILD SUCCESSFUL" in final_log.read_text(encoding="utf-8-sig"), "Final Gradle run did not pass"
    receipts = [json.loads(line) for line in (test_dir / "contract-results.jsonl").read_text(encoding="utf-8").splitlines() if line]
    payload = PUBLISH / "desktop_package"
    index = []
    for name, status in CASES.items():
        source = STAGE / "generated/f6" / (name + ".alprsession")
        digest = sha(source)
        matching = [r for r in receipts if r["sha256"] == digest and r["status"] == status and r["scope"] == "native_f6_graph_and_scene_mp_contract"]
        assert matching, ("No positive producer contract receipt", name, digest)
        session = archive_integrity(source)
        assert session["capture_profile"] == "f6_final180_v1"
        if name == "long-session-4100-frames":
            assert matching[-1]["mp_not_run"] == 4100 and matching[-1]["mp_executed"] == matching[-1]["mp_failed"] == 0
            assert session["tv_bbox_input_evidence"]["frames"] == 4100
            assert session["f6_evidence"]["events_persisted"] == 12300 and session["f6_evidence"]["mt_started_count"] == 0
            with zipfile.ZipFile(source) as archive:
                scene_bytes = archive.getinfo("samples/scene_evidence.jsonl").file_size
                assert scene_bytes >= 34_608_299 and scene_bytes > 32 * 1024 * 1024
        target = payload / "fixtures" / source.name
        copy(source, target)
        index.append({"file": target.relative_to(payload).as_posix(), "sha256": digest, "bytes": target.stat().st_size,
                      "expected_status": status, "producer_contract": "PASS", "synthetic": True,
                      "mp_not_run": matching[-1]["mp_not_run"], "mp_executed": matching[-1]["mp_executed"]})
    legacy = []
    for name in ("tv-r1", "tv-r2-two-roi"):
        source = STAGE / "generated/legacy" / (name + ".alprsession")
        matching = [r for r in receipts if r["sha256"] == sha(source) and r["status"] == "PASS_SCENE_MP"]
        assert matching and matching[-1]["mp_executed"] > 0, name
        archive_integrity(source)
        target = payload / "legacy_regression" / source.name
        copy(source, target)
        legacy.append({"file": target.relative_to(payload).as_posix(), "sha256": sha(target), "bytes": target.stat().st_size,
                       "profile": "F5A TV_BBOX_CROP", "synthetic": True, "mp_executed": matching[-1]["mp_executed"]})
    old = ROOT / "app/build/f6_final180_20261112/fixtures/long-session-4100-frames.alprsession"
    assert sha(old) == OLD_BAD_SHA
    copy(old, payload / "negative_evidence/previous-long-session-4100-frames.INVALID.alprsession")
    for name in SOURCES + PRODUCERS:
        copy(ROOT / name, payload / "sources" / name)
    source_hashes = {name: {"sha256": sha(ROOT / name), "role": "generator_or_delivery_gate" if name in SOURCES else "unchanged_producer"} for name in SOURCES + PRODUCERS}
    copy(REPORT, payload / "report.md")
    for path in sorted(test_dir.glob("*")):
        if path.is_file(): copy(path, payload / "test_results" / path.name)
    for name in ("before.json", "source_diagnosis.json", "initial-regression.log", "regression.log", "bounded-heap.gradle"):
        copy(STAGE / "audit" / name, payload / "audit" / name)
    copy(final_log, payload / "test_results/gradle.log")
    copy(ROOT / "docs/f6_final180_android_contract_20261112.md", payload / "provenance/previous_android_contract.md")
    write_json(payload / "source_sha256.json", source_hashes)
    write_json(payload / "fixture_index.json", {"contract": "F6_FINAL180_SPLIT_V1", "synthetic": True, "device_run": "NOT_RUN",
        "fixtures": index, "legacy_regression": legacy, "negative_evidence": {"file": "negative_evidence/previous-long-session-4100-frames.INVALID.alprsession",
        "sha256": OLD_BAD_SHA, "delivery_contract": "FAIL", "expected_error": "MP_OBSERVATION_FIELDS_MISSING", "import_as_valid_fixture": False}})
    verification = {"source_files_protected": len(baseline["files"]) - len(changed), "previous_delivery_files_unchanged": len(baseline["previous_delivery"]),
                    "desktop_files_unchanged": len(baseline["desktop_read_only"]), "changed_existing_files": changed,
                    "apk_code_changed": False, "apk_rebuilt": False, "apk_installed": False, "apk_sha256": baseline["apk"]}
    write_json(payload / "protected_files_audit.json", verification)
    validation = {"delivery": VERSION, "generated_at_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "head": baseline["head"], "branch": baseline["branch"], "cause": "synthetic_stress_generator_and_delivery_test_gate",
        "unit_tests_final_run_only": totals, "suites": checks, "primary_export_archives": len(index), "legacy_export_archives": len(legacy),
        "previous_invalid_archive": "preserved_negative_evidence", "host_contract": "PASS", "heap_limit_bytes": 512 * 1024 * 1024,
        "apk_code_changed": False, "new_apk_required": False, "device_run": "NOT_RUN", "desktop_acceptance": "NOT_VERIFIED",
        "a12_tv_markers_and_real_run": "NOT_VERIFIED", "android_timer": "disabled", "pc_nominal_show_seconds": 972.5}
    write_json(payload / "validation.json", validation)
    (payload / "README.md").write_text(
        "# F6 MP schema compatibility fix v1 — SYNTHETIC\n\n"
        "READY means packaged and validated on the Android host; Desktop acceptance = NOT_VERIFIED.\n"
        "No APK code change, rebuild or installation. DEVICE_RUN=NOT_RUN; A12/TV=NOT_VERIFIED.\n\n"
        "Import the 20 primary archives in fixtures/ using fixture_index.json (19 small + corrected long).\n"
        "PARTIAL means explicit missing/error/cancellation evidence, not an empty recognition result.\n"
        "legacy_regression/ contains two F5A MP EXECUTED engine fixtures, outside the F6 R0 profile.\n"
        "negative_evidence/ contains the exact OLD INVALID long archive; expect MP_OBSERVATION_FIELDS_MISSING.\n"
        "Do not count its expected rejection as valid delivery acceptance.\n\n"
        "All archives were generated through ResearchSessionStore/ResearchArchive; no completed ZIP was repaired.\n"
        "The stress scene contains 4100 real synthetic NOT_RUN observations and a root _synthetic_test extension,\n"
        "purpose=serialization_load_only, not_model_output=true. Its padding is not MP output.\n"
        "The synthetic 5400-second clock is not a heat or throughput test.\n"
        "Android stays without timer. PC's 176 x 5-second plan is a separate operator decision (972.5s nominal).\n\n"
        "Read report.md, validation.json, source_sha256.json and SHA256SUMS.txt.\n", encoding="utf-8")
    sums(payload)
    bundle = PUBLISH / "desktop_package.zip"
    with zipfile.ZipFile(bundle, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(payload.rglob("*")):
            if path.is_file(): archive.write(path, path.relative_to(payload).as_posix())
    with zipfile.ZipFile(bundle) as archive:
        assert archive.testzip() is None
        for path in payload.rglob("*"):
            if path.is_file():
                digest = hashlib.sha256(archive.read(path.relative_to(payload).as_posix())).hexdigest()
                assert digest == sha(path)
    copy(REPORT, PUBLISH / "report.md")
    write_json(PUBLISH / "delivery_sha256.json", {"desktop_package.zip": sha(bundle), "report.md": sha(REPORT),
        "generator": {name: sha(ROOT / name) for name in SOURCES}, "archives": {"desktop_package/" + entry["file"]: entry["sha256"] for entry in index + legacy}})
    (PUBLISH / "READY.json").write_text(json.dumps({"state": "READY_FOR_DESKTOP_ACCEPTANCE", "host_contract": "PASS",
        "desktop_acceptance": "NOT_VERIFIED", "package_sha256": sha(bundle), "report_sha256": sha(REPORT)}, indent=2) + "\n", encoding="utf-8")
    sums(PUBLISH)
    # Rename only this newly created, explicitly bounded version directory, after all tests/packing.
    assert PUBLISH.resolve().parent == FINAL.resolve().parent == (ROOT / "app/build").resolve()
    PUBLISH.rename(FINAL)
    print(json.dumps({"delivery": str(FINAL), "package_sha256": sha(FINAL / "desktop_package.zip"), "report_sha256": sha(REPORT), "tests": totals}, indent=2))


if __name__ == "__main__":
    main()
