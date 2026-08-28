# Phase 2.1.1 — raport odbiorowy

Data odbioru końcowego: 2026-08-28
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
| `80a0e91` | Bezpieczne wejście małej, zablokowanej tablicy w zoom rescue po pierwszym świeżym MZ |

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
| JVM — `testDebugUnitTest` | `PASS` — 183/183, 0 failures, 0 skipped |
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
| Zoom: frozen → fresh MP → rebuilt pool | `PASS` | Nieruchoma scena: `REQUEST_ZOOM reason=small_plate`, fizyczny zoom `1.00× → 1.79×`, fresh MZ `0.795 → 0.910`, `confirmed=true`, 2 obserwacje, `RETURN_NORMAL`, powrót do tej samej sceny i świeże cykle MP po settle. |
| Długi przebieg minimum 10 minut | `PASS` | 608 s; PID `17116` nie zmienił się, brak crasha/ANR; PSS nie narastał (`376376 kB → 337467 kB`). |

## Dodatkowy wynik smoke testu AZ

Pierwsze próby ujawniły, że realny wolny pipeline może zmienić techniczny
`plateTrackId` przed drugą próbą MZ. Bramka wymagająca bezwarunkowo dwóch
obserwacji blokowała wtedy zoom mimo stanu `LOCKED`, poprawnego quadu i świeżego
odczytu bardzo małej tablicy. Poprawka `80a0e91` dopuszcza pierwszą obserwację
wyłącznie wtedy, gdy geometria celu ma potwierdzony stan `LOCKED`, quad jest
poprawny, MZ rzeczywiście wykonano z wynikiem i tablica spełnia próg
`small_plate`. Test zachowuje dotychczasowe odrzucanie niestabilnego celu.

Semantyka eksportu telemetrii jest pokryta testami automatycznymi: delty
`2,0,1` sumują się do `3`, gauges oraz timestampy/identyfikatory nie trafiają do
sum liczników, a expiration events z `predict()` są emitowane jednokrotnie.

## Decyzja

```text
GO do Phase 3
```

Implementacja P211-01–P211-06, publiczna bramka API Phase 3, pełna walidacja
zoomu oraz długi przebieg urządzeniowy są kompletne. Można rozpocząć Phase 3 —
`Scan–Acquire` bez autozoomu.
