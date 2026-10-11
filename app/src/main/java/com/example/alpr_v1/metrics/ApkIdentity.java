package com.example.alpr_v1.metrics;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import com.example.alpr_v1.inference.RuntimeExecutionEvidence;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Hash the installed base APK and split APKs before START, never a compile-time placeholder. */
public final class ApkIdentity {
    private ApkIdentity() { }
    public static JSONObject capture(Context context) throws Exception {
        ApplicationInfo info = context.getApplicationInfo();
        List<File> splits = new ArrayList<>();
        if (info.splitSourceDirs != null) for (String path : info.splitSourceDirs) splits.add(new File(path));
        return captureFiles(new File(info.sourceDir), splits);
    }
    public static JSONObject captureFiles(File base, List<File> splits) throws Exception {
        List<File> ordered = new ArrayList<>(splits); ordered.sort(Comparator.comparing(File::getName));
        ordered.add(0, base);
        JSONArray files = new JSONArray(); StringBuilder fingerprint = new StringBuilder();
        String baseSha = "";
        for (int i = 0; i < ordered.size(); i++) {
            File file = ordered.get(i);
            if (!file.isFile()) throw new java.io.IOException("APK_FILE_UNAVAILABLE: " + file);
            String sha = RuntimeExecutionEvidence.sha256(file);
            if (i == 0) baseSha = sha;
            String role = i == 0 ? "base" : "split";
            files.put(new JSONObject().put("kind", role).put("name", file.getName())
                    .put("sha256", sha).put("size_bytes", file.length()));
            fingerprint.append(role).append(':').append(file.getName()).append(':').append(sha).append('\n');
        }
        return new JSONObject().put("schema", "alpr.installed_apk_identity.v1").put("status", "OBSERVED_FILE_BYTES")
                .put("base_apk_sha256", baseSha).put("files", files)
                .put("apk_set_fingerprint", RuntimeExecutionEvidence.sha256(ByteBuffer.wrap(fingerprint.toString().getBytes(StandardCharsets.UTF_8))));
    }
}
