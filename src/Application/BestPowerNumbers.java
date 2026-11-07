package Application;

import Fit.FitActivity;
import Fit.FitRecord;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;

public final class BestPowerNumbers {
    private static final int[] BEST_POWER_INTERVALS_SECONDS = {
            1, 2, 3, 4, 5, 10, 15, 20, 25, 30, 45, 60, 90,
            120, 180, 240, 300, 600, 720, 1200, 1800, 2400,
            3600, 7200, 10800
    };

    private BestPowerNumbers() {
    }

    public static double bestTwentyHist(List<FitActivity> activity) {
        return bestTwentyHist(activity, null);
    }

    public static double bestTwentyHist(List<FitActivity> activities, int days) {
        return bestTwentyHist(activities, Integer.valueOf(days));
    }

    private static double bestTwentyHist(List<FitActivity> activities, Integer days) {
        List<FitActivity> selected = selectActivities(activities, days);
        double ftp = 0.0;
        for (FitActivity act : selected) {
            if(act.duration().toSeconds()<20*60){
                continue;
            }
            double bestTwenty = bestTwenty(act);
            if (bestTwenty > ftp) {
                 ftp = bestTwenty*0.95;
            }
        }
        return ftp;
    }

    public static double bestTwenty (FitActivity activity) {
        double ftp = 0.0;
        int twentyminutes = 20*60;
        if(activity.averagePower()==null || activity.duration().toSeconds()<twentyminutes){
            return 0.0;
        }
        ftp = bestPowerWatts(activity, twentyminutes);
        return ftp;
    }

    public static List<PowerPoint> powerPoints(FitActivity activity) {
        if (activity == null || activity.startTime() == null) {
            return List.of();
        }

        List<PowerPoint> points = new ArrayList<>();
        for (FitRecord record : activity.records()) {
            if (record.timestamp() != null && record.power() != null) {
                double seconds = Duration.between(activity.startTime(), record.timestamp()).toMillis() / 1000.0;
                points.add(new PowerPoint(Math.max(seconds, 0), record.power()));
            }
        }
        points.sort(Comparator.comparingDouble(PowerPoint::seconds));
        return smooth(points);
    }

    public static List<PowerPoint> bestPowerPoints(FitActivity activity) {
        return bestPowerPoints(Collections.singletonList(activity));
    }

    public static List<PowerPoint> bestPowerPointsWeight(FitActivity activity, int weight) {
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be greater than zero.");
        }
        return bestPowerPointsWeight(Collections.singletonList(activity), weight);
    }

    public static List<PowerPoint> bestPowerPoints(List<FitActivity> activities) {
        return bestPowerPoints(activities, null, 0);
    }

    /**
     * Calculates best power using only activities from the last {@code days} days.
     * A value of zero includes every activity. The existing overload without
     * {@code days} remains the all-time calculation.
     */
    public static List<PowerPoint> bestPowerPoints(List<FitActivity> activities, int days) {
        return bestPowerPoints(activities, Integer.valueOf(days), 0);
    }

    public static List<PowerPoint> bestPowerPointsWeight(List<FitActivity> activities, int weight) {
        validateWeight(weight);
        return bestPowerPoints(activities, null, weight);
    }

    public static List<PowerPoint> bestPowerPointsWeight(
            List<FitActivity> activities, int weight, int days) {
        validateWeight(weight);
        return bestPowerPoints(activities, Integer.valueOf(days), weight);
    }

    public static Double bestPowerWatts(FitActivity activity, int intervalSeconds) {
        if (activity == null || intervalSeconds <= 0) {
            return null;
        }
        return bestPowerWatts(rawPowerPoints(activity), intervalSeconds);
    }

    private static List<PowerPoint> rawPowerPoints(FitActivity activity) {
        if (activity == null || activity.startTime() == null) {
            return List.of();
        }
        List<PowerPoint> raw = new ArrayList<>();
        for (FitRecord record : activity.records()) {
            if (record.timestamp() != null && record.power() != null) {
                raw.add(new PowerPoint(
                        Duration.between(activity.startTime(), record.timestamp()).toMillis() / 1000.0,
                        record.power()));
            }
        }
        raw.sort(Comparator.comparingDouble(PowerPoint::seconds));
        return raw;
    }

    private static List<PowerPoint> combineBestPowerPoints(List<FitActivity> activities, int weight) {
        if (activities == null || activities.isEmpty()) {
            return List.of();
        }
        List<PowerPoint> result = new ArrayList<>();
        for (int interval : BEST_POWER_INTERVALS_SECONDS) {
            double best = Double.NEGATIVE_INFINITY;
            for (FitActivity activity : activities) {
                Double power = bestPowerWatts(activity, interval);
                if (power != null) {
                    best = Math.max(best, weight > 0 ? power / weight : power);
                }
            }
            if (best != Double.NEGATIVE_INFINITY) {
                result.add(new PowerPoint(interval, best));
            }
        }
        return List.copyOf(result);
    }

    private static List<PowerPoint> bestPowerPoints(
            List<FitActivity> activities, Integer days, int weight) {
        return combineBestPowerPoints(selectActivities(activities, days), weight);
    }

    private static void validateWeight(int weight) {
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be greater than zero.");
        }
    }

    private static List<FitActivity> selectActivities(
            List<FitActivity> activities, Integer days) {
        if (days != null && days < 0) {
            throw new IllegalArgumentException("Days must not be negative.");
        }
        if (activities == null || activities.isEmpty()) {
            return List.of();
        }
        if (days == null || days == 0) {
            return List.copyOf(activities);
        }
        Clock clock = Clock.systemUTC();
        Instant now = clock.instant();
        Instant cutoff = now.minus(Duration.ofDays(days.longValue()));
        return activities.stream()
                .filter(activity -> activity != null
                        && activity.startTime() != null
                        && !activity.startTime().isBefore(cutoff)
                        && !activity.startTime().isAfter(now))
                .toList();
    }

    private static Double bestPowerWatts(List<PowerPoint> raw, int intervalSeconds) {
        if (raw.size() < 2 || raw.get(raw.size() - 1).seconds() < intervalSeconds) {
            return null;
        }

        double[] integral = new double[raw.size()];
        for (int index = 1; index < raw.size(); index++) {
            PowerPoint previous = raw.get(index - 1);
            PowerPoint current = raw.get(index);
            integral[index] = integral[index - 1]
                    + previous.power() * (current.seconds() - previous.seconds());
        }

        double best = Double.NEGATIVE_INFINITY;
        int startIndex = 0;
        for (int endIndex = 0; endIndex < raw.size(); endIndex++) {
            PowerPoint end = raw.get(endIndex);
            double windowStart = end.seconds() - intervalSeconds;
            if (windowStart < 0 || windowStart < raw.get(0).seconds()) {
                continue;
            }

            while (startIndex + 1 < raw.size()
                    && raw.get(startIndex + 1).seconds() <= windowStart) {
                startIndex++;
            }
            if (startIndex >= raw.size() - 1) {
                continue;
            }
            PowerPoint sample = raw.get(startIndex);
            double weightedPower = integral[endIndex]
                    - integral[startIndex]
                    - sample.power() * (windowStart - sample.seconds());
            double coveredSeconds = end.seconds() - windowStart;
            if (coveredSeconds >= intervalSeconds) {
                best = Math.max(best, weightedPower / coveredSeconds);
            }
        }
        return best == Double.NEGATIVE_INFINITY ? null : best;
    }

    private static List<PowerPoint> smooth(List<PowerPoint> rawPoints) {
        List<PowerPoint> smoothed = new ArrayList<>(rawPoints.size());
        double halfWindow = 1.5;
        int left = 0;
        int right = 0;
        double powerSum = 0;
        for (PowerPoint point : rawPoints) {
            double lowerBound = point.seconds() - halfWindow;
            double upperBound = point.seconds() + halfWindow;
            while (right < rawPoints.size() && rawPoints.get(right).seconds() <= upperBound) {
                powerSum += rawPoints.get(right).power();
                right++;
            }
            while (left < right && rawPoints.get(left).seconds() < lowerBound) {
                powerSum -= rawPoints.get(left).power();
                left++;
            }
            smoothed.add(new PowerPoint(point.seconds(), powerSum / (right - left)));
        }
        return List.copyOf(smoothed);
    }
}
