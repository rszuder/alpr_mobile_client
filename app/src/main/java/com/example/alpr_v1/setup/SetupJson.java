package com.example.alpr_v1.setup;

import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Canonical bytes for optional setup evidence. Never used by inference or samples-v2. */
public final class SetupJson {
    private SetupJson() {}

    public static String canonical(Object value) {
        if (value == null || value == JSONObject.NULL) return "null";
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            List<String> keys = new ArrayList<>();
            object.keys().forEachRemaining(keys::add);
            Collections.sort(keys);
            StringBuilder out = new StringBuilder("{");
            for (String key : keys) {
                if (out.length() > 1) out.append(',');
                out.append(JSONObject.quote(key)).append(':').append(canonical(object.opt(key)));
            }
            return out.append('}').toString();
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) out.append(',');
                out.append(canonical(array.opt(i)));
            }
            return out.append(']').toString();
        }
        if (value instanceof Number) {
            if (!Double.isFinite(((Number) value).doubleValue()))
                throw new IllegalArgumentException("Non-finite setup value");
            return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Boolean) return value.toString();
        return JSONObject.quote(value.toString());
    }

    public static String sha256(JSONObject object) {
        try {
            JSONObject payload = new JSONObject(object.toString());
            payload.remove("digest_sha256");
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical(payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (Exception e) { throw new IllegalArgumentException("Invalid setup JSON", e); }
    }

    public static JSONObject seal(JSONObject object) {
        try { object.put("digest_sha256", sha256(object)); return object; }
        catch (org.json.JSONException e) { throw new IllegalArgumentException(e); }
    }

    public static boolean verified(JSONObject object) {
        try { return object != null && sha256(object).equals(object.optString("digest_sha256")); }
        catch (RuntimeException e) { return false; }
    }

    public static JSONArray array(double[] values) {
        JSONArray result = new JSONArray();
        for (double value : values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite geometry");
            result.put(Double.valueOf(value));
        }
        return result;
    }
}
