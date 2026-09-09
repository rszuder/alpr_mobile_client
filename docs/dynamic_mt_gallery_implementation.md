# DYNAMIC: adaptacyjne MT, próg w opcjach i obserwacje galerii

Wdrożenie robocze z 2026-09-09 na podstawie
[handoffu z uwagami użytkownika](handoffs/android-dynamic-adaptive-mt-roi-top1-plate-v1.md).

## Ustawienia i bramka

Opcje → tryb analizy → **DYNAMIC: próg pojazdu i obszar MT** pozwala ustawić:

| Parametr | Domyślnie |
|---|---:|
| Szerokość wejścia | 120 px |
| Wysokość wejścia | 80 px |
| Szerokość podtrzymania | 100 px |
| Wysokość podtrzymania | 64 px |
| Początek podstawowego ROI od góry pojazdu | 35% |

Wymiary muszą mieścić się w 1–8192 px, podtrzymanie nie może przekraczać
wejścia; początek ROI przyjmuje 0–80%. Zmiana progów kasuje dotychczasową
kwalifikację i wymaga nowego MP. Ocena nadal używa surowej ramki MP w obrazie
źródłowym, przed wyborem ROI i bezpośrednio przed backendem MT.
Zmiana ustawień zostaje zastosowana przez istniejące przeładowanie silnika.
Konfiguracja badania zamraża progi, politykę i parametry ROI.

## Polityka ROI

- Znana, prawidłowo przypisana tablica: lokalny wycinek z marginesem po
  50% szerokości i 75% wysokości ramki tablicy. Kotwica ma maksymalny wiek
  5 s w domenie czasu źródłowego i jest przenoszona względem świeżej ramki MP.
  Dwukrotna zmiana skali w dowolną stronę kończy możliwość użycia kotwicy.
- Bez takiej kotwicy: dolne 65% pojazdu, margines poziomy po 4% szerokości
  i dolny 8% wysokości. Początek pionowy jest konfigurowalny.
- Nieudane lokalne MT ustawia kolejny poziom na podstawowy. Brak poprawnej,
  własnej tablicy w podstawowym ROI ustawia szerszy fallback. Jawna dyrektywa
  expanded retry ma pierwszeństwo i może przejść od razu do szerszego ROI.

Zmiana sceny, epoki lub transformacji kamery unieważnia lokalną kotwicę.
Sam predicted-only nie ustanawia kotwicy i nie otwiera bramki rozmiaru.
AutoZoom zachowuje osobną politykę decyzji. STATIC zachowuje dotychczasowe
obszary i selekcję detekcji. R0 bez encji pojazdu pozostaje poza TOP-1 encji.

## Asocjacja i TOP-1

W DYNAMIC z pulą pojazdów asocjacja odbywa się przed selekcją i przed
przypięciem śladu tablicy do encji. Używa ramki MP z tej samej klatki
źródłowej, zamiast późniejszej predykcji przygotowanej do prezentacji.
Nie usunięto walidacji dolnego położenia tablicy ani konkurencji sąsiadów.

Nieprawidłowa geometria, brak jednoznacznego właściciela, zbyt mała encja
lub właściciel inny niż żądany cel wykluczają kandydata z MZ i prezentacji
tego celu. Obca tablica zachowuje rzeczywistego właściciela w telemetrii.

Ranking poprawnych kandydatów: 30% zgodności z poprzednią geometrią,
25% jakości geometrycznej, 25% confidence MT, 10% ostrości i 10% zgodności
położenia pionowego. Remis rozstrzygają jakość, confidence, odległość od
poprzedniego położenia i indeks źródłowy. OCR nie uczestniczy w rankingu.
Do trackera i MZ przechodzi najwyżej jeden kandydat encji w klatce,
a więc również najwyżej jeden z pojedynczej próby MT.

## Telemetria i badania

Trace zapisuje rodzaj i granice ROI, surową liczbę detekcji, liczbę
przypisanych detekcji, wybór i powód TOP-1 oraz faktyczną liczbę kandydatów
wykonanych przez MZ. Parametry polityki znajdują się w zamrożonym
`dynamic_mt` konfiguracji sesji.

Każde rzeczywiste wywołanie backendu nadal otrzymuje własne
`mt_invocation_id` i dowód faktycznego wejścia. Wiersze detekcji tej samej
inferencji dzielą identyfikator wywołania i zachowują surowe
`mt_detection_count`. Metadane adaptacyjnego ROI są kopiowane także do
wierszy kolejnych detekcji. `raw_assigned_count` jest liczone dla encji
w obrębie konkretnego wywołania. TOP-1 nie kasuje surowych wierszy badania.

## Galeria i szczegóły

Normalna galeria DYNAMIC grupuje powtórzony numer również pomiędzy encjami
i scenami. Porównanie ignoruje wielkość liter, odstępy i myślniki; nie
koryguje podobnych znaków OCR. Puste wyniki i krótkie fragmenty poniżej
4 znaków nie są łączone między encjami.

Powtórzenie dodaje metadane obserwacji do jednego wpisu. Nie kopiuje kolejnej
bitmapy tego samego numeru do historii ani normalnej listy cropów.
Korekta tekstu może zastąpić reprezentatywny crop zgodnie z jego jakością.
Callback MZ i końcowy wynik tej samej klatki są jedną obserwacją.
Późne przypisanie właściciela może zaktualizować metadane tej obserwacji.
To grupowanie prezentacji nie łączy encji w trackerze.
Surowe próbki research oraz reguły galerii STATIC pozostają odrębne.

Szczegóły zawierają dla każdej obserwacji czas, tekst, ID encji i tracków,
scenę, epokę, transformację, klatkę, rozdzielczość, pewność MT i odczytu,
czasy MP/MT/MZ, czas potoku do odczytu i powód asocjacji. Zachowują też
FPS, temperaturę baterii, CPU aplikacji oraz warianty/runtime modeli.
Telemetria HUD jest migawką przy odbiorze obserwacji; okno szczegółów
nie podmienia jej później bieżącymi wartościami telefonu.

Canvas podglądu ma 90 dp zamiast 180 dp. Usunięcie wagi layoutu zapobiega
rozciąganiu podglądu na wolną wysokość okna.

## Pliki i walidacja

Główne pliki: `DynamicMtConfig`, `DynamicMtSettings`,
`DynamicVehicleSizeGate`, `SettingsActivity`, `ResearchExecutionConfig`,
`AdaptiveMtRoiPolicy`, `EntityPlateTop1`, `MobileAlprEngine`,
`AlprPipeline`, `ResearchAttemptBatch`, `AcquisitionAttemptRecord`,
`RecognitionHistoryStore`, `RecognitionHistoryItem`,
`RecognitionHistoryObservation`, `ObservationTelemetry`,
`RecognitionObservationDetails`, `LiveHudView`, `MainActivity`
oraz layouty ustawień i szczegółów.

Przeszło 630/630 testów JVM oraz 47 różnych testów instrumentacyjnych
galerii, szczegółów, ustawień i zapisu badań na emulatorze. Jeden starszy
test weryfikacji próbki wymagał przewinięcia do przycisku na małym ekranie;
po korekcie testu przeszedł. Testy archiwum potwierdzają odrębne identyfikatory
retry, zachowanie wejść i surowych liczników po TOP-1. Sprawdzono zrzuty
okna szczegółów i formularza opcji.

Progi, marginesy i pięciosekundowe okno kotwicy wymagają strojenia na
rzeczywistych ujęciach. Nie wykonano jeszcze porównania skuteczności modeli
na fizycznym telefonie; nowy APK nie został na nim zainstalowany.
