# Poprawka wizjera Main — v1

Zakres zatwierdzony 15.11.2026, wykonanie i walidacja 16.11.2026. Katalog wydania zachowuje datę zatwierdzonego zadania: `app/build/android_main_viewfinder_fix_20261115_v1`.

## Wynik i obsługa

Main wykorzystuje pełną wysokość ekranu po ukryciu pasków systemowych. Nie zachowuje globalnego pustego pasa na status bar i nawigację. Fizyczne wycięcie ekranu chronione jest lokalnie: tytuł nie wchodzi w środkowe wycięcie, a boczne wycięcia odsuwają zawartość paska i przyciski. Wysunięte gestem paski nakładają się na zawartość; nie zmieniają wymiarów wizjera. Opcje, dialogi oraz edytory TV/obszaru zdjęcia zachowują własne bezpieczne marginesy. Po wyjściu z kalibratora Main wraca do pełnej wysokości.

„Archiwum sesji gotowe” nie tworzy karty na obrazie kamery. Postęp bieżącego zapisu zajmuje istniejący nagłówek i cienki wskaźnik na jego dolnej krawędzi. Stan kamery podczas analizy i zakończenie timera zachowują pierwszeństwo. Błąd lub niepotwierdzony zapis daje zwięzłą informację w drugim wierszu nagłówka. Trwały opis operacji jest dostępny przez menu **Stan zapisu sesji**; zachowano dziennik operacji i **Sesje / Eksport**.

Przykłady komunikatów kamery:

| Stan | Komunikat | Następny krok |
|---|---|---|
| Podgląd wyłączony | Kamera jest wyłączona | Włącz Podgląd, aby zobaczyć obraz |
| Trwa otwieranie obiektywu | Uruchamianie kamery… | Poczekaj na obraz |
| Brak nowego obrazu | Czekam na nowy obraz z kamery… | Sprawdź podgląd |
| Zmiana konfiguracji | Zmieniono ustawienia kamery | Sprawdź podgląd przed START |
| Geometria niezgodna z TV | Sprawdź ustawienie kamery względem TV | Ustaw kamerę i zatwierdź narożniki TV |

Mapowanie znajduje się na granicy prezentacji (`CameraOperatorPresentation`). `CameraSetupReadiness.Reason.description()`, identyfikatory powodów, porównania dopuszczenia START, rejestrowanie i kontrakty eksperymentów pozostają bez zmian. Nie usunięto kalibracji TV, obszaru zdjęcia, R0/R1/R2, modeli, trybów analizy, eksportów ani zapisu badań.

## Walidacja hosta

**PASS: 180/180 unikalnych testów**, bez pominięć. Zachowano wszystkie selektory wcześniejszych 144 testów; rozszerzenie obejmuje komunikaty kamery, układ Main, menu operacji i timer. Wynik zbiorczy: 173 testy z pełnego przebiegu oraz 7 powtórzonych testów okna i nagłówka. `tests-summary.json` zawiera listę klas i pochodzenie wyników.

Pełny przebieg `host-final-3` zakończył się wynikiem 179/180: symulowane okno bez dołączenia do ekranu zwracało nieprawidłowe współrzędne tytułu przy czcionce 150%. Po poprawieniu przygotowania tego okna powtórzono obie klasy korzystające ze wspólnego narzędzia układu: `host-insets-final`, **7/7 PASS**. Między przebiegami zmienił się tylko plik testu `MainViewfinderInsetsTest.java`; wszystkie źródła produkcyjne pozostały identyczne. Surowy wynik nieudanego przebiegu i porównanie skrótów zachowano.

**PASS:** `assembleDebug` i `compileDebugAndroidTestJavaWithJavac` w końcowym przebiegu. Instrumentacji na telefonie nie uruchamiano; sprawdzenie A12 opisane poniżej obejmuje rzeczywistą obsługę interfejsu.

Uaktualniono dwa istniejące testy prezentacji: współrzędne wskaźnika zapisu są sprawdzane względem stałego nagłówka, a wyłączony podgląd ma teraz jednoznaczny komunikat z działaniem. Test stałości układu timera mierzy bazę po odświeżeniu wybranego trybu pracy; zachowano wszystkie sprawdzenia ustawień czasu i granic wizjera. Kontrole układu Main obejmują Android 10, 12 i 16 oraz czcionki 100% i 150%.

Polecenie odtworzenia: `powershell -NoProfile -File tools/verify_android_main_viewfinder.ps1 -Run <nowy-katalog>`.

Rastry hosta są syntetyczne i mają osobny katalog `host-ui`. Nie są dowodem działania rzeczywistej kamery.

## Galaxy A12

**PASS w opisanym zakresie:** SM-A125F, Android 12, 720 × 1600. APK zainstalowano przez aktualizację z zachowaniem danych. Otworzono podgląd, menu szczegółów operacji, oba kalibratory oraz Opcje; powroty przywracały pełną wysokość Main. Końcowy ekran: Main, kamera wyłączona, HUD 1 i HUD 2 włączone.

| Pomiar na A12 | Przed | Po |
|---|---|---|
| Górna krawędź paska aplikacji | 68 px | 0 px |
| Dolna krawędź panelu sterowania | 1510 px | 1600 px |
| Granice wizjera | `[0,278–720,880]` | `[0,210–720,970]` |
| Wysokość wizjera | 602 px | 760 px |
| Karta zakończonego zapisu na wizjerze | widoczna | GONE |
| Wyłączony podgląd | `CAMERA_NOT_BOUND` | „Kamera jest wyłączona” + działanie „Włącz Podgląd” |

Zapis stanu przed i po instalacji: **349/349 plików `session.json` identycznych bajtowo** (97 COMPLETED, 231 PARTIAL, 20 ERROR, 1 historyczny RUNNING). Wszystkie zatwierdzone ustawienia i zapisane obszary pozostały bez zmian, podobnie ustawienia ekranu telefonu. Istniejący edytor odświeżył wyłącznie czas, skrót i numery wiązania/rewizji w roboczym szkicu TV; narożniki i materialna geometria szkicu są identyczne. Pełny zapis różnicy: `device/preservation.json`.

Dowody względem katalogu wydania:

- `device/before-main.png` i `device/main-final.png`: porównanie Main.
- `device/operation-menu-stable.png` i `device/operation-details.png`: dostęp do szczegółów zapisu.
- `device/tv-editor.png`, `device/photo-editor.png`, `device/options-summary-stable.png`: zachowane narzędzia i marginesy.
- `device/after-tv-editor-layout.json`, `device/after-photo-editor-layout.json`, `device/after-options-stable-layout.json`: powrót do granic wizjera `[0,210,720,970]`.

Przejściowe paski systemowe: **PASS na hoście; NOT_VERIFIED na A12**. Wstrzyknięte gesty ADB nie spowodowały widocznego pokazania pasków. Podczas tych prób wymiary wizjera i wiązanie kamery nr 3 pozostały stałe. Nazwy zrzutów prób zawierające `bars-visible` nie stanowią potwierdzenia widoczności pasków. Ręczne pytanie do operatora pozostaje bez potwierdzenia.

Zainstalowana baza: APK readiness v1, SHA-256 `20e47b6c6f96a5c467935032a68766bb6d411f259f54be0ac6f61a96db9e8e10`. Nowe APK: **88 289 217 B**, SHA-256 **`a882a11aa24341e2283a591b3c52847466f87ea0d36ebb5a275da073243544d4`**. Certyfikat debug pozostaje ten sam; skrót nowego APK jest zgodny z plikiem zainstalowanym na telefonie.

Użytkownik potwierdził dostępność wyłącznie telefonu. Zapis geometrii na rzeczywistym obrazie TV pozostaje **NOT_VERIFIED**. Nie uruchamiamy START ani eksperymentu na 176 fotografiach.

## Pochodzenie i ochrona wcześniejszych wyników

Baza jest istniejącym brudnym katalogiem roboczym po wdrożeniu readiness v1, a nie czystym Git HEAD. HEAD: `f7943a7d7334afd1e4188ef9860b2126331926c8`. Przed zmianami zapisano 1128 skrótów plików i kopię źródeł; osobno chronionych jest 822 plików wcześniejszych wydań Opcji v1/v2 i readiness v1. Audyt potwierdził SHA-256 oraz mtime tych plików, brak obcych zmian oraz zgodność wszystkich 926 plików źródłowych z testowanym zestawem. Sześć metod dopuszczenia START i serializatory konfiguracji są identyczne z bazą. Pakowanie dodatkowo sprawdza zastosowanie patcha w prywatnym repozytorium.

Pliki wydania: APK, `main-viewfinder.patch`, `main-viewfinder-sources.zip`, `READY.json`, `SHA256SUMS.txt`, logi testów, dowody telefonu i kopia tego raportu. Ostateczny skrót APK znajduje się w `READY.json`; zgodność z zainstalowaną aplikacją potwierdza `device/after/state.json`.
