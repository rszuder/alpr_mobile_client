package com.example.alpr_v1.capture;

import android.content.Context;
import android.graphics.Bitmap;
import com.example.alpr_v1.pipeline.PlateObservation;
import com.example.alpr_v1.pipeline.ModelRuntimeSummary;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Durable normal crop sessions, independent of the bounded in-memory gallery. */
public final class CropSessionStore implements AutoCloseable {
    public static final class Summary {
        public final String id;
        public final long startedAtMillis;
        public final int crops, observations;
        Summary(JSONObject manifest) throws Exception {
            id = manifest.getString("session_id"); startedAtMillis = manifest.getLong("started_at_ms");
            JSONArray rows = manifest.getJSONArray("crops"); crops = rows.length();
            int count = 0;
            for (int i=0;i<rows.length();i++) count += rows.getJSONObject(i).getJSONArray("observations").length();
            observations = count;
        }
    }
    private final File root;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Set<String> imageQueued = new HashSet<>();
    private final Map<String, String> failures = new ConcurrentHashMap<>();
    private boolean closed;
    public CropSessionStore(Context context) { this(new File(context.getFilesDir(), "crop-sessions")); }
    public CropSessionStore(File root) { this.root = root; }

    public synchronized CompletableFuture<Void> record(String sessionId, PlateObservation observation,
            ObservationTelemetry telemetry, String source) {
        if (closed) return failed(new IllegalStateException("Session store closed"));
        if (sessionId == null || sessionId.isEmpty() || observation == null || !observation.hasFreshMzRead())
            return CompletableFuture.completedFuture(null);
        if (!validId(sessionId)) return failed(new IllegalArgumentException("Invalid crop session id"));
        RecognitionHistoryObservation record = new RecognitionHistoryObservation(observation, source, telemetry);
        String reservation = sessionId + "\n" + record.text;
        Bitmap image = null;
        if (!imageQueued.contains(reservation) && observation.previewBitmap != null && !observation.previewBitmap.isRecycled()) {
            image = observation.previewBitmap.copy(Bitmap.Config.ARGB_8888, false);
            if (image != null) imageQueued.add(reservation);
        }
        Bitmap ownedImage = image;
        return submit(() -> {
            try { append(sessionId, record, ownedImage, observation.characters); return null; }
            catch (Exception error) {
                synchronized (this) { imageQueued.remove(reservation); }
                failures.put(sessionId, error.toString());
                try { Files.write(new File(directory(sessionId), "incomplete.txt").toPath(),
                        error.toString().getBytes(StandardCharsets.UTF_8)); } catch (IOException ignored) { }
                throw error;
            } finally { if (ownedImage != null) ownedImage.recycle(); }
        });
    }

    public CompletableFuture<List<Summary>> sessions() {
        return submit(() -> {
            List<Summary> sessions = new ArrayList<>();
            File[] dirs = root.listFiles(File::isDirectory);
            if (dirs != null) for (File dir : dirs) {
                if (new File(dir,"session.json").isFile()) sessions.add(new Summary(load(dir, null)));
            }
            sessions.sort(Comparator.comparingLong((Summary summary) -> summary.startedAtMillis).reversed());
            return Collections.unmodifiableList(sessions);
        });
    }

    /** The single queue flushes all earlier observations before taking this export snapshot. */
    public CompletableFuture<Integer> export(String sessionId, Callable<OutputStream> destination) {
        return submit(() -> {
            if (failures.containsKey(sessionId)) throw new IOException("Nie zapisano wszystkich cropów sesji: " + failures.get(sessionId));
            File directory = directory(sessionId);
            if (new File(directory,"incomplete.txt").isFile()) throw new IOException("Sesja zawiera błąd zapisu cropów");
            JSONObject manifest = load(directory, null);
            JSONArray crops = manifest.getJSONArray("crops");
            if (crops.length() == 0) throw new IOException("Sesja nie zawiera cropów");
            // Validate the complete source before opening the destination.
            for (int i=0;i<crops.length();i++) {
                String name = crops.getJSONObject(i).getString("image");
                if (!name.matches("crop-[0-9]+\\.jpg") || !new File(directory,name).isFile())
                    throw new IOException("Brak obrazu cropa w sesji");
            }
            OutputStream output = destination.call();
            if (output == null) throw new IOException("Nie można otworzyć pliku docelowego");
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) {
                zip.putNextEntry(new ZipEntry("session.json"));
                zip.write(manifest.toString(2).getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
                for (int i=0;i<crops.length();i++) {
                    String name = crops.getJSONObject(i).getString("image");
                    zip.putNextEntry(new ZipEntry(name));
                    Files.copy(new File(directory,name).toPath(),zip); zip.closeEntry();
                }
            }
            return crops.length();
        });
    }

    private void append(String sessionId, RecognitionHistoryObservation observation, Bitmap image,
            List<com.example.alpr_v1.pipeline.PlateCharacter> characters) throws Exception {
        File directory = directory(sessionId); Files.createDirectories(directory.toPath());
        JSONObject manifest = load(directory, observation);
        JSONArray crops = manifest.getJSONArray("crops");
        JSONObject crop = null;
        for (int i=0;i<crops.length();i++) {
            JSONObject candidate = crops.getJSONObject(i);
            if (candidate.getString("text").equals(observation.text)) { crop = candidate; break; }
        }
        if (crop == null) {
            if (image == null) return; // Metadata alone cannot create a crop; it can extend an existing one.
            String name = String.format(Locale.ROOT,"crop-%06d.jpg",crops.length()+1);
            File temporary = new File(directory,name+".tmp");
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                if (!image.compress(Bitmap.CompressFormat.JPEG,94,output)) throw new IOException("Nie można zapisać obrazu");
                output.getFD().sync();
            }
            replace(temporary, new File(directory,name));
            JSONArray boxes = new JSONArray();
            for (com.example.alpr_v1.pipeline.PlateCharacter character : characters)
                boxes.put(new JSONObject().put("label",character.label).put("confidence",finite(character.confidence))
                        .put("left",character.left).put("top",character.top).put("right",character.right).put("bottom",character.bottom));
            crop = new JSONObject().put("text",observation.text).put("image",name).put("characters",boxes)
                    .put("image_width",image.getWidth()).put("image_height",image.getHeight())
                    .put("observations",new JSONArray());
            crops.put(crop);
        }
        JSONArray observations = crop.getJSONArray("observations");
        JSONObject evidence = metadata(observation);
        boolean duplicate = false;
        for (int i=0;i<observations.length();i++) {
            JSONObject previous = observations.getJSONObject(i);
            if (previous.getString("observation_id").equals(observation.key())) {
                if (previous.getLong("entity_id") == 0 && observation.entityId > 0) observations.put(i,evidence);
                else return;
                duplicate = true; break;
            }
        }
        if (!duplicate) observations.put(evidence);
        manifest.put("updated_at_ms",Math.max(manifest.optLong("updated_at_ms"),observation.capturedAtMillis));
        File temporary = new File(directory,"session.json.tmp");
        try(FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(manifest.toString().getBytes(StandardCharsets.UTF_8)); output.getFD().sync();
        }
        replace(temporary,new File(directory,"session.json"));
    }
    private JSONObject load(File directory, RecognitionHistoryObservation first) throws Exception {
        File manifest = new File(directory,"session.json");
        if (manifest.isFile()) return new JSONObject(new String(Files.readAllBytes(manifest.toPath()),StandardCharsets.UTF_8));
        if (first == null) throw new FileNotFoundException("Nie znaleziono sesji cropów");
        return new JSONObject().put("schema","alpr_crop_session_v1").put("session_id",directory.getName())
                .put("started_at_ms",first.capturedAtMillis).put("crops",new JSONArray());
    }
    private static JSONObject metadata(RecognitionHistoryObservation o) throws Exception {
        JSONObject result = new JSONObject().put("observation_id",o.key()).put("text",o.text)
                .put("entity_id",o.entityId).put("vehicle_track_id",o.vehicleTrackId).put("plate_track_id",o.plateTrackId)
                .put("scene_generation",o.sceneGeneration).put("visual_epoch",o.visualEpoch)
                .put("camera_transform_generation",o.cameraTransformGeneration).put("frame_id",o.frameId)
                .put("captured_at_ms",o.capturedAtMillis).put("captured_elapsed_ns",o.capturedElapsedNanos)
                .put("source",o.captureSource).put("source_width",o.sourceWidth).put("source_height",o.sourceHeight)
                .put("confidence",finite(o.confidence)).put("plate_confidence",finite(o.plateConfidence))
                .put("confirmed",o.confirmed).put("association_reason",o.associationReason)
                .put("timing",o.timing == null ? JSONObject.NULL : o.timing.toJson());
        ObservationTelemetry t = o.telemetry;
        if (t != null) {
            JSONArray models = new JSONArray();
            for(ModelRuntimeSummary model : t.models) models.put(new JSONObject().put("stage",model.stage)
                    .put("name",model.modelName).put("variant",model.variantId).put("precision",model.precision).put("backend",model.backend));
            result.put("hud",new JSONObject().put("fps",finite(t.cameraFps)).put("temperature_c",finite(t.batteryTemperatureC))
                    .put("cpu_percent",finite(t.cpuPercent)).put("sampled_at_ns",t.sampledAtNanos).put("models",models));
        }
        return result;
    }
    private static Object finite(double number) { return Double.isFinite(number) ? number : JSONObject.NULL; }
    private File directory(String id) {
        if (!validId(id)) throw new IllegalArgumentException("Invalid crop session id");
        return new File(root,id);
    }
    private static boolean validId(String id) { return id != null && id.matches("[A-Za-z0-9_-]{1,120}"); }
    private static void replace(File source, File target) throws IOException {
        try { Files.move(source.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(source.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING); }
    }
    private <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try { worker.execute(() -> { try { future.complete(task.call()); } catch (Exception error) { future.completeExceptionally(error); } }); }
        catch (RejectedExecutionException rejected) { future.completeExceptionally(rejected); }
        return future;
    }
    private static <T> CompletableFuture<T> failed(Exception error) { CompletableFuture<T> future = new CompletableFuture<>(); future.completeExceptionally(error); return future; }
    @Override public synchronized void close() { closed=true; worker.shutdown(); }
}
