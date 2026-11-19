# F6 Android — precyzja narożników i opisy TV/F6

Data: 13.11.2026. Identyfikator: `F6_ANDROID_CALIBRATION_PRECISION_COPY_20261113_V1`.

**Wdrożono poprawkę wspólnego edytora i zbudowano nową APK. TESTY ANDROIDA: PASS na hoście. UI NA URZĄDZENIU oraz ODBIÓR A12+TV: NOT_VERIFIED. APK nie została zainstalowana.**

## Stan źródeł i zakres

Repo: `C:\Users\48572\AndroidStudioProjects\ALPR_v1`, gałąź `main`, HEAD `f7943a7d7334afd1e4188ef9860b2126331926c8`. Budowa wykorzystuje bieżący brudny katalog roboczy, w tym wcześniejsze F6 i korektę MP. Nie wykonano commit/push, checkout/reset/clean, instalowania zależności, instalowania APK ani operacji na repo Desktopu.

Zmodyfikowane pliki istniejące:

```text
app/src/main/java/com/example/alpr_v1/MainActivity.java
app/src/main/java/com/example/alpr_v1/ui/TvCalibrationMagnifier.java
app/src/main/java/com/example/alpr_v1/ui/TvCalibrationOverlayView.java
app/src/main/java/com/example/alpr_v1/ui/TvReferenceLayout.java
app/src/main/java/com/example/alpr_v1/ui/TvSetupController.java
app/src/main/res/layout/view_tv_reference_shell.xml
app/src/main/res/layout/view_tv_setup_panel.xml
app/src/main/res/values/f6_strings.xml
app/src/main/res/values/main_ui_readability.xml
app/src/main/res/values/tv_setup_strings.xml
app/src/test/java/com/example/alpr_v1/setup/TvCornerEditGeometryTest.java
app/src/test/java/com/example/alpr_v1/ui/MainUiSessionLabelsTest.java
app/src/test/java/com/example/alpr_v1/ui/TvCalibrationMagnifierTest.java
app/src/test/java/com/example/alpr_v1/ui/TvMagnifierNativeCopyTest.java
app/src/test/java/com/example/alpr_v1/ui/TvSetupUiTest.java
```

Nowe pliki: `ui/CalibrationOperatorText.java`, `res/layout/view_tv_corner_precision.xml`, `res/values/tv_calibration_precision_strings.xml`, `ui/CalibrationOperatorTextTest.java` w odpowiednich katalogach main/test oraz ten raport. Pełne ścieżki i SHA: `changed-files.txt`, `source_sha256.json` w katalogu dostawy.

## Audyt i wprowadzone zachowanie

Potwierdzono w kodzie: wybór rogu, strzałki 1 px i grab offset już istniały. Wybór był zwijany, sterowanie przewijane, a lupa korzystała z ruchomego PopupWindow i znikała po puszczeniu palca. Główny PreviewView był na żywo; lupa miała osobny odczyt PixelCopy. Wspólny layout wpisywał nagłówek TV również w trybie F6. Bazowe testy geometrii, chwytu i powtarzania przed zmianami: 10/10 PASS. Nie odtworzono zgłoszenia dotykowo na A12; przyczyny sprzętowej nie dopowiadano.

- Istniejący `TvCalibrationMagnifier` został przekształcony w stały panel. Obraz główny edytora i lupa rysują ten sam bufor istniejącego PixelCopy. Uchwyty są osobnym overlayem, więc nie są powiększane. Kamera i inferencja nie zostały zatrzymane ani przełączone; nie dodano nowej klatki badawczej. Widok edytora aktualizuje bufor co 100 ms.
- Lupa ma 4× i 8× względem podglądu, domyślnie 4×. Celownik ma pusty środek, a brakujący kontekst przy brzegu jest neutralny. Powiększenie nie zmienia punktów ani zoomu kamery.
- LG/PG/PD/LD są dostępne poza obrazem; obok jest pełna nazwa aktywnego rogu. Aktywny uchwyt wyróżnia kształt. Zachowano grab offset; DOWN/UP nie dodają ruchu do już chwyconego punktu.
- Strzałki wykorzystują niezmieniony `TvCornerEditGeometry.nudge`. Krok to 1 px źródła lub pięć kolejnych kroków. Ułamkowe pozycje pozostają ułamkowe. Przytrzymanie: pierwszy krok od razu, po 400 ms powtarzanie co 100 ms. Zmiana rogu, kroku lub lupy kończy gest przy ostatniej pokazanej pozycji; CANCEL, utrata fokusu/geometrii i zamknięcie zatrzymują callbacki. Zachowano anulowanie gestu przy rzeczywistej utracie kontekstu.
- Bufor nadal ma limit 32 MiB dla pary bitmap. Zmiana geometrii/powierzchni unieważnia kopię, a spóźniony callback zwalnia własny bufor. Ukryty lub odłączony edytor nie może wznowić próbkowania. Brak odczytu ma jawny komunikat zamiast obrazu z innej geometrii.
- TV i F6 mają osobne tytuły, opisy, szczegóły oraz przyciski zapisu. Opcje otwierają teraz **Pole fotografii F6**. Krótkie teksty są przy celu, pełne pod **Opis ustawienia**. Techniczne kody pozostają w diagnostyce; operator otrzymuje „Ustaw lub potwierdź pole fotografii F6”. Szczegół rozróżnia brak zapisu od zapisu wymagającego sprawdzenia, bez zgadywania zmiany geometrii.
- Mały ekran i duża czcionka korzystają z przewijania panelu, pozostawiając obraz i dolne działania. Lupa i strzałki pozostają obok siebie. Przy bardzo małym widoku z powiększonym tekstem przycisk zapisu ma skrót **Zapisz TV** / **Zapisz pole F6**, z pełnym opisem dostępności. Na A12 przy standardowym tekście widoczne są jednocześnie obraz, lupa, cztery strzałki, krok i powiększenie. Przy 200% tekstu wybór rogu i krok mogą wymagać przewinięcia panelu; obraz i stopka pozostają na miejscu.

## Zapis, ważność i zgodność

Wszystko działa na istniejącym szkicu. Zatwierdzona kalibracja zmienia się tylko po świadomym zapisie. Anuluj/Back przy zmianach daje jedno pytanie; TV zachowuje swój dotychczasowy prywatny szkic odzyskiwania, a poprzednie zatwierdzone dane pozostają bez zmian. F6 zapisuje tylko swój istniejący klucz i nie nadpisuje TV.

Poprawiono odtworzenie pola F6: porównanie z zapisaną tożsamością TV używa geometrii wejścia do edytora, po sprawdzeniu zgodności rzeczywistego źródła. Samo przestawienie paneli UI nie jest zmianą źródła. Rzeczywista zmiana kamery, rozmiarów, cropu, rotacji, zoomu lub generacji nadal wymaga dotychczasowej walidacji; `F6_PHOTO_ROI_REQUIRED_OR_STALE` nie został poluzowany.

Żółta obwiednia używa istniejącej reguły `TvBboxCrop.bounds`, tak jak zapis ROI, i istniejącego mapowania do podglądu. Czworokąt pozostaje oddzielnym konturem. Nie zmieniono `CameraPreviewGeometry`, `TvCornerEditGeometry`, walidacji kalibracji, digestu, formatów preferencji ani serializacji. `CROP_ONLY_TRANSLATION`, `H_TV=NOT_APPLIED`, rzeczywiste wejście MT i pełna klatka dowodowa zachowują dotychczasową semantykę. Poprawny zapis nie oznacza potwierdzenia wykluczenia kodów z wejścia MT.

Nie zmieniano modeli, konfiguracji R0/MP/FP32/wątków, detektora, NMS, schedulera, śledzenia, konsensusu, timera, eksportera, SourceStamp/frame_key ani powiązań MT/FINAL/MZ. Testy używają prywatnych danych syntetycznych. Nie oceniano fotografii względem GT.

## Testy i odbiór wizualny

Końcowy szeroki przebieg: **1368 testów PASS**, 0 failures, 0 errors, 0 skipped. Wyniki tego przebiegu są w `audit/final_run/`; wcześniejszych przebiegów diagnostycznych nie dodaje się do tego wyniku.

Sprawdzono geometrię przy rotacjach/mirror i pasach, kroki 1/5 px oraz zachowanie ułamków, brak ruchu od wyboru/lupy, brak dodatkowego skoku DOWN/UP, granice bez powtarzanego ruchu, anulowanie i zmianę sterowania podczas przytrzymania. Sprawdzono ten sam obraz w lupie/podglądzie, pusty środek celownika, odrzucenie starych kopii i zakończenie próbkowania po ukryciu edytora. Sprawdzono teksty obu trybów, odzyskanie F6, osobne rekordy TV/F6, jedną decyzję anulowania i nadal obowiązującą odmowę przy zmienionej geometrii. Regresja obejmuje historyczne F5A oraz rzeczywisty silnik z syntetycznymi backendami, w tym identyczne tensory/ROI, zero/wiele FINAL, błędy i późny wynik należący do pierwotnego źródła.

Uruchomiono offline `testDebugUnitTest compileDebugAndroidTestJavaWithJavac assembleDebug`, z `audit/full-regression.gradle`. Testowy JVM ma limit 1 GiB, jeden fork jednocześnie i odnowienie procesu co 25 klas. Nie jest to pomiar pamięci APK. W diagnozie rozbudowanej macierzy widoków wystąpiło hostowe OOM przy domyślnej stercie; limit producenta obrazów pozostał bez zmian. Wyłączono wyłącznie ponowne generowanie metody testowej `longSessionStreamsBeyond4096FramesAnd32MiBWithoutRetainingHistory`; istniejące pliki dużej dostawy zachowano. Instrumentację **skompilowano**, wykonanie: **NOT_RUN**, bez instalacji na A12 ani emulatorze.

Uruchomiono rzeczywiste XML/controller/view aplikacji w Robolectric Native z syntetycznym obrazem i granicą PixelCopy. Obejrzano rendery obu trybów dla **720×1600, 300 dpi**, tekst 100% i 200%. To rendery hostowe aplikacji, nie makiety i nie zrzuty telefonu. Testy dodatkowo obejmują 320×568, 360×800, 411×915, orientację poziomą, oba motywy, czcionki do 200% i paski systemowe.

```text
renders/f6-precision-tv-a12-100.png
renders/f6-precision-photo-a12-100.png
renders/f6-precision-tv-a12-200.png
renders/f6-precision-photo-a12-200.png
```

## APK i ochrona materiałów

Nowa APK:
`C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f6_calibration_precision_20261113_v1\alpr-f6-calibration-precision-debug.apk`

SHA-256: `ff6945f6764516d72b28d3c4c8d2cdc8e3fd01d22e3a7ddf237c8a14b7763695`; rozmiar: 86539120 B. Pakiet: `com.example.alpr_v1`, debug. Weryfikacja podpisu: PASS; certyfikat zgodny z poprzednią lokalną APK. Publiczny SHA certyfikatu: `f3385991af6f601faf4df59697d30f143101a71ee988035745027d3e85aa04a2`. Tożsamość pliku APK zmieniła się, ponieważ zmieniono kod UI i zasoby. Nie instalowano go; obecna instalacja telefonu nie została przez agenta zaktualizowana.

Materiały: `app/build/f6_calibration_precision_20261113_v1/`. `validation.json`, `source_sha256.json`, `protected-files-audit.json`, `SHA256SUMS.txt` i `READY.json` opisują końcową dostawę. Stan wejściowy i lokalna kopia poprzedniej APK znajdują się w `audit/`. Raport źródłowy: `docs/f6_android_calibration_precision_20261113_v1.md`.

SHA potwierdziły zachowanie 543 plików poprzednich dostaw F6/MP, w tym odebranej MP v1, archiwów i raportów. Zastane pliki poza wymienionym zakresem pozostały niezmienione. Nie pisano do telefonu, jego modeli, ustawień, kalibracji ani sesji. Zachowanie zatwierdzonych preferencji i rozdzielenie TV/F6 sprawdzono na prywatnych preferencjach testowych; kompletność na telefonie po aktualizacji sprawdzi operator. Nie wykonano migracji, czyszczenia danych, zmian GT ani tekstu pracy.

## Instrukcja operatora i status końcowy

1. Zaktualizuj aplikację otrzymaną APK jako ten sam pakiet, bez odinstalowania starej aplikacji. Sprawdź zachowane modele, wybraną konfigurację, ustawienia i dane. Android pozostaje **Bez timera**.
2. Na PC wyświetl nieruchomą planszę FINAL. W Androidzie otwórz **Kalibracja ekranu TV**, ustaw kamerę/zoom i przejdź do rogów całej powierzchni ekranu, łącznie z obrzeżem kodów, bez obudowy.
3. Wybierz LG/PG/PD/LD poza obrazem, przeciągnij uchwyt zgrubnie, puść i dopasuj strzałkami. Wybierz 4×/8× i krok 1/5 px źródła. Po dopasowaniu wszystkich rogów wybierz **Zapisz kalibrację TV**.
4. Otwórz **Pole fotografii F6**. Ustaw raz całe stałe pole zdjęć na planszy FINAL. Sprawdź czworokąt oraz żółty prostokąt: całe pole zdjęć w środku, kody poza nim. Czarne pasy proporcji mogą pozostać wewnątrz. Wybierz **Zapisz pole fotografii**; nie dopasowuj pola do każdego zdjęcia.
5. Po aktualizacji sprawdź obsługę narożników na A12 oraz kompletność ustawień. Rzeczywistą widoczność kodów w pełnej zapisanej klatce i ich brak w wejściu MT odbierz osobno na stanowisku Desktop + A12 + TV.

| Status | Wynik |
|---|---|
| TESTY ANDROIDA | PASS — JVM, kompilacja instrumentacji, debug APK |
| Wizualizacja hostowa | PASS w opisanym zakresie syntetycznym |
| UI NA URZĄDZENIU | NOT_VERIFIED; APK nie instalowano |
| ODBIÓR A12+TV | NOT_VERIFIED |

Nie wykonano próby dotykowej, pracy na prawdziwej planszy TV, odbioru rzeczywistych kodów/klatki/wejścia MT, pokazu 176×5 s ani testu cieplnego. Ta dostawa jest gotowa do aktualizacji przez operatora i odbioru stanowiska; nie deklaruje pełnego GO eksperymentu FINAL.
