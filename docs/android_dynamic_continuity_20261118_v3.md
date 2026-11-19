# Tryb dynamiczny: ciągłość pojazdu, harmonogram modeli i prezentacja v3

Data: 2026-11-18. Zmiana rozwija istniejący stan roboczy z wcześniejszymi zmianami poza commitami. W repozytorium roboczym nie wykonano resetu, czyszczenia, stasha, commita ani publikacji. Techniczne bazy do weryfikacji łatki są tworzone wyłącznie w izolowanych katalogach `app/build`.

**Końcowy status hosta: 653/653 PASS, build APK i kompilacja testów urządzenia PASS. Instalacja i próby fizyczne: NOT_VERIFIED_DEVICE_UNAVAILABLE — A12 nie jest widoczny przez USB.**

## Zachowanie

- Pn należy do trwałej encji pojazdu. Inny odczyt MZ, zakończenie kolejki akwizycji lub nowy techniczny track nie tworzą nowego pojazdu.
- Lokalny dowód z pojazdu albo przypisanej tablicy podtrzymuje obecność. Po utracie obu kanałów geometria ma dotychczasowe maksimum 600 ms podtrzymania, a możliwość odzyskania Pn trwa 5 s od ostatniego rzeczywistego dowodu. Predykcja, wybór pojazdu i zmiana TTL nie odnawiają tego terminu.
- Odzyskanie wykorzystuje jednoznaczne dopasowanie 1:1 geometrii, ruchu i wyglądu. Zachowano dotychczasową bramkę wyglądu 0,55 oraz istniejące ostrzejsze warunki szerokiego odzyskiwania. Nie wymaga odczytu MZ. Niejednoznaczny kandydat pozostaje bez numeru w stanie „Weryfikacja”.
- Wygaśnięcie niejednoznacznych rodziców samo nie nadaje numeru. Potrzebny jest kolejny rzeczywisty, jednoznaczny pomiar MP.
- Poprzednie dowody znaków pozostają przy potwierdzonym właścicielu. Niezależne zgodne obserwacje zwiększają wsparcie hipotezy; sprzeczne mogą zmniejszać pewność i poprawiać tekst. Sam upływ czasu nie podnosi pewności.

## Wykonanie i pamięć

Modele działają na jednej kolejce wykonania. MP i MT publikują geometrię przed MZ. Po zakończeniu zadania geometrii pełna bitmapa kamery jest zwalniana; oczekujące MZ przechowuje własny crop, kontekst źródła i token właściciela. Śledzenie pikseli i UI pozostają niezależne. Wykonywana inferencja MZ zajmuje wspólnego workera do swojego zakończenia.

Harmonogram oblicza EWMA z wagą 0,25 z kosztu przygotowania, inferencji i obróbki wyniku; MZ uwzględnia rektyfikację. Pominięcie modelu nie dodaje próbki zerowej. Koszty są resetowane po zmianie źródłowej rozdzielczości oraz przy odtworzeniu silnika/modeli/profilu.

`N = max(1, ceil(T_MZ / max(T_MP, T_MT)))`

Początkowo N=2. Pierwszy poprawny crop może zostać odczytany od razu. Kolejne próby wymagają N rzeczywistych, kwalifikujących się cykli geometrii. Stabilny odczyt lub dwie puste próby zwiększają odstęp trzykrotnie; lepszy crop usuwa ten dodatkowy mnożnik. Odrębne timery 750 ms/3 s nie sterują już zwykłym DYN.

Oczekuje najwyżej jeden crop. Nowszy poprawny crop tego samego właściciela zastępuje poprzedni i zwalnia jego pamięć. Stary kontekst, utracony właściciel, STOP, zamknięcie silnika lub odmowa wykonania zwalniają zasoby. Wynik MZ nie dostarcza geometrii do ponownego narysowania. Zachowano limity kamery i pamięci poprzedniej poprawki oraz ustawienia CPU/GPU operatora.

Puste pierwsze MZ zwraca obserwację wykonanej próby z cropem, czasami i zerowym wsparciem znaków. Aktualizuje budżet prób i harmonogram, ale nie tworzy odczytu, dodatkowej encji ani sztucznej pewności. Brak znaków jest odróżniony od odrzucenia nieaktualnego właściciela lub kontekstu.

## Prezentacja i galeria

Oddzielny prezenter DYN składa ramkę, tablicę, numer i odczyt z atomowej migawki. Korekty pomiarowe wygaszają się z czasem 120 ms, a bieżący ruch lokalnego trackera nie dostaje kolejnego opóźnienia. Rewizja korekty pochodzi z kotwicy faktycznie zastosowanej przez tracker pikseli. Czas przetwarzania nie zastępuje czasu źródłowego kamery.

Ramki zachowują turkus. Wybrany pojazd wyróżnia grubość linii i etykieta. DYN pomija animacje przenoszenia odczytu, pulsujący znacznik etapu oraz kolory zależne od akwizycji. Tablica spoza geometrii właściciela jest wycofywana z prezentacji i zlecane jest odświeżenie MT, najwyżej raz na jej pomiar.

Wynik przy HUD 2 pozostaje przy wybranym Pn podczas pominiętych/pustych MZ. W okresie odzyskiwania jest oznaczony jako ostatni odczyt. Wybór automatyczny pozostaje przy pierwszym śledzonym pojeździe do utraty albo wskazania innego przez operatora. Ręcznie wskazany utracony cel nie jest samoczynnie zastępowany numerem innego pojazdu. Oczekujące MZ uwzględnia zmianę rewizji wyboru celu.

Pełny render 720×1600 przy gęstości 320 dpi ujawnił za małą wysokość wiersza wyniku przy foncie 150%. Prezenter DYN zwiększa ją zależnie od skali fontu; po wyjściu z DYN przywraca dotychczasowe wymiary. Wartość odczytu i oznaczenie ostatniego wyniku są sprawdzane pod kątem rzeczywistego ucięcia tekstu i nakładania kontrolek.

Galeria zachowuje istniejące grupowanie po przebiegu, scenie i kanonicznej encji. Warianty MZ i techniczne tracki nie są odrębnymi kartami tego właściciela. Zdjęcia i surowe odczyty zachowują własne pochodzenie. Stare sesje nie są scalane po tekście. Schematy eksportów badawczych nie zostały zmienione.

## Walidacja

| Przebieg | Wynik |
| --- | --- |
| `core-second` | 42/42 PASS — harmonogram, znaki i odzyskiwanie |
| `integration-first` | 37/38; błąd przygotowania tematu kontekstu testowego HUD poprawiony |
| `host-first` | 640/647; sześć nieaktualnych oczekiwań oraz za ostry próg wyglądu poprawione |
| `validated-targeted` | 135/135 PASS; wynik pośredni przed ostatnim doprecyzowaniem licznika nowej encji |
| `host-final` | 649/649 PASS; APK i kompilacja testów urządzenia PASS — przed końcowym dopasowaniem pełnego układu HUD i wyboru ręcznego |
| `hud-layout-final` | 1/1 PASS — pełny render przy fontach 100%/150%, wynik aktualny i ostatni |
| `host-sealed-final` | 652/652 PASS; pełny render HUD, APK i kompilacja testów urządzenia PASS — przed doprecyzowaniem pierwszego pustego MZ |
| `release-final` | **653/653 PASS; APK i kompilacja testów urządzenia PASS** — źródła zgodne bajtowo z manifestem tego przebiegu |
| Fizyczna próba 180 s bez mirroringu | NOT_VERIFIED — A12 nie jest aktualnie widoczny przez USB |
| Fizyczna próba 180 s z mirroringiem | NOT_VERIFIED |

Nowe testy obejmują wolniejsze i szybsze MZ, liczenie tylko rzeczywistych cykli, ograniczenie i zwalnianie kolejki, zmianę odczytu bez zmiany pojazdu, odzyskanie po 4,9 s i granicę 5 s, niejednoznaczność 1:1, brak nieobserwowanego Pn/P0, opóźnione MZ, wspólną geometrię, stały kolor i rzeczywiście renderowane Pn.

Wyniki hosta, telemetrii oraz ocena wizualna operatora są osobnymi dowodami. Syntetyczny HUD nie potwierdza zachowania kamery, drogi, braku krótkich błysków ani stabilności z mirroringiem.

## Pochodzenie

Punkt bazowy: Git HEAD `f7943a7d7334afd1e4188ef9860b2126331926c8` i istniejący brudny stan. Zabezpieczono 957 plików źródłowych oraz sumy 1179 istniejących plików repozytorium w `app/build/android_dynamic_continuity_20261118_v3/audit/`.

Łatka dotyczy tej kopii stanu roboczego, nie czystego HEAD. Końcowy zakres to 28 plików źródłowych i ten raport. 1157 wcześniejszych plików poza zakresem pozostaje zgodnych z zabezpieczonymi sumami. Pełny końcowy zbiór obejmuje 963 pliki `app/src`.

APK: `alpr-dynamic-continuity-20261118-v3.apk` w katalogu projektu; kopia znajduje się w `app/build/android_dynamic_continuity_20261118_v3/alpr-dynamic-continuity-debug.apk`. Podpis APK zweryfikowany narzędziem Android SDK.

SHA-256 APK: `ec78f8876a3ec748424f6adb005f7555dec6ebf03fd398f91d5d71a350ed40dc`.

Paczka źródeł, łatka, `delivery.json`, sumy SHA-256 oraz dowody testów znajdują się w `app/build/android_dynamic_continuity_20261118_v3/`. Weryfikacja dostawy wymaga bezbłędnego zastosowania łatki do izolowanej kopii bazowej i zgodności wszystkich 963 końcowych źródeł. Nie dołącza się prywatnych ustawień, sesji telefonu ani surowych logów urządzenia do paczki źródeł.

Do zakończenia odbioru: ponownie połączyć A12 przez USB, zatrzymać analizę do aktualizacji, zainstalować APK z zachowaniem danych i wykonać osobno 180 s bez mirroringu oraz 180 s z mirroringiem. Przez całą próbę materiał z jednym pojazdem i czytelną tablicą musi pozostać widoczny; ocena operatora jest wymagana niezależnie od telemetrii.
