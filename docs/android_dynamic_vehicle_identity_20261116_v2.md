# Android — rozpoznana tożsamość pojazdu, B v2

Data odbioru: 17.11.2026. Pracę i snapshot rozpoczęto 16.11; zachowano katalog `app/build/android_dynamic_vehicle_identity_20261116_v2/`. Jest to oddzielny patch względem zastanego, brudnego stanu po A i B v1. Nie zastępuje opublikowanych materiałów tych wydań.

## Status dostawy

Źródła i host: **DONE**. Finalny `host-sealed-v2`: **425/425 PASS**, 0 FAIL/ERROR/SKIP; zachowano wszystkie selektory wcześniejszego zestawu 393 testów. Dodatkowy `research-canvas-sealed-v2`: **2/2 PASS**, 0 FAIL/ERROR/SKIP. `assembleDebug`, kompilacja instrumentacji, integralność APK i zgodność podpisu: **PASS**. APK zainstalowano na A12 bez kasowania danych. Odbiór całości: **PARTIAL** — fizyczny UI 100%/150% wymaga odblokowania telefonu, a próba dynamiczna na rzeczywistym materiale pozostaje **NOT_VERIFIED**. Sam build, renderer i instalacja nie oznaczają GO dla wersji dyplomowej.

Finalny APK: `alpr-b-debug.apk`, SHA256 **`de796f273726a3c051f9c2d69304a4069490b418a778d19e75191d53867fa624`**. Podpis SHA256 `f3385991af6f601faf4df59697d30f143101a71ee988035745027d3e85aa04a2` jest zgodny z zastanym APK oraz wcześniejszym A/B v1. Ścieżki raportu, patcha, źródeł, demonstracji, pełnych logów i metadanych są w katalogu wydania i `SHA256SUMS.txt`.

## Diagnoza rzeczywistego błędu

W B v1 nie wystarczało sprawdzenie `missedUpdates > 0`. Przy szybkim zastąpieniu A przez B bez próbki pustego MP bieżący track nadal kojarzył się z A. Poprawka geometrycznego powrotu utraconych tracków nie obejmowała tego przypadku. Ponadto `VehicleEntity.shouldAdoptConsensus` i `BadgeReadingPolicy.retainBest` celowo zachowują mocniejszy historyczny odczyt; słabsze, poprawne B mogło nie zastąpić A. `PlateTrackCoordinator.entityStates` przechowuje również konsensus, oczekiwaną długość, układ i budżet MZ według encji. Błędne wczesne skojarzenie z A dawało więc B cudzą pamięć OCR, zanim powstał nowy odczyt.

Minimalna reprodukcja na **oryginalnych źródłach prywatnego snapshotu**, bez zmiany katalogu roboczego: `audit/replay_before.py` i `audit/before-quick-replacement/`. Log potwierdza: 1,0 s → track 1 / entity 1 / `P1: AB12345`; 1,2 s, świeży OCR `CD9876` → nadal track 1 / entity 1 / `P1: AB12345`. Obok pozostaje osobny pojazd, entity 2. SHA każdego skompilowanego źródła i pełny log są zachowane. To reprodukcja błędu, nie test zaliczony dla starego algorytmu.

Przed zmianą produkcji wykonano też czerwony test `regression-before-v2` (1 test, 1 FAIL). Oddzielnie wykazano zgodność wszystkich **937/937** zastanych plików `app/src` z zapieczętowanym B v1. Zastany APK telefonu ma jednak inne SHA niż APK wydania B v1; nie utożsamiono binariów. Szczegóły: `audit/input-provenance.json`, `device/before/state.json`.

## Mapa identyfikatorów i ich granic

| Nazwa | Znaczenie / moment nadania | Usunięcie / granica | Faktyczne zastosowanie |
|---|---|---|---|
| `sourceFrameId`, `sourceSequence`, `sourceTimestampNanos`, `sourceIndex` | źródło pomiaru i indeks detekcji MP | następna klatka / bramki aktualności | przypisanie prawdziwej ramki; nie numer pojazdu |
| `VehicleTrackManager.Track.trackId`, `vehicleTrackId` | techniczny ślad przy nowej serii detekcji | TTL / reset sceny | diagnostyka i właściciel bieżącego pomiaru |
| `VehicleEntity.entityId` | encja istniejącego repozytorium | jawne retire, pojemność, TTL encji, reset domeny | klucz domeny i powiązań; od v2 nie jest bezwarunkowo numerem P |
| `DynamicVehicleIdentity.State.number` | numer operatora przy zatwierdzeniu odrębnej encji | zakres runtime; nowy przebieg przy pustej domenie może zacząć od 1 | zwykły dynamiczny HUD; kandydat weryfikacji ma 0 wewnętrznie i napis „Weryfikacja” |
| `DynamicVehicleIdentity.Stamp` | entity + technical track + monotoniczna epoka właściciela przy skierowaniu MZ | utrata, konflikt, promocja kandydata, reset / zmiana polityki | odrzuca spóźniony wynik także w tej samej scenie; transient, bez pola w archiwum |
| `OverlayItem.trackId` dla VEHICLE | entity bieżącego MP / lokalnego śledzenia | zastąpienie pomiaru / granice UI | renderer tłumaczy entity na numer operatora; utracony właściciel nie rysuje ramki ani markera |
| `plateTrackId`, `OverlayItem.trackId` dla PLATE | techniczny ślad MT/MZ | polityka trackera tablic / generacje | geometria i wybór tablicy; nie fizyczna tożsamość pojazdu |
| `PlateTrackCoordinator.identityEpochs` | epoka niezależnej pamięci OCR dla encji | zmiana właściciela / reset | świeży konsensus, szablon długości/wierszy i budżet kandydata; unassigned ma oddzielny token utraty |
| `sceneGeneration`, `visualEpoch`, `cameraTransformGeneration` | istniejące granice koordynatora sceny | odpowiedni reset / odzyskiwanie / transformacja | dotychczasowe bramki aktualności pozostają aktywne; runtime epoka właściciela jest dodatkową bramką |
| `TargetSession`, `TargetFocusSnapshot.revision` | uwaga operatora / sesja istniejącego kontrolera | istniejący lifecycle | kanonizacja C→A po dowodzie nie tworzy nowej sesji, czasu ani rewizji wyboru |
| klucz tekstowy `RecognitionHistoryStore` | grupowanie rzeczywistych odczytów galerii | zakres historii | ten sam tekst może grupować różne encje; nie scala fizycznych pojazdów |

`tracksCreated` nadal liczy techniczne ślady. W normalnym dynamicznym trybie `VehicleTrackingStats.entitiesCreated` zwiększa się dopiero dla zatwierdzonej nowej tożsamości; kandydat odzyskany jako A nie dodaje pojazdu. `ScanAcquisitionStats.vehiclesSeen` pozostaje istniejącym wewnętrznym rozliczeniem kandydatów kolejki, nie źródłem numeru przy ramce. Nie zmieniono schematu tej telemetrii ani historycznych rekordów badań. W statycznym/badawczym renderer zachowuje poprzednie mapowanie P + entityId.

## Wdrożone zachowanie

Zasięg produkcyjny: **zwykły dynamiczny TRACKED_MP**, bez aktywnego trybu badawczego i bez zamrożonej konfiguracji badania. Nie dodano trackera ani systemu ReID. `DynamicVehicleIdentity` przechowuje wyłącznie dowody i własność w istniejącym managerze/repozytorium.

1. Dla wcześniej rozpoznanej encji kolejna asocjacja wymaga dodatniego lokalnego dowodu rzeczywistego obiektu, kontynuowanego po poprzednim MP i obejmującego czas nowego źródła. Sam globalny ruch kamery/sceny, predykcja i ponowny anchor nie są takim dowodem. Faktyczny miss, utrata lokalnego śledzenia, niepewny reanchor/global motion, wygaśnięcie tracka lub konflikt świeżego tekstu unieważniają właściciela. Starsza klatka preview nie unieważnia nowszego poprawnego dowodu. Rezerwacja dla zablokowanego utraconego A nie usuwa bieżącej detekcji potrzebnej kandydatowi do OCR; niedopuszczony właściciel A nie konkuruje też z priorytetowym kandydatem o kolejny MP. Aktywny dopuszczony sąsiad nadal podlega dotychczasowym marginesom i regule 1:1.
2. Nowy MP w ograniczonej domenie powrotu tworzy roboczego kandydata z własnym stanem OCR i napisem **„Weryfikacja”**, bez starej tablicy i bez P0. Utracona ramka A i marker A znikają. Pamięć długości, układu, głosów i budżetu A nie przechodzi na kandydata.
3. Potwierdzenie wymaga **dwóch niezależnych, świeżych, identycznych surowych odczytów MZ**, po istniejących bramkach jakości i geometrii. Użyto istniejącego `RegistrationTextPolicy.plausibleLength` (minimum konsensusu 5 znaków), bez nowego progu OCR. Normalizacja to istniejące uppercase/alnum, bez zamiany O/0. Ten sam frame/sequence/time nie jest drugim dowodem. Odczyt pusty lub zbyt krótki nie jest potwierdzeniem. Konflikt zaczyna serię zgodności od nowa. Historyczny konsensus ani cached result nie są świeżym dowodem.
4. Zgodny pełny klucz, ważne okno i dokładnie jeden pasujący utracony właściciel dają C→A. Aktualny technical track, MP, geometria i świeże OCR zostają; numer P i licznik pojazdów pozostają A. Jedno A nie może przejść na dwa tracki. Dwóch równorzędnych rodziców z tą samą tablicą pozostaje nierozstrzygniętych.
5. Dwa spójne różne odczyty zatwierdzają nowe B. Pewność historycznego A nie konkuruje wtedy z pewnością B. Brak dowodu do końca istniejącego okna zatwierdza nową encję; późny zgodny tekst nie odtwarza wygasłego prawa do A. Brak opisu wyglądu lub jego konflikt nie daje prawa do starego ID.
6. Świeży konflikt jest sprawdzany **przed** dodaniem wyniku do agregatora i rejestracji A. Krótkie mutacje pamięci/bindera używają wspólnej blokady właściciela z preview oraz istniejącej bramki sceny. Odbiór końcowego wyniku i asynchronicznego cropa ponownie sprawdza epokę. Kopie DTO/restamping zachowują token. Filtrowane wyniki współdzielą istniejące zamykanie bitmap.
7. HUD usuwa pamięć badge’a, oczekujące transfery i aktywną animację tablicy utraconego właściciela. Ponowne podanie starego snapshotu nie przywraca go na kandydata. Samo grupowanie historii po tekście nie jest zmieniane. Próbkowanie cropów normalnych rozróżnia epokę właściciela mimo ponownego użycia technicznego plateTrackId.

### Okno, geometria i progi

Nie dodano arbitralnego stałego czasu. Przy rzeczywistej utracie zamrażane jest:

`min(lastMp + entityTTL, max(lastMp, lastValidLocalObjectEvidence) + min(entityTTL, currentTrackTTL + currentReacquireGrace))`.

Dowód lokalny oznacza poprawną, nieprzewidywaną obserwację istniejącego trackera, bez kolejnych błędów i z potwierdzonym wyglądem. Global motion nie odnawia dowodu. Po utracie predykcja, wybór celu, nowy TTL i późniejsze zmiany grace nie wydłużają zamrożonego terminu. Granica jest włącznie; 1 ns po niej odzyskanie nie jest dozwolone. Geometria dopasowania może korzystać z ostatniego rzeczywistego lokalnego bbox albo dotychczasowej predykcji, lecz nigdy samodzielnie zatwierdza tożsamości rozpoznanego A.

Istniejące wartości zachowano: bazowy trackTTL 1,8 s; koordynator minimum 3 s i `ceil(1,75 × odstęp/latencja)`, maksimum entityTTL − 0,1 s; zachowanie encji 600 s; grace koordynatora od 1 s, adaptacja ograniczona do 15 s. Wstępne kandydatury używają istniejącej bramki wyglądu 0,55 i geometrycznego score > 0,50. Nie zmieniono scoringu, CER, IoU, detektorów ani modeli. Testy małych syntetycznych TTL sprawdzają oba boki granic, bez twierdzenia, że to pomiar wydajności A12.

## Testy i materiały dowodowe

Pełny wykaz PASS/FAIL/ERROR/SKIP pochodzi z rzeczywistych XML i jest w `tests-summary.json`; logi każdego podejścia pozostają w jego katalogu. Nie zmieniono asercji wcześniejszych 393 testów.

- `RecognizedVehicleIdentityTest`: szybka zamiana bez pustego MP, rzeczywista utrata i wygaśnięcie technical tracka, dwa świeże zgodne/różne odczyty, duplikat źródła, częściowy/brak OCR, konflikt serii, lokalna ciągłość przy zmienionych indeksach MP, global motion, nieaktualny preview/MZ, deadline ±1 ns, brak przedłużenia przez predykcję/wybór/TTL, aktywny sąsiad, ten sam tekst dwóch pojazdów, niejednoznaczność i 1:1, brak/konflikt wyglądu, reset i zwykła/legacy polityka.
- `DynamicIdentityOwnershipTest`: **rzeczywisty MobileAlprEngine, dekodery, binder, rejestracja i agregator**, ze specjalnie oznaczonymi syntetycznymi tensorami zamiast bibliotek NN. Poprawne B z 65% i 6 znakami nie dziedziczy A z 99% i szablonem 7 znaków. Zgodne OCR odzyskuje A. Konflikt przy dodatnim lokalnym śledzeniu nie zatruwa A. Utrata w czasie MZ anuluje wynik tej samej sceny. Kopie/crop/filtr/bitmapy i kanonizacja sesji są sprawdzone.
- `RecognizedVehicleIdentityUiTest`: rzeczywisty natywny renderer HUD, dwa pojazdy, przed/po, powrót A i wejście innego B, 100%/150%, rzeczywiste etykiety i granice badge’y, wygaszenie starego badge’a oraz transferu tablicy.
- Zachowane testy Options v2, gotowości v1, wizjera Main v1 i B v1, a także rozdzielenie normalnego CPU i badań. Oddzielna macierz F4/F5A/F6 porównuje całe konfiguracje R0/R1/R2; F6 R1/R2 pozostają oczekiwanym odrzuceniem kontraktu, nie udawanym udanym eksperymentem.

Podejścia nieudane: `core-first-v2` — brak publicznego dostępu do odczytowego `verifying`, poprawiono; `core-second-v2` — test wybierał pierwszy zatrzymany snapshot A zamiast bieżącej detekcji MP, skorygowano wybór według `sourceIndex` i zachowano ścisłe `assertNotEquals`; `integration-first-v2` — fixture próbowała dziedziczyć po finalnym ModelRegistry, poprawiono wstrzyknięcie fixture; `integration-second-v2` — fixture bez native graphics nie wyznaczała homografii, a oczekiwanie szablonu po jednym odczycie było sprzeczne z istniejącym kontraktem dwóch obserwacji. Poprawiono fixture, bez obniżania bramek produkcyjnych. `integration-third-v2` — 27/27 PASS. Czerwony test na bazie i wszystkie te logi pozostają w dostawie.

Końcowy audyt priorytetu ujawnił dwie dalsze ścieżki tego samego problemu: `reservedForPriorityRecovery` zatrzymywał nowy MP przed kandydatem, a test konkurencji w `assignPriorityTarget` dopuszczał do konkurowania utracone A, które nie miało już prawa do asocjacji. `priority-before-v2`: 2/2 FAIL jako reprodukcja. Pierwsza poprawka usuwała rezerwację, ale rzeczywisty silnik nadal zatrzymywał następny MP (`vehicle_measurement_required`); pełny trace w `focused-trace-v2` dokumentuje drugą przyczynę. Dodano tylko dwa warunki zwykłego dynamicznego trybu, bez zmiany score/progów. `priority-after-v2`: **26/26 PASS**, w tym bieżące ROI kandydata przy blokadzie A, zgodna oraz inna tablica. Wcześniejszy pełny przebieg `host-regression-v2`: 423/423 PASS; po tych końcowych zmianach wykonano osobny `host-sealed-v2`: **425/425 PASS**. Nie miesza się dowodu obu wersji.

Zewnętrzny walidator macierzy i Canvas ma oddzielne źródła w `audit/matrix-tests`, bez zmian testów badań w repo. Pierwsza kompilacja `priority-matrix-canvas-v2` ujawniła konflikt nazw `Path` w fixture, poprawiono kwalifikację Java. Drugi przebieg miał poprawne wyniki testu macierzy, Canvas i managera oraz opisany FAIL integracji priorytetu. Dodatkowa kontrola pikseli obu ramek i etykiet potwierdziła zgodność natywnego Canvas z hierarchią HUD; PNG mają przezroczyste tło, bez sztucznego zdjęcia pojazdów.

`demonstration/index.html`, PNG i CSV przedstawiają **syntetyczne wejście**, nie nagranie drogi. CSV zawiera czas → technical track → entity → faktyczny napis HUD → surowy OCR → licznik zatwierdzonych pojazdów. Jedna scena pokazuje powrót A do P1 mimo nowego tracka; druga nowe B z P3 obok niezmienionego P2, bez przejęcia P1 ani tablicy A. Zapisane źródła i bezpośrednia sonda bazy wiążą „before” z rzeczywistą audytowaną przyczyną.

## Telefon, dane i ograniczenia odbioru

Zastany SM-A125F / Galaxy A12, Android 12, 720×1600, density 300, font 1.0. Przed zmianami zachowano APK, surowe XML preferencji i metadane **366 sesji**. Stan procesu był pusty. Nie odinstalowuje się aplikacji ani nie czyści danych. Przed instalacją należy sprawdzić podpis nowego i zastanego APK; dopuszczalna jest aktualizacja `install -r -t` przy zgodnym certyfikacie.

Instalacja finalnego APK przez `install -r -t`: **PASS**. Odczyt SHA z faktycznie zainstalowanego `base.apk` odpowiada finalnemu plikowi. Zachowano **366/366** SHA metadanych sesji, **5/5** surowych XML preferencji i **38/38** SHA plików modeli/pakietów. Font został przywrócony do 1.0, density pozostało 300. Nie odinstalowywano aplikacji ani nie czyszczono danych. Dowody: `device/install-v2.log`, `final-signature.txt`, `preservation.json`, snapshoty przed/bezpośrednio przed/po oraz wykazy SHA modeli.

Pierwszy fizyczny dump UI ujawnił ekran blokady, a nie Main. Próba wybudzenia potwierdziła wymaganie odcisku palca lub wzoru; nie podawano ani nie obchodzono poświadczeń. `device/ui/main-100.*` i `awake-state.*` są **dowodem blokady, nie odbiorem aplikacji**. Skrypt UI zatrzymał się przed otwarciem Opcji i przywrócił font 1.0 w `finally`. Odbiór UI 100%/150% finalnego APK: **NOT_VERIFIED_LOCKED_DEVICE**. Historyczny odbiór A/B v1 pozostaje historyczny. Operator otrzymał prośbę o odblokowanie; brak odpowiedzi nie stanowi potwierdzenia.

Nie potwierdzono przygotowania bezpiecznego krótkiego materiału pojazdów. Fizyczna próba dynamicznej ciągłości i rzeczywistej inferencji pozostaje **NOT_VERIFIED**. Nie uruchamiano analizy, FINAL176 ani sesji badawczej. Testy tensorowe nie dowodzą rzeczywistej pracy CPU/NN ani odbioru terenowego. `device/acceptance.json` jawnie zachowuje status **PARTIAL**, instalację/dane PASS i obie brakujące części manualne.

## Ochrona zastanej pracy

HEAD wejściowy: `f7943a7d7334afd1e4188ef9860b2126331926c8`. Początkowy status miał **181 wpisów**. `audit/baseline.json` obejmuje 1156 plików wejściowych i 3684 chronione artefakty. Prywatny `baseline-sources.zip` ma około 9,4 MB; nie kopiowano całego `app/build`. Zachowano `git status --short`, pełny diff, SHA i mtime źródeł/artefaktów.

Końcowe `audit/source-integrity.json`, status/diff, `changed-files.json` i byte-identical `patch-roundtrip.json` są podstawą zakresu dostawy. Zakres obejmuje **17 plików**; końcowy git status ma **193 wpisy**, bez etapowania/commitu. Wzrost względem 181 wynika również z 7 wcześniej czystych istniejących plików, a nie tylko z 5 dodanych plików. Kontrola wykazała **0 zmian poza zakresem**, **0 naruszeń SHA/mtime spośród 3684 chronionych plików**, identyczność 16 oddzielnych plików A i wszystkich pól siedmiu obsługiwanych konfiguracji badań względem B v1. Sześć snapshotów gotowości A/B v1/v2 ma identyczne 5610 bajtów i SHA `d04a8f169696e5b02e77f0179e2434693fca78eab5e86d8fc2dc1e5a1c6959db`.

Patch B v2 stosuje się do tego prywatnego brudnego S0, **nie** do samego HEAD i nie bezpośrednio zamiast A/B v1. Brak automatycznego commitu/pushu. Nie wykonano reset/clean/stash ani checkout zastępującego zmiany. Zastany `tools/__pycache__` jest cudzym stanem i nie jest czyszczony.

Nie zmieniono plików modeli, GT, konfiguracji eksperymentów, serializerów, eksportów, .alprsession, statycznej granicy fotografii, mechanizmu badawczego START/STOP, układu fullscreen Main ani CPU/gotowości części A. Zachowano edytory TV, kalibrację, AutoZoom, blokowanie celu i historię; blokowanie otrzymało wyłącznie opisaną obsługę kandydata i kanonizację runtime, bez nowego wyboru operatora. Nie wprowadzono migracji repo ani zmian Desktop. Wspólne pliki Main/Pipeline/Engine mają tylko opisane bramki zwykłej dynamicznej własności.
