package com.example.alpr_v1.vision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Dekoder surowego wyjścia YOLO [1,C,N] lub [1,N,C], bez NMS w grafie. */
public final class YoloRawDecoder {
    private YoloRawDecoder() {}

    public static List<Detection> decode(float[] data, int first, int second, YoloOutputSpec spec) {
        int channels = spec.channelsFirst ? first : second;
        int anchors = spec.channelsFirst ? second : first;
        int expected = 4 + (spec.hasObjectness ? 1 : 0) + spec.classCount
                + spec.keypointCount * spec.keypointDimensions;
        if (channels < expected || data.length < channels * anchors) {
            throw new IllegalArgumentException(
                    "Tensor YOLO ma niezgodny kształt: kanały=" + channels + ", oczekiwano co najmniej " + expected
            );
        }

        List<Detection> decoded = new ArrayList<>();
        for (int anchor = 0; anchor < anchors; anchor++) {
            float cx = value(data, channels, anchors, 0, anchor, spec.channelsFirst);
            float cy = value(data, channels, anchors, 1, anchor, spec.channelsFirst);
            float width = value(data, channels, anchors, 2, anchor, spec.channelsFirst);
            float height = value(data, channels, anchors, 3, anchor, spec.channelsFirst);
            int cursor = 4;
            float objectness = 1f;
            if (spec.hasObjectness) {
                objectness = value(data, channels, anchors, cursor++, anchor, spec.channelsFirst);
            }
            int bestClass = -1;
            float bestClassScore = Float.NEGATIVE_INFINITY;
            for (int classId = 0; classId < spec.classCount; classId++) {
                float score = value(data, channels, anchors, cursor + classId, anchor, spec.channelsFirst);
                if (score > bestClassScore) {
                    bestClassScore = score;
                    bestClass = classId;
                }
            }
            float confidence = objectness * bestClassScore;
            if (confidence < spec.confidenceThreshold) continue;
            cursor += spec.classCount;

            if (spec.normalizedCoordinates) {
                cx *= spec.inputWidth;
                width *= spec.inputWidth;
                cy *= spec.inputHeight;
                height *= spec.inputHeight;
            }
            List<Point2> keypoints = new ArrayList<>();
            for (int point = 0; point < spec.keypointCount; point++) {
                float x = value(data, channels, anchors, cursor++, anchor, spec.channelsFirst);
                float y = value(data, channels, anchors, cursor++, anchor, spec.channelsFirst);
                float pointConfidence = 1f;
                if (spec.keypointDimensions >= 3) {
                    pointConfidence = value(data, channels, anchors, cursor++, anchor, spec.channelsFirst);
                }
                cursor += Math.max(0, spec.keypointDimensions - 3);
                if (spec.normalizedCoordinates) {
                    x *= spec.inputWidth;
                    y *= spec.inputHeight;
                }
                keypoints.add(new Point2(x, y, pointConfidence));
            }

            decoded.add(new Detection(
                    bestClass,
                    confidence,
                    clamp(cx - width * 0.5f, 0, spec.inputWidth),
                    clamp(cy - height * 0.5f, 0, spec.inputHeight),
                    clamp(cx + width * 0.5f, 0, spec.inputWidth),
                    clamp(cy + height * 0.5f, 0, spec.inputHeight),
                    keypoints
            ));
        }
        return NonMaxSuppression.apply(decoded, spec.iouThreshold, true);
    }

    private static float value(
            float[] data,
            int channels,
            int anchors,
            int channel,
            int anchor,
            boolean channelsFirst
    ) {
        return channelsFirst ? data[channel * anchors + anchor] : data[anchor * channels + channel];
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
