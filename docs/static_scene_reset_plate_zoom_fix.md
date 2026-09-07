# STATIC: reset zdjęcia, usuwanie ramek i kolejka AZ

2026-09-07, gałąź `fix/static-scene-reset-and-plate-zoom`, baza `e113a71`.

## Przyczyny i zmiany

1. Bariera prezentacji wyłączała direct-luma, a STATIC_IDLE wyłączał podgląd,
   który miał tę barierę zwolnić. W efekcie ramki/pipeline mogły pozostać przy
   starej scenie. STATIC obserwuje luma niezależnie od bariery i kończy ją przy
   autorytatywnym resecie; nie używa KLT/późnych bitmap Preview jako źródła sceny.
2. Reset miał wspólny cooldown. Weryfikacja na telefonie odtworzyła odrzucenie
   kolejnej potwierdzonej granicy tuż po wcześniejszym resecie. Nowa metoda
   koordynatora deduplikuje po generacji źródła, a nie czasie między zdjęciami.
3. Regiony luma pojawiały się dopiero po baseline, a ich uzbrojenie podmieniało
   referencję ostatnią klatką. Wolna inferencja mogła więc uzbroić obserwację
   już na następnym zdjęciu. Regiony są teraz aktualizowane podczas baseline,
   bez zastępowania oryginalnej referencji.
4. Pusty pomiar nie usuwał poprzedniej listy pojazdów. Ponadto kolejka mogła
   zachowywać niewidocznego aktywnego kandydata. STATIC filtruje predykcje MP,
   przyjmuje pusty pomiar i usuwa brakujące aktywne/oczekujące zadania.
5. AZ był liczony na encję pojazdu i pomijał duże, pewne tablice. Kolejka
   używa teraz tracku tablicy; każda wykryta tablica dostaje jedną próbę,
   także bez przypisania do pojazdu i przed poprawnym MZ/narożnikami.

## Czas reakcji

Zmiana ≥65% obserwowanego regionu lub obrazu: decyzja na pierwszej próbce luma.
Mniejsza istotna zmiana: dwie obserwacje, co najmniej 50 ms (wcześniej trzy
obserwacje i 150 ms). Reset unieważnia stare wyniki oraz lock ROI, czyści UI
i otwiera baseline bez oczekiwania na Preview. Modele pozostają załadowane.
Całkowity czas do nowych detekcji nadal zawiera wykonanie MP/MT/MZ na urządzeniu.

Podczas faktycznej animacji zoomu luma jest zawieszona, by zmiana skali kamery
nie tworzyła fałszywych scen. Po ustabilizowaniu powiększenia działa osobna
referencja luma; powrót do 1× zachowuje porównanie z oryginalną sceną.

## Regresje

- `StaticSceneWatcherTest`: pierwsza próbka silnej zmiany, 50 ms dla mniejszej,
  brak resetu przy jednolitej ekspozycji/szumie, późne uzbrajanie i zoom nie
  mogą ukryć kolejnego zdjęcia; nowe zdjęcie omija cooldown, stary callback nie.
- `StaticSceneCycleTest`: pewna tablica, brak narożników/MZ, kilka tablic tej
  samej encji, tablica bez encji, pusty nowy MP, jeden budżet na scenę.
- `ScanAcquisitionControllerTest`: pusty MP usuwa aktywny i oczekujący pojazd.
- `StaticSceneResetInstrumentedTest`: trzy kolejne zmiany syntetycznej luma
  przez rzeczywisty `MainActivity`, przy STATIC_IDLE i aktywnej barierze;
  sprawdza pustą nakładkę, nową generację, odblokowany baseline i czas obsługi
  poniżej 500 ms bez wykonywania modeli ani czekania na podgląd.

Nie jest to pomiar całkowitego czasu od fizycznej zmiany zdjęcia na monitorze
do wyniku MP/MT/MZ. Test czasowy dotyczy obsługi granicy sceny i wyczyszczenia UI.

## Wyniki wykonania

- Build debug + debugAndroidTest: SUCCESS; 543 testy JVM, 0 błędów/pominiętych.
- Pierwsza pełna regresja Android: 85 prób przeszło, nowy test resetu wykrył
  cooldown opisany wyżej. Po poprawce koordynatora ponowiono 44 powiązane
  testy Androida: **OK (44 tests)**, 26,719 s, w tym nowy test trzech granic.
- APK zainstalowane przez `adb install -r`, z zachowaniem modeli i danych.
- Próba na bieżącym obrazie kamery: AZ rozpoczął się o 23:37:29.740,
  zakończył odczyt o 23:37:32.648 i wrócił do 1× o 23:37:34.269.
  Dla widocznej tablicy pewność zmieniła się z 0,411 do 0,629; UI przeszedł
  do „Analiza zakończona · czekam na zmianę”. To próba jednego aktualnego
  zdjęcia; szybkie kolejne granice sprawdza test luma z rzeczywistym Activity.

Artefakty lokalne w `app/build/reports/gallery-qa/`:
`static-reset-final-regression.log`, `static-fixed-live.log`,
`static-fixed-live.png`, `static-fixed-live.mp4`.
