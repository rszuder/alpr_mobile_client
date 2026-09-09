# DYNAMIC: tożsamość pojazdu podczas ruchu kamery

Uzupełnienie po kolejnym teście telefonu:
[reset przy nawrocie kamery i kolejność wygaszania tracków](dynamic_rocking_camera_reset_fix.md).

Poprawka robocza z 2026-09-09 dotyczy kolejnych identyfikatorów tego samego,
wciąż widocznego pojazdu oraz migania ramek przy zmianie kadru.

## Przyczyny

`SOFT_HOLD` zwiększał epokę wizualną i anulował trwającą inferencję. Samo
oczekiwanie na uspokojenie ruchu unieważniało więc geometrię. Polityka sceny
nie wystarczała do zachowania ciągłości na podstawie puli pojazdów bez
zachowanego celu tablicy. Zwolnienie celu analizy dodatkowo usuwało jego encję.

Kontrola sceny przed inferencją otrzymywała tylko flagi czujnika ruchu,
pomijając oszacowany ruch obrazu, jego spójność i fazę uspokajania. Log telefonu
pokazał przetwarzanie i oczekiwanie na blokadę pipeline'u trwające około 2–3 s,
przy domyślnym budżecie odzyskiwania sceny wynoszącym 1 s.

## Obecne zachowanie

- `SOFT_HOLD` zachowuje epokę wizualną oraz trwającą inferencję.
- Ruch wyjaśniony kompensacją obrazu i zachowaną pulą pojazdów pozwala
  utrzymać scenę także bez aktywnej tablicy. Spójny ruch istniejącej puli,
  bez nowych encji, jest dodatkowym dowodem ciągłości. Sam ruch kamery
  bez puli pojazdów nie wystarcza.
- Utrata lokalnego śledzenia tablicy przy zachowanej puli nie wymusza
  globalnej zmiany epoki; odzyskanie tablicy obsługuje mechanizm celu.
- Zwolnienie uwagi lub odzyskanie puli pojazdów nie usuwa encji. Usunięcie
  po raporcie odzyskiwania wymaga `ACTIVE_TARGET_LOST`. Nadal obowiązują
  zwykłe zasady wygaszania encji i resetu rzeczywiście zmienionej sceny.
- Podgląd przekazuje pełny dowód ruchu do kontroli przed inferencją przez
  nieblokujący bufor. Próbka wygasa po 750 ms; starsza publikacja nie
  zastępuje nowszej. Twardy reset czyści bufor.
- Budżet odzyskiwania w DYNAMIC wynosi co najmniej wartość profilu, a dla
  wolnego przetwarzania rośnie do `profil + 2 × najdłuższy zmierzony czas`,
  z limitem adaptacji 15 s. Telemetria zawiera `scene_reacquire_timeout_ms`.
  Świeży dowód odzyskania jest rozpatrywany przed upływem tego budżetu.
- Szybki ruch nadal wstrzymuje finalizację. Zachowanie encji nie nadaje
  predykcji statusu świeżego MP i nie omija bramki rozmiaru MT.

Rzeczywisty `SOFT_REACQUIRE` nadal stanowi granicę epoki wizualnej; twarde
cięcia i zmiany strukturalne nadal mogą resetować scenę. Ta poprawka
uzupełnia [zmiany prezentacji ramek](dynamic_vehicle_overlay_motion_fix.md).

## Weryfikacja

456/456 testów JVM dla ciągłości, trackingu, pipeline'u, akwizycji i UI.
Regresje obejmują zachowanie jednej encji przy serii przesunięć kamery,
brak zmiany epoki podczas hold, świeży wynik po pierwotnym terminie,
adaptacyjny limit czasu oraz zakaz finalizacji podczas szybkiego ruchu.
`assembleDebug` i `assembleDebugAndroidTest` zakończyły się powodzeniem.

Testów instrumentacyjnych nie uruchamiano. Potwierdzenie zachowania podczas
fizycznego ruchu telefonu pozostaje do wykonania: ten sam widoczny pojazd
powinien zachować ID podczas przesunięcia i powrotu kamery; rzeczywiste
opuszczenie sceny powinno nadal umożliwiać wygaszenie encji i zmianę sceny.
