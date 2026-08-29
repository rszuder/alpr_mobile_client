# Phase 3A.1 — continuity hardening: raport końcowy

Data walidacji: 2026-08-29

## 1. Gałąź, SHA i środowisko

- Gałąź: `phase3a1-continuity-hardening`
- Zwalidowany końcowy SHA implementacji: `ff51a2d631f0f77f3857e87a786a478cbaf0f20e`
- Punkt bazowy Phase 3A: `bf13fafa`
- Urządzenie: Samsung SM-A125F
- Android: 12
- API: 31
- Sensor ruchu: akcelerometr LIS2DLC12; urządzenie nie udostępnia żyroskopu
- Tryb pozostawiony po testach: `dynamic_continuity`

SHA powyżej wskazuje dokładnie stan kodu, dla którego wykonano komplet testów opisany w tym raporcie. Sam raport jest artefaktem dokumentacyjnym utworzonym po zatwierdzeniu i walidacji tego stanu.

## 2. Lista commitów Phase 3A.1

1. `b64abf8` — `Extend soft reacquire outcomes and close no-target recovery`
2. `1624e72` — `Persist reacquire trigger evidence and enforce terminal deadline`
3. `cd0daa2` — `Require measured fresh MP evidence for vehicle reassociation`
4. `8ab4c1c` — `Separate focused tracker loss from raw visual scene change`
5. `1db6ebe` — `Generation-gate intermediate MT callbacks`
6. `59e67bb` — `Route autozoom and internal scene resets through coordinator`
7. `ff51a2d` — `Split continuity telemetry and close device hardening gaps`

## 3. Zmienione kontrakty

- `SoftReacquireResult` obsługuje terminalne wyniki `TARGET_RECOVERED`, `VEHICLE_POOL_RECOVERED`, `ACTIVE_TARGET_LOST` i `FAILED`.
- `SoftReacquireReport` przekazuje jawny wynik oraz liczniki encji: przed recovery, po recovery, zmierzone, predykowane, reassociated i nowe.
- `ReacquireTelemetry` zachowuje przyczynę startową, maksymalny evidence w recovery, obecność aktywnego celu, odzyskanie puli i informację o deadline.
- `VehicleContinuityEvidence` rozdziela fresh pomiar MP od predykcji i udostępnia dostępność agreement zamiast wytwarzać zastępcze wartości.
- `RecoveryFrameGate` blokuje klatki źródłowe starsze niż początek recovery.
- `ContinuityStamp` chroni również pośredni callback MT przed zmianą `sceneGeneration`, `visualEpoch` i `cameraTransformGeneration`.
- `SceneContinuityProfile` jest jedynym miejscem nowych progów ciągłości, w tym parametrów awaryjnego filtra akcelerometru.
- `AlprPipeline` pozostaje jedyną ścieżką wykonującą decyzje `SceneTransitionCoordinator`; autozoom i wewnętrzny detektor sceny nie resetują już samodzielnie stanu.

## 4. Polityka stanów przed i po hardeningu

Przed Phase 3A.1 ścieżka bez aktywnego celu mogła utknąć w `REACQUIRING`, timeout zależał od bieżącego `rawVisualChange`, a odzyskanie puli pojazdów nie było terminalnym sukcesem.

Po Phase 3A.1:

```text
                         ┌──────────────────────────┐
                         │          STABLE          │
                         └─────────────┬────────────┘
                                       │
                    ruch / niepewność  │  niewyjaśniona zmiana
                                       │
                         ┌─────────────▼────────────┐
                         │       MOTION_HOLD        │
                         └───────┬──────────┬───────┘
                                 │          │
                      cel stabilny          │ ruch ustał / max hold
                                 │          │
                                 │   ┌──────▼──────────────┐
                                 └──►│     REACQUIRING     │
                                     └──┬────┬────┬────┬───┘
                                        │    │    │    │
                         TARGET_RECOVERED    │    │    │
                    VEHICLE_POOL_RECOVERED ──┘    │    │
                         ACTIVE_TARGET_LOST ──────┘    │
                      FAILED / DEADLINE / BREAK ──────┘
                              │             │             │
                              ▼             ▼             ▼
                           STABLE   RELEASE_ACTIVE_TARGET HARD_RESET
```

Końcowe inwarianty:

- `RAW_VISUAL_CHANGE` jest evidence, nie poleceniem resetu.
- Tylko `SceneTransitionCoordinator` wybiera akcję przejścia.
- Każde recovery ma terminalny wynik w ograniczonym czasie.
- Stary wynik asynchroniczny nie może odzyskać aktualności przez ponowne ostemplowanie.
- W trybie `strict_scene_boundary` potwierdzona zmiana obrazu ma pierwszeństwo przed timeoutem rozpoczętego wcześniej soft recovery.

## 5. Usunięte błędy

1. Recovery bez aktywnego targetu nie rozpoznawało odzyskania zachowanej puli jako sukcesu. Dodano `VEHICLE_POOL_RECOVERED` i powrót do `STABLE`.
2. `REACQUIRING` mógł pozostać aktywny bez końca. Dodano trwały kontekst, deadline oraz terminalne wyjścia.
3. Timeout oceniał tylko bieżącą klatkę. Decyzja używa teraz trigger evidence i maksymalnego evidence zebranego podczas recovery.
4. Predykowany kandydat mógł zostać policzony jako fresh reassociation. Sukces wymaga rzeczywistego, świeżego pomiaru MP.
5. Projekcja świeżego pomiaru na czas wyniku błędnie oznaczała go jako predykowany. Zachowano measured provenance dla bieżącego pomiaru.
6. Wolna kadencja MP wygaszała tożsamość encji pomiędzy pomiarami. Trwała encja przeżywa predykcję; jej wygaszenie następuje na pomiarze lub hard resecie. Techniczne tracki nadal wygasają.
7. Recovery chroniło tylko encje z ostatniej klatki. Snapshot jest teraz pobierany z trwałego repozytorium encji.
8. Local tracker loss był mieszany z globalnym `RAW_VISUAL_CHANGE`. Są to oddzielne osie evidence.
9. Pośredni callback MT nie miał pełnej bramki generacji. Callback jest sprawdzany przed skutkami ubocznymi analizatora i ponownie przed publikacją UI.
10. Klatka sprzed początku recovery mogła zostać przetworzona jako świeża. `RecoveryFrameGate` odrzuca takie źródła przed inferencją.
11. Autozoom i wewnętrzny detektor mogły tworzyć niezależny reset. Obie ścieżki przechodzą przez koordynator i `applySceneTransition()`.
12. Po udanym recovery stara referencja detektora sceny mogła rozpoczynać kolejne recovery. Referencja jest rebazowana po `TARGET_RECOVERED` i `VEHICLE_POOL_RECOVERED`.
13. Telefon testowy nie ma żyroskopu, więc ruch nie był widoczny dla koordynatora. Dodano jakościowy fallback akcelerometru; nie udaje on prędkości kątowej.
14. Zmiana trybu ciągłości w `SettingsActivity` była zapisywana, ale nie stosowana do działającego pipeline po powrocie. `applySettingsRevision()` przekazuje teraz bieżący tryb i loguje jego zastosowanie.
15. W trybie strict opóźnione `rawVisualChange` mogło przegrać z timeoutem wcześniejszego soft recovery. Strict raw change ma teraz priorytet i wykonuje pojedynczy `HARD_RESET`.
16. Główny pasek statusu mieszał techniczne R1/R2, lock i autozoom ze stanem użytkownika. HUD używa teraz spokojnych, kolorowych stanów: „Szukam tablicy”, „Tablica znaleziona”, „Odczytuję numer”, „Numer odczytany” i „Szukam tablicy ponownie”; szczegóły techniczne pozostają pod ikoną informacji.

## 6. Nowe i rozszerzone testy

- `SceneTransitionCoordinatorTest`: terminalność recovery, no-target pool recovery, trigger evidence, rozdzielenie local loss, tryb strict i priorytet strict raw change po timeout.
- `VehicleTrackingCoordinatorTest`: measured/predicted provenance, wymuszony fresh MP, reassociation chronionych encji, zachowanie tożsamości przy wolnej kadencji MP.
- `RecoveryFrameGateTest`: odrzucenie klatek sprzed recovery i akceptacja świeżych.
- `AccelerometerMotionFilterTest`: bezruch, ruch umiarkowany, rapid latch i wygaśnięcie.
- `IntermediateMtCallbackStampTest` i `GenerationStampedResultsTest`: odrzucenie starej epoki i kompletność stamp.
- `IntermediateMtCallbackInstrumentedTest`: rzeczywisty, wielowątkowy test D na urządzeniu.
- `SceneContinuityTelemetryContractTest`: osobne skip counters i wymagane pola recovery.
- Testy API `AlprPipeline` i `MobileAlprEngine`: wymuszone recovery, callback wyniku i centralna ścieżka resetu.

## 7. Wyniki komend Gradle i kontroli repozytorium

Komendy wykonano dla SHA `ff51a2d631f0f77f3857e87a786a478cbaf0f20e`:

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' `
  -jar 'gradle\wrapper\gradle-wrapper.jar' `
  testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --stacktrace
```

Wynik: `BUILD SUCCESSFUL`.

- testy jednostkowe: 275/275
- failures: 0
- errors: 0
- skipped: 0
- `lintDebug`: PASS
- `assembleDebug`: PASS
- `assembleDebugAndroidTest`: PASS

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' `
  -jar 'gradle\wrapper\gradle-wrapper.jar' `
  connectedDebugAndroidTest --stacktrace
```

Wynik: `BUILD SUCCESSFUL`, 17/17 testów na `SM-A125F - 12`, failures 0, errors 0, skipped 0.

Kontrolowany test D uruchomiono również samodzielnie:

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' `
  -jar 'gradle\wrapper\gradle-wrapper.jar' `
  connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.example.alpr_v1.pipeline.IntermediateMtCallbackInstrumentedTest' `
  --stacktrace
```

Wynik: 1/1, PASS.

```powershell
git diff --check
```

Wynik: PASS; brak błędów whitespace. Ostrzeżenia Git o przyszłej konwersji LF/CRLF nie są błędami `diff --check`.

## 8. Manualne scenariusze A–F

### A — dynamic motion, brak aktywnego targetu, zachowana pula

PASS. Scena z dwoma nieruchomymi samochodami, bez aktywnego celu. Po obrocie telefonu wokół osi pionowej i powrocie wykonano `SOFT_REACQUIRE`, a świeży MP zakończył recovery:

```text
result=VEHICLE_POOL_RECOVERED
before=3 after=2 measured=2 predicted=0 reassociated=2 new=0
```

Nie wystąpił hard reset. Dowód ekranowy: `app/build/phase3a1-A-formal-30-after.png`.

### B — rzeczywiste cięcie sceny w trybie dynamicznym

PASS. Nieruchomy telefon; obraz samochodów zastąpiono jednolitym jasnym tłem. Recovery nie odzyskało puli i zakończyło się jednym `HARD_RESET` na podstawie zachowanego trigger evidence. Nie wystąpiła pętla `REACQUIRING`. Dowód: `app/build/phase3a1-B-after.png`.

### C — chwilowe zasłonięcie tablicy

PASS. Aktywny cel `AT9320IK`; kartonik zasłaniał tylko tablicę przez około 1 s. Evidence miał `raw=false`, `focused_degraded=true`; nastąpiło `SOFT_REACQUIRE`, odzyskanie celu/puli i powrót `TRACKING → LOCKED`, bez hard resetu. Dowód: `app/build/phase3a1-C-clean-after.png`.

### D — opóźniony callback MT

PASS w kontrolowanym teście na fizycznym urządzeniu. Callback wystartował na starym `ContinuityStamp`, następnie `SOFT_REACQUIRE` zmienił `visualEpoch`; po zwolnieniu starego callbacku `setTargetSnapshotIfCurrent()` zwróciło `false`, stary overlay nie został opublikowany, a stary target nie został zakotwiczony.

W próbach ręcznych naturalny timing inferencji nie dawał powtarzalnego callbacku ze starą geometrią dokładnie pomiędzy dwiema epokami. Zaobserwowano natomiast rzeczywiste odrzucenie starego callbacku po zmianie `cameraTransformGeneration`. Nie przypisano temu obserwowanemu zdarzeniu wyniku testu visual-epoch; ten warunek pokrywa kontrolowany test instrumentacyjny.

### E — szybki pan z aktywnym targetem

PASS z ograniczeniem sensora opisanym niżej. Obrót telefonu wokół osi pionowej wywołał:

```text
moving=true rapid=false angular=0.000
action=SOFT_HOLD reason=local_tracking_loss_during_motion
```

Następnie nastąpiło kontrolowane `SOFT_REACQUIRE` i ponowne `LOCKED`, bez nieuzasadnionego hard resetu. `rapid=false` i `angular=0.000` są prawdziwymi wartościami: urządzenie nie ma żyroskopu, a fallback akcelerometru potwierdza kategorię ruchu, lecz nie wytwarza fikcyjnej prędkości kątowej. Dowód: `app/build/phase3a1-E3-after.png`.

### F — strict baseline

PASS po usunięciu dwóch błędów runtime ujawnionych przez próby F1–F3. W F4 nieruchomy telefon obserwował samochód, po czym monitor przełączono na jednolite jasne tło. Wynik:

```text
raw=true score=0.173 fraction=0.580
class=CONTINUITY_BREAK
action=HARD_RESET reason=strict_raw_visual_change
sceneGeneration: 2 -> 3
visualEpoch: 2 -> 3
```

Wystąpił dokładnie jeden hard reset. Kolejne callbacki MT startowały już z `scene=3 visual=3`; nie odnotowano drugiego niezależnego resetu engine. Dowody: `app/build/phase3a1-F4-before.png` i `app/build/phase3a1-F4-after.png`.

## 9. Przykładowe eventy i pola telemetrii

Rozdzielone liczniki pominięć:

```text
frames_skipped_frame_gate
frames_skipped_camera_transform
frames_skipped_hard_scene_reset
frames_skipped_continuity_hold
frames_skipped_continuity_reacquire
```

Recovery:

```text
reacquire_result
reacquire_trigger_classification
reacquire_trigger_cut_score
reacquire_max_cut_score
reacquire_active_target_present
reacquire_vehicle_pool_recovered
reacquire_deadline_reached
fresh_mp_measured_entities
fresh_mp_predicted_entities
fresh_mp_reassociated_entities
```

Przykład odzyskania puli z testu A:

```text
ALPR_REACQUIRE_RESULT result=VEHICLE_POOL_RECOVERED
before=3 after=2 measured=2 predicted=0 reassociated=2 new=0
```

Przykład strict baseline z F4:

```text
ALPR_SCENE_EVIDENCE raw=true focused_lost=false focused_degraded=false
score=0.173 fraction=0.580 class=CONTINUITY_BREAK
action=HARD_RESET reason=strict_raw_visual_change
```

Brak pomiaru nie jest zastępowany wartością wymyśloną. Dla telefonu bez żyroskopu telemetryczne `angular=0.000` oznacza brak dostępnego pomiaru kątowego; kategoryczne `moving` pochodzi z akcelerometru.

## 10. Znane ograniczenia

1. SM-A125F nie ma żyroskopu. Fallback akcelerometru rozpoznaje jedynie jakościowe `moving/rapid`; nie mierzy prędkości kątowej.
2. Progi `SceneContinuityProfile.INITIAL` są jawne i przetestowane, ale nadal wymagają benchmarku na większej liczbie urządzeń i w scenach drogowych.
3. Manualne A–F wykonano w kontrolowanej scenie z monitorem i nieruchomymi obrazami. Nie zastępuje to walidacji terenowej z ruchem pojazdów, zmianą oświetlenia i rolling shutter.
4. Naturalna ręczna reprodukcja dokładnego wyścigu starego callbacku MT jest niedeterministyczna; dlatego D ma deterministyczny test wielowątkowy uruchamiany na urządzeniu.
5. Wydłużona trwałość `VehicleEntity` zabezpiecza wolny MP, lecz polityka limitu wielu encji będzie wymagała ponownej oceny przy wdrażaniu kolejki Phase 3B.
6. Phase 3B i `AcquisitionQueue` nie zostały rozpoczęte w ramach tego zadania.
7. Nie raportowano benchmarków czasu ani wydajności, których nie wykonano w kontrolowanej procedurze.

## 11. Rekomendacja dla Phase 3B

**GO.** Kryteria funkcjonalne, testowe i dokumentacyjne Phase 3A.1 są spełnione. Centralna polityka ma terminalne recovery, measured-only fresh reassociation, pełne bramkowanie pośredniego MT, pojedynczą władzę resetu i rozdzieloną telemetrię. Phase 3B może rozpocząć integrację `ScanAcquisitionController` i kolejki wielu encji, z zachowaniem ograniczeń wymienionych w sekcji 10.
