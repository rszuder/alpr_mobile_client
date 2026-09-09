# HANDOFF — DYNAMIC: adaptacyjne ROI dla MT + jedna aktywna tablica na encję

## Doprecyzowania użytkownika z 2026-09-09

- Minimalny próg pojazdu przed MT musi być konfigurowalny w opcjach, wraz
  z progami podtrzymania histerezy.
- W normalnej galerii DYNAMIC powtórzenia tego samego numeru mają być
  obserwacjami jednego wpisu również po zmianie encji. Grupowanie galerii
  nie nadaje ani nie scala tożsamości w trackerze. Nie trzeba przechowywać
  kolejnej bitmapy identycznego odczytu.
- Okno szczegółów odczytu ma pokazywać dane poszczególnych obserwacji:
  ID encji, tracków i sceny, czasy modeli oraz pomiary dostępne w HUD 1 i HUD 2.
  Podgląd tablicy w tym oknie ma być zmniejszony o połowę.
- Zmiany deduplikacji dotyczą normalnej galerii DYNAMIC; surowe próbki
  badawcze zachowują dotychczasową semantykę.

Raport wdrożenia: [dynamiczne MT i obserwacje galerii](../dynamic_mt_gallery_implementation.md).

## Zakres

Repozytorium: `rszuder/alpr_mobile_client`
Punkt odniesienia: `main` po commicie `355ee2d26358ad0bca3c188546b8d784227ad881`

Celem jest zoptymalizowanie potoku **w trybie DYNAMIC** przez:

1. wykorzystanie zasady domenowej „jedna `VehicleEntity` → jedna aktualna tablica”,
2. ograniczenie liczby kandydatów przekazywanych dalej do MZ do **maksymalnie jednego kandydata na encję w jednej próbie MT**,
3. adaptacyjne wyznaczanie ROI dla MT:
   - lokalny obszar wokół znanej tablicy,
   - dolna część ramki pojazdu jako podstawowy obszar poszukiwania,
   - pełniejszy / poszerzony ROI pojazdu jako fallback.

Nie wprowadzać modelu segmentacyjnego. W pracy mechanizm należy opisywać jako **adaptacyjne wyznaczanie ROI / przestrzenną politykę wycinków MT**, nie jako klasyczną segmentację obrazu.

---

## Stan obecny, który należy zachować

### Encja pojazdu

`VehicleEntity` przechowuje pojedyncze:

```text
vehicleTrackId
plateTrackId
vehicleBounds
plateQuad
```

czyli domenowo encja ma jedną aktualną tablicę.

Zmiana technicznego `plateTrackId` nie powinna tworzyć drugiej tablicy tej samej encji. Nowy poprawnie przypisany ślad może zastąpić poprzedni ślad tablicy przy zachowaniu `entityId`.

### Asocjacja tablica → pojazd

`PlateVehicleAssociator` już wykorzystuje informację przestrzenną:

- wymaga, aby środek tablicy dla bezpośredniego ROI znajdował się poniżej ok. 30% wysokości ramki pojazdu,
- premiuje dolną część pojazdu,
- preferowany punkt znajduje się w okolicy 76% wysokości ramki,
- sprawdza konkurencję sąsiednich encji.

Tych reguł nie usuwać. Należy wykorzystać podobną wiedzę **wcześniej**, podczas budowy wejścia MT.

### Poszerzony ROI

Poszerzony wycinek pojazdu może zawierać tablicę sąsiedniego auta. Dlatego nie wolno przyjmować zasady:

```text
jedno ROI wejściowe MT = tablica właściciela ROI
```

Po MT nadal obowiązuje jawna asocjacja detekcji tablicy do rzeczywistej `VehicleEntity`.

---

# 1. Zasada: jedna aktywna tablica na encję

## Cel

W jednej próbie MT dla jednej `VehicleEntity` do rektyfikacji i MZ powinien trafić **maksymalnie jeden kandydat tablicy należący do tej encji**.

MT może nadal zwrócić wiele detekcji. Wszystkie potrzebne detekcje należy najpierw:

1. zdekodować,
2. ocenić geometrycznie,
3. przypisać do encji,
4. dopiero potem wykonać wybór `TOP-1` per `entityId`.

Nie ograniczać surowego wyniku MT do pierwszej detekcji przed asocjacją.

## Powód

Przykład:

```text
MT na ROI encji B
↓
P1 → B
P2 → B
P3 → C
```

Poprawne zachowanie:

```text
dla B:
P1 vs P2 → ranking → TOP-1

dla C:
P3 zachowuje właściciela C
```

Jeżeli bieżąca dyrektywa dotyczy dokładnie encji B, obca detekcja P3 nie może wejść do MZ ani prezentacji celu B.

---

# 2. Ranking TOP-1 dla tej samej encji

Ranking powinien być deterministyczny.

Uwzględnić co najmniej:

```text
1. poprawną asocjację z entityId,
2. validQuad / jakość geometrii,
3. zgodność z poprzednim plateQuad / śladem tablicy, jeśli istnieje,
4. confidence MT,
5. jakość geometryczną PlateQualityScorer,
6. ostrość wycinka,
7. zgodność z typowym pionowym położeniem tablicy w ramce pojazdu.
```

Nie używać tekstu OCR jako źródła tożsamości.

Przy remisie zastosować stabilny tie-breaker, np.:

```text
wyższa jakość
→ wyższe confidence
→ mniejsza odległość od przewidywanego położenia
→ niższy sourceIndex
```

Telemetria powinna zachować informację, że MT zwrócił N detekcji, nawet jeśli do MZ przeszedł tylko jeden kandydat.

---

# 3. Adaptacyjna polityka ROI dla MT

## Poziom A — lokalny ROI znanej tablicy

Jeżeli dla aktywnej encji istnieje świeży i wiarygodny:

```text
plateQuad / plateTrack
+
zachowana ciągłość encji
+
aktualna geometria pojazdu
```

to pierwszą próbę odświeżenia MT można wykonać na małym ROI wokół przewidywanego obszaru tablicy.

ROI musi mieć bezpieczny margines, ponieważ MT ma ponownie lokalizować tablicę i jej narożniki, a nie jedynie potwierdzać poprzedni prostokąt.

Przykład:

```text
previous plateQuad
↓
bounding region
↓
margin X/Y
↓
LOCAL_PLATE_ROI
↓
MT
```

Jeżeli lokalny ROI nie daje prawidłowej detekcji, przejść do poziomu B lub C zależnie od kontekstu.

## Poziom B — dolna część ramki pojazdu

Jeżeli tablica nie jest jeszcze znana, podstawową próbą MT w DYNAMIC powinien być wycinek obejmujący **dolną część ramki pojazdu**, zamiast całego pojazdu.

Nie ustalać wartości finalnej arbitralnie. Wprowadzić konfigurację, np.:

```text
primary_plate_region_top_fraction
```

Rozsądny zakres startowy do testów:

```text
0.30–0.40
```

czyli zachowanie około dolnych 60–70% ramki pojazdu.

W poziomie należy zachować cały zakres pojazdu plus niewielki bezpieczny margines. W pionie margines przy dolnej krawędzi również powinien być zachowany, aby nie odcinać tablic blisko granicy ramki MP.

Schemat:

```text
vehicle bbox
┌────────────────────────┐
│ dach / szyba            │
│                        │
├────────────────────────┤ ← primary_plate_region_top_fraction
│                        │
│ PRIMARY MT ROI         │
│             [tablica]  │
└────────────────────────┘
```

## Poziom C — pełny / poszerzony ROI pojazdu

Jeżeli poziom B zwróci:

```text
NO_DETECTION
lub
DETECTION_INVALID_QUAD
lub
wynik nie może zostać jednoznacznie przypisany do encji
```

i budżet prób na to pozwala:

```text
→ REQUEST_EXPANDED_ENTITY_MT
→ pełniejszy / dotychczasowy poszerzony ROI pojazdu
```

Nie usuwać istniejącego mechanizmu expanded retry.

---

# 4. Kolejność decyzyjna w DYNAMIC

Docelowo:

```text
fresh MP
↓
VehicleEntity
↓
size gate
├── za mały
│   → tylko śledzenie
│   → bez MT
│
└── wystarczający
    ↓
czy istnieje świeży wiarygodny plateQuad?
├── TAK
│   → LOCAL_PLATE_ROI
│
└── NIE
    → PRIMARY_LOWER_VEHICLE_ROI
        ↓
       MT
        ↓
   asocjacja detekcji do VehicleEntity
        ↓
   TOP-1 per entity
        ↓
   valid candidate?
   ├── TAK → rectification → MZ
   └── NIE → EXPANDED_VEHICLE_ROI retry
```

---

# 5. Związek z bramką minimalnego rozmiaru

Adaptacyjne ROI nie zastępuje bramki minimalnego rozmiaru pojazdu.

Kolejność:

```text
MP
↓
minimalny rozmiar pojazdu
↓
dopiero wybór polityki ROI
↓
MT
```

Powiększenie małego wycinka do wejścia MT nie dodaje informacji. Pojazd zbyt mały nadal ma być śledzony jako encja, ale nie powinien przechodzić do MT.

Pierwsze otwarcie bramki rozmiaru powinno bazować na świeżym pomiarze MP, nie na `predicted-only`.

---

# 6. Związek z AutoZoom

Nie łączyć adaptacyjnego ROI z decyzją o AZ.

```text
adaptive MT ROI
→ wybiera skąd pobrać informację dla MT

AutoZoom
→ zmienia fizyczną skalę obrazu z kamery
```

AZ może później wykonać świeże MT na tej samej encji. Po zmianie zoomu nie używać starego `plateQuad` bez ponownego przeliczenia / świeżego zakotwiczenia w aktualnej `cameraTransformGeneration`.

---

# 7. STATIC

Zakres tej zmiany dotyczy **DYNAMIC**.

Nie zmieniać zachowania STATIC ani eksperymentalnej statycznej próbki bazowej bez osobnego wymagania.

W szczególności nie wolno przez tę zmianę przypadkowo:

```text
- zmienić liczby prób STATIC,
- zmienić sposobu pomiaru MT w eksperymencie STATIC,
- zmienić semantyki mt_invocation_id,
- zmienić dowodów wejścia MT zapisywanych do sesji badawczej.
```

---

# 8. Telemetria

Dodać / utrzymać możliwość zapisania:

```text
mt_roi_policy =
    LOCAL_PLATE
    PRIMARY_LOWER_VEHICLE
    EXPANDED_VEHICLE
    FULL_FRAME

mt_roi_left
mt_roi_top
mt_roi_right
mt_roi_bottom

mt_detection_count_raw
mt_detection_count_assigned_to_entity
mt_top1_selected
mt_top1_selection_reason

primary_plate_region_top_fraction
```

Jeżeli kilka detekcji należało do jednej encji:

```text
raw_assigned_count = N
mz_candidates_executed = 1
```

Telemetria ma umożliwić późniejszą odpowiedź:

- czy dolny ROI zwiększył skuteczność MT,
- czy zmniejszył liczbę expanded retry,
- ile detekcji odrzucono przez TOP-1,
- ile MZ uniknięto,
- czy lokalny ROI znanej tablicy skrócił czas ponownej lokalizacji.

---

# 9. Badania / tryb research

Nie zmieniać semantyki pomiarowej:

```text
jedno faktyczne wywołanie backendu MT
=
jedno mt_invocation_id
```

Jeżeli retry używa innego ROI, jest to **kolejne faktyczne wywołanie MT**, z własnym `mt_invocation_id`.

Każde wywołanie musi nadal zapisywać rzeczywisty `mt_input_evidence_entry`.

TOP-1 po MT nie może zafałszować surowego `mt_detection_count`. Należy zachować liczbę wszystkich surowych detekcji zwróconych przez daną inferencję.

W zamrożonym przebiegu badawczym polityka ROI i jej parametry muszą być częścią konfiguracji i nie mogą zmieniać się w trakcie sesji.

---

# 10. Przypadki brzegowe

### Sąsiedni pojazd w expanded ROI

Jeżeli MT znajdzie tablicę sąsiada:

```text
→ przypisać do rzeczywistej encji
→ nie traktować jako tablicy właściciela ROI
```

### Dwie detekcje tej samej tablicy

```text
→ obie przypisane do tej samej encji
→ TOP-1
→ jeden MZ
```

### Dwie rzeczywiste tablice widoczne przy pojeździe

Dla obecnego zakresu aplikacji obowiązuje model:

```text
jedna VehicleEntity → jedna aktywna tablica
```

Nie rozszerzać teraz domeny do wielu tablic na pojazd. Niejednoznaczność powinna być rozstrzygana przez ranking albo odroczenie, nie przez tworzenie drugiej aktywnej tablicy encji.

### Utrata ciągłości

Stary `plateQuad` nie może otworzyć lokalnego ROI po:

```text
HARD_RESETTING
sceneGeneration change
niezgodnej cameraTransformGeneration
potwierdzonej utracie encji
```

### Predicted-only

`predicted-only` może pomagać utrzymać lokalny obszar krótkotrwale po wcześniejszym poprawnym zakotwiczeniu, ale nie powinien samodzielnie ustanawiać pierwszego położenia tablicy.

---

# 11. Testy

Minimum:

1. jedna encja, jedna detekcja MT → zachowanie bez zmian,
2. jedna encja, trzy detekcje MT → tylko TOP-1 trafia do MZ,
3. dwie detekcje tej samej encji → deterministyczny wybór tej samej TOP-1,
4. expanded ROI zawiera tablicę sąsiada → nie zostaje przypisana właścicielowi ROI,
5. znany świeży `plateQuad` → używany `LOCAL_PLATE_ROI`,
6. brak znanej tablicy → używany `PRIMARY_LOWER_VEHICLE_ROI`,
7. primary ROI daje NO_DETECTION → expanded retry,
8. primary ROI daje invalid quad → expanded retry,
9. pojazd poniżej size gate → MT nie zostaje wywołany,
10. predicted-only nie otwiera size gate,
11. zmiana `cameraTransformGeneration` unieważnia stary lokalny ROI,
12. HARD_RESET unieważnia lokalny ROI,
13. DYNAMIC multi-vehicle → wynik obcej encji nie trafia do MZ bieżącej sesji,
14. STATIC pozostaje bez zmian,
15. `mt_detection_count` nadal opisuje surowy wynik jednej inferencji,
16. każde faktyczne retry MT otrzymuje nowe `mt_invocation_id`,
17. evidence zapisuje faktyczne wejście użyte przez MT dla każdego rodzaju ROI.

---

# 12. Definition of Done

Zmiana jest gotowa, gdy:

- DYNAMIC używa adaptacyjnej polityki ROI,
- mały pojazd nadal nie uruchamia MT,
- znana tablica może być odświeżana przez lokalny ROI,
- nieznana tablica jest najpierw szukana w dolnej części pojazdu,
- brak poprawnego wyniku uruchamia dotychczasowy szerszy fallback,
- jedna encja przekazuje maksymalnie jednego kandydata do MZ na próbę MT,
- obce tablice nie są przypisywane właścicielowi ROI,
- surowa telemetria MT pozostaje poprawna,
- STATIC i semantyka sesji badawczej nie ulegają regresji,
- testy jednostkowe i istniejący zestaw regresyjny przechodzą.

Po wdrożeniu agent powinien przygotować krótki raport:

```text
- zmienione pliki,
- finalna polityka ROI i jej parametry,
- reguła TOP-1,
- sposób zachowania telemetrii,
- wyniki testów,
- ewentualne ryzyka / punkty do strojenia eksperymentalnego.
```
