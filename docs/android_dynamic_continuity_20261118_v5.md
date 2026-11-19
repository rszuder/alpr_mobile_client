# DYN: dwa pojazdy, ciągłość i najlepszy odczyt — v5.20

Stan bieżący: v5.20 jest zainstalowana na A12 (SHA-256 bc0cce8ba5658be9cc22fe9fe995f954cc1419461e80640c6f2b4a834528742b), analiza zatrzymana, mirroring wyłączony. 535/535 testów hosta, APK i kompilacja instrumentation PASS. Końcowa próba dwóch aut i tablic w pełni widocznych trwała 180,031 s: 175 próbek rzeczywistego rysowania, stałe P1/P2, obie pary ramek i oba odczyty, bez przerw i dodatkowych właścicieli. Operator po STOP potwierdził poprawną płynność i pełną widoczność tablic. Wynik tej konkretnej próby PASS_DESCRIPTIVE; GT OCR N/A, akceptacja drogowa NOT_VERIFIED.

Zgodnie z późniejszą jawną zmianą decyzji operatora badge/HUD 2 pokazuje najlepszy niepusty odczyt i jego rzeczywistą ocenę także poniżej 60%. Słabszy bieżący agregat nie zastępuje lepszego historycznego. Weryfikacja przy ramce występuje tylko bez odczytu i podczas rzeczywistego zadania MZ właściciela. Nowa polityka zastępuje wcześniejszy próg widoczności 60%, nie zmieniając potwierdzenia domenowego ani trackingu. Końcowy dziennik potwierdził 22 przypadki zachowania lepszego wyniku; galeria zachowuje jedną kartę właściciela w analizie i wcześniejsze niezależne analizy. Pierwsza próba v5.20 z tablicą chwilami poza kadrem zachowuje ścisły NO_GO; nie przypisujemy jej wyniku końcowej powtórce ani odwrotnie. Szczegóły: [dwa pojazdy i wznowienie ruchu](android_dynamic_stop_resume_20261119_v1.md).

Historia diagnostyki MP v5.12: dwie próbki rzeczywistego wejścia przed backendem i wyjścia przed dekoderem miały maksymalne wyniki klasy car 0,0673 i 0,1138 przy progu 0,25. Wejście odpowiadało zapisanemu letterboxowi; przyczyna niskich wyników pozostaje nieustalona. To nie jest wynik obecnego materiału dwóch aut ani pełny odbiór ALPR. Szczegóły: [raport MP](android_mp_input_diagnostic_20261119_v1.md).

Dowody: app/build/android_dynamic_continuity_20261118_v5/. Dawne APK i surowe pomiary zachowano. Zgodnie z późniejszą decyzją operatora wymagające próby z 2026-11-19 odbywają się bez mirroringu. Okno kwalifikowane trwa trzy minuty.

## Przyczyny i implementacja

1. **Dwa wątki modyfikowały te same trackery.** Zwykły DYN aktualizował KLT zarówno z luminancji kamery, jak i z dawnej ścieżki bitmapy podglądu. Druga ścieżka zmieniała układ odniesienia i zużywała pomiary bez właściwej historii źródłowych pikseli. Zwykły DYN ma teraz jednego właściciela aktualizacji z luminancji; stare zlecenia bitmapowe kończą się przed alokacją, przetwarzaniem i publikacją. Tryby statyczny i badawczy zachowują własne ścieżki.
2. **Odtwarzanie opóźnionego MP/MT blokowało świeże piksele.** Historia ma 4 MiB, 64 klatki, 10 s i próbkowanie co najmniej 200 ms. Każda aktualizacja odtwarza najwyżej jeden rzeczywisty krok KLT, przy łącznym limicie 16 kroków. Nowe pomiary tego samego właściciela są koaleskowane. Stan REPLAYING oznacza brak oceny nieobecności, a nie dowód utraty pojazdu. Piksele kamery trafiają do historii przed odrzuceniem pośrednich zleceń przez kolejkę najnowszej klatki. STOP i zmiana kontekstu anulują odtwarzanie.
3. **Częściowy MT mógł zwężać pełną tablicę i crop MZ.** Wspólna bramka `DynamicPlateGeometryGate` sprawdza właściciela, klatkę, kontekst, wypukłość, zgodność bbox i zachowanie pełnego odniesienia. Częściowy wynik nie zmienia przypisania ani nie uruchamia MZ. Niewielkie odchylenie brzegu może przyjąć pełne, potwierdzone odniesienie (`ACCEPTED_REFERENCE`); wtedy właśnie jego narożniki są rzeczywistym wejściem rektyfikacji MZ. Nie używa się częściowego cropa pod nazwą pełnego odniesienia.
4. **Chwilowo słabsze KLT było traktowane jako nieobecność.** Obecne, przypisane piksele o przejściowo słabszych parametrach są N/A dla oceny utraty. Mocna sprzeczność wyglądu i rzeczywiście oceniony pusty wynik nadal kończą ciągłość. Słabe piksele nie odnawiają terminu dowodów. Ograniczona geometria pomocnicza służy wyłącznie rysowaniu: najwyżej 2 s od ostatniego mocnego dowodu i 600 ms od swojej rzeczywistej próbki.
5. **Rysowanie czekało na kolejne publikacje modeli.** Prezentacja DYN aktualizuje się przy rysowaniu co 33 ms. Prędkość pochodzi z kolejnych rzeczywistych geometrii pikselowych, a nie ze skoku nowego bbox MP. Wyprzedzenie prezentacji ma limit 300 ms, przesunięcie do 1/4 wymiaru pojazdu i skalę 0,85–1,15. Pojazd i przypisana tablica otrzymują wspólną transformację. Zegar prezentacji nie cofa się po spóźnionej publikacji kamery. Korekty neuronowe są wygładzane oddzielnie.
6. **Krótkie opóźnienie publikacji usuwało poprawną geometrię.** v5.7 zachowuje ostatnią rzeczywistą geometrię właściciela, bez zapętlania geometrii przewidzianej. Samo rysowanie otrzymuje 600 ms plus zmierzony odstęp między aktualną kamerą a pikselami, ograniczony do dodatkowych 400 ms. Zatrzymana kamera nadal wygasa po 600 ms; maksymalny limit wynosi 1 s. Nie zmienia to terminów dowodów tożsamości, MP, MT ani MZ.
7. **Przypisana tablica znikała wcześniej niż jej pojazd.** Pomiar v5.7 wykazał dwa zaniki samej ramki tablicy przy opóźnionych, nadal poprawnych pikselach pojazdu. Projekcja tablicy używała progu świeżości ukierunkowanego OCR 350 ms, mimo że ramka pojazdu zachowywała ważność do 600 ms. v5.8 stosuje dla tej projekcji okno wyświetlania pojazdu. Regresja sprawdza jednocześnie widoczną parę ramek przy 450 ms, brak dowodu dla skupionego OCR, nieodnowiony czas MT oraz wygaśnięcie po 600 ms i przy obcym kontekście.
8. **Ruch całej sceny był kosztowny.** Dla zwykłego DYN globalny estymator ruchu otrzymuje luminancję zmniejszoną przez uśrednianie do szerokości 96 px. Lokalne trackery i detektor zmiany sceny zachowują 240 px. Tryb badawczy zachowuje własną rozdzielczość i maskowanie.
9. **Przejściowo niedostępna publikacja samej tablicy nadal rozrywała parę ramek.** v5.9 zachowuje ostatnią poprawną pozycję tablicy względem rzeczywistego bbox jej pojazdu. Przy braku publikacji tablicy może przenieść to przypisanie razem z nadal widocznym pojazdem w tym samym ograniczonym oknie prezentacji, najwyżej 1 s. Rysowanie tej projekcji nie odnawia jej terminu. Nowy run, scena, epoka właściciela lub track tablicy nie dziedziczą obcego przypisania. Jawnie niepoprawny nowy czworokąt nadal jest odrzucany z żądaniem odświeżenia; cache go nie maskuje. Nie zmienia się tożsamość, crop, czas MT ani dowód tablicy.
10. **Pauza pamięci zatrzymywała analizę, ale zostawiała natywne modele.** Po pauzie niskiej pamięci modele są zwalniane na szeregowym wykonawcy dopiero po końcu anulowanej inferencji. Sprawdzenie generacji, bieżącego pipeline i stanu nowego uruchomienia chroni wznowioną analizę. Zabezpieczenie pamięci pozostaje aktywne; sam sygnał odzyskania RAM nie uruchamia detekcji.

Tożsamość pojazdu, numer P i grupowanie galerii pozostają niezależne od zgodności tekstu MZ. Inny odczyt MZ aktualizuje materiał znakowy tego samego właściciela, bez tworzenia nowego pojazdu na podstawie samego tekstu. Nie scalano historycznych wpisów według rejestracji. Brak odczytu nie tworzy wyniku OCR.

Zachowano progi wejścia MT 256 × 112 px i podtrzymania 100 × 64 px, modele, wykonanie CPU/GPU, formaty eksportu i konfigurację operatora. Harmonogram MZ korzysta z wygładzonego rzeczywistego kosztu przygotowania, inferencji, przetwarzania i rektyfikacji. Zlicza kwalifikowane cykle MT; nie zakłada na stałe stosunku 2:1 ani tego, że MZ zawsze jest najwolniejszy. Sam czas obserwacji nie podnosi sztucznie pewności numeru.

## Testy hosta

| Przebieg | Wynik | Zakres dowodu |
| --- | --- | --- |
| `host-release` | 377 PASS / 1 FAIL | Szersza walidacja wcześniejszej v5; błąd `MainUi2x6LayoutTest` z dolnym insetem 24/0 znany z zabezpieczonej bazy, nie naprawiany w tym zakresie. Nie jest to pełny zielony zestaw. |
| `release-v5.6c` | 158/158 PASS | Tożsamość, geometria, współbieżność, historia pikseli i natywna prezentacja przed dwiema ostatnimi korektami. |
| `release-v5.7` | 34/34 PASS | Zmieniona prezentacja, komplementarność UI i ochrona terminów dowodów. |
| `release-v5.8` | 58/58 PASS | Komplementarność pojazdu/tablicy, prezentacja, ochrona dowodów i rzeczywisty przepływ silnika z jawnymi syntetycznymi backendami. |
| `release-v5.9` | **60/60 PASS** | Ten sam zakres, dodatkowo zanik publikacji tablicy podczas ruchu pojazdu, nieodnawianie cache przez jego rysowanie oraz odrzucanie obcego run i jawnie błędnej geometrii. |

`assembleDebug` oraz `compileDebugAndroidTestJavaWithJavac` zakończyły się powodzeniem dla v5.9. Kompilacja testów Android nie oznacza ich wykonania na telefonie. Zestawy nakładają się; ich liczby nie są sumą unikalnych testów. Testy hosta nie stanowią odbioru fizycznego ruchu, płynności mirroringu ani drogowego OCR.

## Telefon A12 i granice odbioru

| Wersja | Wynik z mirroringiem |
| --- | --- |
| v5–v5.2 | Nadal przerwy geometrii; rozpoznano konflikt właścicieli aktualizacji, opóźnione piksele i zaniki tablicy. |
| v5.3 | Operator potwierdził, że ramki nie podążają. Koszt globalnego ruchu i utrata właścicieli pozostawały problemem. |
| v5.4 | Pierwszy start wstrzymany z powodu pamięci; ponowiona próba miała scenę trzech samochodów i zakończyła się zabiciem procesu. Nie jest kwalifikowaną próbą jednego pojazdu. |
| v5.5 | Operator: ramki nadal nie podążają; płynność ważna. Zapis wykazał przejściowo słabsze piksele uznane za nieobecność. **NO_GO**. |
| v5.6 | 180,187 s; jeden P1, zero utrat tożsamości, ciągły tekst. Dwie przerwy pojazdu i tablicy: 111 ms i 34 ms. Ruch niezweryfikowany — operator nie mógł go sprawdzić. |
| v5.7 | 180,015 s; jeden P1, zero utrat tożsamości, brak zarejestrowanych przerw ramki pojazdu i tekstu. Dwie przerwy samej tablicy: 288 ms i 102 ms. Ruch niezweryfikowany — operator odpowiedział po automatycznym STOP. |
| v5.8 | 180,015 s; operator potwierdził płynne podążanie obu ramek podczas wykonanych ruchów. Przed końcowym resetem sceny P1 i tekst były ciągłe; log pokazał siedem przerw samej tablicy 92–373 ms. Po znacznym rozjaśnieniu nastąpił HARD_RESET, a ostatnie około 12 s nie miało ramek. |
| **v5.9** | 2026-11-19, **bez mirroringu**, 180,015 s. Operator: obie ramki podążały płynnie; log: cztery utraty tożsamości, P1 → P2, zaniki pojazdu do 14,324 s i tablicy do 17,063 s, galeria: dwa wpisy / 19 obserwacji. **NO_GO dla ciągłości**. Nocne blokady RAM zachowano poniżej jako historię. |

Pomiar v5.7: START 23:42:54, kwalifikacja 23:43:05, koniec okna 23:46:05; zatrzymanie potwierdzone o 23:46:16. PID aplikacji i agent Studio pozostawały obecne. Log zawiera jednego autora aktualizacji KLT. Nie znaleziono błędu AndroidRuntime w logu pomiaru. Kwalifikacja wymagała rzeczywistych dodatnich wykonań MP, MT i MZ oraz jednoczesnego wyświetlenia pojazdu, tablicy i tekstu. Rejestr obecności ramek wykrywa zmiany stanu także pomiędzy okresowymi zapisami sekundowymi; sam nie mierzy błędu położenia względem fizycznego obiektu.

Rzeczywiste czasy v5.7, z oknem rozruchu zachowanym w logu:

| Model | Wykonania | Mediana inferencji | Mediana PRE + INF + POST |
| --- | ---: | ---: | ---: |
| MP | 39 | 1269,183 ms | 2333,343 ms |
| MT | 38 | 816,158 ms | 1257,611 ms |
| MZ | 30 | 1109,644 ms | 1280,621 ms |

Dane pochodzą z rzeczywistego wejścia 1080 × 1440 px. Widoczne czasy HUD 1 zostały sprawdzone na zrzucie `hud1_enabled.png`; odczyt i P1 były obecne przy pojeździe i w HUD 2. Nie jest to dowód poprawności odczytu względem GT. W v5.7 nie ma zarejestrowanych próbek globalnego ruchu ani potwierdzenia ruchu przez operatora, dlatego nie oceniono podążania jako PASS.

Po STOP galeria v5.7 rzeczywiście pokazała **jeden wpis P1, 31 obserwacji**. Zachowany JSON tej sesji zawiera **10 wariantów surowego MZ**, jeden `entity_id`, jeden techniczny track pojazdu i jeden track tablicy. Zapis galerii obejmuje również obserwacje poza kwalifikowanym oknem, do zatrzymania; nie przeniesiono tych liczników wstecz do okna pomiaru. Dowody: `gallery.png`, `gallery_assessment.json` i `data_after_trial.tar` w `device/with-mirroring-v5.7/`.

### Uzupełnienie v5.8: ruch i granica końcowej zmiany obrazu

Operator potwierdził gotowość przed START, a podczas okna odpowiedział: „Tak — na v5.8 obie podążają płynnie”. Zapisano tę deklarację w `device/with-mirroring-v5.8/operator_motion_confirmation.json`; nie przypisano jej wymyślonego dokładnego przedziału czasu ruchu. Log zawiera 42 próbki globalnego ruchu. Galeria po STOP pokazała jeden wpis P1 i 26 obserwacji.

Okno kwalifikowane: 2026-11-18 23:59:02 – 2026-11-19 00:02:02, zatrzymanie potwierdzone o 00:02:11. PID aplikacji i agent mirroringu zachowane, bez AndroidRuntime. Mediany rzeczywistej inferencji MP/MT/MZ wyniosły 1346,927 / 829,451 / 1150,043 ms, a PRE + INF + POST 2411,508 / 1286,143 / 1342,103 ms; liczby wykonań 43 / 27 / 26 obejmują zachowany rozruch. Po resecie sceny MP pracował dalej bez MT/MZ, dlatego nie traktuje się tych liczników jako stałej proporcji harmonogramu.

O 00:01:49 bezpośrednia luminancja wykazała zmianę średnio o 83 poziomy przy zmienionej frakcji 0,492; wykonano `HARD_RESET reason=abrupt_unexplained_direct_luma_change`. Kolejny reset nastąpił o 00:01:56. Końcowy zrzut rzeczywiście pokazuje znacznie jaśniejszy obraz tego samochodu. Przyczyna zmiany jasności i zachowanie tożsamości przez taką zmianę pozostają do wyjaśnienia z operatorem. Nie usuwa się końcowych zaników z pełnego zapisu ani nie zalicza pełnych 180 s jako nieprzerwanej ciągłości. Analizator v5.8 zachowuje datę logu, także przy przejściu przez północ.

### Kontynuacja v5.9, 00:31

Ponownie sprawdzono wszystkie 970 źródeł względem `release-v5.9` oraz SHA-256 zainstalowanego APK. Bieżący agent Studio 10211 jest aktywny; ekran telefonu pokazuje START i podgląd bez analizy. Zdjęcie samolotów nie spełnia warunku próby jednego pojazdu z tablicą. Nie uruchomiono inferencji i nie utworzono fikcyjnego pomiaru v5.9. Dowody: `device/preflight_v5.9_0031.json`, `device/v5.9_preflight_0031.png` i późniejsza kontrola kadru. Po przywróceniu pojazdu START nastąpi bez ponownego pytania o gotowość.

Dodano pomocniczy audyt `device/assess_presentation.py`: sprawdza krótkie przerwy publikacji, zmiany kontekstu sceny i obecność mirroringu. Samo zakończenie timera nie stanowi odbioru. Uruchomienie na zachowanym zapisie v5.8 prawidłowo pozostawia ścisłą ciągłość jako NO_GO, oddzielnie od pozytywnej oceny płynności przez operatora. Nie zmieniono surowych dowodów. Osobny plik `operator_motion_confirmation_utf8_supplement.json` przywraca dokładną pisownię wcześniejszej odpowiedzi v5.8 po utracie polskich znaków w serializacji; pierwotny plik pozostawiono z jego sumą kontrolną. Nie jest to potwierdzenie ruchu na v5.9.

### Zamknięcie pracy na dziś, 2026-11-19

Kadr jednego samochodu i działający mirroring zostały ponownie potwierdzone. Ustalony próg przygotowania 700 MiB dostępnego RAM jest warunkiem tej próby, nie zmianą zabezpieczenia w aplikacji. Początkowe pomiary pozostawały poniżej progu. Narzędzie sterowania Studio zwracało OS error 2, dlatego operator zamknął i ponownie otworzył samą kartę A12. Nowy agent ma PID 2648 zamiast 10211. Menedżer zasobów kodeków przypisuje jego aktywnego enkodera `c2.mtk.avc.encoder` do mirroringu, z wejściem 384 × 854 przy 60 FPS.

Przed odnowieniem procesu kodeka dotyczyły wartości RSS 990084 KiB i swap PSS 1959028 KiB. Po odnowieniu zapisano RSS 859954 KiB i swap PSS 60048 KiB; tych kategorii nie sumuje się jako odrębnego zużycia fizycznego RAM. Pomiary 01:10:41 i 01:10:47 przekroczyły 700 MiB, ale ALPR był już zakończony i telefon pokazywał ekran główny. Kontrola przycisku zapobiegła wysłaniu START w tym stanie.

Po przywróceniu MainActivity uruchomił się proces 4902. Aplikacja pokazała pauzę pamięci z przyciskiem START; pomiary po wznowieniu znów były poniżej progu. Systemowa historia zakończeń potwierdziła `reason=LOW_MEMORY` dla PID 666 o 01:08:25 oraz PID 4902 o 01:14:05, z ostatnimi zapisanymi PSS około 97 i 102 MB. Nie jest to wynik inferencji ani kwalifikowanego pomiaru. Nie wykonywano kolejnych pętli uruchamiania po stwierdzeniu powtórnego zakończenia.

Dowody i kopia danych: `device/memory_recovery_v5.9_0116/`, w szczególności `assessment.json`, `exit_info.txt`, `codec_memory.txt`, `codec_clients.txt` i `private_data.tar`; dodatkowo szeregi `memory_preflight_v5.9_20261119_010444.json`, `memory_after_renewal_v5.9_011020.json` i `memory_after_app_resume.json`. Kopia z 01:18 ponownie potwierdziła wszystkie 335 wcześniejszych plików galerii i brak zmian wartości preferencji względem początku v5. Źródła oraz SHA-256 zainstalowanej v5.9 ponownie sprawdzono przed próbą przywrócenia aplikacji; pozostały zgodne z dostawą.

Operator odpowiedział, że nie może teraz zrestartować telefonu i kończy na dziś. Telefonu nie restartowano. Następny krok po wznowieniu pracy: zwykły restart telefonu z ręcznym odblokowaniem, przywrócenie mirroringu i kadru, ponowna kontrola pamięci po uruchomieniu ALPR, a dopiero potem trzyminutowa próba v5.9. Nie przenosić potwierdzenia płynności v5.8 na v5.9 i nie traktować NOT_VERIFIED jako rozwiązania problemu.

### Presja pamięci i restart systemu

W ponowionej próbie v5.4 Android zabił ALPR przez LMKD przy presji swap i thrashingu. Migawka systemu pokazała około 1,95 GB ION oraz duże RSS procesu kodeka MediaTek i dawnego agenta mirroringu; wartości współdzielone nie są sumowane jako odrębna pamięć fizyczna. Presja utrzymywała się również po śmierci ALPR. Operator zgłosił restart telefonu. Log starego agenta zawiera `DeadSystemException: system died` o 22:50:58; czas pracy jądra nie został wyzerowany. Dowodzi to restartu systemu Android, nie nowego potwierdzonego kernel panic.

Operator odnowił podgląd Studio. Bieżący agent miał po v5.7 PSS około 43 MB. Przy instalacji v5.7 pojawił się kolejny rzeczywisty sygnał krytycznej pamięci; Android w tym samym czasie zwalniał aplikacje tła. Po odzyskaniu około 1,3 GB dostępnej pamięci i sprawdzeniu stabilności normalny START wznowił analizę. Nie wyłączano zabezpieczenia ani mirroringu. Zwalnianie natywnych modeli po pauzie ogranicza zasoby ALPR, ale nie stanowi naprawy kodeka systemowego lub agenta Studio i nie dowodzi usunięcia przyczyny wszystkich restartów.

## Pochodzenie, dane i dostawa

### Wznowienie bez mirroringu, 2026-11-19

Na pytanie o dodatkową kontrolę z mirroringiem operator wybrał „Sam test bez mirroringu”. Zachowano obecną v5.9, modele, ustawienia CPU/GPU, rozdzielczość i adaptacyjną kadencję MZ. Zmieniono wyłącznie narzędzia pomiarowe i raportowanie; nie wprowadzono nowego APK ani zmian `app/src`.

Nowy `device/trial_v5_9.py` wymaga jawnego `--mirroring off|on`. Dla tej sesji przewidziano `without-mirroring-v5.9 --mirroring off`. Przed START sprawdza zablokowanie/foreground, źródła i APK, dwie próbki RAM co pięć sekund oraz rzeczywisty stan agentów. W trakcie co 15 s ponawia kontrolę trybu, procesu i pamięci. Pojawienie się mirroringu przerywa próbę. Zbiera logi i pojedyncze zrzuty, bez uruchamiania nagrywania ekranu lub enkodera.

Niezależny licznik od kwalifikacji zamyka okno dowodów przed STOP po 180 s, również przy opóźnieniu głównej pętli diagnostycznej. STOP jest jednokrotny przy współbieżnym zakończeniu i sprzątaniu; błąd zapisu dowodów nie pomija zatrzymania. Przed logiem analizy ustawiana jest jednoznaczna bariera, aby wcześniejsze wpisy tego samego PID nie kwalifikowały nowej próby. Historycznego runnera nie zmieniono.

Ocena `device/assess_presentation.py` rozróżnia oczekiwane `off` i `on`; brak agenta jest prawidłowym warunkiem trybu `off`. Brak obserwacji pozostaje niezweryfikowany. Zachowano wcześniejszy assessor w `audit/assess_presentation_before_mode_20261119.py`. Oddzielne pola opisują poprawność procedury i ciągłość publikacji; żadne nie jest automatycznym dowodem płynności, zgodności pikselowej lub poprawności OCR.

Testy offline `device/test_trial_v5_9.py`: **11/11 PASS**, dowód `audit/runner_offline_validation_20261119/{result_sealed.json,control_flow_tests_sealed.log}`. To jawne syntetyczne przypadki sterowania, a nie inferencje ani odbiór Androida. Obejmują m.in. obcy tryb, zablokowany i nieznany stan keyguard, niski RAM, pojawienie się agenta w trakcie, opóźnioną pętlę, błąd zapisu, jednokrotny STOP i zachowanie NO_GO dla rzeczywistego historycznego logu v5.8.

Przed restartem zapisano `device/before_reboot_without_mirroring_v5.9_20261119_100225/`: 411 bieżących plików prywatnych, wszystkie 335 pierwotnych plików cropów i 38 modeli zgodne z sumami, 970 źródeł zgodnych z `release-v5.9`, zainstalowany APK zgodny z SHA-256 v5.9. Następnie wykonano zwykły restart przez ADB. Krótki uptime po ponownym połączeniu potwierdził restart jądra; stary proces kodeka 24669 zastąpił 681, agenta mirroringu nie było, dostępny RAM wynosił około 2 GiB.

Operator potwierdził restart, ale kolejne odczyty Androida nadal wskazywały blokadę ekranu. Nie automatyzowano poświadczeń, nie uruchamiano ALPR za blokadą i nie wysyłano START. Bieżące przygotowanie zapisano w `device/without_mirroring_preparation_*.json` wraz z hashami narzędzi. Po ręcznym odblokowaniu należy uruchomić ALPR, sprawdzić kadr oraz pamięć przy działającym podglądzie i dopiero wtedy rozpocząć próbę. Wynik tej próby będzie dotyczył pracy bez mirroringu; nie zastępuje osobnego odbioru z mirroringiem.

Punkt wyjścia: HEAD `f7943a7d7334afd1e4188ef9860b2126331926c8`, istniejący brudny stan roboczy z 352 wpisami. Zabezpieczono sumy 1198 istniejących plików i kopię 968 plików `app/src`. W tej zmianie zmodyfikowano 18 istniejących plików `app/src` i dodano dwa. Pozostałe **1180 zabezpieczonych plików bez zmian**, bez usunięć. Ostatni audyt: `audit/scope_v5.9.json`.

Wszystkie **970 aktualnych plików źródłowych** odpowiada wejściom przebiegu `release-v5.9`. `source_delta_v5.9.patch` obejmuje wyłącznie 20 plików tej zmiany względem zabezpieczonego stanu roboczego, nie względem czystego HEAD. Poprawkę zastosowano w odrębnej kopii i uzyskano bajtowo identyczne wszystkie 970 plików; sumy i wynik znajdują się w `source_delta_v5.9.json`. Nie wykonywano resetu, czyszczenia, stasha, stagingu, commita ani publikacji.

Instalacja v5.8 zachowała bajtowo wszystkie **403 istniejące przed nią pliki** ustawień i galerii, w tym pięć plików preferencji. Kontrola po instalacji i przed próbą ruchu potwierdziła wszystkie **335 pierwotnych plików galerii**, **38 plików modeli** oraz brak zmian wartości preferencji względem początku v5. Kopie prywatnych danych i surowe logi pozostają w `app/build`, poza poprawką źródeł.

Instalacja v5.9 zachowała wszystkie **411 plików** istniejących przed tą aktualizacją, w tym pięć plików preferencji i komplet wcześniejszych cropów. Dowód: `device/install_validation_v5.9.json`.

Kontrola v5.9 o 00:13:20 ponownie potwierdziła wszystkie 335 pierwotnych plików galerii, 38 plików modeli, niezmienione wartości preferencji i zgodność zainstalowanego APK ze źródłami. Dowód: `device/integrity_v5.9_before_motion.json`. Jest to kontrola integralności przed kolejną próbą, nie jej wynik funkcjonalny.

APK: `alpr-dynamic-continuity-20261118-v5.9.apk`, 88 553 013 bajtów.

SHA-256: `9a4e11cfbb7f0296cd6b617a530f2db8c16efeec9f65a2924389533e6fac8cb3`.

Podpis, komplet źródeł i SHA-256 faktycznie zainstalowanego pakietu sprawdzono. Nie nadpisano starszych APK. Automatyczna obsługa okna Studio była niedostępna; obecność agenta sprawdzano przez ADB, a stan renderowania aplikacji przez natywne zrzuty i logi. Fizyczna ocena płynności i dokładności podążania na v5.9 pozostaje do wykonania z operatorem.

### Zakończona próba v5.9 bez mirroringu, 2026-11-19

Dowody: `device/without-mirroring-v5.9-retry-01/`. START 10:33:16, kwalifikacja 10:33:26, koniec okna 10:36:26; STOP potwierdzony 10:36:31. PID 13581, run 2 i scena 2 pozostały te same. Wszystkie 15 kontroli potwierdziło brak agenta mirroringu. Dostępny RAM wynosił około 1,28–1,59 GiB; brak pauzy pamięci, śmierci procesu i błędu AndroidRuntime w zapisie. Oddzielna wcześniejsza próba `without-mirroring-v5.9` zakończyła się przed START na barierze logu; pozostaje NOT_VERIFIED. Późniejsza poprawka czytnika używa zdalnego PTY i została sprawdzona rzeczywistym znacznikiem przed START.

Operator: „Na v5.9 bez mirroringu obie ramki podążały płynnie”. Zapis dotyczy dokładnie tej próby, bez wymyślonego przedziału ruchu. Jest oceną odczuwanej płynności; nie znosi zaników w pełnym logu. Wystąpiło pięć przerw ramki pojazdu (49 ms, 350 ms, 272 ms, 8,763 s, 14,324 s), pięć przerw tablicy (najdłuższa 17,063 s), trzy przerwy tekstu i cztery utraty tożsamości. Galeria rzeczywiście pokazuje P1 / 8 obserwacji i P2 / 11 obserwacji tego samego przygotowanego samochodu. `gallery_opened_verified.png` przedstawia arkusz galerii, obejrzany po STOP; pierwotny `gallery.png` pokazał jeszcze ekran główny.

| Model | Wykonania z zachowanym rozruchem | Mediana INF | Mediana PRE + INF + POST |
| --- | ---: | ---: | ---: |
| MP | 57 | 857,529 ms | 1789,856 ms |
| MT | 43 | 736,692 ms | 1138,595 ms |
| MZ | 19 | 979,302 ms | 1146,127 ms |

Jeden autor KLT: wątek 14424; mediana aktualizacji 71,386 ms, maksimum 215,304 ms. Log ma 136 próbek ruchu globalnego. Te dane nie są miarą błędu położenia ramek ani dowodem drogowego OCR. GT pozostaje N/A.

### v5.10: odświeżanie wzorca opóźnionego pomiaru

O 10:34:17 przyjęto ciągły opóźniony pomiar MP; zgodność wyglądu spadła z około 0,77 do 0,25, następnie 0,11 i utraty tracka. Audyt wykazał bezwarunkowe zastępowanie wzorca wyglądu pikselami źródła opóźnionego pomiaru. Regresja z rzeczywistą syntetyczną klatką pośrednią i późniejszym callbackiem odtwarza błąd: przed zmianą 1 FAIL / 1 PASS. Nie jest to odtworzenie surowych pikseli z telefonu — ich nie zarchiwizowano w chwili pierwszego spadku.

Nowy wzorzec musi pasować do najnowszych rzeczywistych pikseli co najmniej tak dobrze jak zachowany i spełniać dotychczasowy próg sprzeczności 0,30. Inaczej pozostaje poprzedni wzorzec; opóźniona geometria i czas pomiaru zachowują własny przepływ. Nie przedłużono terminów tożsamości ani progów lokalnego dowodu i nie wprowadzono scalania według tekstu MZ. Druga regresja nadal odrzuca rzeczywistą zmianę bieżących pikseli.

`release-v5.10`: **87/87 PASS**, `assembleDebug` i `compileDebugAndroidTestJavaWithJavac` PASS. Zmienione dwa pliki źródłowe względem dokładnej bazy v5.9, pozostałe 968 zachowane. Procedura próby v5.10 przeszła 12/12 testów sterowania offline, z dodatkowym odrzuceniem dwóch opublikowanych właścicieli w próbie jednego pojazdu nawet przy braku przerw. To testy sterowania, nie modeli ani ruchu fizycznego. Odbiór v5.10 zostanie dopisany po osobnym pomiarze.

Zainstalowany APK v5.10: SHA-256 `58f8143a7cd9ad6a2a694ff0e140c8ba8a448fa7a43dca739339e2d159999b9c`, 88 553 013 bajtów, 970 źródeł z `release-v5.10`. Przed uruchomieniem potwierdzono zachowanie wszystkich 422 plików danych sprzed instalacji, w tym 5 plików preferencji i 335 pierwotnych plików cropów. Instalacja nie wyczyściła historii. Próbę bez mirroringu rozpoczęto na odblokowanym telefonie; dwie kontrole RAM przed START: 1 725 096 i 1 732 580 KiB.

### Wynik v5.10 bez mirroringu: NO_GO, kontynuacja naprawy

Próba trwała 180,016 s od kwalifikacji 10:54:45 do 10:57:45; STOP potwierdzony 10:57:52. Operator odpowiedział: „Na v5.10 były skoki lub zaniki”. Log potwierdza jeden zanik ramki pojazdu przez 12,141 s, zanik tablicy do końca okna (co najmniej 33,464 s), dwie utraty tożsamości i zmianę P1 → P2. Run 2 i scena 2 pozostały stałe, wszystkie kontrole potwierdziły brak mirroringu, bez pauzy RAM i błędu AndroidRuntime. Odbiór fizyczny oraz ciągłość: **NO_GO**; poprawka wzorca nie rozwiązała całego problemu.

Mediany rzeczywistej inferencji MP / MT / MZ: 834,970 / 692,542 / 949,045 ms; PRE + INF + POST: 1764,653 / 1095,478 / 1113,584 ms. Wykonania z zachowanym rozruchem: 62 / 41 / 17. Mediana KLT 64,875 ms, maksimum 206,756 ms, jeden autor 17564. Galeria po STOP zawiera jeden P1 i 17 obserwacji, 7 wariantów surowego MZ. P2 nie zdążył otrzymać odczytu, więc brak drugiego wpisu galerii nie dowodzi stabilnego numeru HUD. Potwierdzono zachowanie 38 plików modeli i wszystkich 417 plików cropów istniejących przed instalacją v5.10.

Dowody: `device/without-mirroring-v5.10/`, w tym dokładna odpowiedź operatora, `analysis.json`, `presentation_assessment.json`, archiwum danych i `gallery_opened_verified.png`. Pierwszy `gallery.png` uchwycił jeszcze ekran główny; późniejszy zrzut pokazuje rzeczywisty arkusz. Ocena procedury pozostaje PASS_DESCRIPTIVE, bez przenoszenia jej na płynność. Narzędzie oceny uwzględnia teraz również wynik ruchu operatora przypisany do dokładnej próby i trybu; 13/13 kontroli offline PASS obejmuje odrzucenie oceny pozytywnej na podstawie samych liczników przy negatywnej odpowiedzi operatora.

W chwili ruchu (10:57:09–11) zgodność lokalnych pikseli spadała stopniowo, mimo dostępnego globalnego przesunięcia 0,02–0,04 szerokości obrazu. Nie poprzedziło jej odrzucenie nowego wzorca. Regresja lokalnego ruchu o 10 px pomiędzy próbkami odtwarza utratę przy pierwszym przesunięciu już na v5.10. To syntetyczny test geometrii z teksturą, nie odtworzenie surowych klatek zdarzenia na telefonie. Naprawa v5.11 jest w toku; nie zainstalowano wariantów, które nie przeszły tej regresji.

### v5.11: lokalne punkty inicjalizowane ruchem właściwych klatek

Ruch kamery jest teraz przekazywany z dokładnymi znacznikami źródła i celu. Tracker używa go tylko dla tego samego kontekstu, domeny kamery, numerów i czasów dwóch rzeczywistych próbek (odstęp do 500 ms) oraz mocnej przestrzennej jakości ruchu. Wskazanie inicjalizuje dopasowanie lokalnych punktów w pełnej rozdzielczości luminancji; nadal wymagane są lokalne gradienty, sprawdzenie w przód i wstecz, spójne affine oraz zgodność wyglądu. Pustego obrazu nie można zastąpić ruchem tła. Nie przenosi się wskazania na replay ani na obcy pomiar. Zwykły DYN publikuje geometrię bieżących pikseli bez drugiego filtru Kalmana; kontroler prezentacji pozostaje właścicielem wygładzania przypisanej pary.

Główny zestaw `release-v5.11`: 102/102 PASS. Końcowy `release-v5.11-sealed`: 47/47 PASS po dodatkowej ochronie oczekującego źródła, z testami wyglądu starego trackera, maski badawczej i granic generacji. APK i kompilacja testów Android PASS, bez twierdzenia o wykonaniu instrumentation na telefonie. Cztery pliki źródłowe zmienione względem zabezpieczonej v5.9, pozostałe 966 zachowane; brak dodanych lub usuniętych źródeł. Nie instalowano pośrednich wariantów, które nie przeszły regresji. Swobodne przeszukiwanie tekstury odrzucono, gdy test ujawnił fałszywe dopasowanie po rzeczywistej zmianie obrazu.

Zainstalowano `alpr-dynamic-continuity-20261118-v5.11.apk`, SHA-256 `1b23f7a7f73d2b7a237f28672dea0afc63eaf5397f01ec5e3428deaf6c5f66a6`, 88 553 013 bajtów. Przed uruchomieniem zachowano wszystkie 430 wcześniejszych plików danych, w tym 5 plików preferencji i 335 pierwotnych plików cropów. Modele, ich wykonanie oraz progi rozmiaru 256 × 112 / 100 × 64 pozostały bez zmian. Porównanie odpowiednich preferencji przed instalacją v5.9, v5.10 i v5.11 nie wykazało zmian.

Pierwsza próba v5.11 bez mirroringu nie osiągnęła kwalifikacji i została automatycznie zatrzymana: START 11:24:43, STOP potwierdzony 11:25:38. W zamkniętym zapisie wykonano 5 MP, zero MT i zero MZ; szósty MP zakończył się już podczas zatrzymywania. Pełny log tego PID wyjaśnia brak kandydata: MP zwracał 1–2 detekcje, wszystkie odrzucone przez filtr klas; `class_eligible=0`, dozwolone klasy `[2,3,5,7]`, próg confidence 0,25. Brak kandydata wystąpił przed kotwiczeniem trackerów, więc nie jest pomiarem podążania v5.11. Nie ma odbioru ruchu ani trzech minut kwalifikowanej analizy. Zapis `device/without-mirroring-v5.11/` zachowano, w tym pełny log PID, rzeczywisty obraz pojazdu przed STOP oraz oceny NOT_VERIFIED dla ruchu i tożsamości. Podgląd ponownie włączony, analiza zatrzymana; oczekiwana niewielka korekta kadru przez operatora.


### Ponowiona próba v5.11 po korekcie kadru, bez mirroringu

START 11:36:55, kwalifikacja 11:37:03, koniec okna 11:40:03; STOP potwierdzony 11:40:09. Okno 180,015 s, PID 20344, run 3 i scena 4 stałe, 15 kontroli bez agenta mirroringu. Dostępny RAM podczas analizy 1 320 168–1 446 568 KiB, bez pauzy pamięci i AndroidRuntime. 177 publikacji po pierwszym kompletnym stanie: wyłącznie 1/P1, zero przerw pojazdu/tablicy/tekstu, zero utrat tożsamości. Jeden autor KLT 21235; mediana aktualizacji 87,561 ms, maksimum 268,170 ms. Zapis zawiera 17 próbek globalnego ruchu. To dowód ciągłości opublikowanego stanu, nie pomiar błędu położenia.

| Model | Wykonania z zachowanym rozruchem | Mediana INF | Mediana PRE + INF + POST |
| --- | ---: | ---: | ---: |
| MP | 75 | 842,269 ms | 1783,969 ms |
| MT | 24 | 738,814 ms | 1151,260 ms |
| MZ | 9 | 995,838 ms | 1158,511 ms |

Galeria rzeczywiście pokazuje jeden P1 i 9 obserwacji. Archiwum zawiera 5 surowych wariantów MZ, jeden entity_id, jeden track pojazdu i jeden track tablicy; wszystkie obserwacje mają scenę 4. Nazwa sesji pochodzi z wcześniejszego otwarcia okna akwizycji o 11:24; przypisanie dowodów do bieżącej próby oparto na rzeczywistych obserwacjach i scenie, a nie na godzinie katalogu. Pierwszy niekwalifikowany start nie miał zapisanych obserwacji. Potwierdzono zgodność wszystkich 38 plików modeli i zachowanie wszystkich 425 plików cropów sprzed instalacji. Dowody: `device/without-mirroring-v5.11-retry-01/`.

Procedura i ciągłość prezentacji: PASS_DESCRIPTIVE. Płynność fizyczna i łączny odbiór pozostają NOT_VERIFIED do odpowiedzi operatora dla tej dokładnej próby. GT odczytu: N/A; odbiór drogowy: NOT_VERIFIED.

Dodatkowe ustalenie: mimo ciągłej pary ramek MT i MZ przestały kończyć nowe analizy. Ostatnie wykonania: MT 2026-11-19T11:38:26.273000, MZ 2026-11-19T11:38:23.258000; do końca okna minęło odpowiednio 96.916 s i 99.931 s. Log kolejki żąda świeżego MP. Audyt źródła potwierdza, że bramka rozmiaru MT wymaga surowego MP dla dokładnie tego obrazu; sama ciągła geometria prezentacji nie spełnia tego kontraktu. Nie zmieniono progów, nie przemianowano lokalnych pikseli na pomiar MP i nie uznano samego timera za pełny odbiór akwizycji. Szczegóły w `model_activity_assessment.json`.


### Powtórki v5.11 nr 02 i 03 oraz diagnostyka v5.12

Powtórka 02: START 12:20:02, STOP potwierdzony 12:20:57, 10 MP / 0 MT / 0 MZ w zapisie, bez kwalifikacji. Powtórka 03 po restarcie samej aplikacji: START 12:25:15, STOP 12:26:10, 11 MP / 0 MT / 0 MZ w zamkniętym zapisie (dwunasty MP podczas zatrzymywania). W obu MP zwracał zero surowych detekcji, bez pauzy RAM. Poprzednie 9 obserwacji nie należy do tych powtórek: porównania archiwów wykazały zero nowych oraz zachowanie wszystkich 839 wcześniejszych obserwacji. Ruch i ciągłość pozostają NOT_VERIFIED dla tych niekwalifikowanych startów.

Operator następnie wybrał kompilację zapisującą rzeczywiste wejście i wynik MP. Ten rozszerzony zakres jest opisany w [raporcie diagnostyki MP](android_mp_input_diagnostic_20261119_v1.md). Testy źródeł v5.12: 62/62 PASS; kontroler zapisu po poprawce: 5/5 PASS i rzeczywisty drugi zapis z potwierdzonym automatycznym STOP po około 19 s. Zachowano wszystkie wcześniejsze APK, logi i archiwa, w tym błędny pierwszy kontroler.
