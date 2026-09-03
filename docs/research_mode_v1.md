# Research Mode v1

## Cel

Research Mode zapewnia powtarzalny przebieg kamery ALPR, którego efektywną
konfigurację można odtworzyć wyłącznie z pliku `.alprsession`. Nie zastępuje
trybu użytkowego i nie usuwa autotuningu.

## USER a RESEARCH

- USER: wariant i `ExecutionProfile` mogą pochodzić z `AutoTuneManager`.
- RESEARCH: przy START powstaje niezmienny `ResearchExecutionConfig`.
- Zmiana ustawień wymaga STOP i rozpoczęcia nowej sesji.
- Niewykonywalna konfiguracja blokuje START; nie ma cichego fallbacku.

## Konfiguracja zamrażana przy START

Globalnie:

- `experiment_type`, `variant`, seria, scenariusz i numer powtórzenia;
- R0/R1/R2 i profil rozpoznawania;
- żądana rozdzielczość CameraX;
- lock i autozoom;
- jawny stan vehicle tracking, plate tracking, temporal MZ i adaptive gate.

Dla każdego aktywnego etapu MP/MT/MZ:

- `model_id` i fingerprint;
- wariant, runtime i precyzja;
- AUTO albo jawne CPU 1/2/4/GPU;
- efektywna liczba wątków, delegat i wejście modelu.

AUTO oznacza snapshot profilu wybranego w chwili START. Późniejszy wynik
autotuningu nie może zmienić aktywnej sesji. GPU jest dostępne tylko dla TFLite;
ONNX i NCNN nie wykonują cichego fallbacku z GPU na CPU.

Autozoom wymaga locka. Przy lock OFF `TargetStateMachine` nie wybiera ani nie
śledzi celu. Po STOP wraca konfiguracja trybu użytkowego.

## Eksport i awarie

`report.json/experiment/effective_execution_config` jest kanonicznym snapshotem
sesji. `capture` zachowuje także faktyczną rozdzielczość źródła, a `app_build`
identyfikuje commit i build.

Trwały marker sesji wykrywa przy kolejnym uruchomieniu niedomknięty pomiar
poprzedniego procesu. `errors.crash_count` nie jest już sztucznym zerem:
zawiera liczbę odzyskanych niedomkniętych sesji albo null, gdy pomiar jest
niedostępny.

## Scan finalization

Stabilny wynik Scan tworzy `AcquisitionRecord`. Deduplikacja działa po
znormalizowanym numerze w obrębie jednego runu. Raport zachowuje również
odrzucone duplikaty, dzięki czemu można niezależnie policzyć:

- liczbę wszystkich finalizacji;
- unikalne tablice;
- współczynnik duplikatów;
- czas akwizycji;
- unikalne tablice na minutę czasu ściennego.

## Ograniczenia v1

- Brak kontrolowanego replayu na Androidzie; porównania są camera-in-the-loop.
- C0/C1 nie jest jeszcze osobną osią eksperymentalną; aktywny jest obecny
  temporalny konsensus.
- C2 geometryczny, Pick UI i Search pozostają poza zakresem freeze.
- `best_crop_id` rekordu Scan wskazuje obserwację pipeline'u, a nie gwarantuje
  automatycznego osobnego JPEG w publicznym katalogu.

## Bramka przed kampanią

Przed freeze należy wykonać pełne unit/lint/build/instrumented tests, pilot
`.alprsession`, ręczny smoke `kamera → Scan → finalizacja → kolejna encja` oraz
10–15 minut stabilności na docelowym Samsungu SM-A125F.

Walidacja 2026-09-04 potwierdziła:

- pilot `PILOT_001 / STATIC_SINGLE_VEHICLE / R0 / replicate 1`, 73,1 s;
- zgodność snapshotu CPU×2 MT/MZ, R0, lock OFF i autozoom OFF z raportem;
- 18 trace'ów, brak błędów i kompletność danych;
- 17/17 zgodnych sum SHA-256 wpisów `.alprsession`;
- smoke Scan na dwóch pojazdach: dwie finalizacje, dwie unikalne tablice,
  przejście do kolejnej encji, średnia 4,62 s i p95 4,79 s;
- osobny podgląd kadru bez zdarzeń inferencji oraz płynne przejście do Scan.

Końcowy smoke 2026-09-04 trwał 633 s i zachował 377/377 trace'ów bez błędu,
crasha, ANR ani ewikcji. PSS raportowany przez aplikację osiągnął 382,7 MB,
a zewnętrzny monitoring chwilowo około 407 MB bez trendu monotonicznego.
Temperatura baterii wzrosła z 32,2 do 34,6°C; Android osiągnął status termiczny
4, dlatego kampanie porównawcze muszą stosować istniejący warunek startowy i
rejestrować historię termiczną. Scan zachował dwie unikalne finalizacje bez
duplikatów. Archiwum miało 17/17 poprawnych hashy i `source_state=dirty`, zgodny
z roboczym stanem źródeł.

Techniczna bramka testowa jest kompletna. Przed freeze pozostaje utworzenie
czystego commita/checkpointu i wykonanie właściwych serii wyłącznie z buildem
`source_state=clean`.
