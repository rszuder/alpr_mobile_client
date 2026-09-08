# Zgodność obrazu cropu, znaków i podpisu

Historia rozpoznawania wybierała najlepszy obraz, ale aktualizowała jego podpis,
pewność, czas i źródło także po przyjściu słabszego odczytu. Powstawał wpis
z obrazem i ramkami znaków z jednej próby oraz numerem z innej próby.

Teraz wybór cropu aktualizuje razem bitmapę, znaki MZ, transkrypcję, pewności,
timing, czas wykonania i źródło. Słabsza obserwacja może zwiększyć liczbę
obserwacji logicznego celu, ale nie przepisuje podpisu zachowanego obrazu.
`lastObservationAtMillis` służy osobno do odrzucania duplikatów i opóźnionych
callbacków; `capturedAtMillis` nadal oznacza czas wybranego cropu. Migawka
szczegółów oraz przypisanie prowizorycznej tablicy do pojazdu zachowują ten stan.

Historia i ręcznie zbierane cropy używają średniej pewności znaków z bieżącego
MZ. Konsensus tracku nie jest zmieniany i nadal może służyć badge pojazdu,
ale nie zastępuje pewności konkretnego cropu. Świeży pusty wynik MZ daje pustą
transkrypcję; nie pożycza numeru z wcześniejszego konsensusu. Fallback pozostaje
wyłącznie dla starych wywołań konstruktora bez danych świeżej inferencji.

Test regresji: obraz A z odczytem A / 95%, potem obraz B z B / 40% i mocniejszym
konsensem tracku. W historii pozostają obraz A, ramki A, podpis A / 95% oraz
czas obrazu A. Dopiero lepszy crop B / 98% wymienia cały zestaw. Osobne testy
sprawdzają spóźniony callback, przypisanie pojazdu, migawkę oraz pusty wynik MZ.

Istniejące pliki eksportu nie są przepisywane. Surowe obserwacje pipeline'u
i tożsamości próbek badawczych pozostają zachowane. Artefakty weryfikacji:
`app/build/reports/crop-reading-consistency/`.
