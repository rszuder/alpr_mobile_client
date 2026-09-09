package com.example.alpr_v1.ui;

import com.example.alpr_v1.capture.RecognitionHistoryObservation;
import com.example.alpr_v1.capture.ObservationTelemetry;
import com.example.alpr_v1.pipeline.CropInferenceTiming;
import com.example.alpr_v1.pipeline.ModelRuntimeSummary;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class RecognitionObservationDetails {
    private RecognitionObservationDetails() {}
    public static String format(List<RecognitionHistoryObservation> observations) {
        if (observations.isEmpty()) return "";
        StringBuilder text = new StringBuilder("Obserwacje odczytu\n");
        int index = 0;
        for (RecognitionHistoryObservation observation : observations) {
            text.append("\n").append(++index).append(". ")
                    .append(new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date(observation.capturedAtMillis)))
                    .append(" · ").append(observation.text.isEmpty() ? "brak znaków" : observation.text)
                    .append("\nEncja: ").append(observation.entityId > 0L ? observation.entityId : "bez przypisania")
                    .append(" · track pojazdu: ").append(observation.vehicleTrackId)
                    .append(" · track tablicy: ").append(observation.plateTrackId)
                    .append("\nScena: ").append(observation.sceneGeneration).append(" · epoka: ").append(observation.visualEpoch)
                    .append(" · transformacja: ").append(observation.cameraTransformGeneration)
                    .append(" · klatka: ").append(observation.frameId)
                    .append("\nKadr: ").append(observation.sourceWidth).append("×").append(observation.sourceHeight)
                    .append(" · źródło: ").append(observation.captureSource)
                    .append("\nPewność MT: ").append(value(observation.plateConfidence * 100, "%"))
                    .append(" · odczytu: ").append(value(observation.confidence * 100, "%"));
            text.append(observation.confirmed ? " · potwierdzony" : " · wstępny");
            CropInferenceTiming timing = observation.timing;
            text.append("\nMP: ").append(timing == null ? "—" : nanos(timing.vehicleInferenceNanos))
                    .append(" · MT: ").append(timing == null ? "—" : nanos(timing.plateInferenceNanos))
                    .append(" · MZ: ").append(timing == null ? "—" : nanos(timing.characterInferenceNanos));
            if (timing != null) text.append("\nPotok do odczytu: ").append(nanos(timing.pipelineToObservationNanos));
            ObservationTelemetry telemetry = observation.telemetry;
            if (telemetry != null) {
                text.append("\nHUD przy odbiorze: FPS ").append(value(telemetry.cameraFps, ""))
                        .append(" · TMP baterii ").append(value(telemetry.batteryTemperatureC, "°C"))
                        .append(" · CPU aplikacji ").append(value(telemetry.cpuPercent, "%"));
                for (ModelRuntimeSummary model : telemetry.models) text.append("\n").append(model.description());
                if (telemetry.models.isEmpty()) text.append("\nWarianty modeli: brak zapisanego pomiaru");
            }
            if (observation.associationReason != null && !observation.associationReason.isEmpty())
                text.append("\nAsocjacja: ").append(observation.associationReason);
            text.append("\n");
        }
        return text.toString();
    }
    private static String nanos(long nanos) { return nanos < 0L ? "—" : value(nanos / 1_000_000.0, " ms"); }
    private static String value(double number, String unit) {
        return Double.isFinite(number) && number >= 0 ? String.format(Locale.forLanguageTag("pl-PL"), "%.1f", number) + unit : "—";
    }
}
