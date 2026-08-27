# Phase 2.1.1 — raport zgodności przed implementacją

Punkt odniesienia audytu:

- gałąź bazowa: `phase2-1-hardening`;
- commit: `0f8b08663ebe97317daa9db0b93189d4d1d669fc`;
- gałąź robocza: `phase2-1-1-hardening`;
- dokument wymagań: `ALPR_phase2_1_1_wytyczne_dla_agenta.md`.

| ID | Wniosek | Status w aktualnym HEAD | Dowód | Decyzja |
|---|---|---|---|---|
| P211-01 | `RAW_MP` otrzymuje trackowane bboxy | `CONFIRMED` | `MobileAlprEngine.detectVehicleRegions()` tworzy surowe `diagnosticVehicles`, po czym nadpisuje je przez `trackedVehicleDetections(...)` przed wywołaniem `selectRawMpCandidates(...)`. | Zachować osobne listy raw/tracked i przenieść wybór polityki do testowalnej warstwy używanej przez engine. |
| P211-02 | background pool zamraża się przy `healthy_tracker` | `CONFIRMED` | Early return po `!mtDecision.runsMt()` następuje przed późniejszą gałęzią wywołującą `refreshPredictedVehicleCache(...)`. | Odświeżać predykcję przed returnem; podczas transformacji kamery jawnie raportować stan frozen. |
| P211-03 | kumulacyjne liczniki są sumowane wielokrotnie | `CONFIRMED` | `recordVehicleTrackingStats()` zapisuje pełny snapshot `VehicleTrackingStats` do każdego trace, a `MetricsCollector` sumuje wszystkie pola z `trace.counters()`. | Raportować delty event counters, duration osobno oraz agregować tylko jawnie sumowalne liczniki; gauges i identyfikatory wyłączyć z sum. |
| P211-04 | `predict()` nie emituje lifecycle events | `CONFIRMED` | `VehicleTrackingCoordinator.updateFromMp()` wywołuje `emitLifecycleEvents(...)`, natomiast `predict()` tylko zapisuje nową ramkę. | Ujednolicić oba przepływy i dodać test wygaśnięcia przez predykcję. |
| P211-05 | encja nie przejmuje istniejącego konsensusu MZ | `CONFIRMED` | `PlateEntityBinder.updateRegistration(...)` jest wywoływane wyłącznie przy `decision.recognize`; `VehicleEntity.updateRegistration()` zawsze zwiększa `mzAttempts`. | Rozdzielić świeżą próbę MZ od adopcji pamięci tracka i chronić lepszy konsensus. |
| P211-06 | `plateTrackId` nie ma jednoznacznego właściciela | `CONFIRMED` | Repozytorium ma mapę tylko dla `vehicleTrackId`; `findByPlateTrackId()` wykonuje liniowy skan, a `attachPlate()` nie wykrywa konfliktu. | Dodać indeks właściciela, bezpieczne attach/reassign, czyszczenie lifecycle oraz eventy wyniku. |

## Dodatkowa bramka Phase 3

`AlprPipeline` posiada współdzielony `VehicleTrackingCoordinator`, ale nie wystawia
publicznego, immutable snapshotu dla przyszłego `AcquisitionQueue`. Należy dodać
testowalne API `latestVehicleTrackingFrame()` bez ujawniania mutable repozytorium.

## Elementy zachowywane

Zmiany pozostają korektą integracji. Nie zastępują koordynatora, track managera,
encji, selektorów ROI, adaptacyjnego TTL, prediction decay, descriptorów,
ograniczonego repozytorium ani istniejących wariantów metodologicznych.
