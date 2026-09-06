package com.example.alpr_v1.capture;

public enum VerificationIssue {
    PLATE_NOT_VISIBLE("plate_not_visible", false),
    MT_REGION("mt_region", true),
    MT_CORNERS("mt_corners", true),
    RECTIFICATION("rectification", true),
    MZ_MISSING_CHARACTER("mz_missing_character", false),
    MZ_WRONG_CLASS("mz_wrong_class", false),
    BLUR("blur", false),
    OCCLUSION("occlusion", false),
    VEHICLE_ASSOCIATION("vehicle_association", true),
    OTHER("other", false);

    private final String wireName;
    private final boolean desktopReviewRecommended;

    VerificationIssue(String wireName, boolean desktopReviewRecommended) {
        this.wireName = wireName;
        this.desktopReviewRecommended = desktopReviewRecommended;
    }

    public String wireName() {
        return wireName;
    }

    public boolean desktopReviewRecommended() {
        return desktopReviewRecommended;
    }

    public static VerificationIssue fromWireName(String value) {
        if (value != null) {
            for (VerificationIssue issue : values()) {
                if (issue.wireName.equals(value)) return issue;
            }
        }
        return null;
    }
}
