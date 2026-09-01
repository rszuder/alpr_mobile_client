# Phase 3B.2 — końcowy raport Motion & Overlay Hardening

Data odbioru: 2026-09-01

Gałąź: `phase3b2-motion-overlay-final-hardening`

Baza audytu: `682461c4c17a57854e22cad2ef9ae83c95eacfe1`

Zwalidowany SHA implementacji: `7d708f38e1fdad25596f606b8e7339755314787f`

Commit utworzenia raportu: `0a80b215ba7084bf9fb0f9b2326c051a825a5a8f`

Urządzenie: Samsung SM-A125F, Android 12, API 31, orientacja portrait.

## 1. Wynik

Status: **PASS**

Blokady B2-01–B2-07 oraz wymaganie świeżości aktywnego markera zostały
zamknięte. Dodatkowo test live ujawnił i doprowadził do naprawy pętli
`SOFT_REACQUIRE`, w której historyczne encje blokowały publikację poprawnych
wyników MP. Finalny HEAD zawiera również spokojny, nadrzędny stan konfiguracji
dla aplikacji bez aktywnych modeli MT/MZ.

## 2. Zrealizowany zakres

### B2-01 — fizyczna bariera RELEASE

- `RELEASE_ACTIVE_TARGET` natychmiast usuwa poprzednią warstwę `PLATE`.
- Anulowany jest także trwający fade poprzedniej encji.
- Fade pozostaje dozwolony wyłącznie w obrębie tej samej encji i sesji.
- Opóźniona obserwacja nie może odtworzyć starej tablicy po przełączeniu celu.

### B2-02 — bramki direct-luma i epoki wizualnej

- Wynik direct-luma jest ponownie sprawdzany przed zmianą motion state,
  target state, koordynatora i UI.
- Stare `visualEpoch`, `sceneGeneration` i `cameraTransformGeneration` są
  odrzucane przed dispatch.
- Zmiana epoki unieważnia historię ruchu i wymaga świeżej referencji.
- Dodano ochronę przed publikacją starego overlayu przed fizyczną barierą
  prezentacji.

### B2-03 — świeżość encji pozostaje nadrzędna

- Globalna transformacja nie przedłuża bez końca życia ramki pojazdu.
- Projekcja zachowuje per-entity TTL i odrzuca encje bez świeżej geometrii.
- Ciągłość prezentacji może korzystać ze świeżego MP, local KLT albo
  zakwalifikowanego global motion, ale nie ze starej historycznej ramki.

### B2-04/B2-05 — jakość i semantyka global motion

- Evidence ma jawne confidence/coherence zamiast stałej wartości `1`.
- Globalny ruch wymaga rozproszonego wsparcia przestrzennego tła.
- Obszary foreground pojazdów są wyłączane z dominującego modelu kamery.
- Ruch samych pojazdów przy nieruchomym telefonie nie jest klasyfikowany jako
  global camera motion.
- Sensor motion i visual motion mają osobne znaczenie i osobną telemetrię.

### B2-06 — ograniczona pamięć ruchu

- Evidence ruchu wizualnego wygasa szybko po ustaniu obserwowanego ruchu.
- Po rebase pierwsza klatka ustanawia referencję; dopiero następna może zwrócić
  transformację.
- Zmiany sceny resetują local/global motion history.

### B2-07 — pełna klatka eksperymentalna i osobny viewport Scan

- R0 ponownie używa prawdziwej pełnej klatki.
- Definicje R1/R2 pozostają zamrożonymi politykami eksperymentalnymi.
- MP i tracker encji widzą pełną klatkę.
- `AnalysisViewport` filtruje wyłącznie kwalifikację do kolejki Scan, a nie
  tożsamość trackera.

### Geometria i prezentacja dynamiczna

- Marker aktywnego pojazdu ma własną świeżość, ograniczoną bieżącym vehicle
  overlay maximum age; domyślny deadline UI wynosi `500 ms`.
- Wspólny `FIT_CENTER` mapper obsługuje bbox, marker i Preview.
- Local luma KLT śledzi każdy pojazd niezależnie między wynikami MP.
- Globalna składowa ruchu kamery i lokalne residuale encji nie są mieszane.
- MP anchor otrzymany podczas potwierdzonego ruchu kamery jest odraczany do
  bezpiecznego handoffu global → local.
- Arbiter prezentacji nie pozwala pustemu, opóźnionemu wynikowi ciężkiego
  pipeline'u wyczyścić świeższej geometrii direct-luma.

### Poprawka ujawniona podczas odbioru live

MP poprawnie zwracał trzy pojazdy, ale recovery porównywało je z siedmioma
historycznymi encjami repozytorium:

```text
result=FAILED
reason=fresh_mp_did_not_recover_vehicle_pool
before=7 after=3 measured=3 reassociated=3
```

Snapshot recovery obejmuje teraz wyłącznie encje z ostatniej widocznej klatki
oraz aktywny cel. Po poprawce świeże trzy detekcje kończą recovery i wracają do
prezentacji. Regresję pokrywa
`MobileAlprEngineContinuityApiTest.reacquireSnapshotContainsVisiblePoolNotHistoricalRepository`.

### Spokojny stan brakujących modeli

- Dodano osobny stan `SETUP_REQUIRED` prezentowany jako
  „Brak wymaganych modeli”.
- Stan ma wyższy priorytet niż `SEARCHING`, `TRACKING`, `RECOGNIZING` i
  `RECOVERING`, dlatego callbacki kamery i Scan nie mogą wywołać migania copy.
- Pasek używa neutralnej, szarej kolorystyki i stałej instrukcji importu modeli
  w Opcjach; komunikat nie jest transientem.
- Wynik `models_missing` nie uruchamia już powtarzanego eventu ani nie wraca na
  końcu dispatch do statusu Scan.
- Blokada jest zdejmowana dopiero po ponownym załadowaniu kompletnego pipeline'u
  MT+MZ. Rzeczywisty błąd kamery nadal może przebić stan konfiguracji.
- Regresję priorytetu pokrywa `LivePresentationControllerStateTest`.

## 3. Walidacja automatyczna

Wszystkie kontrole wykonano ponownie dla SHA
`7d708f38e1fdad25596f606b8e7339755314787f`.

| Kontrola | Wynik |
|---|---|
| `testDebugUnitTest` | PASS — 414/414, 0 failures, 0 errors, 0 skipped |
| `assembleDebug` | PASS |
| `assembleDebugAndroidTest` | PASS |
| `lintDebug` | PASS |
| `connectedDebugAndroidTest` | PASS — 59/59 na SM-A125F, 0 skipped, 0 failed |
| `git diff --check` | PASS |

Testy obejmują wymagane przypadki audytu: odrzucenie starej visual epoch,
reset motion reference po recovery, bounded entity freshness, odrzucenie
foreground-only motion, distributed background support, natychmiastowe
anulowanie plate fade po release/switch, R0 full frame, rozdzielenie viewportu
Scan od tożsamości trackera oraz wygaśnięcie aktywnego markera po geometry TTL.

Repozytorium nie udostępnia niezależnego wyniku CI dla tego SHA. Powyższe wyniki
są lokalnym odbiorem na wskazanej stacji i urządzeniu.

## 4. Próby na fizycznym urządzeniu

### S1 — hard visual release: PASS

Po release poprzedniej encji stara różowa ramka i jej badge znikały
natychmiast, bez 2,4-sekundowego fade. Następna encja nie odziedziczyła starej
tablicy; warstwa pojawiała się dopiero po świeżym MT nowego celu.

Dowód pomocniczy: `app/build/alpr_s1_state.png` oraz testy release/fade.

### S2 — nieruchoma kamera i ruchomy foreground: PASS

Telefon pozostawał nieruchomy, a trzy pojazdy poruszały się w materiale
filmowym. Ramki podążały za samochodami bez wspólnego przesuwania całego HUD.
Telemetria używała `source=LOCAL_KLT`, nie uruchomiła global camera motion ani
transition sceny.

Te same encje `10`, `9`, `11` przeszły od górnej do dolnej części obrazu.
Techniczne track IDs mogły zostać odnowione, ale entity IDs pozostały stałe.
Pula zmniejszała się naturalnie `3 → 2 → 1 → 0` przy opuszczaniu kadru.

Dowody:

- `app/build/alpr_motion_clean_run.mp4`;
- `app/build/alpr_motion_clean_run_log.txt`;
- `app/build/alpr_motion_clean_start.png`;
- `app/build/alpr_motion_clean_end.png`.

### S3 — camera pan: PASS

Podczas ruchu i powrotu telefonu trzy ramki pozostały związane z pojazdami.
Telemetria potwierdziła odroczenie MP anchor podczas ruchu,
`source=GLOBAL_PRESENTATION vehicles=3`, a następnie handoff do local KLT.
Nie wystąpiła bariera prezentacji. Najdłuższa zarejestrowana aktualizacja
trackingu wyniosła `283,435 ms`; żadna nie przekroczyła `500 ms`.

Dowody:

- `app/build/alpr_tracking16_deferred_mp.mp4`;
- `app/build/alpr_tracking16_deferred_mp_log.txt`.

### S4 — abrupt replacement i visualEpoch: PASS

Po zastąpieniu sceny jednolitym białym obrazem bariera została aktywowana przy
`changed_fraction=0.527`. Żadna stara ramka nie została wyrenderowana na białej
scenie. Dokładny wyścig KLT epoch N → transition → UI dispatch jest dodatkowo
sprawdzany deterministycznym testem generacyjnym.

Dowody:

- `app/build/alpr_s4_abrupt_replace_retry.mp4`;
- `app/build/alpr_s4_abrupt_replace_retry_log.txt`;
- `app/build/alpr_s4_contact.jpg`.

### S5 — recovery rebase: PASS

Po przywróceniu sceny wystąpiły:

```text
ALPR_PRESENTATION_BARRIER release generation=7
ALPR_PREVIEW_REFERENCE rebase=coordinator_recovery ... VEHICLE_POOL_RECOVERED
```

Przed ustanowieniem pierwszej referencji nie opublikowano global motion.
Ramki wróciły dopiero po świeżym MP anchor dla trzech pojazdów.

Dowody:

- `app/build/alpr_s5_recovery_rebase.mp4`;
- `app/build/alpr_s5_recovery_rebase_log.txt`.

### S6 — viewport edge i ciągłość encji: PASS z kontrolą deterministyczną

Dokładna semantyka edge jest pokryta testem
`ScanAcquisitionViewportVehicleFrameTest`: pojazd poza working viewport
pozostaje w pełnoklatkowym trackerze, nie trafia do kolejki, a po wejściu staje
się eligible z tym samym `entityId`. Osobne testy potwierdzają prawdziwą pełną
klatkę R0.

Literalna próba obserwacyjna na monitorze nie była miarodajna, ponieważ overlay
poza obszarem roboczym nie pokazuje identyfikatora encji, a trzy pojazdy
wchodziły jednocześnie. Zamiast przypisywać jej fałszywy wynik, wykonano bliższy
rzeczywistości przejazd trzech pojazdów opisany w S2. Potwierdził on stałe
entity IDs oraz tracking aż do krawędzi i opuszczenia obrazu.

### S7 — active marker freshness: PASS

W próbie live zasłonięto kartonikiem aktywny biały pojazd. Trójkąt pozostał
krótko, po czym przestał wskazywać starą pozycję; po usunięciu zasłony system
kontynuował wybór dostępnych pojazdów. Dokładny deadline jest sprawdzony na
fizycznym urządzeniu przez
`DetectionOverlayViewInstrumentedTest.activeVehicleMarkerExpiresWithGeometryDeadline`:
marker jest dostępny przed TTL i niewidoczny po przekroczeniu `500 ms`.

Dowody:

- `app/build/alpr_s7_marker_freshness.mp4`;
- `app/build/alpr_s7_marker_freshness_log.txt`;
- `app/build/alpr_s7_marker_end.png`.

### UX-MISSING — brak modeli bez migania: PASS

Na finalnym APK usunięto aktywne modele MT/MZ i uruchomiono analizę. Po 3 s oraz
po kolejnych 15 s pasek pozostawał identyczny:

```text
Brak wymaganych modeli
```

Kolor pozostał neutralny, nie pojawiły się statusy „Szukam…”, „Odczytuję…” ani
powtarzany transient. Podgląd kamery pozostał dostępny. Dowody:

- `app/build/alpr_models_missing_3s.png`;
- `app/build/model_status_now.png`;
- `app/build/model_status_stable_15s.png`.

## 5. Kryteria odbioru

| Obszar | Kryterium | Wynik |
|---|---|---|
| Floating PLATE | zero starej PLATE i fade po release/switch | PASS |
| Direct-luma | stara epoka nie zmienia state, koordynatora ani UI | PASS |
| Global motion | jawne confidence i rozproszone wsparcie tła | PASS |
| Global motion | foreground-only motion odrzucony | PASS |
| Freshness | global transform nie omija TTL encji | PASS |
| Experiments | R0 full frame, R1/R2 zamrożone | PASS |
| Scan | viewport nie zmienia tożsamości trackera | PASS |
| Geometry | wspólny mapper bbox/marker/Preview | PASS |
| Geometry | kalibracje 640×480, 1280×720, 1920×1080 | PASS |
| Marker | brak ghosta po geometry TTL | PASS |
| Dynamic UI | ciągłość ramek przy pan i ruchu foreground | PASS |
| Recovery | świeża widoczna pula kończy soft reacquire | PASS |
| Konfiguracja | brak MT/MZ daje stały neutralny status bez migania | PASS |

## 6. Ograniczenia metodologiczne

1. Samsung SM-A125F nie udostępnia żyroskopu; ruch urządzenia korzysta z
   dostępnego fallbacku sensorycznego oraz evidence wizualnego.
2. Materiał fotografowany z monitora zawiera aliasing i wzory odświeżania,
   dlatego dokładne race conditions są oceniane deterministycznymi testami, a
   nie wyłącznie obserwacją manualną.
3. S6 nie otrzymał sztucznego wyniku manualnego: brak widocznego `entityId`
   poza viewportem uniemożliwia uczciwe potwierdzenie tożsamości samym okiem.
   Inwariant jest pokryty testem czysto logicznym, a zachowanie realnego ruchu
   osobnym testem live.
4. Fizyczna próba S7 potwierdza brak długotrwałego ghosta, natomiast dokładną
   granicę `500 ms` mierzy test instrumentacyjny.

## 7. Decyzja

**GO do Phase 3C.** Blokady Phase 3B.2 są zamknięte, testy automatyczne i
urządzeniowe są zielone, a odbiór live potwierdził zarówno ruch kamery, jak i
niezależny ruch pojazdów. Phase 3C może rozpocząć się od
`BestCropSelector`, `AcquisitionRecord`, deduplikacji i finalnego UI wyników,
bez cofania generacyjnych bramek, per-entity freshness i rozdzielenia viewportu
Scan od eksperymentów.
