# Phase 2.1.1 — raport odbiorowy

Data odbioru częściowego: 2026-08-28  
Gałąź: `phase2-1-1-hardening`  
Punkt bazowy: `0f8b08663ebe97317daa9db0b93189d4d1d669fc`

## Commity

| Commit | Zakres |
|---|---|
| `05150e6` | Realne rozdzielenie wejścia `RAW_MP` i `TRACKED_MP`; macierz zgodności przed implementacją |
| `3079c25` | Predykcja background pool przed early return `healthy_tracker`; unieważnienie puli po transformacji kamery |
| `4bd392f` | Delty liczników, gauges, duration trackingu oraz niesumowalne timestampy i identyfikatory |
| `e532bcd` | Eventy wygaśnięcia tracku i encji emitowane również przez `predict()` |
| `0df88fb` | Unikalny właściciel `plateTrackId`, kontrolowany reassignment, adopcja konsensusu i publiczny snapshot Phase 3 |

## Wyniki problemów

| ID | Wynik | Dowód |
|---|---|---|
| P211-01 | `RESOLVED` | `VehicleRoiSelector.selectForPolicy()` jest granicą używaną przez realny engine; test porównuje rozbieżne bboxy raw/tracked. |
| P211-02 | `RESOLVED` | Early return przy pominięciu MT odświeża predykcję background pool; test potwierdza ruch drugiej encji bez nowego MP. |
| P211-03 | `RESOLVED` | `VehicleTrackingStats.deltaSince()`, `statsDelta()`, osobne `summary.gauges` oraz filtr pól niesumowalnych; test `2,2,3 → 2,0,1 → 3`. |
| P211-04 | `RESOLVED` | `predict()` wykonuje `emitLifecycleEvents()`; test sprawdza pojedyncze eventy track/entity expiration i brak duplikatów. |
| P211-05 | `RESOLVED` | `TRACK_MEMORY` aktualizuje encję bez zwiększenia `mzAttempts`; stabilny lepszy wynik nie jest zastępowany słabszym. |
| P211-06 | `RESOLVED` | Indeks `plateTrackId → entityId`, konflikt reject, jawny reassignment, czyszczenie przy acquire/expire/reset oraz lookup O(1). |

## Publiczna bramka Phase 3

`AlprPipeline.latestVehicleTrackingFrame()` zwraca immutable
`VehicleTrackingFrame`. Kandydaci zawierają:

- `entityId` i `vehicleTrackId`;
- `bounds`, confidence i `exitUrgency`;
- wiek predykcji;
- aktualny `EntityAcquisitionState`.

API nie ujawnia mutable repozytorium warstwie UI.

## Testy automatyczne

| Kontrola | Wynik |
|---|---|
| JVM — `testDebugUnitTest` | `PASS` — 182/182, 0 failures, 0 skipped |
| lint — `lintDebug` | `PASS` — 0 errors; 8 istniejących warnings (`UnusedResources`, `SmallSp`) |
| `assembleDebug` | `PASS` |
| `assembleDebugAndroidTest` | `PASS` |
| connected tests — SM-A125F / Android 12 | `PASS` — 16/16, 0 failures, 0 skipped |
| `git diff --check` | `PASS` |

## Smoke test na SM-A125F

| Scenariusz | Wynik | Uwagi |
|---|---|---|
| Legacy raw ROI — jeden poruszający się pojazd | `PASS` | `EXPERIMENT_LEGACY`, R2, `legacy_burst`, `same_cycle`; kolejne surowe bboxy MP i ROI, bez crasha/ANR. |
| Live tracked ROI — jeden poruszający się pojazd | `PASS` | `USER_LIVE`, `live_staggered`, `deferred`; lock oraz klatki `NO_MT_RUN`. |
| Active target + drugi pojazd | `PASS` | MP raportował 2–3 pojazdy, dwa ROI; występowały `LOCKED/ACQUIRED` oraz `NO_MT_RUN`; aplikacja stabilna. |
| Zoom: frozen → fresh MP → rebuilt pool | `NOT RUN` | AZ włączony, lecz użyta ruchoma scena nie spełniła bramki stabilnej geometrii i fizyczny zoom nie został wyzwolony. Test należy powtórzyć na nieruchomym zdjęciu z wyraźną tablicą. |
| Długi przebieg minimum 10 minut | `NOT RUN` | Odłożony do kolejnej sesji. |

## Pozostałe czynności odbiorowe

1. Wyświetlić nieruchome zdjęcie jednego centralnego pojazdu z wyraźną, małą tablicą.
2. Włączyć AZ i potwierdzić fizyczny zoom, stan frozen, fresh MP po settle/return i odbudowę puli.
3. Wykonać co najmniej 10-minutowy przebieg live.
4. Sprawdzić eksport `report.json` i `events.jsonl`: prawdziwe sumy delt, gauges oraz kompletność expiration events.

## Decyzja

```text
NO-GO do Phase 3 — wyłącznie do czasu wykonania dwóch pozostałych smoke testów.
```

Implementacja P211-01–P211-06 i bramki API Phase 3 jest kompletna. Decyzja nie
wynika z otwartego błędu kodu, tylko z niewykonanej pełnej walidacji zoomu i
długiego przebiegu. Po ich zaliczeniu raport może zostać zmieniony na `GO` bez
przebudowy architektury Phase 2.1.1.
