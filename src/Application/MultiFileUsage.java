package Application;

import Fit.FitActivity;
import Fit.FitDatabase;
import Fit.FitRecord;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Loads and summarizes multiple FIT activities.
 *
 * The parsed activities are kept together so that later graph and comparison
 * functionality can use the same in-memory data.
 */
public class MultiFileUsage {
    private final List<FitActivity> activities = new ArrayList<>();

    /**
     * Imports any new FIT files in {@code directory} into the database (when a
     * directory is given) and then loads every stored activity from it.
     *
     * @return all stored activities, oldest first
     */
    public List<FitActivity> loadFiles(FitDatabase database, Path directory) throws IOException {
        if (directory != null) {
            System.out.println("Imported " + database.importDirectory(directory) + " new FIT file(s).");
        }
        activities.clear();
        activities.addAll(database.loadAll());
        return List.copyOf(activities);
    }

    public List<FitActivity> activities() {
        return List.copyOf(activities);
    }

    public Duration totalDuration() {
        return activities.stream()
                .map(FitActivity::duration)
                .reduce(Duration.ZERO, Duration::plus);
    }

    /**
     * Calculates distance by integrating the recorded speed over timestamp gaps.
     * FIT speed is expressed in metres per second.
     */
    public double totalDistanceKilometers() {
        return activities.stream()
                .mapToDouble(MultiFileUsage::distanceKilometers)
                .sum();
    }

    public void printSummary() {
        if (activities.isEmpty()) {
            System.out.println("No FIT files found.");
            return;
        }

        for (int index = 0; index < activities.size(); index++) {
            FitActivity activity = activities.get(index);
            String intensityFactor = activity.functionalThresholdPower() == null
                    ? "unavailable"
                    : String.format(Locale.ROOT, "%.3f", activity.intensityFactor());
            String trainingStressScore = activity.TSS() == 0.0
                    ? "unavailable"
                    : String.format(Locale.ROOT, "%.2f", activity.TSS());
            System.out.printf("%d. Duration: %s, Distance: %.2f km, NP: %.2f W, IF: %s%n, TSS: %s%n",
                    index + 1,
                    FitFileUsage.formatDuration(activity.duration()),
                    distanceKilometers(activity),
                    activity.normalizedPower(),
                    intensityFactor,
                    trainingStressScore);
        }
        System.out.println("Total time: " + FitFileUsage.formatDuration(totalDuration()));
        System.out.printf("Total distance: %.2f km%n", totalDistanceKilometers());
        System.out.printf("Average speed: %.2f km/h%n", totalDistanceKilometers() / (totalDuration().toSeconds() / 3600.0));
    }

    public static double distanceKilometers(FitActivity activity) {
        if (activity == null) {
            throw new IllegalArgumentException("activity must not be null");
        }

        double meters = 0.0;
        List<FitRecord> records = activity.records();
        for (int index = 1; index < records.size(); index++) {
            FitRecord previous = records.get(index - 1);
            FitRecord current = records.get(index);
            if (previous.timestamp() == null || current.timestamp() == null
                    || previous.speed() == null || current.speed() == null) {
                continue;
            }

            long seconds = Duration.between(previous.timestamp(), current.timestamp()).toMillis();
            if (seconds <= 0) {
                continue;
            }

            double averageSpeed = (previous.speed() + current.speed()) / 2.0;
            meters += averageSpeed * seconds / 1000.0;
        }
        return meters / 1000.0;
    }
}
