# Phase 3A v2 — raport końcowy ciągłości sceny zorientowanej na cel

## Zakres i punkt odniesienia

- specyfikacja: `ALPR_phase3A_target_centric_scene_continuity_v2.md`;
- branch: `phase3a-target-centric-continuity`;
- baza: `95b7767a7a23228de42adb56fb5b53f3228f00b4`;
- urządzenie odbiorowe: Samsung SM-A125F, Android API 31;
- tryby: `strict_scene_boundary` i `dynamic_continuity`;
- profil progów: `initial_v2`.

Implementacja rozdziela `RAW_VISUAL_CHANGE`, `MOTION_EXPLAINED_CHANGE` i
`CONTINUITY_BREAK`. W trybie dynamicznym zmiana pikseli jest wejściem do oceny,
a nie bezpośrednim poleceniem resetu. Tryb strict zachowuje szybkie rozdzielanie
scen statycznych.

## Zrealizowane elementy

1. Dodano niemutowalne kontrakty evidence, ocenę ciągłości targetu i puli pojazdów,
   wyjaśnienie ruchu oraz centralny `SceneTransitionCoordinator`.
2. Rozdzielono `sceneGeneration`, `visualEpoch` i
   `cameraTransformGeneration`; wyniki są bramkowane względem generacji.
3. Zaimplementowano `NONE`, `SOFT_HOLD`, `SOFT_REACQUIRE`,
   `RELEASE_ACTIVE_TARGET` i `HARD_RESET` wraz z deduplikacją decyzji.
4. Dynamiczny ruch kamery zachowuje encje i sesję celu, natomiast nieruchome,
   niewyjaśnione cięcie uruchamia kontrolowaną reakwizycję.
5. KLT działa bezpośrednio na luma Preview; lokalna geometria, inliery, support i
   wygląd celu wchodzą do evidence.
6. Deskryptor wyglądu LOCK jest chroniony przed aktualizacją przez sprzeczne
   obserwacje. Okresowe MT nie może zastępować stałej kotwicy sceny dla tego
   samego logicznego LOCK.
7. Dowody lokalne zachowują znacznik czasu klatki źródłowej. Opóźniony wynik MT
   sprzed cięcia nie może wyjaśnić późniejszego `RAW_VISUAL_CHANGE`.
8. `SOFT_REACQUIRE` degraduje snapshot pipeline'u, resetuje lekki tracker Preview,
   usuwa starą ramkę i wynik z UI oraz wymusza świeże MP/MT.
9. `FinalizationGate` blokuje finalizację podczas hold/reacquire, przy nieaktualnej
   generacji i bez świeżo zwalidowanej geometrii.
10. Ustawienia i eksport telemetrii zapisują tryb, profil, klasyfikację, score,
    akcję, stan, generacje, zawieszenie finalizacji i zdarzenia recovery.

## Regresja znaleziona podczas odbioru urządzeniowego

Kontrolowane cięcie pojazd → samolot początkowo utrzymywało stary LOCK i nakładało
ramkę tablicy na podwozie samolotu mimo wysokiej jakości KLT. Diagnostyka wykazała
trzy niezależne przyczyny:

- historyczna dostępność deskryptora była traktowana jak podobieństwo `1.0`;
- okresowe MT ponownie ustawiało kotwicę globalnej sceny;
- opóźniony wynik MT sprzed cięcia mógł wyjaśnić późniejszą zmianę, a UI nie
  wykonywało flagi `resetFocusedTracker` przy `SOFT_REACQUIRE`.

Wszystkie trzy ścieżki zostały poprawione i zabezpieczone testami regresyjnymi.
Końcowa próba urządzeniowa dała:

```text
raw=true, score=0.164, fraction=0.370
classification=UNEXPLAINED_CHANGE
action=SOFT_REACQUIRE
```

Po decyzji ekran przeszedł do `R0 · SZUKAM`; stara ramka i odczyt zniknęły.

## Weryfikacja automatyczna

| Kontrola | Wynik |
|---|---|
| `testDebugUnitTest` | PASS — 248/248 |
| `lintDebug` | PASS |
| `assembleDebug` | PASS |
| `assembleDebugAndroidTest` | PASS |
| `connectedDebugAndroidTest` | PASS — 16/16, SM-A125F |
| `git diff --check` | PASS |

Testy obejmują kontrakty, scoring, klasyfikacje, strict/dynamic, hold,
reacquire, hard reset po deadline, scoped release, generacje, odrzucanie starych
wyników, finalization gate, brak nadpisania kotwicy przez okresowe MT oraz cięcie
z pozornie idealnym, lecz czasowo nieaktualnym dowodem celu.

## Weryfikacja manualna

| Scenariusz | Wynik |
|---|---|
| nieruchomy pojazd, akwizycja i LOCK | PASS |
| wolne prowadzenie za celem | PASS — zachowany LOCK, bez resetu |
| szybki pan | PASS — `RECOVERY`, powrót do LOCK bez hard resetu |
| prawdziwe cięcie pojazd → samolot | PASS po poprawkach — `SOFT_REACQUIRE`, brak starego overlay |
| ponowna akwizycja po zmianie | PASS |
| crash/ANR w wykonanych próbach | brak |

Nagrania i zrzuty diagnostyczne z odbioru znajdują się lokalnie w `app/build/`
i nie są częścią commita źródłowego.

## Telemetria i ograniczenia pomiaru

Liczniki oraz czasy wymagane przez specyfikację są zaimplementowane i eksportowane.
W tej sesji nie wykonano kontrolowanego 10-minutowego benchmarku, dlatego nie są
raportowane zmyślone wartości `per_minute`, p50/p95 ani długookresowe wskaźniki
false-reset/missed-cut. Końcowa kontrolowana próba cięcia miała `missed_cut=0/1`,
a wykonane próby ruchu nie spowodowały fałszywego hard resetu.

Pełna tabela statystyczna dla 13 scenariuszy terenowych powinna zostać zebrana jako
osobna kampania benchmarkowa na ustalonej trasie i materiale wejściowym. Nie blokuje
to odbioru implementacji i testów regresyjnych Phase 3A, ale pozostaje wymaganiem
przed publikacją wyników ilościowych pracy.

## Ocena kryteriów odbioru

Kryteria funkcjonalne i metodologiczne Phase 3A są zaimplementowane. Potwierdzono
zachowanie celu podczas ruchu, brak automatycznego resetu od samej różnicy pikseli,
soft recovery, ochronę przed starymi wynikami, blokadę finalizacji, oddzielność
strict/dynamic, brak traktowania zoomu jako cięcia oraz prawidłową reakcję na
rzeczywiste nieruchome cięcie. Rozszerzone statystyki długookresowe pozostają
zadaniem pomiarowym, nie brakującą częścią runtime.
