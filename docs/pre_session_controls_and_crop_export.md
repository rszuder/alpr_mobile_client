# Przygotowanie telefonu i paczki sesji cropów

Oba dolne paski ikon oraz poziomica są widoczne również przed uruchomieniem
analizy. Poziomica ma własne odświeżanie co 250 ms, uruchamiane w onResume
i zatrzymywane w onPause/onDestroy. Czujniki pracują na pierwszym planie także
bez aktywnej kamery; zatrzymanie analizy nie usuwa poziomicy. Przyciski wymagające
analizy lub trybu DYNAMIC pozostają widoczne i wyłączone, gdy są niedostępne.

W zwykłej galerii dodano „Zapisz paczkę sesji”. Lista zawiera datę, liczbę cropów
i obserwacji. Po wybraniu sesji systemowy selektor dokumentów pozwala nadać nazwę
i wskazać miejsce zapisu ZIP. Identyfikator oczekującego eksportu jest zachowany
przy odtworzeniu Activity.

Paczka zawiera `crop-000001.jpg`, kolejne obrazy oraz `session.json`:

- jeden obraz dla każdego dokładnego odczytu MZ w wybranej sesji;
- kolejne zgodne obserwacje, z osobnymi encjami, trackami i kontekstem sceny;
- czasy modeli, rozdzielczość, źródło, pewności i zapisane dane HUD;
- ramki znaków odnoszące się do obrazu bazowego.

`CropSessionStore` zapisuje zwykłe sesje w prywatnym katalogu `files/crop-sessions`.
Zapis nie zależy od limitu pamięci listy cropów ani historii. Dane pozostają po
odtworzeniu aplikacji; wyczyszczenie bieżącej listy galerii nie usuwa archiwum
sesji. Dotychczasowe wstrzymanie/wznowienie zbierania zachowuje identyfikator sesji.
Niepusty bieżący MZ jest wymagany; puste wyniki i sam konsensus nie są zapisywane.

Własna pojedyncza kolejka zapisów porządkuje obrazy, metadane i eksport. Eksport
obejmuje stan po wszystkich wcześniej zgłoszonych obserwacjach. Obrazy i manifest
są publikowane przez atomową zmianę nazwy pliku tymczasowego. Błąd zapisu cropa
blokuje eksport oznaczony jako kompletny, a błąd zapisu ZIP jest zgłaszany bez
usuwania danych źródłowych. Materiał badawczy zachowuje istniejący eksport sesji.

Weryfikacja: 636/636 testów JVM oraz 22 testy instrumentacyjne magazynu sesji,
eksportu przez ContentResolver, poziomicy, pasków ikon, trybu sceny i ścisłego
grupowania historii (trzy testy UI dostosowane i powtórzone osobno). Sprawdzono
zrzut ekranu przed analizą. APK debug zbudowane; telefonu nie przeinstalowywano.
