# Wdrożenie handoff v4 — wyłączny fokus celu

Gałąź: `feature/exclusive-target-focus-v4`.
Commit implementacji v4: `f9ffc7fd5c62dff369029775e8de4ebe62b4fa1c`.
Specyfikacja: [handoff v4](static-dynamic-scene-identity-az-lock-focus-v4.md).
Poprzednia implementacja STATIC/DYNAMIC: `26e2efe3edebd75bba7fff2fc753de1eb7a284c2`.
Poprawki sterowania kamerą, skalowania AZ i zakończenia weryfikacji zapisano przed v4 w `f7d305b`.

## Zakres v4

Trwały USER_PICK lub SEARCH_PURSUIT przejmuje akwizycję i prezentację jednej encji natychmiast po ustanowieniu locka. Nie dodano ApplicationMode ani drugiego kontrolera trybów. `TargetFocusSnapshot` to niemutowalny odczyt istniejącego TargetSession: persistent, cameraAttentionOwned, entityId/sessionId, stan odzyskiwania oraz oczekiwanie na świeży MP po release.

`ScanAcquisitionController` jest właścicielem tego cyklu. `AcquisitionQueue.holdForTarget()` wstrzymuje dispatch i przyjmowanie nowych kandydatów z pomocniczych MP; ukrywane encje pozostają w repozytorium. SEARCH_VERIFICATION nie ustanawia wyłączności. Dopiero `promoteSearchToPursuit()` uruchamia ten sam fokus co ręczny wybór.

`MobileAlprEngine` kieruje MT do wybranej encji także wtedy, gdy scheduler proponuje ROI pojazdu. Wynik rozszerzonego ROI jest sprawdzany przez PlateVehicleAssociator. Decyzje innych lub nieprzypisanych tablic nie przechodzą do callbacku prezentacji i MZ. Pomocniczy MP może nadal dostarczać dane całej puli do continuity/reacquire. W badaniach trwały fokus również korzysta z kierowania pracy na cel, zamiast wykonywać legacy akwizycję pozostałych ROI.

Zmiana trwałego fokusu unieważnia rozpoczętą pracę poprzedniego właściciela; bramka działa między etapami oraz przed końcowym dispatch. Rewizja dotyczy właściciela pracy, nie tożsamości sceny. `sceneGeneration`, `visualEpoch` i `cameraTransformGeneration` nadal należą do SceneTransitionCoordinator.

## Prezentacja i release

`DetectionOverlayView.setTargetFocus()` natychmiast filtruje VEHICLE/VEHICLE_ROI po domenowym entityId, a PLATE po własności tracku w VehicleEntityRepository. Odrzuca nieprzypisane elementy, czyści obce badge, pendingPlateReadings/transfers oraz anuluje fade i animacje. Filtr działa na wejściach setItems, setPreviewItems, setTrackedPlateItems, animacji cropa i przy odbudowie renderItems. Po locku obca ramka nie może powrócić przez późny callback.

MainActivity stosuje ten sam zakres do pamięci geometrii podglądu i AZ. Wykorzystuje istniejącą generację prezentacji do odrzucenia callbacków rozpoczętych przed zmianą fokusu. Historia i dane galerii nie są czyszczone.

RECOVERING zachowuje wyłączny fokus i pokazuje „P… · odzyskuję cel…”. Dopiero rzeczywisty ACTIVE_TARGET_LOST/RELEASE lub granica sceny kończy lock. Potwierdzona utrata usuwa własność technicznych tracków utraconej encji przez `VehicleEntityRepository.retireEntity()`; inne encje i zapisane podsumowania pozostają. Licznik nowych entityId nie jest cofany. SEARCH_PURSUIT zachowuje query i wraca do wyszukiwania, bez przywracania utraconego ID na podstawie OCR.

Po zwolnieniu locka widok jest pusty. Stara kolejka zostaje wyzerowana, stable MP cache nie może opóźnić odświeżenia, a engine wymusza nowy MP. Samo pojawienie się predykcji lub starszego pomiaru nie otwiera bariery: `onFreshVehicleMeasurement()` akceptuje wyłącznie zakończony MP z aktualnej rewizji fokusu, nowszy niż pomiar sprzed release. Dopiero wtedy normalna kolejka i widok wielu pojazdów mogą być odbudowane.

Release przed rozpoczęciem animacji AZ usuwa oczekujący callback, przywraca READY i zwalnia blokadę optyki w pipeline. Release podczas RECOVERING kończy odzyskiwanie poprzedniego celu w istniejącym koordynatorze, bez nowej sceny. Późny wynik tego odzyskiwania nie może ustanowić poprzedniego locka ponownie. Zmiana ręcznego celu usuwa również jego poprzedni lokalny tracker i własność aktywnego celu.

## AZ, search i UI

STATIC zachowuje baseline całej kolejki → jednokrotne AZ na encję/scenę → STATIC_IDLE. AZ wymaga wcześniejszego validQuad i normalnego MZ, robi świeże MT/MZ po 1,8× i wraca do 1× bez MP w fazie poprawy.

DYNAMIC SCAN nie uruchamia AZ. USER_PICK, SEARCH_VERIFICATION i SEARCH_PURSUIT mogą wykorzystać jedną próbę na rewizję locka, zgodnie z geometrią, ruchem i budżetem. AZ nie zwalnia wyłącznego fokusu: po powrocie następuje ponowne zakotwiczenie celu, a ewentualne MP nie przywraca B/C do UI ani MZ. Weryfikacja zachowuje wcześniejszą poprawkę pauzowania budżetu czasu podczas optyki i jawnego zakończenia POSSIBLE_MATCH.

Ręczny wybór działa przed odczytem tablicy, dotyczy entityId i ma pierwszeństwo nad search. OCR nie zwalnia manualnego locka. Search normalizuje tekst do A–Z/0–9; pojedynczy bliski wynik daje POSSIBLE_MATCH, niezależny dokładny wynik z wymaganą pewnością promuje SEARCH_PURSUIT. Odrzucenie kandydata wraca do wyszukiwania, bez ukrywania całej puli jak przy trwałym locku.

Zachowano ostatni układ przycisków: opcje w górnej belce; tryb sceny, lupa, release i AZ w jednym rzędzie. Lupa i release są niewidoczne w STATIC. Opis STATIC w Opcjach wskazuje regiony ALPR i konserwatywny globalny fallback.

## Zachowane kontrakty v3

- Polityki: `ScenePolicy`, `StaticScenePolicy`, `DynamicContinuityPolicy`; wspólny koordynator generacji.
- Legacy: `strict_scene_boundary` → STATIC, `dynamic_continuity` → DYNAMIC. UI: Statyczny/Dynamiczny.
- Watcher: nieruchome powiększone regiony pojazdów, fallback na tablice/R0, następnie global-only. Margines 12%, lokalnie 30% zmienionych próbek, globalnie 55%, delta luminancji 24 po kompensacji ekspozycji, 3 obserwacje przez minimum 150 ms. Regiony uzbrajane po baseline/AZ i powrocie do 1×.
- STATIC_IDLE: MP/MT/MZ/AZ i ciężki tracking wyłączone do potwierdzonej zmiany próbki. Kontrolowany zoom zmienia cameraTransformGeneration, nie sceneGeneration.
- Twarda granica STATIC: nowe sceneGeneration, reset repozytorium/mapowań, trackerów, celu/sesji/locka, kolejki, konsensusów, ROI/cache/AZ i pamięci watchera; puste items/renderItems/badge/pending transfers, anulowane animacje i predykcje. Bramka stempla chroni domenę, historię i UI przed starymi wynikami.
- Ten sam OCR po nowej scenie ma nowe entityId i oddzielny wpis historii. DYNAMIC może zachować entityId po potwierdzonym soft reacquire.
- Freeze przy START zapisuje rzeczywisty analysis_mode wybrany dla badania, niezależnie od R0/R1/R2. Eksport raportu, konfiguracji i protokołu `.alprsession` zachowuje analysis_mode i historyczne scene_handling_mode.
- Historia, provisional promotion, limit 40/recykling, najlepszy badge, read-only boxy MZ, issue codes, notatki, Desktop review, timings, NCNN/runtime i kontrakty modeli pozostają bez zmian.

Szczegółowy opis odziedziczonych mechanizmów: [raport v3](implementation-report-v3.md). Ten raport nie zastępuje wyników badań jakości modeli.

## Walidacja

Build `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest`: PASS.
**533 testy JVM, 0 failures/errors/skips, 125 zestawów.**
**78 testów Android: PASS** na Samsung SM-A125F; instalacja z zachowaniem danych przez `adb install -r`.
Log Android: lokalny `app/build/reports/gallery-qa/handoff-v4-regression.log` (artefakt build, poza Git).

| Kryteria | Pokrycie i wynik |
|---|---|
| L8, L10, L11 | PASS: ScanAcquisitionControllerTest, AcquisitionQueueTest, PersistentTargetAcquisitionTest; blokada dispatch, brak przyjęcia background kandydatów, odrzucenie obcej tablicy z rozszerzonego ROI przed MZ |
| L8, L11 — UI | PASS: DetectionOverlayViewInstrumentedTest, natychmiastowy filtr kilku pojazdów/ROI/tablic, brak obcych badge i opóźnionych transferów po 1100 ms |
| L9 | PASS: PossibleMatch nie ma focus entityId; dopiero świeże CONFIRMED_MATCH ustanawia persistent fokus w ScanAcquisitionControllerTest |
| L12 | PASS: RECOVERING i TARGET_RECOVERED zachowują ten sam focus w teście kontrolera |
| L13 | PASS: kontroler czeka na zakończony świeży MP bieżącej rewizji; View odrzuca stare dane podczas bariery i nie przywraca ich po jej otwarciu; SceneModeHudInstrumentedTest sprawdza anulowanie oczekującego AZ przy release |
| L14 | PASS: utrata pursuit usuwa lock, pozostawia query i wymaga świeżego skanu; test repozytorium potwierdza usunięcie własności i nowe ID po ponownej detekcji |
| L15 | PASS: wcześniejsze odczyty pozostają w kontrolerze po manual focus/release; test repozytorium zachowuje zapisane summary; regresje RecognitionHistoryStore i GalleryPresentation przechodzą |
| S1–S12, D1–D3, M1, C1 | PASS: istniejące testy watchera, scen, generacji, konsensusu, recovery, cache, dispatch i hard resetu overlayu |
| H1–H2, R1–R2 | PASS: istniejące testy historii/provisional promotion oraz freeze/eksportu obu trybów i wszystkich ROI policy |
| AZ1–AZ8, L1–L7 | PASS: istniejące testy StaticSceneCycle, DynamicZoomPolicy, ScanAcquisitionController i SceneModeHud; zachowane limity prób i niezależna weryfikacja |

Zestawy Android: RecognitionHistoryStoreInstrumentedTest, GalleryPresentationInstrumentedTest, HumanVerificationJsonInstrumentedTest, RuntimeCompositionInstrumentedTest, FinalPipelineResultDispatchInstrumentedTest, DetectionOverlayViewInstrumentedTest, IntermediateMtCallbackInstrumentedTest, ResearchExecutionConfigInstrumentedTest, NcnnBackendInstrumentedTest, ScanAcquisitionInstrumentedTest, TerminalRecoveryInstrumentedTest, SceneModeHudInstrumentedTest.

Wszystkie wymagane zestawy JVM z handoff uruchomiono, w tym StableSceneVehicleCacheTest, StationarySceneSupportTest, AcquisitionQueueTest, ScanAcquisitionControllerTest, PlateVehicleAssociatorTest i PlateTrackCoordinatorTest.

## Zakres dowodów i ograniczenia

Próba kamery na końcowej aplikacji (SM-A125F, obraz Renault na monitorze): manualny fokus P1/session 2, jawne zwolnienie podczas analizy, pusty overlay, następnie nowy MP z trackiem 8 i odbudowany widok. Galeria zachowała wcześniejszy wpis. Artefakty lokalne: `focus-v4-final-lock.png`, `focus-v4-final-release.png`, `focus-v4-final-fresh-mp.png`, `focus-v4-final-live.log` w `app/build/reports/gallery-qa/`.

Wielopojazdowa wyłączność i spóźnione animacje są sprawdzone deterministycznie w testach Android; kierowanie pracy i filtr przed MZ — w testach kontrolera, kolejki i engine z rzeczywistym associatorem. Nie jest to pełna terenowa kampania z kilkoma jadącymi samochodami ani pomiar skuteczności OCR. Fizyczny ruchowy abort AZ i wszystkie warianty utraty celu nie były odtwarzane jako nagrania z kamery; ich wynik opisano jako automatyczne testy polityki/recovery.

Nie zmieniono diagramów architektury. Gałąź nie została wypchnięta do origin.

## Zmienione pliki v4

- `app/src/androidTest/java/com/example/alpr_v1/ui/DetectionOverlayViewInstrumentedTest.java`
- `app/src/androidTest/java/com/example/alpr_v1/ui/SceneModeHudInstrumentedTest.java`
- `app/src/main/java/com/example/alpr_v1/MainActivity.java`
- `app/src/main/java/com/example/alpr_v1/acquisition/AcquisitionQueue.java`
- `app/src/main/java/com/example/alpr_v1/acquisition/ScanAcquisitionController.java`
- `app/src/main/java/com/example/alpr_v1/acquisition/TargetFocusSnapshot.java`
- `app/src/main/java/com/example/alpr_v1/continuity/SceneTransitionCoordinator.java`
- `app/src/main/java/com/example/alpr_v1/domain/VehicleEntityRepository.java`
- `app/src/main/java/com/example/alpr_v1/pipeline/AlprPipeline.java`
- `app/src/main/java/com/example/alpr_v1/pipeline/MobileAlprEngine.java`
- `app/src/main/java/com/example/alpr_v1/ui/DetectionOverlayView.java`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/example/alpr_v1/acquisition/AcquisitionQueueTest.java`
- `app/src/test/java/com/example/alpr_v1/acquisition/ScanAcquisitionControllerTest.java`
- `app/src/test/java/com/example/alpr_v1/continuity/SceneTransitionCoordinatorTest.java`
- `app/src/test/java/com/example/alpr_v1/domain/VehicleEntityRepositoryTest.java`
- `app/src/test/java/com/example/alpr_v1/pipeline/PersistentTargetAcquisitionTest.java`
- `docs/handoffs/implementation-plan-v4.md`
- `docs/handoffs/static-dynamic-scene-identity-az-lock-focus-v4.md`
- `docs/handoffs/implementation-report-v4.md` — raport dodany po commicie implementacji.
