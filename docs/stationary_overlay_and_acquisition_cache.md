# Stabilne ramki i akwizycja z cache — 2026-09-09

## Obserwacja i poprawka

W logu `build/diagnostics/stable-frames-current.txt` o 21:56:30.281
podtrzymanie stabilnej sceny przechodzi na `stationary=false`, a nakładka
zmienia liczbę pojazdów z 3 na 0. O 21:56:32.250 wraca stabilność i te same
trzy ramki. Kolejne wyniki MP zachowują identyfikatory 1, 5 i 6.

Dotychczas dowód stabilności wygasał po 1,5 s, choć aktualizacje analizy
nadchodziły co kilka sekund. `StationarySceneSupport` dopasowuje teraz ważność
dowodu do dwukrotnego odstępu MP, w granicach 1,5–10 s. Brak aktualizacji
nie jest traktowany jak zmierzony ruch. Faktycznie zmierzony ruch nadal resetuje
dowód od razu; ruch wykrywany czujnikiem, transformacja kamery i bariera zmiany
sceny wyłączają podtrzymanie. Sam limit wieku geometrii nie został wydłużony.

To poprawka konkretnej ścieżki migania; zachowanie na fizycznym telefonie
trzeba potwierdzić po instalacji nowego APK.

## Cache i galeria

- `RecentReadCache` przechowuje do 40 ostatnich obserwacji z niepustym,
  świeżym wynikiem MZ. Jest buforem RAM z własnością kopii bitmap;
  usuwanie wpisów i zwolnienie ViewModelu recykluje kopie.
- Cache działa także przy wyłączonej akwizycji. Samo buforowanie nie tworzy
  wpisów galerii ani plików paczki sesji.
- Włączenie akwizycji najpierw tworzy lub wznawia sesję, następnie odtwarza
  cache przez tę samą ścieżkę zapisu do galerii i `CropSessionStore`.
- Zachowane są oryginalne identyfikatory, czas obserwacji, wynik MZ, pomiary
  HUD, warianty modeli i źródło odczytu. Nie uruchamiamy ponownie MZ.
- Grupowanie wymaga dokładnie identycznego tekstu. Ten sam odczyt w kolejnej
  encji dodaje obserwację, a ponowne odtworzenie tej samej obserwacji jej
  nie dubluje. Galeria i paczka zachowują pojedynczy obraz dla danego tekstu.
- Nowa ręcznie rozpoczęta sesja analizy czyści cache; przełączanie samej
  akwizycji go zachowuje. Tryb badawczy nie korzysta z tego importu.

Przyciski nazywają się **Galeria** i **Akwizycja**, wraz z opisami dostępności.

## Regresje do sprawdzania

Testy obejmują przerwy przy wolnej analizie, natychmiastowe przerwanie
podtrzymania po ruchu, ostateczne wygaśnięcie bez nowych dowodów, reset,
własność i zwalnianie bitmap, odrzucanie pustych/przeniesionych odczytów,
wyłączoną akwizycję, import po włączeniu i brak duplikatów po wznowieniu.

Weryfikacja: kompilacja APK zakończona powodzeniem, 645 testów JVM bez błędów,
16 testów Android zaliczonych. Test eksportu wymagał powtórzenia po usunięciu
systemowych okien ANR emulatora, które odbierały fokus aplikacji.
