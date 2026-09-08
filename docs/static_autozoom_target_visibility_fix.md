# Statyczny AZ: utrzymanie tablicy w widocznym kadrze

Dalsze poprawki ramek, badge i podwójnego zoomu opisuje `autozoom_overlay_continuity_fix.md`.

2026-09-08, baza `355ee2d26358ad0bca3c188546b8d784227ad881`.

Po przełączeniu Dynamiczny → Statyczny na nieruchomej scenie z Mustangiem
tablica DO / COBRA wypadała za lewą krawędź podglądu. Statyczny wymuszał 1,8×,
pomijając istniejące ograniczenie zoomu względem granic tablicy i widocznego kadru.
CameraX powiększa wokół środka sensora; punkt ostrości nie przesuwa środka zoomu.

`MainActivity.requestAutoZoom` używa teraz `autoZoomRatioKeepingTargetVisible`
dla obu trybów. Pozostaje istniejący margines 5%, a wycentrowana tablica może
nadal otrzymać pełne 1,8×. Zmiana nie dotyczy polityki wyboru celu, budżetu AZ,
lock/search, modeli ani geometrii obrazu analizy.

## Weryfikacja

- 544 testy JVM: PASS. Nowy przypadek odtwarza geometrię screenshotu
  x=115–172 przy szerokości 720 px: bezpieczny zoom ≈1,32245×, zamiast 1,8×.
- 35 istniejących testów Androida przełączania trybów, resetu sceny i nakładek:
  PASS na Samsungu SM-A125F.
- Nowy `AutoZoomFramingInstrumentedTest`: PASS. Wywołuje rzeczywiste
  `MainActivity.requestAutoZoom` i sprawdza wyemitowane żądanie dla czterech
  kombinacji: Static/Dynamic × tablica przy lewej krawędzi/pośrodku. Test
  podstawia tylko dostępność zoomu aparatu i zatrzymuje zaplanowaną animację.
  Regresja do wymuszonego 1,8× w Static powoduje niepowodzenie tego testu.
- APK z poprawką zainstalowano przez `adb install -r`, po wykonaniu kopii danych.
- Rzeczywisty przebieg Dynamiczny → Statyczny na aktualnej scenie z Citroënem:
  pełna tablica pozostaje w kadrze przy 1,8×. Log: request 12:57:37.647,
  wynik `confirmed_improvement` 12:57:45.576, confidence 0,386→0,461.

Podczas sprawdzania poprawki monitor pokazywał już Citroëna z tablicą pośrodku.
Nie potwierdzono ponownie na telefonie oryginalnej sceny z Mustangiem przy
krawędzi; ten przypadek został sprawdzony testem geometrii i testem żądania AZ.
Nie jest to ocena jakości OCR ani pełna kampania terenowa.

Lokalne logi, kopie i nagranie: `app/build/reports/az-mode-switch-fix/`.
Odtworzenie błędu przed poprawką: `app/build/reports/az-mode-switch-qa/`.
