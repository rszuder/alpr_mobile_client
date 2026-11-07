# AKT-54 — wykonanie handoffu, 22.10.2026

**Późniejsze zgłoszenie dotyczące START i timera:** zastosowano [FIX02](AKT54_START_TIMER_FIX02_20261022.md). Poniższy raport i patch opisują wcześniejszy etap; aktualny checkout należy sprawdzać patchem FIX02, ponieważ SHA części plików uległy zmianie.

Zmiany są zastosowane lokalnie w `ALPR_v1`. HEAD zastany: `6900cadb2fd4db00275f359e1c2168e16dd67ac0`.
Zastany worktree zawierał FIX01 oraz badawczy importer MZ INT8; zachowano je. Nie wykonano commitu, pushu, instalacji APK, pomiaru na telefonie ani pełnej sesji F4. Nie zmieniono archiwów, modeli, progów conf/IoU, GT ani ROI.

Status wybudzania: **odtworzono i poprawiono deterministyczne luki w kodzie; potwierdzenie konkretnego incydentu operatora i skuteczności na A12 pozostaje niewykonane**. Nie ma połączonego logu z rzeczywistą chwilą zmiany zdjęcia dla zgłoszonego incydentu. Testy syntetyczne nie stanowią walidacji jakości ani niezawodności fizycznego układu TV–kamera.

| cause | observed | fixed | verified |
|---|---|---|---|
| `watch=plates` ogranicza cechy do poprzednio wykrytej tablicy | Przed poprawką test z niezmienioną tablicą i usuniętymi cechami tła nie wybudzał MT; luminancja pozostawała poniżej progów | Osobny pełnoklatkowy kanał cech, zakotwiczony w tej samej referencji, bez nowych ROI/detekcji | JVM PASS; telefon NOT_RUN |
| Odstępy odczytów cech >500 ms zerują potwierdzenia | Warunek w kodzie uniemożliwia zebranie trzech potwierdzeń przy stale rzadszych callbackach | Przerwa nie jest dowodem powrotu starej sceny; wymagane nadal trzy istotne obserwacje i >=120 ms, zwykła obserwacja lub cofnięcie czasu zeruje serię | Testy regularnych i nieregularnych callbacków PASS; rzeczywista kadencja A12 niezmierzona |
| Auto zatrzymuje widoczny licznik na 49 | `CropCapacityPolicy`: 3% sterty / 160 KiB daje 49 przy stercie 256 MiB; licznik UI pokazywał rozmiar bufora | Licznik pokazuje zapisane rekordy cropów bieżącego `ResearchSessionStore`, niezależnie od limitu RAM | Wzór 49: JVM PASS; test zapisu 60 cropów i resetu licznika kolejnej sesji skompilowany, NOT_RUN |
| Miniatury są wspólne dla kolejnych powtórzeń | START zerował sekwencję i mapę próbkowania, ale zachowywał listę galerii | Galeria domyślnie filtruje bieżącą/ostatnią sesję; osobny przełącznik pokazuje wcześniejsze podglądy | Kompilacja PASS; UI na urządzeniu NOT_RUN |
| Static/dynamic pozostaje klikalny przed START w EXP | Do polityki przycisku przekazywano `experimentSession.isRunning()` zamiast stanu EXP | HUD blokowany przez EXP oraz aktywną sesję; wybór przed START pozostaje w ustawieniach | Polityka JVM PASS; test HUD przed START skompilowany, NOT_RUN |
| Graf pokazuje CPU ×4 po wyborze CPU 2 | Opis korzystał z `AutoTuneManager.chosenProfile`, a dropdown zapisywał oddzielny profil badawczy | Opis EXP korzysta z jawnego `ResearchExecutionChoice`; zmiana dropdownu, EXP i R0/R1/R2 odświeża graf | Kompilacja i regresje wykonania JVM PASS; UI NOT_RUN |
| Wybór wariantu wymaga dodatkowego menu i może skracać opisy | Węzeł otwierał listę akcji, używano standardowego wiersza wyboru | Kliknięcie aktywnego węzła otwiera warianty; wielowierszowe nazwy, rozmiary wejścia i identyfikatory, wyszarzone niedostępne opcje, nieaktywne Zastosuj bez poprawnego wyboru | Kompilacja zasobów i Java PASS; UI NOT_RUN |
| Timer ma tylko presety i ukryty limit 3600 s | Dostępne 15/60/180/300 s, konfiguracja obcinała dłuższy czas do godziny | Pole minut, także ułamki z przecinkiem/kropką, zachowane presety; bez limitu jednej godziny i bez cichego obcinania wpisu | JVM: 2,5 min, 1.25 min, 120 min, błędy i przepełnienie PASS; UI NOT_RUN |

## Mechanizm wybudzenia i granice diagnozy

`CameraController` przekazuje luma przed callbackiem ciężkiej klatki. `MainActivity` przenosi najnowszą próbkę do osobnego wykonawcy; `STATIC_IDLE` nie wyłącza tej ścieżki. `processDirectLumaFrame` pomija ją podczas rzeczywistej transformacji kamery i dla nieaktualnego stampu. Samo `staticWaiting()` odcina inferencję, a nie luma. Nie znaleziono deterministycznego dowodu blokady po zatwierdzonej granicy w tej ścieżce.

Pełnoklatkowe cechy są osobnym detektorem w `StaticSceneWatcher`, więc lokalne regiony `StaticSceneWatchRegions.bounds/featureBounds` i semantyka FIX01 pozostają zachowane. Dla `watch=global` nadal działa kanał FIX01; dodatkowy detektor jest używany dla `plates/vehicles`. Referencja pochodzi sprzed powolnej inferencji. Kompensacja ekspozycji, progi utraty/ruchu punktów oraz osobna referencja przy zoomie pozostają zachowane. Nie dodano restartu okresowego ani inferencji na każdej klatce.

Nowy `StaticPlateBackgroundWakeTest`: pierwsze uruchomienie przed zmianą produkcyjną — 4 testy, 2 oczekiwane błędy (zmiana tła oraz nieregularne odczyty); po zmianie PASS. Następnie dodano osobną regresję rzadkich odczytów kanału globalnego. Długie nieruchome sceny, ekspozycja +30, szum i przesunięcie o 1 px nie powodują resetów w testach. Dotychczasowy test „featurelessObservedRegion” został ograniczony do rzeczywiście niezmiennej, pozbawionej cech sceny: zmiana tła poza pustym regionem jest teraz celowo obsługiwana przez nowy test.

Rozszerzony test Androidowy sprawdza `STATIC_IDLE -> HARD_RESET -> BASELINE`, odblokowanie `staticWaiting`, żądanie natychmiastowej klatki, odrzucenie starego stampu i brak powtórnego zwiększenia generacji. **Skompilowano go, ale nie uruchomiono; nie stanowi dowodu faktycznej inferencji MT na telefonie.**

Logcat `ALPR_STATIC` w DEBUG pokazuje co najwyżej raz na sekundę obserwacje (oraz wykryte granice): generację, fazę, źródło/rozmiary list regionów, liczbę callbacków i odstęp, stan referencji, lokalną/globalną frakcję, punkty, utratę/ruch i potwierdzenia obu kanałów. Faktyczne wywołanie backendu MT ma wpis `MT_INVOKE`, po dekodowaniu `MT_RESULT detections=N`, błąd wykonania `MT_ERROR`. Wpisy nie zawierają rozpoznanego numeru ani obrazu. Brak `MT_INVOKE` nadal oznacza brak dowodu wykonania, a nie `NO_DETECTION`.

## Dokładne zmiany obsługi

- Graf: kliknięcie MT/MZ -> warianty -> Zastosuj. „Model / import” otwiera wcześniejsze akcje. AUTO pozostaje niedozwolone w EXP; niedostępny runtime nie staje się wybieralny. Opis badawczy pokazuje wybrane CPU/GPU i informację o braku przypiętego wariantu; nie potwierdza wykonania przed START.
- EXP: wybrany w ustawieniach static/dynamic jest widoczny, lecz przycisk HUD jest nieaktywny także przed START. Zamrożona konfiguracja rozpoczętej sesji ma nadal pierwszeństwo.
- Timer: „Własny czas (minuty)” -> np. `2,5` -> Zastosuj. Minimum pozostaje 5 s; czas zaokrąglany do sekundy, maksimum wynika z reprezentacji `int` sekund. Błędny wpis pozostawia dialog otwarty. Zapamiętywany jest czas, nie automatyczne uzbrojenie timera po ponownym uruchomieniu aplikacji.
- Cropy: badge pokazuje liczbę zapisanych rekordów bieżącego/ostatniego przebiegu, odświeżaną także bez włączonego timera i po finalizacji. Nowy przebieg ma nowy licznik. W galerii osobno podano liczbę zapisanych cropów i liczbę podglądów w RAM wraz z limitem. „Pokaż także podglądy poprzednich sesji” udostępnia zachowane miniatury. Nie usuwano wcześniejszych danych. Zapis badawczy już wcześniej korzystał z oddzielnego magazynu; zmiana nie dowodzi kompletności istniejących archiwów.

## Walidacja lokalna

`gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --console=plain`

PASS: **764 testy JVM, 153 zestawy, 0 błędów, 0 pominiętych**. Kompilacja kodu/zasobów DEBUG i testów Androidowych PASS. Wśród regresji jest istniejąca bramka badawczego importu MZ INT8. Nie wykonywano `assembleDebug`, instalacji, testów instrumentowanych, pomiarów TV ani kampanii F4. Standardowe ostrzeżenia Gradle/deprecated API pozostają.

Patch: test w odizolowanym katalogu potwierdził brak zapisów przy `--check`, dokładne SHA po `--apply`, zgodność backupów z wejściem, idempotencję i odrzucenie zmienionego źródła bez dalszych zapisów. Bieżący checkout: `SHA_CHECK_OK`, `pending=0`.

## Artefakty i odtworzenie

- [Patch --check/--apply](patches/patch_android_akt54_20261022.py): 17 plików; wymaga dokładnego stanu zastanego po FIX01/importerze. Czysty GitHub HEAD nie jest właściwym wejściem. `--check` jest wyłącznie odczytem, `--apply` tworzy backup przed zapisem. Brak automatycznej kompilacji, instalacji, Git i eksperymentów.
- [Czytelny diff względem zastanego worktree](patches/AKT54_20261022.diff) — nie obejmuje wcześniej wykonanych zmian operatora.
- [SHA przed/po i zabezpieczenia zastanych patchy](patches/AKT54_20261022_sha256.json).
- Backup pierwotnych plików tej pracy: `_patch_backups/AKT54_20261022_original_517514ac/`.

W aktualnym repo poprawki są już zastosowane; wystarczy kontrola:

```powershell
python "C:\Users\48572\AndroidStudioProjects\ALPR_v1\docs\patches\patch_android_akt54_20261022.py" --check
```

Osobne polecenie do zgodnej kopii repo, gdy patch raportuje oczekujące zmiany:

```powershell
python "C:\Users\48572\AndroidStudioProjects\ALPR_v1\docs\patches\patch_android_akt54_20261022.py" --apply
```

Następny krok operatora: zbudować aplikację w Android Studio. Fizyczny smoke test wymaga osobnego uzgodnienia zgodnie z handoffem; wystarczający zakres techniczny to 2–3 przełączenia obrazów oraz nieruchoma plansza z logiem `ALPR_STATIC`. Nie jest to nakaz powtarzania F4. Istniejące sesje zachowują dotychczasową proweniencję; nowe wybudzanie trzeba oznaczyć odrębną wersją APK, gdy operator ją zbuduje/zainstaluje.
