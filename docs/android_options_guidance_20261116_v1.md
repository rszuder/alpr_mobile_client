# Opcje — karta wskazówek i wyróżnienia v1

Wdrożenie zatwierdzonego wariantu krótkiej karty działań, 16.11.2026. Katalog wydania: `app/build/android_options_guidance_20261116_v1`.

## Widoczny efekt

Stan i wskazówki mają własną kartę. Początkowo widoczne są maksymalnie trzy krótkie wiersze, a pełne wyjaśnienia rozwija przycisk **Szczegóły**. Pod kartą znajdują się odstęp 24 dp, nagłówek **Ustawienia** i separator. Zachowano pięć dotychczasowych sekcji.

| Znaczenie | Karta sekcji i właściwe pole |
|---|---|
| Wymaga poprawienia | Bursztynowe oznaczenie i etykieta „Do ustawienia” |
| Kontrola przy uruchamianiu | Niebieskie oznaczenie i etykieta „Przy uruchamianiu” albo „Po START” |
| Brak uwag do danego miejsca | Zwykły wygląd |

Przy dwóch uwagach z przekazanego zrzutu oznaczone są **Kamera i kalibracja**, **Modele i wykonanie**, **Sesja i zapis**. Jedna uwaga może dotyczyć kilku sekcji; licznik podsumowania nadal liczy uwagi. Przygotowanie modeli i zapisu jest opisem czynności aplikacji po START i nie proponuje ponownego importowania poprawnych modeli.

Znany brak zachowuje bezpośrednie działanie do istniejącego pola, wyboru modelu lub kalibratora. Wyróżnienie konkretnego pola pozostaje po zniknięciu chwilowej ramki fokusu. Po poprawieniu ustawienia znika oznaczenie tej przyczyny; inne uwagi pozostają aktualne. Rozwijanie opisu nie otwiera sekcji ani nie zapisuje ustawień. Stan rozwinięcia opisów jest odtwarzany po rekreacji ekranu.

## Walidacja

**193/193 PASS**, bez błędów i pominięć, pełny przebieg `host-final` (7 min 16 s). Zachowano wszystkie selektory poprzednich 180 testów i dodano 13 testów projekcji oraz interfejsu. Nowe testy obejmują powiązania uwag z sekcjami, brak fałszywych działań importu, poprawę ustawienia, niezapisany formularz, rekreację i macierz czcionek 100%/150% oraz orientacji. Wcześniejsze kontrole ukierunkowane: `focused-1` 13/13 i `focused-existing` 28/28.

Budowa `assembleDebug` i kompilacja `compileDebugAndroidTestJavaWithJavac`: **PASS**. Testów instrumentacyjnych nie uruchamiano; odbiór A12 poniżej obejmuje rzeczywistą obsługę aplikacji. Jeden istniejący test układu uaktualniono do sześciu kart: pięć zachowanych sekcji i nowa, osobna karta wskazówek.

Ostateczny wynik i pełne listy klas znajdują się w `tests-summary.json` i katalogu końcowego przebiegu. Testy hosta i rastry syntetyczne nie stanowią odbioru urządzenia.

## Galaxy A12

**PASS w sprawdzonym zakresie**, SM-A125F, Android 12, 720 × 1600, czcionka 100%. Zainstalowano APK jako aktualizację z zachowaniem danych i tym samym certyfikatem. Potwierdzono:

- osobną kartę, oddzielny nagłówek Ustawienia i niebieskie oznaczenia trzech właściwych sekcji;
- rozwijanie i zwijanie pełnego opisu;
- bursztynową wskazówkę po kontrolowanym włączeniu Autozoomu dla wejścia TV;
- przejście przyciskiem wskazówki do rzeczywistego przełącznika, z trwałym obramowaniem;
- usunięcie bursztynowej wskazówki po wyłączeniu Autozoomu, z zachowaniem dwóch informacji o kontrolach przy uruchamianiu;
- powrót do podsumowania i ponowne otwarcie Opcji.

349/349 plików `session.json` pozostało identycznych bajtowo. Zachowano zatwierdzoną geometrię i pozostałe ustawienia. Kontrolne przełączenie przywróciło efektywną konfigurację; zapisało jawne `research_autozoom_enabled=false` (wcześniej brak klucza oznaczał tę samą wartość domyślną) i zwiększyło `settings_revision` z 167 do 169. Pozostałe wartości preferencji i ustawienia wyświetlania telefonu są niezmienione. Szczegóły: `device/preservation.json`. Telefon pozostawiono na nowym podsumowaniu Opcji, z zamkniętymi sekcjami.

Nie uruchamiano START ani eksperymentu na 176 fotografiach. TV jest niedostępny; zapis geometrii na rzeczywistym obrazie pozostaje **NOT_VERIFIED**.

Dowody względem katalogu wydania:

| Widok | Plik |
|---|---|
| Poprzedni ekran | `device/before-options.png` |
| Nowa karta i oznaczenia | `device/01-options-deferred-stable.png` |
| Opis rozwinięty na żądanie | `device/02-details-expanded.png` |
| Znana niezgodność i działanie | `device/03-known-problem.png` |
| Konkretne wyróżnione ustawienie | `device/04-exact-setting.png` |
| Powrót po poprawieniu | `device/05-after-correction.png` |
| Końcowy ekran po ponownym otwarciu | `device/07-current-screen.png` |

Zachowano oryginalnie zainstalowany APK SHA-256 `a2d44495e4e560438e05e88e6860b5e148f289219ae1b2f55cd97be02918cdd8`. Nowy APK: **87 538 729 B**, SHA-256 **`e07000b4e156f202d489dc5c77fb3e7f539b38346d07a57fbdd09c21cfe1523a`**, zgodny z plikiem zainstalowanym na telefonie.

## Zakres i pochodzenie

Nowa `ReadinessPresentation` jest wyłącznie projekcją UI istniejącego wyniku oceny. Nie zmienia reguł oceny, warunków START, formatów preferencji ani sesji. Główne zachowania wdrożono w widoku wskazówek, kontrolerze sekcji i układzie Opcji. Zachowano kalibratory, modele, R0/R1/R2, tryby analizy, eksporty i zapis badań oraz pełną wysokość Main.

Baza: bieżący brudny katalog roboczy, HEAD `f7943a7d7334afd1e4188ef9860b2126331926c8`, 1137 plików. Ochroną SHA-256 i mtime objęto 1135 plików wcześniejszych wydań. Audyt potwierdził brak obcych zmian i zgodność 930 plików źródłowych z pełnym przebiegiem testów. Kod oceny, kontroler jej odświeżania, cele nawigacji, Main i obsługa marginesów okien są identyczne z bazą. Nie wykonywano resetu, czyszczenia, zmiany indeksu ani commitu. Pakowanie sprawdza odtworzenie patcha względem zachowanej bazy.

Wydanie zawiera APK, `options-guidance.patch`, `options-guidance-sources.zip`, raport, dowody, `READY.json` oraz `SHA256SUMS.txt`. Uruchomienie sprawdzeń: `tools/verify_android_options_guidance.ps1 -Run <nowy-katalog>`.
