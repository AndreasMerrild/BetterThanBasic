package Server;

import Fit.FitActivity;
import Fit.FitLap;
import Fit.FitRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The data for a ride's graphs, ready to draw: power (3 s moving average, like the console app's
 * graph), heart rate, speed and cadence, each reduced to at most one point per pixel column, with
 * its average and maximum. Series without any data are left out.
 */
final class RideGraph {
    static final int MIN_POINTS = 50;
    static final int MAX_POINTS = 4000;
    private static final int POWER_SMOOTHING_SECONDS = 3;

    private RideGraph() {
    }

    /** One measure: how to read it from a record and how to round it. */
    private record Measure(String key, String unit, int digits, Function<FitRecord, Double> read) {
    }

    private static final List<Measure> MEASURES = List.of(
            new Measure("power", "W", 0, r -> r.power() == null ? null : r.power().doubleValue()),
            new Measure("hr", "bpm", 0, r -> r.heartRate() == null ? null : r.heartRate().doubleValue()),
            new Measure("speed", "km/h", 1, r -> r.speed() == null ? null : r.speed() * 3.6),
            new Measure("cadence", "rpm", 0, r -> r.cadence() == null ? null : r.cadence().doubleValue()));

    /**
     * @param points how many points to return per series (usually the graph's width in pixels)
     */
    static Map<String, Object> build(FitActivity activity, int points) {
        List<FitRecord> records = activity.records().stream()
                .filter(record -> record.timestamp() != null)
                .sorted(Comparator.comparing(FitRecord::timestamp))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        if (records.isEmpty()) {
            result.put("durationSeconds", 0);
            result.put("t", List.of());
            result.put("lapStarts", List.of());
            result.put("series", List.of());
            return result;
        }

        Instant first = records.get(0).timestamp();
        double[] seconds = new double[records.size()];
        for (int i = 0; i < records.size(); i++) {
            seconds[i] = Duration.between(first, records.get(i).timestamp()).toMillis() / 1000.0;
        }
        double total = Math.max(seconds[seconds.length - 1], 1);
        int count = Math.max(MIN_POINTS, Math.min(points, MAX_POINTS));
        count = Math.min(count, Math.max(2, records.size()));

        // Point i covers the time from i/count to (i+1)/count of the ride; t is its middle.
        List<Double> times = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            times.add((i + 0.5) * total / count);
        }
        int[] bucket = new int[records.size()];
        for (int i = 0; i < records.size(); i++) {
            bucket[i] = Math.min(count - 1, (int) (seconds[i] / total * count));
        }

        List<Map<String, Object>> series = new ArrayList<>();
        for (Measure measure : MEASURES) {
            Double[] raw = new Double[records.size()];
            for (int i = 0; i < records.size(); i++) {
                raw[i] = measure.read().apply(records.get(i));
            }
            boolean anyPositive = Arrays.stream(raw).anyMatch(v -> v != null && v > 0);
            if (!anyPositive) {
                continue; // nothing recorded for this measure (e.g. no power meter)
            }
            Double[] shown = measure.key().equals("power") ? smooth(seconds, raw, POWER_SMOOTHING_SECONDS) : raw;
            series.add(series(measure, raw, shown, bucket, count));
        }

        List<Double> lapStarts = new ArrayList<>();
        for (FitLap lap : activity.laps()) {
            if (lap.startTime() != null) {
                double at = Duration.between(first, lap.startTime()).toMillis() / 1000.0;
                if (at > 0 && at < total) {
                    lapStarts.add(at);
                }
            }
        }

        result.put("durationSeconds", total);
        result.put("t", times);
        result.put("lapStarts", lapStarts);
        result.put("series", series);
        return result;
    }

    private static Map<String, Object> series(Measure measure, Double[] raw, Double[] shown, int[] bucket, int count) {
        double[] sum = new double[count];
        int[] samples = new int[count];
        for (int i = 0; i < shown.length; i++) {
            if (shown[i] != null) {
                sum[bucket[i]] += shown[i];
                samples[bucket[i]]++;
            }
        }
        List<Double> values = new ArrayList<>(count);
        double shownMin = Double.POSITIVE_INFINITY;
        double shownMax = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            Double value = samples[i] == 0 ? null : round(sum[i] / samples[i], measure.digits());
            values.add(value); // null = nothing recorded there: the line breaks
            if (value != null) {
                shownMin = Math.min(shownMin, value);
                shownMax = Math.max(shownMax, value);
            }
        }

        // Average over every recorded sample (zeros included), matching the training summary;
        // maximum and minimum over the samples that were actually above zero.
        double total = 0;
        int recorded = 0;
        double max = Double.NEGATIVE_INFINITY;
        double min = Double.POSITIVE_INFINITY;
        for (Double value : raw) {
            if (value != null) {
                total += value;
                recorded++;
                if (value > 0) {
                    max = Math.max(max, value);
                    min = Math.min(min, value);
                }
            }
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", measure.key());
        map.put("unit", measure.unit());
        map.put("digits", measure.digits());
        map.put("average", round(total / recorded, measure.digits()));
        map.put("max", round(max, measure.digits()));
        map.put("min", round(min, measure.digits()));
        map.put("shownMin", shownMin);   // range of the drawn line, for the graph's scale
        map.put("shownMax", shownMax);
        map.put("values", values);
        return map;
    }

    /** Trailing moving average over {@code windowSeconds}. */
    private static Double[] smooth(double[] seconds, Double[] values, int windowSeconds) {
        Double[] out = new Double[values.length];
        int start = 0;
        double sum = 0;
        int count = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                sum += values[i];
                count++;
            }
            while (seconds[i] - seconds[start] >= windowSeconds) {
                if (values[start] != null) {
                    sum -= values[start];
                    count--;
                }
                start++;
            }
            out[i] = values[i] == null || count == 0 ? null : sum / count;
        }
        return out;
    }

    private static double round(double value, int digits) {
        double factor = Math.pow(10, digits);
        return Math.round(value * factor) / factor;
    }
}
