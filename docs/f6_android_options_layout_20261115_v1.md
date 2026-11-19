# Android — uporządkowanie Opcji i czytelny stan zapisu

Data: 15.11.2026. Dostawa: `f6_options_layout_20261115_v1`.

## Wynik i granica dostawy

Wdrożono zatwierdzony układ pięciu zwijanych sekcji Opcji, osobne przyciski TV / pole F6 / AF na ekranie głównym oraz prezentację rzeczywistych etapów zapisu z blokadą działań kolidujących z operacją. Zbudowano nową APK debug offline. **Nie instalowano APK, nie używano ADB i nie uruchamiano pomiaru na telefonie.**

To poprawka obsługi aplikacji. **Nie jest naprawą wcześniejszego zabicia procesu przez LMKD ani dowodem stabilności przez cały FINAL176.** Stan `EXPERIMENT_START` pozostaje `NO_GO` do wykonania pozostałych odbiorów opisanych w raporcie `docs/f3_android_final176_awaria_i_zapobieganie_20261114_v1.md`. Tamten raport oraz zabezpieczona sesja nie zostały zmienione.

Repozytorium: `C:\Users\48572\AndroidStudioProjects\ALPR_v1`.
Gałąź: `main`. HEAD przed i po pracy: `f7943a7d7334afd1e4188ef9860b2126331926c8`.
Zastano liczne lokalne zmiany F6/F3 i zachowano je. Nie wykonywano commit/push, checkout/reset/clean/stash, instalacji zależności ani operacji w repozytorium Desktopu.

## Układ i zachowanie

| Sekcja Opcji | Zawartość |
|---|---|
| Tryb pracy i strategia | Tryb eksperymentalny, profil zapisu F6, kategoria badania, strategia R0/R1/R2, wejście przetwarzania |
| Kamera i kalibracja | Istniejący wybór rozdzielczości i odczyt strumieni, kalibracja TV, osobne pole F6, kalkulator odległości |
| Modele i wykonanie | Istniejące modele, import, wybór pipeline’u, ustawienia wykonania MP/MT/MZ i CPU |
| Sesja i zapis | Wariant, seria, scenariusz, powtórzenie, notatka, pojemność galerii i lokalizacja zapisu |
| Zaawansowane | Profil zwykłej analizy, obsługa scen, dynamiczny MT, eksperymentalne Lock i Autozoom |

Jednocześnie rozwinięta jest najwyżej jedna sekcja. Pierwsze otwarcie rozwija „Tryb pracy i strategia”; ponowne naciśnięcie nagłówka może zwinąć także ją. Odtworzenie Activity zachowuje wybraną sekcję. Nagłówki pokazują krótkie podsumowania bieżących wartości. Rozwijanie sekcji nie zapisuje preferencji i nie zwiększa `settings_revision`.

F6 pozostaje profilem zapisu, a R0/R1/R2 strategią inferencji. Nie przełączono kategorii na End-to-end i nie zmieniono znaczenia wariantu `R0_FINAL`. Zwykła analiza ma odrębne podsumowanie. Dotychczasowe klucze preferencji, listenery zapisujące ustawienia oraz reguły dostępności modeli pozostały zachowane.

Cztery istniejące przełączniki mają widoczny tekst **„Włączone” / „Wyłączone”**, wyliczany z ich rzeczywistego `isChecked()`, oraz opis stanu dla dostępności. Stan zaznaczenia jest niezależny od `isEnabled()`: zablokowana kontrolka nie jest przedstawiana jako wyłączona funkcja. Stan tekstowy znajduje się pod przełącznikiem, dzięki czemu nie ściska długiej etykiety przy powiększonej czcionce. Opcje otrzymały lokalne nadpisanie kontrastu; przyciski kalibracji korzystają z istniejących kolorów stanu zablokowania.

Na ekranie głównym, w trybie eksperymentalnym, znajduje się wspólny blok:

**Kalibracja TV → Pole F6 → Ustaw AF na TV.**

Przyciski uruchamiają dotychczasowy `TvSetupController` w dwóch osobnych trybach oraz istniejącą obsługę AF. Nie utworzono drugiego edytora ani magazynu kalibracji. Dotychczasowe bramki kategorii badania, źródła kamery i F6 nadal obowiązują. TV i pole fotografii pozostają osobnymi zapisami. Przycisk AF nadal pokazuje prawdziwe oczekiwanie, sukces, porażkę lub zwolnienie; ta dostawa nie zmienia działania AF.

## Etapy zapisu i blokady

Pas operacji korzysta z istniejącego `ResearchOperationState`, jego `operation_id`, `session_id` oraz trwałych potwierdzeń. Pokazuje przygotowanie, domykanie zadań, finalizację dzienników, budowę i sprawdzanie archiwum, kopiowanie dokumentu, jego odczyt kontrolny oraz recovery na kopii. „Szczegóły” otwierają pełny zapis operacji, identyfikatory, przyczynę błędu i potwierdzenie dokumentu.

| Sytuacja | Prezentacja |
|---|---|
| Brak znanego mianownika pracy | Nazwa etapu i wskaźnik nieokreślony; bez wymyślonego procentu |
| Kopiowanie / odczyt dokumentu z `done` i `total` | Procent danego etapu oraz rzeczywiste liczby bajtów |
| Udana finalizacja archiwum | „Archiwum sesji gotowe” — nie oznacza jeszcze zapisu wybranego dokumentu |
| Udany zapis i zgodny odczyt SHA-256 | „Eksport zapisany i sprawdzony” |
| Dokument zachowany bez potwierdzenia odczytu | Osobny komunikat braku potwierdzenia, bez deklaracji weryfikacji |
| Błąd, anulowanie, przerwanie | Osobne stany; pełna przyczyna w szczegółach |

Przy zmianie etapu procent może zacząć się od zera: dotyczy kopiowania albo odczytu, a nie szacowanego procentu całej sesji. Rutynowy odczyt historii `INVENTORY` nie zastępuje ostatniego użytecznego potwierdzenia zapisu. Spóźniony callback nie może zakończyć operacji z innym ID.

Podczas zapisu zablokowane są nowy START, Opcje, TV/F6, AF, przełączanie podglądu i timera oraz zmiany AZ, sceny i akwizycji. Nadal dostępne są historia sesji, szczegóły operacji i odczyt stanu. STOP aktywnej analizy oraz anulowanie własnego oczekującego START pozostają obsługiwane. Otwarcie historii nie tworzy sesji; wybór kolejnego dokumentu lub recovery jest blokowany podczas kolidującej operacji. Bezpośrednie otwarcie SettingsActivity podczas zapisu również nie modyfikuje konfiguracji.

Pas operacji nie jest modalną nakładką na cały ekran. Nie zmienia wymiarów widoku kamery między kolejnymi etapami. Nie resetuje `busy` na podstawie upływu czasu. Ukończenie lub błąd rozlicza właściciel operacji. Po odtworzeniu procesu nieukończony potwierdzony zapis operacji pozostaje przerwany, nie staje się fałszywie udanym eksportem.

## Faktyczny zakres plików i ochrona

Zmodyfikowano 10 zastanych plików:

```text
app/src/main/java/com/example/alpr_v1/MainActivity.java
app/src/main/java/com/example/alpr_v1/SettingsActivity.java
app/src/main/java/com/example/alpr_v1/experiment/ResearchOperationState.java
app/src/main/java/com/example/alpr_v1/experiment/ResearchSessionViewModel.java
app/src/main/java/com/example/alpr_v1/ui/LivePresentationController.java
app/src/main/java/com/example/alpr_v1/ui/TvSetupController.java
app/src/main/res/layout/activity_main.xml
app/src/main/res/layout/activity_settings.xml
app/src/test/java/com/example/alpr_v1/ui/F6ConfigurationUiTest.java
app/src/test/java/com/example/alpr_v1/ui/F6PostStopSessionCreationTest.java
```

Dodano 6 plików kodu/zasobów oraz ten raport:

```text
app/src/main/java/com/example/alpr_v1/ui/OptionsSectionsController.java
app/src/main/java/com/example/alpr_v1/ui/ResearchOperationPresentation.java
app/src/main/res/drawable/ic_options_expand.xml
app/src/main/res/drawable/ic_options_collapse.xml
app/src/main/res/values/options_layout_strings.xml
app/src/main/res/values/options_layout_styles.xml
docs/f6_android_options_layout_20261115_v1.md
```

`ResearchSessionViewModel` zmieniono wyłącznie w bramkach gotowości i wzajemnego wykluczania przygotowania, recovery oraz zapisu dokumentu. Nie zmieniono serializacji wyników. W `TvSetupController` zmiany dotyczą opisu/przycisków ekranu głównego i ich prezentacji; algorytm geometrii, precyzyjne sterowanie i zatwierdzanie narożników pozostały bez zmian.

Przed edycją zabezpieczono stan Git, diff oraz SHA 1019 istniejących plików kodu, testów i dokumentacji. **1009 pozostało identycznych bajtowo**, a pozostałe 10 odpowiada powyższej liście. Zachowano wszystkie oryginalne ID kontrolek: główny ekran 74/74, Opcje 71/71; końcowe ID są unikalne (odpowiednio 81 i 89). Szczegóły: `audit/worktree_before.json`, `audit/protected-source-check.json`, `audit/layout-ids.json`, `audit/task_only.patch` i `audit/source_sha256.json` w katalogu dostawy.

Nie zmieniono modeli, ich ustawień, liczby wątków, NMS, schedulera, konsensusu, timera, CameraX, transformacji ani formatu F5A/F6. Nie edytowano sesji, GT, PC, zapisów telefonu ani wcześniejszych dostaw. Nie wygenerowano ponownie paczki 4100 klatek. Nie ma nowego kontraktu archiwum ani obowiązku zmiany importera Desktopu.

Kontrolne SHA-256 niezmienionych elementów producenta:

| Plik | SHA-256 |
|---|---|
| `ResearchSessionStore.java` | `f31ef6e097ac646a34557a4e2f63297a8528a31adaa38e9f7bdf058e3dcdcc9f` |
| `TvBboxEvidence.java` | `2c6f317557ee77656ba2a671d9ad5310a967bf24f40aa1924b55684241481d87` |
| `ResearchDocumentExport.java` | `f678ed156ab598768b7745567d34ec887c2bfbd3a9534f2ded7ecc17d488e309` |

Potwierdzono również zachowanie kopii `raw-session-original.tar` z wcześniejszej awarii: 345300480 B, SHA-256 `e8e2070c9b5056851d2c7211c8e9b2de7f57775de2219d55e91538de7edefc1b`. Raport awarii nadal ma SHA-256 `b6801d9aff06f30d6a3328f458a753dc53f188bbad8016d6aa053492943a3130`. Pozostałe kontrolowane artefakty MP v1 są zapisane w `audit/protected-artifacts.json`.

## Wykonane testy i dowody

Wykonano trzy celowane grupy JVM/Robolectric z rzeczywistymi Activity, layoutami i istniejącą ścieżką producenta sesji. Kamera i IO modeli w fixture są syntetyczne. Nie uruchamiano pełnej wielotysięcznej regresji.

| Grupa | Unikatowe przypadki | Wynik i zakres |
|---|---:|---|
| `F6ConfigurationUiTest` | 6 | PASS: F6/R0 i kategoria, pięć sekcji bez zapisu preferencji, tekstowe stany przełączników i kontrast, odtworzenie Activity, mały ekran i font 150% |
| `MainUiSessionLabelsTest` | 9 | PASS: dotychczasowe etykiety i bramki zwykłej analizy/F5A, zamrożone wejście, STOP i czytelność stanu zablokowania |
| `F6PostStopSessionCreationTest` | 15 | PASS: istniejące edytory, AF/lifecycle, START/STOP, realna kolejka finalizacji, historia/restart bez nowych sesji, blokady eksportu, ID callbacków, terminalne potwierdzenia i przerwanie procesu |

**30 unikatowych przypadków PASS; 0 końcowych FAIL/ERROR/SKIP.** Nie sumowano ponownych wykonań. Po wspólnym przebiegu 30/30 powtórzono 24 przypadki głównego ekranu dla ostatnich blokad, a następnie 8 przypadków obejmujących ostatnie zmiany kontrastu i układu. Każdy przypadek i źródłowy XML/log są wskazane w `tests-summary.json`.

Próba kolejki finalizacji wykonuje rzeczywisty START → STOP → oczekiwanie finalizatora → archiwum → ponowne otwarcie historii. Sprawdza, że w oczekiwaniu START jest zablokowany, historia dostępna, a po ukończeniu nie powstaje kolejna sesja. Syntetyczna próba bez klatek pozostaje prawidłowo `PARTIAL`; nie dopisano fikcyjnych wyników ani nie zmieniono jej na COMPLETED. Eksport jest sprawdzany dotychczasowym `F6ProducerContractVerifier`.

Kontrole układu obejmują 720×1600 px przy gęstości 2, font 150% dla Opcji, minimalne rozmiary kontrolek oraz symulowane bezpieczne wcięcia systemowe po 48 px u góry i u dołu. Sprawdzono niezmienność granic `camera_preview` po pokazaniu operacji i pozostawienie historii nad dolnym paskiem.

Zachowano logi pośrednich nieudanych przebiegów. Dotyczyły one kontroli widoczności okna w niedołączonej fixture hostowej oraz niepełnej symulacji wcięć systemowych. Fixture poprawiono tak, aby mierzyła właściwy układ i uwzględniała także `getInsetsIgnoringVisibility`; nie osłabiano walidacji kalibracji ani archiwów. Podczas przeglądu renderów poprawiono również rzeczywisty kontrast etykiet i zbyt ciasne poziome wiersze przełączników.

Komendy, uruchomione z lokalnym JBR i `--offline`:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --offline :app:testDebugUnitTest --tests com.example.alpr_v1.ui.F6ConfigurationUiTest --tests com.example.alpr_v1.ui.MainUiSessionLabelsTest --tests com.example.alpr_v1.ui.F6PostStopSessionCreationTest --console=plain
.\gradlew.bat --offline :app:testDebugUnitTest --tests com.example.alpr_v1.ui.MainUiSessionLabelsTest --tests com.example.alpr_v1.ui.F6PostStopSessionCreationTest --console=plain
.\gradlew.bat --offline :app:testDebugUnitTest --tests com.example.alpr_v1.ui.F6ConfigurationUiTest --tests com.example.alpr_v1.ui.F6PostStopSessionCreationTest.operationBannerDoesNotResizeCameraOrHideHistory --tests com.example.alpr_v1.ui.F6PostStopSessionCreationTest.writeProgressLocksNewWorkButKeepsHistoryAndFullDetails --console=plain
.\gradlew.bat --offline :app:assembleDebug :app:compileDebugAndroidTestJavaWithJavac --console=plain
git diff --check
```

Logi końcowe: `tests-final-confirmed.log`, `tests-main-final.log`, `tests-visual-final.log`, `build-final.log`, `apk-signature.log` oraz `audit/git-diff-check.log`. Budowa debug, kompilacja instrumentacji i kontrola diff: PASS. **Instrumentacji nie wykonano na urządzeniu.** Ostrzeżenia o przestarzałych API/Gradle i natywnym dostępie JBR pozostały ostrzeżeniami, nie zostały ukryte.

Rendery rzeczywistych layoutów przez Robolectric znajdują się w `host-ui/options-large-text-0.png` … `options-large-text-4.png`, `host-ui/main-preparation-host.png` i `host-ui/main-export-progress-host.png`. Są to **rendery hostowe na danych syntetycznych, nie screenshoty A12**. Czarny obszar kamery nie przedstawia TV; animowanego rysowania wskaźnika w prawdziwym oknie telefonu ten test nie potwierdza.

## APK i katalog dostawy

```text
C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f6_options_layout_20261115_v1\
```

APK:

```text
C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f6_options_layout_20261115_v1\alpr-f6-options-layout-debug.apk
```

Rozmiar: **86672614 B**. SHA-256:

```text
895b838cc9fb524f152bb1621bc9e5fdab19cd3b38774352a476dfd9688ac37c
```

Tożsamość: `com.example.alpr_v1`, debug, `versionCode=1`, `versionName=1.0`, `GIT_DIRTY=true`, build UTC `2026-11-15T13:41:45.785383100Z`. Metadane znajdują się w `apk-identity.json`. Sprawdzono pełny odczyt ZIP/CRC APK oraz podpis v2 przez lokalny `apksigner verify`.

Certyfikat SHA-256:

```text
f3385991af6f601faf4df59697d30f143101a71ee988035745027d3e85aa04a2
```

Jest identyczny z certyfikatem wcześniejszej dostawy `f3_android_camera_partial_20261114_v1/alpr-f3-camera-partial-debug.apk`. Nie odczytywano w tej pracy wersji ani podpisu obecnie zainstalowanej aplikacji; zgodność porównania dotyczy lokalnych dostaw. Nowa APK nie została zainstalowana.

`changed-sources.zip` zawiera tylko zakres bieżących zmian oraz przyrostowy patch; nie jest pełnym repozytorium. Jego SHA-256: `c0c981cb8c4845ba37433b93d0fb7288af16277eda58bdd084ea4e8fb4c2d770`. Sumy APK, raportu, kodu i dowodów końcowych są w `SHA256SUMS.txt`. `READY.json` oznacza ukończenie pakowania, nie odbiór stanowiska ani zgodę na FINAL176.

## Punkt wznowienia dla operatora

1. W odpowiednim momencie wykonaj zwykłe Run ▶ z aktualnych źródeł albo aktualizację wskazaną APK. Bez odinstalowywania, clear data i Apply Changes. Nie trzeba usuwać wcześniejszych sesji ani modeli.
2. Otwórz Opcje: sprawdź pięć sekcji, rzeczywiste wartości F6/R0/MP, wykonanie MT/MZ, serię i katalog. Rozwiń inną sekcję, wróć do podglądu i ponownie otwórz Opcje.
3. Na nieruchomej planszy technicznej sprawdź kolejno „Kalibracja TV”, „Pole F6”, a przed uzgodnioną próbą „Ustaw AF na TV”. Samo otwarcie edytora nie zapisuje kalibracji. Gdy fizyczne źródło jest niezgodne, nadal obowiązuje istniejąca kontrola geometrii.
4. Po osobno uzgodnionym krótkim START/STOP obserwuj etapy, otwórz szczegóły i historię. Podczas zapisu ustawienia i nowy START mają być zablokowane. Po zakończeniu archiwum wybierz dokument i poczekaj na wynik jego weryfikacji.
5. Nie uruchamiaj FINAL176 na podstawie tego odbioru UI. Wcześniejsze wymagania dotyczące zasobów, normalnego zakończenia dłuższej próby oraz wspólnego A12/TV/Desktop pozostają otwarte.

| Status | Wynik |
|---|---|
| `ANDROID_HOST_TESTS` | PASS w opisanym, celowanym zakresie |
| `SESSION_FINALIZATION_EXPORT` | PASS_HOST_SYNTHETIC; nie jest odbiorem rzeczywistej długiej sesji |
| `DEBUG_APK_BUILD` | PASS; podpis i integralność sprawdzone |
| `INSTRUMENTATION_COMPILE` | PASS |
| `DEVICE_UI_LIFECYCLE` / `UI_A12` | NOT_VERIFIED |
| `DEVICE_EXPORT_VISIBILITY` | NOT_VERIFIED |
| `A12_TV_TECHNICAL` | NOT_VERIFIED dla tej dostawy |
| `LONG_SESSION_STABILITY` | NOT_VERIFIED; nie naprawiano tu LMKD |
| `EXPERIMENT_START` | NO_GO do zamknięcia wcześniejszych odbiorów |
