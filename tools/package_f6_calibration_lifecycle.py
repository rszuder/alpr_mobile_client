"""Freeze the tested Android delivery. No Gradle, ADB, source edits or historical-data writes."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import zipfile
from datetime import datetime, timezone


def sha(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--delivery", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    base = args.delivery.resolve()
    if not base.is_relative_to((root / "app/build").resolve()):
        raise SystemExit("Delivery must be within this Android checkout's app/build")
    if (base / "READY.json").exists() or (base / "desktop_package").exists():
        raise SystemExit("Delivery already frozen/being packaged; choose a new version")
    validation = json.loads((base / "validation.json").read_text(encoding="utf-8"))
    if validation["android_host_tests"] != "PASS" or not validation["protected_files_unchanged"]:
        raise SystemExit("Test/protection gate failed")
    report = root / "docs/f6_android_calibration_lifecycle_export_fix_20261113_v1.md"
    if "PENDING_" in report.read_text(encoding="utf-8"):
        raise SystemExit("Report still has pending sections")
    apk = base / "alpr-f6-calibration-lifecycle-export-debug.apk"
    if sha(apk) != validation["apk_sha256"]:
        raise SystemExit("APK identity changed after verification")
    package = base / "desktop_package"
    package.mkdir()
    for folder in (base / "fixtures").iterdir():
        if folder.is_dir():
            shutil.copytree(folder, package / "fixtures" / folder.name)
    shutil.copy2(report, package / report.name)
    shutil.copy2(root / "docs/f6_final180_android_contract_20261112.md", package / "original_android_contract_20261112.md")
    shutil.copy2(base / "validation.json", package / "validation.json")
    shutil.copy2(base / "fixtures-sha256.json", package / "fixtures-sha256.json")
    shutil.copy2(base / "producer_sha256.json", package / "producer_sha256.json")
    shutil.copy2(base / "audit/final-verified-lifecycle-state-trace.jsonl", package / "synthetic-lifecycle-state-trace.jsonl")
    shutil.copy2(base / "audit/lifecycle-state-table.csv", package / "synthetic-lifecycle-state-table.csv")
    shutil.copy2(base / "scope.patch", package / "scope.patch")
    changed = json.loads((base / "changed-source-files.json").read_text(encoding="utf-8"))
    for relative in changed:
        path = root / relative
        if path.suffix in (".java", ".xml", ".py"):
            target = package / "producer_source" / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, target)
    readme = """F6 Android lifecycle/export fix -- host delivery, not A12/TV acceptance.

fixtures/synthetic: new SYNTHETIC/TEST_ONLY exports from ResearchSessionStore,
the existing engine with synthetic backends, and the production archive writer.
lifecycle-session-1/2-zero-mt: real UI preference -> frozen config -> MT zero -> STOP
-> archive, two consecutive sessions without recalibration.
lifecycle-stop-before-frame-EMPTY: PARTIAL with zero frames and zero MT, not an
executed detector returning zero. Other fixtures cover errors/cancellation/missing
images/queue loss/multiple FINAL and MZ. Never use these as recognition metrics.

fixtures/host_recovered_device_copies: authentic old A12 evidence re-exported by
the current producer on secured host copies. These remain PARTIAL and retain the
original APK provenance; they are not recordings from the new APK. The adjacent
provenance manifests preserve hashes of the original failed session files.

Archive schemas and fields are unchanged. execution.variant is the operator's
label (including R0_FINAL). Actual R0 is execution.roi_budget_policy=r0_full_frame
and stages.mp.enabled=false. The preference-only approval context is not exported.
Refer to both contracts and validation.json. Desktop native-reader acceptance of
this new package is pending. No hardware GO is implied.

The accepted MP v1 delivery and >4096-frame/>32MiB archive were validated in place,
without regeneration or modification. Phone raw copies/APK/logs remain in the
local audit directory and are intentionally absent from this small package.
"""
    (package / "README.txt").write_text(readme, encoding="utf-8")
    entries = {str(p.relative_to(package)).replace("\\", "/"): sha(p) for p in sorted(package.rglob("*")) if p.is_file()}
    (package / "SHA256SUMS.txt").write_text("".join(f"{digest}  {name}\n" for name, digest in entries.items()), encoding="utf-8")
    archive = base / "desktop_package.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as z:
        for p in sorted(package.rglob("*")):
            if p.is_file():
                z.write(p, str(p.relative_to(package)).replace("\\", "/"))
    with zipfile.ZipFile(archive) as z:
        if z.testzip() is not None:
            raise SystemExit("ZIP CRC failure")
        for name, digest in entries.items():
            if hashlib.sha256(z.read(name)).hexdigest() != digest:
                raise SystemExit("Packaged bytes differ: " + name)
    all_files = {str(p.relative_to(base)).replace("\\", "/"): sha(p) for p in sorted(base.rglob("*")) if p.is_file() and p.name != "SHA256SUMS.txt"}
    (base / "SHA256SUMS.txt").write_text("".join(f"{digest}  {name}\n" for name, digest in all_files.items()), encoding="utf-8")
    ready = {"status": "HOST_VERIFIED_OPERATOR_UPDATE_REQUIRED", "completed_at_utc": datetime.now(timezone.utc).isoformat(),
             "apk_sha256": sha(apk), "desktop_package_sha256": sha(archive), "report_sha256": sha(report),
             "generator_sha256": sha(Path(__file__).resolve()), "validation_sha256": sha(base / "validation.json"),
             "ui_a12_lifecycle": "NOT_VERIFIED", "a12_tv_technical": "NOT_VERIFIED", "experiment_start": "NO_GO_PENDING_ACCEPTANCE"}
    (base / "READY.json").write_text(json.dumps(ready, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(ready, indent=2))


if __name__ == "__main__":
    main()
