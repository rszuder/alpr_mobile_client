# HANDOFF — Android: automatyczna sesja badawcza, trwałe zbieranie próbek i paczka do weryfikacji na desktopie

## 0. Cel

Rozbudować tryb badawczy aplikacji Android tak, aby każdy przebieg eksperymentalny tworzył **samowystarczalny zbiór surowych danych do późniejszej weryfikacji przez operatora na desktopie**.

Najważniejsza zmiana metodologiczna:

> W czasie eksperymentu operator nie wybiera ręcznie próbek i nie nadaje im prawdy odniesienia. Telefon automatycznie tworzy sesję, uruchamia zbieranie danych, zapisuje wszystkie wymagane dowody i po zakończeniu tworzy `.alprsession`. Prawda odniesienia i ocena jakości są wykonywane później na desktopie.

Rola operatora na telefonie ogranicza się do:
- przygotowania warunków doświadczenia,
- wyboru konfiguracji eksperymentu przed START,
- fizycznej realizacji scenariusza,
- START/STOP, jeżeli eksperyment nie kończy się automatycznie.

Nie dodawaj ręcznego „wybierania dobrych cropów” w czasie pomiaru.

---

## 1. Stan bazowy i ostrożność z lokalnymi zmianami

Repo:

```text
rszuder/alpr_mobile_client
```

Gałąź użyta przy przygotowaniu handoffu:

```text
phase3b2-motion-overlay-final-hardening
```

Ostatni wypchnięty commit znany przy przygotowaniu dokumentu:

```text
8a19730b405449be698426df311e4085e3963081
```

**UWAGA:** w repo są obecnie wdrażane inne zmiany, które mogą jeszcze nie być wypchnięte.

Przed rozpoczęciem:

```text
git status
git diff
```

Nie resetuj repo do powyższego commita. Nie usuwaj lokalnych zmian. Najpierw zintegrować ten handoff z bieżącym stanem kodu.

---

## 2. Co już istnieje i należy wykorzystać

Obecny kod ma już właściwy fundament:
- `ExperimentSession` — identyfikacja i cykl życia przebiegu,
- `ResearchExecutionConfig` — zamrożona konfiguracja wykonania,
- `ResearchStageExecutionConfig` — dokładny model/wariant/runtime/profil wykonania dla MP/MT/MZ,
- `CapturedPlateItem` — crop tablicy + predykcja + metadane,
- `CaptureGalleryViewModel` — stan bieżącej kolekcji,
- `ResearchArchive` — eksport `.alprsession`,
- `samples/index.csv`,
- `samples/annotations.jsonl`,
- `samples/crops/<capture_id>.jpg`,
- `MetricsCollector`,
- `traces.csv`, `thermal.csv`, `frame_flow.csv`, `events.jsonl`.

Nie twórz drugiego równoległego systemu eksperymentów. Nowe elementy mają domknąć istniejący przepływ.

---

## 3. Najważniejszy kontrakt

Docelowa kolejność START musi być następująca:

```text
operator naciska START
        ↓
walidacja konfiguracji
        ↓
zamrożenie ResearchExecutionConfig
        ↓
utworzenie sessionId
        ↓
UTWORZENIE KATALOGU SESJI
        ↓
zapis session.json = PREPARED
        ↓
uzbrojenie trwałego kolektora próbek
        ↓
ustalenie wspólnego t0
        ↓
ExperimentSession = RUNNING
        ↓
AUTOMATYCZNE zbieranie próbek i prób
        ↓
pomiar
```

Katalog sesji musi istnieć **przed rozpoczęciem mierzonego przebiegu**. Czas tworzenia katalogu i przygotowywania plików nie może być wliczany do czasu eksperymentu.

Jeżeli nie uda się utworzyć katalogu albo uzbroić kolektora:

```text
experiment MUST NOT enter RUNNING
```

---

## 4. Dwuetapowy start sesji

Obecny `ExperimentSession.start(...)` tworzy `sessionId` i od razu ustawia `RUNNING`.

To nie gwarantuje katalogu utworzonego przed startem pomiaru.

Wprowadź kontrolowany dwuetapowy start, np.:

```text
PreparedExperiment
ExperimentSession.prepare(...)
ExperimentSession.startPrepared(...)
```

albo:

```text
ResearchSessionCoordinator.prepare(...)
ResearchSessionCoordinator.start(...)
```

Nie jest konieczne dodawanie `PREPARED` do publicznego `ExperimentSession.State`, jeśli stan przygotowania jest własnością magazynu sesji.

Twarda reguła:

```text
storage prepared
BEFORE
ExperimentSession.startedElapsedNanos
```

---

## 5. ResearchSessionStore

Wprowadź jeden komponent odpowiedzialny za trwałą sesję badawczą, np.:

```text
ResearchSessionStore
```

Odpowiedzialności:
- utworzenie katalogu sesji,
- trwały zapis metadanych,
- trwały zapis prób i cropów,
- synchronizacja writer queue,
- finalizacja sesji,
- przygotowanie danych dla `ResearchArchive`,
- informacja o błędach/zagubionych próbkach.

Nie przenoś tej odpowiedzialności do `DetectionOverlayView`, `MobileAlprEngine` ani `CaptureGalleryViewModel`.

### Proponowany katalog

```text
research/
  sessions/
    <session_id>/
      session.json
      samples/
        attempts.jsonl
        crops.jsonl
        crops/
          <capture_id>.jpg
        evidence/
          <attempt_id>.jpg
      telemetry/
        ...
      final/
        <session_id>.alprsession
```

Nazwy plików roboczych mogą być inne, ale logiczny podział ma pozostać.

---

## 6. Stan trwałej sesji

`session.json` powinien od początku zawierać co najmniej:

```text
schema
session_id
state
experiment_type
series_id
scenario_id
replicate_index
created_at
started_at
finished_at
completion_reason
collection_complete
sample_contract_version
```

Stan magazynu:

```text
PREPARED
RUNNING
FINALIZING
COMPLETED
PARTIAL
ERROR
```

`ExperimentSession` może nadal mieć własne:

```text
IDLE
RUNNING
FINISHED
```

Nie myl stanu domenowego przebiegu ze stanem trwałego zapisu.

---

## 7. Automatyczne zbieranie

Po wejściu sesji w `RUNNING`:

```text
research collection = ON
```

bez osobnego kliknięcia operatora.

Po zakończeniu sesji:

```text
research collection = OFF
```

automatycznie.

Jeżeli aplikacja ma nadal ręczny tryb kolekcji cropów poza eksperymentem, może on pozostać jako funkcja diagnostyczna/użytkowa.

Jednak w:

```text
experimentModeEnabled == true
AND ExperimentSession.RUNNING
```

źródłem prawdy ma być sesja eksperymentalna.

Nie wymagaj:

```text
START experiment
+
START crop collection
```

---

## 8. Nie używaj galerii w pamięci jako źródła prawdy

Obecny `CaptureGalleryViewModel` przechowuje `CapturedPlateItem` z bitmapami w pamięci.

To jest dobre dla UI, ale nie jest wystarczające jako magazyn eksperymentalny.

W czasie eksperymentu:

```text
crop/attempt accepted
        ↓
asynchronous persistent write
        ↓
confirmation in ResearchSessionStore
```

Galeria może mieć ograniczoną liczbę podglądów.

Nie wolno tracić danych badawczych przez:
- limit galerii,
- rotację ekranu,
- evict starego cropa,
- brak pamięci,
- zamknięcie panelu galerii.

---

## 9. Kolejka zapisu

Zapis obrazów nie może blokować wątku inferencji.

Użyj:

```text
bounded writer queue
single background writer
```

Każdy rekord ma mieć wynik:

```text
QUEUED
WRITTEN
FAILED
```

Jeżeli kolejka się przepełni albo plik nie może zostać zapisany:
- nie ignoruj tego,
- zwiększ `dropped_sample_count`,
- zapisz event,
- oznacz końcową sesję jako `PARTIAL` lub `ERROR` zgodnie z wagą problemu.

Raport nie może udawać kompletnego, jeśli część próbek zniknęła.

---

## 10. Dwa poziomy danych: próba i crop

Samo `CapturedPlateItem` nie wystarcza do oceny całego MT.

Jeżeli MT nie wykryje tablicy:

```text
MT miss
→ brak rectification
→ brak cropa tablicy
```

a więc błąd nie pojawi się w `samples/crops`.

Dlatego wprowadź trwały rekord **każdej istotnej próby akwizycji**.

### 10.1. AcquisitionAttemptRecord

Nowy rekord, np.:

```text
AcquisitionAttemptRecord
```

Minimalne pola:

```text
attempt_id
session_id
subject_key
scene_generation
visual_epoch
camera_transform_generation
entity_id
vehicle_track_id
plate_track_id
source_sequence
source_timestamp_nanos
attempt_started_elapsed_nanos
roi_policy
capture_source
camera_zoom_ratio
mt_status
rectification_status
mz_status
prediction
consensus_prediction
plate_confidence
recognition_confidence
evidence_kind
evidence_entry
stale_or_cancelled
cancel_reason
```

### 10.2. Status MT

```text
NOT_RUN
NO_DETECTION
DETECTION_INVALID_QUAD
VALID_QUAD
```

### 10.3. Status rektyfikacji

```text
NOT_RUN
FAILED
OK
```

### 10.4. Status MZ

```text
NOT_RUN
NO_CHARACTERS
READ
```

Stany anulowane przez zmianę sceny/revision nie mogą być mylone z błędem modelu.

Zapisz np.:

```text
stale_or_cancelled = true
cancel_reason = scene_superseded
```

Desktop domyślnie wykluczy je z metryk jakości modelu.

---

## 11. Dowód obrazu dla MT miss

Żeby operator desktopowy mógł stwierdzić, czy MT rzeczywiście „przegapił” tablicę, potrzebuje obrazu wejściowego tej próby.

Dla:

```text
mt_status = NO_DETECTION
```

zapisz reprezentację tego, co faktycznie dostał MT:
- ROI pojazdu dla R1/R2,
- odpowiedni wycinek/full-frame input dla R0,
- po transformacjach potrzebnych do zgodności z inferencją.

`evidence_kind`, np.:

```text
plate_crop
vehicle_roi
mt_full_frame
mt_input_roi
```

Dla poprawnego cropa nie duplikuj obrazu:

```text
evidence_entry = samples/crops/<capture_id>.jpg
```

Dla miss:

```text
evidence_entry = samples/evidence/<attempt_id>.jpg
```

---

## 12. Każda próba MZ w doświadczeniu jakościowym musi być audytowalna

W zwykłej galerii może działać obecna polityka próbkowania.

W sesji badawczej przeznaczonej do pomiaru jakości:

> Każda faktycznie wykonana próba MZ, która ma wynik podlegający ocenie, musi mieć odpowiadający jej crop lub jawnie zapisany powód braku obrazu.

Nie wybieraj tylko „najlepszego” cropa tracka.

Jeśli pipeline wykonał:

```text
MZ attempt 1
MZ attempt 2
AZ retry MZ
```

operator desktopowy ma mieć możliwość ocenić wszystkie trzy.

---

## 13. Tożsamość tablicy / subject_key

Desktop musi grupować wiele prób tej samej fizycznej encji.

Nie używaj do tego OCR.

Zakaz:

```text
subject identity = plateText
```

Preferowany klucz:

```text
session_id + scene_generation + entity_id
```

np.:

```text
exp-.../sg-12/entity-104
```

Zapisz gotowy `subject_key`.

Jeżeli `entity_id` jest chwilowo niedostępne, techniczny track może być fallbackiem, ale rekord powinien zostać promowany do `entity_id`, jeżeli powiązanie pojawi się później.

W STATIC:

```text
NEW_SCENE
→ new scene_generation
→ new subject identity
```

nawet jeśli odczyt rejestracji jest identyczny.

---

## 14. Rozszerzenie CapturedPlateItem / eksportu cropów

Nowe dane cropa powinny zawierać co najmniej:

```text
attempt_id
subject_key
scene_generation
entity_id
vehicle_track_id
plate_track_id
```

Obecne pola pozostają.

Nie grupuj nowych danych tylko po `track_id`.

---

## 15. Docelowy kontrakt `.alprsession`

Nie ma potrzeby łamać obecnego:

```text
alpr.mobile_research_bundle.v1
```

Rozszerzenie może być addytywne.

W finalnym ZIP zachowaj istniejące:

```text
report.json
traces.csv
thermal.csv
frame_flow.csv
events.jsonl
application.log
samples/index.csv
samples/annotations.jsonl
samples/crops/*.jpg
```

i dodaj:

```text
samples/schema.json
samples/attempts.csv
samples/evidence/*.jpg
```

### samples/schema.json

Przykład:

```json
{
  "schema": "alpr.mobile_research_samples.v2",
  "subject_identity": "scene_generation+entity_id",
  "human_review": "desktop",
  "attempts_file": "samples/attempts.csv",
  "crops_file": "samples/index.csv"
}
```

---

## 16. samples/index.csv — nowe kolumny

Do istniejących kolumn dodaj:

```text
attempt_id
subject_key
scene_generation
entity_id
vehicle_track_id
plate_track_id
```

Nie usuwaj obecnych kolumn.

W nowych sesjach:

```text
verification_status = not_reviewed
ground_truth = ""
```

Telefon nie musi wypełniać GT.

---

## 17. samples/attempts.csv

Minimalne kolumny:

```text
attempt_id
session_id
subject_key
scene_generation
visual_epoch
camera_transform_generation
entity_id
vehicle_track_id
plate_track_id
source_sequence
source_timestamp_nanos
attempt_started_elapsed_nanos
roi_policy
capture_source
camera_zoom_ratio
mt_status
rectification_status
mz_status
prediction
consensus_prediction
plate_confidence
recognition_confidence
evidence_kind
evidence_entry
stale_or_cancelled
cancel_reason
```

Można dodać więcej danych technicznych, ale nie zmieniać znaczenia tych pól.

---

## 18. Hash integralności

Każdy nowy plik i obraz w finalnej `.alprsession` musi trafić do istniejącego:

```text
entry_sha256
```

Operator desktopowy będzie pracował na niezmiennym archiwum źródłowym.

---

## 19. STOP eksperymentu

Kolejność:

```text
request STOP
↓
zamknij bramkę przyjmowania nowych prób
↓
zapisz końcowe znaczniki czasu
↓
drain writer queue
↓
finalizuj attempts/crops
↓
zapisz session.json
↓
zbuduj ResearchArchive
↓
sprawdź kompletność
↓
COMPLETED/PARTIAL/ERROR
```

Nie rozpoczynaj budowania ZIP, gdy writer queue nadal zapisuje obrazy.

---

## 20. Automatyczne tworzenie paczki

Po poprawnym zakończeniu sesji przygotuj:

```text
<session_id>.alprsession
```

automatycznie w katalogu sesji.

UI może następnie oferować:

```text
Udostępnij / Eksportuj paczkę
```

Użytkownik nie powinien ręcznie wybierać cropów do tej paczki.

Paczka eksperymentalna = pełna sesja.

---

## 21. Crash recovery

Po uruchomieniu aplikacji sprawdź katalogi sesji.

Jeśli istnieje sesja:

```text
RUNNING
FINALIZING
```

z poprzedniego procesu, oznacz ją jako:

```text
PARTIAL
completion_reason = process_interrupted
```

Nie próbuj po cichu kontynuować tego samego przebiegu jako jednej sesji pomiarowej.

Pozwól użytkownikowi wyeksportować częściową paczkę do diagnostyki, ale jawnie oznacz ją jako niekompletną.

---

## 22. Wolne miejsce

Przed START sprawdź, czy katalog może być utworzony i czy dostępna pamięć nie jest krytycznie mała.

- brak możliwości zapisu → blokada START,
- krytycznie mało miejsca → wyraźne ostrzeżenie/blokada,
- błąd zapisu w trakcie → `PARTIAL/ERROR`.

---

## 23. Relacja z STATIC/DYNAMIC, lock i AutoZoom

Ten handoff nie zmienia semantyki:

```text
STATIC
DYNAMIC
VehicleEntity
TargetSession
AutoZoomController
AcquisitionQueue
```

Kolektor tylko obserwuje rzeczywiste próby i zapisuje dowody.

Nie może wpływać na:
- wybór celu,
- kolejność akwizycji,
- decyzję AZ,
- ciągłość sceny,
- wynik OCR.

Zbieranie danych ma być pasywne względem algorytmu.

---

## 24. Relacja z wyborem wariantu MP/MT/MZ

Sesja ma korzystać z zamrożonego `ResearchExecutionConfig`.

Dla każdego aktywnego etapu eksportuj faktycznie użyte:

```text
model_id
fingerprint
variant_id
runtime
precision
cpu_threads
gpu
input
```

Nie wystarczy nazwa pakietu.

---

## 25. Rola operatora

W metadanych sesji można zapisać:

```text
collection_mode = automatic
review_location = desktop
operator_intervention_in_sampling = false
```

Opcjonalnie `operator_id`, jeżeli użytkownik chce podpisywać przebiegi.

---

## 26. Terminologia jakości

Telefon nie liczy końcowych statystyk prawdy odniesienia.

W kontrakcie desktopowym używamy terminów:

```text
poprawnie rozpoznane znaki
błędnie rozpoznane znaki
brakujące znaki
znaki nadmiarowe
```

---

## 27. Testy Android

### A1 — katalog przed RUNNING
START eksperymentu. Katalog istnieje i `session.json = PREPARED` przed `RUNNING`.

### A2 — failure before start
Błąd utworzenia katalogu → brak `RUNNING`, brak timera, brak sesji metryk.

### A3 — auto collection
Jedno START powoduje jednocześnie `ExperimentSession.RUNNING` i aktywną kolekcję badawczą.

### A4 — automatic stop
Timer kończy eksperyment → kolekcja kończy się automatycznie i powstaje `.alprsession`.

### A5 — wszystkie MZ attempts
Trzy próby MZ dla jednego subject → trzy audytowalne rekordy.

### A6 — MT miss
`mt_status = NO_DETECTION` ma `evidence_entry`.

### A7 — stale result
Anulowanie przez zmianę sceny jest zapisane jako cancelled/stale, nie jako MT miss.

### A8 — subject identity
Wiele prób tej samej `VehicleEntity` ma ten sam `subject_key`.

### A9 — STATIC new scene
Ta sama rejestracja po `NEW_SCENE` ma inny `subject_key`.

### A10 — queue failure
Błąd writer queue → sesja `PARTIAL/ERROR`, licznik utraconych danych > 0.

### A11 — gallery capacity
Limit galerii nie usuwa trwałych danych sesji.

### A12 — rotation
Obrót ekranu nie przerywa kolekcji.

### A13 — crash recovery
Pozostawione RUNNING po restarcie → PARTIAL, nie continuation.

### A14 — hashes
Nowe pliki w `entry_sha256`.

### A15 — research freeze
Przez całą sesję obowiązuje ten sam zamrożony wariant/model dla danego etapu.

---

## 28. Kryteria akceptacji Android

Zadanie jest zakończone, jeśli:

1. Katalog sesji powstaje automatycznie przed mierzoną fazą RUNNING.
2. START eksperymentu automatycznie rozpoczyna zbieranie danych.
3. STOP/timer automatycznie kończy zbieranie.
4. Dane nie zależą od ręcznego zaznaczania cropów.
5. Cropy są zapisywane trwale w czasie sesji.
6. Każda istotna próba MT/MZ ma rekord `attempt`.
7. MT miss ma dowód obrazu umożliwiający późniejszą ocenę.
8. Crop ma `attempt_id`, `subject_key`, `scene_generation` i `entity_id`.
9. `.alprsession` zachowuje stare artefakty i dodaje `samples/attempts.csv`.
10. Wszystkie nowe pliki są objęte SHA-256.
11. Błąd zapisu nie jest ukrywany.
12. Zamrożona konfiguracja modelu/wariantu pozostaje źródłem prawdy.
13. Główna logika inferencji, STATIC/DYNAMIC, lock i AZ nie zmienia zachowania przez sam mechanizm kolekcji.
14. Paczka może zostać otwarta przez zaktualizowany desktop.

---

## 29. Raport końcowy agenta

Na końcu podaj:

```text
1. zmienione pliki,
2. nowy przepływ START,
3. lokalizację katalogu sesji,
4. sposób trwałego zapisu cropów,
5. sposób rejestrowania MT miss,
6. format samples/attempts.csv,
7. sposób finalizacji .alprsession,
8. crash recovery,
9. testy i ich wyniki,
10. ewentualne ograniczenia.
```
