# Zmiana sceny Static na podstawie punktów charakterystycznych

Static zachowuje dotychczasowe porównanie luminancji i dodatkowo sprawdza punkty
charakterystyczne obrazu wewnątrz wykrytych ramek. Dotyczy to zarówno pojazdów,
jak i tablic, również gdy ramka tablicy zajmuje małą część ramki pojazdu.

`RegionFeatureChangeDetector` wybiera narożniki na podstawie lokalnych gradientów
obrazu i śledzi je istniejącą implementacją pyramidalnego Lucas–Kanade. Są to
punkty obrazu, niezależne od animowanych wierzchołków ramek UI. Porównanie odbywa
się względem stałego obrazu referencyjnego, więc powolna zmiana nie jest ukrywana
przez przesuwanie odniesienia po każdej klatce. Dopasowanie punktów przechodzi
kontrolę przód–tył; punkty niemożliwe do śledzenia w samym obrazie referencyjnym
są pomijane.

Nowy sygnał potwierdza zmianę, gdy w jednym obserwowanym obszarze:

- zniknie co najmniej 65% punktów (minimum trzy), albo
- co najmniej 60% punktów (minimum trzy) przesunie się o więcej niż tolerancja
  drobnego drżenia: maksimum z 2,5 px i 1,2% krótszego wymiaru klatki luma.

Obszar musi dostarczyć przynajmniej cztery wiarygodne punkty. Dowód wymaga trzech
kolejnych pomiarów i minimum 120 ms; pomiary punktów są ograniczone do 10 Hz.
Powtarzanie callbacków pomiędzy pomiarami nie zwiększa licznika potwierdzeń.
Kompensacja ekspozycji jest wspólna z kanałem luminancji. Brak tekstury nie jest
sam w sobie dowodem zmiany sceny; nadal działa porównanie luminancji.

Budżet wynosi maksymalnie 16 punktów na obszar i osiem obszarów z punktami.
Nakładające się detekcje tej samej ramki są deduplikowane. Nie uruchamiamy MP,
MT ani MZ do sprawdzania punktów.

Obszary odkryte przez późny wynik detektora są kotwiczone w pierwotnej klatce,
a nie w najnowszym podglądzie. Podczas ustabilizowanego AZ obszary są przeliczane
według zoomu i dostają osobne odniesienie. Powrót do 1× ponownie sprawdza pierwotną
scenę. Klatki trwającej transformacji optycznej są nadal pomijane przez istniejącą
bramkę kamery.

Potwierdzenie emituje `static_alpr_features_changed` i przechodzi przez istniejący
HARD_RESET: nowa generacja sceny, nowy cykl Static, odrzucenie starych wyników
asynchronicznych. Telemetria zawiera liczbę punktów, udział utraconych punktów
oraz udział przesuniętych punktów.

Weryfikacja: 567 testów JVM i 39 wybranych testów Androida bez błędów. Test na
Androidzie wywołuje reset wyłącznie przez utratę punktów przy zmianie luminancji
poniżej dotychczasowych progów i potwierdza odrzucenie starego stempla OCR.
Zmierzony końcowy callback z resetem trwał około 151 ms na SM-A125F; nie jest to
gwarancja czasu dla każdego obrazu ani pełny benchmark.

Próba kamery 2026-09-08, 15:41: jedno AZ 1,8×, powrót do STATIC_IDLE i brak
fałszywego resetu przez 55 sekund obserwacji nieruchomej sceny. Zmianę punktów
sprawdzono deterministycznie w testach; w tej próbie kamerowej nie podmieniano
obrazu na monitorze. Nagranie, logi i hash APK znajdują się lokalnie w
`app/build/reports/static-region-features/`.
