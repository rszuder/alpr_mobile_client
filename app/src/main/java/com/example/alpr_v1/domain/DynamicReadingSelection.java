package com.example.alpr_v1.domain;

/** Presentation policy for ordinary dynamic mode; scores are uncalibrated model scores. */
public final class DynamicReadingSelection {
    private DynamicReadingSelection() {}
    /** Confirmation classification only; text visibility does not use this threshold. */
    public static final double BADGE_MIN_CONFIDENCE=.60;

    public static boolean badgeEligible(PlateTextConsensus reading) {
        return reading!=null&&reading.available();
    }
    public static boolean confirmedForDisplay(boolean confirmed,double score,int observations) {
        return confirmed&&observations>=2&&Double.isFinite(score)&&score>=BADGE_MIN_CONFIDENCE;
    }

    /** Keep an actual best aggregate, without manufacturing its confidence or confirmation. */
    public static boolean improves(double score,int observations,double previousScore,int previousObservations,
            boolean previousAvailable) {
        return Double.isFinite(score)&&score>=0&&(!previousAvailable||score>previousScore
                ||score==previousScore&&observations>previousObservations);
    }
}
