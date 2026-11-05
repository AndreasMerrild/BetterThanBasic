package Fit;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The activity data extracted from a FIT file.
 */
public record FitActivity(
        String deviceName,
        Instant startTime,
        Instant endTime,
        Integer functionalThresholdPower,
        List<FitRecord> records) {

    private static final double METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR = 3.6;

    public FitActivity {
        records = List.copyOf(records);
    }

    public FitActivity(String deviceName, Instant startTime, Instant endTime, List<FitRecord> records) {
        this(deviceName, startTime, endTime, null, records);
    }

    public Duration duration() {
        if (startTime == null || endTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, endTime);
    }

    public double TSS(){
        if (startTime == null || endTime == null) {
            return 0.0;
        }
        return TSS();
    }

    public double intensityFactor(){
        if (functionalThresholdPower == null || functionalThresholdPower <= 0) {
            return 0.0;
        }
        return normalizedPower() / functionalThresholdPower;
    }
    public double normalizedPower(){
        if ((startTime == null || endTime == null)||(averagePower()==null)) {
            return 0.0;
        }
        records.stream().map(FitRecord::power).toList();

            double normalizedPower = 0.0;
            List<Double> rolling30sAveragePower = rolling30SecondAveragePower();
            if (rolling30sAveragePower.isEmpty()) {
                return 0.0;
            }
            List<Double> poweredValues = new ArrayList<>();
            for (Double rollingAvgPwr : rolling30sAveragePower) {
                rollingAvgPwr = Math.pow(rollingAvgPwr, 4);
                poweredValues.add(rollingAvgPwr);
            }
            double rollingAvgPwrValues = average(poweredValues);
            normalizedPower = Math.pow(rollingAvgPwrValues, 0.25);

        return normalizedPower;
    }

    public Double averagePower() {
        return average(records.stream().map(FitRecord::power).toList());
    }

    /**
     * Returns the trailing 30-second average power at each recorded power sample.
     * The first value is returned only after a complete 30-second window exists.
     */
    public List<Double> rolling30SecondAveragePower() {
        List<FitRecord> powerRecords = records.stream()
                .filter(record -> record.timestamp() != null && record.power() != null)
                .sorted(Comparator.comparing(FitRecord::timestamp))
                .toList();

        if (powerRecords.isEmpty()) {
            return List.of();
        }

        List<Double> averages = new ArrayList<>();
        for (int endIndex = 0; endIndex < powerRecords.size(); endIndex++) {
            FitRecord end = powerRecords.get(endIndex);
            Instant windowStart = end.timestamp().minusSeconds(30);
            int startIndex = endIndex;

            while (startIndex > 0
                    && !powerRecords.get(startIndex - 1).timestamp().isBefore(windowStart)) {
                startIndex--;
            }

            if (Duration.between(powerRecords.get(0).timestamp(), end.timestamp())
                    .compareTo(Duration.ofSeconds(30)) < 0) {
                continue;
            }

            averages.add(average(
                    powerRecords.subList(startIndex, endIndex + 1)
                            .stream()
                            .map(FitRecord::power)
                            .toList()
            ));
        }
        return List.copyOf(averages);
    }

    public Double averageSpeed() {
        Double averageSpeed = average(records.stream().map(FitRecord::speed).toList());
        return averageSpeed == null ? null : averageSpeed * METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR;
    }

    public Double averageCadence() {
        return average(records.stream().map(FitRecord::cadence).toList());
    }

    public Double averageHeartRate() {
        return average(records.stream().map(FitRecord::heartRate).toList());
    }

    private static Double average(List<? extends Number> values) {
        return values.stream()
                .filter(value -> value != null)
                .mapToDouble(Number::doubleValue)
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);
    }
}
