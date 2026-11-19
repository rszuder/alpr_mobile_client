# Android — zwykłe CPU i komunikaty gotowości, A v1

Data: 16.11.2026. Katalog wydania: `app/build/android_normal_cpu_readiness_copy_20261116_v1/`.

Status źródeł i hosta: **DONE**. Odbiór UI na A12: **PASS_SCOPED**, zwykły tryb, czcionki 100%/150%, jawna edycja i trwałość profili. Całościowy odbiór pracy inferencji: **PARTIAL**; rzeczywista liczba workerów i próba terenowa pozostają **NOT_VERIFIED**. Nie jest to zgoda GO na badania.

## Zmiana dla operatora

Opcje prowadzą zdaniem „Ustawienia są poprawne” i wyjaśniają, że kamera będzie sprawdzana w podglądzie, a modele przygotowywane przy uruchamianiu analizy. Liczniki reguł nie są wyświetlane w podsumowaniu ani nagłówkach sekcji. Normalne kontrole odroczone nie otrzymują obrysu ostrzegawczego. Znane braki nadal prowadzą do konkretnego modelu, wariantu, pola albo istniejącego kalibratora.

Main korzysta z tej samej projekcji znaczenia stanu, ze skróconą treścią mieszczącą się w zachowanych granicach wizjera. Komunikaty działającej kamery, przygotowania, sesji, zapisu, STOP i timera zachowują pierwszeństwo. Nie zmieniono reguł `ReadinessFinding`, oceny AF ani `CameraSetupReadiness` i bramek START.

Każdy kafel MP/MT/MZ pokazuje wariant i konfigurację CPU. Kliknięcie otwiera istniejący wybór wariantu rozszerzony o „Wątki CPU”. Wariant Auto i wykonanie Auto są oddzielne. Ręczne N jawnie żąda CPU; Auto przywraca dobór CPU/GPU i wątków. MP wyłączony przez R0 lub zwykłe ustawienie jest opisany jako wyłączony, również gdy nie wybrano modelu opcjonalnego.

## Kontrakt wykonania i zgodność danych

- Nowa preferencja `normal_execution` jest indeksowana rolą i fingerprintem modelu. Nie zastępuje `autotune`, przypięcia wariantu ani `research_*_execution`.
- Brak nowego klucza oznacza zachowanie starego AutoTune/fallbacku. Sam odczyt nie zapisuje migracji. Odczyt starszego `roi_budget_policy` w Opcjach jest bezstratny; istniejący klucz pozostaje na miejscu.
- Zapis następuje po „Zastosuj”. Anulowanie niczego nie zmienia. Edycja samego CPU nie zwiększa `settings_revision` i nie uruchamia `settings_applied` ani nowego resetu sceny.
- Profil jest stosowany na istniejącej granicy `prepareModels`, przed nowym strumieniem analizy. Trwająca inferencja zachowuje otwarty backend. Zmiana jest opisana jako oczekująca na następne uruchomienie modelu.
- Zakres jest wyznaczany przez `Runtime.availableProcessors()` procesu, bez stałego 1–8. Obecne integracje mają CPU: LiteRT `setNumThreads`, ONNX `setIntraOpNumThreads`, NCNN `net.opt.num_threads`. Dostarczony NCNN ma włączone OpenMP/threads i wyłączony Vulkan. Nie dodano backendu ani delegata.
- Liczba w UI jest konfiguracją żądaną/wybraną, a nie pomiarem pracujących wątków. Zachowano `cpu_threads_effective=null` i istniejące rozróżnienie żądania, zaakceptowanej opcji oraz braku publicznego pomiaru workerów.
- Nieweryfikowany runtime pozostaje nieweryfikowany. Wybrany wariant NCNN nie jest przedstawiany jako brak modelu ani jako rozstrzygnięte wykonanie Auto.
- Opcje używają odczytowego trybu istniejącego `ModelRegistry`, bez migracji plików i inicjalizacji NCNN. Odczyt metadanych nie otwiera backendów i nie tworzy sesji.
- Zamrożona konfiguracja badawcza nadal wybiera profile z `ResearchStageExecutionConfig`. Nie zmieniono jej serializatorów, konfiguracji F4/F5A/F6, modeli, progów rozpoznawania, GT ani formatów archiwów i eksportu.

## Walidacja i pochodzenie

Baza: brudny katalog roboczy, HEAD `f7943a7d7334afd1e4188ef9860b2126331926c8`, nie czysty HEAD. Snapshot i SHA obejmują 1144 zastane pliki; ochroną objęto 1407 plików wcześniejszych wydań. Nie wykonano commitu, pushu, resetu, stasha ani czyszczenia repozytorium. Snapshot źródeł ma około 3,4 MB; nie kopiowano całego `app/build`.

Przed zmianami odtworzono pełne **193/193 PASS**, bez pominięć, wraz z budową APK i kompilacją instrumentacji: `before-a-bounded/`. Źródła pozostawały identyczne z bazą przez cały ten przebieg. Pierwsza próba `before-a/` zakończyła się brakiem miejsca na dysku i uszkodzeniem binarnego raportu Gradle. Jej skopiowane stare XML oznaczono jako nieprzydatne; nie użyto ich do deklaracji PASS. Powtórzenie ograniczyło proces do jednej klasy na JVM i użyło własnego katalogu tymczasowego.

Wszystkie wcześniejsze selektory Options v2, gotowości, Main v1, START/STOP i ochrony protokołów pozostają w zestawie. Nowe sprawdzenia obejmują niezależność MP/MT/MZ i badań, starsze preferencje, utrzymanie ręcznego CPU mimo nowego AutoTune, powrót do Auto/GPU, zakres CPU, rzeczywisty dialog, anulowanie, rekreację, brak zapisów przy odczycie, nieweryfikowane NCNN i czcionki 100%/150%.

Nie osłabiono wcześniejszych asercji. Test dużego tekstu Main ujawnił za długą nową treść; skrócono komunikat produkcyjny. Test fontów nowego selektora poprawiono tak, aby przy drugim pomiarze nie zwijał otwartej sekcji. Wymagania wysokości i braku ucięcia tekstu pozostały aktywne. Początkowe nieudane przebiegi zachowano.

Wyniki końcowe: **210/210 PASS, 0 FAIL, 0 SKIP**, `host-accepted-a/` i `tests-summary.json`; pełne XML, log Gradle i manifest badanych źródeł w katalogu przebiegu. `assembleDebug` i kompilacja instrumentacji: PASS. Rendery hostowe są syntetyczne i nie dowodzą działania fizycznej kamery ani workerów bibliotek. Pełny protokół JSON przed/po ma identyczne 5610 bajtów i SHA256 `d04a8f169696e5b02e77f0179e2434693fca78eab5e86d8fc2dc1e5a1c6959db`; porównanie nie pomija pól.

Próba na A12 ujawniła dodatkowo wypchnięcie przycisków dialogu przy 150%. W normalnym trybie sekcja CPU i zachowane przejście „Model / import” są teraz stopką przewijanej listy wariantów, a przyciski Zastosuj/Zamknij pozostają poza nią. Badawczy dialog zachowuje swój układ. Test sprawdza pełną widoczność obu przycisków. Zmianę potwierdzono ponowną instalacją i zrzutami fizycznego telefonu. Nieudane przebiegi `device-dialog-a/` i wcześniejsze logi pozostają w wydaniu; wyniki pochodzą z ostatniego poprawnego pełnego przebiegu.

## Galaxy A12 i brakujące odbiory

Przed aktualizacją odczytano SM-A125F, Android 12, 720×1600, gęstość 300 dpi, czcionkę 100%; aplikacja nie miała aktywnego procesu. Zachowano zastany APK, cztery pliki preferencji i metadane **366** sesji, wraz z SHA. Jest to bieżący stan telefonu, niezależny od wcześniejszego raportu opisującego 349 sesji.

Aktualizacja `adb install -r -t` zachowała dane. Certyfikat SHA256 `f3385991af6f601faf4df59697d30f143101a71ee988035745027d3e85aa04a2` jest taki sam jak w zastanym APK; metadane i SHA końcowego pakietu są w `apk-info.json`. Zastany APK oraz wersje pośrednie z prób są zachowane w `device/`.

Na telefonie ustawiono przez UI MP=2, MT=1, MZ=3; kolejne wybory nie zmieniały pozostałych ról. Profile przetrwały zakończenie nieaktywnego procesu i ponowne otwarcie Main/Opcji. Przed Zastosuj szkic nie tworzył preferencji. Po próbie przywrócono każdemu modelowi Auto i systemową czcionkę 100%. Duża czcionka była faktycznym ustawieniem Androida 150%, a nie powiększeniem obrazu. Dowody: `device/ui/`, `device/cpu-independence.json`, `device/normal-manual-before-restart.xml`, `device/normal-manual-after-restart.xml`.

Instalację, skutki jawnych edycji i kontrolę zachowania danych opisują `device/acceptance.json` i `device/preservation.json`. Nie uruchamiano analizy, sesji badawczej ani FINAL176. Rzeczywiste działanie wszystkich backendów z ręcznym CPU, rzeczywista liczba workerów, aktywnie zablokowany badawczy edytor i powrót z fizycznej kalibracji nie otrzymują odbioru urządzeniowego z samych testów hosta. Próba dynamiczna należy do oddzielnego B.

## Odtworzenie i zawartość

Polecenie: `python -B tools/verify_android_scoped.py a --run <nowa-nazwa>`; jeden proces budowy naraz. Nowa nazwa jest wymagana, aby zachować istniejące wyniki.

Wydanie zawiera `patch-a.patch`, `sources-a.zip`, listę zmian, APK, metadane podpisu i pakietu, `SHA256SUMS.txt`, logi, XML, rendery, audyt zakresu i odtworzenia patcha. Patch odnosi się wyłącznie do zabezpieczonej roboczej bazy. Wydanie B będzie miało osobny patch względem ukończonego A.
