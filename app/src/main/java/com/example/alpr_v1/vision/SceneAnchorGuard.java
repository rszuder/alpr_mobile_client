package com.example.alpr_v1.vision;

import android.graphics.Bitmap;
import com.example.alpr_v1.continuity.ObservationMask;


/**
 * Porównuje aktualny PreviewView nie z poprzednią klatką,
 * lecz ze stałą klatką referencyjną.
 *
 * Dzięki temu powolne przesuwanie kamery również
 * ostatecznie unieważni starą detekcję.
 */
public final class SceneAnchorGuard {

    private static final int GRID_X = 20;
    private static final int GRID_Y = 20;

    private static final float SAMPLE_DIFFERENCE_THRESHOLD =
            28f;

    private static final float SCORE_THRESHOLD =
            0.11f;

    private static final float CHANGED_FRACTION_THRESHOLD =
            0.30f;


    public static final class Result {

        public final boolean changed;
        public final float score;
        public final float changedFraction;


        private Result(
                boolean changed,
                float score,
                float changedFraction
        ) {
            this.changed = changed;
            this.score = score;
            this.changedFraction = changedFraction;
        }
    }


    private ObservationMask observationMask;
    private float[] anchorSamples;

    public synchronized void anchorLuma(byte[] gray,int width,int height,ObservationMask mask) {
        if(mask==null) throw new IllegalArgumentException("TV_OBSERVATION_MASK_REQUIRED");
        mask.requireSize(width,height);observationMask=mask;
        anchorSamples=mask.samples(gray,null);anchorWidth=width;anchorHeight=height;
    }
    public synchronized Result evaluateLuma(byte[] gray,int width,int height,ObservationMask mask) {
        if(mask==null) throw new IllegalArgumentException("TV_OBSERVATION_MASK_REQUIRED");
        mask.requireSize(width,height);
        if(!ObservationMask.same(observationMask,mask)||anchorSamples==null){anchorLuma(gray,width,height,mask);return new Result(false,0f,0f);}
        float[] current=mask.samples(gray,null);
        float a=mean(anchorSamples),b=mean(current),sum=0;int changed=0;
        for(int i=0;i<current.length;i++){float d=Math.abs((anchorSamples[i]-a)-(current[i]-b));sum+=d;if(d>=SAMPLE_DIFFERENCE_THRESHOLD)changed++;}
        float score=sum/(current.length*255f),fraction=changed/(float)current.length;
        return new Result(score>=SCORE_THRESHOLD&&fraction>=CHANGED_FRACTION_THRESHOLD,score,fraction);
    }


    private int anchorWidth;
    private int anchorHeight;


    public synchronized void anchor(
            Bitmap frame
    ) {
        observationMask=null;

        if (frame == null
                || frame.isRecycled()) {

            return;
        }


        anchorSamples =
                sampleLuminance(
                        frame
                );

        anchorWidth =
                frame.getWidth();

        anchorHeight =
                frame.getHeight();
    }


    public synchronized Result evaluate(
            Bitmap frame
    ) {
        if(observationMask!=null) reset();

        if (frame == null
                || frame.isRecycled()
                || anchorSamples == null) {

            return new Result(
                    false,
                    0f,
                    0f
            );
        }


        if (frame.getWidth() != anchorWidth
                || frame.getHeight() != anchorHeight) {

            /*
             * Zmiana wysokości PreviewView może wynikać wyłącznie z layoutu UI.
             * Nie unieważniamy wtedy sceny — przeliczamy kotwicę dla nowego
             * rozmiaru i kontynuujemy obserwację od następnej klatki.
             */
            anchorSamples =
                    sampleLuminance(
                            frame
                    );

            anchorWidth =
                    frame.getWidth();

            anchorHeight =
                    frame.getHeight();

            return new Result(
                    false,
                    0f,
                    0f
            );
        }


        float[] current =
                sampleLuminance(
                        frame
                );


        float anchorMean =
                mean(
                        anchorSamples
                );

        float currentMean =
                mean(
                        current
                );


        float differenceSum =
                0f;

        int changedSamples =
                0;


        for (int index = 0;
             index < current.length;
             index++) {

            /*
             * Kompensujemy zmianę globalnej jasności.
             */
            float anchored =
                    anchorSamples[index]
                            - anchorMean;

            float actual =
                    current[index]
                            - currentMean;


            float difference =
                    Math.abs(
                            anchored - actual
                    );


            differenceSum +=
                    difference;


            if (difference
                    >= SAMPLE_DIFFERENCE_THRESHOLD) {

                changedSamples++;
            }
        }


        float score =
                differenceSum
                        / (
                        current.length
                                * 255f
                );


        float fraction =
                changedSamples
                        / (float) current.length;


        boolean changed =
                score >= SCORE_THRESHOLD
                        && fraction
                        >= CHANGED_FRACTION_THRESHOLD;


        return new Result(
                changed,
                score,
                fraction
        );
    }


    public synchronized void reset() {

        observationMask = null;
        anchorSamples =
                null;

        anchorWidth =
                0;

        anchorHeight =
                0;
    }


    public synchronized boolean hasAnchor() {
        return anchorSamples != null;
    }


    private static float[] sampleLuminance(
            Bitmap frame
    ) {

        float[] samples =
                new float[
                        GRID_X * GRID_Y
                        ];


        int index =
                0;


        for (int gy = 0;
             gy < GRID_Y;
             gy++) {

            int y =
                    Math.min(
                            frame.getHeight() - 1,
                            Math.round(
                                    (gy + 0.5f)
                                            * frame.getHeight()
                                            / GRID_Y
                            )
                    );


            for (int gx = 0;
                 gx < GRID_X;
                 gx++) {

                int x =
                        Math.min(
                                frame.getWidth() - 1,
                                Math.round(
                                        (gx + 0.5f)
                                                * frame.getWidth()
                                                / GRID_X
                                )
                        );


                int pixel =
                        frame.getPixel(
                                x,
                                y
                        );


                int red =
                        (pixel >> 16) & 0xff;

                int green =
                        (pixel >> 8) & 0xff;

                int blue =
                        pixel & 0xff;


                samples[index++] =
                        0.2126f * red
                                + 0.7152f * green
                                + 0.0722f * blue;
            }
        }


        return samples;
    }


    private static float mean(
            float[] values
    ) {

        float sum =
                0f;


        for (float value :
                values) {

            sum +=
                    value;
        }


        return values.length == 0
                ? 0f
                : sum / values.length;
    }
}
