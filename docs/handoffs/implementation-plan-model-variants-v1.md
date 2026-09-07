# Plan wdrożenia wyboru wariantów modeli v1

Gałąź: `feature/model-variant-selection-v1`, baza: `5e164c8` (pełne v4).
Przed zmianami sprawdzono `git status` i `git diff`: drzewo czyste.

1. Bezpośrednia akcja wariantu MP/MT/MZ, czytelne runtime + precision,
   AUTO z profilem sprzętowym i zablokowane niedostępne runtime’y.
2. Wspólny ranking wszystkich udanych pomiarów AutoTune, także INT8.
   Zachowanie pinów role + fingerprint oraz deterministycznego fallbacku FP32.
3. Sprawdzenie istniejącej rewizji ustawień, przeładowania MobileAlprEngine
   i zamrożonego ResearchStageExecutionConfig. Rozszerzenie istniejącego logu
   o pliki i typ wejścia z tego samego wariantu otwieranego przez backend.
4. Testy JVM rankingu, Android kompozycji/pinów/badania/UI oraz rzeczywistych
   pięciu wariantów i importu pojedynczego/zagnieżdżonego na podłączonym telefonie.
5. Build, regresja, sprawdzenie UI i zmiany MT INT8 ↔ FP32 bez restartu,
   aktualizacja architektury, dziennika oraz raportu wyników.

Importer i schemat paczki pozostają istniejącym źródłem kontraktu wariantów.
