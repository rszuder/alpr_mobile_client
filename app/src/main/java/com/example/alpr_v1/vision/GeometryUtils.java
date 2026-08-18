package com.example.alpr_v1.vision;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GeometryUtils {
    private GeometryUtils() {}

    /** Zwraca TL, TR, BR, BL, zgodnie z implementacją programu Python. */
    public static List<Point2> orderQuad(List<Point2> points) {
        if (points == null || points.size() != 4) {
            throw new IllegalArgumentException("Rektyfikacja wymaga dokładnie czterech punktów");
        }
        List<Point2> byX = new ArrayList<>(points);
        byX.sort(Comparator.comparingDouble(point -> point.x));
        List<Point2> left = new ArrayList<>(byX.subList(0, 2));
        List<Point2> right = new ArrayList<>(byX.subList(2, 4));
        left.sort(Comparator.comparingDouble(point -> point.y));
        right.sort(Comparator.comparingDouble(point -> point.y));
        List<Point2> ordered = new ArrayList<>(4);
        ordered.add(left.get(0));
        ordered.add(right.get(0));
        ordered.add(right.get(1));
        ordered.add(left.get(1));
        return ordered;
    }

    public static float estimatedWidth(List<Point2> ordered) {
        return distance(ordered.get(0), ordered.get(1));
    }

    public static float estimatedHeight(List<Point2> ordered) {
        return distance(ordered.get(0), ordered.get(3));
    }

    private static float distance(Point2 a, Point2 b) {
        return (float) Math.hypot(a.x - b.x, a.y - b.y);
    }
}
