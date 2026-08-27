# Phase 2.1 — raport końcowy

Data odbioru: 2026-08-28

Gałąź: `phase2-1-hardening`

Punkt bazowy: `a65f645` (`Add Phase 2 multi-vehicle tracking`)

## Commity

| Commit | Zakres |
|---|---|
| `1d4bdfe` | raport zgodności, `VehicleCandidate`, `VehicleRoi`, `VehicleTrackingFrame`, status association |
| `a5bd2e1` | `VehicleTrackingCoordinator` i własność domeny poza model engine |
| `97384a0` | entity-aware ROI, jawne dane MT, full-frame associator i przypięcie plate/MZ do encji |
| `6978241` | `RAW_MP` dla legacy i `TRACKED_MP` dla live |
| `1617760` | source timestamp, predykcja do czasu wyniku, prediction decay i tło focused targetu |
| `8e8e9a3` | deskryptor 75D, progi association, recovery, crossing i occlusion |
| `b7ec792` | monotoniczne ID, bounded repository, summaries, telemetry i events |
| `238aaa4` | adaptacyjny TTL na podstawie zmierzonego odstępu MP |

## Wynik F-01–F-14

| ID | Wynik | Rozwiązanie/dowód |
|---|---|---|
| F-01 | `RESOLVED` | `VehicleCandidate.entityId → VehicleRoi.entityId`; brak kodowania ID w `Detection` lub etykiecie |
| F-02 | `RESOLVED` | repozytorium i manager należą do koordynatora w `AlprPipeline`; reload engine zachowuje stan |
| F-03 | `RESOLVED` | publiczny immutable `VehicleTrackingFrame` z frame ID, timestampami i scene generation |
| F-04 | `RESOLVED` | `PlateObservation` zawiera encję, trzy track ID, status, confidence, reason i źródło MT; test A/B aktualizuje tylko B |
| F-05 | `RESOLVED` | tło jest predykowane przy focused target bez zoomu; podczas transformacji ma `FROZEN_BY_CAMERA_TRANSFORM`; zoom czyści cache MP |
| F-06 | `RESOLVED` | legacy wybiera historyczny surowy bbox; live wybiera bbox encjowy; test porównuje piksele obu ROI |
| F-07 | `RESOLVED` | Kalman jest korygowany timestampem CameraX, wynik jest przewidywany do `resultAvailableNanos` |
| F-08 | `RESOLVED` | osobne detection/effective confidence; kara wieku, miss 1/2/3 = 0,75/0,50/0,25 i motion penalty |
| F-09 | `RESOLVED` | tracked selector nie wykonuje NMS; nachodzące encje zachowują osobne ROI |
| F-10 | `RESOLVED` | każdy poprawny crop daje 75 wartości: 72 spatial RGB + 3 ważone średnie; brak NaN |
| F-11 | `RESOLVED` | `entityId` nie resetuje się ze sceną; `sceneGeneration` jest osobne |
| F-12 | `RESOLVED` | maks. 64 aktywne encje, 256 completed summaries, expired transient jest usuwany |
| F-13 | `RESOLVED` | nazwane progi, association margin i drugi etap active recovery przed utworzeniem encji |
| F-14 | `RESOLVED_WITH_MEASUREMENT` | pomiar SM-A125F: n=16, gap p50=2560 ms, p95/p99=2592 ms; TTL adaptuje się 3,0–4,9 s według `gap × 1,75` |

## Przepływ `entityId`

```text
CameraX frame + sourceTimestamp
  → raw MP detections
  → raw deduplication
  → VehicleTrackingCoordinator / VehicleTrackManager
  → VehicleTrackingFrame(sceneGeneration)
  → VehicleCandidate(entityId, vehicleTrackId)
  → VehicleRoi(entityId, RAW_MP albo TRACKED_MP)
  → MT
  → DIRECT_ROI / ASSOCIATED_FULL_FRAME / AMBIGUOUS / UNASSIGNED
  → PlateObservation(entityId, vehicleTrackId, plateTrackId)
  → VehicleEntityRepository.attachPlate(entityId)
  → VehicleEntityRepository.updateRegistration(entityId)
```

R0 bez MP nie tworzy sztucznej encji. Full-frame associator zwraca jawne
`UNASSIGNED` lub `AMBIGUOUS`, gdy wynik geometrii jest za słaby albo margines
między dwoma pojazdami jest mniejszy niż próg.

## Legacy i live

| Tryb | Geometria pojazdu | Wykonanie MT | Fallback |
|---|---|---|---|
| `EXPERIMENT_LEGACY` | `RAW_MP` | `LEGACY_BURST` | `SAME_CYCLE` |
| `USER_LIVE` | `TRACKED_MP` | maks. 1 MT/cykl | `DEFERRED` |
| R0 | brak MP, full-frame MT | zgodnie z trybem | association albo `UNASSIGNED` |

Tracker może działać diagnostycznie w legacy, ale nie zmienia bboxa ROI.

## Telemetria

Trace zawiera między innymi:

- `vehicle_tracking_policy`;
- liczbę aktywnych tracków i encji;
- utworzenia, wygaśnięcia, reassociation i duplicate prevention;
- unmatched observations oraz capacity drops;
- `mp_observation_gap_ms`, `vehicle_track_ttl_ms`;
- prediction age, effective confidence i czas trackera;
- encję wybranego ROI;
- status, confidence i reason association tablicy.

Do `events.jsonl` trafiają ograniczoną kolejką:

- `vehicle_track_created/expired`;
- `vehicle_entity_created/reassociated/expired`;
- `vehicle_roi_scheduled`;
- `plate_attached_to_entity`;
- `plate_entity_association_ambiguous/failed`.

## Wyniki testów

| Kontrola | Wynik |
|---|---:|
| JVM `testDebugUnitTest` | 172/172, 0 failures, 0 errors |
| `lintDebug` | PASS, brak Error/Fatal |
| `assembleDebug` | PASS, oba ABI |
| `assembleDebugAndroidTest` | PASS |
| SM-A125F `connectedDebugAndroidTest` | 16/16 |
| `git diff --check` | PASS |

Testy obejmują identity propagation, crossing z odwróconą kolejnością,
occlusion przez dwie obserwacje, recovery bez duplikatu, stały deskryptor,
timestamp, prediction decay, raw/tracked isolation i bounded lifecycle.

## Smoke test SM-A125F

| Scenariusz | Wynik | Uwagi |
|---|---|---|
| uruchomienie z realnym pakietem MP/MT/MZ | PASS | MP TFLite FP32, MT/MZ ONNX INT8 |
| live, kilka pojazdów | PASS | logi pokazały 2–3 pojazdy i maks. jeden ROI MT/cykl |
| deferred fallback live | PASS | full-frame następował w następnym cyklu |
| legacy R1/R2 semantics | PASS | `legacy_burst`, `same_cycle`, ROI + full frame |
| crash/ANR podczas krótkich prób | PASS | brak FATAL EXCEPTION |
| crossing rzeczywistych pojazdów | NOT RUN | pokryte deterministycznym testem JVM |
| aktywny lock z drugim pojazdem i zoom-in/out | NOT RUN | wymaga kontrolowanej sceny fizycznej |
| ciągła sesja 10 minut | NOT RUN | wymaga osobnej sesji badawczej i eksportu |

Po testach urządzeniowych pakiet modeli został ponownie zaimportowany i
zweryfikowany: `files/models` 17 MB, `files/alpr-packages` 13 MB. Normalna
kaskada pozostała włączona, eksperyment wyłączony, profil eksperymentu R2.

## Znane ograniczenia

1. Full-frame association jest celowo geometryczne; Phase 2.1 nie dodaje
   ciężkiego ReID i w niejednoznacznej scenie zwraca `AMBIGUOUS`.
2. Pomiar odstępu MP pochodzi z krótkiej próbki jednego SM-A125F; właściwa
   kampania musi zebrać p50/p95/p99 dla profili termicznych i dłuższych sesji.
3. Completed summaries są ograniczone i lekkie, ale nadal tylko pamięciowe;
   trwały zapis należy do późniejszego mechanizmu acquisition/export.
4. Prototyp Phase 3 pozostaje na gałęzi `main` w commicie `8a160bc` i nie może
   zostać scalony bezpośrednio. Należy przenieść jego logikę na nowe kontrakty
   `VehicleRoi` i `PlateObservation`, usuwając wcześniejsze side-channel ID.

## Decyzja o Phase 3

**GO, z warunkiem portowania prototypu.** Bramka wymagana przez handoff jest
spełniona i pokryta testem A/B. Można ponownie rozpocząć `Scan–Acquire`, ale na
gałęzi utwardzonej i bez przywracania anonimowego przepływu ROI/MT z `8a160bc`.
