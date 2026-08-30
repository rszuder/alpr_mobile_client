# Phase 3A.2 — raport końcowy uszczelnienia ciągłości

## 1. Zakres i punkt odniesienia

- Specyfikacja: `ALPR_phase3A2_final_continuity_seal_agent.md`.
- Gałąź: `phase3a2-final-continuity-seal`.
- Baza dokumentacyjna Phase 3A.1: `d690e1d5845901ce2498aebcfdf49ccd78534046`.
- Zwalidowany SHA implementacji: `9b3d3c1a7420645a87d1da1d6dc76b1b6dc86bbb`.
- Urządzenie: Samsung SM-A125F, Android 12, API 31.
- Końcowy tryb pozostawiony użytkownikowi: `dynamic_continuity`.

Raport wskazuje SHA implementacji. Jest osobnym artefaktem dokumentacyjnym
tworzonym po walidacji tego stanu. Nie wdrożono `AcquisitionQueue`,
`ScanAcquisitionController`, `BestCropSelector`, `AcquisitionRecord`, `Pick`
ani `Search`.

## 2. Commity Phase 3A.2

1. `cbc93c4` — `Generation-gate final pipeline result dispatch`.
2. `0f26902` — `Separate recovery runtime and source timestamps`.
3. `eff18d9` — `Evaluate secondary scene evidence before inference mutation`.
4. `5ae6275` — `Make scene coordinator authoritative for preview UI`.
5. `9b3d3c1` — `Stabilize preview after pool recovery`.

Ostatni commit wynika z odbioru na fizycznym urządzeniu. Ujawnił brak
synchronizacji referencji lekkiego Preview po terminalnym recovery, wyścig
między torem bitmapowym i `direct-luma` oraz traktowanie degradacji świeżego,
jeszcze nieustanowionego kandydata jak utraty focused targetu.

## 3. Zrealizowane blokady audytu

### A2-01 — końcowy `PipelineResult`

- Dodano `PipelineResultDispatchGate`.
- Stamp jest sprawdzany po zwrocie pipeline'u, przed telemetrią wyniku,
  przed prezentacją UI, defensywnie w prezentacji i przed zapisem cropa.
- Stary wynik nie może trafić do UI ani galerii.
- Współdzielony właściciel zasobów gwarantuje jedno efektywne `close()` także
  dla aliasów wyniku utworzonych przez `withContinuityStamp()`.
- Dodano osobny event `final_pipeline_result_dropped` i liczniki faz odrzucenia.

### A2-02 — secondary scene preflight

- Pełnobitmapowy detektor przeniesiono z mutującego silnika do `AlprPipeline`.
- Secondary evidence jest oceniane przed MP/MT/MZ i przed zmianą repozytorium.
- Klatka powodująca hold, reacquire albo reset nie uruchamia ciężkiej inferencji.
- Kontrolowane testy potwierdzają brak nowych encji i brak `attachPlate` przed
  decyzją zarówno w `dynamic`, jak i `strict`.

### A2-03 — domeny czasu

- `startedRuntimeNanos` używa zegara monotonicznego runtime do deadline'u,
  duration i cooldownu.
- `triggerSourceTimestampNanos` używa domeny źródłowej CameraX do kolejności
  klatek oraz świeżości MP/MT.
- `RecoveryFrameGate` i fresh reassociation nie porównują już timestampów z
  różnych baz.
- Test offsetu `runtime=1e9`, `source=8e9` potwierdza odrzucenie starej i
  przyjęcie świeżej klatki przy niezależnych zegarach.

### A2-04 — autorytet koordynatora w UI

- Każda niepusta `SceneTransitionDecision` jest jedynym autorytetem overlayu,
  statusu, local release i hard invalidation.
- Legacy fallback działa tylko przy braku decyzji.
- Lokalna utrata trackera nie zwiększa `uiSceneGeneration`.
- Po terminalnym recovery nowa referencja Preview jest stosowana dokładnie raz
  według monotonicznej rewizji decyzji; wyższa równoległa rewizja nie może
  zostać utracona.
- `VEHICLE_POOL_RECOVERED` usuwa ghost focused target UI, ale zachowuje encje
  i overlay pojazdów.
- Local loss/degradation jest evidence continuity tylko dla wcześniej
  ustanowionego targetu (`TRACKING`, `LOCKED` albo istniejący lock), nie dla
  świeżego słabego kandydata.

## 4. Wyniki automatyczne

Komendy wykonano lokalnie dla SHA `9b3d3c1a7420645a87d1da1d6dc76b1b6dc86bbb`.

| Kontrola | Wynik |
|---|---|
| `testDebugUnitTest` | PASS — 286/286, 0 failures, 0 errors |
| `lintDebug` | PASS |
| `assembleDebug` | PASS |
| `assembleDebugAndroidTest` | PASS |
| pełny Android instrumentation runner | PASS — 21/21 na SM-A125F |
| `git diff --check` | PASS |

APK testowe instalowano przez `adb install -r`, a runner uruchamiano ręcznie.
Nie użyto zadania Gradle, które odinstalowuje aplikację docelową; zainstalowane
modele i dane użytkownika zostały zachowane. Po testach usunięto wyłącznie
pakiet `com.example.alpr_v1.test`.

Repozytorium nie ma niezależnego wyniku GitHub Actions dla tego SHA. Powyższe
wyniki są lokalnym odbiorem, a nie wynikiem CI.

## 5. Obowiązkowe scenariusze G–J

### G — final result dispatch race

PASS w kontrolowanym teście wielowątkowym JVM i teście instrumentacyjnym na
telefonie. Wynik ze starej `visualEpoch` został opóźniony do czasu zmiany
epoki. UI i zapis cropa nie zostały wywołane, a bitmapa została zwolniona
dokładnie raz.

Naturalnej ręcznej próbie nie przypisano wyniku, ponieważ dokładne trafienie w
okno między powrotem pipeline'u a kolejką UI jest niedeterministyczne.

### H — secondary-only evidence

PASS w kontrolowanym teście JVM i instrumentacyjnym na telefonie. Source
detector zwraca brak zmiany, secondary detector zwraca zmianę. Przed decyzją
nie wykonano MP/MT/MZ, repozytorium zachowało encje A i B, nie utworzono C/D,
nie wykonano `attachPlate` ani zmiany MZ.

### I — dynamic pool-only continuity

PASS w kontrolowanym teście urządzeniowym dla dwóch overlayów pojazdów, decyzji
`NONE/STABLE`, zachowanej puli i braku focused targetu. Nie wykonano legacy hard
invalidation ani `uiSceneGeneration++`.

Próby ręczne na monitorze potwierdziły rzeczywiste wyniki
`VEHICLE_POOL_RECOVERED` z dwiema świeżo zmierzonymi i reassociated encjami,
bez `HARD_RESET`. Jednocześnie scena bocznych pojazdów okresowo generowała
fałszywy kandydat MT/OCR, dlatego tej obserwacji nie przedstawia się jako
czystego ręcznego testu „bez targetu”. Próby ujawniły i doprowadziły do naprawy:

- powtarzanego recovery od starej referencji Preview;
- nadpisywania flagi rebase między bitmapą i `direct-luma`;
- recovery uruchamianego przez degradację nieustanowionego ghost targetu.

Po finalnej bramce nieruchome 10-sekundowe okno nie zawierało kolejnego
`SOFT_REACQUIRE` ani wyniku recovery. Dowody lokalne obejmują
`app/build/phase3a2-I7-after.png` i wcześniejsze zrzuty diagnostyczne I2–I8.

### J — strict regression

PASS ręcznie na finalnym buildzie. Nieruchomy telefon obserwował scenę dwóch
pojazdów, po czym monitor przełączono jednym cięciem na jednolite jasne tło:

```text
raw=true
score=0.293
fraction=0.923
classification=CONTINUITY_BREAK
action=HARD_RESET
reason=strict_raw_visual_change
```

Wystąpił dokładnie jeden `HARD_RESET`. Po dodatkowych 8 sekundach nadal istniał
tylko ten jeden wpis; nie wystąpił drugi reset z secondary preflight. Dowody:
`app/build/phase3a2-J-before.png` i `app/build/phase3a2-J-after.png`.

## 6. Telemetria

Dodano lub potwierdzono:

```text
final_result_dispatch_status
final_result_drop_phase
final_pipeline_result_dropped

secondary_scene_preflight_detected
secondary_scene_preflight_action
secondary_scene_preflight_skipped_inference

reacquire_started_runtime_nanos
reacquire_trigger_source_timestamp_nanos
source_timestamp_domain
runtime_timestamp_domain

preview_decision_authority
preview_coordinator_decisions
legacy_preview_fallbacks
```

Stary końcowy wynik ma osobny event od starego pośredniego callbacku MT.
Countery liczą zdarzenia, a nie identyfikatory encji.

## 7. Kryteria funkcjonalne

| Kryterium | Wynik |
|---|---|
| stary final result nie trafia do UI | PASS |
| stary final result nie trafia do galerii | PASS |
| stale result jest zamykany dokładnie raz | PASS |
| secondary evidence przed mutacją domeny | PASS |
| recovery snapshot opisuje stan sprzed zmiany | PASS |
| rozdzielone source/runtime clocks | PASS |
| gate działa przy offsecie zegarów | PASS |
| UI nie nadpisuje koordynatora | PASS |
| local loss nie udaje globalnej granicy sceny | PASS |
| strict wykonuje jeden hard reset | PASS |
| dynamic odzyskuje target albo pulę | PASS |
| brak crasha i ANR w wykonanym odbiorze | PASS |

## 8. Znane ograniczenia

1. SM-A125F nie ma żyroskopu; fallback akcelerometru daje kategorię ruchu, ale
   nie rzeczywistą prędkość kątową.
2. Sceny fotografowane z monitora zawierają aliasing, odświeżanie i prostokątne
   wzory, które mogą powodować fałszywe kandydaty MT/OCR. Jest to ograniczenie
   jakości modeli/sceny, odrębne od poprawności granic continuity.
3. Testy G i H są deterministycznymi testami kontrolowanymi na fizycznym
   urządzeniu, nie naturalnymi próbami trafienia w niedeterministyczny wyścig.
4. Progi profilu `initial_v2` nadal wymagają walidacji terenowej na większej
   liczbie urządzeń i rzeczywistych scenach drogowych.

## 9. Decyzja

**GO do Phase 3B.** Wszystkie blokady Phase 3A.2 są zamknięte i pokryte testami.
Można rozpocząć `AcquisitionQueue` i `ScanAcquisitionController`, zachowując
centralny autorytet koordynatora, generacyjne bramki wyników oraz rozdzielone
domeny czasu.
