package com.example.alpr_v1.experiment;

import android.util.JsonReader;
import android.util.JsonToken;
import com.example.alpr_v1.setup.SetupJson;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.ZipFile;

/** Delivery/test gate, not an APK inference gate. Supplements the native F6 graph validator
 * with the existing alpr.scene_evidence.v1 MP semantics, including streamed large scenes. */
public final class F6ProducerContractVerifier {
    private F6ProducerContractVerifier() {}

    public static String validate(File archive) throws Exception {
        String status = Final180ArchiveValidator.validate(archive);
        JSONObject summary = checkScenes(archive);
        receipt(archive, status, "native_f6_graph_and_scene_mp_contract", summary);
        return status;
    }

    static Set<String> keys(JSONObject object) {
        Set<String> result = new HashSet<>(); object.keys().forEachRemaining(result::add); return result;
    }

    static void receipt(File archive, String status, String scope, JSONObject summary) throws Exception {
        String target = System.getenv("F6_CONTRACT_RESULTS"); if (target == null) return;
        summary.put("archive", archive.getAbsolutePath()).put("sha256", com.example.alpr_v1.inference.RuntimeExecutionEvidence.sha256(archive))
                .put("bytes", archive.length()).put("status", status).put("scope", scope).put("synthetic", true).put("device_run", "NOT_RUN");
        File file = new File(target); Files.createDirectories(file.getAbsoluteFile().getParentFile().toPath());
        ResearchSessionStore.append(file, summary.toString() + "\n");
    }

    static void require(boolean ok, String reason) throws IOException {
        if (!ok) throw new IOException("F6_DELIVERY_INVALID: " + reason);
    }

    static long integer(Object value) throws IOException {
        require((value instanceof Integer || value instanceof Long) && ((Number)value).longValue() >= 0,
                "Invalid nonnegative int64: " + value);
        return ((Number)value).longValue();
    }

    static void stamp(JSONObject value) throws Exception {
        require(value != null && keys(value).equals(new HashSet<>(Arrays.asList(MzInputEvidence.STAMP))),
                "Invalid MP SourceStamp fields");
        TvBboxInputValidator.stamp(value);
    }

    static void mp(JSONObject value) throws Exception {
        Set<String> needed = new TreeSet<>(Arrays.asList("source_stamp", "trace_frame_id", "execution_status", "selected_rois"));
        String status = value.optString("execution_status");
        if ("EXECUTED".equals(status) || "NOT_RUN".equals(status))
            needed.addAll(Arrays.asList("raw_count", "rejected_class_count", "rejected_class_ids"));
        if ("NOT_RUN".equals(status)) needed.add("not_run_reason");
        needed.removeAll(keys(value));
        require(needed.isEmpty(), "MP_OBSERVATION_FIELDS_MISSING: " + String.join(",", needed));
        stamp(value.getJSONObject("source_stamp"));
        integer(value.get("trace_frame_id"));
        require(Arrays.asList("EXECUTED", "NOT_RUN", "FAILED").contains(status), "Invalid MP execution_status");
        if ("EXECUTED".equals(status)) {
            long raw = integer(value.get("raw_count")), rejected = integer(value.get("rejected_class_count"));
            JSONArray ids = value.getJSONArray("rejected_class_ids");
            require(ids.length() == rejected && rejected <= raw, "MP rejected population conflict");
            for (int n = 0; n < ids.length(); n++)
                require(ids.get(n) instanceof Integer || ids.get(n) instanceof Long, "Invalid rejected class ID");
        } else if ("NOT_RUN".equals(status)) {
            require(value.opt("not_run_reason") instanceof String && !value.getString("not_run_reason").isEmpty(),
                    "MP NOT_RUN needs a reason");
            for (String key : new String[]{"raw_count", "rejected_class_count", "rejected_class_ids"})
                require(value.get(key) == JSONObject.NULL, "MP NOT_RUN is not an executed empty population: " + key);
        }
        JSONArray rois = value.getJSONArray("selected_rois");
        for (int n = 0; n < rois.length(); n++) {
            JSONObject roi = rois.getJSONObject(n);
            for (String key : new String[]{"left", "top", "right", "bottom", "entity_id", "vehicle_track_id"})
                integer(roi.get(key));
            require(roi.getLong("right") > roi.getLong("left") && roi.getLong("bottom") > roi.getLong("top"),
                    "Invalid MP ROI");
        }
    }

    // Materialize one small MP object, never its scene's array or test padding.
    static Object value(JsonReader in) throws Exception {
        switch (in.peek()) {
            case BEGIN_OBJECT:
                JSONObject object = new JSONObject(); in.beginObject();
                while (in.hasNext()) { String name = in.nextName(); require(!object.has(name), "Duplicate JSON key"); object.put(name, value(in)); }
                in.endObject(); return object;
            case BEGIN_ARRAY:
                JSONArray array = new JSONArray(); in.beginArray();
                while (in.hasNext()) array.put(value(in));
                in.endArray(); return array;
            case NULL: in.nextNull(); return JSONObject.NULL;
            case BOOLEAN: return in.nextBoolean();
            case STRING: return in.nextString();
            case NUMBER:
                String number = in.nextString();
                return new JSONObject("{\"n\":" + number + "}").get("n");
            default: throw new IOException("Invalid JSON value");
        }
    }

    static void observation(ExternalJsonSort joins, String scene, JSONObject observation, String type) throws Exception {
        mp(observation);
        // The producer projects only observations with an actual scene identity. Source-only
        // observations remain in the lifecycle and are still checked against their frame below.
        if(scene!=null)joins.add("P" + scene + "/" + SetupJson.sha256(observation) + "\u0001" + type, new JSONObject());
    }
    static void lifecycleObservation(ExternalJsonSort joins,JSONObject event)throws Exception{
        JSONObject mp=event.getJSONObject("details").optJSONObject("mp");if(mp==null)return;
        String scene=event.isNull("scene_event_id")?null:event.getString("scene_event_id");
        observation(joins,scene,mp,"0");
        if(scene==null){
            if(!event.isNull("source_stamp"))Final180ArchiveValidator.sameStamp(event.getJSONObject("source_stamp"),mp.getJSONObject("source_stamp"));
            if(!event.isNull("trace_frame_id"))require(event.getLong("trace_frame_id")==mp.getLong("trace_frame_id"),"Source-only MP trace identity conflict");
            // SCENE_OBSERVATION envelopes intentionally omit frame/source IDs. The nested MP
            // snapshot carries the exact stamp; join it to the durable frame, never a clock guess.
            joins.add("B"+SetupJson.sha256(mp.getJSONObject("source_stamp"))+"\u00012",mp);
            if(!event.isNull("frame_key"))joins.add("F"+event.getString("frame_key")+"\u00012",mp);
        }
    }

    /** Also used on the existing R1/R2 synthetic engine fixtures (which are not F6/R0 sessions). */
    public static void validateScenes(File archive) throws Exception {
        receipt(archive, "PASS_SCENE_MP", "scene_mp_contract", checkScenes(archive));
    }

    static JSONObject checkScenes(File archive) throws Exception {
        try (ZipFile zip = new ZipFile(archive); ExternalJsonSort joins = new ExternalJsonSort(archive.getAbsoluteFile().getParentFile())) {
            JSONObject session = Final180ArchiveValidator.json(zip, "session.json");
            boolean f6 = Final180Evidence.enabled(session);
            boolean loss = f6 && !session.getJSONObject("f6_evidence").getBoolean("complete");
            String digest = f6 ? session.getJSONObject("tv_bbox_crop").getString("digest_sha256") : "";
            if (f6) {
                Final180ArchiveValidator.rows(zip, TvBboxEvidence.INDEX, row -> {
                    joins.add("F" + row.getString("frame_key") + "\u00010", row);
                    joins.add("B"+SetupJson.sha256(row.getJSONObject("source_stamp"))+"\u00010",row);
                });
                Final180ArchiveValidator.rows(zip, Final180Evidence.FILE,
                        loss && session.getJSONObject("f6_evidence").optLong("projection_error_count") > 0, event -> {
                    if ("FRAME_FINISHED".equals(event.optString("event"))){
                        joins.add("F" + event.getString("frame_key") + "\u00011", event);
                        joins.add("B"+SetupJson.sha256(event.getJSONObject("source_stamp"))+"\u00011",event);
                    }
                    if ("SCENE_OBSERVATION".equals(event.optString("event"))) lifecycleObservation(joins,event);
                });
            }
            long scenes = 0, written = 0; long[] mpCounts = {0, 0, 0};
            try (JsonReader in = new JsonReader(new InputStreamReader(zip.getInputStream(zip.getEntry(F5aEvidence.SCENES)), StandardCharsets.UTF_8))) {
                in.setLenient(true);
                while (in.peek() != JsonToken.END_DOCUMENT) {
                    JSONObject scene = new JSONObject(); boolean observations = false;
                    File spool = Files.createTempFile(archive.getAbsoluteFile().getParentFile().toPath(), "f6-mp-", ".jsonl").toFile();
                    try (BufferedWriter out = ExternalJsonSort.writer(spool)) {
                    in.beginObject();
                    while (in.hasNext()) {
                        String key = in.nextName();
                        if ("mp_observations".equals(key)) {
                            require(!observations, "Duplicate MP array"); observations = true;
                            in.beginArray();
                            while (in.hasNext()) {
                                JSONObject observation = (JSONObject)value(in); mp(observation);
                                mpCounts["NOT_RUN".equals(observation.getString("execution_status")) ? 0 : "EXECUTED".equals(observation.getString("execution_status")) ? 1 : 2]++;
                                out.write(observation.toString()); out.newLine();
                            }
                            in.endArray();
                        } else if ("_synthetic_test".equals(key)) {
                            // This additive scene-level extension is load-test metadata, never model diagnostics.
                            in.skipValue();
                        } else {
                            require(!scene.has(key), "Duplicate scene key"); scene.put(key, value(in));
                        }
                    }
                    in.endObject(); require(observations, "Missing mp_observations");
                    } catch (Exception error) { Files.deleteIfExists(spool.toPath()); throw error; }
                    try (BufferedReader observationsIn = Files.newBufferedReader(spool.toPath(), StandardCharsets.UTF_8)) {
                        String line;
                        while ((line = ExternalJsonSort.line(observationsIn)) != null) {
                            JSONObject observation = new JSONObject(line); String id = scene.getString("scene_event_id");
                            joins.add("S" + id + "\u00011", observation);
                            if (f6) {
                                observation(joins, id, observation, "1");
                                String frame = SetupJson.sha256(new JSONObject().put("source_stamp", observation.getJSONObject("source_stamp")).put("snapshot", digest));
                                joins.add("F" + frame + "\u00012", observation);
                            }
                        }
                    } finally { Files.deleteIfExists(spool.toPath()); }
                    require("alpr.scene_evidence.v1".equals(scene.getString("schema")) && session.getString("session_id").equals(scene.getString("session_id")), "Scene schema/session");
                    for (String key : new String[]{"source_stamp", "event_stamp"}) if (!scene.isNull(key)) stamp(scene.getJSONObject(key));
                    require("mt_source_bitmap_xy".equals(scene.getString("source_frame_coordinate_space"))
                            && "pipeline_bitmap_before_roi".equals(scene.getString("source_frame_origin")), "Scene coordinate system");
                    String status = scene.getString("write_status");
                    if ("WRITTEN".equals(status)) {
                        require(scene.get("missing_reason") == JSONObject.NULL, "Written scene has missing reason");
                        JSONObject image = new JSONObject().put("encoding", "JPEG94").put("exact_model_input", false)
                                .put("coordinate_space", "mt_source_bitmap_xy").put("status", status).put("entry", scene.get("source_frame_entry"))
                                .put("sha256", scene.get("source_frame_sha256")).put("width_px", scene.get("source_frame_width_px"))
                                .put("height_px", scene.get("source_frame_height_px"));
                        TvBboxInputValidator.image(zip, image, "mt_source_bitmap_xy", -1, -1); written++;
                    } else require("MISSING".equals(status) && scene.get("missing_reason") instanceof String
                            && !scene.getString("missing_reason").isEmpty() && scene.get("source_frame_entry") == JSONObject.NULL
                            && scene.get("source_frame_sha256") == JSONObject.NULL, "Missing scene image needs reason");
                    JSONArray calls = scene.getJSONArray("mt_invocation_ids"); String mt = scene.getString("mt_execution_status");
                    require(Arrays.asList("EXECUTED", "NOT_RUN", "UNKNOWN").contains(mt) && (calls.length() > 0) == "EXECUTED".equals(mt), "Scene MT status");
                    require("NOT_RUN".equals(mt) ? scene.get("mt_not_run_reason") instanceof String && !scene.getString("mt_not_run_reason").isEmpty()
                            : scene.get("mt_not_run_reason") == JSONObject.NULL, "Scene MT NOT_RUN reason");
                    joins.add("S" + scene.getString("scene_event_id") + "\u00010", scene); scenes++;
                }
            }
            require(scenes == session.getLong("scene_evidence_expected") && written == session.getLong("scene_evidence_written")
                    && scenes - written == session.getLong("scene_evidence_missing"), "Scene summary");
            class Group {
                String id = ""; JSONObject base, finished; long raw, projected;
                void flush() throws Exception {
                    if (id.startsWith("P")) require(raw == projected, "MP projection differs from durable observation journal");
                }
                void accept(String key, JSONObject row) throws Exception {
                    int split = key.indexOf('\u0001'); String next = key.substring(0, split); char type = key.charAt(split + 1);
                    if (!id.equals(next)) { flush(); id = next; base = finished = null; raw = projected = 0; }
                    if (id.startsWith("P")) { if (type == '0') raw++; else projected++; return; }
                    if (type == '0') { require(base == null, "Duplicate source identity"); base = row; return; }
                    if (id.startsWith("F")||id.startsWith("B")) {
                        if (type == '1') { finished = row; return; }
                        if(id.startsWith("B"))require(base!=null,"Source-only MP has no exact source frame");
                        require(base != null || loss, "MP source frame missing without loss qualification");
                        if (base != null) Final180ArchiveValidator.sameStamp(base.getJSONObject("source_stamp"), row.getJSONObject("source_stamp"));
                        if (finished != null) require(Objects.equals(finished.opt("trace_frame_id"), row.get("trace_frame_id")), "MP trace/frame identity conflict");
                        else require(loss, "MP source frame has no completion record");
                    } else {
                        require(base != null, "MP has no scene");
                        if ("WRITTEN".equals(base.optString("write_status")) && SetupJson.canonical(base.opt("source_stamp")).equals(SetupJson.canonical(row.opt("source_stamp")))) {
                            JSONArray rois = row.getJSONArray("selected_rois");
                            for (int n = 0; n < rois.length(); n++) {
                                JSONObject roi = rois.getJSONObject(n);
                                require(roi.getLong("right") <= base.getLong("source_frame_width_px") && roi.getLong("bottom") <= base.getLong("source_frame_height_px"), "MP ROI outside source bitmap");
                            }
                        }
                    }
                }
            }
            Group group = new Group(); joins.each(group::accept); group.flush();
            return new JSONObject().put("scenes", scenes).put("scene_images_written", written)
                    .put("mp_not_run", mpCounts[0]).put("mp_executed", mpCounts[1]).put("mp_failed", mpCounts[2]);
        }
    }
}
