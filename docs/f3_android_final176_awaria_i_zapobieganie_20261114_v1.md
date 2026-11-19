# F6 / F3 FINAL176 — raport awarii, stanu zapisu i zapobiegania kolejnym przerwaniom

**Data:** 14.11.2026  
**Wersja raportu:** 1  
**Badana sesja:** `exp-1794671202383-01e39cd5`  
**Urządzenie:** Samsung Galaxy A12 / SM-A125F, Android 12 / API 31  
**Zakres:** Android; analiza zabezpieczonych rzeczywistych danych i lokalnego kodu.  
**Charakter dokumentu:** raport ustaleń oraz zaleceń; nie jest dostawą poprawionej APK.

## 1. Wniosek i decyzja

Proces ALPR został zabity przez systemowy `lmkd` o **17:03:34.652 czasu polskiego**. O 17:03:35.908 Android zarejestrował śmierć tego samego procesu, PID `28387`. W zapisanym logu aplikacji nie ma normalnego STOP ani zakończenia tej sesji. Opis operatora, że aplikacja wyglądała na zminimalizowaną, odpowiada w tym przypadku utracie procesu.

Podczas audytu proces już nie działał. Pozostawiony na dysku stan `RUNNING` jest stanem sprzed przerwania, a nie dowodem dalszego nagrywania. `finished_at` ma wartość `null`; katalog `final` był pusty. **Finalne archiwum `.alprsession` nie powstało.**

Surowe dane zachowały się i zostały zabezpieczone na PC: **1617 plików, 342 416 402 B danych**, z potwierdzeniem SHA każdego pliku względem źródła. Zachowano 259 zwróconych i zdekodowanych MT oraz 124 zakończone MZ. Jedno końcowe MT nie ma zarejestrowanego powrotu ani dekodowania.

Bezpośrednia przyczyna zakończenia procesu jest potwierdzona. **Źródło presji pamięci, która doprowadziła do decyzji `lmkd`, pozostaje NIEUSTALONE.** Nie ma podstaw do przypisania jej wyłącznie ALPR, CameraX, backendowi modeli albo Android Studio Device Mirroring.

**Decyzja dla następnego FINAL176: NO_GO do sprawdzenia warunków zasobowych i normalnego zakończenia dłuższej próby technicznej.** Krótkie wcześniejsze odbiory kodów i geometrii nie stanowią potwierdzenia stabilności przez cały pokaz.

Stan telefonu opisany w raporcie pochodzi z odczytów wykonanych podczas audytu po awarii, do około **17:22 czasu polskiego**. Raport nie jest ciągłym monitoringiem późniejszego stanu urządzenia.

## 2. Pochodzenie i ochrona materiału

### 2.1. Repozytorium i tożsamość aplikacji

| Element | Wartość lub zakres potwierdzenia |
|---|---|
| Repo Androida | `C:\Users\48572\AndroidStudioProjects\ALPR_v1` |
| Gałąź | `main` |
| HEAD | `f7943a7d7334afd1e4188ef9860b2126331926c8` |
| Drzewo robocze | Zastane liczne lokalne zmiany F6; zachowane |
| Pakiet | `com.example.alpr_v1` |
| Wersja z metadanych sesji | `1.0`, versionCode `1`, build `debug` |
| Czas kompilacji zapisany w sesji | `2026-11-14T11:02:11.004793900Z` |
| Stan źródeł zapisany w sesji | `git_dirty=true`, `source_state=dirty` |
| Bazowy APK SHA-256 zapisany w sesji | `b1d95ae8f23d80d6569d24acb2a5239dd55d349b65083eefaf3df9550de3c08c` |

SHA powyżej dotyczy obserwowanego **bazowego `base.apk`**. Wcześniejszy odczyt po Run wykazał używanie nakładek DEX Android Studio. Bazowego SHA nie należy utożsamiać z kompletną tożsamością wszystkich plików kodu wykonywanego przez Run. Sam HEAD repozytorium również nie identyfikuje całej zainstalowanej aplikacji. Audyt awarii nie instalował APK ani nie usuwał nakładek.

### 2.2. Kopia surowej sesji

Źródłowy katalog telefonu, odczytywany z uprawnieniami debugowalnej aplikacji:

```text
files/research/sessions/exp-1794671202383-01e39cd5/
```

Oczekiwany, ale nieutworzony plik finalny:

```text
files/research/sessions/exp-1794671202383-01e39cd5/final/exp-1794671202383-01e39cd5.alprsession
```

Katalog zabezpieczenia na PC:

```text
C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f3_final_session_incident_20261114_v1\
```

Główna kopia:

```text
C:\Users\48572\AndroidStudioProjects\ALPR_v1\app\build\f3_final_session_incident_20261114_v1\raw-session-original.tar
```

| Właściwość kopii | Wynik |
|---|---:|
| Pliki źródłowe | 1617 |
| Bajty plików źródłowych | 342 416 402 |
| Pliki w TAR | 1617 |
| Rozmiar TAR | 345 300 480 B |
| Brakujące lub dodatkowe pliki | 0 |
| Niezgodne rozmiary/SHA skopiowanych plików | 0 |
| Zmiany rozmiarów/mtime źródła między odczytami | 0 |
| Zmiany SHA metadanych między odczytami | 0 |

SHA-256 `raw-session-original.tar`:

```text
e8e2070c9b5056851d2c7211c8e9b2de7f57775de2219d55e91538de7edefc1b
```

TAR jest **kopią surowego katalogu**, nie gotowym eksportem `.alprsession`. Potwierdzenie jego SHA nie jest potwierdzeniem CRC nieistniejącego ZIP-a ani przyjęcia sesji przez importer Desktopu.

### 2.3. Pliki dowodowe

Wszystkie poniższe ścieżki są względne wobec katalogu zabezpieczenia podanego wyżej.

| Plik | Zawartość |
|---|---|
| `incident_summary.json` | Zbiorczy wynik audytu i stan z ostatniego odczytu |
| `preservation_result.json` | Wynik zabezpieczenia i porównania źródła z kopią |
| `durable_journal_audit.json` | Liczniki i powiązania z trwałych dzienników |
| `incident_correlation.json` | Chronologia systemowa i sprawdzenie braków obrazów |
| `raw_tar_inventory.json` | Rozmiary i SHA elementów TAR |
| `source_sha256_before.json` | SHA plików źródłowych przed kopiowaniem |
| `source_stats_before.json`, `source_stats_after.json` | Rozmiary i mtime przed/po |
| `source_metadata_sha256_after.json` | Ponowne SHA metadanych |
| `SHA256SUMS.json` | Manifest 58 zabezpieczonych plików kontrolnych |
| `read_only/logcat_events_full.stdout` | Zdarzenia systemowe, w tym `killinfo` i `am_proc_died` |
| `read_only/logcat_crash.stdout` | Bufor awarii, bez jego czyszczenia |
| `read_only/logcat_all_tail.stdout` | Zabezpieczony fragment logcat wszystkich buforów |
| `read_only/app_log.stdout` | Log aplikacji |
| `read_only/exit_info_retry.stdout` | Dostępna historia `ApplicationExitInfo` po restarcie usług |
| `read_only/processes_after.stdout` | Lista procesów; identyfikacja PID 406 jako `lmkd` |
| `read_only/configuration_snapshot.stdout` | Odczyt poprawnego XML preferencji |

Ważne sumy kontrolne:

| Plik | SHA-256 |
|---|---|
| `incident_summary.json` | `f7f4d7692f14e10f6fc05fe26d79c77c802dee642faee1bbe4bbbd814de828a2` |
| `preservation_result.json` | `5b5cab5859f2f15e94385b0663ef0cae2d72b4a415fb002d90b63d4e334ead2c` |
| `durable_journal_audit.json` | `665ba94a8513261d539dae6b8c641d65406a104d0c8a56d2d7f60f2e61689b84` |
| `incident_correlation.json` | `e4038e41a8ab16aac7c0ae0f82291053ee2a6fb1bffb9b6abaed20a445e8762c` |
| Źródłowy `session.json` | `5d3e29343a1aff3124fbd9cd38c71904c9c48df50d6cea8441294e3dec2ac1a2` |

Niektóre początkowe próby odczytu nieistniejących plików, np. `session.json.tmp`, zapisały komunikat `cat: ... No such file or directory` w pliku `.stdout`. Nie są one danymi sesji. Autentyczny wykaz plików znajduje się w inwentarzach źródła i TAR; kod zakończenia samego transportu ADB nie był jedynym kryterium poprawności.

## 3. Chronologia zdarzenia

Czasy w tabeli są lokalne, **Europe/Warsaw, UTC+01:00**, 14.11.2026.

| Czas | Zdarzenie | Dowód / interpretacja |
|---|---|---|
| 16:44:33.883 | Potwierdzenie AF `HELD` | Log aplikacji; binding 15, request 18 |
| 16:46:41.016 | Jawne żądanie START | `RESEARCH_START start_requested`, generation 36 |
| 16:46:42.383 | Utworzenie badanej sesji | `created_at` w `session.json` |
| 16:46:45.781 | Początek pomiaru | `started_at` w `session.json` |
| około 17:03:32.434 | Ostatnie trwałe zdarzenie lifecycle | `MT_STARTED` ostatniego wywołania; czas przeliczony z zegara monotonicznego |
| 17:03:34.652 | `killinfo` dla PID 28387 / UID 10683 | Zdarzenie emitowane przez PID 406, zidentyfikowany jako `lmkd` |
| 17:03:35.908 | `am_proc_died` dla ALPR | Ten sam PID 28387 i pakiet `com.example.alpr_v1` |
| 17:03:35.915 | Usunięcie Activity bez zapisanego stanu | `proc died without state saved` |
| 17:04:06.170 | `mmap failed: Out of memory` | Dotyczy **innego PID 19802**, nie PID ALPR |
| 17:05:35.115 | Ponowne otwarcie z ostatnich aplikacji | `startActivityFromRecents:com.android.shell`; jest to osobne zdarzenie po awarii |
| 17:05:35.201 | Start nowego procesu ALPR | PID 25595; nie wznowienie pierwotnego procesu 28387 |
| 17:05:57.686 | Awaria usługi dostawcy kamery | SIGABRT procesu `provider@4.0-service_64`, już po utracie pierwotnej sesji |
| 17:07:39.162 | `PLATFORM WATCHDOG RESET` | Kolejny problem usług systemowych, po wcześniejszej śmierci ALPR |
| podczas odczytów audytu | Brak działającego ALPR; pozostałe `RUNNING` na dysku | Nie ma podstaw do uznania, że pomiar nadal trwał |

Nie przypisano pierwotnego zakończenia ALPR późniejszej awarii dostawcy kamery ani późniejszemu komunikatowi OOM innego procesu. Uptime urządzenia i zmiana procesów systemowych wskazywały na reset usług Androida; raport nie deklaruje na tej podstawie pełnego restartu sprzętowego telefonu.

Historia `ApplicationExitInfo` dostępna po restarcie usług nie zawierała tej śmierci procesu. Zawierała starsze wpisy, m.in. z godzin porannych. Nie użyto ich jako dowodu przyczyny zdarzenia o 17:03. Rozstrzygające są zgodne rekordy `killinfo`, `am_proc_died` i identyfikacja procesu `lmkd`.

Ostatni czas lifecycle wynosi `2522056615777398 ns` w domenie `ANDROID_ELAPSED_REALTIME`. Względem zapisanego START oznacza to około **1006,653 s = 16 min 46,653 s** trwałych zdarzeń. Wartość lokalna w tabeli jest przeliczeniem względem `started_at`, nie zgadywanym wyrównaniem zegara CameraX do zegara PC.

## 4. Konfiguracja rzeczywiście zapisana w sesji

| Element | Zapisana wartość |
|---|---|
| Profil zapisu | `f6_final180_v1` |
| Kategoria eksperymentu | `roi_budget` |
| Etykieta wariantu / polityka inferencji | `R0_FINAL` / `r0_full_frame` |
| Tryb | `static`, `strict_scene_boundary` |
| MP | Wyłączone |
| Wejście | `TV_BBOX_CROP` |
| Żądana rozdzielczość | `1440x1080` |
| Rzeczywisty Preview | 960×720, crop `[0,0,960,720]`, obrót 90° |
| Rzeczywisty ImageAnalysis | 1440×1080, crop `[0,0,1440,1080]`, obrót 90° |
| Pełna bitmapa po orientacji | 1080×1440, crop `[0,0,1080,1440]` |
| Kamera / zoom / binding | `0` / 1× / 15 |
| Zewnętrzny TV BBOX | `[6,469,982,1013]`, 976×544 |
| Pole fotografii F6 | `[147,506,842,978]`, 695×472 |
| Przetwarzanie geometrii | `CROP_ONLY_TRANSLATION`, `H_TV=NOT_APPLIED` |
| MT | `plate-20261009_082038`, TFLite FP32, CPU ×2, 512×512 |
| MZ | `character-20261007_215302`, TFLite FP32, CPU ×2, 320×320 |
| Fingerprint MT / MZ | `7a745c7b88f6c7d2` / `54a6facf7eda424e` |
| Autozoom / Lock | Wyłączone |
| AF przy START | `HELD`; CaptureResult `af_mode=1`, `af_state=4` |

Współrzędne BBOX/ROI w tej tabeli są pikselami pełnej bitmapy kamery po orientacji. Nie są współrzędnymi planszy TV ani piksela tensora MT. Zewnętrzny `crop_image` TV jest kontekstem obejmującym obrzeże i kody. Należy go odróżniać od wewnętrznego pola F6 i zapisu rzeczywistego wejścia MT.

Digest deskryptora TV w badanej sesji:

```text
77cb904edab080a5c6e473fc276820ce204dc6b4a65ec3e6c73ea31dfb5772f8
```

Digest pola F6 w badanej sesji:

```text
7319877f52e12b84003c790d2e9d14d95e3543e286a73fb5d4c803f5d50e71f6
```

Kolejność zapisanych narożników F6: LG, PG, PD, LD. Wartości znormalizowane:

```json
[
  {"x": 0.1368759423494339,  "y": 0.35174429416656494},
  {"x": 0.7791595458984375,  "y": 0.3611642122268677},
  {"x": 0.7694754004478455,  "y": 0.6789582967758179},
  {"x": 0.13657094538211823, "y": 0.6731657981872559}
]
```

AF `HELD` w snapshotcie START nie jest samoistnym dowodem ostrości każdej fotografii przez cały przebieg. Timer Androida ma pozostać OFF zgodnie z uzgodnionym protokołem; 5 s ekspozycji po stronie PC nie należy zamieniać na timeout backendu lub okres resetowania sceny.

## 5. Co rzeczywiście zachowało się w dziennikach

### 5.1. Wykonania i trwałość

| Licznik | Wynik |
|---|---:|
| `FRAME_OPENED`, unikalne klatki | 454 |
| `FRAME_FINISHED`, unikalne klatki | 453 |
| `MT_STARTED`, unikalne wywołania | 260 |
| `MT_RETURNED` | 259 |
| `MT_DECODED` | 259 |
| `MZ_STARTED` | 124 |
| `MZ_RETURNED` | 124 |
| Zapisane rekordy prób / unikalne `attempt_id` | 292 / 292 |
| Rekordy cropów MZ | 124 |
| Brakujące wpisane pliki cropów MZ | 0 |
| Zapisane zdarzenia F6 lifecycle | 2194 |
| Zakres `event_sequence` | 1–2194, bez luk w zachowanym ciągu |
| Stany zapisu prób | 292 `QUEUED`, następnie 292 `WRITTEN` |
| MZ bez istniejącego rodzica MT | 0 |
| MZ z niezgodnym SourceStamp rodzica MT w sprawdzonym powiązaniu | 0 |

292 próby nie oznaczają 292 wywołań MT: próba, wywołanie i wskazanie detekcji są różnymi rekordami. Liczniki nie określają liczby rozpoznanych fotografii ani skuteczności względem GT.

W rekordach prób zapisano 87 statusów MT `NO_DETECTION` i 205 `VALID_QUAD`. Statusy MZ na poziomie prób to 214 `NOT_RUN`, 73 `READ` i 5 `NO_CHARACTERS`. Nie są one liczbą wywołań backendu MZ: tych dziennik lifecycle potwierdza 124. Rozkład nie jest oceną poprawności odczytanych numerów.

Zweryfikowano wszystkie **11 plików JSONL, łącznie 7595 rekordów**. Nie znaleziono nieparsowalnych niepustych wierszy. Rozkład:

| Plik | Rekordy |
|---|---:|
| `samples/attempts.jsonl` | 292 |
| `samples/crops.jsonl` | 124 |
| `samples/write_states.jsonl` | 584 |
| `samples/f6_execution.jsonl` | 2194 |
| `samples/scene_evidence_journal.jsonl` | 1100 |
| `samples/tv_bbox_inputs_journal.jsonl` | 907 |
| `telemetry/thermal.jsonl` | 897 |
| `telemetry/events.jsonl` | 848 |
| `telemetry/traces.jsonl` | 261 |
| `telemetry/frame_flow.jsonl` | 384 |
| `telemetry/runtime_execution.jsonl` | 4 |

Brak luk w zapisanej sekwencji i poprawne JSONL nie dowodzą istnienia zdarzeń, których proces nie zdążył dopisać. Końcowa część sesji jest jawnie niedomknięta.

### 5.2. Role MT i anulowania po zmianie sceny

| Stan roli | Liczba |
|---|---:|
| `COMPLETE`, bez anulowania | 85 |
| `COMPLETE`, `scene_superseded` | 54 |
| `INCOMPLETE`, `scene_superseded` | 120 |
| Brak roli dla ostatniego rozpoczętego MT | 1 |

Łącznie istnieje 259 rekordów ról: 139 `COMPLETE` i 120 `INCOMPLETE`. W tych rekordach nie zapisano błędu backendu/przetwarzania. `scene_superseded` występuje również w 198 rekordach prób; jest to inna jednostka zliczania, więc tych liczb nie należy sumować.

Anulowanie wyniku po zmianie sceny należy odróżnić od śmierci procesu, braku pliku i błędu zapisu. Nie wolno usuwać tych rekordów ani zamieniać ich na potwierdzone wykonania bez detekcji.

Oficjalny stan tej sesji na dysku nie został jeszcze zamieniony na `PARTIAL`: pozostał `RUNNING`, bez końcowych podsumowań kompletności. Przyszłe odzyskanie powinno uczciwie zachować przerwanie i braki dowodowe. Nie należy wpisywać brakujących flag jako `true` ani wymuszać `COMPLETED`.

### 5.3. Ostatnie niedomknięte MT

```text
mt_invocation_id:
exp-1794671202383-01e39cd5-a00000293

frame_key:
e42ed76129f1a2616362585dd4b1bcaa97cb0ba00b07133ebe1d8c5fb5c92fa1

trace_frame_id: 749
source_sequence: 182725
source_timestamp_nanos: 2522054783904000
source_timestamp_domain: CAMERAX_SENSOR
```

Istnieją `FRAME_OPENED` i `MT_STARTED`. Brak `MT_RETURNED`, `MT_DECODED`, roli końcowej i `FRAME_FINISHED` dla tego wywołania. **Nie jest to wynik „zero detekcji”.**

Pełny obraz źródłowy ostatniej klatki i jej kontekst TV zostały zapisane. SHA pełnego obrazu:

```text
98c205514af117b265a0361002bf64fa35cc6a190091acf500cff3a671d5533b
```

To umożliwia późniejszą kontrolę tożsamości klatki. Nie odtwarza wyniku inferencji, którego nie zapisano.

### 5.4. Dwa braki pełnego obrazu w warstwie TV

Dziennik TV zawiera 454 klucze klatek, 452 obrazy `source_image/WRITTEN`, dwa `source_image/MISSING` oraz 454 `crop_image/WRITTEN`. Dla obrazów oznaczonych `WRITTEN` nie znaleziono niezgodnych SHA ani brakujących wpisanych plików.

Oba braki mają przyczynę `source_frame_memory_or_copy_limit`. Ten kod nie rozstrzyga samodzielnie, czy zabrakło rezerwacji w puli, czy nie udało się wykonać kopii bitmapy. Nie jest dowodem pełnej kolejki ani pełnego dysku.

| `frame_key` | MT na tej klatce | Zachowany obraz sceny z identycznym SourceStamp |
|---|---|---|
| `ce2af0dadeb5988af695cee1377d8f60fa81410734c27aecaac82b4c289976fb` | Brak `MT_STARTED` | `samples/frames/scene-exp-1794671202383-01e39cd5-s00000179.jpg` |
| `38173c9d827d17457bfb3218732218ddac6a675ff50da3b2b52ac99ddf3a55c6` | `exp-1794671202383-01e39cd5-a00000208` | `samples/frames/scene-exp-1794671202383-01e39cd5-s00000196.jpg` |

Obrazy scen mają 1080×1440 px i zgodne zapisane SHA. Ich metadane podają `coordinate_space=mt_source_bitmap_xy`, `origin=pipeline_bitmap_before_roi`. Nie należy pomijać tego opisu domeny tylko dlatego, że wymiary są takie same jak pełnego obrazu kamery.

Znaleziono więc autentyczne kandydatury do wykorzystania zachowanego dowodu z tej samej klatki. Nie wykonano podmiany w dzienniku TV. Przy odzyskiwaniu trzeba zweryfikować dozwolone użycie tych obrazów przez istniejący kontrakt i importer; oryginalny status `MISSING` oraz pochodzenie alternatywnego dowodu muszą pozostać widoczne.

## 6. Pamięć, termika i granice diagnozy

### 6.1. Porównanie z wcześniejszymi rzeczywistymi próbami

`MiB` w tym raporcie oznacza 1 048 576 B. Dane dotyczą pamięci dostępnej w systemie, nie wyłącznie sterty ALPR.

| Próba | Czas zapisu | Dostępna pamięć przy START | Termika przy START → koniec |
|---|---:|---:|---|
| TECHNICAL5 `exp-1794655855091-36adf9c6` | około 101,1 s | około 1111 MiB | TH1 → TH2 |
| TEST_ONLY po korekcie ROI `exp-1794659750094-8d903fc6` | około 24,4 s | około 916 MiB | TH2 → TH2 |
| Badany FINAL | ostatni lifecycle po około 1006,7 s | **255,066 MiB** | **TH3 → TH4** |

Pamięć dostępna w badanym FINAL:

| Wartość | Bajty | MiB |
|---|---:|---:|
| Pierwsza próbka | 267 456 512 | 255,066 |
| Minimum | 97 390 592 | 92,879 |
| Maksimum | 291 860 480 | 278,340 |
| Ostatnia próbka | 110 362 624 | 105,250 |

W 897 próbkach termiki wystąpiło 336 razy TH3 i 561 razy TH4. Pierwsza próbka, z `elapsed_ms=0`, miała już TH3. Bateria miała wtedy 31,4°C, a w ostatniej próbce 32,5°C. Ładowanie było aktywne, bateria miała 100%.

Według [Android PowerManager](https://developer.android.com/reference/android/os/PowerManager#THERMAL_STATUS_CRITICAL) TH3 oznacza silne ograniczanie wydajności (`SEVERE`), a TH4 stan `CRITICAL`. Temperatura baterii nie zastępuje systemowego stanu termicznego. Jest to dodatkowy potwierdzony czynnik ryzyka, a nie dowód, że samo nagrzanie bezpośrednio wywołało decyzję `lmkd`.

### 6.2. Czy potwierdzono wyciek w ALPR?

Nie. Z 261 rekordów telemetrycznych dostępnych jest dziewięć próbek oznaczonych jako pomiar pamięci procesu. Wartości `-1` w pozostałych rekordach nie oznaczają zużycia ujemnego ani zerowego — pomiaru nie wykonano.

| Pomiar w dziewięciu próbkach | Pierwszy | Ostatni | Zakres |
|---|---:|---:|---:|
| `pss_start_kb` | 319 748 | 302 545 | 288 175–326 107 |
| `pss_end_kb` | 305 109 | 312 474 | 290 955–313 180 |
| `native_heap_start_bytes` | 169 844 976 | 170 905 536 | 160 625 888–192 174 376 |
| `native_heap_end_bytes` | 161 157 312 | 179 818 232 | 160 507 400–186 045 120 |

Te rzadkie próbki nie wykazują prostego, stałego narastania PSS do całego RAM telefonu. Nie wykluczają chwilowych pików, presji poza ALPR ani problemów buforów kamery/grafiki. Nie wolno na tej podstawie ogłosić ani „wycieku w APK”, ani „APK bez wpływu na awarię”.

Potwierdzono aktywność Android Studio Device Mirroring. Odczytany log `C:\Users\48572\AppData\Local\Google\AndroidStudio2026.2.1\log\idea.log` zawiera zdarzenie z 11:29:27.991: uruchomienie `com.android.tools.screensharing.Main` z `--max_bit_rate=16000000 --codec=avc`. Jest to dodatkowe obciążenie do wyeliminowania w próbie porównawczej. **Nie potwierdzono, że proces OOM o PID 19802 był właśnie tym agentem ani że mirroring był źródłem presji.** Log IDE był odczytany lokalnie i może podlegać rotacji; nie należy utożsamiać go z zamrożoną kopią katalogu telefonu.

Po awarii na partycji danych było około 2,59 GiB dostępnego miejsca. Nie znaleziono dowodu na zakończenie tej sesji z powodu wyczerpania miejsca na pliki. Tego odczytu po awarii nie należy utożsamiać z ciągłym pomiarem wolnego miejsca przez cały przebieg.

## 7. Co ustalono w istniejącym kodzie Androida

1. **Zapis strumieniowy działał i zachował dane.** `ResearchSessionStore` korzysta z trwałych dzienników, kolejki i finalizacji. Kopia potwierdza obecność wyników zapisanych przed śmiercią procesu. Nie ma uzasadnienia do budowania drugiego magazynu.
2. **Warunek termiczny już istnieje.** `MainActivity.updatePrestartThermalReadiness()` wykorzystuje `ThermalConfig`. Gdy konfiguracja jest wyłączona, gotowość termiczna przechodzi bez blokady. Kontroler wymaga świeżego odczytu i okresu stabilizacji, kiedy warunek jest włączony.
3. **Domyślna konfiguracja warunku jest wyłączona.** `experimentThermalConfig` inicjalizuje się przez `ThermalConfig.disabled()`. Dialog „Warunek temperatury” oferuje m.in. `≤ 32.0°C · TH0`; dostępne w tym dialogu aktywne warianty wymagają TH0 i pięciu sekund stabilizacji. Preferencje zapamiętują ostatnią wybraną temperaturę. Sama obecność tej liczby w preferencjach nie dowodzi aktywnego warunku po odtworzeniu Activity.
4. **Nie rozstrzygnięto dokładnej ścieżki dopuszczenia niezdrowego START.** Badana sesja rozpoczęła się przy TH3. Zabezpieczony snapshot nie pozwala jednoznacznie odtworzyć aktywnej konfiguracji warunku termicznego. Należy rozróżnić wyłączony warunek, odtworzenie domyślnego stanu i ewentualną inną ścieżkę przyjęcia START; nie przypisywać przyczyny operatorowi bez dowodu.
5. **Monitoring RAM nie jest skuteczną bramką gotowości tego przebiegu.** Istnieją pomiary `available_memory_bytes` oraz `DeviceProfile.lowMemoryNow`, ale nie znaleziono blokady START, która w sprawdzonej ścieżce F6 odrzuciłaby tak mały zapas pamięci. Sprawdzenie `getUsableSpace()` dotyczy dysku, a nie RAM.
6. **Utrzymywanie ekranu jest już zaimplementowane.** `FLAG_KEEP_SCREEN_ON` nie zapobiega zabiciu procesu przy krytycznej presji pamięci. [Opis LMKD w AOSP](https://source.android.com/docs/core/perf/lmkd) uwzględnia możliwość zabijania dowolnego procesu przy krytycznej presji. Dodatkowy wake lock lub samo przeniesienie pracy do usługi nie jest dowodem rozwiązania tej awarii.
7. **Nie wykazano przekroczenia limitu liczby trace.** `MetricsCollector` ma limit 5000 rekordów w pamięci; w tym surowym dzienniku jest 261 trace. Istnieją również inne kolekcje i bufory — to nie jest pełny dowód ograniczonej pamięci całej aplikacji.
8. **Nie należy zwiększać buforów jako pierwszej poprawki.** Snapshot F6 podaje kolejkę writerów 256, pojemność prób 64, limit rezerwacji kopii źródła 24 MiB i kopii obrazów 64 MiB. Limity nie są pomiarem faktycznej zajętości RAM całego toru. Dwa komunikaty o limicie kopii wymagają sprawdzenia właścicieli i zwalniania rezerwacji, a nie arbitralnego zwiększania pamięci.
9. **Recovery na kopii już istnieje.** `ResearchSessionStore.recoverCopy()` i `recoverF6Copy()` przewidują autentyczną kopię ze statusem przerwania i pochodzeniem. Nie uruchomiono ich podczas tego audytu. Nie należy dopisywać do historycznej sesji dzisiejszych preferencji.

Miejsca do wykorzystania przy późniejszej poprawce:

- [MainActivity.java](../app/src/main/java/com/example/alpr_v1/MainActivity.java) — przyjęcie START, warunek termiczny, normalny STOP i prezentacja stanu.
- [ThermalConfig.java](../app/src/main/java/com/example/alpr_v1/experiment/ThermalConfig.java) oraz [ThermalMonitor.java](../app/src/main/java/com/example/alpr_v1/experiment/ThermalMonitor.java) — istniejący warunek i pomiary zasobów.
- [ResearchSessionStore.java](../app/src/main/java/com/example/alpr_v1/experiment/ResearchSessionStore.java) — rezerwacje, trwałe dzienniki, finalizacja i recovery na kopii.
- [MetricsCollector.java](../app/src/main/java/com/example/alpr_v1/metrics/MetricsCollector.java) — retencja i pomiary procesu.

## 8. Zalecenia zapobiegawcze — zakres jeszcze niewdrożony

### 8.1. Przygotowanie stanowiska bez zmiany modeli

Po zabezpieczeniu danych operator może wykonać zwykły restart telefonu i pozostawić go do ostygnięcia. Nie należy odinstalowywać ALPR ani czyścić jego danych. Na czas próby należy zamknąć strumień Device Mirroring, nagrywanie ekranu i profiler oraz zbędne aplikacje telefonu. Tekstowy odczyt ADB może pozostać dostępny.

Należy unikać wielogodzinnego pozostawiania aktywnej kamery i mirroringu przed właściwym START. Wyłączenie mirroringu jest ograniczeniem dodatkowego obciążenia; jego skuteczność w zapobieganiu tej awarii trzeba potwierdzić pomiarem, a nie założyć.

Zachować 1440×1080 jako wybraną bazę kamery, istniejące modele, FP32, CPU ×2, MP OFF i właściwe pole F6. Nie używać ponownie 1920×1080 w tym ustawieniu telefonu jako „naprawy pamięci” — ten wariant nie mieścił całego TV w kadrze. Po restarcie sprawdzić rzeczywiste strumienie, zgodność zapisanej geometrii i ponownie ustawić AF; nie wymuszać ponownej kalibracji, jeśli fizyczne źródło jest zgodne.

### 8.2. Minimalna poprawka gotowości START

Zalecana zmiana kodu powinna wykorzystać istniejące kontrolery, a nie tworzyć drugi panel lub nowy mechanizm sesji:

- Jawnie pokazywać i odtwarzać aktywny warunek termiczny F6. Zapisywać jego aktywność i parametry, nie tylko ostatnią liczbę z formularza. Nie dopuszczać cichego powrotu do wyłączonej ochrony po odtworzeniu ekranu.
- Weryfikować RAM i termikę po przygotowaniu modeli oraz kamery, przed utworzeniem i przyjęciem nowej sesji. Odrzucony START nie może tworzyć dodatkowego rekordu „NIE ROZPOCZĘTO”.
- Korzystać z `ActivityManager.MemoryInfo.lowMemory`, `availMem`, systemowego `threshold` oraz zmierzonej rezerwy potrzebnej na przetwarzanie i zapis. [Android dokumentuje znaczenie tych pól](https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo). Nie wpisywać zgadywanego uniwersalnego „bezpiecznego progu A12”.
- Utrwalać wynik sprawdzenia i rzeczywiste parametry warunku w snapshotcie sesji przed inferencją. Dane niedostępne lub nieaktualne powinny mieć jawny stan oczekiwania/blokady.
- Zachować źródłowe identyfikatory i obecną politykę inferencji. Kontrola zasobów przed START nie może przełączać modeli, zmieniać progów, rozdzielczości, NMS ani wybierać korzystniejszych MT.

Udana krótka TECHNICAL5 zaczynała z około 1,08 GiB dostępnej pamięci, a nieudany FINAL z około 255 MiB. To użyteczny punkt odniesienia do próby technicznej, **nie zatwierdzony próg gotowości ani gwarancja przetrwania procesu**.

### 8.3. Ochrona danych i normalna finalizacja

Zachować istniejący zapis strumieniowy. Sprawdzić ograniczenie żywotności kopii bitmap, zwalnianie rezerwacji także przy anulowaniu i błędzie oraz osobno pamięć budowania raportu/ZIP podczas STOP. Błąd `source_frame_memory_or_copy_limit` powinien mieć rozróżnialną diagnostykę rezerwacji i kopiowania, bez ukrywania braków.

Ostatni stan `RUNNING` po śmierci właściciela procesu nie powinien być prezentowany jako aktywny pomiar. Historia ma pokazywać przerwanie i dostępność zabezpieczonych danych, korzystając z istniejącego mechanizmu recovery na kopii. Bez nowego `session_id` i bez podnoszenia odzyskanej sesji do kompletnej.

Przy normalnym STOP istniejący stan operacji powinien wskazywać konkretną sesję, etap, postęp i terminalny wynik. Sukces eksportu wymaga domknięcia strumieni oraz kontroli odczytu/SHA, a nie jedynie powrotu z okna wyboru pliku.

Nie można opierać jedynego zapisu na callbacku zamykania Activity. Android może zakończyć cały proces, zabierając również jego obiekty i zadania. [Dokumentacja cyklu życia Activity](https://developer.android.com/guide/components/activities/activity-lifecycle) opisuje tę zależność. Recovery i trwałe dzienniki są zabezpieczeniem skutków przerwania; nie dowodzą normalnego ukończenia eksperymentu.

Automatyczne awaryjne przerwanie przy krytycznym stanie zasobów, jeśli zostanie później osobno przyjęte, musi oznaczać rzeczywiste przerwanie. Nie może być przedstawiane jako ukończony FINAL176.

## 9. Warunek normalnego zakończenia i weryfikacja przed FINAL

### 9.1. Definicja normalnego zakończenia

Wymagany rezultat to: proces działa do świadomego STOP operatora, przyjmowanie nowych zadań zostaje zatrzymane, zadania w locie są prawidłowo domknięte lub jawnie anulowane według dotychczasowej polityki, dzienniki zostają utrwalone, finalizacja kończy się stanem terminalnym, a `.alprsession` daje się w całości odczytać i przechodzi kontrolę integralności. Sesja pozostaje dostępna w historii po ponownym otwarciu aplikacji.

Normalny STOP i poprawny ZIP są innymi właściwościami niż kompletność wyników wszystkich inferencji. `PARTIAL` może uczciwie pozostać skutkiem `scene_superseded` lub innego rzeczywistego braku dowodowego. Nie należy zmieniać go na `COMPLETED` dla wyglądu. Desktop musi nadal stosować niezmienioną regułę pierwszego rzeczywistego źródłowego MT i zachować N/A tam, gdzie dowodu brakuje.

### 9.2. Ograniczony zestaw przyszłych kontroli

Poniższe kontrole są **zaleceniem do wykonania**, nie wynikiem zaliczonych testów poprawki:

| Kontrola | Przypadki i kryterium |
|---|---|
| Zasoby przy START | Odtworzenie zarejestrowanego TH3/małej pamięci, `lowMemory`, brak i nieaktualność odczytu; jawna blokada przed utworzeniem sesji. Poprawny stan po przygotowaniu zasobów dopuszcza jeden START. |
| STOP i utrata procesu | Normalny STOP z MT/MZ w locie oraz odczyt/odzyskanie autentycznej kopii obecnej sesji. Jedno niedomknięte MT zachowuje brak wyniku; nie powstają fikcyjne FINAL ani zerowe detekcje. Oryginalne SHA pozostają zgodne. |
| Jedna dłuższa próba techniczna | Około 20 min, materiał spoza FINAL, regularne zmiany zdjęć w tempie docelowym, końcowe POSTROLL/T, STOP i pełny eksport. Zapis RAM, termiki, AF i narzutu. |

Próba 20-minutowa powinna obciążać ten sam tor regularnymi zmianami scen, a nie utrzymywać jednego statycznego obrazu przez 20 minut. Osobne przygotowanie techniczne i identyfikatory ekspozycji przygotowuje Desktop; Android nie zmienia jego repozytorium. Nie należy używać FINAL176 do strojenia zabezpieczeń.

Odbiór dłuższej próby wymaga braku zabicia procesu, nowych strat obrazów/dzienników wynikających z zasobów, poprawnego zakończenia writerów i finalizacji, CRC/SHA gotowego archiwum oraz odczytu przez istniejący importer. Przypadki anulowania po zmianie scen należy osobno rozliczyć. Należy potwierdzić zapas zasobów również podczas STOP i budowy eksportu.

Jeżeli te warunki nie przechodzą na A12 przy zachowanej konfiguracji, obowiązuje NO_GO. Nie rozwiązuje się tego usuwaniem nieudanych prób, osłabianiem walidacji lub zmianą modeli/protokołu po obejrzeniu wyników.

### 9.3. Faktycznie wykonane kontrole tego raportu

| Zakres | Wynik |
|---|---|
| Odczyt ADB/logcat bez czyszczenia buforów | Wykonany podczas pierwotnego audytu |
| Identyfikacja zabitego ALPR i `lmkd` | Potwierdzona |
| Kopia 1617 plików i porównanie SHA ze źródłem | PASS |
| Stabilność rozmiarów/mtime i metadanych źródła w czasie kopiowania | PASS |
| Ponowna kontrola 58 plików z `SHA256SUMS.json` przy przygotowaniu raportu | PASS |
| Odczyt 11 JSONL / 7595 rekordów | PASS parsowania; nie jest dowodem domknięcia sesji |
| Liczniki MT/MZ, SourceStamp i wskazane powiązania | Sprawdzone na zabezpieczonej kopii |
| Porównanie zasobów z wcześniejszymi rzeczywistymi próbami | Wykonane |
| Analiza lokalnego kodu warunku START, retencji i recovery | Wykonana; nie jest testem wdrożonej poprawki |
| Recovery tej sesji | NOT_RUN |
| CRC i natywny import finalnego `.alprsession` tej sesji | NOT_VERIFIED — plik nie powstał |
| Nowe regresje JVM / instrumentacja | NOT_RUN; zmiana w tej dostawie dotyczy wyłącznie dokumentu |
| Dłuższa próba po wdrożeniu zabezpieczeń | NOT_VERIFIED |
| Nowa APK / instalacja | Nie wykonywano |
| Pokrycie wszystkich 176 fotografii i ocena względem GT | NOT_VERIFIED / nie wykonywano |

Wcześniejsze krótkie odbiory i testy hostowe pozostają oddzielnymi dowodami opisanymi w poprzednich raportach. Nie dodano ich liczników do wyniku tego audytu.

## 10. Stan końcowy, ograniczenia i punkt wznowienia

| Obszar | Stan na koniec audytu |
|---|---|
| Bezpieczeństwo istniejących danych | ZABEZPIECZONE, potwierdzone SHA |
| Pierwotny proces badanej sesji | Zakończony przez `lmkd` |
| Zapisany stan sesji | `RUNNING`, pozostawiony po przerwaniu; bez `finished_at` |
| Finalny eksport | NIE UTWORZONO |
| Bezpośrednia przyczyna utraty procesu | POTWIERDZONA |
| Źródło presji pamięci | NIEUSTALONE |
| Dostępność większości wcześniejszych wyników | POTWIERDZONA w surowych dziennikach |
| Kompletność ostatniego MT | NIEKOMPLETNE |
| Zmiany zapobiegawcze w APK | NIEWDROŻONE w ramach tego raportu |
| Zgoda na następny FINAL176 | NO_GO do odbioru zasobów, normalnego zakończenia i wspólnego potwierdzenia |

Statusy do przekazania między agentami:

- `RAW_PRESERVATION`: **PASS**.
- `REAL_FILES_RECOVERY`: **NOT_RUN**.
- `SESSION_FINALIZATION_EXPORT`: **NOT_FINALIZED** dla tej sesji.
- `DEVICE_UI_LIFECYCLE`: **PROCESS_DEATH_CONFIRMED**; poprawka odporności nie została odebrana.
- `DEVICE_EXPORT_VISIBILITY`: **NOT_VERIFIED** dla tej sesji — gotowy dokument eksportu nie istnieje.
- `ANDROID_HOST_TESTS`: nowych testów poprawki **nie wykonywano** przy tworzeniu raportu.
- `A12_TV_TECHNICAL`: wcześniejsze krótkie odbiory nie potwierdzają stabilności tego długiego przebiegu.
- `EXPERIMENT_START`: **NO_GO** przed wskazanym odbiorem.

Zalecany punkt wznowienia:

1. Zachować niniejszą kopię surową i jej sumy jako niezmienny materiał dowodowy. Odtwarzanie eksportu prowadzić na osobnej kopii istniejącą ścieżką producenta, bez nadpisania oryginału i bez obietnicy odzyskania niezapisanego wyniku ostatniego MT.
2. Wyjaśnić i ograniczyć dodatkowe obciążenie stanowiska; sprawdzić warunek termiczny i dopuszczenie START przy rzeczywistym zapasie RAM. Minimalne zmiany w kodzie powinny wykorzystać istniejące kontrolery i snapshot sesji.
3. Wykonać celowane regresje i jedną dłuższą próbę techniczną z normalnym STOP/eksportem. Dopiero jej wynik i odbiór Desktop + A12 + TV mogą zmienić NO_GO.

W ramach sporządzania raportu utworzono wyłącznie ten dokument Markdown. Nie zmieniono modeli, rozdzielczości, kalibracji, progów, schedulera, NMS, konsensusu, timerów, formatów eksportu ani GT. Nie edytowano Desktopu, prac dyplomowych ani wcześniejszych dostaw. Nie wykonywano commit/push/reset/clean/stash, instalacji, odinstalowania, czyszczenia danych, zdalnego START/STOP ani recovery na telefonie.

Raport nie zawiera wyników skuteczności FINAL176 i nie podnosi zapisanych braków lub przerwania do kompletnego eksperymentu.
