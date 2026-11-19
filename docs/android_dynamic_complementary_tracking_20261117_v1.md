# Dynamiczny tracking komplementarny — complementary_v1

Data: 2026-11-17. Zakres: zwykły tryb dynamiczny Android ALPR. Stan: **PASS_SOURCE / PASS_HOST**, odbiór fizyczny **NOT_VERIFIED**.

Poprawka utrzymuje tożsamość pojazdu na podstawie rzeczywistego lokalnego śledzenia pojazdu lub jego wcześniej przypisanej tablicy. Odczyt MZ służy do gromadzenia dowodów o znakach; jego chwilowa niezgodność nie kończy ciągłego śledzenia tego samego obiektu. Tryb statyczny i zamrożone konfiguracje badań zachowują dotychczasową politykę rozpoznawania i AZ.

## Tożsamość i geometria

- `VehicleTrackManager.trackId` pozostaje identyfikatorem technicznego śladu. `VehicleEntity.entityId` identyfikuje obiekt domenowy; numer widoczny jako `Pn` pochodzi z `DynamicVehicleIdentity.Presentation.number`. Epoka właściciela zabezpiecza spóźnione wyniki.
- MT tworzy parę pojazd–tablica tylko po walidacji skojarzenia i geometrii w kontekście źródłowej ramki. Lokalny tracker przechowuje stempel właściciela nadany przy zakotwiczeniu tablicy.
- Wspólna ocena obu kanałów uwzględnia rzeczywiste lokalne dopasowanie wyglądu, jakość trackera i inliery. Dodatni dowód jednego kanału podtrzymuje właściciela mimo niepowodzenia drugiego. Sama predykcja, globalny ruch kamery i tekst rejestracji nie tworzą tego dowodu.
- Gdy zostaje tylko tablica, proporcje i przesunięcie pierwotnej pary służą do odtworzenia ramki pojazdu w podglądzie. Ramka ma znacznik predykcji; nie staje się nowym pomiarem MP i nie odświeża czasu MP ani MT.
- Niedostępny kanał nie dostarcza głosu ujemnego. Ostatni dowód wygasa w ograniczonym oknie. Obsługa opóźnień podglądu nie pozwala spóźnionemu brakowi anulować nowszego pomiaru ani starym próbkom wskrzeszać utraconego właściciela.
- Utrata obu kanałów zamyka dotychczasową epokę. Ponowne przypisanie po takiej utracie nadal wymaga ograniczonego okna czasowego, zgodności wyglądu/geometrii, jednoznacznego dopasowania 1:1 oraz dwóch niezależnych świeżych zgodnych odczytów dla jednego właściwego poprzednika. Niejednoznaczne przypadki pozostają w `Weryfikacja`; sam zgodny tekst nie scala pojazdów.
- Przy kontrolowanym AZ znana transformacja skali daje ograniczone czasowo przewidywanie. Utrzymanie tożsamości po transformacji wymaga nowego rzeczywistego pomiaru; transformacja nie jest dowodem obecności obiektu.

## MZ i pewność znaków

Nowa `DynamicCharacterEvidence` gromadzi maksymalnie 16 niepustych odczytów z różnych ramek źródłowych. Pozycje znaków są głosowane z wagą jakości wycinka i pewności danego znaku. Różne liczby znaków i układy wierszy konkurują jako odrębne hipotezy, bez przesuwania znaków między niezgodnymi strukturami. Wczesny błędny odczyt nie narzuca następnej inferencji oczekiwanej długości ani układu.

Stabilizacja wymaga co najmniej dwóch wspierających obserwacji dla każdego znaku oraz udziału co najmniej 75% dla znaków i struktury. Wynik pewności uwzględnia zgodność, pewność znaków i liczbę obserwacji; jest wynikiem agregacji, a nie skalibrowanym prawdopodobieństwem poprawności numeru. Upływ czasu sam go nie zwiększa. Sprzeczne odczyty mogą obniżyć pewność, cofnąć potwierdzenie i skorygować tekst także w HUD i panelu wyniku.

Harmonogram zwykłego trybu dynamicznego:

| Stan | Najwcześniejsza kolejna próba MZ |
| --- | --- |
| Brak wcześniejszej próby | bez oczekiwania na odstęp, po spełnieniu warunków wycinka |
| Nieustabilizowany odczyt | 750 ms od poprzedniej próby |
| Stabilna hipoteza | okresowo po 3 s |
| Dwie kolejne puste próby | po 3 s |
| Wyraźnie lepszy wycinek | może przyspieszyć okresową kontrolę, zachowuje minimum 750 ms |
| Rzeczywisty powrót z AZ | jedna świeża niezależna próba |

Pusta próba nie dodaje fikcyjnych znaków; duplikat ramki nie zwiększa liczby dowodów. Dowody i zegar prób należą do właściciela, dlatego wymiana technicznego tracku tablicy i miękkie odzyskiwanie nie rozpoczynają odczytu od zera. Kolejka zwykłego skanowania wraca do tego samego pojazdu, aby zbierać dowody również po pierwszym czytelnym wycinku. Powtarzanie niezmienionego stabilnego wyniku nie dopisuje kolejnych identycznych finalizacji.

Diagnostyka prób obejmuje `dynamic_policy_version=complementary_v1`, `mz_scheduler_reason`, `last_local_object_evidence` i `mz_evidence_status`. Stempel właściciela i lokalna geometria pozostają danymi wykonania; nie dodano nowych pól do protokołów badań.

## Ręczny LOCK i AZ

Jedno dotknięcie ramki pojazdu, jego etykiety lub przypisanej tablicy wybiera pojazd także przed pierwszym OCR. Trafienie respektuje obszary kontrolek oraz aktualną epokę właściciela. Dodatkowe trafianie w etykietę/tablicę obowiązuje tylko przy zwykłej dynamicznej tożsamości; badania zachowują wcześniejsze trafianie w ramkę pojazdu.

Po wyborze kamera uwagi, MT/MZ i lokalne trackery pracują dla wybranego celu. Pozostałe pojazdy nie otrzymują nowych śladów technicznych ani ramek prezentacji. Dopuszczony jest jedynie kandydat weryfikacyjny służący ostrożnemu odzyskaniu tego samego celu. Ponowne dotknięcie nie przełącza ręcznej blokady; wyszukiwanie rejestracji jest wówczas wyłączone.

Po rzeczywistej utracie obu kanałów działa ograniczone odzyskiwanie. Po jego wyczerpaniu sesja zachowuje wybór i przechodzi w `WAITING_FOR_TARGET`: `LOCK · cel utracony`, `Zwolnij → skan`. Nie przejmuje innego auta. Stan ten nie wykonuje nowych inferencji. `Zwolnij` otwiera skan po nowym pomiarze MP; STOP lub zakończenie trybu kończy blokadę. Twardy reset czyści dowody, zachowując ręczny zamiar w oczekiwaniu na zwolnienie.

AZ w zwykłym trybie dynamicznym dopuszcza tylko `USER_PICK` i `SEARCH_PURSUIT`, po spełnieniu istniejących wymagań geometrii, ruchu i potrzeby poprawy odczytu. Własność próbki jest sprawdzana przez pojazd, epokę, sesję i rewizję wyboru; zmiana tekstu nie zmienia właściciela AZ. Budżet jednego cyklu na blokadę jest rezerwowany przed żądaniem i zużywany dopiero po fizycznie zastosowanym przybliżeniu. Odmowa przed startem zwalnia rezerwację. Przerwanie z powodu gwałtownego ruchu używa właściwego sygnału `isRapidMotion()` wyłącznie w zwykłym trybie; badania zachowują wcześniejszy sygnał.

## Walidacja i granice dowodu

| Przebieg | Wynik |
| --- | --- |
| `host-sealed-v1` — szeroka wybrana regresja | **581/581 PASS**, 0 błędów, 0 pominięć |
| `host-final-v1` — końcowe granice UI/AZ, własności i badań | **63/63 PASS**, 0 błędów, 0 pominięć |
| `assembleDebug` dla końcowych źródeł | **PASS** |
| `compileDebugAndroidTestJavaWithJavac` | **PASS kompilacji**, bez uruchamiania testów na urządzeniu |
| Macierz F4/F5A/F6 × R0/R1/R2 | 7 zgodnych obsługiwanych konfiguracji; 2 oczekiwane odrzucenia `N/A_UNSUPPORTED_F6_POLICY`; żadna sesja badawcza nie była uruchamiana |

Przebiegi zawierają łącznie **583 różne przypadki testowe**; część przypadków uruchomiono ponownie. Szeroki przebieg używa 52 wcześniejszych selektorów regresji i dodatkowych testów bieżącej zmiany. Po nim zmieniły się tylko `MainActivity`, `DetectionOverlayView` i nowy `DynamicComplementaryUiTest`: ograniczono nowe trafianie dotknięciem i sygnał przerwania AZ do zwykłego trybu oraz usunięto starą geometrię w stanie oczekiwania na zwolnienie. Końcowy przebieg sprawdza te poprawki, dotychczasowe kontrakty własności/HUD i macierz konfiguracji. Pozostałe źródła są identyczne między oboma przebiegami; pełny manifest końcowych źródeł znajduje się obok wyników testów.

Celowo zmienione oczekiwania wcześniejszych testów dotyczą nowej zatwierdzonej semantyki: sprzeczny OCR nie zrywa ciągłego właściciela, chwilowa niedostępność lokalnego trackera nie jest natychmiastową utratą, a terminalna utrata ręcznie wybranego celu zachowuje blokadę do `Zwolnij`. Zegary fixture odczytów uwzględniają nowy odstęp 750 ms. Wcześniejsze nieudane przebiegi robocze pozostają w osobnych katalogach i nie są oznaczone jako PASS.

Istotne regresje obejmują zachowanie P1 i jednej encji przez 70 kolejnych braków MP przy obecnym lokalnym śladzie przypisanej tablicy, powrót detekcji po TTL, brak wskrzeszenia po utracie obu kanałów, opóźnienia źródłowe, brak dziedziczenia dowodów między epokami, korektę błędnego OCR, selekcję jednego celu, ograniczone odzyskiwanie, budżet AZ i zwolnienie blokady.

W kontrolowanej sekwencji syntetycznej 200 ramek / 10 s harmonogram wykonał **36 prób MZ w starej polityce i 5 w nowej**, zachowując tekst `AB12345`. To test harmonogramu z przygotowanymi detekcjami znaków, bez inferencji modeli, GT, pomiaru zużycia energii ani oceny skuteczności drogowej.

Obrazy UI powstają z rzeczywistego natywnego układu i rysowania Androida w Robolectric, z syntetyczną geometrią pojazdów. Sprawdzane są dotknięcia, widoczna etykieta LOCK, przycisk `Zwolnij`, aktualizacja pewności oraz komunikat utraty celu przy skali fontów 100% i 150%. Nie stanowią odbioru wizualnego całej aplikacji na A12.

`DEVICE_RUN=NOT_RUN`; fizyczna ciągłość w ruchu i AZ: **NOT_VERIFIED**. ADB wykrył podłączony SM-A125F, ale w tej pracy nie instalowano APK ani nie wykonywano testu sceny na urządzeniu. Brak dowodu z kamery pozostaje brakiem dowodu; wyników hosta nie przeniesiono na telefon ani TV.

## Pochodzenie i dostawa

Punkt odniesienia to lokalny, już zmodyfikowany checkout przed rozpoczęciem tej poprawki, z HEAD `f7943a7d7334afd1e4188ef9860b2126331926c8`. Patch jest różnicą względem tego zachowanego stanu, a nie względem czystego Git HEAD.

Zakres źródeł: **36 plików, w tym 5 nowych**; dodatkowo niniejszy raport. Oryginalne zakończenia linii istniejących plików są zachowane. Wcześniejsze wydania nie były modyfikowane. Nie wykonywano resetu, czyszczenia, stasha, commitu ani publikacji.

Kontrola SHA-256 obejmuje 1130 istniejących plików spoza zakresu poprawki: brak naruszeń. Oba poprzednie wydania dynamicznej tożsamości zachowują zgodność wszystkich 1762 pozycji swoich manifestów. Patch odtwarza 37 plików źródeł/raportu w osobnym katalogu bajt w bajt. Końcowy manifest źródeł jest zgodny z przebiegiem `host-final-v1`.

APK: `alpr-dynamic-complementary-debug.apk`, **88 393 241 B**. SHA-256:

```
8ba92b916cbcb739eddf7724534a0443e923aec5bbe9cd18f9afc5d5a9130587
```

Katalog dostawy: `app/build/android_dynamic_complementary_20261117_v1/`. Zawiera APK debug, patch, archiwum źródeł, raporty testów, natywne obrazy UI, manifesty SHA-256, kontrolę niezmienności plików spoza zakresu i próbę odtworzenia patcha w osobnym katalogu. Archiwum źródeł zachowuje bieżący kontekst źródłowy; nie jest samodzielnym pakietem modeli ani offline SDK.

- [APK debug](../app/build/android_dynamic_complementary_20261117_v1/alpr-dynamic-complementary-debug.apk)
- [Źródła, patch i walidacja](../app/build/android_dynamic_complementary_20261117_v1/sources-and-delta.zip)
- [Manifest dostawy](../app/build/android_dynamic_complementary_20261117_v1/delivery.json)
- [Sumy SHA-256](../app/build/android_dynamic_complementary_20261117_v1/SHA256SUMS.txt)
- [Szeroka regresja](../app/build/android_dynamic_complementary_20261117_v1/host-sealed-v1/summary.json) i [końcowe testy](../app/build/android_dynamic_complementary_20261117_v1/host-final-v1/summary.json)
- [LOCK przy 150%](../app/build/android_dynamic_complementary_20261117_v1/host-final-v1/ui/7/synthetic-locked-font1.5.png) i [utrata celu przy 150%](../app/build/android_dynamic_complementary_20261117_v1/host-final-v1/ui/7/synthetic-lost-lock-font1.5.png)

## Próba fizyczna do wykonania

1. Zwykły tryb dynamiczny, jeden pojazd: śledzić widoczny numer Pn przy chwilowej utracie ramki pojazdu, gdy jego tablica nadal jest lokalnie śledzona, a następnie przy utracie samej tablicy.
2. Utrzymać pojazd w kadrze z gorszym i lepszym wycinkiem tablicy: porównać dowody znaków, odstępy MZ, możliwość spadku pewności i korekty odczytu, bez zmiany właściciela.
3. Dotknąć pojazdu przed OCR; wprowadzić drugi pojazd do kadru; sprawdzić wyłączność LOCK, utratę obu śladów, koniec ograniczonego odzyskiwania i powrót do skanu dopiero przez `Zwolnij`.
4. Przy aktywnym AZ sprawdzić powód oczekiwania, rzeczywisty start i powrót, jedną próbę poprawy po zoomie, zachowanie tego samego Pn i jednego fizycznego cyklu na blokadę.

Ta lista opisuje niewykonany odbiór fizyczny i nie nadaje mu statusu PASS.
