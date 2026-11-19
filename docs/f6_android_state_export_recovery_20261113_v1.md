# F6 Android — konfiguracja, kalibracja, historia i eksport

Identyfikator: `F6_ANDROID_STATE_EXPORT_RECOVERY_20261113`. Data: 13.11.2026.

**Dostawa hostowa ukończona. Potwierdzenie: `READY.json`. Nowa APK nie została zainstalowana; odbiór A12 pozostaje NOT_VERIFIED.**

Katalog dostawy: `C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f6_state_export_recovery_20261113_v1`.
Repo: `main`, HEAD `f7943a7d7334afd1e4188ef9860b2126331926c8`. Zastane zmiany pozostały w katalogu roboczym.

## Zabezpieczone rzeczywiste dane

Z autoryzowanego A12 `SM-A125F`, serial `R58R346GZYW`, odczytano pliki bez uruchamiania aplikacji, nowej sesji, finalizacji, instalowania APK, force-stop ani czyszczenia danych. Nie edytowano Desktopu, GT, modeli, historycznych wyników ani wcześniejszych dostaw. Nie wykonywano commit/push/reset/clean/stash ani instalacji zależności.

Trwałe kopie PC:

| Plik w `device_readonly/public_exports/` | Bajty | SHA-256 | Kontrola |
|---|---:|---|---|
| `alpr_session_20261113_131600.alprsession` | 96 056 264 | `5da9d606fc0eeb1d748fcbc07cb876a1c819b4e3318e607a21a669355beb908a` | stabilny przed/po odczycie; CRC i manifest PASS |
| `alpr_session_20261113_132112.alprsession` | 96 056 264 | `5da9d606fc0eeb1d748fcbc07cb876a1c819b4e3318e607a21a669355beb908a` | stabilny przed/po odczycie; CRC i manifest PASS |

Pełny wspólny katalog Windows: `C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f6_state_export_recovery_20261113_v1\device_readonly\public_exports\`.

Są to dwie **identyczne bajtowo kopie jednej sesji**, nie dwa pomiary. Źródło ustalone na urządzeniu: `/storage/emulated/0/Download/ALPR_EKSPERYMENTY/E-FINAL-ALPR-01/02_sesje/final_przerwany/`. Nazwa tego katalogu nie była zmieniana ani utożsamiana z `series_id`.

Sesja: `exp-1794570535251-7b6aa4a3`. START `2026-11-13T11:49:57.038Z` (12:49:57 lokalnie), STOP `2026-11-13T12:01:50.965Z`. Czas z zegara sesji: **713,927 s**, czyli 11 min 53,927 s. To materiał inny niż wcześniejszy odbiór 7,819 s.

Surowy katalog wraz z dziennikami, obrazami, indeksami i plikami końcowymi znajduje się w:
`device_readonly/private_original/files/research/sessions/exp-1794570535251-7b6aa4a3/`.
Zachowano również oryginalny strumień TAR, preferencje, logi aplikacji, logcat bez czyszczenia, informacje o pakiecie i pliki identyfikujące nakładki Android Studio. Wszystkie **670 plików surowej sesji** porównano po kopiowaniu ze źródłem telefonu przez SHA: zero różnic. SHA metadanych sesji i preferencji przed/po kopiowaniu są identyczne.

Dowody: `device_readonly/exports-verification.json`, `raw-snapshot-verification.json`, `raw-files-phone-after-copy-sha256.json`, `secured-files-sha256.json`, `audit/real-export-audit.json`. Końcowy wykaz sum całej dostawy uzupełnia później odczytane materiały diagnostyczne.

### Dlaczego PARTIAL

W obu ZIP jest 674 wpisów. Pełny odczyt, CRC i sumy manifestu są poprawne; nie wykryto duplikatów ani nieobjętych manifestem wpisów. Natywny walidator Androida i kontrola zagnieżdżonych obserwacji MP przyjmują strukturę jako **PARTIAL**. Nie jest to odbiór przez czytnik Desktopu.

Zapis zawiera 159 rekordów TV, 122 rekordy ról MT, 135 prób i 938 zdarzeń F6. Zdarzenia obejmują 122 `MT_STARTED`, 122 `MT_RETURNED`, 122 `MT_DECODED` oraz 66 par `MZ_STARTED`/`MZ_RETURNED`. Liczby scen aplikacji nie są liczbą fotografii TV ani `exposure_id`.

37 klatek i 37 rekordów MT ma `stale_or_cancelled=true`, `cancel_reason=scene_superseded`. Wśród ról MT: 86 `COMPLETE` i 36 `INCOMPLETE`; te 36 ma `postprocess_status=NOT_REACHED`, a jeden dalszy stary/anulowany rekord ma dowód ról `COMPLETE`. To jawne przerwania/utrata ważności względem sceny, a nie wykonania z zerem detekcji. Nie zamieniano ich w udane wyniki ani nie wybierano późniejszej odpowiedzi.

Producent wymaga kompletnych powiązań i braku takich stanów do oznaczenia pełnego zbioru. Stąd niekompletność, mimo poprawnego ZIP. `last_write_error` jest pusty; liczniki utraty próbek i telemetrii są zerowe. Wszystkie 318 zapisów `source_image`/`crop_image` są obecne. Sam ręczny STOP nie został uznany za uniwersalne wyjaśnienie PARTIAL. Nie wygenerowano wyników skuteczności FINAL ani oceny względem GT.

### Co rzeczywiście było zainstalowane

Pakiet: `com.example.alpr_v1`, versionCode 1, versionName 1.0. Zabezpieczona bazowa APK ma SHA `33ff5c8fe84b4b8a2a77f64c0fa8a6cbcbeabbaf25353a0bc145633098f3a08b`. To inny plik niż poprzednia dostawa lifecycle v1 `1cd1350d…1e071`.

Znaleziono nakładki `code_cache/.overlay/base.apk/classes3.dex`, `classes5.dex`, `classes6.dex`, `classes9.dex` i `.overlay/id`, z mtime 13:24. Zostały skopiowane i zahashowane. Dlatego sam SHA bazowej APK nie identyfikuje wszystkich klas po Apply Changes. Badana sesja przechowuje własną tożsamość buildu z czasu START; nie przypisujemy jej arbitralnie do ostatniego raportu lub HEAD. W czasie odczytu diagnostycznego aplikacja nie miała PID; nie można było zbadać bieżącego stosu dawnego komunikatu busy. Nie uruchamiano jej w celu wymuszenia objawu.

## Objawy, przyczyny i poprawki

| Objaw | Ustalenie | Poprawka i sprawdzenie |
|---|---|---|
| ROI kontra F6, karta „Budżet ROI” | To różne wymiary. `experiment_type=roi_budget`, polityka `r0_full_frame`, profil `f6_final180_v1`; napis wariantu `R0_FINAL` jest etykietą operatora. | Podsumowanie w istniejącym UI; F6 obok kategorii, osobne szczegóły strategii. Historyczne karty korzystają z zamrożonych metadanych ZIP. |
| Oscylacja „Wymaga…”/„Zapisano” | W rzeczywistym logu to samo otwarcie, token 5: 13 wpisów, jeden digest ROI, jeden komplet czterech punktów, jedna fizyczna geometria. Zmieniają się WAITING/EDITING i wysokości 453/340 px. Kod mieszał świeżość kamery z oceną zatwierdzenia i przełączał panel sterowania. | Odtworzony test przed poprawką FAIL. Zapis, potwierdzenie źródła i podgląd są rozdzielone. Panel zachowuje miejsce w oczekiwaniu ze szkicem. Przerwa zatrzymuje gest/powtarzanie, zachowując ostatnią korektę i bufor lupy przy znanej geometrii. |
| Edytor po pauzie/restarcie bez świeżej transformacji | Zachowane zatwierdzenie nie oznacza świeżego wejścia kamery. Sam brak klatki nie dowodzi usunięcia punktów. | Możliwy jest przegląd zatwierdzenia w oczekiwaniu. Szkic powstaje po potwierdzeniu źródła, raz; SAVE i START wymagają rzeczywistej zgodności. Zmiana cropu, zoomu, rotacji, mirror, wymiarów lub kamery nadal blokuje. |
| Eksport znika po restarcie | Widoczność była zależna od RAM: `ExperimentSession.State.FINISHED`. | Stałe wejście „Sesje / Eksport”; inwentaryzacja trwałych katalogów, również sesji częściowych i błędów przed START. Bez uruchamiania nowego pomiaru. |
| „Trwa przygotowanie…” | Historyczny właściciel: **NIEUSTALONY**. Odczytano 333 metadane; 39 starszych sesji ma czas zakończenia przepisany w minucie 13:26. Kod startowego recovery seryjnie przebudowywał takie katalogi. Nie jest to dowód deadlocku. | Startup wykonuje inwentaryzację, nie masową finalizację. Recovery jest jawne, na kopii wybranej sesji. Operacje mają ID, session_id, etap, postęp, czas zmiany, wynik i błąd. Żywy właściciel nie jest arbitralnie zerowany. |
| Możliwa wieczna blokada STOP | Niezależna luka kodu: wyjątek przy tworzeniu/oczekiwaniu bariery cropów lub wysłaniu zadania mógł ominąć finalizator po ustawieniu flagi. Nie przypisujemy jej bez logu do pierwotnego komunikatu operatora. | Regresja rzeczywistej ścieżki MainActivity z odrzuconą barierą; terminalny błąd kończy wyłącznie właściwego właściciela i zachowuje pliki oraz snapshot. Obejmuje także błędy typu `Error`. |
| Telefon widzi pliki, Windows nie | Oba pliki istnieją na pamięci emulowanej, są odczytywalne, mają poprawne CRC/SHA. MediaStore ma oba wpisy z `is_pending=0`. Ten sam katalog przez Windows Shell/MTP nadal ma zero elementów. | **Przyczyna MTP NIEUSTALONA.** Nie dodano skanowania, uprawnień szerokiej pamięci ani zmiany rozszerzenia/lokalizacji. Dostarczono obie poprawne kopie PC. Nowy eksport ma odczyt kontrolny rzeczywistego URI i potwierdzenie. |

`audit/real-calibration-timeline.csv/.json` i `phone-session-inventory.json` dokumentują odczyty. Kopie odpowiednich 39 metadanych są w `device_readonly/startup_recovery_metadata/`. Nie przywracano ich historycznych czasów przez zgadywanie.

## Przepływ stanu przed → po

| Krok | Stan danych / zachowanie |
|---|---|
| Zatwierdzenie TV i F6 | Dwa odrębne rekordy i istniejący kontekst zatwierdzenia; nie ma drugiego magazynu ROI. |
| Przerwa podglądu bez zmiany źródła | Zatwierdzenie pozostaje. UI: „Zapisano · oczekuję potwierdzenia geometrii”; sterowanie/SAVE ograniczone do zgodnego źródła. Lupa zachowuje swoją jawną informację o aktualności obrazu. |
| Powrót świeżej zgodnej geometrii | Istniejąca rewalidacja potwierdza źródło; te same punkty i ROI, bez ponownej korekty. |
| Rzeczywista niezgodność | Zachowany zapis i jawna blokada; brak automatycznego przeliczenia punktów lub zastąpienia polem TV. |
| START i trwanie | Podsumowanie z niezmiennego snapshotu sesji. Późniejsza zmiana preferencji nie zmienia opisu ani eksportu bieżącej sesji. |
| STOP | Dotychczasowa polityka zadań w locie; zapis domykany lub jawnie błędny. UI obserwuje rzeczywiste etapy. |
| Finalizacja | Odpływ kolejki → projekcja JSONL → ZIP → dotychczasowe walidatory. Wyjątek nie usuwa kalibracji ani wcześniejszych plików. |
| Restart | Odczyt historii. Osierocona operacja otrzymuje INTERRUPTED; nadal działający właściciel w tym samym procesie pozostaje rozpoznany. |
| Wybranie sesji bez zamkniętego ZIP | Jawna próba recovery na autentycznej kopii; nie podmienia bieżących preferencji do historycznej konfiguracji. Brak wymaganego snapshotu nadal kończy się błędem. |
| Historia podczas pomiaru/finalizacji | Wejście i szczegóły są dostępne. Uruchomienie zewnętrznego okna zapisu czeka na STOP/finalizację, żeby nie przenosić aktywnej kamery do tła. Spóźniony wynik SAF nie kopiuje dokumentu podczas nowego pomiaru. |
| Eksport zakończonej sesji | Kopiowanie istniejącego ZIP przez SAF; bez powtórnej inferencji/finalizacji, niezależnie od bieżących modeli i kalibracji. |
| Koniec eksportu | Zamknięcie strumienia → odczyt dokumentu → liczba bajtów i SHA. Oddzielne ID kopii; te same dane sesji. Brak odczytu oznacza SAVED_UNVERIFIED, a rozbieżność/błąd FAILED. |

To tabela wymaganych przejść sprawdzanych hostowo, nie deklaracja przejścia nowej APK na telefonie. Fakty historyczne telefonu podano osobno wyżej.

## Zakres implementacji i zgodność

Wykorzystano obecne MainActivity, SettingsActivity, wspólny kontroler i overlay TV/F6, `TvEditDraft.sameCoordinates`, istniejące zatwierdzenie ROI, ResearchSessionViewModel/Store, katalog i picker sesji, archiwum oraz SAF. Bufor PixelCopy/lupy pozostaje dotychczasową implementacją; nie dodano drugiego źródła obrazu ani dekodera znaczników.

Podsumowanie ustawień nie wybiera modeli ani nie hashuje ich plików w pętli renderowania. Dla trwania/finalizacji używa snapshotu, dla następnej sesji rzeczywistych ustawień; tekst jest buforowany według konfiguracji. Kategoria End-to-end nie jest aliasem ROI: w obecnym kodzie ROI umożliwia zamrożony setup kamery, TV_BBOX i rozszerzenia F5A/F6. Nie przełączano kategorii.

Potwierdzona konfiguracja badanej sesji: MT `plate-20261009_082038`, MZ `character-20261007_215302`; oba `tflite-fp32`, CPU po 2 wątki, GPU false. MP OFF; scena statyczna, `strict_scene_boundary`, R0, `TV_BBOX_CROP`, F6. `R0_FINAL` zachowano jako etykietę.

Schemat archiwum i serializacja wyników pozostają bez zmian. Nadal obowiązują `alpr.scene_evidence.v1`, `alpr.mt_detection_roles.v1`, `alpr.tv_bbox_input.v1`, dziennik F6 i istniejący manifest SHA. Nie dodano pól GT, exposure_id, nowych czasów ani geometrii do starych danych. `CROP_ONLY_TRANSLATION`, `H_TV=NOT_APPLIED`, SourceStamp/frame_key/MT/FINAL/MZ oraz dotychczasowe jednostki pozostają bez zmian. Pełny kontrakt producenta: `docs/f6_final180_android_contract_20261112.md`.

Nowe małe pliki sterujące znajdują się poza archiwum: `files/research/operations/<operation_id>.json`, schemat `alpr.research_operation_receipt.v1`. Zawierają session_id, etap, stan, postęp, błąd/przyczynę, szczegóły URI i `updated_wall_millis` (UTC Unix epoch, ms). To stan operacji/potwierdzenie kopii, nie alternatywny magazyn wyników. Przegląd ostatnich operacji jest dostępny z historii również po restarcie. Nieznany/nieczytelny rekord sterujący nie staje się sukcesem pomiaru.

Nie zmieniano modeli, liczby wątków, rozdzielczości, progów, NMS, schedulera, śledzenia, konsensusu, timera ani wyboru pierwszego ocenianego MT. CameraX ViewPort jest nadal oparty na rozmiarze analizy (`ViewPort.FIT`), nie na wysokości opisu UI. Dla identycznej kalibracji/pikseli testy używają dotychczasowej ścieżki wejścia MT/MZ.

Narzut nowego stanu operacji: małe zapisy przy zmianach etapu i co 4 MiB kopiowania dokumentu, poza czasami MT/MZ; diagnostyka kalibracji przy zmianach stanu, bez bitmap w logach. Narzut na A12 i porównywalność czasów z historyczną APK: **NOT_VERIFIED**.

Minimalny diff tej kontynuacji i lista plików: `audit/task-only.patch`, `audit/changed-files.json`. Stan przed zmianą: `audit/worktree-before.patch`, `source-before/`, `source-before-sha256.json`. Poprzednie 2167 plików dostaw oraz modele/biblioteki sprawdza `protected-deliveries-check.json`; końcowy odbiór wymaga zera naruszeń.

## Testy i ograniczenia

Szeroka regresja JVM: **1405/1405 PASS**, 0 FAIL/ERROR/SKIP (`audit/final_run/`, `audit/full-regression-2.log`). Po ostatnim zachowaniu ochrony okna SAF podczas pomiaru: **37/37 PASS**, 0 FAIL/ERROR/SKIP (`audit/final_targeted_run/`). Ten ostatni przebieg sprawdza finalne źródła MainActivity/VM, kopie rzeczywistej sesji i oba przypadki blokady dokumentu. Wyników nie sumuje się. Końcowa kompilacja instrumentacji i budowa debug offline: **PASS** (`audit/final-targeted-and-build.log`). Instrumentacja na urządzeniu: **NOT_RUN**.

Nie sumuje się nachodzących przebiegów ani dawnych 1383/1368/73 testów. Zachowano diagnostyczne FAIL i błędy kompilacji we wcześniejszych logach tej dostawy. Test przed poprawką pokazał chowanie panelu przy przerwie klatek. Pierwsza szeroka regresja miała 1399 przypadków i cztery FAIL: zgodność starego wywołania finalizatora, dwa przycięcia starego opisu HUD przy font 130% i przejście po rzeczywistej utracie transformacji. Przypadki poprawiono i ponownie sprawdzono; nie wyłączano walidatorów ani nie poprawiano ręcznie ZIP.

Podstawowa komenda końcowej regresji, z istniejącym JBR Android Studio jako JAVA_HOME:

```powershell
.\gradlew.bat --offline -I app/build/f6_state_export_recovery_20261113_v1/audit/regression.gradle testDebugUnitTest compileDebugAndroidTestJavaWithJavac assembleDebug --console=plain
```

JVM: 1024 MiB, jeden fork, odnowienie co 25 klas. Wyłączono wyłącznie ponowne generowanie testu 4100 klatek; zaakceptowane archiwa MP v1, w tym >4096 i scena >32 MiB, są sprawdzane bez regeneracji. Ścieżki środowiskowe: `F6_REAL_EXPORTS` wskazuje obie zabezpieczone kopie obecnej sesji; `F6_DEVICE_COPY` — starszą zabezpieczoną kopię dla wcześniejszej regresji; `F6_ACCEPTED_MP_FIXTURES` — odebrane MP v1; `F6_PREVIOUS_BAD_ARCHIVE` — zachowany plik negatywny. Są to odrębne dowody.

Mały nowy materiał producenta: `producer_regression/synthetic-20min-120scenes-mt-mz-PARTIAL.alprsession` (2861726 B, SHA `8e22c81a598d21a0b3b3898408d2c3b4691d2f7cc77835befd155dea0f134244`). Powstał przez istniejący silnik z syntetycznymi backendami, writer, finalizację i eksporter. Zawiera 120 wywołań MT, wiele rzeczywistych w tym teście MZ, zero/wiele detekcji, brak odczytu i rzeczywiste anulowania testowe. PARTIAL jest zachowany. Czas 20 minut jest **osią syntetyczną**, nie czasem pomiaru na A12 i nie testem cieplnym. Nie jest to generator pustego paddingu MP ani ręcznie napisany JSON.

Obejrzano hostowe rendery obu istniejących edytorów w rozdzielczości 720×1600, tekst 100% i 200% (`renders/`). To rzeczywiste widoki XML/kontrolery uruchomione w Robolectric Native z syntetycznym buforem. Nie są zrzutami telefonu. Potwierdzają rozdzielenie opisów TV/F6 oraz dostępność celu, lupy, strzałek i przycisków w sprawdzanym układzie; nie dowodzą braku migania ani wygody dotyku na GPU A12.

Pakiet do odczytu przez Desktop: `producer_regression_package.zip`, SHA `406561d736929232dbb56db8b2257d115acc2d6b8dfa78bac7dc5d7aa804c14f`. Dostarcza regresję zmienionej ścieżki producenta z obserwacją etapów; format sesji pozostaje dotychczasowy. **Odbiór tego pakietu przez Desktop: NOT_VERIFIED**. Rzeczywiste eksporty mają osobne lokalne ścieżki wyżej.

## Nowa APK i aktualizacja

Nowa APK: `C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f6_state_export_recovery_20261113_v1\alpr-f6-state-export-recovery-debug.apk`.
SHA-256: `d6809ba6eec756a5b219187f0992e7c407b67e4dbdc56958ff9caa0407db9fb6`.
Pakiet `com.example.alpr_v1`, versionCode 1/versionName 1.0; build UTC `2026-11-13T14:58:08.555659700Z`.
Podpis: `f3385991af6f601faf4df59697d30f143101a71ee988035745027d3e85aa04a2`; porównanie ze skopiowaną bazową APK telefonu: PASS — ten sam certyfikat; bez instalacji.

Rozmiar APK: 87 283 764 B. Wspólne biblioteki natywne są identyczne ze skopiowaną bazową APK. Zwykłe `assembleDebug` zawiera ponadto istniejące projektowe `lib/armeabi-v7a/libalpr_ncnn.so` i `libc++_shared.so`, nieobecne w zoptymalizowanej bazowej dostawie telefonu. Nie dodawano zależności ani nie wybierano NCNN; badana konfiguracja pozostaje TFLite FP32. Porównanie: `audit/apk-assets-native-comparison.json`. Modele są instalowanymi danymi aplikacji, nie nowymi zasobami tej APK; odczyty ADB ich nie zmieniały. Nie deklarujemy pomiarowej równoważności dwóch APK.

**APK nie została zainstalowana. Zainstalowana stara wersja nie zawiera tej dostawy.** Pliki modeli, ustawień, sesji i kalibracji pozostają w istniejących lokalizacjach. Aktualizacja z tym samym pakietem i zgodnym podpisem nie wymaga odinstalowania ani czyszczenia danych.

Odbiór operatora po aktualizacji, na istniejącej nieruchomej planszy TEST_ONLY/T, bez kolejnego pełnego FINAL:

1. Zachować kopie PC z tego raportu. Zainstalować APK jako aktualizację, bez odinstalowania i bez czyszczenia aplikacji. Sprawdzić nowy build w diagnostyce; nie opierać identyfikacji na samym versionName.
2. Sprawdzić podsumowanie F6/R0/MP OFF/FP32/CPU×2 i timer OFF. Otworzyć istniejące pole F6 bez dotykania narożników; porównać wszystkie cztery punkty i prostokąt. Sam brak świeżej klatki ma być oczekiwaniem, nie utratą zapisu. Przy prawdziwej niezgodności przerwać odbiór i zachować pełną przyczynę.
3. Obserwować bez dotyku, następnie wykonać jedną korektę i anulować szkic albo świadomie zapisać przed testem. Sprawdzić 1/5 px, 4×/8× oraz brak migania. Nie potwierdzać braku kodów na podstawie samego poprawnego czworokąta.
4. Krótki START → STOP, również z zadaniem w locie. Brak tablic/MZ na planszy jest dozwolony; nie wymuszać trafienia. Obserwować etap zapisu i szczegóły ewentualnego błędu.
5. „Sesje / Eksport” → wybrać właściwą datę/session_id → eksport. Sprawdzić potwierdzenie URI, bajtów i SHA; odczytać kopię na PC niezależnie od widoku MTP. Nie kasować pliku telefonu.
6. Ponownie otworzyć F6 bez korekty. Uruchomić aplikację ponownie zwykłą drogą operatora; historia ma pozostać dostępna. Wykonać drugi krótki START bez rekalibracji, STOP i eksport. Nie rozpoczynać pełnego FINAL jako sposobu zbierania diagnostyki.

Zabezpieczenie wadliwej/nieukończonej sesji jest możliwe niezależnie od poprawnego `.alprsession`: zachowuje się jej katalog i dzienniki. Recovery korzysta wyłącznie z autentycznych danych na kopii. Brakującego snapshotu, obrazu lub niewykonanego MT nie można odzyskać przez dopisanie dzisiejszych preferencji. Oryginały tej sesji nie zostały naprawiane ani podniesione do COMPLETED.

## Statusy końcowe

| Status | Wynik |
|---|---|
| HOST_TESTS | PASS — końcowa regresja, kompilacja instrumentacji, debug offline |
| REAL_FILES_RECOVERY | PASS — dwie stabilne kopie, CRC/SHA, surowe 670 plików; sesja nadal PARTIAL |
| DEVICE_UI_LIFECYCLE | NOT_VERIFIED — nowej APK nie instalowano |
| DEVICE_EXPORT_VISIBILITY | Nowa APK NOT_VERIFIED; istniejąca rozbieżność Windows MTP potwierdzona, przyczyna NIEUSTALONA |
| EXPERIMENT_START | NO_GO do rzeczywistego odbioru cyklu A12 i wspólnego odbioru stanowiska |

Hostowy PASS oraz zabezpieczenie plików nie dowodzą ustąpienia oscylacji na A12, poprawnego działania MTP ani gotowości całego FINAL. Wspólny odbiór Desktop + A12 + TV pozostaje oddzielny.
