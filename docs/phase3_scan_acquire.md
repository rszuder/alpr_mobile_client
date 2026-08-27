# Faza 3 — Scan–Acquire bez autozoomu

## Przepływ

Normalny `USER_LIVE` realizuje teraz:

```text
MP → VehicleTrackManager → VehicleEntityRepository → AcquisitionQueue
→ krótka TargetSession → pojedynczy ROI MT → MZ → ocena cropa
→ acquired/requeue/failed → release → następny kandydat
```

Tryb eksperymentalny zachowuje dotychczasową ścieżkę legacy R0/R1/R2.

## Kolejka

- maksymalnie 8 kandydatów;
- TTL 1,8 s od ostatniej obecności kandydata;
- najwyżej jeden aktywny cel;
- najwyżej jeden przebieg MT na cykl live;
- maksymalnie 3 próby MT na encję;
- obowiązkowy `waitingAgeBonus`, narastający do 0,35 w ciągu 4 s;
- ranking uwzględnia novelty, czytelność, pilność wyjścia, priorytet Search,
  ostatnie próby, duplikat i koszt zoomu.

W Fazie 3 koszt zoomu wynosi zero, ponieważ zwykły Scan nie uruchamia autozoomu.

## Zakończenie akwizycji

Sukces następuje po spełnieniu co najmniej jednego warunku:

- stabilny consensus rejestracji;
- wynik jakości cropa co najmniej 0,86;
- wynik mieszany co najmniej 0,68 przy odpowiednim MT i MZ.

Po sukcesie encja otrzymuje najlepszy `WIDE_PLATE` crop, zostaje oznaczona jako
`ACQUIRED`, sesja przechodzi do `COMPLETED`, a focused target jest zwalniany.
Nieudana próba wraca do kolejki z karą; trzecia kończy encję stanem `FAILED`.

## Best crop i deduplikacja

`BestCropSelector` ocenia sharpness, powierzchnię tablicy, perspektywę,
ekspozycję, stabilność ruchu, confidence MT/MZ i źródło zoomu. Repozytorium
zachowuje wyłącznie najlepszą referencję danego rodzaju.

`AcquisitionDeduplicator` odrzuca ten sam stabilny numer oraz bardzo pewne
powtórzenie wyglądu i położenia już pozyskanej encji. Sam podobny wygląd daje
karę priorytetu, a nie twarde odrzucenie.

## Zachowanie locka i UI

Zwykły Scan nie przekazuje celu do globalnego locka v1 i nie uruchamia
autozoomu. Po wyniku tracker precyzyjny jest zwalniany, dzięki czemu pipeline
wraca do następnej encji zamiast pozostawać na pierwszej tablicy. UI nie dostało
nowych kontrolek ani dodatkowych paneli.

## Telemetria

- `scan_candidate_mt_runs`;
- `scan_active_entity_id`;
- `scan_acquisition_outcome`;
- `scan_vehicles_processed`;
- `scan_candidates_requeued`;
- `scan_candidates_failed`.
