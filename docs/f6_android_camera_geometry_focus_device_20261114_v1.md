# A12 — zamknięcie odbioru cząstkowego, 14.11.2026

**Odbiór zamknięty na polecenie operatora. Geometria TV i pole F6: NOT_VERIFIED. FINAL176: NO_GO.** Nie uruchomiono kolejnego pomiaru, testu pięciu zdjęć ani FINAL176. Nie ingerowano w Desktop.

Operator wykonał zwykły Run ▶. Odczytana zainstalowana baza APK ma SHA-256 **e5d513edcc3a496ffbb89917794375f337516f34eca06b9e4f5e78461284cb26**, build UTC **2026-11-14T01:33:17.830402800Z**, 83 181 869 B. Jest identyczna z lokalnym `app/build/intermediates/apk/debug/app-debug.apk`; kopię zachowano jako `operator-run-installed-match.apk`. Podpis v2 PASS, certyfikat debug zgodny z A12. Nie ma nakładek Apply Changes. Sprawdzone źródła app/src są identyczne z zamkniętą dostawą hostową v1.

Pełna debug APK dostawy hostowej ma SHA-256 **4fcb0dd33fac84d9ebafd17e7e784c3142c4ef11cb8454bde16be36a207485bf**, build UTC **2026-11-14T01:27:21.756952500Z**, 87 300 688 B. To inny plik niż zoptymalizowany pakiet Run — nie utożsamiamy ich bajtów ani wyników czasowych. Instalację wykonał operator. Agent wykonał tylko odczyty ADB; bez instalacji, czyszczenia danych, force-stop i sterowania START/STOP.

## Potwierdzone na A12

| Obserwacja | Wynik |
|---|---|
| Żądanie operatora | 1440×1080 |
| Rzeczywisty Preview | **960×720 PRIVATE, 4:3** |
| Rzeczywisty ImageAnalysis | **1440×1080 YUV, 4:3** |
| Źródło w logu zapisu TV | **1080×1440 po obrocie**, crop **[0,0,1080,1440]**, binding 3 |
| Wspólny binding tych strumieni | PASS_BINDING_ONLY — A12 utrzymywał oba jednocześnie |
| Pełny kadr w obu edytorach i poprawność pola F6 | **NOT_VERIFIED** |
| Utrzymanie AF na fizycznym TV | **NOT_VERIFIED** — brak potwierdzenia HELD w tej próbie |
| Pięć zdjęć, source_image i wejście MT bez kodów | **NOT_VERIFIED** — próby nie rozpoczęto |

Wcześniejszy odczyt nowego buildu przy żądaniu 1920×1080 pokazał Preview 1280×720 i analizę 1920×1080, również wspólne 16:9. Nie uznajemy tego za odbiór całego TV lub poprawność zapisanych narożników.

Na screenie `ui-1440-before-af.png` widać TV i planszę w głównym podglądzie. To screenshot UI, nie źródłowy obraz ocenianego MT. Log `tv_saved` z 02:48:33 potwierdza zapis TV w nowej geometrii, ale kliknięcie zapisu nie potwierdza prawidłowego obrysu.

**Użytkownik zgłosił, że boczne QR przecinają obrys kalibracji TV, i zlecił agentowi Desktop korektę rozmieszczenia znaczników.** Nie zatwierdzamy stanowiska ani pola F6. Z samego zgłoszenia nie rozstrzygamy, czy cała przyczyna jest po stronie layoutu PC, czy również obrysu kalibracji. Nie zmieniano teraz żadnego z nich.

## Ostrość — obserwacja, nie odbiór blokady

Nowe logi przy stałym bindingu 3 pokazują powtarzane przejścia PASSIVE_SCAN (1) → PASSIVE_FOCUSED (2), zmiany odczytu focus_distance oraz tryb CONTINUOUS_PICTURE (4), trigger IDLE. W tym oknie potwierdzono ponawiane automatyczne ostrzenie bez rebindu; AE także reguluje ekspozycję. Nie przenosi się tego automatycznie na całą historyczną sesję 005742. Ujemny surowy odczyt focus_distance HAL nie został przeliczony na odległość fizyczną.

Nowa funkcja AF ma **2/2 PASS hostowo** wraz z kontrolą geometrii i eksportu. Na A12 nie potwierdzono jeszcze udanego HELD i utrzymania przez pięć zdjęć. Nie przedstawiamy syntetycznego future jako odbioru sprzętu.

Dowody: `observation-01..03`, `geometry-events.json`, `af-scan-cycles-binding3.txt`, `operator-run-signature.txt`, `ui-1440-before-af.png`, `status.json`. `report-in-progress.md` zachowano jako wcześniejszą notatkę; niniejszy raport jest wynikiem zamknięcia.

Dalszy odbiór tylko z udziałem operatora, po korekcie planszy: weryfikacja TV/pola F6, potwierdzenie AF, jedna próba pięciu technicznych zdjęć, STOP i eksport. Dopiero wspólny odbiór Desktop+A12+TV może dopuścić FINAL176. Bez GT technicznych zdjęć nie wylicza się skuteczności.
