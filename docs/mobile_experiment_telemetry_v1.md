# Kontrakt telemetrii eksperymentalnej v1

Rozszerzenie `alpr.mobile_experiment_telemetry.v1` jest addytywne względem
`alpr.mobile_benchmark_report.v1`. Stare pola, `report.json`, `traces.csv` i
formaty archiwów zachowują dotychczasową semantykę.

## Reguły wspólne

- Brak pomiaru oznacza null, pustą komórkę CSV albo brak klucza; nie zero.
- UTC identyfikuje rekord, a `elapsed_ms` synchronizuje szeregi i mierzy czas.
- Wartości mają jednostkę w nazwie (`_ms`, `_ns`, `_px`, `_ratio`, `_c`,
  `_bytes`, `_kb`). Ratio ma zakres 0–1.
- Android eksportuje dane surowe. Desktop może niezależnie przeliczać summary.
- `confirmed` pozostaje aliasem historycznym stabilności tracku. Nie jest ground
  truth; nowe analizy używają `track_confirmed` i `human_verification` osobno.
- Raport źródłowy jest immutable. Analizy i adnotacje Desktopu są przechowywane
  poza archiwum wejściowym.

## SESSION — `report.json`

| Pole | Typ | Nullable | Źródło / semantyka |
| --- | --- | --- | --- |
| `report_id` | string | nie | Identyfikator raportu |
| `experiment.session.id` | string | tak | Jeden przebieg eksperymentu |
| `experiment.series_id` | string | tak dla starych | Seria porównawcza |
| `experiment.scenario_id` | string | tak dla starych | Kontrolowany scenariusz |
| `experiment.variant` | string | tak | Główna zmienna niezależna |
| `experiment.replicate_index` | int >= 1 | tak dla starych | Numer powtórzenia |
| `app_build.git_commit` | string | tak dla starych | SHA źródeł aplikacji |
| `app_build.git_dirty` | bool | tak dla starych | Czy APK zbudowano z modyfikacjami poza wskazanym SHA |
| `app_build.source_state` | string | tak dla starych | `clean`, `dirty` albo `unknown` |
| `app_build.build_type` | string | tak dla starych | debug/release/research |
| `data_retention.trace_total_seen` | int | tak dla starych | Wszystkie utworzone trace'y |
| `data_retention.trace_records_retained` | int | tak dla starych | Trace'y obecne w eksporcie |
| `data_retention.trace_records_evicted` | int | tak dla starych | Usunięte najstarsze trace'y |
| `data_completeness.status` | string | tak dla starych | `complete` albo `incomplete` |
| `experiment.effective_execution_config` | object | tak dla starych | Zamrożona efektywna konfiguracja MP/MT/MZ, ROI, rozdzielczości i flag |
| `errors.crash_measurement_available` | bool | nie | Czy działa trwały marker niedomkniętej sesji |
| `errors.crash_count` | int/null | nie | Odzyskane niedomknięte sesje poprzedniego procesu; null bez pomiaru |

Konfiguracja eksperymentu jest zamrażana przy starcie. Zmiana ustawień po stopie
nie może zmienić identyfikacji zakończonego przebiegu.

## FRAME — `traces.csv`

Jeden rekord odpowiada przetworzonej klatce. Kolumny timingowe są w ms. CSV
zawiera pełne etapy pipeline'u, timing-audit, confidence, scenę, ROI/scheduler,
autozoom/lock i próbki pamięci. Niewykonany etap ma pustą komórkę.

Klucz: `frame_id`. Synchronizacja z pozostałymi szeregami: `elapsed_ms`.

## FRAME_FLOW — `frame_flow.csv`

| Pole | Typ | Jednostka | Semantyka |
| --- | --- | --- | --- |
| `experiment_session_id` | string | — | Klucz sesji |
| `elapsed_ms` | int | ms | Początek bucketu 1 s |
| `frames_received` | int | klatki | Wejścia do analizatora |
| `frames_processed` | int | klatki | Pełne `InferenceTrace` |
| `frames_skipped_frame_gate` | int | klatki | Pominięcia AdaptiveFrameGate |
| `frames_skipped_camera_transform` | int | klatki | Pominięcia transformacji/zoomu |
| `frames_skipped_hard_scene_reset` | int | klatki | Pominięcia po skoordynowanym `HARD_RESET` |
| `frames_skipped_continuity_hold` | int | klatki | Pominięcia podczas `MOTION_HOLD` |
| `frames_skipped_continuity_reacquire` | int | klatki | Pominięcia podczas `REACQUIRING` |
| `estimated_upstream_gaps` | int | klatki | Estymacja z timestampów CameraX |

`estimated_upstream_gaps` nie może być prezentowane jako bezpośrednio zmierzone
`CameraX dropped_frames`.

## FINAL_RESULT_DISPATCH — `report.json`

| Pole | Typ | Jednostka | Semantyka |
| --- | --- | --- | --- |
| `final_results_dropped_after_return` | int | wyniki | Stary wynik odrzucony po powrocie z pipeline lub przed telemetrią rozpoznania |
| `final_results_dropped_before_ui` | int | wyniki | Stary wynik odrzucony na głównym wątku lub przez defensywny guard prezentacji |
| `final_results_dropped_before_crop` | int | wyniki | Stary wynik odrzucony bezpośrednio przed zapisem cropa |
| `final_result_dispatch_accepted` | int | wyniki | Aktualny wynik przekazany do UI albo ścieżki crop-only |

Odrzucenie końcowego wyniku ma osobny event
`final_pipeline_result_dropped`. Event zawiera `final_result_drop_phase`,
pełny stamp wyniku, pełny aktualny stamp pipeline oraz
`source_timestamp_nanos`. Nie jest grupowany ze stale intermediate MT.

## RECOVERY — domeny czasu

Recovery zapisuje dwie niezależne osie czasu:

| Pole | Domena | Zastosowanie |
| --- | --- | --- |
| `reacquire_started_runtime_nanos` | `elapsed_realtime_nanos` | deadline, duration i cooldown |
| `reacquire_trigger_source_sequence` | monotoniczna sekwencja CameraX | podstawowa kolejność klatek oraz fresh MP/MT |
| `reacquire_trigger_source_timestamp_nanos` | domena wskazana przez `source_timestamp_domain` | dodatkowy dowód czasu klatki |
| `runtime_timestamp_domain` | string | jawna nazwa domeny runtime |
| `source_timestamp_domain` | string | jawna nazwa domeny źródłowej |

`CAMERAX_SENSOR` i `PREVIEW_INHERITED_CAMERA` należą do tej samej osi kamery;
`RUNTIME_UPTIME` i `UNKNOWN` nie są z nią porównywane dla freshness. Czas projekcji
`VehicleTrackingFrame.snapshotTimestampNanos` pozostaje w domenie źródłowej;
czas eventu trackingu jest przekazywany oddzielnie w domenie runtime.

Każdy rzeczywisty `ImageProxy` otrzymuje monotoniczne `source_sequence` przed
rozgałęzieniem na direct-luma i ciężki pipeline. Preview oraz autozoom dziedziczą
ostatni CameraX stamp jako `PREVIEW_INHERITED_CAMERA`; nie tworzą source time z
zegara runtime. `camera_timestamp_source` zapisuje `REALTIME`, `UNKNOWN` albo
`UNAVAILABLE`. Przy `UNKNOWN` freshness nadal opiera się na `source_sequence`.

Terminalny wynik recovery jest zapisywany dokładnie raz jako event
`terminal_recovery_applied`. Event zawiera `terminal_recovery_result`,
`terminal_recovery_reason`, zastosowaną akcję koordynatora oraz flagi
`abort_current_frame` i `request_immediate_frame`. Decyzja jest stosowana
synchronicznie w klatce, która wytworzyła wynik. `ACTIVE_TARGET_LOST`,
`VEHICLE_POOL_RECOVERED` i `FAILED` przerywają ją przed dalszym MT/MZ;
`TARGET_RECOVERED` może kontynuować MZ tej samej, świeżo rewalidowanej encji.

## SCAN_ACQUISITION — `report.json`, `traces.csv`, `events.jsonl`

Raport `scan_acquisition` rozdziela czas ścienny runu od aktywnego czasu
przetwarzania i zawiera: `vehicles_seen`, `vehicles_queued`,
`vehicles_selected`, `vehicles_deferred`, `vehicles_lost`,
`entities_ready_to_finalize`, średnie i p95 czasu oczekiwania oraz sesji,
a także liczbę prób MT/MZ na wybraną encję.

`READY_TO_FINALIZE` uruchamia teraz atomową warstwę finalizacji. Powstaje
`AcquisitionRecord`, po czym tekst po normalizacji `[A-Z0-9]` jest sprawdzany
w deduplikatorze bieżącego `scan_run_id`. Pierwszy wynik jest unikalnym zapisem;
każdy następny o tym samym tekście pozostaje rekordem audytowym ze wskazaniem
`duplicate_of_record_id`, ale nie zwiększa przepustowości unikalnych tablic.

Summary zawiera:

- `acquisitions_finalized`;
- `unique_plates_saved`;
- `duplicate_acquisitions_suppressed`;
- `duplicate_capture_rate`;
- `mean_acquisition_ms` i `p95_acquisition_ms`;
- `unique_plates_per_wall_minute`.

`acquisition_records[]` zachowuje run, sesję, encję, track tablicy, tekst,
confidence, obserwacje konsensusu, czasy, decyzję deduplikacji oraz
`best_crop_id`. Identyfikator cropu wskazuje najlepszą obserwację pipeline'u;
nie oznacza automatycznego pliku JPEG poza archiwum badawczym.

Każda klatka zapisuje stan runu, rozmiar kolejki, aktywne `session_id` i
`entity_id`, dyrektywę, budżety prób oraz czasy aktywne. Eventy kolejki i sesji
używają typów `candidate_queued`, `candidate_updated`, `candidate_selected`,
`candidate_deferred`, `candidate_expired`, `scan_session_started`,
`scan_session_progress`, `scan_session_ready_to_finalize`,
`scan_session_lost`, `scan_session_deferred` i `scan_target_released`.
Finalizacja dodaje `acquisition_finalized` oraz dokładnie jedno z
`unique_plate_saved` / `duplicate_acquisition_suppressed`.
Ranking zawiera `priority`, `readability`, `waiting_age`, `exit_urgency`,
`freshness` oraz `prediction_age_ms`.

## SECONDARY_SCENE_PREFLIGHT — `report.json`

Pełnobitmapowy detektor obrazu po rotacji działa przed `MobileAlprEngine.run()`.
Raport zawiera:

```text
secondary_scene_preflight_detected
secondary_scene_preflight_holds
secondary_scene_preflight_reacquires
secondary_scene_preflight_hard_resets
secondary_scene_preflight_skipped_inference
```

Event `secondary_scene_preflight` zapisuje `secondary_scene_preflight_action`,
`secondary_scene_preflight_skipped_inference`, score, changed fraction i zmianę
jasności. Wykrycie skutkujące hold/reacquire/resetem nie może uruchomić
MP/MT/MZ dla tej samej klatki.

## PREVIEW_DECISION_AUTHORITY — `report.json`

```text
preview_decision_authority = coordinator / legacy_fallback / unavailable
preview_coordinator_decisions
legacy_preview_fallbacks
```

Jeżeli pipeline zwrócił `SceneTransitionDecision`, UI nie interpretuje już
samodzielnie flagi `changed` ani utraty lokalnego trackera. `legacy_fallback`
jest dopuszczalny wyłącznie przy braku decyzji, np. przed inicjalizacją
pipeline'u.

## THERMAL — `thermal.csv`

Próbkowanie około 1 Hz, niezależne od processed FPS.

| Pole | Typ | Jednostka | Nullable |
| --- | --- | --- | --- |
| `experiment_session_id` | string | — | tak poza EXP |
| `elapsed_ms` | int | ms | nie |
| `battery_temperature_c` | number | °C | tak |
| `thermal_status` | int | Android status | tak |
| `thermal_headroom` | number | ratio/status API | tak |
| `headroom_available` | bool | — | nie |
| `battery_percent` | int | 0–100 | tak |
| `charging` | bool | — | nie |
| `available_memory_bytes` | int | bytes | tak |

## EVENT — `events.jsonl`

Każda linia jest niezależnym obiektem JSON. Wspólne pola:
`experiment_session_id`, `event_seq`, `elapsed_ms`, opcjonalne `frame_id` i
`track_id`, oraz `event_type`.

Obsługiwane typy obejmują: `track_created`, `track_lost`, `mz_attempt`,
`mz_result`, `consensus_updated`, `consensus_confirmed`, `scene_reset`,
`auto_zoom_started`, `lock_acquired`, `lock_lost`, `mz_retry_after_zoom`,
`auto_zoom_return_started`, `zoom_finished` i `final_pipeline_result_dropped`.

## TRACK/CROP — `samples/*` i `crop_session.records`

Rekord zachowuje `session_id + track_id + capture_id`, ground truth, confidence,
czasy, `track_confirmed`, wynik świeżej próby MZ, numer próby, observations,
layout/row counts, zoom/source oraz:

- bbox tablicy w pikselach źródła;
- `plate_bbox_area_ratio` i `plate_quad_area_ratio` w zakresie 0–1;
- cztery `plate_corners_norm` w kolejności TL, TR, BR, BL;
- luminancję, odchylenie luminancji oraz under/overexposed ratio;
- `image_difficulty.computation_ms` do audytu narzutu telemetrii.

## Integralność

`manifest.json` jest ostatnim wpisem `.alprsession`. `entry_sha256` obejmuje
również `thermal.csv`, `frame_flow.csv` i `events.jsonl`. Importer musi odrzucać
niezgodne hashe, duplikaty wpisów, ścieżki absolutne i `..`, ale tolerować
nieznane pola przyszłych wersji.

## Phase 2.1.1 — semantyka telemetrii pojazdów

| Pole | Typ | Jednostka | Agregacja w summary |
| --- | --- | --- | --- |
| `vehicle_tracks_created` | event counter delta | zdarzenia | `SUM OF DELTAS` |
| `vehicle_tracks_expired` | event counter delta | zdarzenia | `SUM OF DELTAS` |
| `vehicle_entities_created` | event counter delta | zdarzenia | `SUM OF DELTAS` |
| `vehicle_entities_expired` | event counter delta | zdarzenia | `SUM OF DELTAS` |
| `vehicle_entity_reassociations` | event counter delta | zdarzenia | `SUM OF DELTAS` |
| `vehicle_entity_duplicate_preventions` | event counter delta | zdarzenia | `SUM OF DELTAS` |
| `vehicle_observations_unmatched` | event counter delta | zdarzenia | `SUM OF DELTAS` |
| `vehicle_candidates_dropped_capacity` | event counter delta | zdarzenia | `SUM OF DELTAS` |
| `vehicle_tracks_active` | gauge | encje | `LAST / MAX / DISTRIBUTION` |
| `vehicle_entities_active` | gauge | encje | `LAST / MAX / DISTRIBUTION` |
| `vehicle_tracks_predicted` | gauge | encje | `LAST / MAX / DISTRIBUTION` |
| `vehicle_tracking` | duration | ns w trace | `p50 / p90 / p95` |

Pola identyfikacyjne i zegary monotoniczne (`source_timestamp_nanos`,
`processing_started_nanos`, `result_available_nanos`, `vehicle_roi_entity_id`,
`vehicle_roi_track_id`, `locked_track_id`) są atrybutami trace. Nie trafiają do
`summary.counters` i nie są sumowane.
