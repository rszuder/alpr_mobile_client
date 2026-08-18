# Pakiet modelu ALPR v1

Klient Android nie importuje surowych checkpointów `.pt`. Program Python eksportuje jeden lub kilka wariantów tego samego checkpointu do archiwum ZIP z rozszerzeniem `.alprmodel`.

## Struktura

```text
model.alprmodel
├── manifest.json
└── variants/
    ├── tflite/model.tflite
    ├── onnx/model.onnx
    └── ncnn/model.param + model.bin
```

`manifest.json` musi znajdować się w katalogu głównym archiwum. Każdy plik wariantu ma obowiązkową sumę SHA-256. Importer odrzuca ścieżki wychodzące poza katalog pakietu, powtórzone wpisy, brakujące pliki, błędne sumy oraz archiwa większe niż 512 MB po rozpakowaniu.

## Manifest

```json
{
  "schema": "alpr.model.v1",
  "model_id": "plate-yolo-pose-001",
  "name": "Detektor tablic 001",
  "version": "1",
  "role": "plate",
  "task": "pose",
  "input": {
    "width": 640,
    "height": 640,
    "channels": 3,
    "layout": "NHWC",
    "color": "RGB",
    "data_type": "FLOAT32",
    "scale": 0.0039215686,
    "offset": 0.0
  },
  "output": {
    "decoder": "ultralytics_pose_raw_v1",
    "class_count": 1,
    "keypoint_count": 4,
    "has_objectness": false,
    "tensor_layout": "channels_first",
    "normalized_coordinates": false,
    "nms_in_graph": false,
    "confidence_threshold": 0.25,
    "iou_threshold": 0.45
  },
  "labels": ["plate"],
  "variants": [
    {
      "id": "tflite-fp32",
      "runtime": "tflite",
      "precision": "fp32",
      "file": "variants/tflite/model.tflite",
      "sha256": {
        "variants/tflite/model.tflite": "SUMA_SHA256_UZUPELNIANA_PRZEZ_EKSPORTER"
      }
    },
    {
      "id": "onnx-fp32",
      "runtime": "onnx",
      "precision": "fp32",
      "file": "variants/onnx/model.onnx",
      "input": {
        "width": 640,
        "height": 640,
        "channels": 3,
        "layout": "NCHW",
        "color": "RGB",
        "data_type": "FLOAT32",
        "scale": 0.0039215686,
        "offset": 0.0
      },
      "sha256": {
        "variants/onnx/model.onnx": "SUMA_SHA256_UZUPELNIANA_PRZEZ_EKSPORTER"
      }
    }
  ]
}
```

Dozwolone role to `vehicle`, `plate` i `character`. Obsługiwane formaty pakietu to `tflite`, `onnx` i `ncnn`. Klient uruchamia warianty TFLite oraz ONNX i porównuje je podczas autotuningu. Poprawne warianty NCNN są importowane i przechowywane, ale ich wykonanie wymaga opcjonalnego adaptera JNI.

Model znaków używa `role: character`, `task: detect` oraz pełnej tablicy `labels` w kolejności identyfikatorów klas datasetu. Model tablic typu `pose` musi zwracać przynajmniej cztery keypointy.

Pola `input` i `output` z poziomu pakietu są wartościami domyślnymi. Wariant może je nadpisać, co jest potrzebne np. wtedy, gdy TFLite przyjmuje tensor `NHWC`, a ONNX tensor `NCHW`.

## Zasady porównania formatów

Warianty TFLite, ONNX i NCNN znajdujące się w jednym pakiecie muszą pochodzić z tego samego checkpointu. Tylko wtedy wyniki autotuningu pozwalają porównać wpływ środowiska wykonawczego, bez mieszania go z różnicami wag modelu.
