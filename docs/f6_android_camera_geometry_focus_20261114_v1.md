# FINAL176 — Android: kadr CameraX i utrzymanie AF

**ZAMKNIĘTA DOSTAWA HOSTOWA — kompletność potwierdza READY.json. Odbiór nowej wersji A12/TV pozostaje NOT_VERIFIED.**

Zakres: sekcja B handoffu z 14.11.2026. HEAD `main / f7943a7d7334afd1e4188ef9860b2126331926c8`, lokalne zmiany zachowane. Bez ingerencji w Desktop, GT, historię, modele, regułę first-MT, scheduler, NMS, konsensus i rozmiary wejść produkcyjnych MT/MZ 512×512/320×320. Bez instalacji APK, uruchamiania pomiaru na telefonie, usuwania danych, commit/push/reset/clean/stash i nowych zależności.

## Przyczyna → zmiana

| Obszar | Potwierdzony stan / granica diagnozy | Zmiana |
|---|---|---|
| Kadr | A12 raportował Preview PRIVATE **960×720** i ImageAnalysis YUV **1920×1080**. Preview używał domyślnego selektora 4:3, analiza wybranego 16:9. Wspólny ViewPort FIT już istniał. Różnica surowych buforów sama nie dowodzi różnego efektywnego FOV — CameraX może stosować crop. Fizycznego objawu uchwytów na dywanie nie odtworzono nową APK. | Oba UseCase otrzymują tę samą strategię rozdzielczości i filtr proporcji, ten sam obrót i dotychczasowy wspólny ViewPort. Preview raportuje własne ResolutionInfo; nie jest opisywany rozmiarem analizy. Pozostawiono rzeczywiste macierze źródło→widok i istniejący wspólny PixelCopy edytora/lupy. |
| Zmiana rozdzielczości | Zapis TV dotyczył źródła **960×1280**; aktualny bufor analizy po obrocie miał **1080×1920**. | Nie przeliczano ani nie zatwierdzano starych punktów. Dotychczasowe porównania źródła/cropu/rotacji i bramka F6 nadal wymagają świadomej kalibracji. Zmiana samego layoutu nie zmienia tożsamości źródła. |
| Czytelność ustawień | Rozmiary Preview i analizy mogą się różnić; katalog nie potwierdza wybranej kombinacji. | Ustawienia pokazują żądanie dla obu strumieni oraz ostatnie rzeczywiste bufory, proporcje, crop i obrót, osobno dla każdego. Snapshot jest przekazywany przy otwarciu ustawień, opisany jako ostatni odczyt; po zmianie wyboru trzeba wrócić do podglądu. Brak odczytu nie jest rozmiarem 0×0 ani potwierdzeniem gotowości. Te same szczegóły są w istniejącym opisie kamery edytora. |
| Ostrość | Kamera działała w CONTINUOUS_PICTURE. Trzy odczyty kontrolne pokazały PASSIVE_UNFOCUSED, AF trigger IDLE, AE FLASH_REQUIRED, ekspozycję 66 669 000 ns i ISO 3243. W tym krótkim oknie wartości były stałe; **historyczna przyczyna pompowania: NIEUSTALONA**. Istniejące zoomAndFocus jest ścieżką Auto-Zoom, anuluje po 3 s i nie sprawdza wyniku AF. | Osobny przycisk **„Ustaw AF na TV”** w istniejącym pasku F6. Bez zoomu, tylko FLAG_AF, disableAutoCancel i potwierdzenie future. Nie zmieniano ścieżki Auto-Zoom ani AE/AWB. |

Przycisk preferuje środek poprawnie zatwierdzonego pola F6; przed jego ustawieniem może użyć środka poprawnej kalibracji TV. Punkt w pikselach źródła jest mapowany istniejącą geometrią do fabryki MeteringPoint PreviewView. Obsługa wymaga potwierdzonych możliwości AF AUTO oraz obsługi wybranego punktu. Nieznane możliwości pozostają nieznane, a brak obsługi nie daje statusu sukcesu.

Statusy AF: NOT_REQUESTED, PENDING, HELD, FAILED, UNAVAILABLE, LOST, RELEASED. HELD wymaga udanego wyniku AF. Błąd/wyjątek/6-sekundowy brak potwierdzenia nie jest blokadą. Przycisk, pełny content description/tooltip i komunikat błędu pokazują rzeczywisty stan. Po jawnym żądaniu utrzymania AF START czeka na potwierdzenie; żądanie można anulować. Nie dodano bramki AF do historycznego trybu F5A.

Potwierdzenie należy do konkretnej kamery, bindingu i generacji żądania. START wykorzystujący ten sam binding nie ponawia AF. STOP wywołuje cancelFocusAndMetering i domyka kamerę; zmiana kamery/zoomu/rebind unieważnia potwierdzenie. Stare future nie ustawia statusu nowego żądania. Błąd zwolnienia jest zapisany jako release_error. RELEASED oznacza zwolnienie żądania; bieżący tryb sensora znajduje się w CaptureResult. Nieoczekiwany powrót do ciągłego AF lub utrata stanu FOCUSED_LOCKED oznacza LOST, bez cichego ponawiania AF i bez zmiany schedulera sesji.

## Dane i tożsamość

Dostawa: `C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f6_camera_geometry_focus_20261114_v1`.

Zabezpieczono **92/92 stabilne pliki** telefonu (SHA przed/kopia/po), w tym surową sesję `exp-1794614155571-7bfd532a`, preferencje, logi i nakładki DEX. Kopie: `device_readonly/private_original/`; dowód: `device_readonly/copy-integrity.json`. Kamera była wyłącznie odczytywana. Katalog deklarowanych trybów PRIVATE/YUV obejmuje **1440×1080** dla obu; nie obejmuje 1600×1200 ani 1920×1440. **Wspólne rzeczywiste działanie 1440×1080: NOT_VERIFIED**. Pierwsza próba ma użyć 1440×1080, a w razie braku zgodnego pełnego kadru — jawnego fallbacku 1280×960, bez automatycznej zmiany preferencji przez tę poprawkę.

Zabezpieczone istniejące archiwum w surowym katalogu `final/`: **9 047 817 B**, SHA-256 `d33f22c2e125c53958bb2b7d25bcb54eeb760690672a299969ccdd07bc6023f9`; ZIP/CRC i sumy manifestu PASS. Stan PARTIAL zachowano. Zapisane źródło 960×1280 i żądanie 1280×960 nie potwierdzają pracy przy 1920×1080. Nie analizowano GT, QR ani skuteczności MT/MZ. Szczegóły: `audit/original-session-export-audit.json`.

Bazowa APK telefonu: SHA `70ecfa88902d2c48d73ee0ed12f6f68bb288ebba1975dac88a94f421eea703f0`; występowały **cztery nakładki DEX Apply Changes**. Dlatego sam SHA bazy nie identyfikuje uruchomionych klas. Zachowano ich sumy i bajty. Sesja 005742 zapisuje build UTC 2026-11-13T20:34:27.570595700Z; nie przypisujemy jej arbitralnie do wcześniej dostarczonej APK.

### Minimalne uzupełnienie metadanych

Nie zmieniono schematu kontenera, serializacji wyników, SourceStamp/frame_key/MT/FINAL/MZ, CROP_ONLY_TRANSLATION ani H_TV=NOT_APPLIED. Nowe sesje F6 otrzymują opcjonalny obiekt **session.json → camera_observation**, schema **alpr.camera_observation.v1**:

- analysis_requested_resolution i preview_requested_resolution: faktyczne żądanie przy bindingu; preview/analysis: buffer_width_px, buffer_height_px, crop_rect_buffer_px i rotation_degrees z właściwego ResolutionInfo, bez dopisywania brakującej wartości;
- source_geometry: istniejąca obserwacja źródła, nie nowa transformacja;
- focus: status, requested, supported (null przy braku potwierdzenia), af_auto_supported, reason, release_error, generacje, point_source_xy w camera_full_bitmap_xy i czasy;
- observed_elapsed_ns, requested_elapsed_ns, updated_elapsed_ns: ANDROID_ELAPSED_REALTIME, ns; focus.capture.sensor_timestamp_ns: surowy czas CAMERAX_SENSOR, ns, bez dopasowywania zegarów; ekspozycja w ns, odległość ostrzenia w dioptriach jako surowy odczyt (nie pomiar odległości do TV).

Obiekt jest kopiowany przy rzeczywistym START do istniejącego ResearchSessionStore przed trwałym zatwierdzeniem START. Późniejsze preferencje lub zmiany AF nie przepisują tego snapshotu. Zmiany stanu podczas sesji trafiają do istniejącego telemetry/events.jsonl jako **camera_focus_state_changed** z session_id i obserwacją. Camera2 rejestruje zmiany trybu/stanu AF wraz z AE, ekspozycją i bindingiem; nie zapisuje bitmap ani JSON co klatkę. AF nie jest częścią digestu geometrii. Starsze sesje bez nowego obiektu pozostają bez zmian. Odbiór nowych rzeczywistych metadanych przez Desktop: NOT_VERIFIED.

## Dwie kontrole i build

Końcowy przebieg: **2/2 PASS, 0 FAIL/ERROR/SKIP**, kompilacja instrumentacji i debug offline **PASS** (`audit/two-checks-and-build-final.log`, `audit/final-actual-results/`). Instrumentacji na urządzeniu nie uruchamiano. Są to dwa unikalne testy, bez dodawania wcześniejszych wyników. Poprzedni przebieg tych samych dwóch kontroli także przeszedł; zachowano go oddzielnie. Wcześniejsze próby kompilacji miały dwa błędy typowania/importu, poprawione przed uruchomieniem testów; logi pozostają w dostawie. Nie uruchamiano całego testDebugUnitTest bez filtrów.

Komenda: `gradlew.bat --offline -I app/build/f6_camera_geometry_focus_20261114_v1/audit/two-checks.gradle testDebugUnitTest --tests '*CameraGeometryProviderTest.final176SharedAspectEditorAndSourcePixelsAndStaleApproval' --tests '*CameraSetupControlTest.final176FocusResultLifecycleAndFrozenSessionObservation' compileDebugAndroidTestJavaWithJavac assembleDebug --console=plain`. Użyto istniejącego JBR Android Studio i cache offline; `F6_CAMERA_FIXTURES` wskazywało nowy katalog producer_regression_final.

1. **Geometria:** dobór wspólnego aspektu, rzeczywiste macierze i crop CameraX przez istniejący fixture, letterbox, obrót 90°, piksele konwertowanej pełnej bitmapy i obwiedni F6 w TV cropie; istniejący kontroler edytora otrzymuje tę samą geometrię. Zmiana 4:3→16:9 odrzuca stary snapshot TV/F6, zachowuje poprzednie zatwierdzenie; stary callback nie ożywia bindingu. To test syntetyczny, nie dowód fizycznego FOV A12.
2. **AF/lifecycle/eksport:** realny CameraController z syntetycznym IO: PENDING→HELD, utrzymanie ponad 5 s bez auto-cancel, START tego samego bindingu bez ponowienia AF, bramka F6 i brak nowej bramki w F5A; brak obsługi/nieznane możliwości, false, wyjątek i timeout; anulowanie/STOP odrzucają spóźniony sukces. Istniejący producent/serializer tworzy PARTIAL bez fikcyjnego MT; snapshot AF pozostaje niezmienny po zmianie obiektu i zwolnieniu AF.

Mały wynik drugiej kontroli: `producer_regression_final/synthetic-focus-snapshot-stop-no-frame-PARTIAL.alprsession`, 19602 B, SHA `0effb1d77c054d6cf7309af030bbf0aa4bfa32be64cffd8a1012f8ea86b58ad3`. To **SYNTHETIC/TEST_ONLY**, STOP przed klatkami, nie pięć fotografii z A12. Walidator istniejącego kontraktu producenta przechodzi bez osłabienia reguł. Nie regenerowano 4100 klatek ani nie uruchamiano całej regresji. Nie sumuje się powtórzeń tych samych dwóch kontroli.

## APK i odbiór operatora

APK: `C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f6_camera_geometry_focus_20261114_v1\alpr-f6-camera-geometry-focus-debug.apk`.

SHA-256: **`4fcb0dd33fac84d9ebafd17e7e784c3142c4ef11cb8454bde16be36a207485bf`**, 87300688 B. Build UTC **2026-11-14T01:27:21.756952500Z**, pakiet com.example.alpr_v1, versionCode 1/versionName 1.0. Podpis v2 PASS; certyfikat SHA `f3385991af6f601faf4df59697d30f143101a71ee988035745027d3e85aa04a2`, zgodny z bazową APK A12. Plik jest przeznaczony do aktualizacji bez usuwania danych. Nie instalowano go automatycznie. Nie potwierdzono jego uruchomienia na telefonie. Wspólne zasoby/biblioteki są identyczne; zwykły assembleDebug zawiera również istniejące biblioteki armeabi-v7a, jak poprzednie dostawy debug.

Lista zmian i minimalny diff: `audit/changed-files.json`, `audit/task-only.patch`. Stan wejściowy i kopie źródeł zachowano. Kontrola ochrony: **5897 wcześniejszych plików bez naruszeń**, 0 niepowiązanych zmian źródeł. Modele, biblioteki i zależności źródłowe pozostały bez zmian.

Operator wykonuje zwykły **Run ▶**, bez Apply Changes i bez odinstalowania/czyszczenia danych. Następnie:

1. Wybrać 1440×1080, wrócić do podglądu i sprawdzić faktyczne oba strumienie. W razie niezgodnej kombinacji/full TV wybrać 1280×960; odnotować rzeczywiste wartości, nie sam dropdown.
2. Na nieruchomej planszy TEST_ONLY v2 świadomie ustawić TV i osobne pole fotografii F6. Punkty poza TV nie są akceptacją. Nie dopasowywać pola osobno do JPG.
3. Zamknąć edytor, nacisnąć „Ustaw AF na TV”, zaczekać na **AF ✓**. FAILED/UNAVAILABLE/LOST oznacza brak odbioru ostrości; anulowanie żądania nie daje GO.
4. Jeden START Androida → jeden pokaz pięciu zdjęć technicznych → POSTROLL/T pozostaje → STOP telefonu → eksport. Bez FINAL176 i bez liczenia skuteczności bez GT.
5. Potwierdzić w rzeczywistych source_image pełny TV i pole zdjęć bez ucięcia; w cropie brak kodów. Desktop potwierdza oba QR, cztery ArUco i pierwszeństwo MT. Sam podgląd/AF ✓ nie zastępuje kontroli plików.

| Status | Wynik |
|---|---|
| ANDROID_HOST_CHECKS | PASS — dokładnie dwie kontrole |
| DEBUG / INSTRUMENTATION_COMPILE | PASS offline; instrumentacja urządzeniowa NOT_RUN |
| BASELINE_FILES | PASS — 92 stabilne kopie; stare archiwum CRC/SHA PASS |
| A12_ACTUAL_1440x1080_WITH_PREVIEW | NOT_VERIFIED — zadeklarowane możliwości nie są odbiorem bindingu |
| A12_FOCUS_HOLD | NOT_VERIFIED — udany AF sprawdzono wyłącznie syntetycznym future |
| A12_TV_FIVE_PHOTOS | NOT_VERIFIED — brak nowej próby pięciu zdjęć |
| FINAL176 | NO_GO do wspólnego odbioru technicznego |

Pełny FINAL176 pozostaje **NO_GO**, dopóki wspólny odbiór pięciu fotografii nie potwierdzi kadru, AF, kodów i korelacji. Tej próby nie zastępują dwa testy hostowe ani wcześniejsze archiwum 005742.
