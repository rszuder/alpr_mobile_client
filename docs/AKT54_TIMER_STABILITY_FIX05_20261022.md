# AKT54 — stabilizacja timera eksperymentu, FIX05

Następna poprawka: [FIX06 — nowe cechy sceny](AKT54_STATIC_FEATURE_ADDITION_FIX06_20261022.md). Po jej zastosowaniu bieżący stan sprawdza patch FIX06; historyczny patch FIX05 zachowuje własne dokładne bramki SHA.

Zastosowano zatwierdzony plan na lokalnym stanie po FIX04. **Kod i regresje JVM: PASS. Potwierdzenie zachowania APK na A12: NOT_RUN.** Zachowano przepływ START z FIX04. Nie instalowano APK, nie uruchamiano pomiarów na telefonie ani F4; brak commitu/pushu.

## Przyczyna i dowody

Timer już przed poprawką wyliczał pozostały czas z zegara monotonicznego. Odświeżanie **oraz automatyczny STOP** wykonywały się jednak na głównym wątku. Zablokowanie tego wątku zatrzymywało wskazanie, a kolejny odczyt pokazywał bieżący czas po przeskoczeniu kilku sekund.

| Mechanizm | Dowód | Zmiana | Walidacja |
|---|---|---|---|
| Zajęty główny wątek | Odczyt logcat z tej rozmowy: `Choreographer: Skipped 326 frames`, `297 frames`, `105 frames`, PID 22494 | Usunięto opisane niżej oczekiwania i obróbkę cropów z UI | JVM PASS; rzeczywiste widgety — kompilacja PASS, wykonanie NOT_RUN |
| STOP zależny od UI | `experimentTimerRunnable` był callbackiem głównego Handlera | Osobny wykonawca kontrolera terminu w zachowanym ViewModelu | Testy zegara i sesji JVM PASS; blokada Android Main Looper — test skompilowany, NOT_RUN |
| Galeria współdzieliła tick | Tick odświeżał licznik, a zbieranie cropów analizowało bitmapy i tworzyło JSON na UI | Tick aktualizuje tylko wskazanie; analiza i miniraporty działają poza UI | Kompilacja PASS; testy własności bitmap i kolejności skompilowane, NOT_RUN |
| UI mogło czekać na inferencję i KLT | Polecenia wchodziły na monitory zajęte przez `processBitmap` / optical flow | Rozdzielono publikację ważności wyników od ciężkiego porządkowania; tracker ma skrzynkę poleceń | Reset podczas zajętego monitora trackera: JVM PASS; pozostałe testy Androida skompilowane, NOT_RUN |

Logcat potwierdza zacięcia UI, ale nie zawiera stosu dokładnie z incydentu operatora. Nie przypisujemy każdego zacięcia na telefonie do jednego konkretnego wywołania. Kompilacja nie stanowi pomiaru płynności A12.

## Zachowanie po zmianie

- Jeden termin końca: wspólne `t0` plus zamrożony czas. Odtworzenie ekranu i wznowienie nie przesuwają terminu. Powrót po jego upływie ponownie sprawdza zaległy koniec, bez rozpoczynania nowego odliczania.
- Osobny wykonawca kończy pomiar niezależnie od UI. Sesja, metryki i magazyn odrzucają nową pracę po terminie również przy opóźnionym callbacku. Dodatkowa kontrola znajduje się po przygotowaniu wejścia, przed nowymi wywołaniami MP/MT/MZ i przed oznaczeniem ich jako wykonanych.
- Rozpoczęte wywołanie natywne może się dokończyć. Jego dowody pozostają w archiwum z właściwym oznaczeniem zakończenia/anulowania. Nie zamieniamy braku wywołania w `NO_DETECTION`. Finalizacja paczki może trwać dłużej niż okno pomiaru.
- Logiczny koniec TIMER odpowiada terminowi; rzeczywiste opóźnienie pozostaje jawne. Nowe sesje zapisują `timer_deadline_elapsed_nanos`, `stop_observed_elapsed_nanos`, `timer_stop_lateness_ms`. DEBUG `ALPR_TIMER` rozdziela `ui_gap_ms` i `deadline_lateness_ms`.
- Wskazanie odczytuje aktualny czas, z jednym oczekującym callbackiem na granicy sekund. Nie odtwarza zaległych ticków i nie wygładza sztucznie czasu. Przy finalizacji TIMER pozostaje zero; po zapisie paczki wraca ustawienie następnego przebiegu. Pauza ekranu odłącza wyłącznie prezentację.
- Analiza jasności i miniraporty cropów działają na szeregowym wykonawcy poza UI. Każda przekazana bitmapa ma niezależną kopię. Gotowe podglądy zachowują kolejność i przetrwają odtworzenie Activity. Wyczyszczenie galerii unieważnia spóźnione wyniki i zwalnia bitmapy. Zamknięta galeria nie jest wiązana ponownie przy każdym cropie.
- Kolejka podglądów RAM jest ograniczona dotychczasową pojemnością galerii, także dla wyników oczekujących na dostarczenie. Przy zapełnieniu można pominąć podgląd RAM; trwały kolektor badawczy i jego licznik działają oddzielnie. Finalizacja raportu czeka na przyjęte przygotowania podglądów w tle, bez blokowania UI.
- Reset i zakończenie zoomu nie czekają na monitor inferencji na UI. Utrata śledzenia publikuje unieważnienie generacji przed ciężkim porządkowaniem. Spóźnione polecenia są odrzucane przy resecie. Kotwiczenie/reset trackera nie czekają na optical flow; wynik dla starszej rewizji polecenia jest pomijany.

Zegar interwałów uwzględnia uśpienie urządzenia, zgodnie z [Android SystemClock](https://developer.android.com/reference/android/os/SystemClock). Anulowane zadania są od razu usuwane z kolejki, także przy długim własnym czasie; zob. [ScheduledThreadPoolExecutor](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/ScheduledThreadPoolExecutor.html).

## Zgodność

`ExperimentDeadline` jest wewnętrznym kontrolerem z wstrzykiwanym zegarem i wykonawcą do testów. `ExperimentSession` udostępnia termin i sprawdzenie przyjmowania pracy. ViewModel przechowuje kontroler i zdarzenie końca; Activity obsługuje widoki. Dotychczasowe wejścia pipeline pozostają dostępne, a UI ma oddzielne wejście publikujące zmianę bez czekania na inferencję.

Pola diagnostyczne są dodatkiem do formatu v1. Nie zmieniono istniejących archiwów, modeli, conf/IoU, GT, masek/ROI, trybu statycznego ani bramek MZ INT8. `ExperimentStartRequest` i etykiety START/Anuluj/STOP z FIX04 pozostają bez zmian.

## Walidacja

Wykonano:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --console=plain
```

**PASS: 787 testów JVM, 160 zestawów, 0 failures, 0 errors, 0 skipped.** Obejmuje regresje START oraz pojedynczy koniec, wczesny/opóźniony callback, anulowanie, stare powtórzenie, wznowienie po skoku czasu, dokładną granicę przyjmowania pracy, własny czas 2,5 min, MANUAL/TIMER, reset przy zajętym monitorze trackera i odświeżanie wskazania na granicach sekund.

**Kompilacja wszystkich testów Androidowych: PASS. Wykonanie: NOT_RUN.** Dodano osiem scenariuszy w czterech klasach: odrzucenie pracy po terminie i zachowanie dowodów MT, koniec przy zablokowanym Main Looper, rzeczywisty timer/STOP przy zajętym pipeline i wykonawcy cropów, kolejność/odtworzenie galerii, wyczyszczenie i bitmapy, pojemność/błąd przygotowania oraz nieblokujące polecenia pipeline i trackera. Nie wykonują one inferencji modeli ani kampanii F4.

`git diff --check`: PASS. Weryfikacja odtwarzalności patcha obejmuje SHA, dokładne zastosowanie w kopii tymczasowej, ponowne zastosowanie bez zmian, odrzucenie obcego źródła i backupy. Jej wyniki zapisano w pliku SHA wymienionym poniżej.

Nie ma jeszcze pomiaru płynności ani niezależnego porównania rzeczywistego czasu na A12 dla tej wersji. Ten etap pozostaje po stronie operatora po zbudowaniu/instalacji w Android Studio, zgodnie z handoffem. Nie uruchamiano automatycznie żadnego eksperymentu.

## Artefakty

- [Patch FIX05](patches/patch_android_timer_stability_fix05_20261022.py), wejście: stan po FIX04.
- [Diff](patches/TIMER_STABILITY_FIX05_20261022.diff), [SHA przed/po i wyniki kontroli](patches/TIMER_STABILITY_FIX05_20261022_sha256.json).
- Backup stanu sprzed FIX05 wskazano w pliku SHA.

Poprawka jest już zastosowana. Kontrola aktualnego checkoutu:

```powershell
python "C:\Users\48572\AndroidStudioProjects\ALPR_v1\docs\patches\patch_android_timer_stability_fix05_20261022.py" --check
```

Zastosowanie w zgodnej kopii stanu wejściowego:

```powershell
python "C:\Users\48572\AndroidStudioProjects\ALPR_v1\docs\patches\patch_android_timer_stability_fix05_20261022.py" --apply
```
