# AKT54 — START bez przygotowania po kliknięciu, FIX03

**Poprawka regresji:** [FIX04](AKT54_START_RECOVERY_FIX04_20261022.md) usuwa blokadę START przy pauzie podglądu i błędzie przygotowania. Aktualny checkout należy sprawdzać patchem FIX04. Poniższy raport opisuje wcześniejszy etap.

Poprzedni FIX02 przeniósł przygotowanie poza UI, ale nadal rozpoczynał je po naciśnięciu START. To pozostawiało opóźnienie ładowania modeli przed zegarem. FIX03 zmienia moment przygotowania: odbywa się ono automatycznie na aktywnym ekranie eksperymentu, przed udostępnieniem START.

## Zachowanie

- Po powrocie do ekranu EXP aplikacja przygotowuje konfigurację, kontroluje pliki, otwiera modele i magazyn sesji oraz uruchamia sam podgląd w dokładnie tej rozdzielczości, której użyje analiza. Magazyn pozostaje PREPARED, zegar sesji nie biegnie, obrazy z kamery nie trafiają do inferencji.
- START staje się aktywny po przygotowaniu modeli i zapisów, otrzymaniu świeżych klatek kamery oraz spełnieniu opcjonalnego warunku termicznego. Brak uprawnień kamery lub ręcznie wyłączony podgląd daje „Włącz podgląd”; brak gotowości nie uruchamia później pomiaru automatycznie.
- Kliknięcie gotowego START ustala wspólne `t0` sesji, metryk i timera. Przełącza istniejący strumień kamery na analizę. Nie otwiera ponownie kamery, nie ładuje modeli i nie dodaje zadania `prepareModels` przed pierwszą klatką. Reset stanu pipeline odbywa się podczas przygotowania.
- Zmiana timera, warunku termicznego lub rewizji ustawień oraz opuszczenie ekranu unieważniają przygotowanie. Następny przebieg jest przygotowywany po finalizacji poprzedniego. Ręczne wyłączenie podglądu jest respektowane.

## Zapis i czas

START nie czeka na zapis pliku na wątku UI. Rekord początku sesji jest pierwszy w kolejce zapisu; executor inferencji czeka na jego trwałe zapisanie przed przetwarzaniem pierwszej klatki. Timer pokazuje czas od kliknięcia. Nie ominięto bramki trwałego zapisu: błąd zapisu nie otwiera akwizycji i kończy sesję błędem. Przygotowanie czeka wcześniej na zakończenie zapisów dowodów inicjalizacji backendów.

Usunięto znane etapy ładowania i wiązania kamery po kliknięciu, a nie fizyczny czas zapisu niewielkiego rekordu START, nadejścia kolejnej klatki lub samej inferencji. Nie zmierzono jeszcze tych opóźnień na A12. Nie ma podstaw do deklarowania zerowego czasu ani natychmiastowego wyniku rozpoznania.

DEBUG `ALPR_PIPELINE_START` zapisuje `start_clicked elapsed_ns`, `click_to_first_frame_ms` i użycie istniejącego wiązania kamery (`ALPR_CAMERA_START reused_preview_binding`). Dotychczasowy `ALPR_STATIC MT_INVOKE` pozwala osobno ocenić pierwszą inferencję. Logi nie zawierają obrazu ani numeru tablicy.

## Walidacja

`gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestJavaWithJavac --console=plain` — PASS. **771 regresji JVM, 0 błędów, 0 pominiętych.** Testy Androidowe skompilowano, nie uruchamiano ich.

Dodane testy Androidowe sprawdzają, że kolejka zapisu nie blokuje obsługi START, akwizycja czeka na trwałe `t0`, błąd zapisu nie otwiera akwizycji, a przygotowana sesja nie zaczyna pomiaru przed kliknięciem. Test fizycznej akwizycji oczekuje teraz gotowości przed START i sprawdza aktywację domeny/kamery w tym samym callbacku kliknięcia. To nie jest jeszcze pomiar opóźnienia na telefonie.

Nie instalowano APK, nie wykonywano pomiaru ani F4, nie zmieniono modeli, progów, istniejących sesji ani GT. Kontrola diff i SHA PASS. Patch sprawdzono w katalogu tymczasowym: odczytowy `--check`, dokładne zastosowanie, backupy, idempotencja i odrzucenie zmienionego źródła.

## Artefakty

- [Patch FIX03 --check/--apply](patches/patch_android_instant_start_fix03_20261022.py) — 5 plików; wejście po FIX02.
- [Diff](patches/INSTANT_START_FIX03_20261022.diff), [manifest SHA](patches/INSTANT_START_FIX03_20261022_sha256.json).
- Backup: `_patch_backups/AKT54_INSTANT_START_FIX03_df000c29/`.

Zmiany są już zastosowane lokalnie. Sprawdzenie:

```powershell
python "C:\Users\48572\AndroidStudioProjects\ALPR_v1\docs\patches\patch_android_instant_start_fix03_20261022.py" --check
```

Zastosowanie w zgodnej kopii stanu wejściowego:

```powershell
python "C:\Users\48572\AndroidStudioProjects\ALPR_v1\docs\patches\patch_android_instant_start_fix03_20261022.py" --apply
```

Następny krok operatora: zbudować tę wersję w Android Studio. Rzeczywista latencja pierwszej klatki/MT pozostaje do potwierdzenia; nie jest wymagane powtarzanie kampanii F4.
