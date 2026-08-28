# Phase 3A v2 — zgodność przed integracją runtime

## Punkt bazowy

- gałąź bazowa: `phase3-scan-acquire`;
- commit: `95b7767a7a23228de42adb56fb5b53f3228f00b4`;
- gałąź robocza: `phase3a-target-centric-continuity`;
- specyfikacja nadrzędna: `ALPR_phase3A_target_centric_scene_continuity_v2.md`;
- v2 zastępuje wcześniejszą specyfikację `phase3A_scene_continuity`.

## Baseline

| Kontrola | Wynik na `95b7767` |
|---|---|
| JVM | `PASS` — 189/189 |
| lintDebug | `PASS` |
| assembleDebug | `PASS` |
| assembleDebugAndroidTest | `PASS` |
| connectedDebugAndroidTest | `PASS` — 16/16 na SM-A125F dla tego samego SHA |
| git diff --check | `PASS` |

## Korekta pojęciowa v2

| Pojęcie | Kontrakt |
|---|---|
| `RAW_VISUAL_CHANGE` | obserwacja dużej różnicy pikseli; nigdy samodzielna komenda resetu |
| `MOTION_EXPLAINED_CHANGE` | duża różnica wyjaśniona ruchem i ciągłością targetu lub puli encji |
| `CONTINUITY_BREAK` | potwierdzone zerwanie po niskiej ciągłości i nieudanym recovery albo zdarzenie strukturalne |

W dynamicznym trybie lokalna ciągłość pojazdu/tablicy ma pierwszeństwo przed
globalnym `changedFraction`. Stabilny target może prowadzić do akcji `NONE`, a
utrata tylko aktywnego celu Scan do `RELEASE_ACTIVE_TARGET` bez resetu puli.

## Kontrakty pierwszego commita

| Typ | Rola |
|---|---|
| `SceneHandlingMode` | jawny strict lub dynamic, niezależny od `ApplicationMode` |
| `VisualChangeClassification` | oddziela raw change, explained change i continuity break |
| `TargetContinuityLevel` | hierarchia lokalnych kotwic targetu |
| `SceneTransitionAction` | zawiera `NONE`, soft recovery, scoped release i hard reset |
| `SceneContinuityState` | stabilny, hold, reacquire lub hard reset |
| `TargetContinuityEvidence` | immutable dowody vehicle/plate/KLT/Kalman/appearance |
| `VehicleContinuityEvidence` | immutable ciągłość całej puli encji |
| `MotionExplanationEvidence` | gyro, transformacja kamery i opcjonalny dominujący ruch |
| `SceneEvidence` | kompozycja trzech niezależnych osi evidence |
| `ContinuityAssessment` | klasyfikacja i cztery jawne score |
| `SceneTransitionDecision` | kompletna, immutable decyzja bez wykonywania skutków ubocznych |
| `SceneContinuitySnapshot` | immutable stan dla przyszłego runtime i UI |
| `SceneContinuityProfile` | wszystkie początkowe progi w jednym miejscu |

Brak globalnego flow jest reprezentowany jawnie przez
`dominantMotionEstimated=false`; przyszłe evaluatory mają normalizować wagi
dostępnych składników. Wartości score są walidowane w zakresie `[0,1]`, czasy i
liczniki nie mogą być ujemne, a generacja sceny może wzrosnąć wyłącznie razem z
visual epoch podczas `HARD_RESET`.

## Runtime mode and telemetry

Normalny pipeline live domyślnie używa `dynamic_continuity`. Ustawienia pozwalają
wybrać `strict_scene_boundary`, natomiast aktywny eksperyment R0/R1/R2 zawsze
zamraża efektywny tryb jako strict bez nadpisywania zwykłej preferencji użytkownika.
Sesja pomiarowa zamraża i eksportuje pola `scene_handling_mode` oraz
`scene_continuity_profile=initial_v2`.

Każdy ślad inferencji zapisuje klasyfikację i score, target/vehicle/motion evidence,
akcję z powodem, stan continuity, `sceneGeneration`, `visualEpoch` i
`finalization_suspended`. Eventy obejmują raw, explained, unexplained i potwierdzony
break, a także hold, reacquire, zwolnienie aktywnego celu, hard reset i odrzucenie
starego wyniku. Zakończenia hold/reacquire zawierają zmierzony czas w milisekundach.

## Granica pierwszego commita

Ten commit nie zmienia:

- `MainActivity`;
- `AlprPipeline`;
- `MobileAlprEngine`;
- detektorów sceny i istniejących resetów;
- autozoomu;
- kolejki akwizycji;
- zachowania strict baseline.

Następny commit może dodać wyłącznie czyste evaluatory scoringu. Integracja z
runtime nastąpi dopiero po przetestowaniu polityki.
