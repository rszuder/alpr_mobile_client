package com.example.alpr_v1.pipeline;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;

import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.inference.InferenceBackend;
import com.example.alpr_v1.inference.InferenceRunResult;
import com.example.alpr_v1.inference.RuntimeBackendFactory;
import com.example.alpr_v1.inference.TensorDataReader;
import com.example.alpr_v1.inference.TensorInfo;
import com.example.alpr_v1.metrics.InferenceTrace;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelInputSpec;
import com.example.alpr_v1.model.ModelOutputSpec;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.model.ModelRole;
import com.example.alpr_v1.model.ModelVariant;
import com.example.alpr_v1.ui.OverlayItem;
import com.example.alpr_v1.vision.BitmapTensorPreprocessor;
import com.example.alpr_v1.vision.Detection;
import com.example.alpr_v1.vision.PlateRectifier;
import com.example.alpr_v1.vision.Point2;
import com.example.alpr_v1.vision.PreparedInput;
import com.example.alpr_v1.vision.ReadingOrderResolver;
import com.example.alpr_v1.vision.YoloOutputSpec;
import com.example.alpr_v1.vision.YoloRawDecoder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class MobileAlprEngine implements AutoCloseable {
    private final InstalledModel vehicleModel;
    private final InstalledModel plateModel;
    private final InstalledModel characterModel;
    private final InferenceBackend vehicleBackend;
    private final InferenceBackend plateBackend;
    private final InferenceBackend characterBackend;
    private final ModelInputSpec vehicleInputSpec;
    private final ModelInputSpec plateInputSpec;
    private final ModelInputSpec characterInputSpec;
    private final ModelOutputSpec vehicleOutputSpec;
    private final ModelOutputSpec plateOutputSpec;
    private final ModelOutputSpec characterOutputSpec;

    MobileAlprEngine(ModelRegistry registry, AutoTuneManager autoTuneManager) {
        vehicleModel = registry.getActive(ModelRole.VEHICLE);
        plateModel = required(registry, ModelRole.PLATE);
        characterModel = required(registry, ModelRole.CHARACTER);

        ModelVariant plateVariant = autoTuneManager.chosenVariant(plateModel);
        ModelVariant characterVariant = autoTuneManager.chosenVariant(characterModel);
        plateInputSpec = plateVariant.input(plateModel.manifest().input());
        characterInputSpec = characterVariant.input(characterModel.manifest().input());
        plateOutputSpec = plateVariant.output(plateModel.manifest().output());
        characterOutputSpec = characterVariant.output(characterModel.manifest().output());
        validateDecoder(plateOutputSpec);
        validateDecoder(characterOutputSpec);

        ModelVariant vehicleVariant = null;
        ModelInputSpec resolvedVehicleInput = null;
        ModelOutputSpec resolvedVehicleOutput = null;
        if (vehicleModel != null) {
            vehicleVariant = autoTuneManager.chosenVariant(vehicleModel);
            resolvedVehicleInput = vehicleVariant.input(vehicleModel.manifest().input());
            resolvedVehicleOutput = vehicleVariant.output(vehicleModel.manifest().output());
            validateDecoder(resolvedVehicleOutput);
        }
        vehicleInputSpec = resolvedVehicleInput;
        vehicleOutputSpec = resolvedVehicleOutput;

        InferenceBackend openedVehicle = null;
        InferenceBackend openedPlate = null;
        InferenceBackend openedCharacter = null;
        try {
            if (vehicleModel != null) {
                openedVehicle = RuntimeBackendFactory.create(
                        vehicleModel, vehicleVariant, autoTuneManager.chosenProfile(vehicleModel)
                );
            }
            openedPlate = RuntimeBackendFactory.create(
                    plateModel, plateVariant, autoTuneManager.chosenProfile(plateModel)
            );
            openedCharacter = RuntimeBackendFactory.create(
                    characterModel, characterVariant, autoTuneManager.chosenProfile(characterModel)
            );
        } catch (RuntimeException error) {
            closeQuietly(openedCharacter);
            closeQuietly(openedPlate);
            closeQuietly(openedVehicle);
            throw error;
        }
        vehicleBackend = openedVehicle;
        plateBackend = openedPlate;
        characterBackend = openedCharacter;
    }

    PipelineResult run(Bitmap frame, InferenceTrace trace) {
        Bitmap vehicleCrop = null;
        Bitmap plateSource = frame;
        float plateOffsetX = 0f;
        float plateOffsetY = 0f;
        List<OverlayItem> overlays = new ArrayList<>();
        try {
            if (vehicleBackend != null) {
                trace.start("vehicle_preprocess");
                PreparedInput vehicleInput = BitmapTensorPreprocessor.prepare(
                        frame, vehicleInputSpec, vehicleBackend.inputInfo()
                );
                trace.stop("vehicle_preprocess");

                trace.start("vehicle_inference");
                InferenceRunResult vehicleRun = vehicleBackend.run(vehicleInput.buffer);
                trace.stop("vehicle_inference");

                trace.start("vehicle_postprocess");
                Detection vehicle = decodeFirstOutput(vehicleRun, vehicleInputSpec, vehicleOutputSpec).stream()
                        .max(Comparator.comparingDouble(item -> item.confidence))
                        .orElse(null);
                trace.stop("vehicle_postprocess");
                if (vehicle == null) {
                    trace.finish("no_vehicle", "");
                    return new PipelineResult(
                            "no_vehicle", "Nie wykryto pojazdu", "", 0,
                            Collections.emptyList(), frame.getWidth(), frame.getHeight()
                    );
                }
                trace.putConfidence("vehicle", vehicle.confidence);
                float vehicleLeft = vehicleInput.toSourceX(vehicle.left);
                float vehicleTop = vehicleInput.toSourceY(vehicle.top);
                float vehicleRight = vehicleInput.toSourceX(vehicle.right);
                float vehicleBottom = vehicleInput.toSourceY(vehicle.bottom);
                overlays.add(overlayBox(
                        frame, vehicleLeft, vehicleTop, vehicleRight, vehicleBottom,
                        Collections.emptyList(), "pojazd", vehicle.confidence
                ));

                float padX = (vehicleRight - vehicleLeft) * 0.03f;
                float padY = (vehicleBottom - vehicleTop) * 0.05f;
                int cropLeft = clampToInt(vehicleLeft - padX, 0, frame.getWidth() - 1);
                int cropTop = clampToInt(vehicleTop - padY, 0, frame.getHeight() - 1);
                int cropRight = clampToInt(vehicleRight + padX, cropLeft + 1, frame.getWidth());
                int cropBottom = clampToInt(vehicleBottom + padY, cropTop + 1, frame.getHeight());
                vehicleCrop = Bitmap.createBitmap(
                        frame, cropLeft, cropTop, cropRight - cropLeft, cropBottom - cropTop
                );
                plateSource = vehicleCrop;
                plateOffsetX = cropLeft;
                plateOffsetY = cropTop;
            }

            trace.start("plate_preprocess");
            PreparedInput plateInput = BitmapTensorPreprocessor.prepare(
                    plateSource, plateInputSpec, plateBackend.inputInfo()
            );
            trace.stop("plate_preprocess");

            trace.start("plate_inference");
            InferenceRunResult plateRun = plateBackend.run(plateInput.buffer);
            trace.stop("plate_inference");

            trace.start("plate_postprocess");
            Detection plate = decodeFirstOutput(plateRun, plateInputSpec, plateOutputSpec).stream()
                    .filter(item -> item.keypoints.size() >= 4)
                    .max(Comparator.comparingDouble(item -> item.confidence))
                    .orElse(null);
            trace.stop("plate_postprocess");
            if (plate == null) {
                trace.finish("no_plate", "");
                return new PipelineResult(
                        "no_plate", "Nie wykryto tablicy", "", 0,
                        overlays, frame.getWidth(), frame.getHeight()
                );
            }
            trace.putConfidence("plate", plate.confidence);

            List<Point2> sourceCorners = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Point2 point = plate.keypoints.get(i);
                sourceCorners.add(new Point2(
                        plateOffsetX + plateInput.toSourceX(point.x),
                        plateOffsetY + plateInput.toSourceY(point.y),
                        point.confidence
                ));
            }
            float sourceLeft = plateOffsetX + plateInput.toSourceX(plate.left);
            float sourceTop = plateOffsetY + plateInput.toSourceY(plate.top);
            float sourceRight = plateOffsetX + plateInput.toSourceX(plate.right);
            float sourceBottom = plateOffsetY + plateInput.toSourceY(plate.bottom);
            overlays.add(overlayBox(
                    frame, sourceLeft, sourceTop, sourceRight, sourceBottom,
                    sourceCorners, "tablica", plate.confidence
            ));

            trace.start("rectification");
            Bitmap rectified = PlateRectifier.rectify(frame, sourceCorners);
            trace.stop("rectification");
            try {
                trace.start("character_preprocess");
                PreparedInput characterInput = BitmapTensorPreprocessor.prepare(
                        rectified, characterInputSpec, characterBackend.inputInfo()
                );
                trace.stop("character_preprocess");

                trace.start("character_inference");
                InferenceRunResult characterRun = characterBackend.run(characterInput.buffer);
                trace.stop("character_inference");

                trace.start("character_postprocess");
                List<Detection> characters = decodeFirstOutput(
                        characterRun, characterInputSpec, characterOutputSpec
                );
                String text = ReadingOrderResolver.text(characters, characterModel.manifest().labels());
                trace.stop("character_postprocess");
                if (text.isEmpty()) {
                    trace.finish("no_characters", "");
                    return new PipelineResult(
                            "no_characters", "Wykryto tablicę, ale nie odczytano znaków", "", 0,
                            overlays, frame.getWidth(), frame.getHeight()
                    );
                }

                double minimum = 1.0;
                double sum = 0.0;
                for (Detection character : characters) {
                    minimum = Math.min(minimum, character.confidence);
                    sum += character.confidence;
                }
                double mean = characters.isEmpty() ? 0.0 : sum / characters.size();
                trace.putConfidence("characters_min", minimum);
                trace.putConfidence("characters_mean", mean);
                trace.finish("recognized", text);
                return new PipelineResult(
                        "recognized",
                        String.format(
                                Locale.ROOT, "Tablica: %.1f%%, znaki: %.1f%%",
                                plate.confidence * 100, mean * 100
                        ),
                        text, minimum, overlays, frame.getWidth(), frame.getHeight()
                );
            } finally {
                rectified.recycle();
            }
        } finally {
            if (vehicleCrop != null) vehicleCrop.recycle();
        }
    }

    private static List<Detection> decodeFirstOutput(
            InferenceRunResult run,
            ModelInputSpec inputSpec,
            ModelOutputSpec outputSpec
    ) {
        Map.Entry<Integer, ByteBuffer> entry = run.outputs().entrySet().iterator().next();
        TensorInfo info = run.tensorInfo().get(entry.getKey());
        int[] shape = info.shape;
        if (shape.length < 2) throw new IllegalArgumentException("Wyjście YOLO musi mieć co najmniej 2 wymiary");
        for (int i = 0; i < shape.length - 2; i++) {
            if (shape[i] != 1) throw new IllegalArgumentException("Obsługiwany jest wyłącznie batch size 1");
        }
        int first = shape[shape.length - 2];
        int second = shape[shape.length - 1];
        float[] values = TensorDataReader.toFloatArray(entry.getValue(), info);
        return YoloRawDecoder.decode(values, first, second, new YoloOutputSpec(
                outputSpec.classCount(), outputSpec.keypointCount(), outputSpec.hasObjectness(),
                outputSpec.channelsFirst(), outputSpec.normalizedCoordinates(),
                inputSpec.width(), inputSpec.height(),
                outputSpec.confidenceThreshold(), outputSpec.iouThreshold()
        ));
    }

    private static OverlayItem overlayBox(
            Bitmap frame,
            float left,
            float top,
            float right,
            float bottom,
            List<Point2> corners,
            String name,
            float confidence
    ) {
        float width = frame.getWidth();
        float height = frame.getHeight();
        List<PointF> points = new ArrayList<>();
        for (Point2 point : corners) points.add(new PointF(point.x / width, point.y / height));
        return new OverlayItem(
                new RectF(left / width, top / height, right / width, bottom / height),
                points,
                String.format(Locale.ROOT, "%s %.0f%%", name, confidence * 100)
        );
    }

    private static int clampToInt(float value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, Math.round(value)));
    }

    private static InstalledModel required(ModelRegistry registry, ModelRole role) {
        InstalledModel model = registry.getActive(role);
        if (model == null) throw new IllegalStateException("Brak aktywnego modelu: " + role.wireName());
        return model;
    }

    private static void validateDecoder(ModelOutputSpec outputSpec) {
        if (outputSpec.nmsInGraph()) {
            throw new IllegalArgumentException("Dekoder v1 wymaga eksportu bez NMS w grafie");
        }
        String decoder = outputSpec.decoder();
        if (!decoder.equals("ultralytics_yolo_raw_v1")
                && !decoder.equals("ultralytics_pose_raw_v1")
                && !decoder.equals("ultralytics_detect_raw_v1")) {
            throw new IllegalArgumentException("Nieobsługiwany dekoder: " + decoder);
        }
    }

    private static void closeQuietly(InferenceBackend backend) {
        if (backend != null) backend.close();
    }

    @Override
    public void close() {
        characterBackend.close();
        plateBackend.close();
        closeQuietly(vehicleBackend);
    }
}
