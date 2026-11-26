package Fit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Reads GoldenCheetah ride files (.json) into the same {@link FitActivity} that {@link FitParser}
 * makes from FIT files, so both kinds of file are stored, shown and analysed the same way.
 *
 * Layout: {@code RIDE.STARTTIME} ("2025/01/02 12:18:56 UTC"), {@code RIDE.SAMPLES} with one entry per
 * second ({@code SECS} since the start, {@code WATTS}, {@code HR}, {@code KPH}, {@code CAD}, {@code KM}, ...),
 * {@code RIDE.INTERVALS} (laps are the ones named "Lap N") and {@code RIDE.XDATA} where the "SESSION"
 * entry holds the head unit's {@code threshold_power} (FTP).
 */
public final class GoldenCheetahParser {
    private static final DateTimeFormatter START_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    /** One second of data, as read from the file; null = not recorded. */
    private record Sample(double secs, Double km, Integer power, Integer heartRate, Double speedKmh, Integer cadence) {
    }

    public FitActivity parse(Path file) throws IOException {
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | ClassCastException exception) {
            throw new IOException("Could not read GoldenCheetah file " + file + ": " + exception.getMessage(), exception);
        }
    }

    @SuppressWarnings("unchecked")
    FitActivity parse(String json) {
        if (!(Json.parse(json) instanceof Map<?, ?> root) || !(root.get("RIDE") instanceof Map<?, ?> rideMap)) {
            throw new IllegalArgumentException("The file is not a GoldenCheetah ride (no RIDE object).");
        }
        Map<String, Object> ride = (Map<String, Object>) rideMap;
        Instant start = startTime(ride.get("STARTTIME"));

        List<Sample> samples = new ArrayList<>();
        if (ride.get("SAMPLES") instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> sample && sample.get("SECS") instanceof Double secs) {
                    samples.add(new Sample(secs,
                            number(sample.get("KM")),
                            integer(sample.get("WATTS")),
                            positiveInteger(sample.get("HR")), // GoldenCheetah writes 0 when there was no HR signal
                            number(sample.get("KPH")),
                            integer(sample.get("CAD"))));
                }
            }
        }
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("The ride contains no samples.");
        }

        List<FitRecord> records = new ArrayList<>(samples.size());
        for (Sample sample : samples) {
            records.add(new FitRecord(at(start, sample.secs()), sample.power(), sample.heartRate(),
                    sample.speedKmh() == null ? null : sample.speedKmh() / 3.6, sample.cadence()));
        }

        String device = ride.get("DEVICETYPE") instanceof String text && !text.isBlank() ? text.trim() : null;
        return new FitActivity(device, records.get(0).timestamp(), records.get(records.size() - 1).timestamp(),
                thresholdPower(ride.get("XDATA")), records, laps(ride.get("INTERVALS"), samples, start));
    }

    private static Instant startTime(Object value) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("The ride has no STARTTIME.");
        }
        String clean = text.trim().replaceFirst("\\s*UTC$", "");
        try {
            return LocalDateTime.parse(clean, START_TIME).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Unreadable STARTTIME \"" + text.trim() + "\"");
        }
    }

    private static Instant at(Instant start, double seconds) {
        return start.plusMillis(Math.round(seconds * 1000));
    }

    /** FTP from the "SESSION" extra data, when the head unit stored one. */
    private static Integer thresholdPower(Object xdata) {
        if (!(xdata instanceof List<?> entries)) {
            return null;
        }
        for (Object entry : entries) {
            if (entry instanceof Map<?, ?> data && "SESSION".equals(data.get("NAME"))
                    && data.get("VALUES") instanceof List<?> names
                    && data.get("SAMPLES") instanceof List<?> rows && !rows.isEmpty()
                    && rows.get(0) instanceof Map<?, ?> row && row.get("VALUES") instanceof List<?> values) {
                for (int index = 0; index < names.size() && index < values.size(); index++) {
                    if (names.get(index) instanceof String name && name.trim().equals("threshold_power")) {
                        Integer ftp = positiveInteger(values.get(index));
                        return ftp;
                    }
                }
            }
        }
        return null;
    }

    /** Laps from the "Lap N" intervals, with their numbers computed from the samples inside them. */
    private static List<FitLap> laps(Object intervals, List<Sample> samples, Instant start) {
        List<FitLap> laps = new ArrayList<>();
        if (!(intervals instanceof List<?> list)) {
            return laps;
        }
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> interval) || !(interval.get("NAME") instanceof String name)
                    || !name.trim().startsWith("Lap") || !(interval.get("START") instanceof Double from)
                    || !(interval.get("STOP") instanceof Double to) || to <= from) {
                continue;
            }
            List<Sample> inside = samples.stream().filter(s -> s.secs() >= from && s.secs() < to).toList();
            Double distanceMeters = null;
            List<Double> km = inside.stream().map(Sample::km).filter(v -> v != null).toList();
            if (km.size() >= 2) {
                distanceMeters = (km.get(km.size() - 1) - km.get(0)) * 1000;
            }
            Double avgSpeed = average(inside, Sample::speedKmh);
            Double maxSpeed = max(inside, Sample::speedKmh);
            laps.add(new FitLap(laps.size() + 1, at(start, from), Duration.ofMillis(Math.round((to - from) * 1000)),
                    distanceMeters,
                    avgSpeed == null ? null : avgSpeed / 3.6,
                    maxSpeed == null ? null : maxSpeed / 3.6,
                    round(average(inside, s -> toDouble(s.power()))), round(max(inside, s -> toDouble(s.power()))),
                    round(average(inside, s -> toDouble(s.heartRate()))), round(max(inside, s -> toDouble(s.heartRate()))),
                    round(average(inside, s -> toDouble(s.cadence()))), round(max(inside, s -> toDouble(s.cadence())))));
        }
        return laps;
    }

    private static Double average(List<Sample> samples, Function<Sample, Double> field) {
        return samples.stream().map(field).filter(v -> v != null)
                .mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null);
    }

    private static Double max(List<Sample> samples, Function<Sample, Double> field) {
        return samples.stream().map(field).filter(v -> v != null)
                .mapToDouble(Double::doubleValue).max().stream().boxed().findFirst().orElse(null);
    }

    private static Double toDouble(Integer value) {
        return value == null ? null : value.doubleValue();
    }

    private static Integer round(Double value) {
        return value == null ? null : (int) Math.round(value);
    }

    private static Double number(Object value) {
        return value instanceof Double number && Double.isFinite(number) ? number : null;
    }

    private static Integer integer(Object value) {
        Double number = number(value);
        return number == null ? null : (int) Math.round(number);
    }

    private static Integer positiveInteger(Object value) {
        Integer number = integer(value);
        return number == null || number <= 0 ? null : number;
    }
}
