# Przywracanie ramki tablicy po zoom out

Poprzednio `presentZoomedMtStage` i `freezeZoomResultForReturn` nadpisywały
`autoZoomBaseMemoryOverlayItems` odwrotnie przeskalowaną geometrią z zoomu.
Jeśli MT zwęził tablicę, UI zachowywało zwężenie także po powrocie do 1×.
Ochrona cropu w silniku nie wystarczała: jest celowo warunkowa i nie obejmuje
każdej regresji geometrii MT.

`ZoomPlateReturnGeometry` zachowuje niezależną, głęboką kopię obrysu celu sprzed
AZ. Wyniki MT podczas powiększenia nie zmieniają tej kopii. Przy zoom out
geometria przechodzi płynnie od obrysu widocznego w zoomie do pierwotnego
obrysu bazowego. Interpolacja zależy od rzeczywistego zoomu kamery i zawsze
liczy się względem niezmiennych danych początkowych. Przy 1× granice bbox
i narożniki wracają dokładnie do zapisanego odniesienia.

Tekst i track po rozpoznaniu pozostają aktualne. Przy zmianie tracku wymagany
jest ten sam znany właściciel tablicy. Inny pojazd, zmiana sceny/epoki wizualnej,
niejednoznaczna ramka i istotne przesunięcie celu wyłączają przywrócenie.
Brak warstwy można uzupełnić tylko dla nadal wybranego pierwotnego tracku.
Przy zmianie sceny i czyszczeniu pamięci AZ usuwana jest również kopia obrysu.

Mechanizm działa przy każdym żądaniu powrotu, także timeout i wyłączeniu AZ,
niezależnie od uzyskania świeżego OCR. Nie zmienia surowych detekcji MT, cropów
badawczych ani tożsamości próbek. Ramka po powrocie jest pamięcią geometrii
sprzed zoomu, a nie deklaracją wykonania nowej detekcji przy 1×.

Testy Androida obejmują końcową geometrię, płynne pozycje pośrednie, zachowanie
lepszego odczytu, zmianę tracku, głęboką kopię punktów, bbox/quad, brak warstwy,
zmianę sceny, właściciela i położenia. Test `AutoZoomFramingInstrumentedTest`
przechodzi przez żądanie zoomu w Activity, symuluje zwężoną detekcję, zamraża
wynik i sprawdza powrót do pełnej szerokości w Static i Dynamic, dla tablicy
centralnej i peryferyjnej.

Weryfikacja: 567 testów JVM oraz 44 testy Androida bez błędów. Materiały z próby
telefonu, wyniki i hash APK: `app/build/reports/az-return-geometry/`.
Instalacja przez `adb install -r`, po kopii danych, bez odinstalowania aplikacji.

Na telefonie potwierdzono powrót pełnej ramki na frontalnej scenie Citroëna
`RJA12455`: log `return_to_pre_zoom_outline` o 16:14:05.907 po timeout,
powrót do tej samej sceny o 16:14:07.420, a następnie STATIC_IDLE. Zrzut
`after-return-citroen.png` pokazuje pełną ramkę. Kolejny powrót po poprawie
odczytu również uruchomił przywrócenie obrysu (16:15:27.196).
Te późniejsze cykle potwierdzają log i zrzut; zapisane wcześniej nagranie
automatycznego startu nie zawierało AZ i nie jest dowodem działania powrotu.
