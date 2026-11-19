# Android — ciągłość pojazdów dynamicznych, B v1

Data: 16.11.2026. Katalog: `app/build/android_dynamic_vehicle_identity_20261116_v1/`. B jest patchem względem ukończonego A, a nie względem HEAD. Źródła/host: **DONE**, pełny zestaw **393/393 PASS, 0 FAIL, 0 SKIP** (`host-sealed-b/`), `assembleDebug` i kompilacja instrumentacji: PASS. Odbiór urządzeniowy całości: **PARTIAL**. Fizyczna ciągłość pojazdów: **NOT_VERIFIED**; symulacja nie jest odbiorem terenowym.

## Diagnoza przed zmianami

Audyt i uruchomione na oryginalnych źródłach sondy są w `audit/before/`: kod, SHA wejścia, log kompilacji i `reproduction.log`. Audyt wykonano przed zastosowaniem B. Nie wymieniono trackera ani algorytmu OCR.

Samo wygaśnięcie technicznego tracka nie powodowało utraty numeru. Oryginalny manager po utracie tracków 1/2 przypisał odwrócone detekcje do nowych tracków 3/4, zachowując encje 2/1 i oczekiwane etykiety P2/P1. Problem leżał dalej: świeży MP zawierający B, lecz bez wybranego A, dawał `ACTIVE_TARGET_LOST`. `SceneTransitionCoordinator` zwalniał tylko cel, z zachowaniem generacji sceny. Jednak `AlprPipeline.handleSoftReacquireReport` wykonywał `retireEntity(previousFocusEntity)`. Przy powrocie A manager nie miał już encji do odzyskania: track 5 otrzymywał nową encję 3 i HUD P3. To rzeczywiste usunięcie encji, nie pomylenie licznika tracków w rendererze.

Druga ujawniona luka: oryginalne dopasowanie dormant mogło odzyskać encję po samej geometrii przy niedostępnym wyglądzie. Nie można więc było bezwarunkowo pozostawić encji do odzyskania przez historyczne 600 sekund.

## Mapa identyfikatorów

| Nazwa w kodzie | Znaczenie / nadanie | Usunięcie lub granica | Użycie w UI |
|---|---|---|---|
| `sourceFrameId`, `sourceTimestampNanos`, `sourceIndex` | rzeczywista obserwacja MP i indeks detekcji tej klatki | indeks nie ma znaczenia w następnej klatce; znacznik źródła chroni wynik | powiązanie świeżej ramki z pomiarem, nigdy numer pojazdu |
| `VehicleTrackManager.Track.trackId`, `VehicleCandidate.vehicleTrackId` | krótki techniczny ślad; `nextTrackId++` przy nowym tracku | TTL / reset sceny; techniczny licznik wraca do 1 przy resecie | diagnostyka; nie numer P na HUD |
| `VehicleEntity.entityId`, `VehicleCandidate.entityId` | encja z istniejącego `VehicleEntityRepository.create`, `nextEntityId++` | jawne retire, pojemność, TTL encji lub reset domeny; licznik nie jest cofany przy resecie sceny | trwały numer P w danej domenie; reassignVehicleTrack zachowuje ID |
| `OverlayItem.trackId` dla `Kind.VEHICLE` | mimo nazwy zawiera entityId z mapowania MP | zastąpienie/wyczyszczenie warstwy pomiarów | `DetectionOverlayView`: `P` + entityId, opcjonalnie tekst OCR |
| `plateTrackId`, `OverlayItem.trackId` dla `Kind.PLATE` | techniczny ślad MT/MZ, wiązany z encją przez istniejącego bindera | polityka śledzenia tablic i generacje | etykieta tablicy/wybór celu; nie fizyczna tożsamość pojazdu |
| `sceneGeneration` | twarda granica istniejącego koordynatora sceny | następny twardy reset, osobna fotografia, zmiana strukturalna | odrzuca stare dowody i wywołania; bez łączenia encji przez reset |
| `visualEpoch`, `cameraTransformGeneration` | epoka odzyskiwania / transformacji, część `ContinuityStamp` | zmiana epoki lub transformacji | istniejące bramki aktualności; nie tworzą same numeru P |
| `TargetFocusSnapshot.entityId` | uwaga operatora na istniejącej encji | zwolnienie celu, STOP/granice kontrolera | wskazanie celu; zwolnienie uwagi nie musi usuwać encji |
| kanoniczny tekst w `RecognitionHistoryStore` | grupowanie odczytów galerii przy `upsertObservation` | zakres historii/sesji | jedna grupa tekstowa może zawierać obserwacje różnych entityId/scen; nie jest dowodem wspólnego pojazdu |

Nie dodano nowego licznika pojazdów. Numer P nadal jest ID istniejącej encji, nie globalnym licznikiem detekcji. `VehicleTrackingStats.tracksCreated` opisuje techniczne tracki, a `entitiesCreated` nowe encje; test sprawdza brak przyrostu tego drugiego przy potwierdzonym powrocie. Nie obiecuje to ponumerowania bez luk po rzeczywistych nowych scenach lub wygaśnięciu dowodów.

## Wąska zmiana zachowania

Tylko zwykły tryb dynamiczny (`frozenResearchExecutionConfig == null`, scena dynamiczna; wykonawca `TRACKED_MP`) zachowuje encję po terminalnym zwolnieniu celu. Badania i tryb statyczny zachowują stare gałęzie. Dane historyczne i metryki badań nie są przeliczane.

Istniejący manager otrzymuje przejściową politykę odzyskiwania, domyślnie wyłączoną. Okno potwierdzonego ostatniego MP wynosi `min(entityTTL, trackTTL_z_pomiaru + reacquireTimeout_z_pomiaru)`. Są to istniejące jednostki czasu i wartości wyznaczane przez program; brak nowego stałego „5 s”. Późniejsza predykcja, wybór celu ani wzrost adaptacyjnego TTL nie odnawiają tego okna. Rzeczywisty kolejny pomiar odnawia dowód.

Istniejące wartości: bazowy trackTTL 1,8 s; koordynator dostosowuje go do przerw MP (`ceil(1,75 × odstęp)`, minimum 3 s, maksimum entityTTL minus 0,1 s). Historyczne entityTTL 600 s służy zachowaniu encji, nie jest samodzielnym dowodem powrotu. Koordynator odzyskiwania używa 1 s na początku i `min(15 s, 1 s + 2 × najdłuższe przetwarzanie)`. B przechwytuje bieżące wartości przy pomiarze, nie zmienia tych algorytmów.

Po rzeczywistej utracie MP wymagany jest dostępny opis wyglądu i istniejąca zgodność co najmniej 0,55, geometria/motion/rozmiar z istniejącego score oraz margines niejednoznaczności 0,035. Aktywne skojarzenie zachowuje próg 0,36, dormant wymaga wyniku ponad 0,50; istniejące odzyskiwanie wspomagane wyglądem ma próg 0,46 i appearance 0,72. Nie zmieniono tych score ani progów. Dwustronny wybór dormant jest obliczany przed przypisaniami i wymaga jednoznaczności 1:1; nie przejmuje obserwacji pasującej do aktywnego sąsiada. Brak wyglądu, konflikt, nierozstrzygnięte dopasowanie lub wygasłe okno dają nową encję. OCR nie bierze udziału w tej decyzji.

Niezakłócony mierzony track zachowuje istniejącą politykę asocjacji, w tym adaptację do wolnego MP. Zmieniono warunki odzyskiwania utraconej tożsamości, nie bazowy score ciągłej obserwacji.

Zwykły dynamiczny overlay MP pobiera rzeczywiste `rawMpBounds` po istniejącym kluczu klatki/czasu i entityId. Nie przedstawia prognozy jako nowego MP. Wewnętrzna predykcja nadal służy śledzeniu. Main w normalnej gałęzi odrzuca przenoszone predykcje przed składaniem warstwy zamiast przedstawiać je jako świeże pomiary. Audyt dalszego składania podglądu wykazał również fallback ruchu kamery/KLT, który potrafił przywrócić ramkę pojazdu mimo świeżego miss MP. Normalna gałąź po projekcji odrzuca takie pojazdy według `missedUpdates/sourceIndex` ostatniej klatki MP; potwierdzony ruch pomiędzy MP nadal korzysta z istniejącej projekcji. Test reprodukuje stary fallback i sprawdza usunięcie ramki oraz zachowanie encji do późniejszego powrotu. Końcowe publikacje odzyskiwania MP/MT w tej samej normalnej gałęzi przechodzą dodatkowo przez istniejący `SceneMutationGate` ze znacznikiem źródła; stare generacje/epoki nie zwalniają nowego celu.

## Dowody i ograniczenia

Nowe testy sprawdzają rzeczywisty tekst renderera `DetectionOverlayView`, nie tylko ID w repozytorium. Artefakty `ui/6/` zawierają PNG i CSV `czas → indeks → track → entity → HUD → OCR`; zbiorczy podgląd jest w `demonstration/index.html`. Dwie sceny wielopojazdowe: zasłonięcie/powrót z odwróconą kolejnością oraz przecięcie torów z identycznym tekstem OCR. Wariant „before” odtwarza dokładnie audytowaną akcję retire i używa zachowanego starego mapowania HUD; nie jest nagraniem z drogi. Fixture ma trackTTL=1 s, grace=1 s i retencję encji=5 s wyłącznie jako jawne parametry testu granic, bez zmiany wartości produkcyjnych. Przejścia po twardym resecie i biblioteki inferencji mają odrębne testy i statusy.

Testy obejmują brak MT/MZ, brak/conflict wyglądu, aktywnego sąsiada, niejednoznaczność 1:1, granicę okna (równość i +1 ns), wygląd po obu stronach 0,55, ruch kamery bez odnowienia MP, zwolnienie celu w faktycznym pipeline, stare generacje, osobne fotografie i nowy manager po nowej sesji. Testy historii dowodzą oddzielnych właścicieli i generacji w grupie tego samego tekstu, bez zmiany produkcyjnej historii.

Opis wyglądu pozostaje istniejącym deskryptorem programu, nie pełnym ReID. Wiarygodność kojarzenia podobnie wyglądających pojazdów przy realnej zmianie perspektywy wymaga próby na materiale kamery; nie wynika z syntetycznych kolorów fixture. Nie dobierano nowych progów na podstawie takiej symulacji.

Odtworzenie: `python -B tools/verify_android_scoped.py b --run <nowa-nazwa>`. Jeden proces Gradle naraz. Zestaw zawiera wszystkie 210 testów A i wcześniejsze selektory Options, gotowości, Main, START/STOP, protokołów, a dodatkowo testy śledzenia, scen statycznych i historii. Nie zmieniono wcześniejszych asercji.

Końcowy przebieg na hoście 8 GB używał `audit/run_bounded.py`: 1 GB dla Gradle i 1 GB dla jednego testowego JVM, nowy JVM na klasę, jeden worker. Nie zmieniono selektorów ani asercji. Wcześniejszy `host-final-b/` został przerwany z powodu intensywnego stronicowania przy około 167 MB wolnego RAM; jego 383 wyniki z jednym pominięciem i niezerowym kodem wyjścia nie są końcowym PASS. Zachowano log, `ABORTED.json` i identyfikator zatrzymanego własnego workera. Po jego zakończeniu zwolniono wyłącznie potwierdzony bezczynny daemon używany przez ten przebieg; nie zamykano Android Studio ani innych aplikacji operatora.

Próby `focused-b/` i `verified-b-focused/` zawierają błędy kompilacji nowych testów (pole `entityId` i dostęp do klasy pakietowej), poprawione w fixture. `corrected-b/` ujawnił błędne użycie fabryki stempla w teście historii: argument był czasem, a nie generacją. Test dostał rzeczywisty `ContinuityStamp(scene, ...)`; asercja odrębnych generacji została zachowana. Celowane przebiegi po poprawkach: `actual-hud-b/` 44/44, `preview-miss-b/` 18/18, `bounded-ui-b/` 13/13 PASS. Końcowy pełny przebieg obejmuje ich ostateczne źródła.

Dodatkowa macierz: `research-contract-matrix-b/`, **1/1 test JUnit PASS, 0 SKIP**, dziewięć sprawdzonych kombinacji. Faktyczny producent `MainActivity.buildResearchExecutionConfig` buduje pełną konfigurację ponownie po zmianie normalnych profili na CPU=1; badawcze pozostają CPU=2. **7/7 dozwolonych snapshotów jest identycznych we wszystkich polach**: F4 i F5A dla R0/R1/R2 oraz F6/R0. **F6/R1 i F6/R2 są N/A jako niedozwolone kombinacje**; test sprawdza identyczne odrzucenie przed/po: `F6_REQUIRES_R0_TV_BBOX_WITHOUT_MP`. Nie poluzowano tej bramki. JSON, SHA i przyczyny odrzuceń są w `research-contract-matrix-b/ui/1/`. Nie wykonywano START ani inferencji; F6 korzysta z jawnej syntetycznej geometrii fixture, nie z nowych danych badania.

Nieudane poprzednie próby macierzy zachowano: `research-matrix-b/` — fixture zapisał badawczy CPU do innego namespace preferencji niż używany przez testowy Main, więc istniejąca bramka poprawnie odrzuciła AUTO; `research-matrix-approved-b/` — test błędnie zakładał dopuszczalność F6/R1. Poprawiono wyłącznie niezależny fixture i oczekiwanie odrzucenia, bez zmian produkcyjnego kodu. Test oraz runner są w `audit/matrix-tests/` i `audit/run_matrix.py`; nie dopisują plików do `app/src` ani do narzędzi A. Pełny wykaz prób i kodów wyjścia: `validation-index.json`.

Snapshot kanonicznego protokołu z bazowego przebiegu 193, końcowego A i końcowego B także jest bajtowo identyczny: 5610 bajtów, SHA256 `d04a8f169696e5b02e77f0179e2434693fca78eab5e86d8fc2dc1e5a1c6959db`. `audit/protocol-comparison.json` wymienia wszystkie sześć wejść/wyjść bez wyłączania pól.

## Galaxy A12

SM-A125F, Android 12, 720×1600, gęstość 300 dpi. Przed aktualizacją zachowano preferencje i SHA metadanych 366 sesji. Próba UI B przy 100%/150% tekstu pokazała poprawne komunikaty, trzy oddzielne profile Auto i dostępne przyciski dialogu. Po próbie wrócono do 100%; nie zmieniano modeli, wariantów, kamery ani CPU. Surowe XML wszystkich pięciu plików preferencji i SHA 366 metadanych sesji pozostały identyczne po tej próbie.

`device/ui-apk.json` wiąże fizyczne zrzuty 100%/150% z pośrednim APK o tych samych końcowych źródłach aplikacji. Finalny APK z pełnego przebiegu został osobno zainstalowany i sprawdzony: SHA256 `ad8331fe934136b0c7213aab71272b1c6b554ece40a9980bcfa7fa240000d3fd`. Instalacja, zrzut kontrolny 100% oraz końcowe porównanie danych są w `device/acceptance.json` i `device/preservation.json`. Certyfikat jest zgodny z A i zastanym APK, więc aktualizacja odbyła się przez `install -r -t`, bez odinstalowania i kasowania danych. Po finalnej instalacji nadal: 366/366 identycznych SHA metadanych sesji i 5/5 identycznych surowych XML preferencji; wspólne `settings_revision=183`, normalne CPU Auto z rewizją 6, czcionka 100%, DPI bez zmian.

Nie potwierdzono przygotowania bezpiecznego materiału z pojazdami. Nie uruchamiano analizy kamery ani sesji badawczej. Fizyczne odzyskiwanie tożsamości podczas ruchu, praca inferencji wszystkich modeli i rzeczywista liczba workerów pozostają **NOT_VERIFIED**. Materiałem dowodowym B dla track/entity/HUD są jawnie syntetyczne fixture i rzeczywisty kod renderera.

## Ochrona A, badań i materiałów

HEAD pozostaje `f7943a7d7334afd1e4188ef9860b2126331926c8`, katalog nadal jest brudny. Bazą B jest 1151 zastanych plików po A. SHA/mtime chronią 2808 materiałów, w tym finalne pliki z manifestu A i 85 archiwów/tar zgodnych z historycznie opublikowanymi SHA. Snapshot zawiera źródła, status i diff roboczej bazy, nie cały `app/build`.

Nie zmieniono modeli/fingerprintów, GT, CER, IoU, scoringu badań, serializerów sesji/eksportu, F4/F5A/F6, konfiguracji ROI/kamery/AF/AutoZoom, badawczych wątków, flag zapisu ani granicy osobnych fotografii. `source-integrity.json` wskazuje dokładny zakres B. `patch-roundtrip.json` dowodzi bajtowego odtworzenia patcha na prywatnej kopii bazowej. Część A ma osobny, zachowany APK i patch; wspólne pliki Main/Pipeline/Engine zmieniają się tylko w opisanych gałęziach B.

Nie wykonywano FINAL176, commitu, pushu, resetu, clean ani stash. Testy hostowe, build i instalacja same nie stanowią GO dla wersji dyplomowej. Pełny odbiór dynamicznej ciągłości na fizycznym materiale musi mieć jawny oddzielny wynik.
