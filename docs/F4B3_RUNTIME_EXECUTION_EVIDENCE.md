# F4-B3: dowód runtime w sesji badawczej

Zdarzenia `alpr.runtime_execution_evidence.v1` są zapisywane w
`telemetry/runtime_execution.jsonl` i podsumowywane w polu
`report.json.runtime_execution_evidence`. Dotychczasowe schematy paczki i sesji
pozostają v1. Plik dziennika podlega `manifest.json.entry_sha256`.

## Co jest obserwacją

- LiteRT odczytuje wszystkie publiczne TensorInfo I/O: dtype, shape, byte_size,
  scale i zero_point. Rejestruje pojemności buforów wyjścia oraz faktycznego wejścia.
  Wejście jest porównywane z manifestem; wyjście główne z kontraktem dekodera.
  Obecny ModelOutputSpec nie deklaruje quantizerów wyjścia — ten brak jest jawny.
- SHA modelu pochodzi z prywatnej kopii bajtów przekazywanej temu interpreterowi.
  Kontrola SHA poprzedza otwarcie runtime. Ta kopia zastępuje wcześniejsze
  mapowanie pliku i pozostaje w pamięci do zamknięcia backendu.
- `TensorFlowLite.runtimeVersion(FROM_APPLICATION_ONLY)` i `schemaVersion`
  są odczytami runtime. Wersje zależności Maven są osobnymi polami.
- Profil zapisuje requested backend/threads i zaakceptowane opcje. Liczba
  rzeczywiście pracujących wątków, partycje delegatów i ich pokrycie nie są
  udostępnione przez API Java i pozostają UNVERIFIED/null z powodem.
- CPU jawnie żąda XNNPACK. GPU wyłącza XNNPACK, jawnie wyłącza precision loss,
  rejestruje wynik CompatibilityList i opcje delegata. NNAPI i
  allow_fp16_precision_for_fp32 są wyłączone. Są to zamrożone opcje,
  nie dowód dtype arytmetyki kernela ani wykonania całego grafu na GPU.
- Zdarzenia: ALLOCATED, FIRST_INVOKE_COMPLETED, ERROR i błąd inicjalizacji engine.
  Pierwsze pomyślne Invoke jest obserwacją wywołania, nie dowodem INT8.
  Zmiany tensorów i niezgodne bufory są błędami; brak fallbacku wariantu/backendu.
  Błędy zawierają etap, typ, przyczynę i fakty dostępne do chwili błędu.

## Zakres statusu precyzji

| Pole / status | Znaczenie |
|---|---|
| public_io_precision_status=FP32 | wszystkie zaobserwowane publiczne I/O są FLOAT32 |
| public_io_precision_status=MIXED | publiczne I/O zawierają float i integer |
| public_io_precision_status=INT8_NOT_PROVEN | integer I/O; brak dowodu arytmetyki |
| static_graph_classification | dołączony raport B1, powiązany SHA z modelem; nie jest ponownie analizowany na Androidzie |
| kernel_execution_status=INT8_NOT_PROVEN | wariant/graf/I/O wskazuje integer lub mixed, lecz brak dowodu wykonywanych kerneli |
| kernel_execution_status=UNVERIFIED | brak kompletnego dowodu kerneli, także dla FP32 I/O |
| INT8_PROVEN | zarezerwowany dla przyszłego kompletnego dowodu natywnego; adapter Java nigdy go nie nadaje |

Nie wolno pomijać pola scope. FP32 publicznego wejścia nie dowodzi FP32 wszystkich
kerneli, a integer grafu nie dowodzi integer arytmetyki na telefonie. GPU może
przetwarzać modele kwantyzowane za pomocą reprezentacji zmiennoprzecinkowej.

## Trwałość i zamrożenie

Przed START zapisywane są SHA faktycznego base APK i wszystkich split APK przez
`ApplicationInfo.sourceDir/splitSourceDirs`. Błąd odczytu blokuje przygotowanie.
Odtwarzanie przerwanej sesji korzysta z jej zapisanego app_build, a nie APK
zainstalowanego w momencie odzyskania. Zapisywane są również rzeczywiste SHA
źródłowych paczek, tożsamość wariantu i niezmienny ResearchExecutionConfig.

Research Mode wymaga jawnego pinu wariantu i CPU 1/2/4 albo GPU. Brak pinu,
nieaktualny wariant, niedostępny runtime i AUTO blokują START. Zwykły tryb zachowuje
dotychczasowy AUTO. Zmiana konfiguracji podczas RUNNING jest zabroniona.
Przy ponownym otwarciu backendu sprawdzane są zamrożone metadane i SHA plików.

Dziennik jest strumieniowany przez istniejący writer sesji. Zdarzenia kończącej
się operacji są dopuszczone również podczas FINALIZING. Odrzucenie kolejki lub
błąd zapisu zwiększa licznik strat; końcowy archiwizator nie uznaje takiej sesji za
kompletną. Niepoprawne rekordy dziennika dają INCOMPLETE. Brak rekordów oznacza
UNVERIFIED. Podsumowanie zachowuje błędy i oddzielne informacje MT/MZ.

Instrumentacja obejmuje hash/kopię modelu przy inicjalizacji, kontrolę tensorów
przy Invoke oraz zapis pierwszego pomyślnego wywołania i błędów. Ma własny narzut;
nie wyznaczano go w F4-B3. Należy używać tego samego APK i polityki instrumentacji
w obu ramionach przyszłego porównania. NCNN/ONNX zapisują odczyty swoich adapterów
i pierwsze wyjścia, z jawnym ograniczeniem dostawcy; dowód kerneli także nie istnieje.

## Minimalny dalszy dowód kerneli

Publiczna Java Interpreter API nie udostępnia execution_plan ani profilu operatorów.
Minimalne rozszerzenie to kontrolowany build tego samego LiteRT z mostem JNI
działającym na **tej samej instancji interpretera**, która wykonuje etap MT/MZ:

1. Po delegacji przechwycić execution_plan, node_and_registration, przynależność
   do delegata oraz odwzorowanie partycji na pierwotne węzły.
2. Dołączyć profiler do tej instancji; utrwalić wykonane węzły, dtype/dispatch
   faktycznie użytych kerneli i — dla XNNPACK — zdarzenia per operator/microkernel.
3. Powiązać wszystko z runtime build/SHA, modelem, instancją, etapem i wywołaniem.
   Brakujące, nieznane, float/hybrid albo niepokryte operacje wykluczają INT8_PROVEN.

Sama lista operatorów lub czas jednego węzła DELEGATE nie wystarcza. Oddzielny
benchmark interpretera także nie poświadcza wykonania w kliencie ALPR. Nie stosować
refleksji do prywatnego uchwytu natywnego jako stabilnego kontraktu.

Źródła API: [Interpreter Java](https://developers.google.com/edge/api/tflite/java/org/tensorflow/lite/Interpreter),
[TensorFlowLite](https://developers.google.com/edge/api/tflite/java/org/tensorflow/lite/TensorFlowLite),
[Interpreter C++](https://developers.google.com/edge/api/tflite/cc/class/tflite/impl/interpreter),
[profilowanie XNNPACK](https://blog.tensorflow.org/2022/06/Profiling-XNNPACK-with-TFLite.html),
[GPU i modele kwantyzowane](https://android.googlesource.com/platform/external/tensorflow/+/upstream-master/tensorflow/lite/g3doc/performance/gpu.md).

## Odbiór

Testy JVM używają sztucznych bajtów i sterownika testowego, ale wykonują właściwy
backend, walidację, serializację i kod archiwum. Osobne trzy testy
`TfliteRuntimeEvidenceInstrumentedTest` zawierają małe grafy RESHAPE/QUANTIZE
zbudowane bezpośrednio ze schematu FlatBuffers (FP32, INT8, MIXED).
Ich uruchomienie na Samsungu A12 wymaga osobnego polecenia; nie jest testem jakości
ALPR ani eksperymentem produkcyjnym. F4-B3 nie uruchamia telefonu, modeli
produkcyjnych, kalibracji ani konwersji. FINAL_TEST_INVENTORY_NOT_VERIFIED z F4-B2
pozostaje blokadą produkcyjnego eksperymentu.
