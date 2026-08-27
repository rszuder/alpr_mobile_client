# Baseline stabilizacyjny `lock v1`

Data checkpointu: 2026-08-27  
Commit bazowy: `e2beed316473785dddf3ba0fbb0094e471601cf9` (`lock v1`)  
Urządzenie: Samsung SM-A125F, Android 12

## Status budowy

- `testDebugUnitTest`: 130/130 testów, 0 błędów, 0 pominiętych;
- `lintDebug`: sukces;
- `assembleDebug`: sukces;
- `assembleDebugAndroidTest`: sukces;
- `connectedDebugAndroidTest`: 16/16 testów na SM-A125F, 0 błędów.

## Odtwarzalne polityki MT

| Profil | ROI | Wykonanie | Fallback | Inwariant |
|---|---|---|---|---|
| `EXPERIMENT_LEGACY` | R0/R1/R2 | `LEGACY_BURST` | `SAME_CYCLE` | zachowanie historycznych eksperymentów |
| `USER_LIVE` | R0 albo aktywna kaskada | `LIVE_STAGGERED` | `DEFERRED` | maksymalnie 1 MT/cykl |

Każdy trace zapisuje `mt_execution_policy`, `mt_fallback_policy`,
`mt_scheduler_reason` i `mt_runs_this_frame`.

## Ciągłość MZ

- `NO_MT_RUN` nie zmienia temporalnego konsensusu;
- pojedynczy `MT_RUN_WITHOUT_DETECTIONS` zachowuje konsensus;
- stan bez obserwacji ma TTL `2500 ms`;
- `SCENE_RESET` nadal czyści stan natychmiast;
- trace zapisuje `mz_state_event`.

## Smoke test funkcjonalny

Zweryfikowane we wcześniejszych przebiegach na SM-A125F:

- osobna ścieżka `LumaFrame` działa podczas ciężkiej inferencji;
- KLT/affine/Kalman aktualizuje overlay pomiędzy MT;
- scheduler live wykonuje 0/1 MT na cykl;
- target ROI, deferred fallback i re-anchor działają;
- zmiana sceny unieważnia stare wyniki;
- sticky lock nie przełącza celu bez stanu utraty;
- autozoom korzysta z bieżącej geometrii targetu.

## Kontrolowany koszt polityk MT

Scena: dwa identyczne rzeczywiste pojazdy wyświetlone obok siebie, stały kadr,
SM-A125F, pakiet INT8 MP/MT/MZ. Tablice w tej scenie były celowo małe, dlatego
seria mierzy koszt wykonawczy polityk, a nie skuteczność OCR ani locka.

| Metryka | USER_LIVE | LEGACY R0 | LEGACY R1 | LEGACY R2 |
|---|---:|---:|---:|---:|
| liczba próbek | 14 | 21 | 7 | 5 |
| MT/cykl | 1 | 1 | 2 | 3 |
| pipeline p50 [ms] | 1764,3 | 972,3 | 3620,9 | 4500,8 |
| pipeline p95 [ms] | 3422,3 | 1055,4 | 5958,0 | 7027,7 |
| końcowy PSS [KB] | 314206 | 188839 | 280638 | 285471 |
| thermal status | 0 | 0 | 0 | 1 |
| AP / BAT [°C] | 49,1 / 33,6 | 50,0 / 34,0 | 51,4 / 33,5 | 51,4 / 34,5 |

R1 wykonało `ROI + full frame`, natomiast R2 wykonało `2 × ROI + full frame`.
Potwierdza to odtworzenie historycznej polityki burst/same-cycle. USER_LIVE nie
przekroczył jednego MT na cykl.

## Kontrolowany lock USER_LIVE

Scena: pełnoekranowy obraz `577WK_WE911GT_001.jpg`, ten sam telefon i pakiet
modeli. Pomiary po pierwszej detekcji MT:

| Metryka | Wynik |
|---|---:|
| próbki trackera | 398 |
| tracker FPS | 14,64 |
| overlay FPS p50 / p95 | 14,95 / 31,00 |
| tracker update p50 / p95 [ms] | 33,326 / 49,585 |
| tracking quality p50 / p05 | 0,894 / 0,821 |
| inliers p50 / p05 | 8 / 7 |
| pierwszy lock | 3 aktualizacje / 376 ms |
| kolejne recovery lock | 3 aktualizacje / 103–113 ms |
| lock losses w 32 s | 3 |
| MT skip ratio | 1/18 = 5,6% |
| pipeline p50 / p95 [ms] | 1055,1 / 3581,8 |
| końcowy PSS [KB] | 335354 |
| thermal status, AP / BAT | 0, 50,0°C / 34,1°C |

`PSS` jest pojedynczym pomiarem końcowym z `dumpsys meminfo`, nie maksimum z
całej sesji. Pełny `memory_peak` i przebieg termiczny nadal należy potwierdzić
eksportem `.alprsession`, `traces.csv`, `frame_flow.csv` i `thermal.csv`.

## Kryterium tagu

Tag `lock-v1-stable` można utworzyć po zatwierdzeniu zmian w repozytorium i
sprawdzeniu `git diff --check`. Wartości są baseline'em inżynierskim z krótkich
sesji kontrolowanych; właściwa kampania badawcza powinna użyć wielu replikacji
oraz eksportów `.alprsession`.
