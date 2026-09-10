# Architektura klienta mobilnego ALPR

Aktualizacja kontraktów z 10.09.2026: [interoperacyjność Android–Desktop](android_full_interop_2026-09-10.md).
Nowe zapisy galerii i paczek grupują po kanonicznym `registration_key`, zachowując
surowy tekst każdej obserwacji. Starsze opisy ścisłego klucza raw są historyczne.

Układ nakładki telemetrycznej i wskaźnika orientacji:
[lekki HUD kamery](compact_live_hud.md).

## Wybór wariantu wykonawczego MP/MT/MZ

Każdy węzeł kompozycji udostępnia osobno wybór modelu, wybór wariantu
wykonawczego i import modelu. Wariant pochodzi z `ModelManifest.variants`:
runtime + precision + pliki + opcjonalne nadpisania kontraktu wejścia/wyjścia.
CPU ×1/×2/×4 lub GPU jest odrębnym profilem wykonania. Niedostępny runtime
pozostaje widoczny z przyczyną, ale nie można go wybrać.

Kolejność rozstrzygania: zamrożony `ResearchStageExecutionConfig`, ręczny pin
`AutoTuneManager`, zwycięzca AutoTune, deterministyczny fallback.
Pin jest zapisany istniejącym kluczem rola + fingerprint; zmiana fingerprintu
nie przenosi ustawienia. AUTO usuwa tylko pin danego modelu. AutoTune mierzy
wszystkie dostępne warianty i profile, również INT8 przy obecnym FP32, a wybiera
najniższą medianę udanego pomiaru. Błędne pomiary nie wygrywają. Sam pomiar
nie nadpisuje pinu. Bez poprawnego profilu/pinu fallback preferuje kolejno
TFLite FP32, ONNX FP32, inne FP32, TFLite, pozostałe dostępne warianty.

Zastosowanie wariantu zwiększa rewizję ustawień. Po powrocie
`MainActivity.applySettingsRevision()` odświeża rejestr i wywołuje
`AlprPipeline.invalidateModels()`. Przed kolejną inferencją `ensureEngineLoaded()`
zamyka poprzedni silnik i otwiera nowy bez restartowania aplikacji.
Badanie korzysta z `requireVariant()` i zamrożonego profilu przez całą sesję.
Log `ALPR_ENGINE_MODEL` powstaje z tego samego obiektu wariantu przekazanego
do fabryki backendu: zawiera rolę, model, fingerprint, wariant, runtime,
precision, rozmiar i typ wejścia oraz pliki.

Schemat i importer nie zmieniają się. W szczególności ONNX INT8 QDQ nadal
zachowuje publiczne wejście FLOAT32. Wyniki walidacji wdrożenia:
`docs/handoffs/implementation-report-model-variants-v1.md`.

## Mechanizm priorytetowego śledzenia encji i bramka rozmiaru pojazdu w trybie dynamicznym

W trybie DYNAMIC pojazd może zostać wykryty i objęty śledzeniem wcześniej,
zmieniać rozmiar w kolejnych klatkach i pozostawać tą samą `VehicleEntity`
mimo chwilowego braku kwalifikacji do MT. Priorytet aktywnego celu określa
pierwszeństwo jego śledzenia i odzyskiwania, natomiast bramka rozmiaru określa
możliwość rozpoczęcia analizy tablicy.

**Status na 2026-09-09:** roboczy kod zawiera priorytet śledzenia aktywnego celu
oraz `DynamicVehicleSizeGate`, podłączoną do kolejki Scan i wywołań MT.
Progi startowe to 120×80 px, a progi podtrzymania wynoszą 100×64 px.
Oba wymiary muszą spełniać próg. Wartości znajdują się w
`DynamicVehicleSizeGate.Config.INITIAL` i wymagają kalibracji na próbkach.
Są konfigurowalne w Opcje → DYNAMIC: próg pojazdu i obszar MT. Rozszerzenie
ROI i galerii opisuje [raport wdrożenia](dynamic_mt_gallery_implementation.md).

Zakres mechanizmu jest następujący:

```text
SceneHandlingMode = DYNAMIC (w kodzie: DYNAMIC_CONTINUITY)
+ śledzona VehicleEntity
+ bramka rozmiaru przed MT

śledzenie encji ≠ zgoda na uruchomienie MT
```

Mały pojazd nadal jest śledzony, ale MT dla tej encji nie jest uruchamiany,
dopóki aktualna, świeżo zmierzona przez MP geometria nie spełni minimalnych
wymiarów. Sama predykcja trackera lub powiększenie wycinka do rozdzielczości
wejściowej modelu nie zastępuje świeżego pomiaru. Niespełnienie progu rozmiaru
nie usuwa encji ani nie zmienia jej `entityId`; utrata tożsamości nadal wynika
z odrębnych zasad ciągłości śledzenia.

Kwalifikacja rozmiarowa przed skierowaniem encji do MT oraz druga kontrola
bezpośrednio przed wywołaniem MT korzystają z aktualnej, świeżo zmierzonej
ramki pojazdu. Oczekiwanie z powodu zbyt małego pojazdu jest odnotowane
w telemetrii jako `VEHICLE_TOO_SMALL`. Histereza rozdziela progi wejścia
i podtrzymania. Bramka nie zmienia hierarchii encji ani polityki AutoZoom.

Implementacja wykorzystuje surowe ramki MP zachowane w
`VehicleTrackingCoordinator.rawMpBounds()` przed predykcją Kalmana, razem
z identyfikatorem i czasem klatki źródłowej. Kolejka wybiera spośród encji
kwalifikujących się według ostatniego rzeczywistego pomiaru MP. Przed MT
silnik wymaga pomiaru dla dokładnie tej klatki źródłowej, jej rozdzielczości,
sceny, epoki wizualnej i generacji transformacji kamery. Jeśli go brakuje,
odświeża MP. Opóźnienie obliczeń nie zmienia rozmiarów tej samej klatki,
natomiast wynik z poprzedniej klatki nie zastępuje nowego pomiaru.

Pierwsza kontrola odrzuca zbyt małe encje przed wyborem z kolejki i przed
przydzieleniem budżetu ROI. Druga kontrola znajduje się bezpośrednio przed
`plateBackend.run()`, dodatkowo poprzedzona kontrolą przed przygotowaniem
tensora. Oczekiwanie aktywnej encji z potwierdzoną zbyt małą ramką zawiesza
jej budżety czasu akwizycji. Rzeczywisty brak pojazdu w kolejnym MP przywraca
normalne zasady utraty celu i timeoutów. Nie rejestruje się nieudanej detekcji
MT, jeśli model nie został wywołany.

Bramka działa w DYNAMIC z aktywną kaskadą MP (R1/R2). R0 bez encji pojazdu
pozostaje poza jej zakresem. MT ukierunkowane na znaną encję, także przez ROI
celu lub pełnoklatkową próbę odzyskania, podlega kontroli tej encji. Gdy
wszystkie świeżo zmierzone pojazdy są za małe, pusty wybór ROI nie uruchamia
pełnoklatkowego fallbacku omijającego bramkę. Nie zmieniono zasad wyzwalania
ani budżetów AutoZoom; powiększenie wymaga nowego MP do kwalifikacji MT.

Telemetria zapisuje `VEHICLE_TOO_SMALL`, identyfikator encji, zmierzone
wymiary, wymagane progi i etap kontroli. Brak właściwego pomiaru ma osobną
przyczynę `VEHICLE_MEASUREMENT_REQUIRED`. Trace zawiera również konfigurację
progów i faktyczną liczbę wywołań MT. Weryfikacja na telefonie i kalibracja
progów pozostają osobnym etapem od testów jednostkowych. Wymóg pomiaru z tej
samej klatki może zwiększyć częstotliwość MP przed MT; wpływ na opóźnienie
wymaga pomiaru na urządzeniu.

Walidacja implementacji: 400/400 testów JVM dla akwizycji, trackingu, pipeline'u
i ciągłości, w tym 13 nowych przypadków; `assembleDebug` i
`assembleDebugAndroidTest` zakończone powodzeniem. Testów instrumentacyjnych
nie uruchamiano na telefonie.

Mechanizm ten dotyczy trybu dynamicznego, w którym geometria pojazdu zmienia
się w czasie. Tryb statyczny wykorzystuje odrębną politykę analizy sceny.
Oczekiwanie na spełnienie progu nie przełącza DYNAMIC w STATIC i nie rozszerza
opisanych zasad oczekiwania na wzrost ramki na tryb STATIC.

W trybie dynamicznym pojazd może być śledzony od chwili jego wykrycia,
natomiast model MT zostaje uruchomiony dopiero po osiągnięciu przez aktualną,
świeżo zmierzoną ramkę pojazdu minimalnych wymiarów wymaganych do analizy tablicy.

Źródło doprecyzowania: [handoff zakresu DYNAMIC](handoffs/dynamic-vehicle-size-gate-scope-v1.md).

Śledzenie i prezentację ramek podczas ruchu kamery opisuje również
[poprawka ciągłości overlayu DYNAMIC](dynamic_vehicle_overlay_motion_fix.md).
Zasady zachowania identyfikatora i sceny podczas zmiany kadru opisuje
[poprawka ciągłości encji DYNAMIC](dynamic_scene_identity_continuity_fix.md).

## Tryb statyczny: granica zdjęcia i autozoom tablic

Detektor sceny pracuje na lekkich klatkach luma także podczas `STATIC_IDLE`
i blokady prezentacji. Znaczna zmiana (≥65% pikseli regionu obserwowanego lub
całego obrazu, po kompensacji ekspozycji) unieważnia scenę przy pierwszej
obserwacji. Mniejsza istotna zmiana wymaga dwóch obserwacji i co najmniej 50 ms.
Rzeczywista animacja optyczna nadal zawiesza porównanie obrazu.

`requestStaticSceneReset()` odrzuca stare generacje zamiast blokować kolejne
zdjęcie czasowym cooldownem. UI czyści ramki i zwalnia barierę prezentacji;
nie czeka na bitmapę Preview ani zakończenie starej inferencji. Kolejna klatka
odświeża MP z użyciem już otwartych modeli. Regiony obserwacji są aktualizowane
już podczas baseline, a późny wynik nie może podmienić referencji luma nowym
zdjęciem. KLT nie jest źródłem geometrii ani decyzji o scenie w trybie statycznym.

STATIC dopuszcza tylko pojazdy zmierzone przez MP. Pusty nowy pomiar usuwa
ramki, oczekujące zadania i aktywny cel. Autozoom ma budżet jednej próby na
wykryty track tablicy w scenie, także dla mocnego odczytu, tablicy bez encji
i ramki wymagającej dopiero ustalenia narożników/MZ. Tryb dynamiczny zachowuje
dotychczasowe kryteria jakości i geometrii zoomu.

## Trwały magazyn automatycznej sesji badawczej

`ExperimentSession.prepare()` przydziela tożsamość i config bez RUNNING.
`ResearchSessionStore.prepare()` tworzy katalog, PREPARED i writer przed t0;
dopiero potem `startPrepared()` i `MetricsCollector.startMeasurementSession()`
uruchamiają pomiar na wspólnych znacznikach czasu. Odmowa przygotowania
magazynu blokuje START.

`ResearchAttemptBatch` obserwuje wywołania MT oraz rektyfikację/MZ w silniku,
bez zmieniania detekcji, kolejki, AZ i decyzji o tożsamości. Kopiuje obraz przed
zwolnieniem bitmapy, a jeden writer `ResearchSessionStore` kompresuje i zapisuje
dowody poza inferencją. `CaptureGalleryViewModel` pozostaje ograniczonym podglądem.
Kolektor nie podlega odrzucaniu wyników przez UI ani polityce najlepszego cropa.

STOP zamyka bramkę prób, a finalizator za zakończeniem pracy pipeline’u czeka
na writer i buduje istniejący `ResearchArchive` z dyskowego samples v2.
`ResearchSessionViewModel` utrzymuje zapis/timer/config przez odtworzenie Activity.
Proces przerwany pozostawia PARTIAL oraz odzyskiwalny zbiór dowodów. Eksport UI
kopiuje gotową paczkę całej sesji. Szczegóły: `docs/mobile_research_export.md`.

## Potok wykonawczy

```text
CameraX YUV 4:2:0
  -> adaptacyjna redukcja klatek
  -> [opcjonalnie] model pojazdu, maksymalnie 2 poszerzone i krótkotrwale używane ROI
  -> letterbox i tensor modelu tablic
  -> YOLO Pose + NMS + deduplikacja zagnieżdżonych ramek
  -> mapowanie czterech narożników do obrazu kamery
  -> walidacja czworokąta i geometryczny fit_score
  -> przypisanie tablicy do tracku
  -> adaptacyjna decyzja o uruchomieniu MZ
      -> homografia i normalizacja 256x64 / 256x128
      -> model znaków YOLO Character Detection
      -> deduplikacja znaków i filtr spójności sekwencji
      -> grupowanie wierszy i kolejność odczytu
      -> temporalny konsensus klas znaków
  -> natychmiastowy wynik wstępny
  -> wynik potwierdzony przez konsensus, overlay i InferenceTrace
```

Opcjonalna rola `vehicle` może zostać włączona w menu jako kaskada pojazdu.
Detektor odświeża maksymalnie dwa dominujące ROI co trzy analizowane klatki.
ROI są poszerzane o 18%, a MT analizuje wycinki źródłowej klatki. Brak tablicy
w ROI uruchamia natychmiastowy fallback pełnoklatkowy; kontrolny fallback jest
wykonywany również okresowo. Brak aktywnego modelu pojazdów nigdy nie blokuje
MT — pipeline wraca wtedy do pełnej klatki.

Żyroskop wykrywa szybki obrót telefonu. W takim stanie aplikacja nie używa
buforowanego ROI przez kolejne klatki: MP jest odświeżany natychmiast, a
margines ROI rośnie z 18% do 28%. Nie jest to pełna kompensacja globalnego
ruchu ani estymacja homografii sceny, lecz zabezpieczenie przed analizą
nieaktualnego wycinka.

Tracker wykonawczy znajduje się w pipeline, przed MZ. Tracker nakładki UI jest
od niego niezależny i służy wyłącznie płynnemu rysowaniu. MZ nie jest klasycznym
OCR-em: wykrywa osobne znaki jako klasy YOLO, a tekst powstaje z uporządkowanych
detekcji.

Pierwszy kompletny odczyt MZ jest przekazywany do UI jako wynik wstępny.
Pipeline, a nie UI, jest jedynym właścicielem konsensusu czasowego. Po uzyskaniu
zgodnych obserwacji wynik otrzymuje stan potwierdzony.

Profile `Szybki`, `Zrównoważony` i `Dokładny` określają początkowy budżet prób
MZ, minimalny `fit_score`, wymaganą poprawę jakości oraz odstęp późniejszych
prób okresowych. Po uzyskaniu dwóch zgodnych obserwacji każdej pozycji znaku
track przestaje uruchamiać MZ.

## Prezentacja wyników

Dolny panel rozdziela teraz `Podgląd` od `Uruchom analizę`. Podgląd wiąże
CameraX z tym samym formatem i proporcjami kadru, które zostaną użyte przez
nadchodzący przebieg (w tym wyższym celem AUTO dla Scan), ale zamyka klatki bez
uruchamiania MP/MT/MZ, trackingu, Scan ani sesji metryk. HUD pokazuje spokojny
stan `Podgląd kadru`, a piktogram oka zmienia się na przekreślone oko. Start
analizy przejmuje kamerę z podglądu i dopiero wtedy rozpoczyna pomiar.

Inferencja i bieżący overlay działają niezależnie od kolektora. Interfejs
wyników rozdziela dwa przepływy. W trybie normalnym `Ostatnie odczyty` działa
bez aktywnej kolekcji cropów. Jeden wpis odpowiada logicznemu pojazdowi lub
tablicy, identyfikowanemu kolejno przez `(sceneGeneration, entityId)`,
`plateTrackId` albo `trackId`. Nowszy potwierdzony wynik aktualizuje wpis, a
miniatura jest zastępowana tylko przez próbkę o wyższym confidence, następnie
ostrości, a przy remisie nowszym czasie. Historia utrzymuje maksymalnie 40
wpisów i recykluje wyparte bitmapy. Szczegóły pozwalają skopiować numer,
zapisać obraz lub usunąć wpis.

W trybie badawczym `Weryfikacja próbek` prezentuje jeden techniczny crop naraz.
Próbki można filtrować według stanu, przełączać poprzednia/następna i oznaczać
jako `Zgodne`, `Koryguj odczyt` albo `Nie do oceny`. Predykcyjne boxy MZ są
read-only i można je jedynie ukryć. Niezależny zestaw kodów problemów oraz flaga
`Do analizy w Desktop` trafiają do mini-raportu i `.alprsession`. Zapis bieżącej
próbki oraz zbiorczy zapis wszystkich zweryfikowanych nadal korzystają z
dotychczasowego `CaptureDirectoryStore`.

`MobileAlprEngine` przekazuje `PlateObservation`: identyfikatory encji i
tracków, generację sceny, datę, bitmapę rektyfikacji utworzoną podczas MZ,
pozycje znaków oraz osobne czasy konkretnego cropu. `MainActivity` zawsze
zamyka `PipelineResult`, również dla wyniku pominiętego przez limiter UI, co
zwalnia bitmapę należącą do pipeline'u.

Warstwa graficzna rozdziela semantycznie treść detekcji: nazwa/ciąg znaków ma
kolor niebieski, confidence zielony, a dane pomocnicze są przygaszone. Overlay
na żywo najpierw wylicza wszystkie ramki, a następnie szuka dla badge'a miejsca
nad, pod albo obok detekcji. Kandydat kolidujący z dowolną ramką lub innym
badge'em jest odrzucany; jeżeli nie istnieje bezpieczne miejsce, badge nie jest
rysowany. Brak etykiety jest preferowany względem zasłonięcia obrazu.

W weryfikacji badge'e znaków nie są nanoszone na bitmapę. `PlateCropView` rezerwuje
pod obrazem osobny pas legendy: znak jest niebieski, a jego confidence zielone.
Na obrazie pozostają tylko cienkie ramki znaków. Tracker używa ramki o małej
grubości i obniżonej nieprzezroczystości, mniejszych punktów narożnych, a
jednoklatkowa predykcja bez świeżej detekcji jest przerywana i pozbawiona
badge'a. Geometria jest przygotowywana po zmianie danych lub rozmiaru widoku;
`onDraw()` nie tworzy kolekcji ani obiektów ramek.

Zbiorczy zapis zweryfikowanych cropów działa sekwencyjnie przez jeden executor,
blokuje ponowne uruchomienie do zakończenia partii i pokazuje jedno podsumowanie
liczby sukcesów oraz błędów. Element z błędem może zostać ponowiony, a zapisany
element jest wyłączany z kolejnej partii.

Wygładzanie trackera rozróżnia mały jitter od wyraźnego przesunięcia. Dla
przemieszczeń do 0,012 rozmiaru znormalizowanego współczynnik korekcji wynosi
0,38, dla ruchu pośredniego 0,58, a dla ruchu powyżej 0,03 — 0,78. Składowe
prędkości są filtrowane współczynnikiem 0,30, zmiana rozmiaru 0,22, a prędkość
jest ograniczona do ±1,25 jednostki znormalizowanej na sekundę. Horyzont
predykcji ograniczono z 220 do 140 ms, co zmniejsza przestrzeliwanie ramki po
gwałtownym zatrzymaniu kamery.

## Nawigacja i ekrany pomocnicze

`MainActivity` jest przeznaczona wyłącznie do kamery, overlayu, sterowania
sesją i galerii. Toolbar udostępnia dwie ikonowe akcje prowadzące do osobnych
aktywności:

- `SettingsActivity` — profil rozpoznawania, rozdzielczość analizy, limit
  cropów, kaskada pojazdu, import modeli i katalog zapisu;
- `DiagnosticsActivity` — dwukolumnowy grid stanu urządzenia, pamięci,
  pipeline'u i sesji, a poniżej aktywne modele, runtime i trwały log.

Oba ekrany mają strzałkę nawigacji wywołującą `finish()` i obsługują systemowy
Back, dlatego powrót odsłania istniejącą instancję okna kamery. Opcje zapisują
wersjonowane `SharedPreferences`; po powrocie `MainActivity` stosuje wyłącznie
zmienioną konfigurację, przeładowuje modele i w razie potrzeby restartuje
analizę kamery. Eksport z Diagnostyki wraca wynikiem aktywności i wykorzystuje
metryki bieżącej sesji należące do `MainActivity`.

Ikony interfejsu są zasobami `VectorDrawable` opartymi na ścieżkach SVG.
Paleta rozdziela semantycznie obszary: niebieski oznacza stan podstawowy,
fiolet ustawienia obrazu, zieleń gotowość pipeline'u, a róż operacje sesji i
zapisu.

Limit może wynosić 10, 25, 50 lub 100 albo działać automatycznie. W trybie
automatycznym aplikacja przeznacza początkowo około 3% maksymalnego heapu,
przyjmując 160 KiB na element, z zakresem 10–100 i maksimum 25 dla urządzenia
`lowRamDevice`. Po przekroczeniu limitu usuwany jest najstarszy element, który
nie jest właśnie zapisywany. Sesja i każdy crop mają osobne identyfikatory.

Checkbox karty jedynie zaznacza crop. Zbiorcze CTA zapisuje JPEG i sąsiedni
miniraport JSON przez MediaStore do stałego katalogu
`Download/Mobilny ALPR - cropy/`, tworzonego automatycznie przez aplikację.
Raport ma schemat
`alpr.mobile.crop_report.v1` i zawiera czas UTC, strefę czasową, tekst,
confidence, sharpness, znaki, profile, modele i czasy etapów. Nieudana próba
usuwa oba pliki utworzone przez tę próbę. Aplikacja nie wymaga ręcznego wyboru
katalogu ani szerokiego dostępu do pamięci urządzenia.

Import modelu znajduje się w Opcjach, a wybór eksportu w Diagnostyce. Dolny
panel jest przeznaczony na sesję, zaznaczanie i wyniki.

Okno aplikacji ustawia `FLAG_KEEP_SCREEN_ON`, dlatego ekran nie wygasza się,
gdy aktywność jest widoczna; system zwalnia tę ochronę po przejściu aplikacji
do tła.

## Kamera i rozdzielczość

Menu udostępnia profile `Automatyczna`, `Szybka 640×480` i `Daleki odczyt
1920×1080`. Profil automatyczny zachowuje bazowe `1280×720`, a na urządzeniu z
małą ilością RAM wybiera `640×480`. Zmiana profilu ponownie wiąże strumień
CameraX i resetuje tracki; nie zachodzi co klatkę.

`ImageAnalysis` używa kamerowego formatu `YUV_420_888` i strategii
`STRATEGY_KEEP_ONLY_LATEST`. Konwersję do bitmapy wykonuje natywna ścieżka
CameraX 1.4 (`ImageProxy.toBitmap()`), po czym stosowany jest obrót z metadanych
kamery. Usunięto ręczne kopiowanie każdego piksela RGBA w kodzie Java. Nadal
powstaje pełna bitmapa przed pierwszym modelem; bezpośrednie tworzenie tensora
z płaszczyzn YUV pozostaje możliwą dalszą optymalizacją wymagającą osobnego
benchmarku zgodności kolorów.

## Runtime'y

- LiteRT/TFLite: CPU 1/2/4 wątki oraz delegat GPU, jeżeli urządzenie i model go obsługują.
- ONNX Runtime Android: CPU 1/2/4 wątki.
- NCNN: aktywny backend JNI (`NcnnBackend` + `alpr_ncnn`) dla ABI ARM; import, otwarcie modelu i inferencja są objęte testem instrumentacyjnym.

W trybie użytkowym autotuning jest wykonywany osobno po imporcie każdego modelu.
Wynik jest powiązany z SHA-256 manifestu, więc zmiana pakietu wymusza nowy
pomiar. W trybie badawczym `ResearchExecutionConfig` zamraża przy START osobno
dla MP/MT/MZ: model i fingerprint, wariant, runtime, precyzję, CPU 1/2/4 albo
GPU oraz wejście modelu. `MobileAlprEngine` korzysta wtedy wyłącznie z tego
snapshotu i nie pyta `AutoTuneManager` o nowy profil podczas sesji. Regulator
klatek nadal może chronić urządzenie, ale nie zmienia liczby wątków ani delegata.

## Raportowanie

Eksport sesji tworzy ZIP zawierający:

- `report.json` — urządzenie, aktywne modele, profile autotuningu, statusy, percentyle p50/p90/p95/p99 i pełne ślady;
- `traces.csv` — jeden wiersz na klatkę z czasami etapów, wartościami confidence,
  `plate_fit`, `plate_sharpness`, udział powierzchni ROI i licznikami wykonania schedulera;
- `README.txt` — opis zawartości.

Czas mierzony jest monotonicznym zegarem Androida. Confidence tablicy i znaków
są raportowane oddzielnie; aplikacja nie przedstawia confidence jako
dokładności. Raport zapisuje również profil rozpoznawania oraz liczniki
`mz_runs`, `mz_skipped`, `invalid_plate_geometry`, `vehicle_runs`,
`plate_roi_runs` i `full_frame_fallbacks`. Sekcja `recognition_latency` zawiera
czas od uruchomienia sesji do pierwszego wyniku wstępnego i potwierdzonego.
Sekcja `capture` zapisuje profil, rozdzielczość żądaną i faktyczną, format YUV
oraz dostępność żyroskopu. CSV zawiera rozmiar źródła i licznik klatek szybkiego
ruchu.
Sekcja `crop_session` zawiera identyfikator, stan Start/Stop, limit i rekordy
wszystkich zebranych cropów wraz z datami, znakami i czasami per crop.

Sekcja `scan_acquisition` zawiera trwałe rekordy finalizacji Scan. Stabilny
wynik przechodzi przez deduplikację tekstu w obrębie jednego runu. Raport
rozróżnia wszystkie finalizacje, unikalne zapisy oraz stłumione duplikaty,
zachowuje `entity_id`, `session_id`, `plate_track_id`, tekst znormalizowany,
confidence, liczbę obserwacji i identyfikator najlepszego cropu źródłowego.

`report.json` używa schematu `alpr.mobile_benchmark_report.v1`. Pole `execution`
zawiera osobne rekordy `vehicle`, `plate` i `character`, w tym model, fingerprint, wybrany
wariant, runtime, precyzję, delegata, efektywne wejście i progi. Top-level
`variant_id` jest identyfikatorem kombinacji obu wykonań; może opisywać układ
mieszany, np. TFLite/GPU dla MT i ONNX/CPU dla MZ.

Raport sesji kamery dostarcza opóźnienia, pamięć, rozmiar pakietu i błędy
runtime. Nie wylicza CER ani exact match bez danych referencyjnych: sekcja
`quality` jawnie oznacza wtedy brak ground truth, aby desktop nie uznał samego
confidence lub częstości odczytu za jakość rozpoznawania.

Manualna walidacja `accepted/corrected` udostępnia ground truth i aktywuje
exact match, CER oraz znormalizowaną odległość edycyjną liczone per unikalny
track. Eksport badawczy jest strumieniowy i tworzy pełny `.alprsession` albo
samodzielny pakiet TeX. Szczegółowy kontrakt opisuje
`docs/mobile_research_export.md`, a manifest waliduje
`docs/alpr-mobile-research-bundle-v1.schema.json`.

Rozszerzenie `alpr.mobile_experiment_telemetry.v1` dodaje do `.alprsession`
szeregi `thermal.csv`, `frame_flow.csv` i `events.jsonl`, pełny timing-audit w
`traces.csv`, identyfikację kampanii oraz geometrię cropów. Kontrakt pól i
reguły kompatybilności opisuje `docs/mobile_experiment_telemetry_v1.md`.
