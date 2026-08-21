package com.example.alpr_v1.vision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Dekoder wyjścia Ultralytics end-to-end [1,N,C] lub [1,C,N], już po selekcji detekcji. */
public final class YoloEndToEndDecoder {
    private YoloEndToEndDecoder() {}

    public static List<Detection> decode(float[] data, int first, int second, YoloOutputSpec spec) {
        int attributes = spec.channelsFirst ? first : second;
        int detections = spec.channelsFirst ? second : first;
        int keypointStart = Math.max(6, Math.max(spec.scoreIndex, spec.classIndex) + 1);
        int expectedAttributes = keypointStart + spec.keypointCount * spec.keypointDimensions;
        if (attributes < expectedAttributes || data.length < attributes * detections) {
            throw new IllegalArgumentException(
                    "Tensor YOLO end-to-end ma niezgodny kształt: atrybuty=" + attributes
                            + ", oczekiwano co najmniej " + expectedAttributes
            );
        }

        List<Detection> decoded = new ArrayList<>();
        for (int detection = 0; detection < detections; detection++) {
            float confidence = value(
                    data, attributes, detections, spec.scoreIndex, detection, spec.channelsFirst
            );
            if (!Float.isFinite(confidence) || confidence < spec.confidenceThreshold) continue;

            float classValue = value(
                    data, attributes, detections, spec.classIndex, detection, spec.channelsFirst
            );
            int classId = Math.round(classValue);
            if (!Float.isFinite(classValue) || classId < 0 || classId >= spec.classCount) {
                throw new IllegalArgumentException("Tensor YOLO end-to-end zawiera nieprawidłowy class_id: " + classValue);
            }

            float left = value(data, attributes, detections, 0, detection, spec.channelsFirst);
            float top = value(data, attributes, detections, 1, detection, spec.channelsFirst);
            float right = value(data, attributes, detections, 2, detection, spec.channelsFirst);
            float bottom = value(data, attributes, detections, 3, detection, spec.channelsFirst);
            if (spec.normalizedCoordinates) {
                left *= spec.inputWidth;
                right *= spec.inputWidth;
                top *= spec.inputHeight;
                bottom *= spec.inputHeight;
            }

            List<Point2> keypoints = new ArrayList<>();
            int cursor = keypointStart;
            for (int point = 0; point < spec.keypointCount; point++) {
                float x = value(data, attributes, detections, cursor++, detection, spec.channelsFirst);
                float y = value(data, attributes, detections, cursor++, detection, spec.channelsFirst);
                float pointConfidence = 1f;
                if (spec.keypointDimensions >= 3) {
                    pointConfidence = value(
                            data, attributes, detections, cursor++, detection, spec.channelsFirst
                    );
                }
                cursor += Math.max(0, spec.keypointDimensions - 3);
                if (spec.normalizedCoordinates) {
                    x *= spec.inputWidth;
                    y *= spec.inputHeight;
                }
                keypoints.add(new Point2(x, y, pointConfidence));
            }

            decoded.add(new Detection(
                    classId,
                    confidence,
                    clamp(left, 0, spec.inputWidth),
                    clamp(top, 0, spec.inputHeight),
                    clamp(right, 0, spec.inputWidth),
                    clamp(bottom, 0, spec.inputHeight),
                    keypoints
            ));
        }
        return Collections.unmodifiableList(decoded);
    }

    private static float value(
            float[] data,
            int attributes,
            int detections,
            int attribute,
            int detection,
            boolean channelsFirst
    ) {
        return channelsFirst
                ? data[attribute * detections + detection]
                : data[detection * attributes + attribute];
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
