package Application;

import Fit.FitActivity;
import Fit.FitParser;
import Fit.FitRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
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
     * Loads every FIT file directly inside {@code directory}.
     *
     * @return the parsed activities, in filename order
     * @throws IOException if the directory cannot be read or a file cannot be parsed
     */
    public List<FitActivity> loadFiles(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IOException("FIT file directory does not exist: " + directory);
        }

        FitParser parser = new FitParser();
        List<Path> files;
        try (var paths = Files.list(directory)) {
            files = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".fit"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }

        List<FitActivity> loaded = new ArrayList<>();
        for (Path file : files) {
            loaded.add(parser.parse(file));
        }

        activities.clear();
        activities.addAll(loaded);
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
            System.out.printf("%d. Duration: %s, Distance: %.2f km, NP: %.2f W, IF: %s%n",
                    index + 1,
                    FitFileUsage.formatDuration(activity.duration()),
                    distanceKilometers(activity),
                    activity.normalizedPower(),
                    intensityFactor);
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
