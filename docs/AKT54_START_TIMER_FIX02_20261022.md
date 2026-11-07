# AKT-54 — stabilność START i timera, FIX02

**Zastąpiony w zakresie momentu przygotowania przez [FIX03](AKT54_INSTANT_START_FIX03_20261022.md):** modele, magazyn i kamera są teraz przygotowywane przed udostępnieniem START. Poniższy raport opisuje wcześniejszy etap; aktualny checkout sprawdza patch FIX03.

Zmiana zastosowana lokalnie po zgłoszeniu przymrożeń odliczania i opóźnionego początku pipeline. Nie zainstalowano APK, nie uruchomiono kamery ani eksperymentu. Odczyt `adb devices` potwierdził podłączony A12; `pidof` nie znalazł działającej aplikacji, a filtrowany logcat nie zawierał incydentu. Nie przypisujemy więc konkretnego fizycznego zdarzenia jednej potwierdzonej przyczynie.

## Ustalenia i poprawki

1. Timer już wcześniej obliczał pozostały czas z `elapsedRealtime`, a nie odejmował sekund. Zatrzymany UI pokazuje jednak starą wartość i po wznowieniu przeskakuje do aktualnej. Po poprzedniej zmianie odświeżanie odbywało się co 1000 ms; teraz odczytuje zegar maksymalnie co 250 ms, dopasowując się do granic sekund. Nie odtwarza zaległych ticków. `setText` i odświeżenie licznika cropów następują dopiero po zmianie wartości. DEBUG `ALPR_TIMER ui_gap_ms` pozwala odróżnić opóźnienie UI od problemu czasu sesji.
2. `AlprPipeline.process/prepareModels` trzymają monitor pipeline podczas ciężkiej pracy. `setTargetSnapshot[IfCurrent]`, wywoływane także z UI, używały tego samego monitora. Teraz aktualizacja prezentacji korzysta z krótkiej blokady koordynatora generacji; kontrola aktualności stampu pozostaje atomowa. Wybór kolejnego statycznego doprecyzowania jest zlecany wykonawcy inferencji zamiast blokować UI.
3. `StaticSceneWatcher.arm/reset/prepareForZoom` czekały na monitor obejmujący optical flow. Poprzednia poprawka dodała drugi kanał cech pod tą blokadą. Polecenia mają teraz oddzielną, krótką kolejkę stanu; obrazami zarządza wyłącznie `observe`. Reset unieważnia wynik obserwacji w toku. Nie wycofano kanału pełnoklatkowego ani regresji `watch=plates/global`.
4. Przy START budowanie konfiguracji badawczej, kontrola plików APK/modeli i przygotowanie magazynu odbywały się częściowo na UI. Odliczanie rozpoczynało się przed ładowaniem modeli, a pierwsza klatka mogła czekać w kolejce za `prepareModels`. Nowy przebieg ma jawny etap **„Przygotowanie modeli i zapisu…”** na wykonawcy inferencji. Dopiero po poprawnym przygotowaniu ustalane są wspólne `t0` magazynu, domeny i metryk, uruchamiana kamera i timer. Ładowanie modeli nie zużywa już początkowych sekund mierzonego przebiegu.
5. START podczas przygotowania nie dodaje drugiego zadania. Anulowanie, `onPause`, zmiana rewizji ustawień lub zniszczenie Activity unieważniają oczekujące zakończenie. Przygotowany, anulowany magazyn pozostaje oznaczony błędem przygotowania; nie powstaje pomiar z automatycznie uruchomionej kamery. Rezerwacja w `ResearchSessionViewModel` zapobiega nakładaniu przygotowań i wejściu w nową sesję, gdy poprzedni magazyn jest jeszcze PREPARED/RUNNING/FINALIZING. Błędy modeli i runtime blokują START zamiast uruchamiać pusty przebieg.

Przygotowanie nadal może potrwać kilka sekund — teraz jest widocznym, anulowalnym etapem przed pomiarem. Czas otwarcia kamery i pierwszej inferencji po `t0` nadal należy do mierzonego przebiegu. Wznowienie już trwającej sesji po odtworzeniu Activity zachowuje dotychczasowy zegar. Ta zmiana początku pomiaru wymaga odróżnienia wersji aplikacji w przyszłych raportach; nie zmieniono istniejących archiwów i nie zlecono ponowienia F4.

## Dowody i zakres walidacji

- W odizolowanej kompilacji JVM uruchomiono ten sam test współbieżności na kodzie sprzed i po poprawce. Test celowo trzyma monitor obserwacji do zakończenia poleceń sterujących: **przed — TimeoutException, po — PASS**. To reprodukcja zależności blokad, nie pomiar czasu na telefonie.
- Pełne `:app:testDebugUnitTest`: **771 testów, 156 zestawów, 0 błędów, 0 pominiętych**. Obejmuje zegar po opóźnionym callbacku, granice sekund, anulowanie i ponowienie przygotowania, polecenia watchera podczas zajętego monitora oraz dotychczasowe testy statycznego wybudzania i bramek badawczych.
- `:app:compileDebugAndroidTestJavaWithJavac`: **PASS**. Dodano test prezentacji przy zajętym monitorze inferencji oraz test responsywnego anulowania przygotowania bez uruchamiania kamery. Test fizycznego zbierania dostosowano do asynchronicznego START. **Testów Androidowych nie uruchomiono.**
- Kontrola diff i SHA PASS. Patch sprawdzony w katalogu tymczasowym: `--check` bez zapisów, zgodność wyniku i backupów, ponowne `--apply` bez zmian, odrzucenie obcej zmiany bez zapisów.

Polecenie wykonanej walidacji:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --console=plain
```

## Artefakty

- [Patch --check/--apply](patches/patch_android_start_timer_fix02_20261022.py), 12 plików, wejście po poprzedniej lokalnej poprawce AKT54.
- [Diff tego zgłoszenia](patches/START_TIMER_FIX02_20261022.diff).
- [Manifest SHA przed/po](patches/START_TIMER_FIX02_20261022_sha256.json).
- Backup: `_patch_backups/AKT54_START_TIMER_FIX02_b0270a85/`.

Poprawki są już zastosowane. Aktualna kontrola:

```powershell
python "C:\Users\48572\AndroidStudioProjects\ALPR_v1\docs\patches\patch_android_start_timer_fix02_20261022.py" --check
```

Osobne polecenie dla zgodnej kopii stanu wejściowego:

```powershell
python "C:\Users\48572\AndroidStudioProjects\ALPR_v1\docs\patches\patch_android_start_timer_fix02_20261022.py" --apply
```

Następny krok operatora: zbudować tę wersję w Android Studio. Nie potwierdzono jeszcze płynności UI ani opóźnienia pierwszej inferencji na A12. Oryginalne ograniczenia handoffu dotyczące instalacji i pomiarów pozostają zachowane.
