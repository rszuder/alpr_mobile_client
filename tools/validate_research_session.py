"""Validate the portable samples-v2 ZIP/CSV/JSON contract using only the standard library.

Usage: python tools/validate_research_session.py path/to/run.alprsession
"""
import csv
import hashlib
import io
import json
import sys
import zipfile
from collections import Counter, defaultdict


def validate(path):
    with zipfile.ZipFile(path) as archive:
        names = {entry.filename for entry in archive.infolist() if not entry.is_dir()}
        manifest = json.loads(archive.read("manifest.json"))
        assert manifest["schema"] == "alpr.mobile_research_bundle.v1"
        hashes = manifest["entry_sha256"]
        assert set(hashes) == names - {"manifest.json"}, "Every entry must have SHA-256"
        for name, expected in hashes.items():
            digest = hashlib.sha256()
            with archive.open(name) as stream:
                for block in iter(lambda: stream.read(65536), b""):
                    digest.update(block)
            assert digest.hexdigest() == expected, f"Hash mismatch: {name}"
        for name in ("report.json", "traces.csv", "thermal.csv", "frame_flow.csv", "events.jsonl",
                     "application.log", "session.json", "samples/schema.json", "samples/attempts.csv",
                     "samples/index.csv", "samples/annotations.jsonl"):
            assert name in names, f"Missing {name}"
        descriptor = json.loads(archive.read("samples/schema.json"))
        assert descriptor["schema"] == "alpr.mobile_research_samples.v2"
        assert descriptor["human_review"] == "desktop"
        session = json.loads(archive.read("session.json"))
        attempts = list(csv.DictReader(io.StringIO(archive.read("samples/attempts.csv").decode("utf-8"))))
        crops = list(csv.DictReader(io.StringIO(archive.read("samples/index.csv").decode("utf-8"))))
        annotations = [json.loads(line) for line in archive.read("samples/annotations.jsonl").decode("utf-8").splitlines() if line]
        by_attempt = {row["attempt_id"]: row for row in attempts}
        warnings = []
        invocations = defaultdict(list)
        invocation_contract = descriptor.get("mt_invocation_identity") == "one_backend_execution_one_input"
        for row in attempts:
            assert None not in row, "Invalid CSV column count"
            assert None not in row.values(), "Missing CSV columns"
            assert row["session_id"] == session["session_id"]
            assert row["mt_status"] in {"NOT_RUN", "NO_DETECTION", "DETECTION_INVALID_QUAD", "VALID_QUAD"}
            assert row["rectification_status"] in {"NOT_RUN", "FAILED", "OK"}
            assert row["mz_status"] in {"NOT_RUN", "NO_CHARACTERS", "READ"}
            assert row["subject_key"].startswith(f'{session["session_id"]}/sg-{row["scene_generation"]}/')
            if int(row["entity_id"]) > 0:
                assert row["subject_key"].endswith(f'/entity-{row["entity_id"]}')
            if row["evidence_entry"] not in names:
                assert row["missing_evidence_reason"], "Missing evidence needs an explicit reason"
                warnings.append(row["attempt_id"] + ": " + row["missing_evidence_reason"])
            if row["stale_or_cancelled"] == "true":
                assert row["cancel_reason"], "Cancelled work needs a reason"
            if row["mz_status"] == "NO_CHARACTERS":
                assert row["prediction"] == "", "Empty MZ cannot inherit consensus"
            if invocation_contract:
                if row["mt_executed"] == "true":
                    assert row["mt_invocation_id"], "Executed MT needs invocation identity"
                    invocations[row["mt_invocation_id"]].append(row)
                    if row["mt_input_evidence_entry"] not in names:
                        assert row["mt_input_missing_evidence_reason"], "Missing MT input needs a reason"
                        warnings.append(row["attempt_id"] + ": " + row["mt_input_missing_evidence_reason"])
                else:
                    assert row["mt_status"] == "NOT_RUN"
                    assert not row["mt_invocation_id"] and not row["mt_detection_count"] and not row["mt_detection_index"]
        for invocation, rows in invocations.items():
            count = int(rows[0]["mt_detection_count"])
            assert count >= 0
            for row in rows:
                assert int(row["mt_detection_count"]) == count
                for key in ("session_id", "scene_generation", "visual_epoch", "camera_transform_generation",
                            "source_sequence", "source_timestamp_nanos", "roi_left", "roi_top", "roi_right",
                            "roi_bottom", "input_width", "input_height", "input_scale", "input_pad_x", "input_pad_y"):
                    assert row[key] == rows[0][key], f"Inconsistent {key} in {invocation}"
            if count == 0:
                assert len(rows) == 1 and rows[0]["mt_detection_index"] == ""
                assert rows[0]["mt_status"] == "NO_DETECTION"
            else:
                indices = [int(row["mt_detection_index"]) for row in rows]
                assert len(set(indices)) == len(indices) and all(0 <= index < count for index in indices)
                if sorted(indices) != list(range(count)):
                    warnings.append(invocation + ": incomplete detection rows")
        assert len(crops) == len(annotations)
        for row, annotation in zip(crops, annotations):
            assert None not in row, "Invalid crop CSV column count"
            assert row["verification_status"] == "not_reviewed" and row["ground_truth"] == ""
            assert annotation["prediction"] == row["prediction"]
            assert annotation["attempt_id"] == row["attempt_id"]
            assert row["attempt_id"] in by_attempt, "Crop must reference an attempt"
            assert annotation["subject_key"] == by_attempt[row["attempt_id"]]["subject_key"]
            assert row["prediction"] == by_attempt[row["attempt_id"]]["prediction"]
        if session["collection_complete"]:
            assert session["state"] == "COMPLETED"
            assert session["dropped_sample_count"] == 0 and session["dropped_telemetry_count"] == 0
            assert session.get("integrity_loss_count", 0) == 0
            assert not warnings
            assert len(by_attempt) == len(attempts), "Duplicate attempt IDs"
        if invocation_contract:
            assert session["storage_prepared"] and session["collection_mode"] == "automatic"
            assert session["attempt_count"] == len(attempts) and session["crop_count"] == len(crops)
            report = json.loads(archive.read("report.json"))
            assert report["app_build"] == session["app_build"]
            assert report["app_version"] == session["app_version"]
            build = report["app_build"]
            for key in ("git_commit", "git_dirty", "git_dirty_available", "built_at_utc"):
                assert key in build
            assert build["source_state"] == (("dirty" if build["git_dirty"] else "clean")
                                             if build["git_dirty_available"] else "unknown")
        return {"file": str(path), "state": session["state"], "attempts": len(attempts),
                "crops": len(crops), "hashes_verified": len(hashes),
                "mt_invocation_contract": invocation_contract, "mt_invocations": len(invocations),
                "mt_status": dict(Counter(row["mt_status"] for row in attempts)),
                "mz_status": dict(Counter(row["mz_status"] for row in attempts)),
                "cancelled": sum(row["stale_or_cancelled"] == "true" for row in attempts),
                "warnings": warnings}


if __name__ == "__main__":
    for source in sys.argv[1:]:
        print(json.dumps(validate(source), indent=2, ensure_ascii=False))
