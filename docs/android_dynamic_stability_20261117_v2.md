# Stabilność zwykłego trybu dynamicznego — 2026-11-17, v2

Wdrożono poprawkę ciągłości ramki pojazdu, tożsamości kart galerii i ograniczenia zasobów kamery. APK jest aktualizowany na Samsungu A12 z zachowaniem danych. Końcowe testy ukierunkowane: **68/68 PASS**; pełna regresja i odbiór fizyczny wersji z historią luminancji są w toku. Odbiór fizyczny jest zapisywany osobno w `device/acceptance.json` obok artefaktów wydania; nie wynika z testów hosta.

## Rozpoznane przyczyny

- Galeria grupowała obserwacje po tekście MZ, mimo przekazanej informacji o trybie dynamicznym. W sesji incydentu dwie różne hipotezy tego samego pojazdu i tablicy otrzymały osobne karty. Tożsamość karty nie może wynikać z poprawności pojedynczego odczytu.
- Końcowy filtr prezentacji usuwał ramkę przy braku pomiaru MP, również wtedy, gdy lokalny kanał pojazdu/tablicy nadal potwierdzał obiekt. Ruch kamery dodatkowo odraczał kotwiczenie MP i resetował lokalny tracker.
- Podgląd czekał na monitor zajęty inferencją. W materiale incydentu blokada trwała 2–4 s. Rzeczywista analiza kamery wynosiła 3000×4000 przy zapisanym wyborze 4000×3000.
- Systemowy `lmkd` zabił aplikację 17:52:16.983 przy presji pamięci/swapu; `exit-info` wskazuje `LOW_MEMORY`. Drugi proces został zabity 17:53:20. O 17:55:09 wystąpił SIGABRT usługi producenta `android.hardware.graphics.composer@2.1-service`, ze stosem `BliterNode::invalidate` / `AsyncBliterHandler::processVirMirror`; później wystąpił błąd `DisplayStreamer` i `SYSTEM_RESTART`. Są to dowody osobnych zdarzeń. Nie dowodzą, że aplikacja samodzielnie spowodowała błąd sterownika. Historyczny `bootreason=kernel_panic` nie został przypisany do tej próby.

Pełne surowe logi incydentu pozostają lokalnie w `app/build/android_dynamic_incident_plan_audit_20261117_180343/`. Archiwum źródeł nie zawiera logów innych aplikacji ani danych telefonu.

## Kamera i pamięć

Ograniczenie obowiązuje w zwykłej analizie dynamicznej. Zapisany wybór rozdzielczości pozostaje ustawieniem użytkownika; efektywna rozdzielczość sesji jest dobierana z katalogu kamery, z uwzględnieniem proporcji i limitu pikseli.

| Pamięć urządzenia | Maksymalna powierzchnia analizy |
| --- | ---: |
| Do 3 GiB, `lowRam`, heap poniżej 256 MiB lub nieznana ilość RAM | 921 600 px |
| Powyżej 3 do 6 GiB | 2 073 600 px |
| Powyżej 6 GiB | 3 686 400 px |

Preview ma osobny limit 921 600 px i zachowuje wspólny ViewPort. CameraX odrzuca rozmiary ponad limit podczas wyboru; zbyt duża rzeczywista klatka zatrzymuje analizę przed konwersją, zamiast rozpoczynać pętlę ponownego wiązania kamery.

Budżet przejściowych bitmap wynosi `min(96 MiB, maxHeap / 4)`. Przed konwersją rezerwowane jest miejsce na obraz źródłowy i jego kopie. Jednocześnie dopuszczona jest jedna inferencja oraz ograniczona liczba bitmap podglądu; zajętość podglądu kończy się dopiero po zwolnieniu bitmapy przez UI. Dzierżawy pamięci są zwalniane także po odrzuceniu zadania lub błędzie. Budżet nie obejmuje wszystkich modeli, buforów natywnych i pamięci sterownika — ich zużycie jest obserwowane oddzielnie.

Presja pamięci oraz krytyczne sygnały systemu zatrzymują zwykłą analizę z komunikatem o małej ilości pamięci. Galeria pozostaje dostępna. Wznowienie wymaga świadomego START; aplikacja nie uruchamia kamery automatycznie po takim zatrzymaniu.

Na podłączonym A12 podczas rozpoczętej próby potwierdzono efektywną analizę **1080×1440**, limit **2 073 600 px** oraz stempel źródłowej klatki o tym samym rozmiarze. Nie jest to deklaracja odczytana wyłącznie z ustawień.

## Ciągłość i numer Pn

`trackId` pozostaje identyfikatorem technicznym trackera. `entityId` jest właścicielem logicznym, a Pn numerem prezentowanym użytkownikowi. Obraz dla HUD jest jednym spójnym snapshotem właścicieli, aliasów, numerów i geometrii z tego samego przebiegu oraz generacji sceny.

Brak kolejnego MP nie usuwa ramki właściciela, jeżeli świeże lokalne piksele pojazdu lub przypisanej tablicy nadal potwierdzają ciągłość. Bez nowych lokalnych dowodów dopuszczone jest maksymalnie **600 ms** podtrzymania od ostatniego rzeczywistego potwierdzenia; brak dowodu nie odnawia tego czasu. Silna sprzeczność lokalnego wyglądu zamyka tożsamość od razu, chyba że drugi kanał ma aktualny prawidłowy dowód tego samego obiektu.

Końcowy filtr HUD korzysta z tego samego snapshotu i nie odrzuca ramki wyłącznie z powodu braku w surowej liście MP. Podtrzymana geometria nie staje się fikcyjnym pomiarem MP/MT. Zwykły podgląd nie czeka na monitor zajęty inferencją; stare stemple źródłowe nie mogą zmienić nowej sceny. Oczekujące przejście sceny nie jest nadpisywane przez późniejszą decyzję bez zmiany sceny.

Weryfikacja po rzeczywistej utracie zachowuje ostrożne warunki przypisania 1:1. Sam tekst tablicy nie łączy dwóch pojazdów. Niejednoznaczny kandydat pozostaje w `Weryfikacja`.

Każde kotwiczenie MP/MT w zwykłym DYN otrzymuje małą kopię luminancji dokładnie tej klatki kamery, której dotyczy wynik sieci (zgodny numer źródła i czas). Tracker najpierw tworzy odniesienie z tych pikseli, a następnie dopasowuje bieżący obraz. Nie inicjalizuje wyglądu pojazdu na późniejszym fragmencie drogi. Zmiana geometrii źródła odrzuca taką kotwicę. Kopia jest ograniczona rozmiarem, a kolejna kotwica zastępuje poprzednią. Zachowanie lokalnej geometrii wymaga wsparcia właściwych właścicieli w tym samym snapshotcie.

Przejście od opóźnionej kotwicy do podglądu korzysta z rzeczywistych pośrednich próbek luminancji. Wspólna historia obu trackerów ma limit 4 MiB, 64 klatek i 10 s; odtworzenie obejmuje najwyżej 16 klatek pośrednich. Luka większa niż 500 ms, zmiana sceny, transformacji kamery lub geometrii odrzuca ścieżkę. Odtworzenie nie publikuje pośrednich wyników jako nowych MP/MT. Brak pamięci podczas zapisu historii uruchamia bezpieczne wstrzymanie analizy.

W tej ścieżce dopasowanie przepływu korzysta z dziewięciu punktów wewnątrz obiektu. Narożniki ramki są przenoszone przez wynikową transformację. Model ruchu kolejnych rzeczywistych klatek jest sprawdzany zgodnością wyglądu z obrazem odniesienia; tracker nie przechodzi do dopasowania narożników tła po utracie wewnętrznych punktów. Dotychczasowa ścieżka statyczna i badawcza zachowuje własną kolejność dopasowania.

Przy dopasowaniu nowego MP lokalna geometria jest propozycją położenia z czasu tej konkretnej klatki źródłowej. Pozostają warunki wyglądu i jednoznaczności 1:1. Późniejsze piksele nie dostarczają geometrii starszej klatce; lokalna propozycja nie zapisuje fikcyjnego MP.

Obecność obiektu jest oceniana oddzielnie od blokady OCR. Oprócz wcześniejszego dowodu przy jakości co najmniej 0,75 dopuszczony jest dowód przy jakości co najmniej 0,60, jeżeli jednocześnie wsparcie punktów wynosi co najmniej 0,50, średni błąd nie przekracza 16, zgodność lokalnego wyglądu wynosi co najmniej 0,65, liczba inlierów spełnia istniejące minimum i nie wystąpiło nieudane dopasowanie. Predykcja i brak walidacji wyglądu nie spełniają tego warunku. Słabsze dane nie odnawiają limitu 600 ms. Próg blokady OCR pozostaje 0,75.

## Galeria i dowody MZ

Zwykła galeria dynamiczna grupuje po `(przebieg analizy, scena, kanoniczny właściciel pojazdu)`. Zweryfikowany alias może połączyć historię tego samego właściciela; zgodny tekst bez zgodnej tożsamości nie wystarcza. Różne przebiegi ani różne pojazdy z tym samym napisem nie są scalane po numerze rejestracyjnym.

Karta pokazuje Pn i aktualną hipotezę łączną MZ, jej wynik pewności oraz stan weryfikacji. Sprzeczne nowe odczyty mogą zmniejszyć ten wynik i skorygować tekst. Surowy odczyt, pewność, znaki, czas i identyfikatory każdej obserwacji pozostają osobnym dowodem. Zdjęcie reprezentatywne zachowuje swój własny surowy odczyt — nie jest podpisywane wynikiem z innej klatki.

Szczegóły pozwalają otworzyć zapisane zdjęcie konkretnej próby MZ po identyfikatorze sesji i obserwacji. Odczyt jest asynchroniczny, ogranicza dekodowanie do 512 px na wymiar i wymaga dokładnego powiązania źródłowego. Nie podstawia zdjęcia innego pojazdu o tym samym tekście. Brak takiego dowodu jest widoczny jako brak zdjęcia. Lista prób jest stronicowana po 32, dzięki czemu długa obserwacja nie buduje tysięcy kontrolek naraz.

Harmonogram MZ oraz akumulacja dowodów z poprzedniej poprawki `complementary_v1` pozostają aktywne: nieustabilizowane odczyty mają minimum 750 ms odstępu, stabilna hipoteza kontrolę okresową po 3 s, a wyraźnie lepszy wycinek może przyspieszyć kontrolę z zachowaniem minimum. Sam upływ czasu nie dodaje pewności. Nie zmieniono schematów eksportu badań.

## Walidacja hosta

| Przebieg | Wynik |
| --- | --- |
| `replay-proof-final-v2` — testy ukierunkowane, build APK, kompilacja testów urządzenia | 68/68 PASS; build PASS |
| `host-replay-sealed-v2` — pełna wybrana regresja | W TOKU |
| Macierz F4/F5A/F6 × R0/R1/R2 | 7 niezmienionych snapshotów; 2 oczekiwane `N/A_UNSUPPORTED_F6_POLICY` |
| Native UI przy skali fontu 100%/150% | Pn, wynik łączny, surowy odczyt i status bez nakładania oraz ucięcia |
| HUD po 70 brakach MP ze wsparciem tablicy | nadal widoczny P1; test obejmuje końcowy filtr MainActivity i rysowanie HUD |
| Współbieżność | blokada inferencji przez 5 s nie blokuje zwykłego preview; stara klatka nie resetuje nowej sceny |
| Powiązanie zdjęcia próby | dokładna obserwacja; brak zapożyczenia zdjęcia innego właściciela; manifest sesji niezmieniony |

Dodatkowe testy obejmują opóźnione kotwiczenie na przesuniętym obiekcie, zmianę geometrii kamery oraz dopasowanie MP do lokalnych pikseli bez używania geometrii z przyszłości.

Sześć wcześniejszych testów oczekujących natychmiastowej utraty po krótkim braku zostało przesuniętych poza nowe okno 600 ms. Zachowano ich asercje tożsamości i HUD; osobny test potwierdza utrzymanie właściciela przy krótkiej luce. Końcowe wyniki wymagają zgodności bajtów źródeł z manifestami przebiegów. Kompilacja testów urządzenia nie oznacza ich wykonania na telefonie.

Obrazy natywnego HUD i galerii z hosta znajdują się w `replay-proof-final-v2/ui/8/` oraz `host-replay-sealed-v2/ui/8/`. Są dowodem testów syntetycznych, a nie odbiorem kamery lub ruchem drogowym.

## Instalacja i odbiór urządzenia

- Aktualizacja `adb install -r -t` zakończona powodzeniem; SHA zainstalowanego `base.apk` jest zgodny z wydanym APK.
- Podczas instalacji zachowano bajtowo 373/373 manifesty sesji, 5/5 plików ustawień i 38/38 plików modeli/pakietów.
- Aplikacja uruchomiona, odblokowany ekran, gotowość ustawień potwierdzona; rozpoczęto zwykły DYN po potwierdzeniu gotowego kadru przez operatora.
- Próby wcześniejszych APK bez mirroringu są zachowane jako dowód etapów naprawy; w pierwszej wystąpiło rozdzielenie właścicieli 5 i 13 w tej samej scenie 4 / epoce 6. Nie są odbiorem końcowej poprawki. Próba końcowego APK bez mirroringu: **W TOKU**. Próba z mirroringiem: **NOT_VERIFIED**. Końcowe stany i odnośniki do dowodów zostaną wpisane po zakończeniu prób.
- Narzędzie sterowania oknem Android Studio zgłosiło niedostępną natywną usługę. Otworzenie mirroringu może wymagać działania operatora. Nie instalowano pomocników i nie zmieniano zabezpieczeń.

Każde nowe mruganie, czarny ekran, utrata procesu lub restart systemu oznacza `NO_GO` dla odbioru stabilności. Sama telemetria nie potwierdza braku krótkich artefaktów wizualnych. Prób drogowych, fizycznego fontu 150% i działania pod każdym obciążeniem RAM nie uznaje się za wykonane na podstawie hosta.

## Pochodzenie i artefakty

Baza Git: `f7943a7d7334afd1e4188ef9860b2126331926c8`, z istniejącymi zmianami użytkownika. Delta jest liczona od zachowanej migawki sprzed tej poprawki, a nie od czystego HEAD. Zakres: **32 pliki źródłowe** (21 zmienionych, 11 nowych) i ten raport. Historyczne wydania oraz pliki poza zakresem są chronione audytem SHA.

Artefakty: `app/build/android_dynamic_stability_20261117_v2/`:

- `alpr-dynamic-stability-debug.apk` — 88 468 421 B; SHA-256 `824b1dec30075fdef26c2aa81fdf36abe330a63323156d0bfbcb125ec6a58869`.
- `dynamic-stability-v2.patch` — zmiany względem migawki sprzed edycji.
- `sources-and-delta.zip` — komplet `app/src`, wejścia Gradle, delta i końcowe dowody hosta, bez danych telefonu.
- `delivery.json`, `SHA256SUMS.txt`, `audit/patch-roundtrip.json`, `audit/protected-files.json` — pochodzenie i weryfikacja bajtów.
- `device/acceptance.json` — rozdzielone statusy odbioru urządzenia.

Archiwum jest sprawdzane przez ZIP CRC i SHA każdego pliku źródłowego. Patch jest stosowany kontrolnie w osobnym katalogu do zachowanej migawki i porównywany bajtowo z końcowymi źródłami. Nie wykonano commita, publikacji ani zmian indeksu właściwego repozytorium.
