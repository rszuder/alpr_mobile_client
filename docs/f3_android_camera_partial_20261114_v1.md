# Android F3 — PARTIAL, kamera i przekazanie do odbioru, 14.11.2026, v1

Wdrożono odczytywane w tle rozliczenie F6 w istniejących szczegółach historii. Zabezpieczono rzeczywisty zapis i oba eksporty. Zachowano wspólny CameraX ViewPort, AF i poprzednią poprawkę czterech narożników. **Trzy celowane kontrole hostowe PASS; APK debug i kompilacja instrumentacji offline PASS. Nowej APK nie instalowano. Fizyczny wybór rozdzielczości, AF i TECHNICAL5 pozostają NOT_VERIFIED; FINAL176 NO_GO.** Odbiór z operatorem ma osobne dowody, nie zmienia niniejszej zamkniętej dostawy hostowej.

## Tożsamość i zabezpieczenie

Repo `C:\Users\48572\AndroidStudioProjects\ALPR_v1`, gałąź `main`, HEAD `f7943a7d7334afd1e4188ef9860b2126331926c8`, stan dirty przed i po zadaniu. Nie wykonywano commit/push/reset/checkout/clean/stash, instalacji zależności, instalacji APK, force-stop ani czyszczenia danych. Desktop był wyłącznie odczytywany.

Wszystkie ścieżki względne poniżej odnoszą się do nowej dostawy:

`C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f3_android_camera_partial_20261114_v1\`

| Dowód | Lokalna kopia | Rozmiar B / SHA-256 |
|---|---|---|
| Właściwy pełny eksport | `real_files/alpr_session_20261114_044745_sprawdzam.alprsession` | 416787416 / `fe34f86d72eb49ee6e4ec303ad2eae25921e106003d7f29a0c411ea2f8da9a11` |
| Surowa sesja, wraz z obrazami, dziennikami i finalnym ZIP | `device_readonly/raw-session-original.tar` | 858228736 / `6957cc61249862570d46103575e28c57ea9a4c9b1695a5c04dfe87c4bd2316db` |
| Wcześniejszy przerwany transfer — zachowany, wyłączony z analizy | `real_files/INCOMPLETE_DO_NOT_ANALYZE-alpr_session_20261114_042337.alprsession` | 374603776 / `7bf67f000a24d1baae5023b4eebf4974667ceed38f287e7168026e10a17593a5` |

Źródła dwóch eksportów: pliki o odpowiadających nazwach bez prefiksu `INCOMPLETE_DO_NOT_ANALYZE-` w `C:\Users\48572\Desktop\`. Kopie i oryginały mają zgodne SHA; rozmiar i mtime właściwego źródła były stabilne. Tar zawiera **2320 plików** surowego katalogu `files/research/sessions/exp-1794625079647-242826bb`. Inwentaryzacja telefonu oraz SHA kluczowych dzienników, metadanych i finalnego ZIP były identyczne przed/po kopiowaniu. Zawartość tych plików w tar odpowiada SHA z telefonu.

Zachowano `alpr_ui-before.xml`, logcat bez czyszczenia, oba rotowane `alpr.log*`, pełną identyfikację pakietu, kopię APK i odczyt serwisu kamery. Dowody stabilności: `device_readonly/source-provenance.json`, `session-critical-before.sha256`, `session-critical-after.sha256`, inwentaryzacje i `host-only-preservation.json`. Po pracach hostowych wszystkie wartości preferencji i lista identyfikatorów sesji telefonu pozostały identyczne. Nie tworzono tam żadnej sesji.

## Dokładne rozliczenie exp-1794625079647-242826bb

Pełny ZIP: **CRC PASS, 2323/2323 SHA manifestu PASS**, bez powtórzonych wpisów. Native `Final180ArchiveValidator` przyjmuje autentyczny eksport jako **PARTIAL**. Nie wykonywano recovery ani ponownej finalizacji.

| Wielkość | Wynik |
|---|---:|
| MT_STARTED / MT_RETURNED / MT_DECODED | 390 / 390 / 390 |
| MZ_STARTED / MZ_RETURNED | 185 / 185 |
| MT_FAILED / MZ_FAILED | 0 / 0 |
| Klatki otwarte / domknięte | 629 / 629 |
| Pełne obrazy / cropy TV | 629 / 629, wszystkie WRITTEN |
| Brakujące obrazy / błędy przetwarzania w indeksie | 0 / 0 |
| Sceny zapisane / brakujące | 420 / 0 |
| Role MT COMPLETE / INCOMPLETE | 201 / 189 |
| Role bez unieważnienia | 177 |
| Role unieważnione przez scene_superseded | 213 |
| Z nich INCOMPLETE / NOT_REACHED | 188 |
| Z nich INCOMPLETE / COMPLETED | 1 |
| Z nich COMPLETE / COMPLETED | 24 |
| Próby / unieważnione próby | 428 / 227 — próby nie są liczbą wywołań MT |
| RAW / FINAL / suppressed / unresolved attempt IDs | 294 / 187 / 13 / 94 |
| Zdarzenia oczekiwane / zapisane / odczytane | 3188 / 3188 / 3188 |
| Straty kolejki próbek / telemetrii / dziennika F6 | 0 / 0 / 0 |
| Błędy projekcji / inflight po finalizacji | 0 / 0 |

Wszystkie rozpoczęte MT mają tę samą tożsamość i SourceStamp w zwrocie/dekodowaniu/rolach oraz odpowiadającą zapisaną klatkę. Wszystkie 185 MZ mają zgodne źródło oraz powiązanie z rzeczywistym MT. Zero błędów backendu wynika także z potwierdzonych zwrotów, a nie tylko braku rekordów błędów. `completion_reason=manual`, `last_write_error=""`, `scene_evidence_complete=true`, `mt_detection_roles_complete=false`, `f6_evidence.complete=false`, `collection_complete=false`. Czas według zegara monotonicznego: około 17 min 19,675 s.

**Łańcuch nadania PARTIAL:** `ResearchSessionStore.finalizeSession → buildArchive → finalizeSamples → Final180Evidence.finalizeStored`. Liczby wywołań i ról są zgodne, brak zadeklarowanych strat, ale 213 ról jest unieważnionych i 189 nie ma kompletnej populacji FINAL. Jednocześnie 213 rekordów klatek jest unieważnionych. Oba warunki uniemożliwiają potwierdzenie kompletności. Pole `tv_bbox_input_evidence.pending_loss_unknown=true` agreguje także te unieważnienia; nie jest samodzielnym dowodem utraty obrazów. Podsumowanie F6 ma `pending_loss_unknown=false` i `unlocated_loss_possible=false`.

Nie podniesiono PARTIAL do COMPLETED. Anulowania są rzeczywistą utratą ważności wyniku i mogą ograniczać ocenialność. Nie dowodzą utraty pliku; brak pełnego FINAL nie został zamieniony na zero detekcji. Nie zmieniano ani nie przeliczano wcześniejszych wyników Desktopu N/A.

Zapisane przedziały `MT_STARTED → MT_RETURNED`: mediana 773,27 ms, p95 837,74 ms; `MZ_STARTED → MZ_RETURNED`: mediana 1025,43 ms, p95 1114,89 ms. Są to przedziały dziennika, nie deklaracja wyłącznego czasu backendu ani porównanie rozdzielczości.

Wykryto również **jedną** obserwację MP `NOT_RUN` z pustą tożsamością sceny i pustą kopertą źródłową zdarzenia. Jej zagnieżdżony rekord MP zachowuje pełny SourceStamp i `trace_frame_id=395`. Dokładne dopasowanie prowadzi do jednej klatki `6fa16709d0bfdefefa4908319e04c79149f2f6441729a05e51fb3ffba79f6b29` i jej FRAME_FINISHED; nie jest to zgadywanie czasu. Producent celowo projektuje do scen tylko obserwacje mające rzeczywisty identyfikator sceny. Uzupełniający weryfikator hostowy wcześniej odrzucał ten poprawnie zachowany, źródłowy przypadek. Poprawiono tylko testowy weryfikator: nadal wymaga wszystkich pól i prawdziwego NOT_RUN oraz sprawdza relację z klatką przez pełny SourceStamp i trace. Nie dodano fikcyjnego MP ani sceny, nie zmieniono walidatora Desktopu.

Skrypt audytu i wyniki: `audit/audit_real_session.py`, `real-session-audit.json`, `real-history-text.txt`.

## Kamera i AF: stan rzeczywisty, nie wybór docelowy

Zainstalowana i użyta w tej sesji APK: SHA `b1d95ae8f23d80d6569d24acb2a5239dd55d349b65083eefaf3df9550de3c08c`, build UTC `2026-11-14T02:53:44.433449100Z`, 83217417 B. To inny plik niż nowa pełna APK hostowa.

| Parametr | Odczyt rzeczywistej sesji |
|---|---|
| Kamera / zoom | 0 / 1× |
| Żądanie | 1440×1080 |
| Preview | 960×720 PRIVATE; crop [0,0,960,720], rotation 90° |
| ImageAnalysis | 1440×1080 YUV; crop [0,0,1440,1080], rotation 90° |
| Pełny obraz po orientacji | 1080×1440, crop [0,0,1080,1440] |
| F6 | [166,506,851,963], 685×457 |
| AF przy START | NOT_REQUESTED; capture AF mode 4, state 2 — ciągły AF, nie potwierdzona blokada |

Kod już stosuje wspólny `UseCaseGroup/ViewPort(FIT)`, rzeczywiste cropy i transformacje CameraX. Zachowano go. Kamera 0 deklaruje PRIVATE/YUV dla **1440×1080, 1920×1080 i 1280×960**. W tej liście **nie ma 1920×1440**. Sama deklaracja nie dowodzi wspólnego bindingu ani widoczności TV. Podczas odczytu kamera była nieaktywna; stan ostatniego rzeczywistego bindingu zapisano w zrzucie serwisu, bez uruchamiania nowego.

Nie wybrano automatycznie 1920×1080. Dotychczasowe obrazy nie dowodzą działania F3 w tym trybie. Istniejący AF ma jawny future success/failure/timeout, `disableAutoCancel`, unieważnienie po zmianie źródła i zwolnienie przy STOP; nie zmieniono jego działania. Dane: `device_readonly/camera-configurations.json` i `camera-service-before.txt`.

## Zmiana UI i wykonane kontrole

Produkcja: `ResearchSessionSummary` odczytuje wybrane archiwum strumieniowo istniejącym czytnikiem JSONL, sprawdza CRC/SHA metadanych i dzienników, a brak/błąd wyświetla jako „nieustalone”. Liczby obrazów są jawnie opisane jako dane indeksu z kontrolą obecności wpisów ZIP; panel nie udaje pełnego badania wszystkich bajtów obrazów. `ResearchSessionPicker` korzysta z istniejącego executora przekazanego przez MainActivity, odrzuca wyniki zamkniętego okna i obsługuje niedostępny executor. Nie zmienia archiwów, konfiguracji, identyfikatorów ani flag sesji.

Trzy unikalne kontrole JVM:

1. **A1 PASS:** istniejący test mapowania rozszerzony do 4:3/16:9, czterech obrotów, różnych buforów oraz rzeczywistych cropów obliczonych przez CameraX dla różnych proporcji strumieni. Sprawdza kolorowe piksele po produkcyjnym konwerterze, punkty TV/F6, crop offsety, invalidację starego zatwierdzenia oraz odrzucenie starego callbacku. Wzorzec syntetyczny jest mały; nie jest to odbiór fizycznego FOV A12.
2. **A2 PASS:** rzeczywiste MainActivity, kontroler AF, prefs, producent, finalizator i historia; wyłącznie IO kamery/modeli jest syntetyczne. Sprawdza AF failed/pending/timeout/late result/success, brak auto-cancel po 7 s, snapshot HELD przed START, jeden START/STOP, zwolnienie AF, historię i restart bez dodatkowej sesji, odrzucony START i zmianę źródła bez utraty zatwierdzeń.
3. **A3 PASS:** zabezpieczony autentyczny ZIP przyjęty jako PARTIAL przez produkcyjny walidator grafu i uzupełniający kontrakt MP; rzeczywiste liczby w nowym panelu, bez zmiany SHA/statusu. Negatywne testy brakującego/uszkodzonego dziennika, sum, brakujących obrazów, niedozwolonego MP, konfliktu SourceStamp i spóźnionego callbacku okna. Brak pola profilu F6 nie jest zastępowany etykietą R0_FINAL. Prywatny materiał wskazuje się przez `F3_REAL_ARCHIVE`; w zwykłym CI bez niego ta kontrola jest jawnie pomijana, a nie zastępowana syntetycznym PASS.

Pierwszy przebieg: 3 FAIL — asercja cropu oparta na błędnym założeniu o FIT, asercja wyłączenia przycisku zamiast rzeczywistej bramki FOCUS i opisany błąd weryfikatora MP. Następny przebieg: 3 PASS, build i kompilacja instrumentacji PASS. Następnie wzmocniono kontrolę obserwacji bez sceny; dodatkowy przebieg ujawnił, że źródło tej obserwacji jest w `details.mp`, a nie w kopercie. Po poprawieniu złączenia **A3 ponownie PASS**. Nie sumuje się powtórzeń jako nowych testów. A1/A2 nie powtarzano po zmianach dotyczących wyłącznie testowego weryfikatora A3. Instrumentację skompilowano, nie wykonywano na urządzeniu.

Logi: `audit/three-checks-and-build-1.log`, `three-checks-and-build-2.log`, `a3-source-only-mp-final.log`, `a3-exact-stamp-final.log`; XML i dokładny wynik końcowy w `audit/final-results/` oraz `final-test-summary.json`. Użyto `gradlew.bat --offline`, JBR Android Studio, 1024 MiB dla JVM testów, pojedynczego forka i trzech filtrów `--tests`; późniejsze powtórzenie dotyczyło tylko A3. Nie uruchamiano pełnej regresji ani ponownego generatora 4100 klatek.

## Kontrakt F3 i ochrona

Odczytano gotową dostawę Desktopu `F6_F3_PAIRQR_20261114_001`, raport i `final_status.json`; ich kopie i SHA są w `integration/`. Android potwierdza zgodność minimalnego kontraktu: **bez nowego formatu sesji i bez dekodera QR na telefonie**. Pełna klatka, SourceStamp/frame_key i rzeczywiste MT/FINAL/MZ pozostają źródłem powiązań. Desktop dekoduje różne L/R z tej samej klatki i odpowiada za manifest, indeks, fazy oraz GT. Formalny odbiór fizyczny wspólnej konfiguracji nadal NOT_VERIFIED.

TECHNICAL5 F3:
`C:\Users\48572\Desktop\dyplom\start4\output\F6_F3_PAIRQR_20261114_001\technical_plans\prepared_20261114T095315Z_5e7fceea`

Manifest SHA `e45d0586b3dd5535b51a608d51f5daf11786c10c38b4599cf1cf12944e07d6e3`.

FINAL176 F3 — dopiero po odbiorze:
`C:\Users\48572\Desktop\ALPR_EKSPERYMENTY\F6-FINAL180-A12-01\prepared_20261114T095317Z_fe0c4b44`

Manifest SHA `f3c59b4791ddde3ec02d38bc8696e421a1368bcff2ff140484eefa519339b6d4`; referencja nadal `7df541895524d6a8bb916da41fe5b347382ad44ba370be8921b77ca455b5937f`. Sprawdzono bajtowe sumy obu manifestów, nie przeprowadzano nowej oceny zdjęć.

**8196 plików wcześniejszych dostaw Androida bez zmian SHA/rozmiaru/mtime.** Wszystkie 14 wpisów assets/lib nowej APK są identyczne z poprzednią pełną dostawą corner_frame_nudge. Modele, parametry, MT 512×512 / MZ 320×320, R0/MP OFF, progi, scheduler, F5A, źródłowe kadrowanie i format eksportu nie zmieniły się. Nie powstaje nowa paczka eksportera, bo producenta danych nie zmieniano. Duże prywatne kopie pozostają w ignorowanym app/build, poza Git i małą paczką do przeglądu kodu.

## Nowa APK i dokładny punkt wznowienia

APK: `C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f3_android_camera_partial_20261114_v1\alpr-f3-camera-partial-debug.apk`

- 86641356 B, SHA **`510b15b7966d56b479b07b7406a27454161b184b20f05b8143b314e9bbac8c54`**.
- Build UTC **2026-11-14T10:23:24.648240700Z**, package `com.example.alpr_v1`, versionCode 1 / versionName 1.0, dirty HEAD podany wyżej. Tożsamość potwierdzono w DEX APK, nie tylko wygenerowanym BuildConfig.
- Podpis v2 PASS; certyfikat SHA `f3385991af6f601faf4df59697d30f143101a71ee988035745027d3e85aa04a2`, identyczny z odczytaną APK telefonu. Instalacja nie wymaga odinstalowania ani czyszczenia danych; wykonuje ją operator zwykłym Run, bez Apply Changes. Run może utworzyć inny bajtowo pakiet ABI — jego SHA trzeba odczytać, nie przypisywać mu automatycznie powyższego SHA pełnej APK.

Punkt wznowienia z operatorem: nowa TECHNICAL5 → nieruchoma plansza F3 TEST_ONLY, bez START PC → Run Androida → wybór 1440×1080 i odczyt obu rzeczywistych strumieni → kontrola TV/F6 i jawne ustawienie AF → osobny krótki zapis T. Potem analogicznie 1920×1080, o ile nie ucina TV. Nie używać starych punktów innego źródła bez ponownego zatwierdzenia.

Porównanie: 30 s na konfigurację, minimum 10 różnych źródeł. Desktop sprawdza oba QR/ArUco, moduły, reprojekcję i pokrycie JPG/MT; zestawienie musi obejmować również nieudane klatki. Odrzucić niezgodną geometrię. Wybrać najwyższy udział poprawnych par z kompletem ArUco, potem minimalny rozmiar modułu i p95 przetwarzania; równorzędny wynik pozostawia bazowe 1440×1080. Bez wystarczających danych nie ogłaszać wyboru.

Po wyborze potwierdzić TV/F6 dla jednego źródła i wykonać jedną próbę pięciu zdjęć, z Android STOP na POSTROLL T. GO wymaga integralnego eksportu, niezależnych par L/R, pełnych ArUco, braku kodów w rzeczywistym wejściu MT i dowodu pierwszeństwa dla pięciu ekspozycji. Dopiero wspólny odbiór może dopuścić FINAL176; hostowe PASS go nie zastępuje.
