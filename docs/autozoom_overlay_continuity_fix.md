# AZ: ramka detekcji, najlepszy badge i ciągłość geometrii

2026-09-08. Rozszerzenie poprawki opisanej w `static_autozoom_target_visibility_fix.md`.

## Zachowanie

- Podczas AZ geometria tablicy pozostaje widoczna również po przeniesieniu
  odczytu do badge pojazdu. Zapamiętana ramka używa wyraźnego obrysu.
- Start statycznego AZ odtwarza bbox zachowany w fazie bazowej, także gdy
  wcześniejsza prezentacja zdążyła usunąć tę tablicę z listy UI. Ten bbox
  służy również do ograniczenia zoomu przy krawędzi kadru.
- Świeża geometria MT jest prezentowana niezależnie od powodzenia MZ.
- Badge zachowuje odczyt o większej pewności. Chwilowo nieprzypisany odczyt
  zachowuje tekst i pewność; gdy pipeline przypisze jego track do pojazdu,
  wynik może zaktualizować badge nawet przy pustej kolejnej próbie MZ.
  Przypisanie pochodzi z pipeline, nie jest zgadywane przez UI.
- Statyczny cykl deduplikuje nakładające się detekcje tej samej tablicy
  (IoU >= 0,5, zgodny pojazd albo wcześniejszy brak przypisania). Zmiana tracku
  nie daje drugiego AZ. Dwie różne tablice na jednym pojeździe nadal mają
  osobne próby, a nowa scena dostaje nowy budżet.
- Korekty geometrii PLATE, VEHICLE i ROI są interpolowane w obu trybach.
  Zgodny numer tracku nie powoduje już natychmiastowego przeskoku do nowego bbox.
  Retargetowanie zaczyna się od pozycji aktualnie narysowanej. Nowa detekcja
  pojawia się od razu; różne zidentyfikowane pojazdy nie są morphowane między sobą.
- Pojawienie się lub utrata narożników MT płynnie przechodzi między bboxem
  a quadem w warstwie rysowania, bez zmiany surowej geometrii detekcji.
- Aktualizacje preview używają 80 ms, pomiary 120 ms. Postęp optycznego zoomu
  jest rysowany bez dodatkowej interpolacji. Zmiana sceny i STOP nadal jawnie
  usuwają nieaktualną geometrię i stan odczytów.

## Implementacja

`StaticSceneCycle` zachowuje geometrię kandydatów i aktywnej próby, łącząc
powtarzające się tracki. `AlprPipeline.staticRefinementBounds` udostępnia bbox
aktywnego celu. `MainActivity` odtwarza ramkę przed zoomem, prezentuje MT bez
oczekiwania na odczyt i zaczyna transformacje od widocznej geometrii.

`DetectionOverlayView` rozdziela geometrię tablicy od transferu odczytu,
zachowuje nieprzypisany odczyt podczas cyklu AZ i animuje korekty. Dowody wejścia,
predykcje modeli i tożsamość próbek badawczych nie są zmieniane przez tę pamięć UI.

## Weryfikacja

Końcowy build: 546 testów JVM bez błędów; 65 testów UI Androida bez błędów.
Jeden test opt-in automatycznej sesji badawczej był pominięty w pakiecie UI.
Podsumowanie z hashem APK: `app/build/reports/az-overlay-hardening/verification.json`.

- Testy JVM: deduplikacja przy zmianie tracku/przypisania, różne tablice,
  nowy budżet sceny, granice zoomu i polityka interpolacji.
- Android: ramka podczas transferu do badge, mocniejszy/słabszy wynik,
  odczyt przed przypisaniem pojazdu, zachowanie przez zmianę zoomu,
  pośrednie pozycje animacji i retargetowanie w obu trybach, powrót do
  bazowej geometrii oraz reset sceny.
- Test żądania AZ w Activity obejmuje pustą listę bieżących ramek w Static:
  odtwarza ramkę i ogranicza zoom na podstawie detekcji zachowanej w cyklu.
- Pełne wyniki wykonanych testów i nagranie urządzenia są lokalnie w
  `app/build/reports/az-overlay-hardening/`.

Podczas próby na Fiatcie odczyt zmienił się z `J3` / 25% na `PJA59AF` / 45%.
Silniejszy odczyt pojawił się najpierw bez przypisania pojazdu i został
poprawnie przeniesiony do badge po późniejszym przypisaniu tracku. W tym cyklu
wystąpił jeden request AZ, potem powrót do 1× i STATIC_IDLE. To weryfikacja
prezentacji i sterowania, nie potwierdzenie poprawności transkrypcji względem GT.

W kolejnym przebiegu po odtworzeniu ramki bazowej obrys był widoczny już
na początku AZ, a badge zmienił się z `L59AF` / 82% na `RJA59AF` / 84%.
Log: request 14:12:23.671, powrót 14:12:34.882, STATIC_IDLE 14:12:36.758.
W całym tym cyklu również wystąpiło dokładnie jedno żądanie zoomu.

Instalacje wykonano przez `adb install -r`, po kopii danych aplikacji.
Nie używano zadania Gradle odinstalowującego aplikację po testach.
