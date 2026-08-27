# Faza 1 — model domenowy

## Cel

Faza wprowadza warstwę domenową wymaganą przez
`ALPR_wytyczne_dla_agenta_praca_inzynierska.md`, bez przebudowy modeli MP/MT/MZ,
bez ingerencji w obecny pipeline i bez zmian UI.

## Dodane elementy

- `ApplicationMode` — `SCAN_ACQUIRE`, `PICK_ACQUIRE_LOCK`,
  `SEARCH_VERIFY_PURSUIT`.
- `VehicleEntity` — trwała tożsamość łącząca pojazd, tablicę, consensus numeru,
  stan akwizycji, stan wyszukiwania i najlepsze cropy.
- `VehicleEntityRepository` — jedno źródło prawdy dla encji bieżącej sceny.
- `TargetSession` — stan krótkiej lub trwałej pracy nad pojedynczą encją.
- `TargetPurpose` — jawna intencja sesji i wynikająca z niej trwałość.
- `ModeController` — zgodność trybu z intencją i najwyżej jedna aktywna sesja
  pierwszoplanowa.

Pomocnicze typy są niezależne od Androida: znormalizowana geometria, deskryptor
wyglądu, ruch, consensus tekstu, referencje cropów oraz stany encji i sesji.

## Niezmienniki

1. `entityId` jest dodatni, stabilny i nie zmienia się po zmianie technicznego
   `vehicleTrackId`.
2. Jeden dodatni `vehicleTrackId` może wskazywać najwyżej jedną encję.
3. Zmiana trackera przez `reassignVehicleTrack(...)` zachowuje tablicę,
   rozpoznany numer, cropy oraz historię prób.
4. Sesja terminalna (`COMPLETED`, `CANCELLED`, `LOST`) nie przyjmuje dalszych
   aktualizacji i automatycznie oddaje uwagę kamery.
5. Tylko `USER_PICK` oraz `SEARCH_PURSUIT` są sesjami trwałymi i mogą wejść
   w stan trwałego locka.
6. Zmiana trybu anuluje niezgodną aktywną sesję. Promocja wyszukiwania zamyka
   krótką weryfikację i tworzy trwały pursuit dla tej samej encji.
7. Wartości jakości i geometria są normalizowane, a tablice danych są kopiowane
   defensywnie.

## Granica integracji

Nowe klasy nie są jeszcze używane przez `MainActivity`, `MobileAlprEngine`,
`TargetStateMachine` ani autozoom. Integracja rozpocznie się dopiero w Fazie 2
od `VehicleTrackManager`, aby nie tworzyć dwóch konkurencyjnych źródeł stanu.

## Weryfikacja

Testy jednostkowe obejmują:

- stabilność encji i zmianę technicznego track ID;
- wyłączność mapowania track ID;
- wygaszanie encji i zachowanie najlepszego cropa;
- geometrię i kopie defensywne;
- legalne oraz zabronione przejścia sesji;
- zgodność trybów z intencją sesji;
- promocję `SEARCH_VERIFICATION` do `SEARCH_PURSUIT`.
