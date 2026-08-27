# Faza 2 — tracking wielu pojazdów

## Zakres

Za modelem MP działa teraz `VehicleTrackManager`. Jest to osobny mechanizm od
`MotionBoxTracker` i `PreviewPlateTracker`: pierwszy z nich pozostaje lekkim
trackerem geometrii tablic, a drugi dokładnym trackerem pojedynczego celu.

`VehicleTrackManager` odpowiada za:

- maksymalnie 16 równoległych śladów pojazdów;
- filtr Kalmana `[cx, cy, width, height]` i predykcję między przebiegami MP;
- globalne zachłanne przypisanie na podstawie IoU, odległości, skali,
  confidence i opcjonalnego deskryptora wyglądu;
- stabilny `VehicleEntity.entityId` niezależny od technicznego
  `vehicleTrackId`;
- ponowne skojarzenie encji po wygaśnięciu technicznego śladu;
- `motion` oraz `exitUrgency` potrzebne w następnej fazie do rankingu kolejki;
- dwa poziomy TTL: 1,8 s dla technicznego śladu oraz 5 s dla nieaktywnej encji.

## Integracja

MP nadal wykonuje te same modele i zachowuje polityki R0/R1/R2. Po
deduplikacji pojazdów:

1. ramki są normalizowane;
2. z cropa powstaje tani deskryptor układu koloru 6×4;
3. manager aktualizuje `VehicleEntityRepository`;
4. wygładzone lub przewidziane ramki zasilają cache diagnostyczny i wybór ROI;
5. reset sceny atomowo czyści tracker pojazdów i repozytorium encji.

W cyklach bez MP cache nie zamraża już ostatniej ramki: korzysta z czasowej
predykcji managera. Faza nie uruchamia dokładnego trackera tablic dla każdego
pojazdu i nie zmienia UI.

## Telemetria

Dodane liczniki śladu inferencji:

- `vehicle_tracks_active`;
- `vehicle_tracks_predicted`;
- `vehicle_entities_active`.

## Testy

Testy jednostkowe sprawdzają:

- ciągłość dwóch zbliżających się pojazdów;
- predykcję ruchu podczas krótkiej przerwy MP;
- TTL technicznego tracka;
- reassociation tej samej encji z nowym track ID;
- kierunkową wartość `exitUrgency`;
- limit liczby jednoczesnych śladów.
