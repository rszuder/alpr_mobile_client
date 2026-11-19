"""Read secured copies only. No recovery, repacking, GT matching or device writes."""
from pathlib import Path
import argparse, collections, csv, hashlib, json, re, zipfile


def sha(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def rows(archive, name):
    with archive.open(name) as stream:
        for line in stream:
            if line.strip():
                yield json.loads(line)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("delivery", type=Path)
    args = parser.parse_args()
    delivery = args.delivery.resolve()
    evidence = delivery / "device_readonly"
    results = []
    for path in sorted((evidence / "public_exports").glob("*.alprsession")):
        before = sha(path)
        with zipfile.ZipFile(path) as archive:
            manifest = json.loads(archive.read("manifest.json"))
            bad_hashes = []
            for name, expected in manifest["entry_sha256"].items():
                digest = hashlib.sha256()
                with archive.open(name) as stream:
                    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                        digest.update(chunk)
                if digest.hexdigest() != expected:
                    bad_hashes.append(name)
            duplicates=[name for name,count in collections.Counter(archive.namelist()).items() if count>1]
            unlisted=sorted(set(archive.namelist())-set(manifest["entry_sha256"])-{"manifest.json"})
            assert not duplicates and not unlisted
            meta = json.loads(archive.read("session.json"))
            roles = list(rows(archive, "samples/mt_detection_roles.jsonl"))
            frames = list(rows(archive, "samples/tv_bbox_inputs.jsonl"))
            events = collections.Counter(r["event"] for r in rows(archive, "samples/f6_execution.jsonl"))
            result = dict(path=str(path), bytes=path.stat().st_size, sha256=before,
                          crc_bad_entry=archive.testzip(), manifest_bad_entries=bad_hashes, duplicate_entries=duplicates, unlisted_entries=unlisted,
                          zip_entries=len(archive.infolist()), session_id=meta["session_id"],
                          state=meta["state"], collection_complete=meta["collection_complete"],
                          started_at=meta["started_at"], finished_at=meta["finished_at"],
                          duration_ns=meta["finished_elapsed_nanos"]-meta["started_elapsed_nanos"],
                          execution=meta["execution"], completion_reason=meta["completion_reason"],
                          last_write_error=meta.get("last_write_error"),
                          scenes=meta.get("scene_evidence_written"), attempts=meta.get("attempt_count"),
                          tv_frames=len(frames), mt_roles=len(roles), lifecycle_events=dict(events),
                          role_proofs=dict(collections.Counter(r.get("role_proof_status") for r in roles)),
                          role_cancel_reasons=dict(collections.Counter(r.get("cancel_reason") for r in roles if r.get("stale_or_cancelled"))),
                          stale_frames=sum(bool(r.get("stale_or_cancelled")) for r in frames),
                          frame_statuses=dict(collections.Counter(r.get("pipeline_status") for r in frames)),
                          evidence_status={k:v for k,v in meta.items() if k in (
                              "f6_evidence","tv_bbox_input_evidence","scene_evidence_complete","mt_detection_roles_complete",
                              "scene_evidence_integrity_loss_count","dropped_sample_count","dropped_telemetry_count")})
            assert before == sha(path), "Secured archive changed during read"
            assert not result["crc_bad_entry"] and not bad_hashes
            results.append(result)
    (delivery/"audit/real-export-audit.json").write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding="utf-8")
    timeline = []
    for log in sorted((evidence/"private_original/files/logs").glob("alpr.log*")):
        for line in log.read_text(encoding="utf-8",errors="replace").splitlines():
            if "F6_CALIBRATION" not in line or "{" not in line:
                continue
            try:
                data=json.loads(line[line.index("{"):]); g=data.get("source_geometry") or {}
                physical={k:g.get(k) for k in ("camera_id","physical_camera_id","source_image_width","source_image_height",
                          "buffer_width","buffer_height","crop_rect_px","rotation_degrees","display_rotation_degrees","mirrored","zoom_ratio")}
                timeline.append(dict(time=line.split()[0],cause=data.get("cause"),phase=data.get("phase"),
                          reason=data.get("reason"),roi_digest=data.get("roi_digest"),dirty=data.get("dirty"),
                          callback=data.get("editor_callback_token"),draft_initializations=data.get("draft_initializations"),
                          binding=g.get("binding_generation"),view_revision=g.get("viewport_revision"),
                          view_width=g.get("preview_width"),view_height=g.get("preview_height"),
                          source=json.dumps(physical,sort_keys=True),draft_points=json.dumps(data.get("draft_corners_norm")),
                          bbox=json.dumps(data.get("draft_bbox_camera_xy"))))
            except (ValueError,KeyError):
                continue
    timeline.sort(key=lambda r:r["time"])
    (delivery/"audit/real-calibration-timeline.json").write_text(json.dumps(timeline,ensure_ascii=False,indent=2),encoding="utf-8")
    if timeline:
        with (delivery/"audit/real-calibration-timeline.csv").open("w",encoding="utf-8-sig",newline="") as stream:
            writer=csv.DictWriter(stream,fieldnames=list(timeline[0]));writer.writeheader();writer.writerows(timeline)
    print(json.dumps(dict(exports=len(results),identical=len({r["sha256"] for r in results})==1,
                         states=[r["state"] for r in results],timeline_rows=len(timeline))))


if __name__ == "__main__":
    main()
