# Android: gotowość konfiguracji i powrót do rozwiązania

Wdrożenie specyfikacji UX v2 z doprecyzowaniami odbioru z 15 listopada 2026. Bazą jest roboczy stan Opcji v2, a nie czysty Git HEAD. HEAD: `f7943a7d7334afd1e4188ef9860b2126331926c8`, `dirty=true`.

## Wynik wdrożenia

Opcje oceniają aktualny formularz, włącznie z niezapisanymi polami przebiegu. Odczyt i zapis korzystają z tej samej normalizacji. Zmiany ustawień, modeli, plików, cyklu życia oraz obserwacji urządzenia unieważniają ocenę; późne wyniki nie zastępują nowszej konfiguracji.

W podsumowaniu występują trzy stabilne stany:

- **Wymaga ustawienia** — znane niespełnione wymaganie, z konkretną akcją.
- **Sprawdzimy przy uruchamianiu** — brak znanej niezgodności, pozostaje potwierdzenie urządzenia lub przygotowanie po START.
- **Gotowe do rozpoczęcia** — wyłącznie przy aktualnych pozytywnych potwierdzeniach wszystkich wymaganych kontroli.

Przeliczanie ma neutralny wskaźnik postępu i usuwa poprzednią deklarację gotowości. Brak przygotowanej sesji ma własne wyjaśnienie i nie kieruje do ponownego importu prawidłowego modelu. Wyłączony warunek temperatury nie jest liczony jako niezweryfikowany warunek.

Akcje wskazują dokładne pole, istniejący wybór modelu/wariantu/wykonania albo istniejący kalibrator. Zapis i anulowanie kalibratora prowadzą przez ekran główny z powrotem do podsumowania; wynik pochodzi z ponownego odczytu danych. Dotyczy to również kalibratorów otwieranych ręcznie z Opcji. Jeżeli obszar fotografii wymaga najpierw zatwierdzenia TV, akcja wyjaśnia zależność i otwiera kalibrator TV. Przejście w tło i blokada zapisu zachowują ręczny powrót, bez automatycznego otwierania Opcji po zwolnieniu blokady.

Zachowano pięć sekcji, animacje 200 ms, wszystkie dotychczasowe identyfikatory kontrolek, kalibrację TV, obszar fotografii, R0/R1/R2, modele, tryby analizy, eksporty i rejestrację badań. Podsumowanie pokazuje najwyżej trzy pozycje oraz „Pokaż wszystkie”. Nazwy narzędzi opisują przeznaczenie; oznaczenia techniczne pozostają w szczegółach i protokołach. Układ XML ekranu głównego nie został zmieniony.

## Reguły i zgodność badań

[Mapa źródeł wymagań](C:/Users/48572/AndroidStudioProjects/ALPR_v1/docs/android_ux_readiness_sources_20261115.md) została spisana przed edycją GUI. Czyste reguły istniejącego kontraktu są współdzielone z dotychczasowymi ścieżkami uruchamiania: zależności strategii, wejścia, Autozoom/Lock, profilu fotografii, wariantów, wykonania, dekodera i metadanych wejścia NCNN. Zachowano dotychczasową warunkową blokadę AF oraz kolejność i komunikaty wyjątków START.

Sama ocena nie przygotowuje sesji, nie uruchamia inferencji i nie inicjalizuje backendu. Nie wywołuje `ensureExperimentPrepared`, `prepareCameraStart` ani całego producenta konfiguracji. Dostępność NCNN odczytuje z wcześniejszej obserwacji; brak obserwacji oznacza kontrolę odroczoną. Integralność modeli i historyczne zatwierdzenia są odczytywane poza wątkiem UI.

Nowe typy gotowości i kontekstu powrotu należą do UI. Nie dodano pól do archiwów, preferencji protokołu ani konfiguracji zamrożonej sesji. Porównano rzeczywisty syntetyczny snapshot protokołu przed i po ocenie: pełna zgodność, bez wyłączania pól. Treść obu istniejących metod serializacji konfiguracji jest zgodna z zachowaną bazą. Materiał syntetyczny nie dowodzi działania modeli ani poprawności ustawienia fizycznego TV.

## Etap 1 — testy i APK

**144/144 testy hostowe PASS**, bez pominięć, we wspólnym końcowym przebiegu `host-final-4`. Zakres zawiera dotychczasowe 50 testów Opcji v2 oraz testy oceny, niezapisanych pól, nawigacji, anulowania, rekreacji, nieaktualnych odpowiedzi, blokady zapisu, wyboru wariantu, reguł wykonania, integralności modeli, START i serializacji. Testy interfejsu obejmują czcionkę 150%.

`:app:assembleDebug` i `:app:compileDebugAndroidTestJavaWithJavac` — PASS. Kompilacja testów urządzeniowych nie oznacza ich uruchomienia. Nie wykonywano pełnej regresji całego repozytorium. Pierwszy rozszerzony przebieg przerwał limit pamięci procesu testowego JVM; końcowy przebieg używał 2048 MiB i odświeżenia procesu co cztery klasy. Logi wcześniejszych prób pozostają w dostawie.

Odtwarzalna komenda: `tools/verify_android_readiness.ps1 -Run <nowa-nazwa>`. Wyniki: `host-final-4/gradle.log`, `host-final-4/test-results/`, `tests-summary.json`. Audyt obejmuje **921 plików app/src**, zachowanie pozostałych plików bazowych i **368 chronionych artefaktów Opcji v1/v2**. Nie wykonano commit/push ani czyszczenia katalogu roboczego.

APK: [alpr-ux-readiness-v1-debug.apk](C:/Users/48572/AndroidStudioProjects/ALPR_v1/app/build/android_ux_readiness_20261115_v1/alpr-ux-readiness-v1-debug.apk), **87 498 869 B**.

```text
SHA-256: 20e47b6c6f96a5c467935032a68766bb6d411f259f54be0ac6f61a96db9e8e10
applicationId: com.example.alpr_v1
versionCode: 1
versionName: 1.0
built_at_utc: 2026-11-15T21:51:19.479496400Z
signer_sha256: f3385991af6f601faf4df59697d30f143101a71ee988035745027d3e85aa04a2
```

Podpis odpowiada aplikacji zastanej na A12. `readiness.patch` i `readiness-sources.zip` zawierają wyłącznie zakres tego wdrożenia względem zachowanej roboczej bazy Opcji v2. Pełną tożsamość źródeł określa manifest SHA-256; patch nie jest przedstawiany jako zmiana względem czystego HEAD.

Trzy obrazy rzeczywistych widoków hostowych, z syntetyczną kamerą:

1. [Podsumowanie z brakującym obszarem](C:/Users/48572/AndroidStudioProjects/ALPR_v1/app/build/android_ux_readiness_20261115_v1/host-ui/photo-01-summary-missing-area.png).
2. [Przejście do istniejącego kalibratora](C:/Users/48572/AndroidStudioProjects/ALPR_v1/app/build/android_ux_readiness_20261115_v1/host-ui/photo-02-existing-calibrator.png).
3. [Powrót po rzeczywistym zapisie fixture](C:/Users/48572/AndroidStudioProjects/ALPR_v1/app/build/android_ux_readiness_20261115_v1/host-ui/photo-03-summary-after-save.png).

## Etap 2 — Galaxy A12

Urządzenie: Samsung SM-A125F, Android 12, czcionka systemowa 100%. Przed aktualizacją zachowano oryginalne APK, cztery pliki preferencji oraz metadane 349 wcześniejszych sesji. Zastana aplikacja pokazywała START, a dziennik nie zawierał aktywnej operacji zapisu. Jeden dawny plik metadanych miał stan RUNNING; nie uznano go za zakończone badanie.

Użytkownik potwierdził, że obecnie dostępny jest sam telefon. Na ostatnim APK zaobserwowano otwarcie istniejącego kalibratora fotografii i powrót do podsumowania po anulowaniu. Kontrola pola obejmuje rzeczywiste przełączenie R0 → R1 → R0, wskazanie dokładnego pola oraz ponowną ocenę po korekcie. Brak przygotowanej sesji nie wywołuje sugestii importowania poprawnych modeli.

**Zapis i odbiór obszaru na rzeczywistym TV pozostają NOT_VERIFIED.** Dotychczasowy napis „Zapisano” w kalibratorze opisuje zastane zatwierdzenie; nie jest wynikiem nowego zapisu podczas testu. Szczegółowy wynik kontroli telefonu, obrazy oraz porównanie zachowanych danych znajdują się w `device/acceptance.json`.

Wynik kontroli telefonu: **PASS_SCOPED**. Zainstalowane APK ma identyczną sumę SHA-256 jak dostarczony plik. Po kontroli 349 wcześniejszych plików `session.json` zachowuje identyczne SHA-256; nie powstała nowa sesja. Wszystkie wartości preferencji powróciły do stanu początkowego poza `settings_revision` (162 → 166), zgodnie z czterema rzeczywistymi zmianami strategii w dwóch kontrolowanych przejściach R0 → R1 → R0. Zachowano również oryginalne rekordy TV i obszaru fotografii.

Trzy najważniejsze obrazy z ostatniego APK na fizycznym A12:

1. [Podsumowanie ze znaną niezgodnością](C:/Users/48572/AndroidStudioProjects/ALPR_v1/app/build/android_ux_readiness_20261115_v1/device/final/07-summary-known-conflict.png).
2. [Przejście do konkretnego pola strategii](C:/Users/48572/AndroidStudioProjects/ALPR_v1/app/build/android_ux_readiness_20261115_v1/device/final/08-exact-control.png).
3. [Podsumowanie po poprawieniu ustawienia](C:/Users/48572/AndroidStudioProjects/ALPR_v1/app/build/android_ux_readiness_20261115_v1/device/final/10-final-summary.png).

[Kalibrator fotografii na A12](C:/Users/48572/AndroidStudioProjects/ALPR_v1/app/build/android_ux_readiness_20261115_v1/device/final/04-photo-calibrator-open.png) i [powrót po anulowaniu](C:/Users/48572/AndroidStudioProjects/ALPR_v1/app/build/android_ux_readiness_20261115_v1/device/final/06-return-after-cancel-settled.png) pokazują rzeczywisty moduł i jego nawigację. Nie uruchamiano instrumentacji Android na telefonie; kontrolę wykonano przez ADB i obserwację rzeczywistych ekranów.

Telefon miał włączony filtr niebieskiego światła (`blue_light_filter=1`). Nie zmieniano ustawień wyświetlania telefonu; obrazy urządzenia przedstawiają jego zastany profil kolorów.

Nie uruchomiono kolejnego eksperymentu ani pokazu 176 fotografii.
