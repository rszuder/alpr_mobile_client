package com.example.alpr_v1.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Statistics {
    private Statistics() {}

    public static Summary summarize(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return new Summary(0, 0, 0, 0, 0, 0, 0, 0);
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        double sum = 0.0;
        for (double value : sorted) sum += value;
        double mean = sum / sorted.size();
        double variance = 0.0;
        for (double value : sorted) {
            double delta = value - mean;
            variance += delta * delta;
        }
        variance /= sorted.size();
        return new Summary(
                sorted.size(),
                mean,
                percentile(sorted, 50),
                percentile(sorted, 90),
                percentile(sorted, 95),
                percentile(sorted, 99),
                sorted.get(0),
                sorted.get(sorted.size() - 1),
                Math.sqrt(variance)
        );
    }

    static double percentile(List<Double> sorted, double percentile) {
        if (sorted.isEmpty()) return 0.0;
        if (sorted.size() == 1) return sorted.get(0);
        double index = (percentile / 100.0) * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) return sorted.get(lower);
        double fraction = index - lower;
        return sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * fraction;
    }

    public static final class Summary {
        public final int count;
        public final double mean;
        public final double median;
        public final double p90;
        public final double p95;
        public final double p99;
        public final double min;
        public final double max;
        public final double standardDeviation;

        Summary(
                int count,
                double mean,
                double median,
                double p90,
                double p95,
                double p99,
                double min,
                double max
        ) {
            this(count, mean, median, p90, p95, p99, min, max, 0.0);
        }

        Summary(
                int count,
                double mean,
                double median,
                double p90,
                double p95,
                double p99,
                double min,
                double max,
                double standardDeviation
        ) {
            this.count = count;
            this.mean = mean;
            this.median = median;
            this.p90 = p90;
            this.p95 = p95;
            this.p99 = p99;
            this.min = min;
            this.max = max;
            this.standardDeviation = standardDeviation;
        }
    }
}
