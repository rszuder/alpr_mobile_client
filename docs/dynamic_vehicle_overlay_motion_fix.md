# DYNAMIC — ciągłość i płynność ramek pojazdów

Zmiana robocza z 2026-09-09 dotyczy prezentacji pojazdów podczas zmiany kadru.

## Ustalone przyczyny

- Log telefonu z 15:44 pokazuje częste przełączenia `LOCAL_KLT` ↔
  `GLOBAL_FRAME_MOTION`. Przejście do ruchu globalnego resetowało lokalny tracker,
  a krótka przerwa w ruchu pozwalała od razu kotwiczyć go ponownie.
- W `SOFT_HOLD` pusta lista śledzonych tablic prowadziła do wyczyszczenia całego
  overlayu, mimo odrębnego śledzenia pojazdów.
- Przy nieruchomej kamerze aktualizacje lokalnego trackera pojazdów mogły być
  pomijane, jeśli istniała śledzona tablica: odświeżano wtedy tylko tablice.
- Awaryjne wyszukiwanie translacji miało zakres 18% szerokości, ale tylko 5,5%
  wysokości. Test przesunięcia pionowego o 28 px w obrazie 180×240 nie przechodził.
- Stabilizacja nieruchomej sceny zatrzymywała zmiany współrzędnych mniejsze niż
  1%, co w DYNAMIC dawało skok po przekroczeniu progu. Animacja dodatkowo
  rozpoczynała hamowanie od nowa przy kolejnych aktualizacjach.

## Zmiany

`VehiclePreviewMotionPolicy` wymaga 600 ms bez nowego dowodu ruchu przed
przełączeniem z kompensacji globalnej do lokalnego trackera. Nowy MP respektuje
tego samego właściciela geometrii. Reset sceny zeruje tę politykę.

W DYNAMIC ramki pojazdów są aktualizowane również bez śledzonej tablicy oraz
podczas lokalnego ruchu pojazdu przy nieruchomej kamerze. `SOFT_HOLD` korzysta
z projekcji pojazdów zamiast interpretować brak tablicy jako pusty overlay.

Przy ruchu potwierdzonym czujnikiem awaryjne wyszukiwanie pionowe obejmuje 18%
wysokości. Walidacja przestrzennego wsparcia, błędu obrazu, zgodności ruchu
w obu kierunkach i maksymalnego przesunięcia pozostaje aktywna.

`DetectionOverlayView` w DYNAMIC nie stosuje progu zamrażającego małe zmiany
ramek. Przesunięcie i rozmiar są interpolowane liniowo przez 16–80 ms,
z czasem dopasowanym do odstępu aktualizacji. Transformacja optycznego zoomu
nadal korzysta z osobnej ścieżki bez dodatkowej animacji.

Nie wydłużono TTL ani nie nadano predykcji statusu pomiaru MP. Bariery sceny,
zmiany epoki wizualnej, rzeczywista utrata tożsamości i kontrola świeżości
pozostają obowiązujące. Bramka minimalnego rozmiaru MT nadal używa surowego MP,
a nie interpolowanej geometrii ekranu.

## Weryfikacja

- 345/345 testów JVM dla UI, trackingu, ciągłości i pipeline'u.
- Nowe testy: szybkie przesunięcie pionowe i powrót, przerwy w ruchu,
  reset i kolejność czasu polityki przełączania.
- Dodany test instrumentacyjny małego przesunięcia i zmiany rozmiaru ramki
  w DYNAMIC, wraz z natychmiastowym czyszczeniem na granicy sceny.
- `assembleDebug` i `assembleDebugAndroidTest` zakończone powodzeniem.
- Test instrumentacyjny i płynność na fizycznym telefonie nie zostały jeszcze
  sprawdzone. Szersze wyszukiwanie może zwiększyć koszt awaryjnego odzyskiwania
  przy szybkim ruchu; wymaga to pomiaru na urządzeniu.
