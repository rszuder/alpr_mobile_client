# Ochrona geometrii tablicy po auto zoomie

W scenie Renault `RJA5001G` MT przy 1× obejmował całą tablicę. Po AZ 1,8×
wejście modelu nadal zawierało cały numer, ale tensor MT zwracał bbox i narożniki
ucinające `G`. Dekoder oraz mapowanie ROI potwierdzono niezależnie dla sześciu
detekcji; maksymalna różnica wynosiła 0,000072 px. Materiał pomiarowy znajduje się
lokalnie w `app/build/reports/plate-geometry-return-audit/`.

`AutoZoomGeometryGuard` zapamiętuje poprawne czworokąty MT przed zoomem i zamraża
je na czas powiększenia. Przelicza narożniki przez rzeczywisty względny zoom
kamery, bez przycinania do krawędzi obrazu. W przypadku jednostronnego ucięcia
używa tego obrysu do rektyfikacji bieżącej klatki oraz obu publikacji overlayu
(MT i wynik OCR). Nie używa poprzedniego obrazu ani tekstu jako nowej predykcji.

Warunki użycia odniesienia:

- ta sama generacja sceny i epoka wizualna, nowa generacja transformacji kamery;
- ten sam właściciel tablicy; przy nieznanym właścicielu wymagany ten sam track;
- jednoznaczne dopasowanie położenia i orientacji, ważność do 30 sekund;
- nowy obrys węższy o ponad 6%, cofnięcie jednej strony ponad 6,5% szerokości,
  druga strona zgodna w granicach 3,5% szerokości;
- aktywny cykl AZ, bez szybkiego ruchu kamery ani trwającej transformacji.

Równomierne zmniejszenie, przesunięcie celu, drobny jitter i poprawna świeża
geometria nie uruchamiają korekty. Wiele pasujących tablic, zmiana sceny,
utrata celu, powrót zoomu i narożniki wypadające poza sensor wyłączają ochronę.
Zwykłe zakończenie bazowego odczytu (`scan_read_captured`, `scan_ready_to_finalize`)
zachowuje odniesienie potrzebne przy następującym po nim AZ.

Surowa detekcja pozostaje obiektem używanym do identyfikacji próby badawczej,
trackingu i przypisania pojazdu. `PlateGeometry` zachowuje dotychczasowe surowe
pola; w razie korekty dodaje `effective_crop_geometry`,
`crop_geometry_source=pre_zoom_reference` i `crop_reference_source_sequence`.
UI używa `forRecognition()`, aby publikacja odczytu nie przywracała węższego
surowego bbox. Pewność OCR pozostaje niezależna od decyzji o geometrii.

Testy obejmują współrzędne z Renault, zmianę tracku, oba kierunki ucięcia,
kolejne gorsze detekcje, inne pojazdy, zmianę sceny, ruch, wygaśnięcie,
niejednoznaczność oraz przekazanie celu ze skanowania do AZ. Test Androida
sprawdza rzeczywistą rektyfikację: kolorowy znak poza surowym quad trafia do
poprawionego cropu, a raport nadal zawiera surową geometrię MT.

Weryfikacja na telefonie i wyniki testów: `app/build/reports/az-geometry-guard/`.
Instalacja przez `adb install -r`, po kopii danych. Bez odinstalowywania aplikacji.

Końcowy build: 558 testów JVM i 59 testów Androida bez błędów. W próbie Static
2026-09-08 o 15:22:47 guard zachował obrys referencyjnej klatki 53: surowy bbox
kończył się na x=742,8, a pełny obrys na x=815,2. Odczyt `RJA5001G` wzrósł
z 35% do 64%; ramka obejmowała końcowe `G` podczas AZ i po powrocie do 1×.
Wystąpiło jedno żądanie zoomu i powrót do STATIC_IDLE. Nagranie i zrzuty:
`app/build/reports/az-geometry-guard/run3/`; hash APK: `verification.json`.
Cały cykl na kamerze sprawdzono w Static; wspólna polityka jest używana również
w Dynamic, ale osobnego cyklu kamerowego Dynamic w tej weryfikacji nie wykonano.
