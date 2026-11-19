# DYN: dwa pojazdy, postój i wznowienie ruchu

## v5.13 — ciągłość odzyskana, płynność nadal NO_GO

Operator potwierdził, że oba pojazdy pozostawały w kadrze. Log poprzedniej v5.12 obejmował serię `abrupt_unexplained_direct_luma_change`, kolejne numery oraz utratę obu właścicieli przez `pixel_gap_expired`. Dokładną bazę 972 źródeł i log operatora zabezpieczono przed zmianami.

W zwykłym DYN ocena lokalnych pikseli poprzedza decyzję o zmianie sceny. `LocalVehiclePoolEvidence` wiąże stan każdego właściciela z konkretnym źródłem i epoką. Poprawne śledzenie nieruchomego obiektu oraz przypisanej tablicy potwierdza obecność; niedostępność i replay nie są nieobecnością. Nie są też pomiarem MP/MT. Żądanie kontrolnego MP nie zeruje zdrowych trackerów. Korekta MP zawierająca tylko jeden ze śledzonych obiektów zachowuje lokalny tor sąsiada. Miękkie przestawienie referencji ruchu zachowuje ograniczoną historię pikseli, z dotychczasowymi bramkami kontekstu i replay. Rozbieżności MZ nie zmieniają właściciela.

Regresja sceny przed poprawką: 2 FAIL / 1 PASS. Końcowe źródła v5.13: **454/454 PASS**, `assembleDebug` i `compileDebugAndroidTestJavaWithJavac` PASS. Kompilacja instrumentation nie oznacza jego wykonania. Pierwszy test źródła referencji ujawnił dalsze czyszczenie historii; usunięto je przed końcowym przebiegiem. Test realnego KLT i natywnego HUD obejmuje dwa obiekty, ośmiosekundowy postój, ponowny ruch oraz częściową listę MP. To syntetyczne piksele, nie materiał z kamery ani odbiór drogowy.

APK v5.13: `alpr-dynamic-continuity-20261118-v5.13.apk`, SHA-256 `c0ccc70496a8d3d1779dcb112dbb65721c1329c60beebc568d98d4a1d890cfc1`. Pieczęć `release-v5.13` obejmuje 975 źródeł: osiem istniejących plików zmienionych, trzy dodane, 964 zachowane względem dokładnej bazy v5.12.

## Rzeczywista próba A12 v5.13 bez mirroringu

START 13:56:13, kwalifikacja 13:56:34, koniec pomiaru 13:59:34, STOP potwierdzony 13:59:43. Okno **180,016 s**, stały PID 3119, run 2 i scena 2. Wszystkie kontrole potwierdziły brak mirroringu; RAM podczas analizy około 1,32–1,69 mln KiB. Nie odnotowano pauzy pamięci ani AndroidRuntime.

184 publikacje od stanu kwalifikacji: wyłącznie P1 i P2, bez zaniku ramki pojazdu lub tekstu, bez utraty tożsamości i resetu sceny. Wystąpiło pięć krótkich braków pojedynczej ramki tablicy: 0,113 / 0,020 / 0,282 / 0,108 / 0,073 s. Nie zamieniono ich na wynik pozytywny.

Operator zaznaczył pozytywną opcję, ale dopisał: „podążają i nie znikają ale nie jest to ruch płynny”. Dopisek rozstrzyga ocenę: ciągłość i numery **PASS_DESCRIPTIVE**, płynność **NO_GO**, łączny odbiór dynamiczny **NO_GO**. Surową odpowiedź przypisano do dokładnego APK i próby w `operator_feedback.json`.

| Model | Wykonania w kwalifikowanym oknie | Mediana INF | Mediana PRE + INF + POST |
| --- | ---: | ---: | ---: |
| MP | 53 | 851,387 ms | 1801,279 ms |
| MT | 53 | 741,962 ms | 1143,191 ms |
| MZ | 14 | 971,655 ms | 1132,840 ms |

Ostatni MZ poprzedzał koniec okna o 83,990 s; MP i MT nadal kończyły analizy. Sam ten odstęp nie ustala przyczyny braku dalszego MZ. Późny log kolejki zawiera `scan_deferred_mt_attempts_exhausted`. GT OCR pozostaje N/A.

Galeria rzeczywiście pokazuje dwa wpisy: P2 z dziewięcioma obserwacjami i P1 z siedmioma. Archiwum ma 16 nowych obserwacji w tych dwóch encjach, mimo 15 surowych wariantów tekstu. Wszystkie 848 wcześniejszych identyfikowalnych obserwacji zachowano bez zmian. Instalacja zachowała 445 kontrolowanych plików danych, w tym pięć plików preferencji i 335 pierwotnych plików cropów; po próbie zgodne były wszystkie 38 plików modeli.

Dowody: `app/build/android_dynamic_continuity_20261118_v5/device/without-mirroring-v5.13-two-vehicles/`. Pierwszy zrzut nazwany `gallery_opened.png` pokazuje jeszcze ekran główny; faktycznie otwartą galerię pokazuje późniejszy `gallery_verified.png`.

## v5.14 — kontynuacja naprawy płynności

Mediana całej obsługi lekkiej klatki wyniosła około 245,579 ms, w tym ruch globalny 155,258 ms i lokalny tracking 89,281 ms; mediana opóźnienia wejścia 236,472 ms. To pomiary A12, niezależne od czasów inferencji modeli.

Dodatkowa regresja prezentacji wykazała skok lokalnej geometrii pomiędzy wywołaniami MP: przed poprawką 1 FAIL. Wygładzanie obejmuje teraz każde nowe źródło lokalnej geometrii, z odpowiedzią 60 ms; korekty neuralne zachowują 120 ms. Tablica podąża z tą samą prezentowaną ramką pojazdu. Rysowanie korzysta z callbacku odświeżania ekranu. Nie wydłużono czasu życia geometrii ani dowodu tożsamości.

Estymacja affine kończy szukanie hipotez po uzyskaniu pełnej zgodności wszystkich dopasowań. Końcowe dopasowanie najmniejszych kwadratów nadal wykorzystuje ten sam pełny zbiór; test numeryczny potwierdza identyczne współczynniki. Progi, testy sprzeczności, walidacja lokalna i droga obsługi outlierów pozostają obecne. Zestaw tej zmiany: **35/35 PASS**. Końcowe źródła v5.14: **456/456 PASS**, APK i kompilacja testów Android PASS. SHA-256 APK: `2834bf46d8f158854de57cb88207096b3050efc78e36d2e18eaacf1803c3fe2c`.

## v5.14 — rozdzielenie spokojnego okna i późniejszej próby ruchu

Kontrolowany przebieg bez mirroringu: START 14:17:58, kwalifikacja 14:18:21, koniec 14:21:21, STOP potwierdzony 14:21:30. Okno 180,032 s, PID 5120, run 2 / scena 2. 177 publikacji: te same P1 i P2, bez braków pojazdu, tablicy lub tekstu, utraty tożsamości i resetu sceny. Ten zapis ma PASS_DESCRIPTIVE dla opublikowanego stanu. Nie zawiera próbek zaakceptowanego globalnego ruchu kamery, więc nie stanowi odbioru fizycznej płynności. W kwalifikowanym oknie MP / MT / MZ: 48 / 48 / 26 wykonań; mediany INF 854,664 / 728,651 / 969,850 ms. W archiwum całego przebiegu, z rozruchem i zatrzymywaniem, przybyło 31 obserwacji: encja 1 — 16, encja 2 — 15. Zachowano wszystkie 864 wcześniejsze obserwacje. Instalacja zachowała 461 kontrolowanych plików, a po próbie potwierdzono zgodność 38 plików modeli.

Po tym STOP ponownie zaobserwowano aktywną analizę. Osobny log `device/v5.14-operator-followup/` zawiera kolejne run 3 / scena 4 i run 4 / scena 6, 121 próbek globalnego ruchu oraz trzy utraty lokalnego dowodu obecności. To odrębne przebiegi; nie dopisuje się ich do zamkniętego trzyminutowego okna. O 14:26:45 potwierdzono już zatrzymaną analizę, bez wysyłania kolejnego STOP.

Operator odpowiedział: „poprzednia wersja raddziła sobie nieco lepiej, ramki czasem znikają ale same obiekty po ich przywróceniu zachowująnumer ramki pojazdu - płynność jest słaba”. Dopisek nie potwierdza zmiany numeru po przywróceniu. **v5.14 pozostaje NO_GO dla fizycznej płynności i zaników.** Odpowiedź przypisano do tej wersji i osobnego audytu późniejszych przebiegów; nie przedstawia się spokojnego okna jako udanej próby ruchu.

Cała obsługa lekkiej klatki v5.14 miała medianę 251,739 ms, ruch globalny 141,495 ms i lokalny tracking 108,870 ms. Nie ma dowodu poprawy całkowitej przepustowości. Porównanie z v5.13 jest opisowe: ten sam materiał fotograficzny, ale inne ujęcie i aktywność modeli.

## v5.15 — dalsza praca nad kosztem lokalnego trackingu

Zabezpieczono pełną bazę v5.14 oraz dane późniejszych przebiegów. Dodano szybszą wewnętrzną drogę interpolacji pikseli, zachowując wzór biliniowy i dotychczasowe zachowanie brzegu. Test potwierdził identyczne bity wyników z poprzednim wzorem dla 4000 współrzędnych. Dodatkowy pomiar rozdziela flow, affine i fallback ruchu globalnego, zachowując znacznik tej samej klatki. Nie wydłużono czasu życia geometrii, nie obniżono progów lokalnych ani progu MP 0,25.

`release-v5.15`: **234/234 PASS** dla trackerów, prezentacji i granic właściciela; APK i kompilacja testów Android PASS. To odrębny zakres od 456 testów wcześniejszej v5.14, bez sumowania ich jako jednego przebiegu. Zainstalowano APK `alpr-dynamic-continuity-20261118-v5.15.apk`, SHA-256 `248f19f10a25aa0301ec35db8d8fb30ab9254b29582a80e4b4abfc51844bfcb8`, z pieczęcią 976 źródeł. Zapis diagnostycznych tensorów nie jest uzbrojony przy zwykłym uruchomieniu. Fizyczna próba v5.15 czeka na potwierdzenie gotowości operatora; analiza jest zatrzymana. Wdrożenie nie jest jeszcze odbiorem płynności.


## v5.15: wynik operatora i poprawka v5.17

Próba v5.15 bez mirroringu: kwalifikacja 14:38:37, koniec 14:41:37 (180,047 s), STOP potwierdzony 14:41:45. Operator potwierdził płynne podążanie i zachowanie numerów przy pełnej widoczności, ale zgłosił zanik przy częściowym wyjściu auta oraz zastępowanie wcześniejszego odczytu MZ słabszym. Całość pozostała NO_GO. W logu 185 publikacji stanu, 427 próbek ruchu globalnego, dokładnie dwa utworzenia tożsamości, odzyskanie tych samych numerów, bez resetu sceny i AndroidRuntime. Publikacje obejmują też stany pośrednie; nie utożsamiono każdej logicznej przerwy z widocznym zanikiem. Mediana lokalnego trackingu 54,217 ms; mediana całego pomiaru z ostatnich 300 próbek około 205,710 ms. MP/MT/MZ w oknie: 55/54/7, mediany INF 856,427/744,405/978,742 ms. Dziewięć nowych zapisanych obserwacji (P1: 2, P2: 7), 902 wcześniejsze bez zmian; 38 plików modeli bez zmian.

Operator zatwierdził tylko potwierdzony wynik od 60% na badge oraz jedną pozycję galerii na obiekt z najlepszym skumulowanym wynikiem, zastępowanym dopiero przez lepszy. Oryginalną odpowiedź zachowuje operator_policy.json. Wskaźnik MZ nie jest skalibrowanym prawdopodobieństwem. Surowe próby pozostają dowodami analizy i eksportu, bez tworzenia dodatkowych wyników w dynamicznej galerii.

### v5.16: optymalizacja hosta, bez instalacji

235/235 PASS, APK i kompilacja instrumentation PASS. Wykluczenie pierwszego planu w translacji jest obliczane raz na klatkę i współdzielone przez oba kierunki. Test 315 przypadków porównuje bity z poprzednią formułą; kryteria maski celu i progi pozostają identyczne. APK SHA-256: 4dabfaced5142062988fd68e5dbd0e4d5181af4a4659157440827b54f80d1523. Wersja nie była instalowana; dalszy zakres wszedł do v5.17.

### v5.17: częściowa widoczność

Wyłącznie zwykły dynamiczny tracker pojazdu porównuje cechy w ich pozycjach względem pełnego auta, z pominięciem niewidocznych pól. Wymaga co najmniej połowy widocznego obszaru i dostatecznej liczby wspólnych pól oraz dotychczasowego lokalnego flow, podobieństwa, skali i ograniczenia skoku. Pełny czworokąt zachowuje współrzędne poza obrazem, prostokąt rysowania jest przycinany. Przycięty MP przy brzegu nie zawęża pełnego układu pojazd–tablica nadal potwierdzonego pikselami. Niezależna widoczna tablica nadal może potwierdzić swojego właściciela. Nie wydłużono TTL ani nie uznano predykcji za dowód. Pełny zanik nadal wymaga odzyskania, a nie bezterminowego rysowania pustej ramki.

Regresja przed poprawką: FAIL w 38. klatce (podobieństwo 0,443). Po poprawce: 90 klatek wyjścia i powrotu PASS; pusty obraz nie odnawia dowodu. Osobny test wykrył przesunięcie tablicy po przyciętej korekcie MP (oczekiwane x=0,053, otrzymane 0,117); końcowy test pełnego układu z taką korektą PASS. To rzeczywisty lokalny tracker na syntetycznych pikselach, nie dowód drogowy.

### v5.17: najlepszy odczyt i jedna karta

VehicleEntity przechowuje bieżący konsensus osobno od najlepszego agregatu i najlepszego potwierdzonego wyniku prezentacji. Sprzeczne aktualne dowody nadal mogą obniżyć bieżącą ocenę i zmienić wynik; historyczne maksimum nie zamraża identyfikacji/wyszukiwania. Badge i HUD 2 wybierają rzeczywisty potwierdzony agregat co najmniej 60% z minimum dwiema obserwacjami; słabszy pozostawia Pn i Weryfikacja. Nowy obiekt/run nie dziedziczy odczytu sąsiada ani innego obiektu.

Galeria zachowuje jedną kartę właściciela. Lepszy agregat zastępuje wynik i zdjęcie wraz z rzeczywistymi znakami, czasami i metadanymi tej próby. Słabszy nie obniża najlepszego wyniku. Szczegóły DYN pokazują jeden najlepszy numer i jedno zdjęcie, bez listy słabych odczytów lub stron surowych identyfikatorów. Jedyna akcja zdjęcia odnosi się do zwycięskiej próby. Na zdjęciach DYN ukryto surowe oznaczenia znaków i dodatkowe procenty; oryginalne piksele i metadane nie są zmieniane. Statyczna galeria zachowuje swoje nakładki. Nie usunięto ani nie przepisano surowych obserwacji/archiwów.

### Walidacja hosta i instalacja

Pierwszy szerszy przebieg: 521 PASS / 3 FAIL. Oczekiwania dotyczące podmiany przy identycznej ocenie, najnowszych 50% zamiast najlepszych 80% oraz listy 32 surowych prób dostosowano do zatwierdzonej polityki. Powtórzony release-v5.17-sealed: **524/524 PASS**, APK oraz kompilacja instrumentation PASS. Kompilacja nie oznacza wykonania testów instrumentation.

Oględziny natywnego widoku przy czcionce 150% ujawniły surowe oznaczenia znaków z drugim procentem na zdjęciu. Końcowa korekta dwóch plików UI (MainActivity, RecognitionHistoryAdapter) wyłączyła je tylko dla galerii DYN. Logika trackingu, kumulacji, właścicieli i historii pozostaje zgodna z szeroką pieczęcią. Ostateczny presentation-v5.17-final: **19/19 PASS**, ponowny render czcionek 100%/150%, APK i kompilacja instrumentation PASS. Porównanie do szerokiej pieczęci: audit/presentation_v5.17_final_binding.json.

APK v5.17: alpr-dynamic-continuity-20261118-v5.17.apk, SHA-256 d937997d734674e4f8fb66569bb94f8075e73f93239012f14651313c7ea64ccf. 981 źródeł; względem v5.16: 19 zmienionych istniejących, cztery dodane, 958 zachowanych. Instalacja na A12 potwierdziła ten sam SHA, pięć zachowanych preferencji, wszystkie 500 plików sprzed instalacji oraz 335 pierwotnych plików cropów. Dane i modele zachowano.

Kontroler v5.17 wymaga dwóch pojazdów, dwóch tablic, rzeczywistych prób MZ dla obu rysowanych właścicieli oraz MP/MT/MZ. Nie wymaga wyświetlenia słabego tekstu. Siedem testów kontrolera PASS. Po jawnej gotowości operatora uruchomiono próbę bez mirroringu: START 15:37:43, kwalifikacja dwóch par z próbami MZ 15:38:04. Na ekranie P1 i P2, dwie tablice, Weryfikacja oraz rzeczywiste czasy modeli. Wynik ruchu i częściowego wyjścia pozostaje NOT_VERIFIED do końca okna i odpowiedzi operatora; planowany koniec około 15:41:05. GT OCR N/A, akceptacja drogowa NOT_VERIFIED.


## v5.17: wynik fizyczny i izolacja właścicieli v5.18

Próba zakończyła 180,031 s, od 15:38:04,755618 do 15:41:04,772404. STOP potwierdzono 15:41:13,520376, PID 12922 pozostał ten sam, mirroring nie działał, nie było pauzy pamięci ani AndroidRuntime. Zapis ma 189 publikacji, 441 próbek ruchu globalnego oraz przerwy P1: 0,063 s, 2,341 s i 1,248 s. Osiem przedziałów miało mniej niż dwie ramki tablic. P1 i P2 zachowały numery. Przejściowo utworzono też P3 i utracono jego lokalny dowód; bez geometrii źródłowego MP nie przypisano tego do duplikatu ani do rzeczywistego trzeciego obiektu.

Operator: dwa chwilowe zaniki po powrocie, Px nie zmieniają się, ruch w miarę płynny. Dodatkowo potwierdził, że tablica była cały czas widoczna. Ciągłość pozostaje NO_GO, zachowanie numerów PASS_DESCRIPTIVE, płynność tylko opisowo z zastrzeżeniem. Galeria po STOP ma dwie karty: P1 z 10 obserwacjami i najlepszym agregatem 23%, P2 z siedmioma i 14%. Żaden wynik nie zakwalifikował się na badge; Weryfikacja przez całą próbę jest zgodna z zatwierdzonym progiem. Zapisano 17 nowych surowych obserwacji i 15 wariantów tekstu bez mnożenia kart. Wszystkie 911 wcześniejszych obserwacji zachowano bez zmian.

Rzeczywiste MP/MT/MZ w kwalifikowanym oknie: 53/52/13, mediany INF 855,564/728,404/969,834 ms; PRE+INF+POST 1833,661/1159,966/1144,589 ms. Mediana lokalnego trackingu 68,025 ms. Źródła i odpowiedź operatora: device/without-mirroring-v5.17-two-vehicles/.

Audyt wykazał reset wszystkich tablic przy zmianie celu prezentacji akwizycji. Ponadto nowy lub słabszy pomiar jednego właściciela powodował restart wspólnej puli pikseli. Cztery regresje przed poprawką: 0 PASS / 4 FAIL. Dodatkowy test porównania obszaru wyglądu już przechodził w v5.17; nie wprowadzano na tej podstawie zmiany deskryptora ani progów.

W v5.18 zmiana prezentacji zwykłego DYN zachowuje tracker tablic. Pomiary właścicieli są obsługiwane osobno. Zdrowy sąsiad zachowuje własny tor i wiek. Nowy lub ponownie kotwiczony tor dołącza dopiero po rzeczywistym lokalnym dopasowaniu jego obrazu źródłowego do ostatniego przetworzonego obrazu. Wymaga tych samych bramek jakości, wyglądu, inlierów i geometrii; globalny ruch i predykcja nie zastępują pikseli. Każdy tor zachowuje swój właściwy obraz referencji. Nowa epoka nie przejmuje starego dowodu, a wynik odwołanej epoki nie może zastąpić nowszej.

Wynik modelu wyprzedzający lokalnego workera jest odraczany do osiągnięcia jego źródła; zdrowi sąsiedzi nadal przetwarzają bieżące klatki. Nie nadano przyszłym pikselom bieżącego znacznika. Nie zmieniono TTL, MZ, modeli, progów MT, galerii, trybu statycznego ani kontraktów badawczych. Referencje mają ograniczenie do liczby aktywnych właścicieli, bez nowych bitmap pełnej kamery i bez mirroringu.

Pierwszy poprawiony zestaw: **44/44 PASS**. Zawiera sąsiada przy nowym opóźnionym pomiarze, brak dopasowania bez sfabrykowania obiektu, zmianę epoki, szybszy model i zachowany reset statyczny. Końcowy pełny przebieg trwa. Następna próba fizyczna v5.18 pozostaje NOT_VERIFIED. Dodatkowe logi zapiszą geometrię narodzin właściciela oraz bieżący i historycznie najlepszy agregat MZ. Zegar aktualizacji konsensusu jest zegarem Android elapsed realtime, nie źródłowym czasem sensora.


## v5.18: NO_GO, powrót do v5.17 i poprawka dostępności v5.19

v5.18 przeszła 530/530 szerokich testów oraz 45/45 testów końcowej diagnostyki, APK i kompilację instrumentation. SHA-256: 8449a0d7c0951eb98a7e849b0ecb22547ca462a3df15f8213f521c361491dd17. Testy hosta nie stanowiły odbioru fizycznego.

Pierwsza próba v5.18 nie wysłała START: czytnik logcat uruchomił się po pojedynczym znaczniku. Dane tej próby zachowano jako NOT_VERIFIED_LOG_BARRIER. Kontroler ponawia teraz znacznik do potwierdzenia odbioru przed START. Trzy testy opóźnionego czytnika, przerwanego strumienia i braku znacznika PASS; łącznie 11 testów kontrolera PASS. APK nie zmieniano przy tej poprawce kontrolera.

Właściwa próba device/without-mirroring-v5.18-two-vehicles-02: START 16:30:42,811797, kwalifikacja 16:31:05,638883, koniec 16:34:06,041624 (180,407 s), STOP potwierdzony 16:34:15,917907. Widoczność jest rejestrowana wyłącznie po onDraw, nie podczas pośredniego publikowania snapshotów. P1/P2 zmieniły się do P5/P6, osiem przedziałów miało mniej niż dwie ramki pojazdu (najdłuższy 12,218 s), a długi brak drugiej tablicy wyniósł około 137 s do końca okna. Operator potwierdził zanik lub zmianę numeru. Wynik fizyczny i telemetryczny NO_GO. Nie zarejestrowano AndroidRuntime, pauzy pamięci ani mirroringu.

W oknie MP/MT/MZ: 60/46/14; mediany INF 860,334/722,181/979,590 ms, mediana lokalnego trackingu 60,387 ms. Dziennik MZ ma 18 aktualizacji właścicieli. W 13 przypadkach bieżący agregat był słabszy od zachowanego najlepszego; nie wykryto regresji historycznego maksimum ani błędu progu badge. To PASS_DESCRIPTIVE tylko spójności cache, bez GT OCR i bez prawidłowej ciągłości właścicieli. Podział jednego auta na kilka właścicieli nadal rozdziela jego materiał znakowy.

Po zabezpieczeniu danych przywrócono historyczny APK v5.17 (d937997d734674e4f8fb66569bb94f8075e73f93239012f14651313c7ea64ccf), bez cofania checkoutu ani czyszczenia aplikacji. Wszystkie 532 pliki danych i pięć preferencji zachowano bitowo. Pierwsza kontrola po jednosekundowym uruchomieniu nie widziała jeszcze START; późniejszy rzeczywisty START potwierdzono. Walidacja: device/restore-v5.17-after-v5.18-no-go/validation.json. To wersja awaryjna z wcześniejszym NO_GO przy granicy, nie ogłoszenie rozwiązania problemu.

### v5.19: brak wyniku właściciela jest niedostępnością

Regresja używa rzeczywistych lokalnych trackerów na dwóch syntetycznych obiektach. Oba obiekty pozostają w obrazie, lecz aktualizowany jest tylko tor B. W v5.18 LIVE toru B był traktowany jako ocena nieobecności wszystkich właścicieli; po 600 ms unieważniał A. Test przed poprawką FAIL na warunku zachowania właściciela A.

PreviewTrackingFrame otrzymał jawny zakres oceny lokalnego trackera. Wynik KLT z kamery nie jest pełnokadrową negatywną detekcją wszystkich pojazdów lub tablic. Nieobecność A w takim wyniku pozostaje UNAVAILABLE; rzeczywisty silny konflikt wyglądu nadal jest odrzucany. Starsza jawna negatywna ścieżka zachowuje swoje zasady. Nie zwiększono czasu rysowania starej geometrii, nie odnawia się MP/MT/MZ i nie przedłużono okna odzyskania.

Test po poprawce potwierdza zachowanie A, brak rysowania jego nieobserwowanej pozycji, spóźniony MP z oryginalnym znacznikiem źródła, ponowne pokazanie ramki po rzeczywistym odzyskaniu pikseli bez zmiany numeru oraz wygaszenie przy długiej niedostępności. Zestaw 37/37 PASS, obejmujący także wcześniejsze testy sceny, tablic, granicy oraz natywnego HUD.

Szeroki release-v5.19 zakończył się **531/531 PASS**, APK i kompilacja instrumentation PASS. Instrumentation nie wykonywano. Pieczęć 983 plików źródłowych odpowiada checkoutowi; względem v5.18 zmieniono trzy istniejące pliki produkcyjne i dodano jeden test, 979 plików zachowano bitowo. Kontroler próby: 11/11 PASS.

v5.19 zainstalowano na A12 bez usuwania danych. APK SHA-256 f5ab32db32b711b851ce62bcd1a8c3c810a2c155b4327902b605bd5ee8b5a070, zgodny z zainstalowanym pakietem. Zachowano wszystkie 532 pliki sprzed instalacji, pięć preferencji i 335 pierwotnych plików cropów. START potwierdza analizę zatrzymaną; podgląd pokazuje oba przygotowane auta. PID 21091, brak agenta mirroringu. Fizyczna akceptacja v5.19 **NOT_VERIFIED**, oczekuje na świeżą gotowość operatora po instalacji. OCR GT N/A.

### v5.19: pierwsza próba nie osiągnęła kwalifikacji

Po potwierdzeniu gotowości operatora wysłano START 17:28:58,050424. W ciągu 50 s nie wystąpiły jednocześnie dwie ramki tablic z rzeczywistymi próbami MZ obu właścicieli. Okno zakończono 17:29:48,299752, STOP wysłano 17:29:53,629770 i potwierdzono 17:29:56,802145. Kontrolowane 180 s ruchu nie rozpoczęło się: **NOT_VERIFIED_QUALIFICATION_NOT_REACHED**, nie PASS ani wynik ruchu.

45 próbek rzeczywistego rysowania ma po uzyskaniu dwóch ramek właścicieli wyłącznie P1/P2, bez utraty tożsamości i dodatkowych narodzin. Nie było mirroringu, pauzy pamięci ani zmiany PID. To opis rozgrzewki, nie zastępstwo pełnego testu częściowego wyjścia z kadru. MP/MT/MZ: 13/10/4, mediany INF 842,757/752,080/972,744 ms.

Pierwszy rzeczywisty MP prawego auta miał 250,871 × 147,423 px przy zachowanych preferencjach wejścia MT 256 × 112 px. Bramka rozmiaru wpływa na wybór z kolejki, nie na istnienie P2. Nie twierdzimy, że każdy późniejszy MP był poniżej progu. Pierwszy zaakceptowany wynik geometrii MT dla P2 pojawił się o 17:29:56,110, już po STOP; odrzucenia callbacków wystąpiły dopiero po wysłaniu STOP i nie dowodzą uszkodzonej bramki prezentacji. Nie zmieniono progów, kolejki ani kodu aplikacji. Do powtórki potrzebny jest obraz obu aut większy o około 15–20%; operator został poproszony o przygotowanie kadru.

Zachowano wszystkie 946 poprzednich obserwacji, zapisano pięć nowych dla P1. Galeria rzeczywiście pokazuje jedną kartę P1, pięć obserwacji i najlepszy agregat 14%, mimo spadku bieżącego do około 8%. Dziennik ma trzy przypadki słabszego bieżącego wyniku z zachowanym najlepszym i zero naruszeń progu badge. PASS_DESCRIPTIVE dotyczy tylko obserwowanego właściciela i cache; GT OCR N/A. Dowody: device/without-mirroring-v5.19-two-vehicles/qualification_analysis.json, reading_trace_analysis.json, observation_delta.json, gallery_verified.png oraz pełne archiwum danych po STOP.

### v5.19: powiększony kadr, pełna próba PASS_DESCRIPTIVE

Operator przygotował większy kadr. Powtórka device/without-mirroring-v5.19-two-vehicles-02: START 17:35:33,250713, kwalifikacja obu par i prób MZ obu właścicieli 17:35:54,046276, koniec 17:38:54,071363 (**180,031 s**), STOP potwierdzony 17:39:03,030548. Operator najpierw ocenił płynność podczas pomiaru, następnie po STOP potwierdził: „Tak — do końca bez zaników i zmiany numerów”.

W oknie są 176 próbek rzeczywistego onDraw, wyłącznie właściciele 3/P1 i 4/P2, bez przerw pojazdów lub tablic, utrat tożsamości, dodatkowych narodzin ani ponad dwusekundowych przerw publikacji. PID 21091 zachowany, bez mirroringu, pauzy pamięci i AndroidRuntime. **PASS_DESCRIPTIVE dotyczy ciągłości i płynności w tej konkretnej próbie dwóch aut**, nie akceptacji drogowej ani poprawności OCR.

MP/MT/MZ w kwalifikowanym oknie: 47/47/23, mediany INF 861,971/742,366/995,500 ms; całe etapy 1812,700/1155,995/1162,379 ms. Lokalny tracker: 1326 aktualizacji, mediana 98,379 ms. Pełny dziennik do STOP ma 27 aktualizacji MZ, w tym 25 przypadków słabszego bieżącego agregatu z zachowanym najlepszym, bez naruszeń cache. Zachowano 951 wcześniejszych obserwacji, dodano 27 (15 dla P1 i 12 dla P2). Galeria pokazuje dwie karty tej próby z najlepszymi ocenami 22% i 17% oraz jedną zachowaną kartę poprzedniej próby; nie oznaczamy tego jako trzech nowych właścicieli bieżącego pomiaru. GT OCR N/A.

### v5.20: jawna zmiana polityki badge przez operatora

Przy przygotowaniu powtórki operator zmienił wcześniejszą decyzję progu 60%: badge pojazdu po odczycie ma pokazywać numer i pewność; Weryfikacja tylko przy braku odczytu i podejmowanej próbie MZ. Zachowujemy najlepszy rzeczywisty agregat właściciela, a słabszy wynik nie zastępuje lepszego. Nowa polityka dotyczy prezentacji zwykłego DYN, nie potwierdzenia tożsamości, wyszukiwania ani liczby wywołań modeli.

Źródła v5.19 zabezpieczono przed zmianami: audit/baseline-v5.19-before-badge-policy. v5.20 usuwa próg widoczności tekstu badge/HUD 2, zachowując oryginalną ocenę i stabilność agregatu. Migawka prezentacji otrzymuje mzInProgress z rzeczywistego zadania MZ; zakończenie, anulowanie i granica sceny usuwają ten stan, a spóźnione zakończenie nie może wyczyścić nowszego zadania. Historyczny licznik prób nie jest już przesłanką statusu Weryfikacja. Walidacja hosta trwa; fizyczna prezentacja v5.20 NOT_VERIFIED.

Pierwszy zestaw v5.20: 64 PASS / 2 FAIL. Nowy komunikat odczytu przesłaniał informację o odrzuconej geometrii; przywrócono pierwszeństwo rzeczywistego problemu geometrii, zachowując ostatni odczyt. Drugie oczekiwanie ukrywania niepotwierdzonego tekstu zaktualizowano do nowej decyzji operatora. Końcowy release-v5.20: **535/535 PASS**, APK i kompilacja instrumentation PASS; instrumentation nie wykonywano. Kontroler: 11/11 PASS, analizator nowej polityki: 6/6 PASS, w tym wynik 0% z niepustym tekstem, brak dowodu i niezgodny zegar.

v5.20 ma 984 pliki źródłowe; względem pieczęci v5.19 zmieniono dziesięć istniejących, dodano jeden test, 973 zachowano bitowo. APK SHA-256 bc0cce8ba5658be9cc22fe9fe995f954cc1419461e80640c6f2b4a834528742b jest zgodny z zainstalowanym na A12. Zachowano wszystkie 559 plików danych przed instalacją, pięć preferencji i 335 pierwotnych plików cropów. Bez reinstalacji z usuwaniem danych i bez mirroringu.

Po świeżej gotowości operatora uruchomiono próbę v5.20. Kwalifikacja wymaga dwóch pojazdów, dwóch tablic, rzeczywistych prób MZ obu właścicieli i dwóch wyświetlonych odczytów. Początkowy rzeczywisty zrzut pokazuje P1: EL927JM, MZ 22% oraz P2: EJW1, MZ 11%; bez zastępowania tekstu statusem Weryfikacja. To potwierdza renderowanie, nie prawidłowość numerów względem GT. Pełne okno i ocena końcowa pozostają NOT_VERIFIED do STOP i odpowiedzi operatora.

### v5.20: pierwszy pomiar, tablica wychodziła poza kadr

Pierwsza próba v5.20 zakończyła 180,062 s: kwalifikacja 17:56:11,501506, koniec 17:59:11,556036, STOP potwierdzony 17:59:21,133143. Operator po STOP: „Tak — ciągłość i badge’e działały poprawnie do końca”. W 188 próbkach rzeczywistego rysowania są stale P1/P2 i dwa odczyty, bez przerw ramki pojazdu, bez zmiany numerów, pauzy pamięci i AndroidRuntime. PID 26749 zachowany, mirroring wyłączony. Badge, zachowanie najlepszego agregatu, galeria i ramki pojazdów mają PASS_DESCRIPTIVE dla tej próby.

Pełny dziennik ma 21 aktualizacji MZ, 19 przypadków zachowania lepszego wyniku po słabszym, zero naruszeń nowej polityki. Najlepsze agregaty P1 21,943735% i P2 10,639748% pozostają zarówno na badge, jak i w dwóch kartach galerii. Nowych obserwacji 21 (P1: 2, P2: 19), 18 wariantów surowego tekstu; wszystkie 978 wcześniejszych obserwacji zachowano bez zmian. 38/38 plików modeli/pakietów ma pierwotne SHA-256. GT OCR N/A.

Ścisły analizator pozostawia NO_GO: dziewięć przedziałów z jedną ramką tablicy (0,093–2,135 s) i przejściowe narodziny właściciela 3/P3, którego nie ma w żadnej z 188 próbek rysowania ani w nowych obserwacjach galerii. Brak źródłowych pikseli MP/GT nie pozwala nazwać go duplikatem lub prawdziwym trzecim autem. Zrzut check-7.png pokazuje P2 i jego tablicę przycięte prawym brzegiem podglądu, przy zachowanych P1/P2 i odczytach. Operator doprecyzował: „Tablica chwilami wychodziła poza kadr”. Nie przypisujemy na tej podstawie wszystkich dziewięciu przerw do przycinania ani do błędu śledzenia widocznej tablicy.

MP/MT/MZ w oknie: 51/50/18, mediany INF 843,851/734,800/988,080 ms; całe etapy 1798,180/1137,482/1151,147 ms. Lokalny tracker: 1725 aktualizacji, mediana 73,531 ms. Surowe dane i ścisły wynik zachowano bez zmiany kryteriów po pomiarze. Do rozstrzygnięcia ciągłości w pełni widocznych tablic rozpoczęto powtórkę bez zmiany APK: device/without-mirroring-v5.20-two-vehicles-02, niewielkie ruchy i postoje z oboma autami i całymi tablicami z dala od brzegów. Ten wynik pozostaje NOT_VERIFIED do zakończenia i oceny operatora.

### v5.20: końcowa powtórka z pełną widocznością — PASS_DESCRIPTIVE

Ta sama pieczęć APK, bez kolejnej instalacji i zmian źródeł. START 18:04:43,370876, kwalifikacja 18:05:01,880033, koniec 18:08:01,906278 (**180,031 s**), STOP potwierdzony 18:08:10,552429. Operator po zakończeniu: „Tak — wszystko działało poprawnie, tablice cały czas w kadrze”. Ruch, postoje i wznowienie przy pełnej widoczności mają **PASS_DESCRIPTIVE w tej konkretnej próbie**.

175 próbek rzeczywistego onDraw: stale 4/P1 i 5/P2, dwie ramki tablic i dwa odczyty. Zero przedziałów braku ramek pojazdu lub tablicy, zero zmian numerów, dodatkowych narodzin, utrat tożsamości i ponad dwusekundowych przerw publikacji. PID 26749 zachowany, bez mirroringu, pauzy pamięci i AndroidRuntime. Ścisły analizator i ocena operatora są zgodne: PASS_DESCRIPTIVE. Nie przenosimy tego wyniku na wcześniejszą próbę z przyciętą tablicą; jej NO_GO i surowe dane pozostają zachowane.

Pełny dziennik do STOP ma 26 aktualizacji MZ i 22 przypadki słabszego bieżącego agregatu z zachowanym najlepszym, bez naruszeń. Odczyty i ich oceny są widoczne na badge oraz w HUD 2. Galeria ma jedną najlepszą kartę każdego właściciela tej próby: P1 15% / 9 obserwacji, P2 6% / 17 obserwacji. Dwie karty poprzedniego niezależnego START są zachowane, więc całkowity widoczny licznik wynosi cztery. Nie twierdzimy, że program scala fizyczne pojazdy między niezależnymi analizami. Zachowano wszystkie 999 wcześniejszych obserwacji bez zmian, dodano 26 nowych. Końcowe 38/38 SHA-256 modeli zgodne z pierwotnym wzorcem.

MP/MT/MZ w kwalifikowanym oknie: 50/49/22, mediany INF 837,223/739,231/974,536 ms; całe etapy 1782,820/1146,507/1138,943 ms. Lokalny tracker: 1207 aktualizacji, mediana 110,488 ms; nie utożsamiamy tego z częstotliwością renderowania ani z oceną płynności operatora.

Końcowy dowód: audit/verification_final_v5.20.json oraz device/without-mirroring-v5.20-two-vehicles-02/ (summary, analysis, operator_feedback, reading_trace_analysis, observation_delta, gallery_verified, final_stopped i niezmienione surowe logi/archiwum). 535/535 testów hosta, APK i kompilacja instrumentation PASS; instrumentation nie wykonywano. Telefon pozostawiono w START, analiza zatrzymana. GT OCR N/A, akceptacja drogowa NOT_VERIFIED. Częściowe wyjście pojazdu z tablicą gwarantowanie w pełni widoczną nie jest osobno dowiedzione tą końcową powtórką.
