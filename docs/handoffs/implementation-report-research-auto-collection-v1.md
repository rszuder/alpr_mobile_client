# Raport: automatyczna trwała kolekcja sesji badawczej v1

Data: 2026-09-08. Baza `506649b`; gałąź `feature/research-session-auto-collection-v1`.
Przed rozpoczęciem potwierdzono czyste drzewo. Zachowano wdrożenia wyboru
wariantów modeli, STATIC/DYNAMIC, fokus/lock i kolejkę AZ.

## 1. Zmienione pliki

- `experiment/ExperimentSession.java`: przygotowana tożsamość i jawne znaczniki START/STOP.
- Nowe `experiment/ResearchSessionStore.java`, `ResearchSessionViewModel.java`,
  `ResearchAttemptBatch.java`, `AcquisitionAttemptRecord.java`, `ResearchSampleIdentity.java`:
  trwały writer, lifecycle, dane prób, tożsamość i finalizacja.
- `MainActivity.java`, `strings.xml`: automatyczne START/STOP/timer, odtworzenie
  Activity, etykieta „Zapis automatyczny”, blokada ręcznej weryfikacji podczas
  pomiaru oraz wybór zapisanej pełnej/niepełnej paczki do eksportu.
- `pipeline/AlprPipeline.java`, `MobileAlprEngine.java`, `PlateObservation.java`,
  `vision/BitmapTensorPreprocessor.java`: pasywne punkty audytu wywołań i kopia
  rzeczywistego wejścia MT przed zwolnieniem bitmapy; bez zapisu obrazów w inferencji.
- `capture/CapturedPlateItem.java`: identyfikacja próby/subject w podglądzie.
- `metrics/MetricsCollector.java`, `ResearchArchive.java`: wspólne zegary,
  strumienie trwałej telemetrii, eksport z plików, samples v2, kompletność i SHA-256.
- `continuity/SceneTransitionCoordinator.java`: po odtworzeniu instancji kamery
  generacja pozostaje większa od poprzedniej; nowe tracki nie kolidują w tej samej sesji.
- Trzy nowe zestawy Android: `ResearchSessionStoreInstrumentedTest`,
  `ResearchAutoCollectionInstrumentedTest`, `ResearchAttemptAuditInstrumentedTest`.
- Dokumentacja architektury, trybu badawczego, eksportu, schematy JSON,
  dziennik budowy, kopia handoffu i plan; `tools/validate_research_session.py`.

## 2. START

Walidacja → zamrożony config → `ExperimentSession.prepare()` → katalog sesji
i `session.json=PREPARED` → gotowy writer → wspólne t0 → RUNNING domeny i metryk
oraz automatyczna kolekcja. Niepowodzenie utworzenia katalogu/uzbrojenia magazynu
nie uruchamia domeny, timera ani nowej sesji metryk. Minimalne wolne miejsce: 128 MiB.

## 3. Katalog

Prywatna przestrzeń aplikacji:

```text
/data/user/0/com.example.alpr_v1/files/research/sessions/<session_id>/
  session.json
  samples/attempts.jsonl
  samples/crops.jsonl
  samples/write_states.jsonl
  samples/crops/<attempt_id>.jpg
  samples/evidence/<attempt_id>.jpg
  telemetry/...
  pipeline/...
  final/<session_id>.alprsession
```

Eksport UI kopiuje gotowy ZIP do miejsca wybranego przez operatora. Nie wybiera
cropów do sesji; lista paczek jawnie oznacza niekompletne przebiegi.

## 4. Zapis cropów

Próba rezerwuje miejsce w ograniczonym magazynie. Kopia bitmapy powstaje przed
zwolnieniem wejścia przez pipeline; JPEG i pliki zapisuje jeden background writer.
Limity: 256 zadań, 64 przyjęte próby, 64 MiB kopii obrazów. Nadmiar i błędy mają
FAILED/zdarzenie/licznik strat. Galeria i jej polityka próbkowania nie sterują magazynem.

Każde wywołanie MZ ma rektyfikowany crop albo jawny powód braku obrazu.
Świeża pusta predykcja pozostaje pusta; wcześniejszy konsensus nigdy jej nie zastępuje.
Nowe próbki raportują średnią confidence świeżych znaków, osobno od confidence konsensusu.
Crop/attempt mają wspólny `attempt_id`. Tożsamość jest oparta na session + scene + entity;
fallback technicznego tracku jest promowany przy późniejszym przypisaniu tej samej sceny.

## 5. MT miss

Każde wykonane MT jest obserwowane przed filtrami UI. NO_DETECTION zachowuje
JPEG dokładnego letterbox ROI/full-frame, który posłużył do utworzenia tensora.
Metadane wejścia i offset ROI pozwalają powiązać go ze źródłem. Rekord odfiltrowanej
detekcji pozostaje z MZ NOT_RUN. Anulowanie sceny lub STOP jest oznaczone osobno;
nie jest interpretowane jako błąd detekcji/modelu.

## 6. CSV i integralność

Zachowano `alpr.mobile_research_bundle.v1` i dotychczasowe pliki/kolumny.
Dodano `samples/schema.json` (`alpr.mobile_research_samples.v2`), `attempts.csv`,
obrazy evidence oraz sześć kolumn identyfikacji do index.csv. Pełna lista pól
i statusów znajduje się w `docs/mobile_research_export.md`.
GT nowych próbek pozostaje pusty, status to `not_reviewed`, review_location=desktop.
Wszystkie nowe pliki są w `entry_sha256`; Pythonowy walidator niezależnie czyta
ZIP/CSV/JSON, sprawdza hashe, powiązania prób/cropów i świeże predykcje.

## 7. STOP i finalizacja

STOP/timer zamyka gate i zapisuje wspólny czas końca domeny/metryk/magazynu
przed oczekiwaniem na kamerę, in-flight work lub writer. Finalizacja czeka na
rozpoczętą pracę i zapis, składa indeksy, sprawdza brakujące dane, tworzy ZIP
tymczasowy, weryfikuje hashe i publikuje paczkę. Dopiero potem utrwala COMPLETED.
Straty danych dają PARTIAL; błąd samej finalizacji daje ERROR.

## 8. Crash recovery

Nowy proces wykrywa RUNNING/FINALIZING oraz osierocone PREPARED, oznacza PARTIAL
z `process_interrupted` i buduje paczkę diagnostyczną z zapisanych danych.
Nie kontynuuje pomiaru. Niepełne ostatnie rekordy/brakujące obrazy obniżają
kompletność. `pending_sample_loss_unknown` ujawnia nieznaną zawartość utraconej
kolejki RAM. Sesje PARTIAL/ERROR bez ZIP-a można odzyskać przy następnym uruchomieniu,
gdy zapis jest ponownie możliwy. Błąd jednego katalogu nie zatrzymuje sprawdzania kolejnych.

## 9. Zakres prób akceptacyjnych

Końcowa kompilacja `assembleDebug`, `assembleDebugAndroidTest` i
`testDebugUnitTest` zakończona sukcesem: **543 testy JVM, bez błędów**.
Pełna regresja na Samsungu SM-A125F: **145 testów Androida, wszystkie przeszły**
(132,211 s), w tym opt-in rzeczywistych modeli i kamery:

```text
adb shell am instrument -w -r -e liveResearch true -e installedVariants true com.example.alpr_v1.test/androidx.test.runner.AndroidJUnitRunner
python tools/validate_research_session.py app/build/reports/gallery-qa/research-qa-auto.alprsession app/build/reports/gallery-qa/research-qa-mz.alprsession
```

Niezależna walidacja końcowych paczek na komputerze:

| Paczka | Stan | Próby | Cropy | Zweryfikowane SHA-256 | Wynik |
| --- | --- | --- | --- | --- | --- |
| `research-qa-auto.alprsession` | COMPLETED | 6 | 5 | 31 | Bez ostrzeżeń; 3 READ, 2 NO_CHARACTERS, 1 NOT_RUN. |
| `research-qa-mz.alprsession` | COMPLETED | 4 | 4 | 27 | Bez ostrzeżeń; 4 READ, w tym 1 anulowane przez zmianę sceny. |

Wcześniejszy rzeczywisty przebieg kamery bez tablic potwierdził 10 MT miss
z obrazami wejścia. Końcowa scena kamery zawierała już detekcje; obie sytuacje
uzupełniają testy statusów. Obejrzano także wyeksportowany crop MZ.
Logi i paczki lokalne: `app/build/reports/gallery-qa/`, m.in.
`research-auto-final-regression.log` i `research-auto-archive-validation.log`.
APK zainstalowano przez `adb install -r`, zachowując dane aplikacji.
Pierwszy długi przebieg napotkał błędy lifecycle po uśpieniu ekranu;
końcową regresję wykonano przy aktywnym ekranie. Ustawienia operatora przywrócono.

| Próba | Weryfikacja |
| --- | --- |
| A1 | PREPARED istnieje przed RUNNING; wspólne t0 magazynu/domeny/metryk. |
| A2 | Plik zamiast katalogu i krytyczny brak miejsca blokują przygotowanie. |
| A3 | Jeden START MainActivity włącza automatyczny zapis; ręczny toggle go nie wyłącza. |
| A4 | Timer sam kończy pomiar, zamyka kolektor i tworzy ZIP. Czas końca zgodny w domenie i pliku. |
| A5 | Trzy i więcej rzeczywistych wywołań MZ mają własne cropy; porównanie predykcji z/bez kolektora. |
| A6 | Rzeczywisty przebieg kamery bez tablic daje MT miss z obrazami wejścia. |
| A7 | Anulowana praca rzeczywistego silnika ma crop i scene_superseded. |
| A8 | Przypisanie encji promuje wcześniejszy fallback tracku. |
| A9 | Ten sam tekst po zmianie generacji daje inny subject_key. |
| A10 | Wymuszone błędy kompresji/zapisu i przepełnienie writer queue kończą się PARTIAL. |
| A11 | Własne kopie magazynu nie zależą od bitmap i limitu galerii. |
| A12 | ActivityScenario.recreate zachowuje ten sam ViewModel, store, t0 i frozen config. |
| A13 | Trwały RUNNING z identyfikatorem poprzedniego procesu jest odzyskiwany jako PARTIAL. |
| A14 | Weryfikacja wszystkich hashy w ZIP przez Androida i niezależny parser Python. |
| A15 | Wariant MT w paczce odpowiada snapshotowi sprzed odtworzenia Activity; regresja istniejącego freeze. |

## 10. Ograniczenia

- Nie uruchamiano GUI zaktualizowanej aplikacji desktopowej. Zgodność sprawdzana
  jest na poziomie udokumentowanego kontraktu ZIP/CSV/JSON i SHA-256.
- Test crash recovery używa rzeczywistych plików z oznaczeniem poprzedniego procesu;
  nie jest kampanią losowych odcięć zasilania podczas każdej instrukcji zapisu.
- Obrazy JPEG są dowodem wizualnym wejścia/cropa, a nie binarnym zrzutem tensora.
- Kopiowanie obrazu i serializacja metadanych mają koszt; kompresja i I/O są
  poza inferencją. Limity kolejki nie gwarantują braku strat przy dowolnym obciążeniu,
  ale każda wykryta strata obniża kompletność zamiast udawać pełną sesję.
