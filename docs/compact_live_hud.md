# Lekki HUD kamery

Zmiana robocza z 2026-09-09 zastępuje przewijaną kartę diagnostyczną
przezroczystą nakładką przy prawej krawędzi obrazu.

Trzy osobne pastylki pokazują FPS kamery, TMP oraz RES. Poniżej znajdują się
małe badge MP → MT → MZ, ułożone pionowo i połączone strzałkami, z czasami
ostatnich inferencji. Każdy badge ma dwa wyśrodkowane wiersze: nazwę etapu
u góry i czas poniżej. Przed pierwszym wynikiem dla nowej sceny czasy pozostają
nieznane. FPS nadal pochodzi z licznika klatek kamery, a nie z odwrotności
czasu inferencji. Przycisk HUD 1 pokazuje i ukrywa górną nakładkę.

TMP oznacza temperaturę baterii w stopniach Celsjusza z istniejącego monitora
termicznego. RES oznacza procent wykorzystania CPU przez proces aplikacji
względem wszystkich dostępnych rdzeni:
`100 × przyrost czasu CPU / przyrost uptime / liczba rdzeni`.
Nie jest to pomiar obciążenia GPU ani łączny procent wszystkich zasobów.
Pierwsza próbka CPU po wznowieniu ustala punkt odniesienia i pokazuje kreskę.
Nazwy dostępności wyjaśniają oba pomiary. Aktualizacja korzysta z istniejącego
odpytywania termicznego, bez dodatkowego odczytu czujników.

Tekstowe kąty i ostrzeżenie usunięto z panelu między dolnymi przyciskami.
Zastępuje je celownik 56 dp po lewej stronie, nad wynikiem i sterowaniem:
okrąg, dwie prostopadłe średnice oraz animowana czerwona kulka. Kulka pokazuje
odchylenie boczne i przód/tył, z ograniczeniem ruchu do wnętrza okręgu.
Okrąg jest zielony przy poprawnym ustawieniu i czerwony przy ostrzeżeniu.
Obowiązuje dotychczasowa histereza: ostrzeżenie od 30°, powrót przy 25°.
Brak aktualnego pomiaru daje szary okrąg bez kulki. Wskaźnik ma opis
dostępności, a animacja wygasa po ukryciu lub odłączeniu widoku.

HUD i celownik nie przechwytują dotknięć oraz nie wyznaczają rozmiaru
podglądu kamery. Komunikaty chwilowe mają osobne miejsce na lewo od HUD.
Dokładniejsze teksty diagnostyczne nie są rysowane na obrazie.

Uzupełnienie HUD: dolna nakładka nad przyciskami pokazuje dla MP/MT/MZ
identyfikator wariantu, precyzję i runtime wraz z CPU/GPU. Dane pochodzą
z faktycznie otwartych backendów, także przy zamrożonej konfiguracji badania.
Przeładowanie lub zamknięcie silnika usuwa poprzednie opisy; odczyt UI nie
czeka na monitor inferencji. Pełna nazwa modelu jest dostępna w opisie
dostępności i podpowiedzi. Obok znajduje się rozdzielczość klatki źródłowej;
przed jej otrzymaniem rozdzielczość z ustawień jest oznaczona jako „zadana”.
Osobny przycisk HUD 2 pokazuje i ukrywa dolną nakładkę. Obie ikony znajdują
się obok siebie pod paskiem aplikacji. Widoczność paneli jest niezależna:
można pokazać oba, tylko jeden lub ukryć oba. Kolor każdej ikony sygnalizuje
stan jej panelu.

Czasy etapów pokazują teraz ostatnie rzeczywiste uruchomienie danego modelu
w bieżącej generacji obrazu. Wcześniej kolejne trace bez MZ zastępowały jego
czas kreską, a odświeżanie UI co sekundę mogło całkowicie ominąć klatkę z MZ.
`LiveStageTimings` przechowuje pomiary niezależnie od odpytywania HUD.
Nowa scena, epoka, transformacja, sesja lub otwarcie nowych modeli czyści
zapamiętane czasy. Trace i statystyki badania nadal zawierają wyłącznie etapy,
które wystąpiły w konkretnej klatce; do klatek bez MZ nie dopisuje się czasów.

Weryfikacja: 93/93 testy JVM dla UI i czujników oraz 5/5 testów
instrumentacyjnych dashboardu na emulatorze Android 14. Dwa testy układu
powtórzono po zastąpieniu tekstowych strzałek ikonami. Sprawdzono zrzuty
360×640 dp, 360×800 dp i 320×640 dp z czcionką 130%, a także działającą
nakładkę z ikonami i odczytami TMP/RES na emulatorze. Test wskaźnika sprawdza
kolory i animację kulki; test układu potwierdza stały rozmiar podglądu przy
zmianie widoczności HUD. Obie paczki debug zbudowane poprawnie.
Na fizycznym telefonie nowej wersji nie instalowano.

Weryfikacja uzupełnienia MZ i wariantów: 201/201 testów JVM dla metryk, UI
i pipeline'u oraz 6/6 testów dashboardu na emulatorze. Test integracyjny
potwierdza czas MZ 420 ms po kolejnej klatce zawierającej wyłącznie MP,
bez dopisywania MZ do jej trace. Sprawdzono również zrzuty małego ekranu
z normalną i powiększoną czcionką. Test przełączania zaktualizowano dla
niezależnego HUD 1 i HUD 2.

Po rozdzieleniu sterowania przeszły 3 testy instrumentacyjne: niezależne
przełączanie obu paneli, układ na krótkim i wysokim ekranie oraz większa
czcionka na wąskim ekranie. Obie paczki debug zbudowano poprawnie.
