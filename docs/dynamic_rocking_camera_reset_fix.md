# DYNAMIC: reset przy nawrocie kamery i przedwczesne wygaszenie tracka

Diagnoza z logów telefonu z 2026-09-09 po instalacji aplikacji o 16:54.
Poprzednia poprawka koordynatora sceny nie obejmowała szybkiej ścieżki
`requestAbruptSceneReset` z direct-luma.

## Potwierdzony łańcuch zdarzeń

O 16:55:52.913 obraz miał potwierdzony ruch. O 16:55:53.063 kolejny pomiar
przepływu nie potwierdził ruchu, przy 51,6% zmienionych pikseli. W tym momencie
żyroskop również nie zgłaszał ruchu. Direct-luma aktywował barierę prezentacji
i zażądał `HARD_RESET`. Następny MP utworzył encje 10 i 11 w nowej scenie.
Analogiczna sekwencja wystąpiła o 16:54:43.113 i 16:55:55.689.

`VisualMotionEvidenceDecay` ma dwa rozłączne stany: świeży dowód przez
500 ms, a następnie okres uspokajania do 5 s od ostatniego wiarygodnego
ruchu. Direct-luma sprawdzał wyłącznie bieżący przepływ, żyroskop i
`settling`. W pierwszych 500 ms `settling` jest fałszywe, mimo zachowanego
`motionEstimated`. Powstawała więc luka dokładnie przy krótkim zaniku
przepływu lub nawrocie kamery. Log pokazał nawet sytuację, w której
koordynator potwierdził ciągłość puli pojazdów, po czym zaległe żądanie
direct-luma wymusiło reset.

## Zmiany

Szybka ścieżka respektuje teraz `Snapshot.protectsContinuity()`, czyli
`motionEstimated || settling`. Nie zmieniono progów ani długości okien.
Nie dodano wyobrażonej transformacji dla klatki bez przepływu. Gdy dowód
ruchu wygaśnie, rzeczywiste cięcie nadal może od razu wymusić reset.
Log `ALPR_LUMA_AUDIT` zawiera również `recent_visual_motion`.

Druga poprawka dotyczy kolejności obsługi wolnego MP. Kompensacja kamery
wykonywała pośrednią predykcję, która wygaszała tracki według poprzedniego,
krótszego odstępu MP. Dopiero późniejszy `updateFromMp` dostosowywał TTL
do nowego odstępu, kiedy track już nie istniał. Pozostawało bardziej
restrykcyjne dopasowanie encji uśpionej, mogące nadać nowy identyfikator.
TTL uwzględnia teraz nadchodzący odstęp przed tą predykcją.

Przed projekcją wyniku uwzględniany jest też czas przetwarzania MP. Pierwszy
wynik trwający dłużej niż domyślne 3 s nie wygasza własnych świeżo utworzonych
tracków, zanim trafią do prezentacji. Czas ostatniego pomiaru nadal jest
czasem klatki źródłowej; predykcja nie odnawia pomiaru ani nie omija bramki MT.

## Weryfikacja

461/461 testów JVM dla trackingu, ciągłości, akwizycji, pipeline'u i UI.
Nowe przypadki odtwarzają zanik przepływu po 150 i 497 ms, przejście do
uspokajania, wygaśnięcie ochrony, dłuższy odstęp MP po krótszym oraz pierwszy
MP trwający 4 s. Regresja wygaszenia przy kompensacji kamery nie przechodziła
przed poprawką. Po poprawce zachowane są zarówno ID encji, jak i ID tracka.

Zmian nie potwierdzono jeszcze przy fizycznym kołysaniu telefonu.
