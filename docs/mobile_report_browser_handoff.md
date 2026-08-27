# Handoff: przeglądarka raportów mobilnego ALPR

## Cel

Zaimplementować w aplikacji macierzystej przeglądarkę plików eksportowanych przez
klienta Android. Narzędzie ma służyć do analizy metryk wydajności, jakości oraz
diagnostyki przebiegu, bez uruchamiania modeli dołączonych do paczki.

## Obsługiwane wejścia

Eksport inicjuje `MainActivity.showExportOptions()`, a zapis wykonuje
`MainActivity.writeResearchExport()`:

| Plik | Kontrakt | Najważniejsza zawartość |
| --- | --- | --- |
| `*.alprsession` | `alpr.mobile_research_bundle.v1` + `alpr.mobile_experiment_telemetry.v1` | `manifest.json`, pełny `report.json`, `traces.csv`, `thermal.csv`, `frame_flow.csv`, `events.jsonl`, log, środowisko, cropy i adnotacje, manifesty/artefakty modeli |
| `alpr_benchmark_report_*.zip` | zgodność wsteczna; `report.json.schema = alpr.mobile_benchmark_report.v1` | `report.json`, `traces.csv`, opcjonalny `application.log`, `README.txt` |
| `alpr_thesis_*.zip` | `alpr.mobile_thesis_bundle.v1` | `manifest.json`, `metadata.json`, `tables/trace_data.csv`, gotowe tabele i dokument TeX, wybrane cropy |

MVP powinno w pełni analizować `.alprsession` i klasyczny raport ZIP. Paczkę TeX
można otwierać w trybie skróconym na podstawie `metadata.json` oraz
`tables/trace_data.csv`.

## Otwieranie i walidacja

1. Traktować wszystkie trzy formaty jako ZIP niezależnie od rozszerzenia.
2. Odrzucać ścieżki absolutne, `..`, duplikaty wpisów i niekontrolowane
   rozpakowanie (ochrona przed Zip Slip i zip bomb).
3. Jeśli istnieje `manifest.json`, zweryfikować go według
   `alpr-mobile-research-bundle-v1.schema.json`, a następnie policzyć SHA-256
   wpisów wymienionych w `entry_sha256`. Manifest nie hashuje samego siebie.
4. Dla raportu głównego wymagać
   `report.json.schema == "alpr.mobile_benchmark_report.v1"`. Nie ma jeszcze
   osobnego JSON Schema dla `report.json`: parser ma ignorować nieznane pola,
   bezpiecznie obsługiwać brak pól opcjonalnych i zachować surowy JSON do
   diagnostyki.
5. Nie ładować całych archiwów, modeli ani wszystkich obrazów do RAM. Czytać
   wpisy strumieniowo, a miniatury cropów dekodować na żądanie.

## Model danych i ekrany MVP

`report.json` jest źródłem danych zagregowanych, a `traces.csv`/`traces[]`
źródłem danych per klatka. Nie należy sumować obu źródeł.

- **Podsumowanie:** `report_id`, czas pomiaru i sesji, urządzenie, wersja
  aplikacji, `package_id`, `variant_id`, liczba przetworzonych i odrzuconych
  klatek oraz statusy z `summary.statuses`.
- **Konfiguracja:** `capture`, `recognition_profile`, `normal_configuration`,
  `experiment`, `execution.vehicle|plate|character`, `runtime_composition` i
  `autotune_profiles`. Pokazać osobno MP (vehicle), MT (plate) i MZ (character),
  ponieważ mogą używać różnych runtime'ów/delegatów.
- **Opóźnienia:** `latency.mt|mz|pipeline` oraz `summary.stages`; prezentować
  `count`, mean, p50, p90, p95, p99, min, max i odchylenie. Wykres per klatka
  budować z kolumn `*_ms` w `traces.csv`.
- **Diagnostyka:** `errors`, `summary.counters`, status/text/confidence per
  klatka, użycie PSS/native heap i `application.log`. Warto filtrować m.in.
  `pipeline_error`, `no_plate`, klatki pominięte oraz skoki czasu/pamięci.
- **Kampania:** `experiment.series_id|scenario_id|variant|replicate_index`,
  `app_build.git_commit`, status zakończenia i ostrzeżenie z `data_completeness`.
- **Termika i przepływ:** szeregi z `thermal.csv` oraz `frame_flow.csv` łączyć po
  `experiment_session_id + elapsed_ms`; luki upstream są wyłącznie estymacją.
- **Zdarzenia:** oś czasu `events.jsonl` dla tracków, MZ, konsensusu i autozoomu;
  nie utożsamiać `track_confirmed` z ręcznym ground truth.
- **Jakość:** `quality.available`, liczebności, exact match, CER i próbki.
  Gdy `available == false`, wyświetlić `quality.reason`; confidence ani
  `observed_recognition_yield` nie mogą być etykietowane jako accuracy.
- **Cropy:** `crop_session.records` oraz — w `.alprsession` —
  `samples/index.csv`, `samples/annotations.jsonl` i `samples/crops/*.jpg`.
  Statusy walidacji człowieka to `not_reviewed`, `accepted`, `rejected` i
  `corrected`.
- **Surowe dane:** podgląd `report.json`, manifestu i logu oraz możliwość
  zapisania wybranego wpisu bez modyfikowania oryginalnego archiwum.

Metryki jakości są liczone na unikalnej parze `session_id + track_id`, tylko dla
`accepted`/`corrected` z ground truth. Normalizacja to uppercase i usunięcie
białych znaków. Nie przeliczać CER per klatka ani na podstawie `rejected` bez
transkrypcji.

## Sugerowany podział implementacji

1. `ReportBundleReader` — rozpoznanie typu, bezpieczny indeks ZIP, manifest i
   kontrola sum.
2. `MobileBenchmarkReportParser` — tolerancyjne DTO dla wersji
   `alpr.mobile_benchmark_report.v1`.
3. `TraceReader` — strumieniowy parser CSV; JSON `traces[]` jako fallback.
4. `TelemetryReader` — strumieniowe `thermal.csv`, `frame_flow.csv` i JSONL;
   brak pola zachowuje jako null, nigdy jako zero.
5. Warstwa widoków: podsumowanie, konfiguracja, latency, jakość, diagnostyka,
   cropy i surowe pliki.
6. Testy fixture dla każdego z trzech formatów oraz przypadków: błędny hash,
   uszkodzony ZIP, brak opcjonalnego logu/pól, nieznane pola i złośliwa ścieżka.

## Kryteria odbioru MVP

- Otwarcie `.alprsession` i legacy ZIP nie wymaga ręcznego rozpakowania.
- Użytkownik widzi wynik walidacji integralności przed metrykami.
- Wartości kart odpowiadają `report.json`, a wykresy per klatka — `traces.csv`.
- Brak ground truth jest wyraźnie odróżniony od wyniku jakości równego zero.
- Błąd pojedynczej sekcji nie zamyka całego raportu; UI pokazuje częściowe dane i
  diagnostykę parsera.
- Duże obrazy i artefakty modeli nie są automatycznie ładowane do pamięci.

## Źródła kontraktu w repozytorium

- [`MainActivity.java`](../app/src/main/java/com/example/alpr_v1/MainActivity.java)
  — wybór rodzaju eksportu i utworzenie snapshotu.
- [`MetricsCollector.java`](../app/src/main/java/com/example/alpr_v1/metrics/MetricsCollector.java)
  — faktyczny kontrakt `report.json` i nagłówek `traces.csv`.
- [`InferenceTrace.java`](../app/src/main/java/com/example/alpr_v1/metrics/InferenceTrace.java)
  — rekord śladu per klatka.
- [`ResearchArchive.java`](../app/src/main/java/com/example/alpr_v1/metrics/ResearchArchive.java)
  oraz [`ReportArchive.java`](../app/src/main/java/com/example/alpr_v1/metrics/ReportArchive.java)
  — układ archiwów.
- [`mobile_research_export.md`](mobile_research_export.md),
  [`mobile_experiment_telemetry_v1.md`](mobile_experiment_telemetry_v1.md) i
  [`alpr-mobile-research-bundle-v1.schema.json`](alpr-mobile-research-bundle-v1.schema.json)
  — opis semantyki i schemat manifestu.
