# Android → Desktop ALPR: źródłowa klatka MT i ROI, kontrakt P1 (2026-10-18)

**Status:** kod przygotowany, wymaga `--check`, `--apply`, kompilacji i testów na aktualnym Androidzie oraz rzeczywistym urządzeniu. **Nie** jest to zatwierdzony wynik F4 ani `final_test`.

## 1. Źródło obrazu

`MobileAlprEngine.detectPlates()` przekazuje do MT `Bitmap frame` reprezentujący **klatkę w układzie wejściowym potoku**. Prostokąt `boundedRegion` (`roi_left/top/right/bottom`) jest w układzie pikselowym **tej samej** bitmapy. To nie musi być surowy kadr matrycy kamery; nie należy tego tak nazywać w raporcie.

Patch `ANDROID_ROI_FRAME_P1_20261018` kopiuje tę bitmapę raz na rzeczywiste wywołanie MT do osobnego bufora i zapisuje do `samples/frames/<mt_invocation_id>.jpg`. Nie zmienia składu wejścia modelu, predykcji, metody doboru ROI ani progów detekcji. Obraz wejścia MT (`samples/evidence/`) oraz cropy MZ (`samples/crops/`) pozostają odrębne. Jeden obraz source frame jest współdzielony (po ścieżce) przez wszystkie detekcje tego wywołania; dla kolejnych wywołań na tej samej klatce mogą powstać oddzielne JPEG.

### Kontrakt próby (dopisana końcówka CSV, zachowany prefiks v2)

- `source_frame_expected`: `true` gdy aktualny potok żąda zrzutu; starsze wywołania bez zrzutu mają `false`.
- `source_frame_entry`: ścieżka względna w ZIP (`samples/frames/<id>.jpg`), nie dowód sama w sobie.
- `source_frame_status`: `NOT_REQUESTED` / `CAPTURED` (przejściowe) / `WRITTEN` / `MISSING`.
- `source_frame_missing_evidence_reason`: przyczyna braku, nie zastępuje metryk MT.
- `source_frame_width_px`, `source_frame_height_px`: faktyczne wymiary kopiowanego `Bitmap frame`.
- `source_frame_coordinate_space`: `mt_source_bitmap_xy`.
- `source_frame_origin`: `pipeline_bitmap_before_roi`.
- `source_frame_capture_elapsed_nanos`: mierzy narzut tworzenia kopii obrazu poza odcinkiem `durations[1]` MT.
- Identyfikatory `mt_invocation_id`, `source_sequence`, `source_timestamp_nanos`, `source_timestamp_domain`, generacje ciągłości i pola ROI są już obecne.

`manifest.json` nadal zawiera SHA-256 wszystkich wpisów archiwum; archiwum z brakującym obrazem nie jest deklarowane jako kompletne. `source_frame_integrity_loss_count` liczy *unikalne wywołania MT*, a nie detekcje z tego samego wywołania. Nie ma automatycznego imputowania brakującego obrazu ani pełnego ROI z samego obrazu 512×512.

## 2. Metodologia

- GT pełnej klatki operator może oznaczyć tylko wtedy, gdy obraz pełnej klatki jest rzeczywiście dostępny i ma sprawdzoną zgodność współrzędnych z ROI. Samo pole `source_frame_entry` nie wystarczy: plik musi istnieć w ZIP, jego SHA musi się zgadzać, a wymiary i granice ROI muszą być prawidłowe.
- Gdy MT otrzymał pełną klatkę, pominiętą rzeczywistą tablicę można rozpatrywać jako FN **po** potwierdzeniu niezależnego GT, polityki kwalifikacji i progu IoU.
- Dla częściowego ROI tablice poza ROI nie są FN wywołania detektora MT. Pokrycie ROI i błędy selekcji celu liczymy w oddzielnym wymiarze.
- Powiązania operatora i obrys poprawiony po obejrzeniu predykcji MT są **diagnostyczne**, nie niezależnym GT.
- `MZ NOT_RUN` pozostaje `NOT_RUN`, nie zerowym CER.

## 3. Wydajność i ograniczenia

Źródłowy kadr jest kopiowany synchronicznie z bitmapy przed wejściem MT, a kodowanie JPEG odbywa się przez istniejący pojedynczy writer sesji. Maksymalnie 24 MiB nowo przetrzymywanych bitmap źródłowych naraz, niezależnie od dotychczasowego limitu 64 MiB na obrazy MT/cropy. Przy przekroczeniu budżetu zapis jest jawnie niekompletny, a ewaluacja pełnoklatkowa musi pozostać `NOT_EVALUABLE`. Istnieje dodatkowy koszt kopiowania oraz kodowania, który może wpłynąć na E2E i obciążenie urządzenia — nowych czasów nie wolno bezpośrednio utożsamiać z wcześniejszymi sesjami bez kontroli narzutu.

W P1 **nie wdrażamy** redukcji liczby obrazów dla powtórzonych `source_sequence`, limitów danych na dysku ani nowego UI. To kolejne elementy do oceny po teście A12 i audycie wielkości sesji.

## 4. Bramy odbioru

1. `--check` zgodny z komitem i sumami SHA; kopia bezpieczeństwa utworzona przez instalator.
2. Kompilacja `assembleDebug` oraz testy jednostkowe/wskazane `androidTest`, w szczególności `ResearchSourceFrameArchiveInstrumentedTest`.
3. Jedno wywołanie MT z 2 detekcjami → dokładnie jedna wspólna ścieżka JPEG full-frame w wierszach prób. Obraz 160×100 zachowuje faktyczne wymiary i układ współrzędnych; MT input 64×32 pozostaje osobny.
4. Usunięcie dowodu przed odbudową ZIP → `PARTIAL`, `source_frame_integrity_loss_count=1`, brak formalnego pełnoklatkowego GT.
5. Błąd kodowania full-frame → MT input i wyniki modelu nadal obecne, próba archiwalna niekompletna.
6. Fizyczny Galaxy A12: sprawdzić JPEG, SHA wpisów ZIP, `source_sequence`, poprawność ROI overlay względem obrazu i koszty RAM / FPS / E2E.
7. Z4: wizualizacja wielu ROI/wiele GT w całej klatce z istniejącym edytorem Z2, nie sztuczny drugi edytor. Raport formalny TP/FP/FN nadal fail-closed aż do oddzielnego testu naukowej poprawności.

## 5. Kontrakty nienaruszalne

Nie modyfikujemy modeli, dekoderów, thresholdów, treningów, źródłowych archiwów `.alprsession`, dotychczasowych GT/sidecarów, wyników pomiarowych ani pracy TeX. Zmiany dotyczą tylko przyszłych sesji po instalacji nowego APK, a wprowadzenie dodatkowej akwizycji dowodu oznaczamy jako zmianę protokołu pomiarowego.
