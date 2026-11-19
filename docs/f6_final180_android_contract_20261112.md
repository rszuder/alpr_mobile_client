# F6 / FINAL180 — Android producer contract, 2026-11-12

Contract: **F6_FINAL180_SPLIT_V1**. Android repository only.
Baseline branch `main`, HEAD `f7943a7d7334afd1e4188ef9860b2126331926c8`.
The worktree was already dirty. This change does not publish, install or alter historical sessions.

**Dla operatora:** wdrożono opcjonalny zapis strumieniowy i osobny obszar fotografii F6.
Potrzebna jest nowa APK. Kalibracja TV pozostaje osobna, a F5A po wyłączeniu F6 zachowuje
historyczną ścieżkę. Odbiór A12 + TV + Desktop i rzeczywiste 90 minut pozostają NOT_VERIFIED.

## Audit and implementation boundary

The old producer contains the required source/MT/FINAL/MZ identities, including empty MT results.
It does **not** establish readiness for 90 minutes: TV evidence has a session limit of 4096 frames;
frame/role/scene metadata is retained in RAM and folded at finalization; an interrupted in-flight
attempt may not yet exist in attempts.jsonl. A long timer alone does not address these limits.

The optional `f6_final180_v1` profile uses the same ResearchSessionStore and FIFO writer, image
reservations, journals, recovery and ResearchArchive serializer. Completed TV metadata has a
64-record working cache; immutable roles are journaled without retaining all roles in RAM.
Scene observations and invocation links are folded from durable records during finalization.
Derived external sort files use a 4 MiB buffer and merge at most 16 runs at once. They are disposable
indexes inside this session, excluded from the archive, and never an independent evidence store.
Primary journals are not rewritten from a partial RAM cache.
Final scene observation/invocation arrays are emitted incrementally as JSON, including a single
scene larger than 32 MiB; no per-scene history array is accumulated in the finalizer either.

**A new APK is required.** The user approved a second, explicit change: a separate photo rectangle
inside the existing TV crop. A code drawn on the TV border would otherwise remain inside the old
whole-TV R0 input. This new input domain is F6-only and is not described as a passive logging cost.
Models, model thresholds, scheduler, NMS, scene handling, consensus and MZ decisions are preserved.
F6 off retains the historical F5A path and its 4096-frame limit.

## Schemas and files

| File | Schema / role |
| --- | --- |
| `manifest.json` | `alpr.mobile_research_bundle.v1`; existing per-entry SHA-256 and collection flags |
| `session.json`, `report.json/research_collection` | `alpr.mobile_research_session.v1`; identical frozen session metadata |
| `samples/schema.json` | `alpr.mobile_research_samples.v2`; additive capability `f6_final180_v1=true` and `f6_execution_file` |
| `samples/tv_bbox_inputs.jsonl` | `alpr.tv_bbox_input.v1`; source and TV images, source identity, actual attempt links |
| `samples/scene_evidence.jsonl` | `alpr.scene_evidence.v1`; existing scene evidence, not the photo manifest |
| `samples/mt_detection_roles.jsonl` | `alpr.mt_detection_roles.v1`; actual merged-dedup memberships |
| `samples/attempts.jsonl` | Existing per-detection/invocation records; authoritative TV and F6 additions are here |
| `samples/attempts.csv` | Existing column set; it does not contain every JSONL/TV/F6 field |
| `samples/f6_execution.jsonl` | **New:** `alpr.f6_execution_event.v1`, append-only lifecycle events |
| `samples/*_journal.jsonl` | Original streaming evidence history, retained in the archive |
| `samples/evidence/*.jpg`, `samples/crops/*.jpg`, `samples/frames/*.jpg` | Existing writer's JPEG evidence |
| `telemetry/*.jsonl`, top-level CSV/log files | Existing telemetry; F6 exports persistent files as streams |

Absent F6 capability means the historical contract. Do not infer F6 from names or directories.
The infrastructure experiment type remains `roi_budget`; the distinct frozen `capture_profile`
identifies F6. No F5A protocol/version is renamed and no old archive is migrated.

`execution` preserves actual `variant`, `roi_budget_policy`, `scene_handling_mode`, `analysis_mode`,
`feature_flags`, `stages.mp/mt/mz`, and model references/manifests. Stage fields include model_id,
fingerprint, variant_id, runtime, precision, CPU threads, GPU/delegate and input specification.
F6 requires the selected R0 without MP and TV_BBOX_CROP; it never silently changes a selection.
Actual MT-DAY/MZ-DAY FP32/backend identity still needs comparison with the operator's selected
packages on A12. Synthetic test model IDs are not evidence of installed real models.

## Identity and join rules

1. Desktop decodes its opaque exposure code from `source_image.entry`, after export. Android has
   no exposure_id, GT, code decoder or network synchronization.
2. The TV row's `frame_key` is the existing canonical SHA-256 of `{source_stamp, snapshot}`;
   `snapshot` is the TV crop digest. SourceStamp has scene_generation, visual_epoch,
   camera_transform_generation, source_sequence, source_timestamp_nanos, source_timestamp_domain.
3. Join attempts by `tv_bbox_frame_key`, not OCR, filenames, wall-clock proximity or scene number.
   Frame keys are scoped by session_id; do not deduplicate frames across sessions by frame_key alone.
   `attempt_links` reproduces actual attempt_id, mt_invocation_id, mt_executed, mt_detection_index,
   roi_local_xy and local_geometry. Empty links do not establish an empty MT result.
4. One backend MT start assigns `mt_invocation_id=attempt_id` to the original invocation row.
   Additional decoded detections have distinct attempt_id and zero-based decoder index, sharing
   the invocation and source. No detection is created for a genuinely empty decoder output.
5. Read every `final_attempt_ids` member of that invocation's role row. RAW, SUPPRESSED,
   rejected_before_nms and unresolved_attempt_ids remain diagnostic. Keep all FINAL members,
   including excess detections. Do not derive role from overlap or the number of MZ calls.
   For the selected input frame, include all its actual MT invocations in the shared
   postprocess_scope_id; do not evaluate just the first backend call if the frame has more calls.
6. MZ belongs to the detection's attempt_id and that attempt's MT invocation. Each actual F6 MZ
   start has `mz_invocation_id=<attempt_id>/mz-<counter>` in lifecycle events; the attempt retains
   `f6_mz_invocation_id`. It is assigned at the actual backend-call site.
7. Use `mz_executed`, `mz_status`, `mz_execution_error`, `raw_prediction`/`prediction` together.
   `consensus_prediction` can contain earlier observations; it is not the fresh result of this call.

Late results retain the captured SourceStamp, frame_key, scene_event_id and original invocation.
`scene_event_id` is never exposure_id. The phone neither selects the best observation nor chooses
Desktop's first evaluable frame. Missing evidence that could hide an earlier MT start must not
be repaired by choosing a later successful invocation.

## Lifecycle and missing evidence

Every F6 event has schema, session_id, monotonically increasing event_sequence, event,
elapsed_nanos, time_domain=`ANDROID_ELAPSED_REALTIME`, nullable source_stamp/frame_key/
scene_event_id/trace_frame_id/attempt_id/mt_invocation_id/mz_invocation_id, and details.
JSON null is an absent identifier, not the string `"null"`.

| Event | Meaning |
| --- | --- |
| FRAME_OPENED | The producer accepted this specific source for evidence |
| MT_STARTED | Immediately before entering the actual MT backend; preserved even if it throws |
| MT_RETURNED | Backend returned; this alone does not establish a decoded detection population |
| MT_DECODED | Decoder completed; details.raw_detection_count is the actual count, including zero |
| MT_FAILED | Actual backend exception, details.execution_error |
| MZ_STARTED / MZ_RETURNED / MZ_FAILED | Actual MZ lifecycle, tied to the original detection and frame |
| FRAME_FINISHED | Pipeline status, actual MT-start count, NOT_RUN or STARTED, cancellation and processing error |
| SCENE_OBSERVATION | Existing scene diagnostics and no-MT reason, without accumulating the whole scene in live RAM |
| SYNTHETIC_LOAD | Test-only stress padding; never an invocation or device measurement |

A finished frame with zero actual starts records NOT_RUN. An error before the backend does not
create an invocation. Source conversion failure records SOURCE_UNAVAILABLE and missing images.
Fatal Java Errors are rethrown with the existing control flow; the F6 audit records the observed
processing_error before unwinding. Synthetic fatal-error tests allocate no excess memory.
Camera frames rejected before processing admission are not individually archived; an unrepresented
exposure remains a coverage gap, not a negative answer.

Confirmed no detection requires a real START, successful return/decoding with zero output and
consistent empty role membership. The historical default mt_status=NO_DETECTION set at MT start
does not prove this when an execution/processing error or incomplete lifecycle exists.

An unmatched START after process death means unknown/interrupted outcome, not zero and not NOT_RUN.
No recovered detection, invocation, time or image is invented. Recovery retains committed rows,
marks pending loss unknown, and exports PARTIAL. A broken JSONL tail is retained as evidence;
the F6 validator tolerates malformed raw journal/attempt lines only with declared integrity loss.
Identity conflicts, double offsets, duplicate identities and invalid hashes are validation errors.

Read **all** completeness indicators: f6_evidence.complete, pending_loss_unknown,
unlocated_loss_possible, lifecycle/projection errors, TV image completeness, role completeness,
dropped_sample_count and dropped_telemetry_count. General collection_complete alone is insufficient
for old exports. New F6 exports lower it when required F6 evidence is incomplete.
Even f6_evidence.complete=true describes the consistency of produced records, not coverage of
180 exposures. Only Desktop's manifest/code join can establish that population and its gaps.

## Coordinates, pixels and clocks

`source_image`: full pipeline camera bitmap after CameraX cropRect and rotation, before TV crop.
It is not the uncropped raw sensor buffer or a later screenshot. `source_image` and `crop_image`
are copied from the same TvBboxCrop.Frame with the same immutable SourceStamp.

`crop_image`: existing whole TV bounding-box context in `tv_bbox_local_xy`, including TV margins.
Its meaning is unchanged; it may contain the code. R0 F6 further crops the explicit photo rectangle
before MT preprocessing. The preprocessor's existing observer saves the letterboxed MT image at
`mt_input_evidence_entry`. Source/crop/MT JPEGs are JPEG94, **not exact tensor bytes**.

`execution.f6_photo_roi`: sealed `alpr.f6_photo_roi.v1`, with coordinate_space=
`camera_full_bitmap_xy`, source_width_px/source_height_px, corners_norm, photo_roi_camera_xy,
tv_bbox_crop_digest_sha256 and digest_sha256. The integer rectangle is floor(minima)/ceil(maxima)
of the separately selected photo corners. It is validated against the frozen TV/source context.
The outer TV corners and calibration are not overwritten.

Actual attempt roi_left/top/right/bottom are full-camera pixels. `tv_bbox_roi_local_xy` and the
frame link's roi_local_xy subtract the **outer TV** left/top. `tv_bbox_local_geometry` is likewise
TV-local, not photo-local. Add the TV offset exactly once. Attempt plate_left/top/right/bottom
are already full-camera pixels. `mt_pose_corners_norm` is the existing `x:y;x:y;...` string in
`source_frame_normalized_v1`, TL_TR_BR_BL, with its source dimensions; do not add an offset again.
`input_scale`, input_pad_x/y and input_width/height describe the actual letterbox preprocessing.
ROI left/top are inclusive and right/bottom exclusive. Full-image normalization divides by width
and height, not width-1/height-1. The legacy capture_source label follows zoom ratio; use frozen
feature_flags.autozoom and camera_setup to determine whether AZ was enabled, not that label alone.

`processing_transform=CROP_ONLY_TRANSLATION`, `H_TV=NOT_APPLIED`. Desktop owns projective
transforms, marker reliability, manifest assignment, 1:1 matching and GT metrics.

Source timestamps preserve their named domain (normally CAMERAX_SENSOR) in nanoseconds.
They are not aligned to the PC clock. F6 event elapsed_nanos and mt_started_elapsed_nanos use
Android SystemClock.elapsedRealtimeNanos; attempt_started_elapsed_nanos starts earlier, before
preprocessing. captured_at_ms is wall-clock epoch milliseconds; UTC ISO strings are wall time.
Keep integer timestamps as int64; fixtures deliberately include values above JavaScript's 2^53.

F6-only mt_backend_elapsed_nanos/mz_backend_elapsed_nanos exclude lifecycle event recording.
f6_evidence separately records capture_elapsed_nanos, write_elapsed_nanos and lifecycle_bytes.
Image write durations and the existing crop preparation/source-copy durations remain separate.
Wall/end-to-end time still includes evidence work and resource contention. Host synthetic timings
are not A12 overhead measurements or proof of comparability with historical F5A/APKs.

## Limits and NO_GO conditions

- Existing writer queue: 256; attempt permits: 64; source copies: 24 MiB; other image copies: 64 MiB.
  Limits are not increased and do not throttle or admit model inference. Missing evidence has a reason.
- F5A retains MAX_FRAMES=4096. F6 has no session frame-count cap; its TV working cache is 64 rows.
- START's existing 128 MiB free-space threshold is **not** a 90-minute capacity guarantee.
  f6_storage records capacities/free bytes and storage_budget_90min=NOT_VERIFIED.
- No early images, failed attempts or prior exports are deleted to fit later evidence.
- Source files, temporary final archive and finalization scratch need simultaneous disk space.
  Full-disk, writer failure or unexplained loss means incomplete evidence and NO_GO for a complete run.
- F6 reads journals/frame/attempt/role JSONL line-by-line instead of imposing the historical
  32 MiB whole-file read limit; these individual records retain a 32 Mi-character ceiling.
  Final scene arrays are written and validated with streaming JSON, without that row-size ceiling.
  A large single-scene diagnostic array also requires streaming or sufficient memory on Desktop.
  Image validation retains the
  existing 20 MiB/JPEG, 12000-pixel dimension and 40-million-pixel limits.
- Existing STOP finalization waits up to 60 s for attempt permits and 60 s for writer termination.
  A timeout is a failure, not a successful export. The HUD trace window remains 5000; persistent
  traces.jsonl/traces.csv contain the durable stream and report statistics retain their own window.
- The existing ZIP writer retains central-directory/hash metadata proportional to file count.
  This is not image/result retention, but device heap and finalization capacity still require
  a real volume test. No 90-minute storage/thermal readiness is inferred from host tests.
- If the camera-space photo bounding rectangle includes a code or clips the photo, NO_GO until
  the Desktop margins/camera alignment are corrected. No live decoder, mask or homography hides it.

## Operator procedure and unperformed acceptance

1. In **Tryb eksperymentalny**, retain the chosen R0, MT-DAY + MZ-DAY, TFLite FP32 and selected
   backend. Select **Wycinek TV (BBOX) — próba nieoficjalna**. Do not retune models or thresholds.
2. Confirm camera/zoom and **Kalibracja TV**. Enable **F6 / FINAL180 — zapis strumieniowy**.
   Open **Obszar fotografii F6**, select four photo corners and confirm. The yellow rectangle
   shows MT's actual floor/ceil crop; all photo pixels must fit inside, all codes outside.
3. Keep the camera/setup fixed. Changed or stale geometry requires an explicit new confirmation.
   Android records actual settings; package/model identity must be checked before the final run.
4. Choose **Timer → Bez timera** for the whole show plus countdown/technical cards. START before
   countdown, run one 180 × 30 s show, STOP after the final card and wait for finalization.
5. Use **Eksport sesji → Pełna paczka .alprsession**. Preserve the original export for Desktop.
   Export/recovery is the existing session path; no network or remote START is added.

Device installation, A12 marker visibility in the saved source, actual marker exclusion from MT,
real TV operation, 90-minute thermal/storage operation and the separate Desktop importer/code
decoder acceptance are **NOT_VERIFIED / NOT_RUN_DEVICE**. Synthetic model calculations and
technical colour patches are not the Desktop marker codec or real FINAL180 data. No GT,
recognition accuracy results, final HUD capture or thesis results are generated by this change.

Host verification and the final inventory follow below; machine-readable evidence is also in
`app/build/f6_final180_20261112/validation.json` and its `audit/` directory.


## Completed host validation and artifacts

Final command (offline, no device install):

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:F6_FIXTURES=Join-Path (Get-Location) 'app\build\f6_final180_20261112\fixtures'
.\gradlew.bat --offline testDebugUnitTest compileDebugAndroidTestJavaWithJavac assembleDebug --console=plain
```

- **1353/1353 JVM PASS**, 0 failures/errors/skips on the final sources; instrumentation Java compilation PASS;
  debug APK assembly PASS. Instrumentation execution on A12: NOT_RUN_DEVICE.
- Final focused producer/F5A set: **66/66 PASS**, including 12 corrupted F6 archive variants rejected.
- Long-session fixture: **4100 frames, 8200 images, 12300 lifecycle events**, synthetic clock exactly 5400 s.
  Lifecycle JSONL: **42,126,956 bytes**; one scene JSON object: **34,608,299 bytes**, with all 4100
  observations read back through streaming JSON. TV working cache <=64 records. This fixture has
  **zero MT calls** deliberately; it tests persistence/finalization/limits, not A12 inference throughput.
- Actual-error, fatal-error (synthetic, no memory exhaustion), late completion, cancellation, no-MZ,
  empty-MZ, excess/duplicate FINAL, missing-image, full-queue, interrupted-START and broken-tail cases pass.
  Logging sink on/off receives identical tensors/model-call counts for the same photo input domain.
- `git diff --check`: PASS. Protected-file SHA audit: **1034/1034 PASS**;
  19 baseline files changed for F6, 7 new source/test/resource/document files. Existing unrelated dirty
  and untracked files remain unchanged. No staging, commit, push, reset/clean, dependency install or APK install.
- Earlier test-development failures were corrected: JSON null identifiers and synthetic MZ expected
  geometry/quality fixtures. The first failing full-run XMLs remain in `audit/first_full_run/`;
  current full-run XMLs are under `app/build/test-results/testDebugUnitTest/`.

Delivery directory: `C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f6_final180_20261112`.

| Artifact | Actual output |
| --- | --- |
| Small Desktop package | `desktop_package.zip`, 19 synthetic producer archives, 659,872 bytes |
| Package SHA-256 | `f80e7e85d72617ffafeb5eb275d045d7a0e8bfea5cf52e88c1ce3dfad9707e0f` |
| Package inventory/checksums | `desktop_package/fixture_index.json`, `desktop_package/SHA256SUMS.txt` |
| Debug APK, copied from Gradle output | `alpr-f6-final180-debug.apk`, 87,155,979 bytes |
| APK SHA-256 | `4c7c2ba4ac0d305ca0b6d4d4ff27c4c7cd18690fe5ff318385a2407ff643fc07` |
| Original APK path | `app/build/outputs/apk/debug/app-debug.apk` |
| Long-session archive (outside small package) | `fixtures/long-session-4100-frames.alprsession` |
| Validation/provenance | `validation.json`, `audit/changes.json`, `audit/changed-files.txt` |

Unpack the **outer delivery ZIP** first, then import its contained `fixtures/*.alprsession` files.
The outer ZIP is a delivery container, not an Android session archive. All session archives come
from the actual ResearchSessionStore finish/recovery and ResearchArchive exporter; the fixture index
is ancillary metadata, not a hand-written substitute for an Android export. Native Android-side
validation plus independent ZIP CRC/entry-SHA validation passed for all 19 packaged archives.
The Desktop agent still needs to test its own importer on this package and its own actual marker codec.

Build identity: versionName 1.0/versionCode 1 remain unchanged; the new APK is distinguished by its
actual SHA, generated built_at_utc, Git HEAD and dirty source inventory. Live Context preparation
also captures installed APK identity; host fixtures do not claim an installed APK or device model identity.

**Status:** ANDROID_HOST=PASS; A12/TV/real 90-minute thermal-storage/Desktop acceptance=NOT_VERIFIED.
**FINAL180 overall=NO_GO until the joint technical acceptance.** No recognition-accuracy or thesis results.

## F6 file inventory relative to the pre-existing dirty worktree

Modified baseline files:

- `app/src/main/java/com/example/alpr_v1/MainActivity.java`
- `app/src/main/java/com/example/alpr_v1/SettingsActivity.java`
- `app/src/main/java/com/example/alpr_v1/experiment/AcquisitionAttemptRecord.java`
- `app/src/main/java/com/example/alpr_v1/experiment/F5aEvidence.java`
- `app/src/main/java/com/example/alpr_v1/experiment/ResearchAttemptBatch.java`
- `app/src/main/java/com/example/alpr_v1/experiment/ResearchExecutionConfig.java`
- `app/src/main/java/com/example/alpr_v1/experiment/ResearchSessionStore.java`
- `app/src/main/java/com/example/alpr_v1/experiment/TvBboxEvidence.java`
- `app/src/main/java/com/example/alpr_v1/experiment/TvBboxInputValidator.java`
- `app/src/main/java/com/example/alpr_v1/metrics/ResearchArchive.java`
- `app/src/main/java/com/example/alpr_v1/pipeline/AlprPipeline.java`
- `app/src/main/java/com/example/alpr_v1/pipeline/MobileAlprEngine.java`
- `app/src/main/java/com/example/alpr_v1/ui/TvCalibrationOverlayView.java`
- `app/src/main/java/com/example/alpr_v1/ui/TvSetupController.java`
- `app/src/main/java/com/example/alpr_v1/ui/TvSetupNavigation.java`
- `app/src/main/res/layout/activity_settings.xml`
- `app/src/test/java/com/example/alpr_v1/experiment/TvBboxProducerFixture.java`
- `app/src/test/java/com/example/alpr_v1/pipeline/TvBboxPipelineTest.java`
- `app/src/test/java/com/example/alpr_v1/ui/TvSetupUiTest.java`

New files:

- `app/src/main/java/com/example/alpr_v1/experiment/ExternalJsonSort.java`
- `app/src/main/java/com/example/alpr_v1/experiment/Final180ArchiveValidator.java`
- `app/src/main/java/com/example/alpr_v1/experiment/Final180Evidence.java`
- `app/src/main/java/com/example/alpr_v1/experiment/Final180PhotoRoi.java`
- `app/src/main/res/values/f6_strings.xml`
- `app/src/test/java/com/example/alpr_v1/experiment/Final180EvidenceTest.java`
- `docs/f6_final180_android_contract_20261112.md`

Split instruction source SHA-256 supplied in the task (not a locally re-read file):
`26570d1a61b318c2e3c0fcda3a81cfd2e32799b5d483bcc9967ec605a36f8fdb`.
