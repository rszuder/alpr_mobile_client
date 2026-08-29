package com.example.alpr_v1.metrics;

import android.graphics.Bitmap;

import com.example.alpr_v1.capture.CapturedPlateItem;
import com.example.alpr_v1.model.InstalledAlprPackage;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.model.ModelRole;
import com.example.alpr_v1.pipeline.PlateCharacter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Iterator;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Strumieniowy eksport paczki badawczej oraz samodzielnego skrótu TeX. */
public final class ResearchArchive {
    public enum Kind { RESEARCH_SESSION, THESIS_BUNDLE, LEGACY_REPORT }

    public static final String RESEARCH_SCHEMA = "alpr.mobile_research_bundle.v1";
    public static final String THESIS_SCHEMA = "alpr.mobile_thesis_bundle.v1";
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_TEX_CROPS = 12;

    private ResearchArchive() {}

    public static void writeResearchSession(
            OutputStream destination,
            String reportJson,
            String tracesCsv,
            String applicationLog,
            List<CapturedPlateItem> crops,
            ModelRegistry registry
    ) throws Exception {
        writeResearchSession(
                destination,
                reportJson,
                tracesCsv,
                "experiment_session_id,elapsed_ms,battery_temperature_c,thermal_status,thermal_headroom,headroom_available,battery_percent,charging,available_memory_bytes\n",
                "experiment_session_id,elapsed_ms,frames_received,frames_processed,frames_skipped_frame_gate,frames_skipped_camera_transform,frames_skipped_hard_scene_reset,frames_skipped_continuity_hold,frames_skipped_continuity_reacquire,estimated_upstream_gaps\n",
                "",
                applicationLog,
                crops,
                registry
        );
    }

    public static void writeResearchSession(
            OutputStream destination,
            String reportJson,
            String tracesCsv,
            String thermalCsv,
            String frameFlowCsv,
            String eventsJsonl,
            String applicationLog,
            List<CapturedPlateItem> crops,
            ModelRegistry registry
    ) throws Exception {
        JSONObject report = new JSONObject(reportJson);
        ArchiveWriter archive = new ArchiveWriter(destination);
        boolean exactSource = false;
        try {
            archive.writeText("report.json", reportJson);
            archive.writeText("traces.csv", tracesCsv);
            archive.writeText("thermal.csv", thermalCsv == null ? "" : thermalCsv);
            archive.writeText("frame_flow.csv", frameFlowCsv == null ? "" : frameFlowCsv);
            archive.writeText("events.jsonl", eventsJsonl == null ? "" : eventsJsonl);
            archive.writeText("application.log", applicationLog == null ? "" : applicationLog);
            archive.writeText("README.md", researchReadme());
            archive.writeText("protocol.json", protocolJson(report).toString(2));
            archive.writeText("environment/device.json", report.optJSONObject("device") == null
                    ? "{}"
                    : report.getJSONObject("device").toString(2));
            archive.writeText("environment/software.json", softwareJson(report).toString(2));

            InstalledAlprPackage activePackage = registry.getActivePackage();
            InstalledAlprPackage basePackage = registry.getBasePackage();
            InstalledAlprPackage provenancePackage = activePackage == null
                    ? basePackage
                    : activePackage;
            if (provenancePackage != null) {
                archive.writeText(
                        "pipeline/package_manifest.json",
                        provenancePackage.manifest().rawJson()
                );
                if (provenancePackage.sourceArchive().isFile()) {
                    archive.writeFile(
                            activePackage == null
                                    ? "pipeline/base_pipeline.alprmodel"
                                    : "pipeline/pipeline.alprmodel",
                            provenancePackage.sourceArchive()
                    );
                    exactSource = activePackage != null;
                }
            }
            boolean vehicleInExactPackage = exactSource
                    && activePackage != null
                    && activePackage.vehicleModel() != null;
            writeModel(
                    archive,
                    registry.getActive(ModelRole.VEHICLE),
                    "vehicle",
                    !vehicleInExactPackage
            );
            writeModel(archive, registry.getActive(ModelRole.PLATE), "plate", !exactSource);
            writeModel(archive, registry.getActive(ModelRole.CHARACTER), "character", !exactSource);

            archive.writeText("samples/index.csv", cropIndexCsv(crops));
            archive.writeText("samples/annotations.jsonl", annotationsJsonl(crops));
            for (CapturedPlateItem item : crops) {
                if (item.bitmap == null || item.bitmap.isRecycled()) continue;
                archive.writeBitmap("samples/crops/" + safeId(item.captureId) + ".jpg", item.bitmap);
            }

            JSONObject manifest = baseManifest(RESEARCH_SCHEMA, report, archive.hashes());
            manifest.put("telemetry_schema", "alpr.mobile_experiment_telemetry.v1");
            manifest.put("self_contained", hasRequiredModels(registry));
            manifest.put("exact_source_package_embedded", exactSource);
            manifest.put("crop_count", crops.size());
            manifest.put("entry_sha256", new JSONObject(archive.hashes()));
            archive.writeManifest(manifest.toString(2));
        } finally {
            archive.close();
        }
    }

    public static void writeThesisBundle(
            OutputStream destination,
            String reportJson,
            String tracesCsv,
            List<CapturedPlateItem> crops
    ) throws Exception {
        JSONObject report = new JSONObject(reportJson);
        ArchiveWriter archive = new ArchiveWriter(destination);
        try {
            archive.writeText("summary.tex", summaryTex(report, crops));
            archive.writeText("references.bib", referencesBib());
            archive.writeText("metadata.json", thesisMetadata(report, crops).toString(2));
            archive.writeText("tables/configuration_mt_mz.tex", configurationTableTex(report));
            archive.writeText("tables/mobile_quality.tex", qualityTableTex(report));
            archive.writeText("tables/mobile_latency.tex", latencyTableTex(report));
            archive.writeText("tables/trace_data.csv", tracesCsv);
            int included = 0;
            for (CapturedPlateItem item : crops) {
                if (item.bitmap == null || item.bitmap.isRecycled()) continue;
                archive.writeBitmap(
                        "figures/selected_crops/" + safeId(item.captureId) + ".jpg",
                        item.bitmap
                );
                included++;
            }
            JSONObject manifest = baseManifest(THESIS_SCHEMA, report, archive.hashes());
            manifest.put("crop_count", included);
            manifest.put("tex_preview_crop_limit", MAX_TEX_CROPS);
            manifest.put("entry_sha256", new JSONObject(archive.hashes()));
            archive.writeManifest(manifest.toString(2));
        } finally {
            archive.close();
        }
    }

    private static void writeModel(
            ArchiveWriter archive,
            InstalledModel model,
            String role,
            boolean includeArtifact
    )
            throws Exception {
        if (model == null) return;
        archive.writeText("pipeline/" + role + "_manifest.json", model.manifest().rawJson());
        if (!includeArtifact) return;
        if (model.sourceArchive().isFile()) {
            archive.writeFile("pipeline/models/" + role + "/model.alprmodel", model.sourceArchive());
        } else {
            archive.writeDirectory(model.directory(), "pipeline/installed/" + role + "/");
        }
    }

    private static JSONObject protocolJson(JSONObject report) throws JSONException {
        JSONObject protocol = new JSONObject();
        protocol.put("methodology", "MLPerf Mobile inspired; not an MLPerf result");
        protocol.put("scenario", "live_camera_session");
        protocol.put("clock", "monotonic_nanos");
        protocol.put("cold_start_separated", false);
        protocol.put("single_stream_target_samples", 1024);
        protocol.put("single_stream_target_duration_s", 60);
        protocol.put("primary_latency_percentile", 90);
        protocol.put("reported_percentiles", new JSONArray(Arrays.asList(50, 90, 95, 99)));
        protocol.put("measured_samples", report.optInt("measured_runs", 0));
        protocol.put("mlperf_compliant", false);
        protocol.put("quality_unit", "unique_crop_or_track_with_human_ground_truth");
        return protocol;
    }

    private static JSONObject softwareJson(JSONObject report) throws JSONException {
        JSONObject software = new JSONObject();
        software.put("app_version", report.optString("app_version", "unknown"));
        software.put("package_id", report.optString("package_id", ""));
        software.put("variant_id", report.optString("variant_id", ""));
        software.put("execution", report.optJSONObject("execution"));
        software.put("autotune_profiles", report.optJSONObject("autotune_profiles"));
        return software;
    }

    private static JSONObject baseManifest(
            String schema,
            JSONObject report,
            Map<String, String> currentHashes
    ) throws JSONException {
        JSONObject manifest = new JSONObject();
        manifest.put("schema", schema);
        manifest.put("bundle_id", "b-" + UUID.randomUUID());
        manifest.put("created_at", Instant.now().toString());
        manifest.put("report_schema", report.optString("schema", ""));
        manifest.put("report_id", report.optString("report_id", ""));
        manifest.put("package_id", report.optString("package_id", ""));
        manifest.put("variant_id", report.optString("variant_id", ""));
        manifest.put("hash_algorithm", "SHA-256");
        manifest.put("entries_before_manifest", currentHashes.size());
        return manifest;
    }

    private static boolean hasRequiredModels(ModelRegistry registry) {
        return registry.getActive(ModelRole.PLATE) != null
                && registry.getActive(ModelRole.CHARACTER) != null;
    }

    private static String cropIndexCsv(List<CapturedPlateItem> crops) {
        StringBuilder csv = new StringBuilder(
                "capture_id,session_id,track_id,captured_at_ms,prediction,verification_status,ground_truth,plate_confidence,recognition_confidence,sharpness,pipeline_ms,mz_ms,camera_zoom_ratio,capture_source,track_confirmed,fresh_mz_successful,crop_supports_consensus,consensus_observations,mz_attempt_index,layout,row_counts,plate_bbox_width_px,plate_bbox_height_px,plate_bbox_area_ratio,plate_quad_area_ratio,plate_corners_norm,mean_luminance,luminance_stddev,underexposed_ratio,overexposed_ratio,image_metrics_computation_ms\n"
        );
        for (CapturedPlateItem item : crops) {
            csv.append(csv(item.captureId)).append(',')
                    .append(csv(item.sessionId)).append(',')
                    .append(item.trackId).append(',')
                    .append(item.capturedAtMillis).append(',')
                    .append(csv(item.text)).append(',')
                    .append(item.verificationStatus.wireName()).append(',')
                    .append(csv(item.groundTruthText)).append(',')
                    .append(format(item.plateConfidence)).append(',')
                    .append(format(item.recognitionConfidence)).append(',')
                    .append(format(item.sharpness)).append(',')
                    .append(item.timing == null ? "" : format(item.timing.totalMilliseconds())).append(',')
                    .append(item.timing == null ? "" : format(
                            item.timing.characterInferenceNanos / 1_000_000.0
                    )).append(',')
                    .append(format(item.cameraZoomRatio)).append(',')
                    .append(csv(item.captureSource)).append(',')
                    .append(item.trackConfirmed).append(',')
                    .append(item.freshMzSuccessful).append(',')
                    .append(item.cropSupportsConsensus).append(',')
                    .append(item.consensusObservations).append(',')
                    .append(item.mzAttemptIndex).append(',')
                    .append(csv(item.layout)).append(',')
                    .append(csv(item.rowCounts.toString())).append(',')
                    .append(item.plateGeometry.available() ? format(item.plateGeometry.bboxWidthPx()) : "").append(',')
                    .append(item.plateGeometry.available() ? format(item.plateGeometry.bboxHeightPx()) : "").append(',')
                    .append(item.plateGeometry.available() ? format(item.plateGeometry.bboxAreaRatio) : "").append(',')
                    .append(item.plateGeometry.available() ? format(item.plateGeometry.quadAreaRatio) : "").append(',')
                    .append(csv(corners(item))).append(',')
                    .append(item.imageDifficulty.available ? format(item.imageDifficulty.meanLuminance) : "").append(',')
                    .append(item.imageDifficulty.available ? format(item.imageDifficulty.luminanceStddev) : "").append(',')
                    .append(item.imageDifficulty.available ? format(item.imageDifficulty.underexposedRatio) : "").append(',')
                    .append(item.imageDifficulty.available ? format(item.imageDifficulty.overexposedRatio) : "").append(',')
                    .append(item.imageDifficulty.available ? format(item.imageDifficulty.computationMs) : "")
                    .append('\n');
        }
        return csv.toString();
    }

    private static String annotationsJsonl(List<CapturedPlateItem> crops) throws JSONException {
        StringBuilder jsonl = new StringBuilder();
        for (CapturedPlateItem item : crops) {
            JSONObject record = new JSONObject();
            record.put("capture_id", item.captureId);
            record.put("session_id", item.sessionId);
            record.put("track_id", item.trackId);
            record.put("captured_at_ms", item.capturedAtMillis);
            record.put("prediction", item.text);
            record.put("plate_confidence", item.plateConfidence);
            record.put("recognition_confidence", item.recognitionConfidence);
            record.put("sharpness", item.sharpness);
            record.put("camera_zoom_ratio", item.cameraZoomRatio);
            record.put("capture_source", item.captureSource);
            record.put("track_confirmed", item.trackConfirmed);
            record.put("fresh_mz_successful", item.freshMzSuccessful);
            record.put("crop_supports_consensus", item.cropSupportsConsensus);
            record.put("consensus_observations", item.consensusObservations);
            record.put("mz_attempt_index", item.mzAttemptIndex);
            record.put("layout", item.layout);
            record.put("row_counts", new JSONArray(item.rowCounts));
            record.put("fresh_prediction", item.freshPrediction);
            record.put("plate_geometry", item.plateGeometry.toJson());
            record.put("image_difficulty", item.imageDifficulty.toJson());
            record.put("verification_status", item.verificationStatus.wireName());
            record.put("ground_truth_text", item.groundTruthText);
            record.put("verified_at_ms", item.verifiedAtMillis);
            record.put("verification_revision", item.verificationRevision);
            JSONArray characters = new JSONArray();
            for (PlateCharacter character : item.characters) {
                JSONObject value = new JSONObject();
                value.put("label", character.label);
                value.put("confidence", character.confidence);
                value.put("left", character.left);
                value.put("top", character.top);
                value.put("right", character.right);
                value.put("bottom", character.bottom);
                characters.put(value);
            }
            record.put("characters", characters);
            if (item.timing != null) record.put("timing", item.timing.toJson());
            jsonl.append(record).append('\n');
        }
        return jsonl.toString();
    }

    private static JSONObject thesisMetadata(JSONObject report, List<CapturedPlateItem> crops)
            throws JSONException {
        JSONObject metadata = new JSONObject();
        metadata.put("schema", THESIS_SCHEMA);
        metadata.put("created_at", Instant.now().toString());
        metadata.put("report_id", report.optString("report_id", ""));
        metadata.put("package_id", report.optString("package_id", ""));
        metadata.put("variant_id", report.optString("variant_id", ""));
        metadata.put("device", report.optJSONObject("device"));
        metadata.put("capture", report.optJSONObject("capture"));
        metadata.put("quality", report.optJSONObject("quality"));
        metadata.put("latency", report.optJSONObject("latency"));
        metadata.put("crop_count", crops.size());
        return metadata;
    }

    static String summaryTex(JSONObject report, List<CapturedPlateItem> crops) {
        StringBuilder tex = new StringBuilder();
        tex.append("\\documentclass[11pt,a4paper]{article}\n")
                .append("\\usepackage[T1]{fontenc}\n")
                .append("\\usepackage[utf8]{inputenc}\n")
                .append("\\usepackage{graphicx,booktabs,longtable,geometry,float}\n")
                .append("\\geometry{margin=2cm}\n")
                .append("\\title{Mobilny ALPR -- skrót eksperymentu}\n")
                .append("\\date{").append(tex(report.optString("measured_at", ""))).append("}\n")
                .append("\\begin{document}\\maketitle\n")
                .append("Metodykę pomiarową oparto na zasadach transparentności benchmarków mobilnych~\\cite{reddi2022mlperfmobile}; metryki sekwencji wykorzystują exact match i odległość edycyjną~\\cite{shahab2011robust}.\\par\n")
                .append("\\section{Konfiguracja MT i MZ}\\input{tables/configuration_mt_mz.tex}\n")
                .append("\\section{Skuteczność}\\input{tables/mobile_quality.tex}\n")
                .append("\\section{Czasy inferencji}\\input{tables/mobile_latency.tex}\n")
                .append("\\section{Wybrane cropy}\n");
        int shown = 0;
        for (CapturedPlateItem item : crops) {
            if (shown >= MAX_TEX_CROPS || item.bitmap == null || item.bitmap.isRecycled()) break;
            String name = safeId(item.captureId) + ".jpg";
            tex.append("\\begin{figure}[H]\\centering\n")
                    .append("\\includegraphics[width=0.72\\linewidth]{figures/selected_crops/")
                    .append(tex(name)).append("}\n")
                    .append("\\caption{")
                    .append(tex(cropCaption(item))).append("}\n")
                    .append("\\end{figure}\n");
            shown++;
        }
        tex.append("\\bibliographystyle{plain}\n")
                .append("\\bibliography{references}\n")
                .append("\\end{document}\n");
        return tex.toString();
    }

    static String configurationTableTex(JSONObject report) {
        StringBuilder table = new StringBuilder();
        table.append("\\begin{tabular}{lllllll}\\toprule\n")
                .append("Rola & Model & Wariant & Runtime & Precyzja & Wejście & SHA \\\\ \\midrule\n");
        JSONObject execution = report.optJSONObject("execution");
        if (execution != null) {
            addExecutionRow(table, "MT", execution.optJSONObject("plate"));
            addExecutionRow(table, "MZ", execution.optJSONObject("character"));
            addExecutionRow(table, "MP", execution.optJSONObject("vehicle"));
        }
        table.append("\\bottomrule\\end{tabular}\n");
        return table.toString();
    }

    private static void addExecutionRow(StringBuilder table, String role, JSONObject value) {
        if (value == null) return;
        JSONObject input = value.optJSONObject("input");
        String size = input == null ? "--" : input.optInt("width") + "x" + input.optInt("height");
        table.append(tex(role)).append(" & ")
                .append(tex(value.optString("model_id", "--"))).append(" & ")
                .append(tex(value.optString("variant_id", "--"))).append(" & ")
                .append(tex(value.optString("runtime", "--"))).append(" & ")
                .append(tex(value.optString("precision", "--"))).append(" & ")
                .append(tex(size)).append(" & ")
                .append(tex(shortHash(firstChecksum(value))))
                .append(" \\\\ \n");
    }

    static String qualityTableTex(JSONObject report) {
        JSONObject quality = report.optJSONObject("quality");
        if (quality == null || !quality.optBoolean("available", false)) {
            return "Brak ground truth -- metryki exact match i CER są niedostępne.\\par\n";
        }
        return "\\begin{tabular}{lr}\\toprule\n"
                + "Metryka & Wynik \\\\ \\midrule\n"
                + "Liczba próbek GT & " + quality.optInt("ground_truth_samples", 0) + " \\\\ \n"
                + "Exact match & " + percent(quality.optDouble("exact_match_rate", 0.0)) + " \\\\ \n"
                + "CER & " + format(quality.optDouble("cer", 0.0)) + " \\\\ \n"
                + "Śr. NED & " + format(quality.optDouble("normalized_edit_distance_mean", 0.0))
                + " \\\\ \\bottomrule\\end{tabular}\n";
    }

    static String latencyTableTex(JSONObject report) {
        StringBuilder table = new StringBuilder();
        table.append("\\begin{tabular}{lrrrrr}\\toprule\n")
                .append("Etap & n & p50 [ms] & p90 [ms] & p95 [ms] & p99 [ms] \\\\ \\midrule\n");
        JSONObject latency = report.optJSONObject("latency");
        if (latency != null) {
            addLatencyRow(table, "MT", latency.optJSONObject("mt"));
            addLatencyRow(table, "MZ", latency.optJSONObject("mz"));
            addLatencyRow(table, "Pipeline", latency.optJSONObject("pipeline"));
        }
        table.append("\\bottomrule\\end{tabular}\n");
        return table.toString();
    }

    private static void addLatencyRow(StringBuilder table, String name, JSONObject value) {
        if (value == null) return;
        table.append(tex(name)).append(" & ")
                .append(value.optInt("count", 0)).append(" & ")
                .append(format(value.optDouble("p50_ms", 0.0))).append(" & ")
                .append(format(value.optDouble("p90_ms", 0.0))).append(" & ")
                .append(format(value.optDouble("p95_ms", 0.0))).append(" & ")
                .append(format(value.optDouble("p99_ms", 0.0))).append(" \\\\ \n");
    }

    static String tex(String value) {
        if (value == null) return "";
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\': escaped.append("\\textbackslash{}"); break;
                case '&': escaped.append("\\&"); break;
                case '%': escaped.append("\\%"); break;
                case '$': escaped.append("\\$"); break;
                case '#': escaped.append("\\#"); break;
                case '_': escaped.append("\\_"); break;
                case '{': escaped.append("\\{"); break;
                case '}': escaped.append("\\}"); break;
                case '~': escaped.append("\\textasciitilde{}"); break;
                case '^': escaped.append("\\textasciicircum{}"); break;
                case '\n': escaped.append(' '); break;
                default: escaped.append(character); break;
            }
        }
        return escaped.toString();
    }

    private static String cropCaption(CapturedPlateItem item) {
        String groundTruth = item.groundTruthText.isEmpty() ? "brak GT" : item.groundTruthText;
        double pipeline = item.timing == null ? 0.0 : item.timing.totalMilliseconds();
        return item.captureId + "; prediction=" + item.text
                + "; ground truth=" + groundTruth
                + "; MT=" + percentPlain(item.plateConfidence)
                + "; MZ=" + percentPlain(item.recognitionConfidence)
                + "; pipeline=" + format(pipeline) + " ms";
    }

    private static String researchReadme() {
        return "# Mobilny ALPR -- pakiet badawczy\n\n"
                + "Schemat: " + RESEARCH_SCHEMA + ". Archiwum zawiera raport, surowe ślady, "
                + "termikę 1 Hz, przepływ klatek w bucketach 1 s, zdarzenia track/MZ/konsensusu, "
                + "cropy z geometrią, adnotacje człowieka, aktywne modele i SHA-256 wpisów. "
                + "protocol.json opisuje metodykę inspirowaną MLPerf Mobile; wynik nie jest "
                + "oficjalnym wynikiem MLPerf. Exact match i CER są liczone wyłącznie dla "
                + "rekordów accepted/corrected z ground truth.\n";
    }

    private static String referencesBib() {
        return "@article{reddi2022mlperfmobile,\n"
                + "  title={MLPerf Mobile Inference Benchmark: An Industry-Standard Open-Source Machine Learning Benchmark for On-Device AI},\n"
                + "  author={Reddi, Vijay Janapa and others}, journal={Proceedings of Machine Learning and Systems}, volume={4}, pages={352--369}, year={2022}\n}\n\n"
                + "@inproceedings{laroca2018alpr, title={A Robust Real-Time Automatic License Plate Recognition Based on the YOLO Detector}, author={Laroca, Rayson and others}, booktitle={IJCNN}, year={2018}, doi={10.1109/IJCNN.2018.8489629}}\n\n"
                + "@inproceedings{shahab2011robust, title={ICDAR 2011 Robust Reading Competition Challenge 2}, author={Shahab, Asif and Shafait, Faisal and Dengel, Andreas}, booktitle={ICDAR}, year={2011}, doi={10.1109/ICDAR.2011.296}}\n\n"
                + "@inproceedings{mitchell2019modelcards, title={Model Cards for Model Reporting}, author={Mitchell, Margaret and others}, booktitle={FAT*}, pages={220--229}, year={2019}, doi={10.1145/3287560.3287596}}\n\n"
                + "@inproceedings{silva2018unconstrained, title={License Plate Detection and Recognition in Unconstrained Scenarios}, author={Silva, Sergio Montazzolli and Jung, Claudio Rosito}, booktitle={ECCV}, year={2018}, doi={10.1007/978-3-030-01258-8_36}}\n";
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return '"' + safe + '"';
    }

    private static String corners(CapturedPlateItem item) {
        StringBuilder value = new StringBuilder();
        for (com.example.alpr_v1.vision.Point2 point : item.plateGeometry.cornersNorm) {
            if (value.length() > 0) value.append(';');
            value.append(format(point.x)).append(':').append(format(point.y));
        }
        return value.toString();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f\\%%", value * 100.0);
    }

    private static String percentPlain(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100.0);
    }

    private static String shortHash(String value) {
        return value.length() <= 10 ? value : value.substring(0, 10);
    }

    private static String firstChecksum(JSONObject execution) {
        JSONObject checksums = execution.optJSONObject("sha256");
        if (checksums != null) {
            Iterator<String> keys = checksums.keys();
            if (keys.hasNext()) return checksums.optString(keys.next(), "--");
        }
        return execution.optString("fingerprint", "--");
    }

    private static String safeId(String value) {
        String safe = value == null ? "item" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isEmpty() ? "item" : safe;
    }

    private static final class ArchiveWriter implements AutoCloseable {
        private final ZipOutputStream zip;
        private final Map<String, String> hashes = new LinkedHashMap<>();

        ArchiveWriter(OutputStream output) {
            zip = new ZipOutputStream(output, StandardCharsets.UTF_8);
        }

        Map<String, String> hashes() { return new LinkedHashMap<>(hashes); }

        void writeText(String name, String text) throws IOException {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            MessageDigest digest = digest();
            digest.update(bytes);
            zip.putNextEntry(new ZipEntry(name));
            zip.write(bytes);
            zip.closeEntry();
            hashes.put(name, hex(digest.digest()));
        }

        void writeManifest(String text) throws IOException {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(text.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        void writeBitmap(String name, Bitmap bitmap) throws IOException {
            MessageDigest digest = digest();
            zip.putNextEntry(new ZipEntry(name));
            DigestSink sink = new DigestSink(zip, digest);
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, sink)) {
                throw new IOException("Nie udało się skompresować cropu " + name);
            }
            sink.flush();
            zip.closeEntry();
            hashes.put(name, hex(digest.digest()));
        }

        void writeFile(String name, File file) throws IOException {
            MessageDigest digest = digest();
            zip.putNextEntry(new ZipEntry(name));
            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream input = new FileInputStream(file)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    digest.update(buffer, 0, read);
                    zip.write(buffer, 0, read);
                }
            }
            zip.closeEntry();
            hashes.put(name, hex(digest.digest()));
        }

        void writeDirectory(File root, String prefix) throws IOException {
            List<File> files = new ArrayList<>();
            collect(root, files);
            files.sort(Comparator.comparing(File::getAbsolutePath));
            String rootPath = root.getAbsolutePath();
            for (File file : files) {
                String relative = file.getAbsolutePath().substring(rootPath.length())
                        .replace(File.separatorChar, '/');
                if (relative.startsWith("/")) relative = relative.substring(1);
                writeFile(prefix + relative, file);
            }
        }

        @Override
        public void close() throws IOException { zip.close(); }

        private static void collect(File value, List<File> destination) {
            if (value == null || !value.exists()) return;
            if (value.isFile()) {
                destination.add(value);
                return;
            }
            File[] children = value.listFiles();
            if (children == null) return;
            for (File child : children) collect(child, destination);
        }
    }

    private static final class DigestSink extends FilterOutputStream {
        private final MessageDigest digest;

        DigestSink(OutputStream output, MessageDigest digest) {
            super(output);
            this.digest = digest;
        }

        @Override
        public void write(int value) throws IOException {
            digest.update((byte) value);
            out.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            digest.update(bytes, offset, length);
            out.write(bytes, offset, length);
        }

        @Override
        public void close() throws IOException { flush(); }
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("Brak SHA-256", error);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format(Locale.ROOT, "%02x", item & 0xff));
        return value.toString();
    }
}
