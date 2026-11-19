# Android — Opcje v2, animowane sekcje i pełny ekran

Data: 15.11.2026. Dostawa: `f6_options_layout_20261115_v2`.

Ukończono zatwierdzoną korektę układu Opcji i pełnego ekranu. Wynik hostowy: **50/50 celowanych przypadków PASS**, budowa debug i kompilacja instrumentacji PASS. APK ma sprawdzone CRC ZIP, podpis i zgodny z v1 certyfikat. Nie instalowano aplikacji, nie używano ADB i nie uruchamiano eksperymentu.

Repozytorium: `C:\Users\48572\AndroidStudioProjects\ALPR_v1`, gałąź `main`, HEAD `f7943a7d7334afd1e4188ef9860b2126331926c8`. Zachowano zastany stan roboczy; nie wykonywano commit/push ani operacji w repozytorium Desktopu.

## Zachowanie aplikacji

Pięć sekcji ma wspólne grafitowe powierzchnie, cyjanowe zaznaczenie, marginesy 16 dp i pojedynczy obrys. Usunięto wewnętrzne kolorowe karty oraz dziedziczony obrys nagłówków. Opisy pól skrócono, a pełne wyjaśnienia pozostają w „Pomoc” i „Szczegóły”. Przełączniki mają etykietę i rzeczywisty stan po lewej oraz kontrolkę po prawej; zablokowanie nadal jest odrębne od wyłączenia funkcji.

| Sekcja | Zawartość |
|---|---|
| Tryb pracy i strategia | Tryb eksperymentalny, zapis F6, kategoria, R0/R1/R2, wejście |
| Kamera i kalibracja | Rozdzielczość, odczyt strumieni, TV, pole F6, kalkulator odległości |
| Modele i wykonanie | Pionowe MP/MT/MZ, ustawienia wykonania, oddzielone akcje importu |
| Sesja i zapis | Wariant, seria, scenariusz, powtórzenie, notatka, galeria i pliki |
| Zaawansowane | Profil zwykłej analizy, obsługa sceny, Lock i Autozoom |

Nowe wejście otwiera wszystkie sekcje zwinięte. Jednocześnie wybrana jest najwyżej jedna sekcja. Odtworzenie Activity oraz powrót po cyklu odpowiadającym wyborowi pliku zachowują otwartą sekcję i wartości formularza. Rozwijanie nie zmienia preferencji ani `settings_revision`.

Animacja wysokości, przezroczystości i strzałki trwa 200 ms. Szybkie dotknięcia przerywają poprzedni przebieg od aktualnego położenia; nieaktualne callbacki nie zmieniają nowego wyboru. Zwykłe odświeżanie wartości nie uruchamia animacji. Przy wyłączonych animacjach systemowych zmiana jest natychmiastowa. Zwijanie formularza przenosi fokus na nagłówek i zamyka klawiaturę. Po zakończonym układzie ekran przesuwa się tylko tyle, ile potrzeba do pokazania całego nagłówka; poprzednie przewijanie formularza nie może pozostawić go poza ekranem. Tożsamość sesji zachowuje dotychczasowy zapis w `onPause`.

Wspólna obsługa pełnego ekranu obejmuje główny ekran, oba edytory kalibracji, Opcje, diagnostykę, galerię oraz własne dialogi. Paski można przywołać gestem; nakładają się na zawartość. Stałe wcięcia wynikają z fizycznego wycięcia i obszaru gestów. Zachowano obserwatora IME, a formularz Opcji uwzględnia klawiaturę w przewijaniu. Własne dialogi zachowują istniejące listenery pokazania i zamknięcia. Systemowy wybór plików pozostaje pod kontrolą Androida.

Nie zmieniono kluczy preferencji, modeli, runtime’ów, liczby wątków, inferencji, CameraX ViewPort, serializacji F5A/F6 ani zasad zatwierdzania kalibracji. F6 pozostaje profilem zapisu, a R0/R1/R2 strategią inferencji. Zachowano blokady pracy podczas zapisu oraz dostęp do historii i szczegółów.

## Testy i przegląd wyglądu

Końcowy wspólny przebieg: `tests-verified-final.log`, 50/50 PASS. `tests-final/` i `tests-verified-final/` zawierają oryginalne XML tego przebiegu. Lista każdego przypadku i właściwego XML: `tests-summary.json`. Nie sumowano ponownych wykonań tego samego przypadku. Sumy źródeł zamrożonych przed końcowym przebiegiem są w `audit/tested_source_sha256.json`; pakowanie wymaga ich zgodności.

| Grupa | Przypadki | Wynik |
|---|---:|---|
| `F6ConfigurationUiTest` | 6 | PASS |
| `F6PostStopSessionCreationTest` | 15 | PASS |
| `MainUiSessionLabelsTest` | 9 | PASS |
| `OptionsMotionUiTest` | 10 | PASS |
| `TvSetupUiTest` | 10 | PASS |

Testy obejmują początkowe zwinięcie, pojedynczy wybór, przerwanie i odwrócenie animacji, odświeżanie wartości, wyłączone animacje, odtworzenie Activity, fokus, IME, własne dialogi i diagnostykę. Regresja sprawdza START/STOP i finalizację na istniejącej ścieżce syntetycznej, blokady zapisu, historię bez nowych sesji, punkty TV/F6, obsługę gestów oraz brak ponownego bindowania kamery przy chwilowych paskach. Kontrole IME i wcięć obejmują API 29 i 36.

Testy animacji sterują czasem rzeczywistego `ValueAnimator` deterministycznie. Potwierdzają przejścia stanów, geometrię i odrzucanie spóźnionych callbacków; nie mierzą płynności ani FPS telefonu. Kamera, modele i obrazy używane w fixture są syntetyczne.

Obejrzano wszystkie pięć rozwiniętych sekcji przy czcionce 100% i 150%, na układzie 720×1600 px. Sprawdzono nagłówki, kontrast, pojedyncze ramki i dostępność kontrolek. Wygenerowano także warianty dla obu motywów systemowych; odpowiadające sobie obrazy sekcji mają identyczne SHA-256, zgodnie ze stałą paletą Opcji (`audit/theme-renders.json`).

Rendery są w `host-ui/options-v2-{day|night}-font-{100|150}-{collapsed|section-N|viewport-N}.png`. Dłuższe sekcje mają dodatkowe przewinięte widoki `section-N-page-K.png`. Rysowany jest rzeczywisty korzeń okna: bezpośrednie rysowanie samej karty w innym początku współrzędnych powodowało w natywnym rendererze hostowym błędne wycięcia pływających etykiet. Obrazy okna pokazują je poprawnie. Są to rendery Robolectric, nie screenshoty A12. Przykłady:

- [Opcje zwinięte](C:/Users/48572/AndroidStudioProjects/ALPR_v1/app/build/f6_options_layout_20261115_v2/host-ui/options-v2-day-font-100-collapsed.png)
- [Tryb pracy](C:/Users/48572/AndroidStudioProjects/ALPR_v1/app/build/f6_options_layout_20261115_v2/host-ui/options-v2-day-font-100-section-0.png)
- [Modele przy 150%](C:/Users/48572/AndroidStudioProjects/ALPR_v1/app/build/f6_options_layout_20261115_v2/host-ui/options-v2-day-font-150-section-2.png)

Zachowano pośrednie logi i XML. Stare fixture wymagały dostosowania fokusu `ShadowActivity`, fizycznego wycięcia okna na API 29, kolejki callbacków i czasu animatora. Poprawiono też kwalifikatory zasobów oraz oczekiwanie zapisu tożsamości dopiero w `onPause`. Próba F6 pomniejszała już wewnętrzny szkic i poprawnie trafiała na niezmieniony próg 24 dp; pozytywny fixture korzysta teraz z jawnego pola wyznaczonego z zatwierdzonych punktów TV, pionowej klatki i bieżącej gotowości kamery. Przed zapisem jawnie sprawdza istniejącą walidację. Nie osłabiano walidacji archiwów ani kalibracji. Przewijane rendery ujawniły rzeczywisty problem ustawiania nagłówka po zmianie układu; poprawiono go w kontrolerze i objęto końcowym przebiegiem. Osobno poprawiono rzeczywiste obrysy i zbyt długie etykiety.

Weryfikację wykonano offline z lokalnym JBR. Dokładna końcowa komenda testów jest w `commands.txt`. Budowa:

```powershell
.\gradlew.bat --offline :app:assembleDebug :app:compileDebugAndroidTestJavaWithJavac --console=plain
git diff --check
```

Logi: `build-final.log`, `apk-signature.log` i `audit/git-diff-check.log`. Ostrzeżenia o przestarzałych API/Gradle i natywnym dostępie JBR pozostały widoczne. Nie wykonywano pełnej regresji całego projektu ani instrumentacji na urządzeniu.

## APK i materiały dostawy

Katalog: `app/build/f6_options_layout_20261115_v2/`.

APK: `alpr-f6-options-layout-v2-debug.apk`, **86705361 B**.

```text
SHA-256: df2143757387bbdb881ab1a43d4afc527b447ed481be90532c4f0465c40dd9e9
```

Identyfikator: `com.example.alpr_v1`, debug, `versionCode=1`, `versionName=1.0`, `GIT_DIRTY=true`. Build UTC: `2026-11-15T17:09:48.925181300Z`. Metadane: `apk-identity.json`.

Certyfikat SHA-256: `f3385991af6f601faf4df59697d30f143101a71ee988035745027d3e85aa04a2`, identyczny z lokalną dostawą v1 (`signature-comparison.json`). Porównanie nie stanowi odczytu wersji obecnie zainstalowanej na telefonie.

`changed-sources.zip` zawiera wyłącznie zakres v2 względem zabezpieczonego stanu v1 oraz `task_only.patch`. Nie jest pełnym repozytorium. Raport znajduje się również w `report.md`. `SHA256SUMS.txt` obejmuje pliki dostawy, a `READY.json` oznacza ukończenie pakowania.

## Ochrona wcześniejszych prac

Audyt porównuje 946 plików z początku v2 oraz 1027 plików zabezpieczonych przy wznowieniu. Każda zmiana musi należeć do jawnej listy zakresu; brakujące lub obce zmiany zatrzymują pakowanie. Szczegóły: `audit/protected-source-check.json`, `audit/scope.json`, `audit/source_sha256.json` i `audit/task_only.patch`.

Patch sprawdzono przez zastosowanie do prywatnej kopii zabezpieczonej bazy v1: wszystkie 21 plików zakresu odtworzono identycznie bajtowo (`audit/patch-roundtrip.json`). Użyto `git -c core.autocrlf=false -c core.whitespace=cr-at-eol apply`, aby zachować zastane LF/CRLF. ZIP przechowuje dokładne bajty plików.

Opcje zachowały wszystkie 89 wcześniejszych ID; obecnie jest 102 unikalnych ID. Główny layout pozostał bez zmian w v2. Kontrola: `audit/layout-ids.json`.

Potwierdzono zgodność SHA-256 chronionych APK, raportu, `READY.json` i paczki źródeł v1, a także wcześniejszej paczki MP oraz raportu awarii. Oryginał przerwanej sesji `raw-session-original.tar` nadal ma 345300480 B i SHA-256 `e8e2070c9b5056851d2c7211c8e9b2de7f57775de2219d55e91538de7edefc1b`. Pełne zestawienie: `audit/protected-artifacts.json`.

## Pozostały odbiór urządzenia

| Status | Wynik |
|---|---|
| `ANDROID_HOST_TESTS` | PASS w opisanym zakresie |
| `DEBUG_APK_BUILD` / integralność / podpis | PASS |
| `INSTRUMENTATION_COMPILE` | PASS |
| `UI_A12` / fizyczna płynność animacji / paski i IME | NOT_VERIFIED |
| Rzeczywisty eksport i długi przebieg A12/TV | NOT_VERIFIED dla tej dostawy |
| Naprawa LMKD | Poza zakresem |
| `EXPERIMENT_START` | NO_GO do zamknięcia wcześniejszych odbiorów |

Po aktualizacji w uzgodnionym momencie pozostaje sprawdzić na A12: wszystkie sekcje i duży tekst, szybkie dotknięcia, klawiaturę, powrót z wyboru pliku, dialogi, gest przywołania pasków oraz niezmienność podglądu w edytorach TV/F6. Ta dostawa nie zmienia wymagań z [raportu awarii](C:/Users/48572/AndroidStudioProjects/ALPR_v1/docs/f3_android_final176_awaria_i_zapobieganie_20261114_v1.md).
