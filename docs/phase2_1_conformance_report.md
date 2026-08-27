# Phase 2.1 — raport zgodności przed implementacją

Data audytu: 2026-08-28

Gałąź robocza: `phase2-1-hardening`

Commit odniesienia: `a65f645ab1519ad8318dca595481984d1295457d`

Prototyp Phase 3 z commita `8a160bc` pozostaje zachowany na gałęzi `main` i nie
wchodzi do punktu startowego Phase 2.1. Niniejszy raport powstał przed zmianą
zachowania modeli lub trackera.

## Macierz zgodności

| ID | Wniosek z audytu | Status w aktualnym HEAD | Dowód | Decyzja |
|---|---|---|---|---|
| F-01 | `entityId` ginie przed MT | `CONFIRMED` | `MobileAlprEngine.trackedVehicleDetections()` zamienia snapshot na anonimowy `Detection`; `VehicleRoiSelector.Region` nie ma ID | Dodać `VehicleCandidate` i `VehicleRoi`; nie kodować ID w innych polach |
| F-02 | repozytorium encji jest własnością silnika | `CONFIRMED` | prywatne pola `vehicleEntityRepository` i `vehicleTrackManager` w `MobileAlprEngine`; reload tworzy nowy engine | Przenieść własność do `VehicleTrackingCoordinator` w `AlprPipeline` |
| F-03 | brak publicznego `VehicleTrackingFrame` | `CONFIRMED` | `PipelineResult` udostępnia tylko plate observations i overlaye | Dodać immutable frame z timestampami i generacją sceny |
| F-04 | brak powiązania MT z encją | `CONFIRMED` | `PlateObservation` ma wyłącznie techniczny `trackId`; brak full-frame associatora | Dodać association metadata, direct ROI binding i `PlateVehicleAssociator` |
| F-05 | tło zamraża się przy aktywnym celu | `CONFIRMED` | gałąź `anyTargetGeometry` tylko rysuje cache, bez `vehicleTrackManager.predict()` | Predykować tło bez zoomu; podczas transformacji oznaczać je jako frozen |
| F-06 | legacy używa trackowanych bboxów | `CONFIRMED` | `detectVehicleRegions()` zawsze przechodzi przez manager, a następnie buduje ROI | Dodać `VehicleTrackingPolicy`: legacy `RAW_MP`, live `TRACKED_MP` |
| F-07 | timestamp MP oznacza koniec inferencji | `CONFIRMED` | manager otrzymuje `SystemClock.elapsedRealtimeNanos()` po MP, mimo dostępnego timestampu CameraX | Przenieść source timestamp przez engine i korygować Kalman czasem obrazu |
| F-08 | predykowane tracki zachowują priorytet | `CONFIRMED` | `Snapshot.confidence` jest niezmienionym confidence tracka; brak wieku predykcji | Dodać `effectiveConfidence`, prediction age i jawne kary |
| F-09 | podwójna deduplikacja usuwa tracki | `CONFIRMED` | `VehicleRoiSelector.select()` wykonuje NMS przed managerem i ponownie po snapshotach | Rozdzielić raw dedup od rankingu encji bez drugiego NMS |
| F-10 | deskryptor ma zmienny wymiar | `CONFIRMED` | teksturowany crop zwraca 72 wartości, jednolity 3 | Wprowadzić stały `DESCRIPTOR_SIZE = 75` i testy bitmap |
| F-11 | `entityId` resetuje się ze sceną | `CONFIRMED` | `VehicleEntityRepository.resetScene()` ustawia `nextEntityId = 1` | Zachować monotoniczne ID sesji; osobno `sceneGeneration` |
| F-12 | repozytorium rośnie bez limitu | `CONFIRMED` | `EXPIRED` pozostaje w `byEntityId`; `ACQUIRED` jest niewygaszane i nadal aktywne | Rozdzielić active/completed, purge transient i dodać limity |
| F-13 | brak progów i recovery unmatched | `CONFIRMED` | aktywne assignment przyjmuje każdy score `>= 0`; wszystkie active entity blokują dormant recovery | Nazwać progi i dodać drugi etap recovery przed utworzeniem encji |
| F-14 | TTL może być krótszy od odstępu MP | `PARTIALLY_CONFIRMED` | TTL tracka 1,8 s; brak metryk `mp_observation_gap_ms`; historyczny pipeline p95 live 3,4 s | Najpierw dodać telemetrię i pomiar; nie zwiększać TTL bez danych |

## Stan testów przed implementacją

Istniejące testy potwierdzają podstawy: dwa zbliżające się pojazdy, krótką
predykcję, reassociation po wygaśnięciu tracka, limit 16 tracków i techniczną
zmianę track ID. Nie pokrywają jeszcze:

- pełnego crossing ze zmianą kolejności detekcji;
- 1–2 obserwacji occlusion aktywnego tracka;
- zapobiegania duplikatowi przed wygaśnięciem tracka;
- stałego wymiaru deskryptora bitmapowego;
- source timestamp i prediction decay;
- izolacji geometrii ROI legacy;
- przepływu `entityId → ROI → MT → PlateObservation → repository`;
- ograniczonego lifecycle repozytorium.

## Decyzja

Wnioski audytu są aktualne dla commita bazowego. Phase 2.1 jest wymagana przed
ponownym włączeniem prototypu `AcquisitionQueue`. Implementacja będzie wykonana
w commitach A–G zgodnie z handoffem, bez zmiany modeli MP/MT/MZ i bez dokładania
logiki domenowej do `MainActivity`.
