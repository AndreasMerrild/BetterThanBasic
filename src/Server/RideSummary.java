package Server;

import Application.BestPowerNumbers;
import Application.MultiFileUsage;
import Fit.FitActivity;
import Fit.FitLap;
import Fit.Json;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * One ride's numbers, worked out once from its records and saved in the database (as JSON) so the
 * training list never needs every recorded second in memory.
 *
 * @param id         the ride's start time in epoch ms: the id used in links (-1 = no start time)
 * @param bestPower  best average power (W) per duration in seconds; empty without power data
 */
record RideSummary(
        long id,
        String device,
        Long start,
        long durationSeconds,
        double distanceKm,
        Double avgSpeedKmh,
        Double avgPower,
        Double normalizedPower,
        Double avgHeartRate,
        Integer maxHeartRate,
        Double avgCadence,
        Integer ftp,
        Double intensityFactor,
        Double tss,
        int laps,
        Map<Integer, Double> bestPower) {

    RideSummary {
        bestPower = Map.copyOf(bestPower);
    }

    static RideSummary of(FitActivity activity) {
        Long start = activity.startTime() == null ? null : activity.startTime().toEpochMilli();
        return new RideSummary(
                start == null ? -1 : start,
                activity.deviceName(),
                start,
                activity.duration().toSeconds(),
                distanceKilometers(activity),
                activity.averageSpeed(),
                activity.averagePower(),
                positive(activity.normalizedPower()),
                activity.averageHeartRate(),
                activity.maxHeartRate(),
                activity.averageCadence(),
                activity.functionalThresholdPower(),
                positive(activity.intensityFactor()),
                positive(activity.TSS()),
                activity.laps().size(),
                BestPowerNumbers.bestPowerCurve(activity));
    }

    boolean hasPower() {
        return !bestPower.isEmpty();
    }

    /** The JSON object sent to the browser and saved in the database. */
    Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("device", device);
        map.put("start", start);
        map.put("durationSeconds", durationSeconds);
        map.put("distanceKm", distanceKm);
        map.put("avgSpeedKmh", avgSpeedKmh);
        map.put("avgPower", avgPower);
        map.put("normalizedPower", normalizedPower);
        map.put("avgHeartRate", avgHeartRate);
        map.put("maxHeartRate", maxHeartRate);
        map.put("avgCadence", avgCadence);
        map.put("ftp", ftp);
        map.put("intensityFactor", intensityFactor);
        map.put("tss", tss);
        map.put("laps", laps);
        if (hasPower()) {
            map.put("bestPower", new TreeMap<>(bestPower));
        }
        return map;
    }

    String toJson() {
        return JsonText.write(toMap());
    }

    /** Reads a summary saved by {@link #toJson()}. */
    static RideSummary fromJson(String json) {
        Map<?, ?> map = (Map<?, ?>) Json.parse(json);
        Map<Integer, Double> best = new TreeMap<>();
        if (map.get("bestPower") instanceof Map<?, ?> curve) {
            for (Map.Entry<?, ?> entry : curve.entrySet()) {
                if (entry.getValue() instanceof Double watts) {
                    best.put(Integer.parseInt(entry.getKey().toString()), watts);
                }
            }
        }
        Double start = number(map.get("start"));
        return new RideSummary(
                start == null ? -1 : start.longValue(),
                map.get("device") instanceof String text ? text : null,
                start == null ? null : start.longValue(),
                orZero(map.get("durationSeconds")).longValue(),
                orZero(map.get("distanceKm")),
                number(map.get("avgSpeedKmh")),
                number(map.get("avgPower")),
                number(map.get("normalizedPower")),
                number(map.get("avgHeartRate")),
                integer(map.get("maxHeartRate")),
                number(map.get("avgCadence")),
                integer(map.get("ftp")),
                number(map.get("intensityFactor")),
                number(map.get("tss")),
                orZero(map.get("laps")).intValue(),
                best);
    }

    /** Uses the head unit's lap distances when present, otherwise integrates the recorded speed. */
    private static double distanceKilometers(FitActivity activity) {
        double lapMeters = activity.laps().stream()
                .map(FitLap::distanceMeters)
                .filter(meters -> meters != null)
                .mapToDouble(Double::doubleValue)
                .sum();
        return lapMeters > 0 ? lapMeters / 1000.0 : MultiFileUsage.distanceKilometers(activity);
    }

    private static Double positive(double value) {
        return value > 0 && Double.isFinite(value) ? value : null;
    }

    private static Double number(Object value) {
        return value instanceof Double number ? number : null;
    }

    private static Double orZero(Object value) {
        return value instanceof Double number ? number : 0.0;
    }

    private static Integer integer(Object value) {
        return value instanceof Double number ? (int) Math.round(number) : null;
    }
}
