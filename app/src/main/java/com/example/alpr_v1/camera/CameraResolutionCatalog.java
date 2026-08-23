package com.example.alpr_v1.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CameraResolutionCatalog {

    private final String cameraId;
    private final List<Size> resolutions;
    private final List<Size> highResolutions;

    public CameraResolutionCatalog(
            Context context
    ) {
        Result result =
                readCapabilities(
                        context.getApplicationContext()
                );

        cameraId = result.cameraId;

        resolutions =
                Collections.unmodifiableList(
                        result.resolutions
                );

        highResolutions =
                Collections.unmodifiableList(
                        result.highResolutions
                );
    }


    public String cameraId() {
        return cameraId;
    }


    /**
     * Wszystkie rozdzielczości YUV_420_888:
     *
     * - standardowe,
     * - wysokiej rozdzielczości.
     *
     * Posortowane rosnąco według liczby pikseli.
     */
    public List<Size> resolutions() {
        return new ArrayList<>(resolutions);
    }


    public int regularCount() {
        int count = 0;

        for (Size size : resolutions) {
            if (!isHighResolution(size)) {
                count++;
            }
        }

        return count;
    }


    public int highResolutionCount() {
        return highResolutions.size();
    }


    public boolean isHighResolution(
            Size size
    ) {
        if (size == null) {
            return false;
        }

        for (Size candidate : highResolutions) {
            if (sameSize(candidate, size)) {
                return true;
            }
        }

        return false;
    }


    public boolean contains(
            Size size
    ) {
        if (size == null) {
            return false;
        }

        for (Size candidate : resolutions) {
            if (sameSize(candidate, size)) {
                return true;
            }
        }

        return false;
    }


    public Size find(
            int width,
            int height
    ) {
        for (Size size : resolutions) {
            if (size.getWidth() == width
                    && size.getHeight() == height) {
                return size;
            }
        }

        return null;
    }


    /**
     * Wybór dla AUTO.
     *
     * Preferujemy standardowe rozdzielczości, ponieważ AUTO
     * ma zachowywać rozsądną wydajność.
     *
     * High-resolution pozostaje świadomym wyborem użytkownika.
     */
    public Size closestRegularTo(
            Size target
    ) {
        List<Size> regular =
                new ArrayList<>();

        for (Size size : resolutions) {
            if (!isHighResolution(size)) {
                regular.add(size);
            }
        }

        if (regular.isEmpty()) {
            return closestTo(
                    resolutions,
                    target
            );
        }

        return closestTo(
                regular,
                target
        );
    }


    public String label(
            Size size
    ) {
        if (size == null) {
            return "";
        }

        long pixels =
                (long) size.getWidth()
                        * size.getHeight();

        double megapixels =
                pixels / 1_000_000.0;

        String ratio =
                aspectRatioLabel(
                        size
                );

        String base =
                String.format(
                        Locale.getDefault(),
                        "%d × %d · %s · %.1f MP",
                        size.getWidth(),
                        size.getHeight(),
                        ratio,
                        megapixels
                );

        if (isHighResolution(size)) {
            return base
                    + " · wysoka / niższy FPS";
        }

        return base;
    }


    public static String wireName(
            Size size
    ) {
        if (size == null) {
            return "";
        }

        return size.getWidth()
                + "x"
                + size.getHeight();
    }


    private static Size closestTo(
            List<Size> candidates,
            Size target
    ) {
        if (candidates == null
                || candidates.isEmpty()) {
            return null;
        }

        if (target == null) {
            return candidates.get(0);
        }

        Size bestSameRatio = null;
        long bestSameRatioDifference =
                Long.MAX_VALUE;

        Size bestAny = null;
        long bestAnyDifference =
                Long.MAX_VALUE;

        long targetPixels =
                pixels(target);

        for (Size candidate : candidates) {

            long difference =
                    Math.abs(
                            pixels(candidate)
                                    - targetPixels
                    );

            if (difference
                    < bestAnyDifference) {

                bestAnyDifference =
                        difference;

                bestAny =
                        candidate;
            }

            if (sameAspectRatio(
                    candidate,
                    target
            )) {

                if (difference
                        < bestSameRatioDifference) {

                    bestSameRatioDifference =
                            difference;

                    bestSameRatio =
                            candidate;
                }
            }
        }

        return bestSameRatio != null
                ? bestSameRatio
                : bestAny;
    }


    private static Result readCapabilities(
            Context context
    ) {
        CameraManager manager =
                (CameraManager)
                        context.getSystemService(
                                Context.CAMERA_SERVICE
                        );

        if (manager == null) {
            return Result.empty();
        }

        try {
            String backCameraId =
                    findBackCameraId(
                            manager
                    );

            if (backCameraId == null) {
                return Result.empty();
            }

            CameraCharacteristics characteristics =
                    manager.getCameraCharacteristics(
                            backCameraId
                    );

            StreamConfigurationMap map =
                    characteristics.get(
                            CameraCharacteristics
                                    .SCALER_STREAM_CONFIGURATION_MAP
                    );

            if (map == null) {
                return Result.empty();
            }

            Map<String, Size> regularMap =
                    new LinkedHashMap<>();

            Size[] regular =
                    map.getOutputSizes(
                            ImageFormat.YUV_420_888
                    );

            addSizes(
                    regularMap,
                    regular
            );


            Map<String, Size> highMap =
                    new LinkedHashMap<>();

            try {
                Size[] high =
                        map.getHighResolutionOutputSizes(
                                ImageFormat.YUV_420_888
                        );

                addSizes(
                        highMap,
                        high
                );

            } catch (RuntimeException ignored) {
                /*
                 * Część urządzeń nie udostępnia osobnej
                 * puli high-resolution.
                 */
            }


            /*
             * Jeżeli rozdzielczość występuje także w zwykłej
             * puli, traktujemy ją jako standardową.
             */
            for (String regularKey :
                    regularMap.keySet()) {

                highMap.remove(
                        regularKey
                );
            }


            Map<String, Size> all =
                    new LinkedHashMap<>();

            all.putAll(
                    regularMap
            );

            all.putAll(
                    highMap
            );


            List<Size> allSizes =
                    new ArrayList<>(
                            all.values()
                    );

            List<Size> highSizes =
                    new ArrayList<>(
                            highMap.values()
                    );


            Comparator<Size> comparator =
                    (left, right) -> {

                        int pixelsCompare =
                                Long.compare(
                                        pixels(left),
                                        pixels(right)
                                );

                        if (pixelsCompare != 0) {
                            return pixelsCompare;
                        }

                        int widthCompare =
                                Integer.compare(
                                        left.getWidth(),
                                        right.getWidth()
                                );

                        if (widthCompare != 0) {
                            return widthCompare;
                        }

                        return Integer.compare(
                                left.getHeight(),
                                right.getHeight()
                        );
                    };


            allSizes.sort(
                    comparator
            );

            highSizes.sort(
                    comparator
            );


            return new Result(
                    backCameraId,
                    allSizes,
                    highSizes
            );

        } catch (CameraAccessException
                 | SecurityException error) {

            return Result.empty();
        }
    }


    private static String findBackCameraId(
            CameraManager manager
    ) throws CameraAccessException {

        String firstBackCamera =
                null;

        String logicalBackCamera =
                null;

        for (String id :
                manager.getCameraIdList()) {

            CameraCharacteristics characteristics =
                    manager.getCameraCharacteristics(
                            id
                    );

            Integer facing =
                    characteristics.get(
                            CameraCharacteristics.LENS_FACING
                    );

            if (facing == null
                    || facing
                    != CameraCharacteristics.LENS_FACING_BACK) {
                continue;
            }

            if (firstBackCamera == null) {
                firstBackCamera =
                        id;
            }


            int[] capabilities =
                    characteristics.get(
                            CameraCharacteristics
                                    .REQUEST_AVAILABLE_CAPABILITIES
                    );

            if (capabilities == null) {
                continue;
            }

            for (int capability :
                    capabilities) {

                if (capability
                        == CameraMetadata
                        .REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) {

                    logicalBackCamera =
                            id;

                    break;
                }
            }

            if (logicalBackCamera != null) {
                break;
            }
        }

        return logicalBackCamera != null
                ? logicalBackCamera
                : firstBackCamera;
    }


    private static void addSizes(
            Map<String, Size> destination,
            Size[] sizes
    ) {
        if (sizes == null) {
            return;
        }

        for (Size size : sizes) {
            if (size == null
                    || size.getWidth() <= 0
                    || size.getHeight() <= 0) {
                continue;
            }

            destination.put(
                    wireName(size),
                    size
            );
        }
    }


    private static boolean sameSize(
            Size left,
            Size right
    ) {
        return left.getWidth()
                == right.getWidth()
                && left.getHeight()
                == right.getHeight();
    }


    private static boolean sameAspectRatio(
            Size left,
            Size right
    ) {
        return (long) left.getWidth()
                * right.getHeight()
                == (long) right.getWidth()
                * left.getHeight();
    }


    private static long pixels(
            Size size
    ) {
        return (long) size.getWidth()
                * size.getHeight();
    }


    private static String aspectRatioLabel(
            Size size
    ) {
        int divisor =
                gcd(
                        size.getWidth(),
                        size.getHeight()
                );

        return (size.getWidth() / divisor)
                + ":"
                + (size.getHeight() / divisor);
    }


    private static int gcd(
            int a,
            int b
    ) {
        a = Math.abs(a);
        b = Math.abs(b);

        while (b != 0) {
            int temporary =
                    a % b;

            a = b;
            b = temporary;
        }

        return Math.max(
                1,
                a
        );
    }


    private static final class Result {

        final String cameraId;

        final List<Size> resolutions;

        final List<Size> highResolutions;


        Result(
                String cameraId,
                List<Size> resolutions,
                List<Size> highResolutions
        ) {
            this.cameraId =
                    cameraId == null
                            ? ""
                            : cameraId;

            this.resolutions =
                    resolutions;

            this.highResolutions =
                    highResolutions;
        }


        static Result empty() {
            return new Result(
                    "",
                    new ArrayList<>(),
                    new ArrayList<>()
            );
        }
    }
}
