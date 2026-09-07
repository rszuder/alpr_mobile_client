# Wdrożenie STATIC / DYNAMIC, tożsamość, AZ i lock/search

Data: 2026-09-07. Gałąź: `feature/static-dynamic-scene-policy`.
Commit implementacji: `26e2efe3edebd75bba7fff2fc753de1eb7a284c2`.
Punkt odniesienia: `8a19730b405449be698426df311e4085e3963081`.
Specyfikacja: [handoff v3](static-dynamic-scene-identity-az-lock-v3.md).
Plan: [implementation-plan-v3.md](implementation-plan-v3.md).

## Polityki i tożsamość

`ScenePolicy` ma odrębne implementacje `StaticScenePolicy` i `DynamicContinuityPolicy`.
STATIC nie wywołuje evaluatorów ciągłości celu, puli i ruchu. DYNAMIC zachowuje ich ocenę oraz istniejące KEEP, hold, reacquire, release i hard reset.
`SceneTransitionCoordinator` nadal jest jedynym właścicielem generacji i decyzji continuity.

Zachowano enum i zapis ustawień:

| Pole historyczne | Semantyka | UI |
|---|---|---|
| `strict_scene_boundary` | `analysis_mode: static` | Statyczny |
| `dynamic_continuity` | `analysis_mode: dynamic` | Dynamiczny |

Parser obsługuje również aliasy `static` i `dynamic`. Zmiana polityki tworzy twardą granicę sceny. OCR nie przywraca tożsamości między scenami; `nextEntityId` pozostaje monotoniczny. Test z powtórzonym `WI1234A` potwierdza nową encję i pusty początkowy konsensus po granicy. Historia nadal rozróżnia scenę i encję, a nie sam tekst.

STATIC używa potwierdzonego sygnału z luma watchera. Utrata lokalnego trackera sama nie jest nową sceną ani cross-scene reacquire. Lokalne odświeżenie geometrii unieważnia stary `visualEpoch`, zachowując bieżącą scenę. W DYNAMIC potwierdzony reacquire zachowuje encję; brak ciągłości kończy scenę.

## Watcher i zakończenie sceny statycznej

`StaticSceneCycle` prowadzi `BASELINE → AZ_REFINEMENT → STATIC_IDLE`. Zwykła kolejka jest ograniczona istniejącymi budżetami MT/MZ/czasu. Nieudany kandydat kończy próbę baseline bez fałszywego zatwierdzenia odczytu. Wolny MP pozostaje użyteczny przez baseline w tej samej generacji; nie wygasa przed obsługą kolejnych pojazdów.

`StaticSceneWatchRegions` zawiera nieruchome prostokąty bez identyfikatorów i mechanizmu trackingu. Źródła: powiększone pojazdy, następnie tablice (także R0), następnie sam globalny fallback. Regiony są uzbrajane po baseline i zakończeniu AZ przy bazowym zoomie. Migawka pojazdów używana do prezentacji wyniku STATIC jest oddzielona od tych regionów i nie staje się nowym pomiarem MP.

Parametry `StaticSceneWatcher.DEFAULT`:

| Parametr | Wartość |
|---|---|
| Margines regionu | 12% jego szerokości/wysokości na każdą stronę |
| Lokalna zmiana | co najmniej 30% próbek dowolnego regionu |
| Globalny fallback | co najmniej 55% próbek kadru |
| Różnica luminancji | co najmniej 24 poziomy, po kompensacji średniej ekspozycji |
| Potwierdzenie | 3 kolejne obserwacje i co najmniej 150 ms |
| Próbkowanie | krok `max(1, width / 96)` |

Jednorazowy szum i jednolity skok ekspozycji nie tworzą sceny w testach. Referencja bazowa jest zachowana przez zoom; ustabilizowane powiększenie ma osobną referencję globalną. Klatki fizycznego przejścia optycznego są pomijane. Zmiana próbki podczas AZ nie jest maskowana przez ponowne uzbrojenie regionów po powrocie.

W STATIC_IDLE wyłączone są MP/MT/MZ/AZ, konwersja klatek kolorowych do inferencji oraz tracking podglądu. Zostaje lekka luminancja i infrastruktura kamery/UI. Nie obowiązuje okresowe odświeżanie MP co 10 s. Ramki i najlepsze odczyty pozostają widoczne; status to „Analiza zakończona · czekam na zmianę”.

## Twarda granica i opóźnione wyniki

Watcher nie czeka na monitor ciężkiego `processBitmap()`. Pod monitorem koordynatora zwiększa generację i zamyka stare zapisy. `SceneMutationGate` atomowo sprawdza stamp przed mutacjami MP, trackingu tablic, rejestracji i przypisania tablicy do encji. Istniejące bariery dispatch i prezentacji nadal sprawdzają wyniki końcowe, cropy i callbacki MT.

Reset obejmuje repozytorium i mapowania encji, trackery MP/MT, cel, krótką sesję i lock, kolejkę, odczyty i konsensusy, pamięć ROI, stable MP cache, fazę i budżet AZ oraz referencje watchera. Reset podczas pauzy AZ czyści cel i wznawia nowy baseline. Fizyczny reset technicznych trackerów następuje przed następną pracą engine; ich stara migawka jest od razu niedostępna dla prezentacji.

`DetectionOverlayView.hardResetForNewScene()` usuwa items/renderItems, badge, oczekujące odczyty i transfery, geometrię hold/prediction, aktywny marker i dotyk oraz anuluje fade/absorption/animację overlayu. Granica potwierdzonej sceny nie jest pomijana podczas kontrolowanej transformacji. Nie ma fade starej sceny. Overlay czeka pusty na aktualny stamp. Geometria samego okna kadru pozostaje niezależna od wykrytych obiektów.

`static_scene_boundary` raportuje generację przed/po, powód oraz lokalny i globalny udział zmienionych próbek. `static_scene_idle` oznacza zakończenie ciężkiej analizy.

## AZ

`AutoZoomController` odpowiada za optykę i techniczne stany, a prawo rozpoczęcia próby wynika z polityki nadrzędnej. Brak poprawnego quad lub wcześniejszej normalnej próby MZ wyklucza AZ.

STATIC najpierw kończy zwykłą kolejkę. Kolejka poprawy dopuszcza słaby, niepotwierdzony, nieudany lub mały odczyt. Budżet to maksymalnie jeden cykl na `(sceneGeneration, entityId)`, zużywany także przy nieudanej próbie. Żądanie 1,8× prowadzi przez AF/AE do świeżego MT i MZ, bez MP, a następnie powrót do 1×. `cameraTransformGeneration` zmienia się, `sceneGeneration` pozostaje bez zmian. `ZoomTargetGeometry` przelicza położenie; nowa rewizja locka po optyce nie traktuje skoku zoomu jako dryfu pojazdu.

DYNAMIC SCAN nie uruchamia automatycznego AZ. `DynamicZoomPolicy` wymaga USER_PICK / SEARCH_VERIFICATION / SEARCH_PURSUIT, stabilnej geometrii, normalnego MZ, potrzeby poprawy, stanu STABLE i braku ruchu. Środek celu musi leżeć między 0,15 a 0,85 w obu osiach. Budżet jest przypisany do sesji/rewizji locka, po jednym cyklu; promocja search zachowuje wykorzystany budżet. Dispatch innych kandydatów jest wstrzymany bez kasowania puli. Po powrocie wymagany jest świeży MP/anchor, gdy kaskada go używa. Motion/loss/break oraz zmiana celu przez użytkownika powodują powrót i oddanie sterowania continuity.

## Lock i wyszukiwanie

Wykorzystywany jest istniejący `ModeController`, `ApplicationMode` i `TargetPurpose`; nie dodano drugiego kontrolera trybu aplikacji. Tap widocznej ramki wybiera domenowe `entityId`, nie `vehicleTrackId` i nie tekst. USER_PICK jest trwały także po słabszym lub sprzecznym OCR i krótkiej utracie geometrii. Odświeżenie MP wymagane przez lock ma pierwszeństwo nad pomijaniem inferencji przez tracker MT. Menu udostępnia „Zwolnij cel”; wybór innej encji przejmuje priorytet.

Lupa jest widoczna tylko w DYNAMIC. Normalizacja: wielkie litery A–Z i cyfry, usunięcie separatorów, minimum 4 znaki. Odczyt dokładny lub w odległości edycyjnej ≤1 i pewności ≥0,25 daje POSSIBLE_MATCH. Także wcześniej odczytana encja może zostać skierowana do weryfikacji. Kolejna niezależna klatka/sequence z dokładnym odczytem i pewnością ≥0,65 daje CONFIRMED_MATCH i `promoteSearchToPursuit()` dla tej samej encji. Sprzeczność albo dwie niepotwierdzające próby odrzucają kandydata. Pojedynczy możliwy odczyt nie tworzy trwałego pursuit. USER_PICK ma pierwszeństwo nad search; użytkownik może zmienić lub zakończyć zapytanie.

## Badania, eksport i zachowane elementy

`ResearchExecutionConfig` zamraża wybrany tryb przy START. UI blokuje jego zmianę w aktywnej sesji, a pipeline respektuje frozen config. R0/R1/R2 same nie wymuszają STATIC. Dla zgodności stare konstruktory konfiguracji zachowują historyczny domyślny tryb; rzeczywisty START przekazuje wybrany tryb jawnie.

JSON raportu, konfiguracja, protokół `.alprsession`, zdarzenia cropów i diagnostyka mają `analysis_mode`; pozostaje `scene_handling_mode`. Podsumowanie konfiguracji pokazuje tryb i jego zamrożenie.

Nie zmieniono kontraktów modeli, inferencji NCNN ani danych weryfikacji. Pozostają: osobna historia/weryfikacja, provisional promotion z visualEpoch, limit 40 i recykling bitmap, best confidence/sharpness/czas, read-only boxy MZ, issue codes, Desktop review, notatki, batch save i czasy cropów. Zachowano najsilniejszy odczyt badge, bezpieczne przypisanie rozszerzonego ROI, monotoniczne plateTrackId, przyjęcie wolnego MP, StationarySceneSupport i StableSceneVehicleCache.

## Wyniki testów

Build: `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest` — PASS.
Testy JVM: **519, 0 failures, 0 errors, 0 skipped**, 124 zestawy.
Android: **73 PASS** na Samsung SM-A125F, bez usuwania danych aplikacji.

Poniższa tabela oznacza pokrycie automatyczne; nie oznacza wykonania wszystkich scenariuszy jako terenowego nagrania E2E.

| ID | Wynik i dowód |
|---|---|
| S1, S3, S6 | PASS: StaticSceneWatcherTest, SceneTransitionCoordinatorTest, VehicleEntityRepositoryTest oraz istniejące testy konsensusu/PlateTrackCoordinator; nowa encja i pusty konsensus po resecie |
| S2, H1, H2 | PASS: RecognitionHistoryStoreInstrumentedTest — osobne identyczne teksty i provisional promotion |
| S4, S7, S8, S9 | PASS: StaticSceneWatcherTest — szum/ekspozycja, lokalny region, tło poza regionami, globalny fallback |
| S5 | PASS: StaticSceneWatcherTest, ZoomTargetGeometryTest, testy continuity transform; dodatkowo rzeczywisty AZ i powrót do tej samej sceny |
| S10, S12 | PASS: DetectionOverlayViewInstrumentedTest — wyczyszczenie geometrii/badge i brak końcowego callbacku starej animacji |
| S11 | PASS: SceneMutationGateTest, IntermediateMtCallbackInstrumentedTest, FinalPipelineResultDispatchInstrumentedTest |
| D1, D2, D3 | PASS: SceneTransitionCoordinatorTest, istniejące testy trackingu/repozytorium i TerminalRecoveryInstrumentedTest; reacquire zachowuje cel albo kończy scenę |
| M1 | PASS: modeChangeClearsRecoveryState i reset kontrolera; próba przełączenia na telefonie |
| C1 | PASS: StableSceneVehicleCacheTest.sceneEpochAndTransformChangesRejectOldVehicles |
| R1, R2 | PASS: ResearchExecutionConfigInstrumentedTest — obie polityki i wszystkie RoiBudgetPolicy, freeze i zgodność raportu |
| AZ1–AZ5 | PASS: StaticSceneCycleTest i test pauzy/resetu ScanAcquisitionControllerTest; AZ4 dodatkowo logi kamery bez inferencji po wejściu w idle |
| AZ6–AZ8 | PASS: DynamicZoomPolicyTest i budżet sesji ScanAcquisitionControllerTest; abort ruchowy sprawdzony automatycznie, bez terenowej próby z poruszającą się kamerą |
| L1, L2 | PASS: ScanAcquisitionControllerTest; dodatkowo lock P1 na telefonie przetrwał OCR i zmianę tracku 2 → 3 → 4 |
| L3–L6 | PASS: ScanAcquisitionControllerTest — normalizacja, niezależna weryfikacja, promocja i manual override; dialog oraz POSSIBLE_MATCH dodatkowo na telefonie |
| L7 | PASS: controller odrzuca STATIC; lupa znika po zmianie polityki na telefonie |

Wszystkie wskazane zestawy JVM z handoff uruchomiono, m.in. AcquisitionQueue, ScanAcquisitionController, PlateVehicleAssociator, PlateTrackCoordinator, StationarySceneSupport i StableSceneVehicleCache.
Zestaw Android: RecognitionHistoryStoreInstrumentedTest, GalleryPresentationInstrumentedTest, HumanVerificationJsonInstrumentedTest, RuntimeCompositionInstrumentedTest, FinalPipelineResultDispatchInstrumentedTest, DetectionOverlayViewInstrumentedTest, IntermediateMtCallbackInstrumentedTest, ResearchExecutionConfigInstrumentedTest, NcnnBackendInstrumentedTest, ScanAcquisitionInstrumentedTest, TerminalRecoveryInstrumentedTest.

## Dowody z kamery i ograniczenia

Artefakty lokalne znajdują się w ignorowanym przez Git `app/build/reports/gallery-qa/`:

- `handoff-v3-regression-final.log`: końcowy wynik Android.
- `handoff-static-az-success.log`: świeże MZ po AZ, poprawa pewności 0,283 → 0,846, powrót i STATIC_IDLE o 19:05:28; brak późniejszych ciężkich etapów w zebranym logu.
- `handoff-static-idle-retained.png`: bazowa zielona ramka, badge i status oczekiwania po AZ.
- `handoff-camera-final.log`: lokalna granica o 18:54:25, scene 1 → 2, local 0,479 / global 0,296 — lokalny watcher zadziałał poniżej globalnego progu; log wcześniejszej iteracji.
- `handoff-search-possible.png`: lupa i POSSIBLE_MATCH po wpisaniu `lz-576l`.
- `handoff-manual-lock-final.png`, `handoff-dynamic-lock-final.log`: P1 wybrany, świeże MP przy utracie geometrii, zachowane session=2/entity=1 mimo kolejnych tracków.

Obraz kamery przedstawiał pojazd na ekranie monitora. Nie wykonano kompletnej terenowej kampanii z wieloma jadącymi pojazdami ani fizycznego ruchowego abortu DYNAMIC AZ; te gałęzie mają testy automatyczne. Końcowa promocja search do CONFIRMED_MATCH jest potwierdzona deterministycznym testem, a nie odczytem z kamery o wymaganej pewności. Progi watchera są jawnym profilem początkowym i wymagają dalszej walidacji na innych ekspozycjach/próbkach. R0 nie wytwarza encji pojazdu z samego tekstu: watcher może używać regionów tablic, natomiast funkcje wymagające istniejącej encji pojazdu nie syntetyzują jej z OCR.

Odczyty z monitora nadal bywają błędne; wyższa pewność nie oznacza poprawnej ground truth. Raport nie deklaruje poprawy jakości samych modeli.

Przycisk AZ i stała geometria wycienienia kadru z wcześniejszej prośby są w oddzielnym commicie `1e8539e`. Nowa gałąź nie została wypchnięta do origin.

## Zmienione pliki handoff

- `app/src/androidTest/java/com/example/alpr_v1/experiment/ResearchExecutionConfigInstrumentedTest.java`
- `app/src/androidTest/java/com/example/alpr_v1/ui/DetectionOverlayViewInstrumentedTest.java`
- `app/src/main/java/com/example/alpr_v1/DiagnosticsActivity.java`
- `app/src/main/java/com/example/alpr_v1/MainActivity.java`
- `app/src/main/java/com/example/alpr_v1/SettingsActivity.java`
- `app/src/main/java/com/example/alpr_v1/acquisition/DynamicZoomPolicy.java`
- `app/src/main/java/com/example/alpr_v1/acquisition/RegistrationSearchPolicy.java`
- `app/src/main/java/com/example/alpr_v1/acquisition/ScanAcquisitionController.java`
- `app/src/main/java/com/example/alpr_v1/acquisition/StaticSceneCycle.java`
- `app/src/main/java/com/example/alpr_v1/camera/AutoZoomController.java`
- `app/src/main/java/com/example/alpr_v1/continuity/DynamicContinuityPolicy.java`
- `app/src/main/java/com/example/alpr_v1/continuity/SceneHandlingMode.java`
- `app/src/main/java/com/example/alpr_v1/continuity/ScenePolicy.java`
- `app/src/main/java/com/example/alpr_v1/continuity/SceneTransitionCoordinator.java`
- `app/src/main/java/com/example/alpr_v1/continuity/StaticScenePolicy.java`
- `app/src/main/java/com/example/alpr_v1/continuity/StaticSceneWatchRegions.java`
- `app/src/main/java/com/example/alpr_v1/continuity/StaticSceneWatcher.java`
- `app/src/main/java/com/example/alpr_v1/domain/ModeController.java`
- `app/src/main/java/com/example/alpr_v1/experiment/ResearchExecutionConfig.java`
- `app/src/main/java/com/example/alpr_v1/metrics/MetricsCollector.java`
- `app/src/main/java/com/example/alpr_v1/metrics/ResearchArchive.java`
- `app/src/main/java/com/example/alpr_v1/pipeline/AlprPipeline.java`
- `app/src/main/java/com/example/alpr_v1/pipeline/MobileAlprEngine.java`
- `app/src/main/java/com/example/alpr_v1/pipeline/SceneMutationGate.java`
- `app/src/main/java/com/example/alpr_v1/pipeline/ZoomTargetGeometry.java`
- `app/src/main/java/com/example/alpr_v1/ui/DetectionOverlayView.java`
- `app/src/main/res/drawable/ic_search_24.xml`
- `app/src/main/res/menu/main_menu.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/example/alpr_v1/acquisition/DynamicZoomPolicyTest.java`
- `app/src/test/java/com/example/alpr_v1/acquisition/ScanAcquisitionControllerTest.java`
- `app/src/test/java/com/example/alpr_v1/acquisition/StaticSceneCycleTest.java`
- `app/src/test/java/com/example/alpr_v1/continuity/SceneTransitionCoordinatorTest.java`
- `app/src/test/java/com/example/alpr_v1/continuity/StaticSceneWatcherTest.java`
- `app/src/test/java/com/example/alpr_v1/pipeline/SceneMutationGateTest.java`
- `app/src/test/java/com/example/alpr_v1/pipeline/ZoomTargetGeometryTest.java`
- `docs/handoffs/implementation-plan-v3.md`
- `docs/handoffs/static-dynamic-scene-identity-az-lock-v3.md`
- `docs/handoffs/implementation-report-v3.md` — raport dodany po commicie implementacji.
