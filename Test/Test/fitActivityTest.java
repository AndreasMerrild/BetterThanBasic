package Test;

import Application.BestPowerNumbers;
import Fit.FitActivity;
import Fit.FitRecord;
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
