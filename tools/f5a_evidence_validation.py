"""Strict validation of optional F5A producer evidence; standard library only."""
import hashlib
import json
import struct
from pathlib import PurePosixPath


def safe_path(value):
    assert isinstance(value, str) and value, "Missing image path"
    path = PurePosixPath(value)
    assert not path.is_absolute() and not any(p in {"", ".", ".."} for p in value.split("/")), "Unsafe evidence path"
    assert "\\" not in value and ":" not in value and "\x00" not in value, "Unsafe evidence path"
    return value


def jpeg_dimensions(data):
    assert data[:2] == b"\xff\xd8", "Invalid JPEG signature"
    pos = 2
    while pos < len(data):
        assert data[pos] == 255, "Invalid JPEG marker"
        while pos < len(data) and data[pos] == 255:
            pos += 1
        assert pos < len(data), "Truncated JPEG"
        marker = data[pos]
        pos += 1
        if marker in {0xD8, 0x01} or 0xD0 <= marker <= 0xD7:
            continue
        assert marker not in {0xD9, 0xDA}, "JPEG dimensions missing"
        assert pos + 2 <= len(data), "Truncated JPEG"
        size = struct.unpack_from(">H", data, pos)[0]
        assert size >= 2 and pos + size <= len(data), "Truncated JPEG segment"
        if marker in {0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF}:
            assert size >= 8, "Invalid JPEG SOF"
            height, width = struct.unpack_from(">HH", data, pos + 3)
            assert width > 0 and height > 0, "Invalid JPEG dimensions"
            return width, height
        pos += size
    raise AssertionError("JPEG dimensions missing")


def integer(value):
    assert type(value) is int and 0 <= value < 2**63, "Invalid int64"
    return value


def stamp(value):
    assert isinstance(value, dict), "Missing source stamp"
    for key in ("scene_generation", "visual_epoch", "camera_transform_generation", "source_sequence", "source_timestamp_nanos"):
        integer(value[key])
    assert value["source_timestamp_domain"] in {"CAMERAX_SENSOR", "PREVIEW_INHERITED_CAMERA", "RUNTIME_UPTIME", "UNKNOWN"}


def ids(value):
    assert isinstance(value, list) and all(isinstance(x, str) and x for x in value), "Invalid identity array"
    assert len(set(value)) == len(value), "Duplicate identity"
    return set(value)


def validate_extension(archive, descriptor, session, attempts):
    caps = descriptor.get("capabilities", {})
    if not caps.get("scene_evidence_v1") and not caps.get("mt_detection_roles_v1"):
        return {"available": False}
    assert caps.get("scene_evidence_v1") is True and caps.get("mt_detection_roles_v1") is True
    names = archive.namelist()
    assert len(names) == len(set(names)), "Duplicate ZIP entry"
    for name in names:
        safe_path(name.rstrip("/"))
    assert archive.testzip() is None, "ZIP CRC mismatch"
    assert descriptor["scene_evidence_file"] == "samples/scene_evidence.jsonl"
    assert descriptor["mt_detection_roles_file"] == "samples/mt_detection_roles.jsonl"

    def rows(name):
        info = archive.getinfo(name)
        assert info.file_size <= 32 * 1024 * 1024, "Evidence index too large"
        content = archive.read(name).decode("utf-8")
        assert not content.startswith("\ufeff"), "Unexpected BOM"
        values = [json.loads(line) for line in content.splitlines() if line.strip()]
        assert all(isinstance(row, dict) for row in values), "Invalid evidence row"
        return values

    scenes = rows(descriptor["scene_evidence_file"])
    roles = rows(descriptor["mt_detection_roles_file"])
    scene_ids = ids([row["scene_event_id"] for row in scenes])
    ids([row["attempt_id"] for row in attempts])
    invocations = {}
    for row in attempts:
        if str(row["mt_executed"]).lower() == "true":
            invocations.setdefault(row["mt_invocation_id"], []).append(row)
    role_ids = ids([row["mt_invocation_id"] for row in roles])
    assert role_ids <= invocations.keys(), "Role references a nonexistent MT"
    written = 0
    scene_calls = set()
    for row in scenes:
        assert row["schema"] == "alpr.scene_evidence.v1" and row["session_id"] == session["session_id"]
        assert row["event_type"] in {"SESSION_START", "SCENE_BOUNDARY"}
        assert row["scene_event_id"].startswith(session["session_id"] + "-s")
        assert isinstance(row["event_reason"], str) and row["event_reason"]
        assert row["settling_status"] == "NOT_ASSESSED"
        assert row["source_frame_coordinate_space"] == "mt_source_bitmap_xy"
        assert row["source_frame_origin"] == "pipeline_bitmap_before_roi"
        integer(row["event_elapsed_nanos"])
        if row["event_stamp"] is not None:
            stamp(row["event_stamp"])
        if row["source_stamp"] is not None:
            stamp(row["source_stamp"])
        if row["write_status"] == "WRITTEN":
            written += 1
            assert row["missing_reason"] is None
            entry = safe_path(row["source_frame_entry"])
            assert entry.startswith("samples/frames/") and entry.endswith(".jpg")
            payload = archive.read(entry)
            assert hashlib.sha256(payload).hexdigest() == row["source_frame_sha256"], "Scene SHA mismatch"
            assert jpeg_dimensions(payload) == (row["source_frame_width_px"], row["source_frame_height_px"]), "Scene dimension mismatch"
            stamp(row["source_stamp"])
            event, source = row["event_stamp"], row["source_stamp"]
            for key in ("scene_generation", "visual_epoch", "camera_transform_generation", "source_timestamp_domain"):
                assert source[key] == event[key], "Scene source identity conflict"
            assert source["source_sequence"] >= event["source_sequence"], "Stale scene image"
            integer(row["capture_elapsed_nanos"])
            integer(row["capture_duration_nanos"])
        else:
            assert row["write_status"] == "MISSING" and row["missing_reason"], "Missing image needs a reason"
            assert row["source_frame_entry"] is None and row["source_frame_sha256"] is None
        calls = ids(row["mt_invocation_ids"])
        assert calls <= invocations.keys(), "Scene references a nonexistent MT"
        assert not (scene_calls & calls), "Invocation assigned to multiple scenes"
        scene_calls |= calls
        assert row["mt_execution_status"] in {"EXECUTED", "NOT_RUN", "UNKNOWN"}
        assert bool(calls) == (row["mt_execution_status"] == "EXECUTED")
        if row["mt_execution_status"] == "NOT_RUN":
            assert row["mt_not_run_reason"], "NOT_RUN needs a reason"
        else:
            assert row["mt_not_run_reason"] is None
        assert row["links_status"] in {"COMPLETE", "INCOMPLETE"}
        for mp in row["mp_observations"]:
            stamp(mp["source_stamp"])
            integer(mp["trace_frame_id"])
            assert mp["execution_status"] in {"EXECUTED", "NOT_RUN", "FAILED"}
            if mp["execution_status"] == "EXECUTED":
                integer(mp["raw_count"]); integer(mp["rejected_class_count"])
                assert len(mp["rejected_class_ids"]) == mp["rejected_class_count"] <= mp["raw_count"]
                assert all(type(x) is int for x in mp["rejected_class_ids"])
            elif mp["execution_status"] == "NOT_RUN":
                assert mp["not_run_reason"] and mp["raw_count"] is None and mp["rejected_class_count"] is None and mp["rejected_class_ids"] is None
            for roi in mp["selected_rois"]:
                for key in ("left", "top", "right", "bottom", "entity_id", "vehicle_track_id"):
                    integer(roi[key])
                assert roi["right"] > roi["left"] and roi["bottom"] > roi["top"]
    assert session["scene_evidence_expected"] == len(scenes)
    assert session["scene_evidence_written"] == written
    assert session["scene_evidence_missing"] == len(scenes) - written
    if session["scene_evidence_complete"]:
        assert written == len(scenes) > 0 and session["scene_evidence_integrity_loss_count"] == 0
        assert all(row["links_status"] == "COMPLETE" for row in scenes)
    role_complete = True
    for row in roles:
        gid = row["mt_invocation_id"]
        assert row["schema"] == "alpr.mt_detection_roles.v1" and row["session_id"] == session["session_id"]
        stamp(row["source_stamp"])
        assert row["raw_scope"] == "DECODER_OUTPUT" and row["role_scope"] == "MERGED_MT_DEDUP"
        assert row["postprocess_scope_id"] == session["session_id"] + "/frame-" + str(integer(row["trace_frame_id"]))
        assert row["postprocess_status"] in {"COMPLETED", "BYPASSED", "NOT_REACHED"}
        assert row["role_proof_status"] in {"COMPLETE", "INCOMPLETE"}
        assert row["scene_event_id"] in scene_ids or (row["scene_event_id"] is None and row["role_proof_status"] == "INCOMPLETE")
        source_rows = invocations[gid]
        for source in source_rows:
            for key, value in row["source_stamp"].items():
                assert str(source[key]) == str(value), "Role stamp conflict"
            assert [int(source["roi_" + key]) for key in ("left", "top", "right", "bottom")] == row["roi"], "Role ROI conflict"
            assert source["source_frame_entry"] == (row["source_frame_entry"] or ""), "Role image conflict"
        if row["source_frame_entry"] in names:
            width, height = jpeg_dimensions(archive.read(safe_path(row["source_frame_entry"])))
            assert width == int(source_rows[0]["source_frame_width_px"]) and height == int(source_rows[0]["source_frame_height_px"]), "Invocation image dimensions conflict"
            left, top, right, bottom = row["roi"]
            assert 0 <= left < right <= width and 0 <= top < bottom <= height, "ROI outside its source bitmap"
        expected = {r["attempt_id"] for r in source_rows if r["mt_detection_index"] != ""}
        final, suppressed = ids(row["final_attempt_ids"]), ids(row["suppressed_attempt_ids"])
        rejected = ids([item["attempt_id"] for item in row["rejected_before_nms"]])
        unresolved = ids(row["unresolved_attempt_ids"])
        partitions = [final, suppressed, rejected, unresolved]
        assert all(item["reason"] == "INSUFFICIENT_KEYPOINTS" for item in row["rejected_before_nms"])
        assert all(str(source.get("mz_executed")).lower() != "true" and source.get("evidence_kind") != "plate_crop"
                   for source in source_rows if source["attempt_id"] in suppressed), "Suppressed candidate has downstream MZ evidence"
        assert sum(map(len, partitions)) == len(set.union(*partitions)), "Role populations overlap"
        assert set.union(*partitions) <= expected, "Foreign attempt in roles"
        if row["raw_attempt_ids"] is not None:
            assert ids(row["raw_attempt_ids"]) == expected == set.union(*partitions), "Incomplete RAW population"
        if row["role_proof_status"] == "COMPLETE":
            assert row["raw_attempt_ids"] is not None and not unresolved and row["postprocess_status"] != "NOT_REACHED"
        else:
            role_complete = False
        if row["stale_or_cancelled"]:
            assert row["cancel_reason"]
    if session["mt_detection_roles_complete"]:
        assert role_ids == invocations.keys() == scene_calls and role_complete
    manifest = json.loads(archive.read("manifest.json"))
    report = json.loads(archive.read("report.json"))["research_collection"]
    for key in ("scene_evidence_expected", "scene_evidence_written", "scene_evidence_missing", "scene_evidence_integrity_loss_count", "scene_evidence_complete", "mt_detection_roles_complete"):
        assert manifest[key] == session[key] == report[key], "Extension summary conflict"
    return {"available": True, "scenes": len(scenes), "written": written, "missing": len(scenes) - written,
            "mt_role_records": len(roles), "scene_evidence_complete": session["scene_evidence_complete"],
            "mt_detection_roles_complete": session["mt_detection_roles_complete"]}
