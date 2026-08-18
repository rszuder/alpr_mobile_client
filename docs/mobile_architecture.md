# Architektura klienta mobilnego ALPR

## Potok wykonawczy

```text
CameraX RGBA
  -> adaptacyjna redukcja klatek
  -> [opcjonalnie] model pojazdu i wycięcie ROI
  -> letterbox i tensor modelu tablic
  -> YOLO Pose + NMS
  -> mapowanie czterech narożników do obrazu kamery
  -> homografia i normalizacja 256x64 / 256x128
  -> model znaków YOLO
  -> grupowanie wierszy i kolejność odczytu
  -> wynik, overlay i InferenceTrace
```

Opcjonalna rola `vehicle` ogranicza obszar wyszukiwany przez model tablic. Gdy model pojazdu nie jest aktywny, model tablic analizuje pełną klatkę.

## Runtime'y

- LiteRT/TFLite: CPU 1/2/4 wątki oraz delegat GPU, jeżeli urządzenie i model go obsługują.
- ONNX Runtime Android: CPU 1/2/4 wątki.
- NCNN: pakiet jest walidowany i przechowywany; wykonanie wymaga adaptera JNI oraz bibliotek dla ABI ARM.

Autotuning jest wykonywany osobno po imporcie każdego modelu. Wynik jest powiązany z SHA-256 manifestu, więc zmiana pakietu wymusza nowy pomiar. Regulator działający podczas sesji zmniejsza liczbę analizowanych klatek przy małej pamięci lub throttlingu termicznym.

## Raportowanie

Eksport sesji tworzy ZIP zawierający:

- `report.json` — urządzenie, aktywne modele, profile autotuningu, statusy, percentyle p50/p90/p95/p99 i pełne ślady;
- `traces.csv` — jeden wiersz na klatkę z czasami etapów i wartościami confidence;
- `README.txt` — opis zawartości.

Czas mierzony jest monotonicznym zegarem Androida. Confidence tablicy i znaków są raportowane oddzielnie; aplikacja nie przedstawia confidence jako dokładności.
