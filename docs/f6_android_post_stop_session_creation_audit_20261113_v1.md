# F6 — dodatkowe sesje po STOP: audyt i poprawka

**ZAMKNIĘTA DOSTAWA HOSTOWA — kompletność potwierdza READY.json. Nowej APK nie instalowano; odbiór A12 pozostaje NOT_VERIFIED.**

Zadanie: kontynuacja rzeczywistej sesji `exp-1794585411433-6305e1b2`, 13.11.2026. Zakres: Android, bez zmian Desktopu, GT, modeli, ustawień inferencji, reguły oceny lub istniejących danych. Nie wykonano instalacji, uruchomienia pomiaru na telefonie, force-stop, restartu telefonu, czyszczenia danych ani logcat. Bez commit/push/reset/clean/stash i nowych zależności.

## Zabezpieczone dane rzeczywiste

Katalog dostawy: `C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f6_post_stop_session_creation_audit_20261113_v1`.

Surowa główna sesja: `device_readonly\private_original\files\research\sessions\exp-1794585411433-6305e1b2\`. W tym samym katalogu nadrzędnym zachowano cztery przygotowania: `exp-1794586891284-5915cc94`, `exp-1794586992993-972bed5e`, a także wcześniejsze `exp-1794584443113-f0144051` i `exp-1794585013430-9a9bdb35`. Zachowano konfiguracje, wszystkie dostępne dzienniki/obrazy, pliki końcowe, `shared_prefs`, log aplikacji i potwierdzenia operacji. Oryginałów nie modyfikowano.

Odczyt autoryzowanym ADB/run-as: Samsung SM-A125F, serial `R58R346GZYW`. **1044/1044 pliki** mają tę samą sumę SHA przed kopiowaniem, w kopii i po odczycie. Dotyczy to także preferencji, metadanych i dzienników nowych rekordów. Zabezpieczenie zakończono `2026-11-13T16:57:40.521294+00:00`. Szczegóły: `device_readonly/copy-integrity.json`, pliki `before-*`, `after-*` i `commands.json`.

Pierwszy transfer TAR przekroczył limit 180 s. Pozostawiono go jako `private-snapshot.tar`; kompletne elementy zostały odczytane, a 64 brakujące pliki zabezpieczono osobnym `private-snapshot-tail.tar`. Nie uznano urwanego TAR za kompletny. Złożona kopia plików ma potwierdzone wszystkie 1044 sumy; nie ma potrzeby używania urwanego transferu do analizy.

Istniejący publiczny eksport skopiowano bez ponownej finalizacji:

`C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f6_post_stop_session_creation_audit_20261113_v1\device_readonly\public_exports\alpr_session_20261113_172230.alprsession`

- Rozmiar: **138 070 379 B**.
- SHA-256: **`2c27e2dbaa1ebc23b52b39fee7d5b6ab158903c1cca788fd5944c94642e0a7f3`**.
- Źródło: `/storage/emulated/0/Download/ALPR_EKSPERYMENTY/E-FINAL-ALPR-01/02_sesje/final_niekompletna/alpr_session_20261113_172230.alprsession`, potwierdzone rzeczywistym URI dokumentu i odczytem pliku.
- Prywatne `final/exp-1794585411433-6305e1b2.alprsession` ma identyczne bajty/SHA. Rozmiary/mtime źródeł były stabilne.
- ZIP: 986 wpisów, brak duplikatów, CRC PASS; 985/985 sum manifestu PASS, brak nieujętych plików. `session_id` odpowiada głównej sesji, stan pozostaje PARTIAL.

Pełny odczyt logcat: `device_readonly/logcat-readonly.txt` (61 098 003 B); wyciąg czasowy: `audit/real-post-stop-logcat-window.txt`. Dostępność poszczególnych buforów jest nierówna: zachowały się zdarzenia okna systemowego, lecz nie pełne logi aplikacyjne/camera-ready z analizowanej minuty. Brak wpisu o kliknięciu nie jest dowodem jego niewystąpienia.

## Rzeczywista wersja telefonu

Pakiet: `com.example.alpr_v1`, versionCode 1/versionName 1.0, build UTC **2026-11-13T15:39:46.991538200Z**. SHA bazowej APK: `70ecfa88902d2c48d73ee0ed12f6f68bb288ebba1975dac88a94f421eea703f0`, 83 201 033 B. Ta sama tożsamość jest zamrożona w głównej sesji. Kopia: `device_readonly/installed-base.apk`; identyfikacja i podpis: `audit/installed-apk-signature.txt`. Nie znaleziono nakładki `code_cache/.overlay`.

To inny plik niż APK `d6809b…` poprzedniej zamkniętej dostawy. Nie przypisano objawów wyłącznie na podstawie HEAD. W `classes5.dex` rzeczywistej APK potwierdzono niestrzeżoną końcową gałąź `ensureExperimentPrepared()` oraz wywołanie tej metody przed sprawdzeniem oczekującego START w `refreshExperimentStartReadiness()`. Odczyt/disassembly istniejącym SDK: `audit/installed-code/readiness-methods.txt`.

## Pochodzenie nowych rekordów

Czasy poniżej: lokalne Europe/Warsaw, UTC+01:00. Szczegóły: `audit/record-inventory.json`, `audit/operation-timeline.json`, zabezpieczony log aplikacji.

| Rekord | Fakty zapisane na telefonie | Klasyfikacja i granica dowodu |
|---|---|---|
| `exp-1794586891284-5915cc94`, 17:21:31.284 | PREPARING zakończone 17:21:32.285; później ERROR, `preparation_failed`, `start_cancelled_before_camera`; brak started_at/finished_at | Trwałe przygotowanie, później odrzucone/anulowane przed kamerą. Utworzone 1,454 s po terminalnej finalizacji głównej sesji. `session.json` ma mtime 17:22:30, zgodne z otwarciem SAF o 17:22:30.534 i onPause. Dokładny callback utworzenia/odrzucenia nie został historycznie zapisany. |
| `exp-1794586992993-972bed5e`, 17:23:12.993 | PREPARED; operacja przygotowania zakończona 17:23:13.514; brak START i zakończenia | Następne przygotowanie. System rejestruje powrót z DocumentsUI około 17:23:09.728–17:23:10.476; automatyczna gotowość/onResume stanowi wykazaną w kodzie drogę. Konkretny stos callbacku: NIEUSTALONY. |
| Przygotowania 16:40:43.113 i 16:50:13.430 | ERROR, `start_cancelled_before_camera`; brak START | Zachowane jako kontekst. Historyczny inicjator każdego z nich: NIEUSTALONY; nie są nowymi pomiarami MT/MZ. |

Każde z czterech przygotowań ma zero prób i zero zdarzeń F6. Dwa wskazane rekordy mają po dwa rekordy runtime `ALLOCATED` (MT/MZ), `invocation_count_at_observation=0`, `invoke_observed=false`. Są to inicjalizacje interpreterów, nie wykonania detekcji. Brak dzienników ról/scen i brak czasu START. Etykieta historii „NIE ROZPOCZĘTO” odpowiada tym danym; nie należy zamieniać ich w wykonania z zerem detekcji.

Wszystkie pięć zabezpieczonych rekordów ma tego samego właściciela procesu. Nie ma dowodu, że te dwa nowe identyfikatory utworzył restart lub recovery. Historyczne wystąpienie komunikatu „START zablokowany” ma **NIEUSTALONĄ dokładną przyczynę**: toast występuje w kilku ścieżkach, a trwałe dane nie zapisują jego pełnego kontekstu. Nie przypisano go arbitralnie do deadlocku ani wykonanej inferencji.

Główna sesja i oba późniejsze przygotowania zachowują F6, kategorię ROI, `r0_full_frame`, MP OFF, MT/MZ FP32 CPU po 2 wątki oraz pole `[150,523,778,786]` w pikselach pełnego źródła 960×1280. Fizyczne parametry źródła są zgodne; techniczne binding/view zmieniły się z 1/5 na 3/6. Digest kontekstu cropu może się przy tym różnić, bez zmiany fizycznego pola. Nie wyłączono żadnego porównania geometrii. Zestawienie rzeczywistych snapshotów: `audit/source-config-timeline.json`.

## Przyczyna w kodzie i poprawka

Przed zmianą pętla gotowości co 250 ms i wznowienie ekranu wywoływały `ensureExperimentPrepared()`. Przy poprawnym wejściu i `researchSessions.ready()` metoda wywoływała `prepareCameraStart(false)` bez oczekującego START. Worker wywoływał `ExperimentSession.prepare()` (nowy session_id), następnie `ResearchSessionViewModel.prepare()`/`ResearchSessionStore.prepare()` (trwały katalog), a później inicjalizował modele. Zakończenie finalizacji zwalniało gotowość i umożliwiało takie przygotowanie. Powrót z SAF/settings mógł je powtórzyć.

Historia/recovery z poprzedniej poprawki nie ma tu nowej fabryki session_id: startup robi INVENTORY, historia odczytuje katalog, recovery wybranej sesji zachowuje jej ID i oryginał. Nowa historia ujawniła przygotowania, które wcześniej były słabo widoczne. Otwarcie/zamknięcie ekranu może pośrednio uruchamiać wadliwą gotowość MainActivity. Nie usunięto widoczności historii ani żadnego starego rekordu.

Test przed poprawką odtworzył błąd: `Readiness without START must not allocate a durable session expected:<[]> but was:<[exp-1794589259040-8965ef13]>`. Dowody: `audit/baseline-reproduction.log`, `audit/baseline-results/`. To test hostowy rzeczywistej ścieżki Activity/Store, z syntetycznym IO kamery/modeli.

Minimalna poprawka produkcji dotyczy tylko `MainActivity.java` i `ExperimentStartRequest.java`:

- Gotowość może utrzymywać podgląd, lecz trwałe przygotowanie wymaga oczekującego START i zakończonej obsługi uprawnienia.
- Odczyt `pendingGeneration()` nie tworzy żądania. Worker sprawdza generację i istniejący ticket przygotowania przed konfiguracją, bezpośrednio przed alokacją sesji, po przygotowaniu i w callbacku UI.
- Anulowanie nie uruchamia później nowego/starego pomiaru. Jeżeli operator zdążył rzeczywiście zlecić przygotowanie i katalog już powstał, pozostaje uczciwy zapis anulowania, bez kasowania danych.
- Nieaktualny błąd nie pokazuje toasta blokującego nowsze żądanie. Anulowanie ma odrębną ścieżkę terminalną.
- Stan bezczynny pokazuje „START przygotuje modele i zapis”, zamiast deklarować pracę, która nie trwa. Log `RESEARCH_START` łączy przyszłe żądanie, generację, ticket i session_id przy zmianach stanu, bez wpisów co klatkę.

Zachowano świadomie kliknięty START oczekujący na kamerę lub finalizację. To inne zdarzenie niż gotowość bez żądania. STOP/anulowanie kończy to uprawnienie. Testy wcześniejszej inspekcji prewarmingu zostały dostosowane do jawnego żądania i istniejącej bramki świeżości; nadal sprawdzają geometrię i snapshot przed dopuszczeniem inferencji.

## Dlaczego główna sesja jest PARTIAL

START: 16:59:17.489, STOP: 17:19:44.353; czas monotoniczny **1226,864406304 s**. `completion_reason=manual`. Zmiana tego stanu nie jest częścią poprawki. Dane nie zostały przeprojektowane ani ponownie sfinalizowane.

| Dowód | Liczba / wynik |
|---|---|
| MT_STARTED / MT_RETURNED / MT_DECODED | 184 / 184 / 184 |
| Rekordy ról MT; brakujące/zdublowane identyfikatory | 184; 0 / 0 |
| COMPLETE / INCOMPLETE dowodu ról | 135 / 49 |
| Postprocess COMPLETED / NOT_REACHED | 135 / 49 |
| Stale/cancelled | 51, wszystkie `scene_superseded`; 49 INCOMPLETE + 2 COMPLETE |
| Kompletne, nieunieważnione rekordy ról | 133; nie jest to mianownik oceny fotografii |
| Powiązania FINAL / unresolved attempt links | 97 / 5; pięć unresolved należy do czterech anulowanych NOT_REACHED |
| MZ_STARTED / MZ_RETURNED | 97 / 97; brak niesparowanych wywołań |
| Próby / statusy MZ | 200; READ 84, NO_CHARACTERS 11, NOT_RUN 105 |
| Rzeczywiście wykonane MZ ze statusem NOT_RUN po unieważnieniu | 2 (`a00000007`, `a00000058`); nie wolno uznać wszystkich 105 za niewykonane |
| Klatki TV / source+crop WRITTEN / brak obrazów | 236 / 472 / 0 |
| Sceny expected/written/missing | 184 / 184 / 0; scene_evidence_complete=true |
| Zdarzenia F6 expected/written/persisted | 1402 / 1402 / 1402; brak luk sekwencji |
| Zadeklarowane straty kolejki/lifecycle/projekcji/integrity | 0; last_write_error pusty |
| Konflikty SourceStamp MT→role, START→RETURN MT/MZ i rodzica MT dla MZ | 0 w wykonanym audycie; pełny walidator grafu uruchomiony w regresji |

Ostatni zwrot MT należącego do unieważnionych wywołań nastąpił 992,222052598 s od START, **234,642353706 s przed STOP**. Nie są to wyłącznie zadania przerwane ręcznym STOP. Nie ustalano przypisania do exposure_id ani fotografii przez OCR, kolejność nazw lub numer sceny.

`Final180Evidence.project()` uznaje role za kompletne dopiero przy zgodnej liczbie wywołań i braku INCOMPLETE/stale/error. Tutaj liczba jest zgodna, ale 51 rekordów nie przechodzi warunku. Ta sama projekcja uznaje 51 klatek stale/cancelled za niekompletne dowody wejścia. Stąd `mt_detection_roles_complete=false`, `tv_bbox_input_evidence.complete=false` i `f6_evidence.complete=false`. Surowe pole ma nazwę `f6_evidence.complete`; określenie `f6_evidence_complete` z odbioru nie jest nowym polem dodanym do telefonu.

`tv_bbox_input_evidence.pending_loss_unknown=true` jest w tym kodzie również skutkiem stale/cancelled; nie dowodzi utraty obrazów. Nadrzędne `f6_evidence.pending_loss_unknown=false`, `unlocated_loss_possible=false`, obrazy/SHA i liczniki są zachowane. Brak dowodu kompletnego FINAL dla anulowanego wywołania pozostaje brakiem; nie dopisano pustych wyników. Archiwum jest czytelne i wartościowe, lecz pełne pokrycie 176 ekspozycji wymaga analizy Desktopu, poza tym zadaniem.

Szczegóły i lista każdego problematycznego MT: `audit/main-session-completeness.json`. Analiza powtarzalna wyłącznie na kopiach: `audit/analyze_record_origins.py` i `tools/audit_f6_state_exports.py`.

## Testy i ograniczenia

Końcowa pełna regresja JVM: **1416/1416 PASS**, 0 FAIL/ERROR/SKIP; unikalne przypadki, jeden przebieg (`audit/final_results/`, `audit/final-test-summary.json`). Kompilacja instrumentacji i budowa debug offline: **PASS** (`audit/full-regression-and-build.log`). Instrumentacja na urządzeniu: **NOT_RUN**.

Przed poprawką: 1/1 FAIL odtwarzający błąd. Pierwszy ukierunkowany przebieg po zmianie: 14 przypadków, 1 FAIL — test szybkiego zastąpienia żądania nie symulował dostępności źródła po STOP. Poprawiono wyłącznie IO kamery w teście; kod produkcji nie został dla tego wyniku zmieniony. Kolejny przebieg: **55/55 PASS**, osobno, bez dodawania do pełnej regresji. Wszystkie logi/XML FAIL pozostają w dostawie.

Komenda: `.\gradlew.bat --offline -I app/build/f6_post_stop_session_creation_audit_20261113_v1/audit/regression.gradle testDebugUnitTest compileDebugAndroidTestJavaWithJavac assembleDebug --console=plain`. Zastosowano istniejący JBR Android Studio, limit JVM 1024 MiB i odnowienie forka co 25 klas. Zmienne ścieżek i pełna komenda: `audit/build-command.ps1`.

Nowe testy przechodzą przez rzeczywiste MainActivity, kontroler gotowości, executor, zapis ResearchSessionStore, finalizator oraz historię. Obejmują 60 aktualizacji bez START; jeden START→STOP→finalizacja→dwa otwarcia historii→brak nowych ID; odrzucony START i nowy ViewModel/inwentaryzację; anulowanie przed uruchomieniem workera; starszy callback po zastąpieniu żądania. IO kamery/modeli jest syntetyczne, a źródło w tych testach pozostaje dostępne po STOP. To nie odbiór CameraX/A12.

Osobny test odczytuje zabezpieczone rzeczywiste archiwum nowej sesji produkcyjnym walidatorem i istniejącą weryfikacją SHA, zachowując PARTIAL/anulowania. Dawne dwie kopie 12:49:57, wcześniejszy recovery, zaakceptowane MP v1 i wadliwe stare archiwum mają oddzielne regresje. Nie sumuje się nachodzących przebiegów. Nie generowano ponownie 4100 klatek.

Mały wynik scenariusza START/STOP: `producer_regression/synthetic-main-start-stop-no-frame-PARTIAL.alprsession`, 28454 B, SHA-256 `cb2cf2cf19432c6951af48a3991313633b9dea4b9dce94a41a86961f28643e57`. Powstał w produkcyjnej ścieżce Activity→Store→eksport, zawiera pełny snapshot i rzeczywisty w tym teście STOP przed pierwszą klatką; nie udaje wykonanego MT. Oznaczony w metadanych TEST_ONLY/SYNTHETIC. Nie zastępuje rzeczywistych danych 20-minutowej sesji. Producent/format eksportu nie zmienił się, więc nie wydaje się nowej paczki 4100 ani nowej wersji kontraktu dla Desktopu.

## Nowa APK, diff i ochrona

APK: `C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f6_post_stop_session_creation_audit_20261113_v1\alpr-f6-post-stop-session-creation-debug.apk`.

SHA-256: **`00bb941662302b760a3516f1339adf9169da510f68889933db982d70b64a7b01`**, 86607564 B. Pakiet `com.example.alpr_v1`, versionCode 1/versionName 1.0, build UTC **2026-11-13T17:11:41.157016900Z**.

Podpis: `f3385991af6f601faf4df59697d30f143101a71ee988035745027d3e85aa04a2`; weryfikacja v2 PASS, ten sam certyfikat co zabezpieczona rzeczywista APK telefonu. Wspólne zasoby/biblioteki natywne są identyczne; zwykły assembleDebug zawiera również istniejące biblioteki armeabi-v7a, jak poprzednia dostawa debug. Nie dodano zależności ani modeli. Szczegóły: `audit/apk-identity.json`, `audit/apk-assets-native-comparison.json`.

Stan źródeł: main / `f7943a7d7334afd1e4188ef9860b2126331926c8`, zastane zmiany robocze zachowane. `audit/worktree-before.patch`, `source-before/`, `audit/source-before-sha256.json` zabezpieczają stan wejściowy. Dokładna lista zmian tej kontynuacji i minimalny diff: `audit/changed-files.json`, `audit/task-only.patch`. Dwa pliki produkcyjne; pozostałe zmiany to testy i ten raport.

Nie zmieniono detektora, schedulera inferencji, NMS, konsensusu, modeli, progów, liczby wątków, kalibracji, rozdzielczości, czasu ekspozycji ani reguły oceny. Zmienił się moment inicjalizacji modeli: po świadomym START, zamiast automatycznie podczas bezczynnej gotowości. Pomiar sesji nadal zaczyna się po dotychczasowej bramce przygotowania. Nie deklarujemy identycznej latencji od kliknięcia lub pomiarowej porównywalności APK bez odbioru.

Serializacja F6/F5A, SourceStamp/frame_key/MT/FINAL/MZ, CROP_ONLY_TRANSLATION i H_TV=NOT_APPLIED pozostają bez zmian. Wcześniejszych kalibracji, sesji, modeli i eksportów telefonu nie zapisywano. Kontrola poprzednich dostaw: **PASS — 3536 plików, 0 naruszeń**; brak niepowiązanych zmian źródeł (`audit/protected-deliveries-check.json`).

## Aktualizacja i odbiór operatora

Nie instalowano APK. Zgodny pakiet i podpis pozwalają operatorowi wykonać aktualizację bez odinstalowania i czyszczenia danych. Stara APK telefonu nie zawiera tej poprawki.

Na nieruchomej planszy TEST_ONLY/T, bez nowego pełnego FINAL: sprawdzić zachowane ustawienia i pole F6; otworzyć/zamknąć historię przed START — bez nowych wpisów; jeden START, krótki zapis, STOP, zaczekać na terminalny wynik operacji; otworzyć historię kilka razy i odczekać — dokładnie jeden nowy pomiar. Wyeksportować jego kopię, ponownie otworzyć historię i uruchomić aplikację ponownie — bez kolejnego rekordu. Przy rzeczywiście odrzuconym START nie może powstać nowy pomiar; nie należy mylić go z jawnie oczekującym START, który przycisk Stop anuluje. Zachować log RESEARCH_START, session_id i pełny błąd, jeśli próba się nie powiedzie. Starych „NIE ROZPOCZĘTO” nie usuwać.

| Status | Wynik |
|---|---|
| ANDROID_HOST_TESTS | PASS — pełny nowy przebieg; kompilacja instrumentacji/debug offline |
| REAL_FILES_RECOVERY | PASS — stabilne kopie 1044 plików, rzeczywisty eksport CRC/SHA; stan PARTIAL zachowany |
| SESSION_FINALIZATION_EXPORT | PASS dla testów hostowych i odczytu rzeczywistego starego archiwum; nowa APK na A12 NOT_VERIFIED |
| UI_A12_LIFECYCLE | NOT_VERIFIED — nie instalowano ani nie uruchamiano nowej APK |
| A12_TV_TECHNICAL | NOT_VERIFIED w zakresie nowej poprawki |
| EXPERIMENT_START | NO_GO do odbioru nowego cyklu na A12; brak deklaracji pełnego GO FINAL |

READY.json powstaje na końcu, po testach, kontroli sum i spakowaniu dowodów. Nie oznacza GO eksperymentu ani odbioru fizycznego A12/TV.
