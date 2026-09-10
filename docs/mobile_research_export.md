# Eksport badawczy klienta mobilnego ALPR

Uzupełnienie z 10.09.2026: [addytywne pola raw/key/backend oraz test artefaktów na Desktopie](android_full_interop_2026-09-10.md).

## Artefakty

Ekran Diagnostyka zwraca sterowanie do `MainActivity`, która udostępnia trzy
formaty:

- `*.alprsession` — pełny `alpr.mobile_research_bundle.v1`;
- `alpr_thesis_*.zip` — `alpr.mobile_thesis_bundle.v1`;
- klasyczny ZIP `alpr.mobile_benchmark_report.v1` dla zgodności wstecznej.

Eksport jest strumieniowy. Pliki modeli i JPEG nie są agregowane w pamięci RAM.
Na czas zapisu kolektor zostaje wstrzymany, a snapshot cropów jest chroniony
przed wyparciem. `manifest.json` jest ostatnim wpisem archiwum i zawiera
SHA-256 wszystkich wcześniejszych wpisów.

Pełny `.alprsession` zawiera obecnie również trzy addytywne strumienie
telemetrii `alpr.mobile_experiment_telemetry.v1`:

- `thermal.csv` — próbki termiki, baterii i dostępnej pamięci niezależne od FPS;
- `frame_flow.csv` — jednosekundowe buckety klatek odebranych, przetworzonych,
  pominiętych przez gate/transformację/szybki reset sceny oraz estymowanych luk upstream;
- `events.jsonl` — uporządkowane zdarzenia tracków, prób MZ, konsensusu,
  zmiany sceny i autozoomu.

Nowe wpisy są objęte `manifest.json/entry_sha256`. Klasyczny raport ZIP pozostaje
bez zmian strukturalnych dla zgodności wstecznej.

## Automatyczna kolekcja badawcza — samples v2

Od wdrożenia `research-session-auto-collection-v1` START eksperymentu tworzy
`files/research/sessions/<session_id>/session.json` w stanie PREPARED przed t0.
Zamrożony config oraz uzbrojony writer poprzedzają RUNNING domeny i metryk.
Nie ma drugiego START kolekcji ani wybierania próbek do paczki. STOP/timer
zamyka przyjmowanie nowych prób, czeka na rozpoczętą pracę pipeline’u i writer,
a następnie automatycznie buduje `final/<session_id>.alprsession`.
Wspólny czas końca domeny, metryk i magazynu jest ustalany przy zamknięciu
bramki STOP, zanim zatrzymanie kamery, drain lub ZIP mogłyby opóźnić ten znacznik.

W `.alprsession` zachowany jest `alpr.mobile_research_bundle.v1`. Nowe wpisy:

- `session.json` — konfiguracja, czasy, stan trwałego zapisu i kompletność;
- `samples/schema.json` — `alpr.mobile_research_samples.v2`, review na desktopie;
- `samples/attempts.csv` — każda wykonana próba MT i jej dalszy przebieg;
- `samples/evidence/<attempt_id>.jpg` — obraz wejścia MT, także gdy zapisano crop MZ;
- `samples/attempts.jsonl`, `samples/crops.jsonl`, `samples/write_states.jsonl`
  — dzienniki robocze pozwalające diagnozować zapis;
- `telemetry/*.jsonl` — strumienie utrwalane także podczas pomiaru.

MT evidence jest kopią faktycznego obrazu letterbox użytego przez preprocessor,
po wyborze ROI i rotacji źródła, przed normalizacją kanałów/liczb do tensora.
`roi_*`, `input_width/height`, `input_scale`, `input_pad_x/y` w CSV i dzienniku prób
opisują mapowanie geometrii. Przy wykonanym MZ zapisuje się rektyfikowany crop,
a `evidence_entry` wskazuje ten sam JPEG w `samples/crops` — bez drugiej kopii.
Osobne `mt_input_evidence_entry` zachowuje JPEG wejścia MT; przy braku cropa
wskazuje ten sam plik co `evidence_entry`. Wejście jest zapisywane osobno dla
każdej detekcji. `mt_input_missing_evidence_reason` opisuje brak tego dowodu,
niezależnie od dostępności cropa. Utrata wejścia obniża kompletność sesji.

`mt_invocation_id` identyfikuje dokładnie jedno rzeczywiste wykonanie backendu
MT na jednym wejściu. Jest nadawane tuż przed wejściem do backendu, również
gdy backend rzuci wyjątek. Przy błędzie przygotowania wejścia pozostaje puste,
`mt_executed=false`, `mt_status=NOT_RUN`, a count/index są puste.
Wszystkie detekcje jednego invocation mają wspólne ID, session/generacje,
sequence/timestamp źródła oraz geometrię wejścia. STOP lub anulowanie sceny
nie zmienia tego ID. `subject_key` nadal opisuje obiekt, a nie wykonanie MT.

`mt_detection_count` podaje liczbę wyników dekodera MT przed dalszą selekcją
trackera/celu (także wyników z niepoprawnym quad). `mt_detection_index` jest
indeksem od zera w kolejności dekodera. Zero detekcji pozostawia jeden rekord
`NO_DETECTION`, `mt_executed=true`, count=0 i pusty index. Jedna detekcja daje
count=1/index=0; trzy dają trzy rekordy o wspólnym invocation ID, count=3
i indeksach 0,1,2. Dwa wywołania z 2 i 1 detekcją dają dwa ID i trzy rekordy.
`execution_error` oraz anulowanie wykluczają interpretację wiersza jako
normalnie zakończonego pomiaru jakości; wyjątek backendu nie jest poprawnym
negatywnym wynikiem MT, nawet jeśli domyślny status pozostał `NO_DETECTION`.
Android nie estymuje liczby prawdziwych tablic na wejściu — to GT na desktopie.
Próby odfiltrowane później przez tracker/NMS/wybór celu pozostają audytowalne
z `mz_status=NOT_RUN`.

`samples/attempts.csv` zawiera:

```text
attempt_id,session_id,subject_key,scene_generation,visual_epoch,
camera_transform_generation,entity_id,vehicle_track_id,plate_track_id,
source_sequence,source_timestamp_nanos,attempt_started_elapsed_nanos,
roi_policy,capture_source,camera_zoom_ratio,mt_status,rectification_status,
mz_status,prediction,consensus_prediction,plate_confidence,recognition_confidence,
evidence_kind,evidence_entry,stale_or_cancelled,cancel_reason,write_state,
missing_evidence_reason,mz_executed,mt_invocation_id,
mt_detection_index,mt_detection_count,mt_executed,
roi_left,roi_top,roi_right,roi_bottom,input_width,input_height,
plate_left,plate_top,plate_right,plate_bottom,input_scale,input_pad_x,input_pad_y,
mt_input_evidence_entry,mt_input_missing_evidence_reason,execution_error
```

Statusy MT: NOT_RUN / NO_DETECTION / DETECTION_INVALID_QUAD / VALID_QUAD.
`roi_*` opisuje wejściowy region w pikselach źródła, `input_width/height`
rozmiar wejścia po letterbox, a `plate_*` bbox konkretnej detekcji w źródle.
Nowe kolumny są dopisane na końcu; samples v2 i bundle v1 zachowują wersje.
Czytnik wybierający znane kolumny po nazwie może ignorować dodatki. Descriptor
samples dodaje `mt_invocation_identity`, `mt_detection_index_base` oraz
`mt_detection_order`, bez zmiany dotychczasowych pól wymaganych.
Rektyfikacja: NOT_RUN / FAILED / OK. MZ: NOT_RUN / NO_CHARACTERS / READ.
`mz_executed` odróżnia wywołanie backendu przerwane przed dekodowaniem wyniku.
`prediction` jest zawsze świeżym wynikiem MZ, również pustym; nigdy nie jest
uzupełniany poprzednim konsensusem. `recognition_confidence` nowych próbek to
średnia confidence świeżych znaków (0 przy ich braku); osobny
`consensus_confidence` w dzienniku/adnotacji opisuje stan konsensusu.
Anulowana scena ma `stale_or_cancelled=true`, `cancel_reason=scene_superseded`;
STOP podczas pracy ma `session_stopped`. Takich prób nie należy liczyć jako
błędów jakości modelu. Brak obrazu ma jawny `missing_evidence_reason`.

`samples/index.csv` zachowuje stare kolumny i dodaje na końcu: `attempt_id`,
`subject_key`, `scene_generation`, `entity_id`, `vehicle_track_id`, `plate_track_id`.
Te same dane są w `annotations.jsonl`. Wszystkie nowe cropy mają
`verification_status=not_reviewed`, pusty GT. `report.crop_session.records_file`
wskazuje trwałe adnotacje zamiast ograniczonej galerii, a `quality` jawnie czeka
na weryfikację desktopową. Ręczna weryfikacja galerii pozostaje funkcją
diagnostyczną poza trwającym badaniem; nie modyfikuje gotowej paczki źródłowej.

Tożsamość: `<session>/sg-<generation>/entity-<id>`, tymczasowo `/track-<id>` lub
`/attempt-<id>`. OCR nigdy nie służy do identyfikacji. Późniejsze przypisanie
promuje fallback w końcowych CSV/adnotacjach; append-only dzienniki zachowują
oryginalny przebieg. Odtworzenie Activity zachowuje kolektor, t0, timer i config,
a nowy pipeline rozpoczyna kolejną generację sceny, by nie zderzyć nowych tracków
z identyfikatorami wcześniejszej instancji kamery.

Writer ma kolejkę 256 zadań, maksymalnie 64 przyjęte próby i budżet kopii obrazów
64 MiB. Kompresja JPEG i I/O pracują poza wątkiem inferencji. Przekroczenie limitu
lub błąd I/O zwiększa licznik strat, zapisuje zdarzenie i prowadzi do PARTIAL/ERROR.
START wymaga co najmniej 128 MiB wolnej przestrzeni w prywatnym magazynie aplikacji.
`storage_prepared=true` i `collection_mode=automatic` są utrwalone przed START.
Przy limicie obrazów rozpoczęty batch nadal zachowuje metadane invocation
i kolejność detekcji, z jawnym powodem braku dowodu. Przepełnienie writer queue
korzysta z istniejącego ograniczonego dziennika odrzuceń; jego utrata jest stratą
sesji, więc paczka nie może służyć jako kompletny pomiar. Po finalizacji
`collection_complete`, `dropped_sample_count`, `dropped_telemetry_count`,
`integrity_loss_count`, `attempt_count` i `crop_count` opisują kompletność.
Ucięte rekordy telemetrii zwiększają `dropped_telemetry_count`; pomocnicze
`integrity_telemetry_loss_count` pozwala ponawiać eksport bez ponownego naliczania strat.

Po restarcie procesu RUNNING/FINALIZING (także osierocone PREPARED) przechodzi
w PARTIAL z `process_interrupted`. Odzyskana paczka nie kontynuuje pomiaru.
`pending_sample_loss_unknown=true` ostrzega o niemożności policzenia niezapisanej
kolejki RAM. Niepełny ostatni rekord dziennika lub brak obrazu obniża kompletność.
ZIP powstaje po opróżnieniu writer queue, jest weryfikowany SHA-256 i dopiero
wtedy publikowany; stan COMPLETED jest utrwalany po publikacji.

Każdy nowy wpis podlega `entry_sha256`. `samples_self_contained` opisuje pełność
dowodów, natomiast dotychczasowe `self_contained=false` nadal dotyczy braku wag
modeli w archiwum. Pełny dziennik trace’ów odtwarza `traces.csv` niezależnie od
limitu ring buffera UI; statystyki raportu zachowują deklarację własnego okna retencji.

## Tożsamość i kompletność eksperymentu

Sekcja `report.json/experiment` przechowuje zamrożone przy starcie:
`series_id`, `scenario_id`, `variant`, `replicate_index`, notatkę operatora,
timer, warunek termiczny oraz konfigurację autozoomu. `app_build` dodaje SHA
commita, stan `clean/dirty/unknown`, typ buildu i czas zbudowania aplikacji.
`git_commit` bez równoczesnego sprawdzenia `source_state` nie jest wystarczającą
identyfikacją APK roboczego.
Przed START `session.json` zamraża `app_version` oraz `app_build` z
`git_commit`, `git_dirty`, `git_dirty_available`, `built_at_utc`. Finalny raport
odtwarza te pola z sesji także po odzyskaniu pod innym APK. Build bez `.git`
ma `git_dirty_available=false` i `source_state=unknown`; samo `git_dirty=false`
nie oznacza wtedy czystego commita.

`experiment.effective_execution_config` pozwala odtworzyć wykonanie bez
odwoływania się do aktualnych preferencji telefonu. Dla MP/MT/MZ zawiera
`model_id`, fingerprint, `variant_id`, runtime, precyzję, liczbę wątków,
CPU/GPU/delegata i efektywne wejście. Globalnie zapisuje żądaną rozdzielczość,
R0/R1/R2, profil rozpoznawania oraz flagi lock, autozoom i mechanizmów
tracking/temporal. Konfiguracja nie może zmienić się do STOP.

`data_retention` raportuje pojemność ring buffera, całkowitą liczbę trace'ów,
liczbę zachowaną i usuniętą oraz zakres czasu zachowanych rekordów. Utrata
najstarszych trace'ów ustawia `data_completeness.status = incomplete`; nie jest
już cicha. Brak etapu pozostaje brakiem pola/pustą komórką, a nie zerem.

## Geometria i trudność obrazu

Rekordy cropów oraz `samples/annotations.jsonl` zawierają bbox w pikselach,
cztery narożniki znormalizowane, udział bbox/quad w powierzchni klatki,
stan konsensusu i numer próby MZ. Metryki luminancji, kontrastu i ekspozycji są
liczone tylko dla zapisywanego cropa; raportowany jest również koszt ich
wyliczenia w `image_difficulty.computation_ms`.

## Ground truth

Każdy crop ma niezależny stan:

- `not_reviewed`;
- `accepted` — ground truth jest równy oryginalnej predykcji;
- `rejected` — próbka jest `Nie do oceny` i nie da się wiarygodnie ustalić transkrypcji;
- `corrected` — `ground_truth_text` pochodzi od użytkownika.

Raport zawsze przechowuje predykcję, status, czas, rewizję,
`eligible_for_text_metrics`, `issue_codes`, `needs_desktop_review` i opcjonalną
notatkę. Stare rekordy bez nowych pól pozostają poprawne, a eligibility jest
wyznaczane ze statusu i ground truth. CER i exact match są liczone tylko dla
`accepted/corrected`. Jednostką jakościową
jest unikalna para `session_id + track_id`; kilka klatek tego samego tracku nie
zwiększa sztucznie liczebności próby.

Normalizacja sekwencji jest jawna: wielkie litery i usunięcie białych znaków.
CER jest ilorazem sumy odległości Levenshteina i sumy długości ground truth.
Raport zawiera też średnią znormalizowaną odległość edycyjną per próbka.

## Modele

Standardowy `.alprsession` jest lekkim archiwum i nie osadza pakietów ani wag
(`.alprmodel`, `.tflite`, `.onnx`, `.param`, `.bin`, `.pt`). Kanoniczne portable
identity znajduje się w `report.json/model_refs` i `pipeline/model_refs.json`.
Małe manifesty JSON pozostają w `pipeline/` do celów audytowych.

W `pipeline/model_refs.json` role są zapisane bezpośrednio na poziomie głównym
obok addytywnego pola `schema`; plik nie używa wrappera `models`. Dzięki temu
Desktop odczytuje `plate`, `character` i opcjonalne `vehicle` bez normalizacji.

Referencje są zamrażane przy START razem z efektywnym wariantem. Eksport po STOP
nie odczytuje ich ponownie z aktualnego `ModelRegistry`, dlatego późniejsza
aktywacja innego modelu nie zmienia opisu zakończonej sesji. Dla składanej
konfiguracji `model_refs` opisuje faktycznie użyte MP/MT/MZ, natomiast
`pipeline/package_manifest.json` pozostaje manifestem pakietu bazowego.
Przy START zamrażane są również metadane pakietu bazowego i rozmiar kompozycji
raportowany jako `memory.package_size_mb`. Eksperyment uruchomiony bez pakietu
bazowego nie przejmuje metadanych pakietu aktywowanego dopiero po STOP.

Manifest lekkiego bundle'a zapisuje `model_artifacts_embedded: false`,
`self_contained: false` i `exact_source_package_embedded: false`.
`reproducible_by_model_hash` jest prawdziwe tylko wtedy, gdy wszystkie wymagane
modele mają `package_sha256` albo co najmniej jeden poprawny
`variant_artifact_sha256`. Sam `checkpoint_sha256` identyfikuje checkpoint
treningowy, ale nie dokładny artefakt mobilny i nie wystarcza do reprodukcji wag.

Importer nadal zachowuje dokładny plik `source.alprmodel` w instalacji lokalnej,
aby umożliwić audyt i zarządzanie modelem. Plik źródłowy nie jest jednak
kopiowany do standardowego eksportu badawczego.

`report.json/execution` wiąże osobno MP, MT i MZ z:

- `model_id`, nazwą, wersją i fingerprintem;
- wariantem, runtime'em, precyzją, delegatem i liczbą wątków;
- efektywnym wejściem, dekoderem i progami;
- listą plików, SHA-256 oraz rozmiarem artefaktu;
- rodziną YOLO, liczbą parametrów i datą eksportu, jeżeli manifest je zawiera.

## TeX

`summary.tex` jest samodzielnym dokumentem i używa względnych `\input{}` dla
tabel. Generator escapuje znaki specjalne TeX. Paczka zawiera:

- konfigurację MT/MZ/MP;
- exact match, CER i liczebność ground truth;
- p50/p90/p95/p99 MT, MZ i pipeline'u;
- do 12 cropów w treści dokumentu oraz wszystkie dostępne cropy w katalogu;
- `references.bib`, `metadata.json` i CSV śladów do własnych wykresów.

## Metodyka

`protocol.json` opisuje założenia benchmarku inspirowanego MLPerf Mobile:
single-stream, cel 1024 próbek i 60 sekund, p90 jako percentyl główny. Pole
`mlperf_compliant` ma wartość `false`, ponieważ aplikacja nie korzysta z
oficjalnego LoadGena. `research-v1` opisuje wyłącznie sesję
`camera-in-the-loop`. Kontrolowany replay nie jest częścią tej wersji i nie
wolno przedstawiać wyników live jako czystego benchmarku runtime na identycznym
wejściu. Jeżeli replay stanie się wymaganiem pracy, będzie osobnym runnerem i
nowym checkpointem po zakończeniu bieżącej kampanii.
