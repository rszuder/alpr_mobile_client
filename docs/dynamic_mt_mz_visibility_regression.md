# DYNAMIC: tablice odrzucane przed MZ (2026-09-09)

Logi telefonu potwierdziły detekcje MT z confidence 0.91–0.94 i kolejne przebiegi
z `MZ_RUNS=0`. Nowy filtr TOP-1 usuwał kandydatów z niejednoznacznym przypisaniem
do pojazdu, a wczesny zwrot `no_plate` pomijał również callback ramek tablic.

W klatce 174 (960×1280) tablica `[534.6,593.7,604.9,633.8]` należała do przedniego
auta `[501.51,382.72,959.96,722.81]`. Środek tablicy leżał także w ramce sąsiada
`[342.73,401.76,787.27,618.46]`, chociaż jej dolna część wyraźnie wychodziła poza
tę ramkę. Punktowa ocena dawała konflikt, blokujący TOP-1.

Poprawka:

- DYNAMIC uwzględnia udział powierzchni ramki tablicy zawarty w ramce pojazdu.
  Częściowe przecięcie nie dostaje takiej samej oceny jak pełne zawieranie.
  STATIC zachowuje dotychczasowe przypisanie.
- Jednoznaczne zawieranie co najmniej 95% tablicy przy najwyżej 85% u każdego
  sąsiada rozstrzyga bliski wynik punktowy. Drobna różnica granic ramek ani pełne
  zawieranie przez dwa auta nie otwierają tego wyjątku; pozostaje kontrola
  sensownego pionowego położenia tablicy.
- Nadal obowiązuje margines pewności, geometryczna kontrola właściciela oraz
  TOP-1. Samo pochodzenie z ROI nie przypisuje tablicy jego właścicielowi.
- Poprawne wykrycia bez pewnego właściciela trafiają do bieżącego overlayu jako
  „Tablica: ustalanie pojazdu”, bez nadawania encji lub uruchamiania dla niej MZ.
  Duplikaty tego podglądu są tłumione. Nie są przechowywane jako nowy odczyt.
- Przy pustym TOP-1 aktualizowany jest tracker i emitowany callback MT.
- `ALPR_MT_TOP1` pokazuje dla każdego kandydata właściciela, cel, przyczynę
  przypisania, poprawność geometrii, wynik bramki wielkości i końcowy wybór.

Regresja JVM odtwarza podane współrzędne z telefonu, dawny konflikt i przejście
poprawnego właściciela do TOP-1. Obejmuje też wykrycie z ROI sąsiada, pełną klatkę
i zachowanie odmowy przy faktycznie niejednoznacznej geometrii.

Pełny zestaw JVM: 636/636. APK debug zbudowane poprawnie. Opcjonalny test
`DynamicMtMzInstrumentedTest` wymaga zainstalowanych modeli i oryginalnego zdjęcia
`ZX1090C_GWE1099L_LU6366Y_001.jpg`, skopiowanego do external files aplikacji jako
`dynamic-mz-source.jpg`; uruchomienie przez `-e liveDynamicMz true`.

Weryfikacja na emulatorze Pixel 3: test zakończony `OK (1 test)`, rzeczywiste
inferencje MP onnx-fp32, MT tflite-int8, MZ onnx-int8. Sprawdzono dodatni licznik
wywołań MZ, obserwację z `freshMzAttempted` i publikację geometrii tablicy przez
callback MT. Wcześniejsza wersja poprawki nie przeszła tej samej próby na
oryginalnym zdjęciu; test ujawnił potrzebę rozstrzygnięcia zawierania 95%/85%.

Test na nieruchomym zdjęciu nie zastępuje sprawdzenia ruchu kamery na telefonie.
