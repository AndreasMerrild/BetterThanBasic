package Fit;

import java.time.Duration;
import java.time.Instant;

/**
 * Summary data recorded for one lap in a FIT activity.
 */
public record FitLap(
        int number,
        Instant startTime,
        Duration duration,
        Double distanceMeters,
        Double averageSpeed,
        Double maxSpeed,
        Integer averagePower,
        Integer maxPower,
        Integer averageHeartRate,
        Integer maxHeartRate,
        Integer averageCadence,
        Integer maxCadence) {

    private static final double METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR = 3.6;

    public Double averageSpeedKilometersPerHour() {
        return averageSpeed == null ? null : averageSpeed * METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR;
    }

    public Double maxSpeedKilometersPerHour() {
        return maxSpeed == null ? null : maxSpeed * METERS_PER_SECOND_TO_KILOMETERS_PER_HOUR;
    }
}
