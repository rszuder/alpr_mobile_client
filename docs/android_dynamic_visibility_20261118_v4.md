# DYN: ciągłość geometrii, MT/MZ i zimny start — 2026-11-18

Status: v4.4 zaimplementowana, zbudowana i zainstalowana na A12. Testy ostatniej korekty: 59/59 PASS. Końcowa weryfikacja urządzenia: **NO_GO dla ciągłości i geometrii tablicy**. Wykonano trzyminutową próbę bez mirroringu; próbę z mirroringiem pozostawiono **NOT_VERIFIED**, ponieważ podgląd Studio nie jest włączony. Nie zmieniano źródeł aplikacji podczas tej weryfikacji.

## Przyczyny i zmiana

1. Telefon miał zapisany próg rozpoczęcia MT 256 × 112 px, a pierwszy obserwowany pojazd mierzył około 185 × 143 px. MT i MZ rzeczywiście nie były wtedy wykonywane. Zachowano wybór operatora; HUD wyjaśnia oczekiwanie i wymagane wymiary. Formularz odnosi wartości do rzeczywistego kadru analizy, waliduje dodatnie wymiary, histerezę i rozmiar kadru. Brak odczytu wymiarów jest jawnie oznaczony. Konfiguracja niemożliwa po zmianie rozdzielczości blokuje MT z wyjaśnieniem.
2. Ten sam MP był kotwiczony dwukrotnie. Reset KLT uruchamiał odtwarzanie historii, które na A12 potrafiło trwać około 0,93 s. Deduplikacja uwzględnia klatkę, kontekst i właściciela. Kolejny pomiar tego samego, poprawnie śledzonego obiektu koryguje geometrię przez historię lokalnego ruchu; zachowuje punkty, stan i dowody KLT. Brak wiarygodnej ścieżki do starego pomiaru nie kasuje aktualnego śledzenia.
3. Prezentacja DYN była częściowo zależna od krótkich sesji Scan. MT i śledzenie przypisanej tablicy mają teraz publikację według trwałego właściciela. Zakończenie sesji akwizycji nie resetuje tej warstwy. Brak migawki nie włącza prezentacji statycznej. Świeży, oznaczony klatką wynik lokalny może dostarczyć geometrię w bieżącej epoce, zanim wróci następny MP; zmiana sceny nadal odcina poprzednich właścicieli.
4. Próba telefonu ujawniła błąd nowego porównania domen: `PREVIEW_INHERITED_CAMERA` dziedziczy czas `CAMERAX_SENSOR`. Użyto istniejącego kontraktu porównywalności tych domen. Czas wykonania programu nie zastępuje czasu kamery. Dodano test rzeczywistego znacznika podglądu dla obu kanałów śledzenia oraz odrzucania starych i nieoznaczonych wyników.
5. Przy zimnym starcie 64 gęsto zapisywane klatki nie obejmowały opóźnionego MP. W próbie P1 nie dostał dowodu pikselowego, a podobny kandydat otrzymał P2 po upływie odzyskiwania. Historia ma teraz próbkowanie czasowe co najmniej 200 ms, przy tych samych limitach 4 MiB / 64 klatki / 10 s. Limit odtwarzania i odrzucanie rzeczywistych luk pozostają aktywne. Test odtwarza pięć sekund napływu 30 FPS. Stary MP bez nadgonionej geometrii nie może błysnąć ramką w aktualnym podglądzie.
6. Ostatnie rzeczywiste czasy modeli w DYN przeżywają pominięcie etapu i zmianę epoki obrazu. Pomiar ma czas wykonania i wynik; brak wykonania nie daje zera. Naprawiono licznik `mz_runs` dla odroczonego MZ. Znacznik pojazdu pokazuje tekst i pewność MZ bez statycznego oznaczenia „zap.”. Zachowano jedną kolejkę modeli, adaptacyjny harmonogram MZ i dotychczasowe limity pamięci.
7. Próba v4.3 z mirroringiem i ruchem operatora ujawniła kolejną przerwę: zmiana technicznego tracka tablicy resetowała poprawny KLT tego samego właściciela i blokowała wspólny wątek odtwarzaniem historii. v4.4 przekazuje nowy track do zachowanego stanu wyłącznie przy jednoznacznym właścicielu, zdrowym śledzeniu, potwierdzonej historii ruchu i zgodnej geometrii w klatce pomiaru. Nie łączy tracków po tekście MZ. Częściowy pomiar MT nie może zwęzić poprawnej pełnej ramki poniżej zgodności geometrycznej.
8. Pierwszy wynik przepływu po luce klatek większej niż 500 ms nie może orzekać o nieobecności obiektu. Dodatni, zweryfikowany wynik nadal jest użyteczny. Faktycznie oceniane kolejne braki nadal kończą ciągłość; 600 ms geometrii, 5 s odzyskiwania i dotychczasowe wygaśnięcie dowodów pozostają bez wydłużenia. Ten przypadek i przekazanie technicznego tracka mają osobne regresje.

Tożsamość i galeria pozostają niezależne od zgodności tekstu MZ. Zachowano 600 ms podtrzymania geometrii po utracie dowodów oraz 5 s odzyskiwania numeru. Zmiana nie scala historycznych sesji na podstawie tablicy. Formaty eksportu i ustawienia CPU/GPU nie zostały zmienione.

## Testy hosta

Dowody znajdują się w `app/build/android_dynamic_visibility_20261118_v4/`.

| Przebieg | Wynik i granica dowodu |
| --- | --- |
| `targeted-verified` | 42/42 PASS, stan pośredni |
| `full-validation` | 1668 testów: 1643 PASS, 17 FAIL, 8 SKIP; stan pośredni |
| `baseline-final` | 139 testów zabezpieczonego stanu sprzed zmiany: 125 PASS, 14 FAIL |
| `context-final` | 48/48 PASS, APK i kompilacja testów urządzenia PASS; stan pośredni |
| `scoped-release` | 468/468 PASS, APK i kompilacja testów urządzenia PASS; obejmuje naprawę domen podglądu |
| `cold-start-release` | 242/242 PASS, APK i kompilacja testów urządzenia PASS; obejmuje próbkowanie historii |
| `final-presentation` | 46/46 PASS, APK i kompilacja testów urządzenia PASS; ochrona przed starą geometrią MP |
| `handoff-final` | 59/59 PASS, APK i kompilacja testów urządzenia PASS; końcowe źródła v4.4, przekazanie tracka i rozróżnienie luki od ocenionego braku |

Czternaście błędów pełnego zestawu odtworzono na niezmienionych źródłach początkowych. Dotyczą F6, istniejących testów Opcji/START, insets i edytora TV. Trzy pozostałe błędy pełnego przebiegu nie powtórzyły się po przygotowaniu katalogów renderów i izolacji procesów (jeden brak layoutu, dwa OOM środowiska hosta). Szczegóły metod zawiera `audit/baseline_comparison.json`. Pełny zestaw nie jest oznaczony jako zielony.

Niektóre początkowe uruchomienia nie wykonały testów: odziedziczony format binarnych wyników Gradle oraz błędne przekazanie parametrów/wzorców przez powłokę. Są zachowane w katalogach diagnostycznych i nie są liczone jako wyniki aplikacji.

## Telefon i próby

Urządzenie: A12 SM-A125F, Android 12. Analiza: 1080 × 1440, przy limicie 2 073 600 pikseli. Materiał: pojazd z tablicą na ekranie obserwowany kamerą telefonu. Nie jest to próba drogowa ani źródło GT dla poprawności OCR.

| Wersja / przebieg | Wynik |
| --- | --- |
| v4, `without-mirroring` | 183 s kwalifikowanego okna, MP/MT/MZ 44/42/39. P1 utrzymany; zanik tablicy: **NO_GO_PLATE_VISIBILITY**. |
| v4.1, `without-mirroring-final` | 183 s kwalifikowanego okna, MP/MT/MZ 44/40/36. Tablica i odczyt widoczne; zimny start P1 → P2: **NO_GO_COLD_START_IDENTITY**. |
| v4.3, `without-mirroring-v4_3` | Zatrzymana przed START: wykryto działającego agenta mirroringu. Nie jest próbą bez mirroringu. |
| v4.3, `with-mirroring-v4_3` | 184 s kwalifikowanego okna, MP/MT/MZ 45/44/28. Jeden właściciel P1, brak restartu procesu. Operator potwierdził niewielkie ruchy. Zaniki: pojazd do 13,474 s, tablica do 16,951 s; **NO_GO_CONTINUITY**. |
| v4.4, `with-mirroring-v4_4` | Próba nie zakwalifikowała się: na zapisie końcowym ciemny ekran i odbicie pokoju, bez pojazdu. MP/MT/MZ 8/0/0. Analizę zatrzymano; nie jest dowodem ciągłości ani błędu MT/MZ. |
| v4.4, `without-mirroring-v4_4-final` | 183,078 s kwalifikowanego okna, MP/MT/MZ 45/44/38. Jeden właściciel 1/P1, jedno utworzenie, zero utrat tożsamości, ten sam PID 3339. Wszystkie 15 kontroli wykazały brak mirroringu. Po rozruchu brak zaników ramek i tekstu; rozruch zawiera zanik obu ramek na 0,157 s. Ramka tablicy później obejmowała tylko część znaków: **NO_GO**. |
| v4.4, `additional-run4-v4_4` | Osobny krótki przebieg widoczny w logu po zakończeniu pomiaru, około 20:13:16–20:13:45; nie jest trzyminutową próbą. Właściciel 2/P1, zanik pojazdu 7,463 s, tablicy 8,029 s; wystąpiła również utrata lokalnego dowodu obecności. **NO_GO**. Nie przypisano inicjatora START/STOP ani nie połączono tego przebiegu z pomiarem run=3. |
| v4.4 z mirroringiem Studio | **NOT_VERIFIED** — agent Studio nie jest uruchomiony; oczekiwanie na aktywację podglądu przez operatora. |

W pierwszej próbie mediany samych inferencji MP/MT/MZ wyniosły 1084 / 715 / 962 ms. Harmonogram korzysta z pełnego kosztu przygotowania, inferencji i obrabiania, a nie wyłącznie tych trzech wartości. Nie wynika z nich stały stosunek szybkości modeli ani faktyczna liczba wątków CPU.

HUD 1 i HUD 2 włączono do kontroli urządzenia. Surowe warianty MZ nie są oceniane jako poprawny numer bez GT. Pewność może spadać przy sprzecznych danych; sam upływ czasu jej nie podnosi.

### Wynik końcowej weryfikacji v4.4

- Podczas pomiaru bez mirroringu rzeczywiste mediany inferencji MP/MT/MZ wyniosły **839,607 / 747,206 / 995,865 ms**. Pełne etapy modeli: **1793,116 / 1177,995 / 1167,521 ms**. W tym materiale sam MZ ma najdłuższą inferencję, ale pełny etap MP kosztuje więcej; wyniki nie uzasadniają stałego harmonogramu 2:1.
- Kwalifikacja wymagała dodatnich rzeczywistych wykonań wszystkich trzech modeli oraz jednocześnie widocznego pojazdu, tablicy i niepustego tekstu. Rozruch 10,844 s pozostał w logu. Kontrole mirroringu wykonywano przed START, co około 15 s i na końcu. PID nie uległ zmianie; nie wystąpił błąd AndroidRuntime.
- Zanik przy 20:05:50,314–20:05:50,471 nastąpił po aktualizacji wspólnego wątku śledzenia trwającej **573,372 ms** podczas pierwszego odtwarzania historii MT. Ostatni przetworzony znacznik `2878599799707000` był przy rysowaniu `2878600475339000` starszy o **675,632 ms**, przekraczając limit geometrii 600 ms. Dowód lokalny wrócił bez zmiany P1. Zwiększanie limitu wyświetlania nie było częścią weryfikacji.
- Zrzuty `midpoint.png` i `end_before_stop.png` pokazują zwężenie ramki tablicy. Zapisany crop `crop-000024.jpg` z klatki 1258 zawiera tylko lewą część tablicy; odczyt MZ ma jeden znak. Obraz jest jednoznacznie przypisany do pierwszej i jedynej obserwacji `5:5:0:1258:4:1795028961058`. To obserwacja po kwalifikowanym oknie, przed STOP; nie przeniesiono jej wstecz do statystyk tych 183 s. Sam licznik obecności ramki nie wystarcza do odbioru jej poprawności geometrycznej.
- Bezpośrednio po pierwszym pomiarze galeria pokazała **jeden wpis P1 dla 41 obserwacji i 24 wariantów surowego MZ**. Liczba obserwacji galerii obejmuje również pracę przed ręcznym STOP po zakończeniu kwalifikowanego okna. Po późniejszym, odrębnym uruchomieniu galeria miała dwa wpisy; są to dwa różne uruchomienia analizy i dwa trwałe identyfikatory, a nie podział pierwszego pomiaru według tekstu tablicy. Historycznych wpisów nie usunięto ani nie scalono po MZ.
- Operator potwierdził wykonanie niewielkich ruchów na v4.4, bez precyzyjnych znaczników czasu. Zapis zawiera pomiar run=3 i późniejszy krótki run=4; zachowano ich rozdzielenie. Materiał ekranowy i zrzuty nie stanowią drogowego odbioru ani GT OCR.

Dowody: `device/without-mirroring-v4_4-final/{summary,assessment,acceptance}.json`, log od START, uzupełniający log do zakończenia późniejszego przebiegu, zrzuty kwalifikacji/połowy/końca/STOP i galerii oraz `device/additional-run4-v4_4/assessment.json`. Zapis inicjalnej próby i późniejszej aktywności pozostawiono osobno, bez zastępowania historycznych wyników. Hostowe 59/59 PASS pozostaje dowodem dla niezmienionych źródeł, nie odbiorem urządzenia.

## Pochodzenie i ochrona danych

Początek: HEAD `f7943a7d7334afd1e4188ef9860b2126331926c8`, 332 wpisy istniejącego stanu roboczego. Zabezpieczono sumy 1187 plików i kopię 963 plików `app/src`. Bez resetu, czyszczenia, stasha, commita lub publikacji. Zabezpieczone źródła były użyte do osobnego przebiegu bazowego, bez podmiany bieżących plików roboczych.

Pierwsza instalacja zachowała bajtowo 5 plików ustawień, 194 istniejące pliki galerii i 38 plików modeli. Przed aktualizacjami zapisano pełną kopię galerii i ustawień. Każda dostawa ma sprawdzone wejścia źródłowe, podpis APK i SHA-256 zainstalowanego pakietu. Dane telefonu i surowe logi pozostają w prywatnym katalogu `app/build`; nie wchodzą do paczki źródeł.

Audyt po instalacji i po pomiarze v4.4 ponownie potwierdził zachowanie wszystkich 194 pierwotnych plików galerii oraz 38 modeli. W pięciu plikach ustawień zmieniły się wyłącznie cztery klucze znacznika cyklu uruchomienia (`research_session_marker`); konfiguracja operatora pozostała taka sama. Do końca pierwszego pomiaru dane z bieżącej pracy obejmowały cztery sesje, z odpowiednio 53, 42, 18 i 24 wariantami surowego MZ. Audyt `device/integrity-after-without-final/final_integrity.json` potwierdza jednego właściciela ostatniej sesji przed późniejszym run=4. Kolejnych uruchomień nie scalano po numerze tablicy.

Ostatni audyt, `device/integrity-after-additional-run/final_integrity.json` (20:21:37), ponownie potwierdza wszystkie 194 pliki i 38 modeli bez zmian. Ostatnia sesja ma 43 obserwacje, 24 warianty MZ i dwóch właścicieli z odrębnych uruchomień. Zrzut `validation_final_state_202257.png` potwierdza zatrzymaną analizę i licznik galerii 2. Końcowa kontrola wszystkich 968 plików źródłowych ponownie odpowiada przebiegowi `handoff-final`.

Zakres względem zabezpieczonego stanu roboczego: 26 zmodyfikowanych i 5 nowych plików `app/src`. Pozostałe 1161 chronionych plików bez zmian; brak usunięć. Wszystkie 968 bieżących plików źródeł odpowiada wejściom przebiegu `handoff-final`.

`app/build/android_dynamic_visibility_20261118_v4/source_delta_v4.4.patch` obejmuje dokładnie te 31 plików względem zabezpieczonego stanu roboczego (nie względem czystego HEAD). Zastosowano go w osobnej kopii początkowych plików i uzyskano bajtowo identyczny wynik dla wszystkich 31 plików. Manifest `source_delta_v4.4.json` zawiera sumy przed i po zmianie.

APK: `alpr-dynamic-visibility-20261118-v4.4.apk`, 88 551 677 bajtów, SHA-256 `89f58011deec2ec1dd2a6dee00229b946e99afb804c600318e23343bf9344ad0`. Podpis i suma rzeczywiście zainstalowanego pakietu sprawdzone. Historyczne APK pozostawiono bez nadpisania.

Automatyczna obsługa okien Windows/Studio dwukrotnie zwróciła `Computer Use native pipe is unavailable` (OS error 2), również podczas końcowej weryfikacji. Sterowanie aplikacją Android i zbieranie danych przez ADB działają. Obecność mirroringu sprawdzano przez proces `com.android.tools.screensharing.Main`; nie jest to wizualne potwierdzenie okna Studio. Po wcześniejszym zgłoszeniu operatora zakończono przez ADB wyłącznie proces agenta na telefonie (PID 1438). Brak agenta potwierdzono przez cały trzyminutowy pomiar. Kadr został przywrócony, a START normalnie wznowił analizę po sprawdzeniu pamięci. Na ostatnim zrzucie po późniejszym przebiegu analiza jest zatrzymana, PID 3339 zachowany. Druga próba wymaga rzeczywiście włączonego podglądu Studio; nie zastępuje go host ani nagrywanie ekranu.

Przed zamknięciem weryfikacji sprawdzono również połączenie istniejącego podglądu: `adb forward --list` jest puste, a dawny `screen-sharing-agent.jar` został usunięty z telefonu. Nie ma działającej sesji Studio, którą można jedynie wznowić przez istniejący agent. Prośba o aktywację Running Devices pozostaje bez potwierdzenia.
