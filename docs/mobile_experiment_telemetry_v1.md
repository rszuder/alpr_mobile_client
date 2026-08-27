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
| `app_build.build_type` | string | tak dla starych | debug/release/research |
| `data_retention.trace_total_seen` | int | tak dla starych | Wszystkie utworzone trace'y |
| `data_retention.trace_records_retained` | int | tak dla starych | Trace'y obecne w eksporcie |
| `data_retention.trace_records_evicted` | int | tak dla starych | Usunięte najstarsze trace'y |
| `data_completeness.status` | string | tak dla starych | `complete` albo `incomplete` |

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
| `frames_skipped_gate` | int | klatki | Pominięcia AdaptiveFrameGate |
| `frames_skipped_camera_transform` | int | klatki | Pominięcia transformacji/zoomu |
| `frames_skipped_scene_change` | int | klatki | Klatka przeznaczona na szybkie unieważnienie starej sceny |
| `estimated_upstream_gaps` | int | klatki | Estymacja z timestampów CameraX |

`estimated_upstream_gaps` nie może być prezentowane jako bezpośrednio zmierzone
`CameraX dropped_frames`.

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
`auto_zoom_return_started` i `zoom_finished`.

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
