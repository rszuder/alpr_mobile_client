"""F5A/12A synthetic archive validation + guarded, read-only native Desktop compatibility.

No model execution, APK, ADB, historical-session changes or Desktop source changes.
Run after F5aEvidenceTest generated producer archives via F5A12A_FIXTURES.
"""
import argparse
import csv
import hashlib
import io
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import warnings
import zipfile

sys.dont_write_bytecode = True


def desktop_worker(source, artifacts, work):
    source, artifacts, work = map(lambda p: Path(p).resolve(), (source, artifacts, work))
    os.chdir(work)
    sys.path.insert(0, str(source))
    blocked = []

    def allowed(value):
        if isinstance(value, int):
            return True
        target = Path(os.fsdecode(value)).resolve()
        return target == work or work in target.parents

    def audit(event, args):
        targets = []
        if event == "open":
            path, mode, flags = args
            if (isinstance(mode, str) and any(c in mode for c in "wa+")) or flags & (os.O_WRONLY | os.O_RDWR | os.O_CREAT | os.O_TRUNC | os.O_APPEND):
                targets = [path]
        elif event == "os.mkdir":
            if not Path(os.fsdecode(args[0])).is_dir():
                targets = [args[0]]
        elif event in {"os.remove", "os.rmdir", "os.chmod", "os.utime"}:
            targets = [args[0]]
        elif event in {"os.rename", "os.replace"}:
            targets = list(args[:2])
        elif event == "subprocess.Popen":
            blocked.append({"event": event})
            raise PermissionError("Child processes forbidden during native Desktop read")
        for target in targets:
            if not allowed(target):
                blocked.append({"event": event, "path": os.fsdecode(target)})
                raise PermissionError("Read-only Desktop acceptance blocked external write")

    sys.addaudithook(audit)
    from auto_annotation_tool.ranking.mobile_package_experiments import read_mobile_report_bundle
    from auto_annotation_tool.ranking.mobile_human_review import MobileReviewSession
    from auto_annotation_tool.ranking.mobile_canvas_presentation import build_inventory
    results = {}
    for path in sorted(artifacts.glob("*.alprsession")):
        before = hashlib.sha256(path.read_bytes()).hexdigest()
        try:
            bundle = read_mobile_report_bundle(path)
            assert bundle.validation.ok, str(bundle.validation.errors)
            session = MobileReviewSession(bundle, sidecar_path=work / (path.stem + ".review.json"))
            inventory = build_inventory(session)
            assert inventory["assessed"] == 0, "Unreviewed fixture acquired a false assessment"
            assert not (work / (path.stem + ".review.json")).exists(), "Read-only test saved a sidecar"
            assert hashlib.sha256(path.read_bytes()).hexdigest() == before
            results[path.name] = {"status": "PASS", "attempts": len(session.attempts),
                                  "mt_invocations": len(session.mt_invocations), "assessed": inventory["assessed"],
                                  "source_na": inventory["source_na"], "archive_sha256": before,
                                  "scene_extension_loaded": bool(getattr(session, "scene_evidence", None)),
                                  "warnings": list(bundle.validation.warnings)}
        except Exception as error:
            results[path.name] = {"status": "FAIL", "error": repr(error)}
    output = {"source": str(source), "fixtures": results, "blocked_writes": blocked,
              "status": "PASS" if results and not blocked and all(r["status"] == "PASS" for r in results.values()) else "FAIL"}
    (work / "result.json").write_text(json.dumps(output, indent=2), encoding="utf-8")
    return 0 if output["status"] == "PASS" else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixtures", type=Path)
    parser.add_argument("--desktop-root", type=Path)
    parser.add_argument("--desktop-snapshot", type=Path)
    parser.add_argument("--worker", nargs=3)
    args = parser.parse_args()
    if args.worker:
        return desktop_worker(*args.worker)
    assert args.fixtures and args.desktop_root and args.desktop_snapshot
    fixtures = args.fixtures.resolve()
    out = fixtures.parent
    assert out == Path(__file__).resolve().parents[2] / "app/build/f5a12a", "Use the private Android build artifact directory"
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from validate_research_session import validate
    producer = {}
    for path in sorted(fixtures.glob("*.alprsession")):
        result = validate(path)
        producer[path.name] = {"status": "PASS", **result}
    assert len(producer) >= 11, "Missing required producer fixtures"
    negatives = out / "negative"
    negatives.mkdir(exist_ok=True)

    def entries(name):
        with zipfile.ZipFile(fixtures / (name + ".alprsession")) as archive:
            return {name: archive.read(name) for name in archive.namelist()}

    def update_jsonl(values, name, change):
        rows = [json.loads(line) for line in values[name].decode().splitlines() if line]
        change(rows)
        values[name] = ("".join(json.dumps(row) + "\n" for row in rows)).encode()

    negative_results = {}

    def negative(name, source="zero-roi", mutate=None, rehash=True, duplicate=False, crc=False):
        values = entries(source)
        if mutate:
            mutate(values)
        manifest = json.loads(values["manifest.json"])
        if rehash:
            manifest["entry_sha256"] = {key: hashlib.sha256(data).hexdigest() for key, data in values.items() if key != "manifest.json"}
            values["manifest.json"] = json.dumps(manifest).encode()
        target = negatives / (name + ".alprsession")
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(target, "w", zipfile.ZIP_STORED) as archive:
                for key, data in values.items():
                    archive.writestr(key, data)
                if duplicate:
                    archive.writestr("samples/scene_evidence.jsonl", values["samples/scene_evidence.jsonl"])
        if crc:
            with zipfile.ZipFile(target) as archive:
                info = archive.getinfo("samples/scene_evidence.jsonl")
                header_offset = info.header_offset
            data = bytearray(target.read_bytes())
            filename_len, extra_len = struct.unpack_from("<HH", data, header_offset + 26)
            data[header_offset + 30 + filename_len + extra_len] ^= 1
            target.write_bytes(data)
        try:
            validate(target)
        except (AssertionError, KeyError, ValueError, zipfile.BadZipFile) as error:
            negative_results[name] = {"status": "PASS_REJECTED", "reason": str(error)}
            return
        raise AssertionError("Invalid fixture accepted: " + name)

    scene_file = "samples/scene_evidence.jsonl"
    role_file = "samples/mt_detection_roles.jsonl"

    def scene_change(change):
        return lambda values: update_jsonl(values, scene_file, lambda rows: change(rows[0]))

    def role_change(change):
        return lambda values: update_jsonl(values, role_file, lambda rows: change(rows[0]))

    negative("crc", crc=True)
    negative("index-sha", mutate=lambda values: values.update({scene_file: values[scene_file] + b" "}), rehash=False)
    negative("traversal", mutate=scene_change(lambda row: row.update(source_frame_entry="../outside.jpg")))
    negative("absolute-path", mutate=scene_change(lambda row: row.update(source_frame_entry="C:/outside.jpg")))
    negative("dimensions", mutate=scene_change(lambda row: row.update(source_frame_width_px=999)))
    negative("scene-sha", mutate=scene_change(lambda row: row.update(source_frame_sha256="0" * 64)))
    negative("duplicate-scene-id", mutate=lambda values: update_jsonl(values, scene_file, lambda rows: rows.append(dict(rows[0]))))
    negative("duplicate-zip-entry", duplicate=True)
    negative("missing-index", mutate=lambda values: values.pop(scene_file))
    negative("unknown-scene-schema", mutate=scene_change(lambda row: row.update(schema="alpr.scene_evidence.v999")))
    negative("stamp-type", mutate=scene_change(lambda row: row["source_stamp"].update(source_sequence=True)))
    negative("stamp-generation", mutate=scene_change(lambda row: row["source_stamp"].update(scene_generation=999)))
    negative("stale-sequence", mutate=scene_change(lambda row: row["source_stamp"].update(source_sequence=0)))
    negative("fictional-mt", mutate=scene_change(lambda row: row["mt_invocation_ids"].append("fictional-mt")))
    negative("duplicate-role-id", source="two-roi", mutate=lambda values: update_jsonl(values, role_file, lambda rows: rows.append(dict(rows[0]))))
    negative("foreign-raw-id", source="two-roi", mutate=role_change(lambda row: row["raw_attempt_ids"].append("foreign-attempt")))
    negative("overlapping-roles", source="two-roi", mutate=role_change(lambda row: row["suppressed_attempt_ids"].extend(row["final_attempt_ids"])))
    negative("unknown-raw-claimed-complete", source="two-roi", mutate=role_change(lambda row: row.update(raw_attempt_ids=None)))
    negative("role-roi-conflict", source="two-roi", mutate=role_change(lambda row: row.update(roi=[0, 0, 999, 100])))
    negative("role-stamp-conflict", source="two-roi", mutate=role_change(lambda row: row["source_stamp"].update(source_sequence=999)))
    negative("sentinel-as-detection", source="fallback", mutate=role_change(lambda row: row["raw_attempt_ids"].append(row["mt_invocation_id"])))
    negative("missing-image", mutate=lambda values: values.pop(next(key for key in values if key.startswith("samples/frames/"))))

    desktop = {}
    for label, source in (("T17_old_desktop", args.desktop_snapshot), ("current_desktop", args.desktop_root)):
        source = source.resolve()
        work = Path(tempfile.mkdtemp(prefix=label + "-", dir=out))
        process = subprocess.run([sys.executable, "-B", "-X", "utf8", str(Path(__file__).resolve()), "--worker", str(source), str(fixtures), str(work)],
                                 cwd=work, text=True, encoding="utf-8", stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        (work / "worker.log").write_text(process.stdout, encoding="utf-8")
        result_path = work / "result.json"
        result = json.loads(result_path.read_text(encoding="utf-8")) if result_path.exists() else {"status": "FAIL", "error": process.stdout[-4000:]}
        result["exit_code"] = process.returncode
        desktop[label] = result
    desktop_support = all(result.get("scene_extension_loaded") for name, result in desktop["current_desktop"].get("fixtures", {}).items() if not name.startswith("legacy-"))
    t19 = {"status": "NOT_RUN_DESKTOP_PENDING", "reason": "Native Desktop scene/role extension reader and round-trip not implemented"}
    if desktop_support:
        t19 = {"status": "NOT_RUN", "reason": "Scene loading detected; explicit Desktop save/export acceptance remains required"}
    output = {"producer": producer, "T16_negative": negative_results, "desktop": desktop, "T19": t19}
    (out / "acceptance.json").write_text(json.dumps(output, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({"producer_fixtures_pass": len(producer), "negative_cases_rejected": len(negative_results),
                      "desktop": {key: value["status"] for key, value in desktop.items()}, "T19": t19["status"]}, indent=2))
    return 0 if all(result["status"] == "PASS" for result in desktop.values()) else 1


if __name__ == "__main__":
    raise SystemExit(main())
