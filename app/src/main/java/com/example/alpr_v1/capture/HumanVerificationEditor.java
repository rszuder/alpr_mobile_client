package com.example.alpr_v1.capture;

import java.util.Locale;

public final class HumanVerificationEditor {
    private HumanVerificationEditor() {}

    public static void applyStatus(
            CapturedPlateItem item,
            CapturedPlateItem.VerificationStatus status,
            String correctedText,
            long nowMillis
    ) {
        if (item == null) throw new IllegalArgumentException("item");
        CapturedPlateItem.VerificationStatus safeStatus = status == null
                ? CapturedPlateItem.VerificationStatus.NOT_REVIEWED : status;
        item.verificationStatus = safeStatus;
        switch (safeStatus) {
            case ACCEPTED:
                item.groundTruthText = item.text;
                item.verifiedAtMillis = Math.max(0L, nowMillis);
                break;
            case CORRECTED:
                item.groundTruthText = correctedText == null
                        ? "" : correctedText.trim().toUpperCase(Locale.ROOT);
                item.verifiedAtMillis = Math.max(0L, nowMillis);
                break;
            case REJECTED:
                item.groundTruthText = "";
                item.verifiedAtMillis = Math.max(0L, nowMillis);
                break;
            case NOT_REVIEWED:
            default:
                item.groundTruthText = "";
                item.verifiedAtMillis = 0L;
                break;
        }
        item.verificationRevision++;
    }

    public static void touchMetadata(CapturedPlateItem item, long nowMillis) {
        if (item == null) throw new IllegalArgumentException("item");
        item.verificationRevision++;
        item.verifiedAtMillis = Math.max(0L, nowMillis);
    }
}
