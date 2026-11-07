# AKT54 — przywrócenie działania START, FIX04

Zastosowano zatwierdzony plan FIX04. Zmiana lokalna, na stanie po FIX03; brak instalacji APK, pomiarów na A12, uruchomienia F4 oraz commitu/pushu.

## Przyczyna

W FIX03 `experimentPreviewPaused` jednocześnie zmieniał etykietę START na „Włącz podgląd” i powodował natychmiastowe wyjście z `ensureExperimentPrepared`. Dostępność START była uzależniona od zakończenia tego przygotowania. Pauza nie wygasała, więc czekanie nie mogło odblokować przycisku. Podobny brak drogi wyjścia występował po błędzie przygotowania: ustawiano flagę błędu, wyłączano ponawianie w tle i pozostawiano START nieaktywny.

W odczycie diagnostycznym poprzedzającym wdrożenie A12 raportował przyznane uprawnienie CAMERA, aktywny ekran MainActivity oraz zamkniętą kamerę. To pasuje do ścieżki pauzy. Nie odczytywano pamięci procesu, więc nie przedstawiamy konkretnej wartości flagi na telefonie jako zmierzonej. Pętla blokująca jest potwierdzona w kodzie.

## Zachowanie po poprawce

- **START** zachowuje swoją etykietę i pozostaje dostępny przy zatrzymanym podglądzie, braku uprawnienia i błędzie przygotowania. Wyjątkiem jest finalizacja poprzedniej sesji, której przyczyna pozostaje widoczna w statusie.
- Kliknięcie usuwa pauzę i blokadę ponowienia, zapisuje jedno oczekujące żądanie i uruchamia potrzebne przygotowanie. Jeśli modele, zapis, kamera i warunki termiczne są już gotowe, rozpoczyna pomiar w obsłudze kliknięcia.
- Jeśli trzeba poczekać, etykieta zmienia się na **Anuluj**. Status podaje powód oczekiwania. Po gotowości żądanie jest zużywane raz, a przycisk przechodzi w **STOP**. Operator wybrał automatyczne rozpoczęcie po przygotowaniu, bez drugiego kliknięcia.
- Anulowanie zatrzymuje oczekujące żądanie i podgląd; START pozwala ponowić. Błąd przygotowania lub kamery kończy oczekiwanie i pokazuje przyczynę, pozostawiając drogę ponowienia. Nie dodano automatycznej pętli ponawiania błędów.
- Żądanie może przetrwać pauzę wywołaną systemowym dialogiem uprawnienia. Odmowa, anulowanie, opuszczenie ekranu, zniszczenie Activity lub zmiana konfiguracji je unieważniają. Przejście do ustawień Androida po trwałej odmowie wymaga nowego START po powrocie.
- Gotowość aktualizują zakończenie przygotowania, pierwsza rzeczywista klatka podglądu, wynik uprawnienia, wznowienie ekranu i osobne lekkie sprawdzenie co 250 ms. Nie zależy już od pracy monitora temperatury. Spóźnione zdarzenia kamery są filtrowane generacją żądania podglądu; przygotowanie zachowuje własny numer generacji.

Zachowano przygotowanie przed kliknięciem, ponowne użycie strumienia i modeli, wspólne `t0` metryk/sesji/timera oraz trwały zapis początku sesji przed dopuszczeniem inferencji. Nie zmieniono bramek modeli, progów, istniejących archiwów ani GT. Gdy przygotowanie jest konieczne po kliknięciu, timer zaczyna biec po osiągnięciu gotowości — zgodnie z wybranym wariantem „Przygotuj i uruchom”.

Wewnętrzny `ExperimentStartRequest` rozdziela zamiar operatora od przygotowania zasobów. Przeciążenie `CameraController.startPreview` przyjmuje opcjonalny callback pierwszej klatki na wątku UI; wcześniejsza sygnatura pozostaje dostępna. Brak zmian formatów zewnętrznych.

## Walidacja

Wykonano:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --console=plain
```

**PASS: 777 testów JVM, 157 zestawów, 0 błędów i 0 pominiętych.** Nowe testy sprawdzają pojedyncze uruchomienie po gotowości, brak automatycznego pomiaru bez żądania, pauzę dialogu uprawnienia, odmowę i ponowienie, spóźniony wynik uprawnienia, anulowanie oraz opuszczenie ekranu.

**Kompilacja testów Androidowych PASS; wykonanie na Androidzie NOT_RUN.** Dodany test używa rzeczywistego `MaterialButton`, symulowanej konfiguracji podglądu i zajętego wykonawcy przygotowania. Sprawdza START/Anuluj, dostępność przy pauzie i błędzie, usunięcie pauzy po kliknięciu, ponowienie oraz brak uruchomienia pomiaru przed gotowością. Nie wykonuje inferencji modeli. Nie należy utożsamiać kompilacji tego testu z potwierdzeniem działania APK na A12.

Kontrola diff i SHA PASS. Patch sprawdzony w katalogu tymczasowym: `--check` bez zmian, dokładne zastosowanie i backupy, ponowne `--apply` bez zmian oraz odrzucenie zmienionego źródła bez zapisów.

## Artefakty

- [Patch FIX04](patches/patch_android_start_recovery_fix04_20261022.py), 5 plików, wejście po FIX03.
- [Diff](patches/START_RECOVERY_FIX04_20261022.diff), [SHA przed/po](patches/START_RECOVERY_FIX04_20261022_sha256.json).
- Backup: `_patch_backups/AKT54_START_RECOVERY_FIX04_a2317ad8/`.

Poprawka jest zastosowana. Kontrola aktualnego checkoutu:

```powershell
python "C:\Users\48572\AndroidStudioProjects\ALPR_v1\docs\patches\patch_android_start_recovery_fix04_20261022.py" --check
```

Zastosowanie w zgodnej kopii stanu wejściowego:

```powershell
python "C:\Users\48572\AndroidStudioProjects\ALPR_v1\docs\patches\patch_android_start_recovery_fix04_20261022.py" --apply
```

Następny krok operatora: zbudować tę wersję w Android Studio. Potwierdzenie zachowania na A12 pozostaje niewykonane; nie zlecono powtarzania kampanii F4.

## Dalsza poprawka timera

FIX05 zachowuje naprawiony START z tego raportu i oddziela termin końca od kolejki UI. Aktualny stan, walidacja i patch: [AKT54_TIMER_STABILITY_FIX05_20261022.md](AKT54_TIMER_STABILITY_FIX05_20261022.md).
