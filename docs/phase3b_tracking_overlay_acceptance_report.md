# Phase 3B.1 — raport odbioru trackingu i overlayu

Data odbioru: 2026-08-31

Gałąź: `phase3b1-tracking-overlay-hardening`

Baza: `950be979973d1341b3afcbfcfabb80b1e53d4c9d`

## Wynik

Status: **PASS**

Phase 3B.1 zamyka blokery tożsamości i aktualności fizycznych ramek UI.
Scheduler, kolejka Scan oraz result tray nie zostały cofnięte ani połączone z
życiem floating overlayu.

## Zakres implementacji

1. `RELEASE_ACTIVE_TARGET` jest twardą barierą wizualną.
   Historyczna `PlateObservation` nie może odtworzyć starej ramki, a bieżący
   `DetectionOverlayView` usuwa warstwę `PLATE` natychmiast.
2. Alignment opóźnionego MT korzysta z relacji
   `plateTrackId -> PlateObservation.entityId -> diagnostic.trackId`.
   Containment środka tablicy nie jest kryterium własności.
3. Dynamiczna projekcja jest wykonywana per encja. Ruch tablicy celu nie jest
   stosowany globalnie do innych pojazdów.
4. Fresh MP wiąże ramki z `VehicleCandidate.sourceIndex`, one-to-one.
   IoU jest wyłącznie fallbackiem z progiem i marginesem.
5. Fizyczna geometria tablicy ma niezależny TTL 375 ms. Predykcja nie przedłuża
   świeżości bez końca.
6. Dynamiczna prezentacja używa jawnego `DynamicOverlayDisposition`.
   `HARD_RESET` jest granicą `CLEAR`; zwykły ruch zachowuje bounded predicted
   vehicles.
7. Kolejność renderowania jest deterministyczna:
   `VEHICLE -> VEHICLE_ROI -> PLATE`, następnie badges.
8. `OverlayViewportTransform` jest wspólnym mapperem `FIT_CENTER` dla
   `DetectionOverlayView` i `PreviewPlateTracker`.
9. Dodano telemetrię rewizji, źródła i wieku overlayu, entity/plate IDs,
   liczników warstw, bariery release oraz projekcji dynamicznej.

## Walidacja automatyczna

- JVM: **376/376 PASS**.
- Android instrumented: **39/39 PASS**, bez pominięć.
- Android Lint: **PASS**.
- `assembleDebug`: **PASS**.
- `git diff --check`: **PASS**.

Testy obejmują między innymi:

- A visible -> release -> zero plate -> B selected -> fresh MT B;
- defer i lost jako bariery release;
- overlap dwóch bboxów zawierających środek tej samej tablicy;
- trzy encje z niezależnymi wektorami ruchu;
- brak identity bez globalnej translacji;
- crossing i reorder detekcji;
- one-to-one `sourceIndex` oraz niejednoznaczny fallback IoU;
- single KLT miss, wygaśnięcie po TTL i brak odświeżania przez carried prediction;
- bounded vehicle age;
- jawny z-order;
- cztery narożniki, środek, bbox i round-trip mappera dla 16:9, 1:1 i portrait;
- rzeczywisty ekranowy `RectF` w `DetectionOverlayView`.

## Próby live

### S1 — trzy pojazdy, statycznie

MP wykrył trzy encje. Pierwsza próba ujawniła, że pamięć prezentacji była
czyszczona przy `RELEASE`, ale aktualny stan `DetectionOverlayView` mógł nadal
zawierać starą tablicę. Dodano natychmiastowe `clearPlateItems()` w
`applyScanTargetReleaseIfNeeded()`.

Powtórzenie:

```text
A PLATE visible
-> RELEASE A
-> zero floating PLATE
-> B selected
-> nadal zero PLATE
-> fresh MT B
-> B PLATE visible
```

Result tray pozostał widoczny po usunięciu ramki.

### D1 — obrót telefonu 15–20 stopni wokół osi pionowej

Scena z trzema pojazdami. Podczas ruchu i powrotu encje zachowały IDs `1/2/3`.
Nie wystąpił `HARD_RESET`. Po powrocie tablica została ponownie osadzona na
aktualnej pozycji. Syntetyczny test per-entity potwierdził różne delty A/B/C.

### N1 — trzy pojazdy bez czytelnych tablic

MP utrzymywał trzy pojazdy, exact MT zwracał `no_plate`, a warstwa `PLATE`
pozostawała pusta. Pula pojazdów nie była czyszczona przez ticki Preview.
Po ruchu i powrocie widoczne były trzy ramki pojazdów; nie wystąpił
`HARD_RESET`.

### O1 — pojazdy w overlapie

Pojawienie się tablicy encji 13 nie zmieniło geometrii encji 14.

```text
przed PLATE:
entity 13 = [0.041737795, 0.22558928, 0.6187008, 0.55462855]
entity 14 = [0.53125656, 0.26015967, 0.82216036, 0.49211842]

po PLATE:
entity 13 = [0.041737795, 0.22558928, 0.6187008, 0.55462855]
entity 14 = [0.53125656, 0.26015967, 0.82216036, 0.49211842]
```

Dokładny przypadek, w którym oba bboxy zawierają środek tablicy, przeszedł w
teście instrumentacyjnym `EntityAwareOverlayAlignmentInstrumentedTest`.

## Kalibracja CameraX -> Preview

Telefon: Samsung SM-A125F, orientacja portrait.

| Ustawienie | ImageProxy po orientacji | cropRect | Preview | mapped center |
|---|---:|---:|---:|---:|
| 640x480 | 480x640 | pełny | 720x1288 | 360,644 |
| 1280x720 | 720x1280 | pełny | 720x1288 | 360,644 |
| 1920x1080 | 1080x1920 | pełny | 720x1288 | 360,644 |

Po kalibracji przywrócono `Auto · zalecane`.

## Kryteria odbioru

- [x] exact entity niezależne od indeksu ROI;
- [x] stable entity ID przy reorder i crossing;
- [x] preview KLT zmienia wyłącznie PLATE;
- [x] delayed MT nie przesuwa overlapping neighbor;
- [x] focused plate nie przesuwa cudzych pojazdów;
- [x] release nie odtwarza historycznej PLATE;
- [x] stale PLATE ma bounded lifetime;
- [x] vehicle overlays mają bounded prediction;
- [x] brak natychmiastowego migania puli bez PLATE;
- [x] z-order jest jawny i deterministyczny;
- [x] wspólny FIT_CENTER mapper przechodzi corners i round-trip;
- [x] pozycja na urządzeniu przeszła kalibrację 640x480, 1280x720 i high-res;
- [x] dodano telemetrię acceptance.

## Decyzja

Phase 3B.1 można zamknąć. Następny etap: Phase 3C — `BestCropSelector`,
`AcquisitionRecord`, deduplikacja oraz finalne UI wyników Scan.
