package Test;

import Application.BestPowerNumbers;
import Fit.FitActivity;
import Fit.FitParser;
import Fit.FitRecord;
import Fit.FitLap;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class fitActivityTest {
    @Test
    void testAverageSpeedIsConvertedToKilometersPerHour() {
        FitActivity activity = new FitActivity(
                null,
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(1),
                List.of(
                        new FitRecord(Instant.EPOCH, null, null, 10.0, null),
                        new FitRecord(Instant.EPOCH.plusSeconds(1), null, null, 20.0, null)));

        assertEquals(54.0, activity.averageSpeed());
    }

    @Test
    void bestPowerCanBeLimitedToRecentActivities() {
        Instant now = Instant.now();
        FitActivity oldActivity = powerActivity(now.minusSeconds(100L * 24 * 60 * 60), 500.0);
        FitActivity recentActivity = powerActivity(now.minusSeconds(2L * 24 * 60 * 60), 200.0);

        assertEquals(500.0, BestPowerNumbers.bestPowerPoints(
                List.of(oldActivity, recentActivity)).getFirst().power());
        assertEquals(200.0, BestPowerNumbers.bestPowerPoints(
                List.of(oldActivity, recentActivity), 7).getFirst().power());
        assertEquals(500.0, BestPowerNumbers.bestPowerPoints(
                List.of(oldActivity, recentActivity), 0).getFirst().power());
    }

    @Test
    void bestPowerRejectsNegativeAge() {
        assertThrows(IllegalArgumentException.class, () ->
                BestPowerNumbers.bestPowerPoints(List.of(), -1));
    }

    @Test
    void heartRatePointsUseRawTimestampedSamplesWithoutSmoothing() {
        Instant start = Instant.EPOCH;
        FitActivity activity = new FitActivity(
                null,
                start,
                start.plusSeconds(2),
                List.of(
                        new FitRecord(start, null, 100, null, null),
                        new FitRecord(start.plusSeconds(1), null, 150, null, null),
                        new FitRecord(start.plusSeconds(2), null, null, null, null)));

        assertEquals(List.of(
                new Application.PowerPoint(0, 100),
                new Application.PowerPoint(1, 150)),
                BestPowerNumbers.heartRatePoints(activity));
    }

    @Test
    void cadencePointsUseRawTimestampedSamplesWithoutSmoothing() {
        Instant start = Instant.EPOCH;
        FitActivity activity = new FitActivity(
                null,
                start,
                start.plusSeconds(2),
                List.of(
                        new FitRecord(start, null, null, null, 80),
                        new FitRecord(start.plusSeconds(1), null, null, null, 95),
                        new FitRecord(start.plusSeconds(2), null, null, null, null)));

        assertEquals(List.of(
                new Application.PowerPoint(0, 80),
                new Application.PowerPoint(1, 95)),
                BestPowerNumbers.cadencePoints(activity));
    }

    @Test
    void parserExposesLapSummaries() throws Exception {
        FitActivity activity = new FitParser().parse(
                java.nio.file.Path.of("Test", "Test", "2026-01-20-125514-ELEMNT ROAM EFB1-17-0.fit"));

        assertEquals(1, activity.laps().getFirst().number());
        assertEquals(1, activity.laps().size());
        FitLap lap = activity.laps().getFirst();
        assertEquals(4204, lap.duration().toSeconds());
        org.junit.jupiter.api.Assertions.assertTrue(lap.distanceMeters() > 0);
        assertEquals(152, lap.averageHeartRate());
        assertEquals(188, lap.maxHeartRate());
    }

    private static FitActivity powerActivity(Instant start, double power) {
        return new FitActivity(
                null,
                start,
                start.plusSeconds(1),
                List.of(
                        new FitRecord(start, (int) power, null, null, null),
                        new FitRecord(start.plusSeconds(1), (int) power, null, null, null)));
    }
}
