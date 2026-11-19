# F6 Android — korekta zgodności MP, dostawa v1, 12.11.2026

Identyfikator: `F6_ANDROID_MP_SCHEMA_COMPATIBILITY_FIX_20261112`.

**Przyczyna: generator syntetycznego testu obciążeniowego oraz niepełna kontrola dostawy. Kod APK nie został zmieniony. Nie jest potrzebna nowa APK z powodu tej korekty.** Wynik kontroli producenta na hoście: **PASS**, 73/73 testy. Odbiór nowej paczki przez natywny czytnik Desktopu: **NOT_VERIFIED**. A12/TV: **NOT_VERIFIED**, DEVICE_RUN=NOT_RUN. Zakończenie pakowania potwierdza wyłącznie `READY.json` w nowym katalogu dostawy.

## Stan i zakres

Repozytorium Androida: `C:\Users\48572\AndroidStudioProjects\ALPR_v1`.
Gałąź: `main`, HEAD: `f7943a7d7334afd1e4188ef9860b2126331926c8`.
Zastany brudny katalog roboczy zachowano; nie wykonano checkout/reset/clean, commit/push, instalacji zależności ani APK. Nie edytowano repozytorium Desktopu, wyświetlarki, GT ani tekstu pracy.

Pliki zmienione w tej korekcie względem stanu zastanego:

| Plik | Zmiana |
|---|---|
| `app/src/test/java/com/example/alpr_v1/experiment/Final180EvidenceTest.java` | Poprawny generator MP OFF, padding poza MP, regresja starego pliku i relacji źródłowych, mocniejsza kontrola przed eksportem |
| `app/src/test/java/com/example/alpr_v1/pipeline/TvBboxPipelineTest.java` | Dodatkowa kontrola MP przy eksporcie istniejących scenariuszy silnika F6 i F5A |
| `app/src/test/java/com/example/alpr_v1/experiment/F6ProducerContractVerifier.java` | Nowa testowa kontrola dostawy: natywny graf F6 plus strumieniowa walidacja MP i jego pochodzenia |
| `app/src/test/java/com/example/alpr_v1/experiment/F6ProducerContractVerifierTest.java` | Nowe regresje wymaganych pól, typów, statusów, populacji MP i ROI |
| `tools/package_f6_mp_schema_fix.py` | Nowy publikator wymagający pozytywnego potwierdzenia kontraktu z SHA każdego eksportu |
| `docs/f6_android_mp_schema_compatibility_fix_20261112_v1.md` | Ten raport |

Cały `app/src/main`, zasoby, konfiguracja modeli i budowania oraz dotychczasowy raport F6 pozostały bez zmian. Kontrola SHA plików zastanych, poprzedniej dostawy, APK i odczytanych materiałów Desktopu znajduje się w `desktop_package/protected_files_audit.json`; stan wejściowy w `desktop_package/audit/before.json`.

## Odtworzenie i rozstrzygnięcie fixture / produkcja

Odczytano dokładnie wskazany raport Desktopu v2, SHA-256 `39e8ced94dce73befd1b153849f306e801d809c36b1aa1a52296a45765683487`, `ANDROID_CONTRACT_GAP.txt` oraz `producer_final_snapshot`. Oryginały pozostały bez zmian.

W starym długim pliku o SHA-256 `0323d3e5d1f79fe32e99c193a6ce71ee558b31ac0d0ed8aca57e0b7c48212f5f` każda z 4100 obserwacji zawierała tylko `execution_status`, `source_stamp`, `synthetic_padding`. Każdej brakowało sześciu zgłoszonych pól. Pierwszy niepełny obiekt już w `SCENE_OBSERVATION.details.mp` jest identyczny z obiektem końcowej projekcji. Błąd powstał przed finalizacją, w metodzie testowej `longSessionStreamsBeyond4096FramesAnd32MiBWithoutRetainingHistory`.

Ścieżki produkcyjne sprawdzono w kodzie:

1. `MobileAlprEngine.run` inicjuje `trace_frame_id`, pełny `source_stamp`, `NOT_RUN`, rzeczywistą przyczynę `mp_not_requested_or_cached`, trzy pola populacji równe JSON `null` oraz `selected_rois=[]`. Przy uruchomieniu MP zapisuje `FAILED` na czas wykonania, a po dekodowaniu `EXECUTED` z rzeczywistą populacją i wybranymi ROI.
2. `F5aEvidence.observation` dla F6 zapisuje niezmieniony obiekt w `details.mp` trwałego dziennika F6. Wywołanie `static_idle` z `mp=null` nie tworzy fikcyjnej obserwacji modelu.
3. `Final180Evidence.finalizeStored` przenosi `details.mp` do sceny, bez usuwania lub uzupełniania jego pól. Recovery używa tej samej ścieżki przez `ResearchSessionStore.buildArchive` i finalizację próbek.
4. Dotychczasowy `Final180ArchiveValidator` pomijał wnętrze MP w scenie. Stary plik otrzymywał z niego `PASS` i deklarował `complete=true`, mimo niezgodnego MP. **To była luka kontroli dostawy; historyczne PASS nie dowodziło pełnej zgodności.** Natywnego walidatora APK nie zmieniano — nowa kontrola dostawy działa w `src/test`.

Nie wykryto utraty tych pól w produkcyjnym serializerze, finalizacji ani recovery. Nie jest to deklaracja odbioru rzeczywistej sesji telefonu. Dowód odtworzenia i charakterystyka poprawionego pliku: `desktop_package/audit/source_diagnosis.json`.

Test `deliveryGateRejectsTheExactPreviousLongArchive` sprawdza dokładny stary SHA, potwierdza historyczne przepuszczenie przez wąski walidator i wymaga odrzucenia przez nową kontrolę z `MP_OBSERVATION_FIELDS_MISSING`. **Wynik kontraktu starej dostawy pozostaje FAIL.** PASS tej regresji oznacza wyłącznie poprawne wykrycie starego błędu.

## Minimalna korekta generatora i kontrakt MP

Zachowano `alpr.scene_evidence.v1`, `alpr.f6_execution_event.v1`, dotychczasowy format archiwum i kontrakt `F6_FINAL180_SPLIT_V1`. Nie zmieniono znaczenia żadnego istniejącego pola.

Każda z 4100 obserwacji nowego testu R0 jest jawną syntetyczną obserwacją MP OFF:

| Pole | Typ i wartość w teście R0 |
|---|---|
| `trace_frame_id` | Nieujemny int64, rzeczywisty licznik syntetycznej klatki `n`, ten sam co `FRAME_FINISHED.trace_frame_id` |
| `source_stamp` | Dokładnie sześć istniejących pól SourceStamp tej klatki |
| `execution_status` | String `NOT_RUN` |
| `not_run_reason` | String `mp_disabled_in_synthetic_r0` |
| `raw_count` | JSON `null` |
| `rejected_class_count` | JSON `null` |
| `rejected_class_ids` | JSON `null` |
| `selected_rois` | Pusta tablica JSON |

Nie utworzono wykonań MP ani MT. Nie zastąpiono NOT_RUN wykonaną detekcją z zerem. Populacja wykonanego MP jest sprawdzana dodatkowo w istniejących testach silnika R1/R2: syntetyczny backend rzeczywiście działa, dekoder daje dwie detekcje, wybierane są odpowiednio jeden i dwa ROI. Te dwa eksporty mają profil F5A TV_BBOX_CROP i są osobno w `legacy_regression/`; nie udają konfiguracji F6 R0.

Istniejąca semantyka pozostałych stanów:

- `EXECUTED`: `raw_count` i `rejected_class_count` są nieujemnymi liczbami całkowitymi, `rejected_class_ids` jest tablicą całkowitych ID klas; długość tablicy zgadza się z liczbą odrzuceń, nie większą od populacji surowej.
- `FAILED`: nie wymaga fikcyjnej zdekodowanej populacji. Zachowuje identyfikację, status i rzeczywiste dostępne pola.
- `selected_rois`: tablica obiektów z całkowitymi nieujemnymi `left`, `top`, `right`, `bottom`, `entity_id`, `vehicle_track_id`; szerokość i wysokość muszą być dodatnie. Przy zgodności ze źródłem zapisanej sceny ROI musi mieścić się w jej bitmapie. NOT_RUN może w ogólnym historycznym kontrakcie zawierać ROI z cache; korekta nie zakazuje tego ani nie tworzy takich ROI dla R0.

SourceStamp zachowuje `scene_generation`, `visual_epoch`, `camera_transform_generation`, `source_sequence`, `source_timestamp_nanos` jako nieujemne int64 oraz `source_timestamp_domain`. Domeny istniejącego kontraktu: `CAMERAX_SENSOR`, `PREVIEW_INHERITED_CAMERA`, `RUNTIME_UPTIME`, `UNKNOWN`. W fixture użyto `CAMERAX_SENSOR`, nanosekund, w tym wartości ponad 2^53. Nie wyrównuje się ich do zegara PC. Zdarzenia F6 używają `ANDROID_ELAPSED_REALTIME`, `elapsed_nanos` w ns.

## Padding, skala i kontrola pochodzenia

Usunięto `synthetic_padding` z obserwacji MP. Obciążenie znajduje się wyłącznie w dodatkowym polu na poziomie sceny:

```json
"_synthetic_test": {
  "purpose": "serialization_load_only",
  "not_model_output": true,
  "padding": "33100000 znaków ASCII x — opis, nie dosłowna wartość"
}
```

Jest to jawna testowa metadana sceny. Obecny kontrakt dopuszcza dodatkowe pola sceny; nie dodano nic do ścisłego SourceStamp. Harness testowy używa istniejącego prywatnego serializera `F5aEvidence.journal("SCENE", ...)` przez refleksję **przed finalizacją**, na świeżo wygenerowanej scenie. Następnie pracują istniejący writer, projekcja strumieniowa i `ResearchArchive`. Nie dopisywano pól do gotowego ZIP.

Nowy długi eksport:

| Własność | Wynik |
|---|---|
| Klatki TV | 4100, ponad historyczne 4096 |
| Obserwacje MP | 4100 kompletnych `NOT_RUN`, 0 `EXECUTED`, 0 `FAILED` |
| Zdarzenia F6 | 12 300 |
| Rozpoczęte MT | 0, zgodnie z charakterem testu magazynu |
| Jedna scena JSONL | **34 677 685 B**, ponad 32 MiB i ponad stare 34 608 299 B |
| Zegar syntetyczny | 5400 s; nie jest pomiarem czasu działania urządzenia |
| Limit sterty testowej JVM | **536 870 912 B / 512 MiB** |
| Cache TV w teście | Nie więcej niż 64 rekordy |
| Rozmiar archiwum | 7 022 677 B |
| SHA-256 archiwum | `f4c9c9976e460ad85cb8e655311f45dbb090c9f8368dd0644fc70ec8ebff50d9` |

Limity produkcyjne nie zostały zwiększone. `ExternalJsonSort`: nadal bufor 4 MiB, fan-in 16, limit wiersza 33 554 432 znaków. Duży nagłówek testowej sceny mieści się w tym limicie; końcowa scena z tablicą obserwacji może być większa. Kontrola dostawy czyta pojedyncze MP, używa krótkiego pliku tymczasowego dla niezależności od kolejności kluczy JSON i zewnętrznego sortowania do złączeń. Nie materializuje całej tablicy MP ani paddingu podczas walidacji. Sam generator jednorazowo tworzy testowy padding w pamięci. Limit 512 MiB jest limitem JVM tego testu hostowego, nie zmierzonym zużyciem RAM APK ani gwarancją termiczną telefonu.

Nowy `F6ProducerContractVerifier.validate` najpierw uruchamia istniejącą kontrolę hashy i grafu F6: SourceStamp/frame_key → MT_STARTED/mt_invocation_id → komplet ról FINAL i prób → rzeczywiste MZ. Następnie sprawdza MP: wymagane pola, typy, statusy, populacje, ROI, SourceStamp, zgodność z klatką TV i `FRAME_FINISHED.trace_frame_id` oraz zgodność końcowej projekcji z trwałym `SCENE_OBSERVATION.details.mp`. Brak źródła wymaga deklaracji utraty; konflikt identyfikacji jest błędem. Jawne archiwa PARTIAL zachowują taki status.

Kontrola jest uruchamiana przed skopiowaniem każdego eksportu testowego. Zapisuje potwierdzenie z SHA archiwum. Publikator wymaga tego pozytywnego potwierdzenia i poprawnych wyników testów, sprawdza CRC i wszystkie SHA wewnętrznego manifestu. Sama obecność pliku, stare `complete=true` ani oczekiwane odrzucenie innego pliku nie wystarczają do oznaczenia nowego eksportu jako zgodnego.

Układy geometrii i relacje F6 pozostają zgodne z poprzednim raportem: `source_image.coordinate_space=camera_full_bitmap_xy`, `crop_image.coordinate_space=tv_bbox_local_xy`. Historyczne `source_frame_coordinate_space=mt_source_bitmap_xy` oznacza pełne źródło przed ROI. `tv_bbox_local_geometry` i `roi_local_xy` przechodzą do pełnego źródła przez rzeczywiste offsety cropu; `processing_transform=CROP_ONLY_TRANSLATION`, `H_TV=NOT_APPLIED`. `scene_event_id` nie jest `exposure_id`. Weryfikacja geometrii i relacji MT/FINAL/MZ została powtórzona na małych eksportach rzeczywistej ścieżki silnika z syntetycznymi backendami.

## Wykonane testy

Jeden zakończony przebieg regresyjny: **73 testy, 0 failures, 0 errors, 0 skipped**, 6 zestawów. Nie sumuje się go z historycznymi 1353 testami F6. Pierwsza próba kompilacji nowych testów zatrzymała się przed ich wykonaniem na nieobecnym w Android API `JSONObject.keySet`; użyto istniejącego `keys()`. Nie był to osobny przebieg testów. Zachowano jego log diagnostyczny.

| Zestaw | Liczba | Wynik / zakres |
|---|---:|---|
| `F5aEvidenceTest` | 30 | PASS: historyczny zapis F5A, populacje, role, braki, STOP i recovery |
| `F6ProducerContractVerifierTest` | 5 | PASS: brak każdego wymaganego pola, typy, NOT_RUN vs wykonane zero, SourceStamp, odrzucenia klas, ROI, FAILED |
| `Final180EvidenceTest` | 8 | PASS: 4100 klatek, duża scena, stary dokładny SHA, błędny source/trace, pełna kolejka, brak obrazów, recovery i uszkodzony ogon |
| `ResearchExecutionEvidenceContractTest` | 9 | PASS: kontrakt wykonania i eksportu |
| `TvBboxEvidenceTest` | 8 | PASS: geometria, obrazy, limity i jawne braki |
| `TvBboxPipelineTest` | 13 | PASS: rzeczywista ścieżka silnika z syntetycznymi backendami, R0 MP OFF, zero/wiele/duplikaty FINAL, MZ brak/błąd, MT błąd, anulowanie, opóźniony wynik, R1/R2, brak regresji wejść |

Dokładne XML i stdout, w tym `F6_SCALE ... max_heap_bytes=536870912` oraz `F6_PREVIOUS_DELIVERY_CONTRACT=FAIL ... MP_OBSERVATION_FIELDS_MISSING`, są w `desktop_package/test_results/`. `validation.json` podaje liczby tylko tego przebiegu. `git diff --check`: PASS; ostrzeżenia LF/CRLF dotyczyły zastanego katalogu. Nie wykonywano ponownie całego zestawu UI ani build/assemble APK — zmiany dotyczą testów i dostawy.

Test opóźnionego wyniku w istniejącym silniku zmienia generację sceny podczas pracy MT. Późniejsza próba i zdarzenia MT/MZ nadal wskazują pierwotną klatkę/generację 1. Osobny przypadek anulowania zachowuje faktyczny wyjątek i `stale_or_cancelled`; nie wymusza przyjęcia starego wyniku ani zakończenia w 5 s.

## Nowa wersjonowana dostawa

Po zakończonym pakowaniu:

```text
C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f6_mp_schema_compatibility_fix_20261112_v1\
  READY.json
  desktop_package.zip
  report.md
  delivery_sha256.json
  SHA256SUMS.txt
  desktop_package\
    fixtures\long-session-4100-frames.alprsession
    fixtures\... 19 małych eksportów regresyjnych ...
    legacy_regression\tv-r1.alprsession
    legacy_regression\tv-r2-two-roi.alprsession
    negative_evidence\previous-long-session-4100-frames.INVALID.alprsession
    fixture_index.json
    validation.json
    report.md
    source_sha256.json
    protected_files_audit.json
    sources\... kopie generatora, kontroli i niezmienionego producenta ...
    test_results\...
    audit\...
    provenance\previous_android_contract.md
    SHA256SUMS.txt
```

Desktop powinien pobrać **nowy `desktop_package.zip` po obecności READY**, zweryfikować jego SHA z `READY.json` lub `delivery_sha256.json`, a następnie użyć `fixture_index.json`. `fixtures/` zawiera 20 podstawowych eksportów: poprawiony długi plus te same 19 rodzajów małych przypadków. `legacy_regression/` to dwa dodatkowe eksporty z wykonanym MP. `negative_evidence/` jest celowo niezgodnym starym plikiem; nie należy importować go jako poprawnej populacji dostawy.

`delivery_sha256.json` i oba `SHA256SUMS.txt` podają SHA wszystkich końcowych archiwów, tego raportu i generatorów. `source_sha256.json` zawiera też SHA produkcyjnych serializerów, recovery, eksportera i natywnego walidatora. Raportu nie hashuje się wewnątrz niego samego. ZIP i katalog z jego zawartością powstają w nowym katalogu staging; nowa wersja jest publikowana przez pojedynczą zmianę nazwy po walidacji i spakowaniu. Nie nadpisuje się poprzedniej dostawy ani już opublikowanej wersji.

Zachowane poprzednie materiały:

| Materiał | SHA-256 |
|---|---|
| Stare wadliwe długie archiwum | `0323d3e5d1f79fe32e99c193a6ce71ee558b31ac0d0ed8aca57e0b7c48212f5f` |
| Stary `app/build/f6_final180_20261112/desktop_package.zip` | `f80e7e85d72617ffafeb5eb275d045d7a0e8bfea5cf52e88c1ce3dfad9707e0f` |
| Stary `docs/f6_final180_android_contract_20261112.md` | `0379c88a70a53e7912c045735fd1fac5e2bc9c0322c5aee21538f2958a18bb83` |
| Dotychczasowa APK; nie budowano i nie instalowano nowej | `4c7c2ba4ac0d305ca0b6d4d4ff27c4c7cd18690fe5ff318385a2407ff643fc07` |

## Odtworzenie przez programistę i odbiór stanowiska

Do nowej generacji użyć **nowego** katalogu roboczego/wersji, nigdy starej dostawy. Zmienne środowiskowe testów: `F6_FIXTURES` na katalog świeżych eksportów F6, `F5A20_FIXTURES` na świeże eksporty historyczne, `F6_CONTRACT_RESULTS` na świeży plik potwierdzeń JSONL, `F6_PREVIOUS_BAD_ARCHIVE` na zachowany plik o dokładnym starym SHA. Ograniczenie testowej sterty jest zapisane w dostarczonym `audit/bounded-heap.gradle`. Wykonany Gradle offline: `testDebugUnitTest` z `--tests` dla sześciu zestawów z tabeli i `-I` dla tego skryptu. Kopie XML i potwierdzeń stanowią jeden przebieg `final_run`; nie uruchamiano dodatkowo powtarzającego go zestawu.

Publikator `tools/package_f6_mp_schema_fix.py` jest celowo przypisany do wersji v1 i odmawia nadpisania jej katalogu. Nie generuje danych ani nie naprawia archiwów. Wymaga wcześniej zakończonej generacji/testów, kopii ich wyników oraz tego raportu.

Android pozostaje **Timer → Bez timera**. Istniejące ustawienia to **F6 / FINAL180 — zapis strumieniowy**, **Kalibracja TV** i **Obszar fotografii F6**, z R0 bez MP i wcześniej wybraną konfiguracją rozpoznawania. Operator uruchamia jedną sesję START przed pokazem, kończy STOP po pokazie i eksportuje istniejącą ścieżką sesji. Korekta nie zmienia tych ustawień ani parametrów przetwarzania.

Nowa decyzja operatora dotyczy PC: 176 fotografii × 5 s = 880 s, 5 s odliczania, 175 przejść × 0,5 s = 87,5 s, łącznie **972,5 s / 16:12,5** nominalnego pokazu. Przygotowanie/manifesty zmienia agent Desktop. Android nie otrzymał timeoutu 5 s, interwału czyszczenia wyników ani resetowania scen. Zachowano strumieniowy F6 oraz test 4100 klatek niezależnie od krótszego pokazu.

Nie wykonano: natywnego importu **nowej** paczki przez Desktop (osobny agent), odczytu prawdziwych kodów na pełnej zapisanej klatce A12, potwierdzenia ich braku w rzeczywistych pikselach MT na stanowisku, pracy na prawdziwym TV, rzeczywistej sesji 176 × 5 s, testu cieplnego 90 minut ani pomiaru latencji/narzutu na urządzeniu. Wszystkie te odbiory: **NOT_VERIFIED**. Nie generowano skuteczności względem GT. Część Androida ma gotową do przekazania korektę syntetycznej dostawy; pełne GO eksperymentu wymaga osobnego wspólnego odbioru Desktop + A12 + TV.
