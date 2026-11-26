package Server;

import Fit.FitDatabase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The athletes the web app knows, each with their own training database and uploads folder.
 *
 * The first athlete ("default") uses the original {@code training.db} and {@code uploads/}, so the
 * console app keeps seeing the same data. Athletes added later live in {@code athletes/<id>/}.
 * The list itself is {@code athletes.txt}: one {@code id<TAB>name} line per athlete.
 */
final class Athletes {
    static final String DEFAULT_ID = "default";
    private static final Path LIST_FILE = Path.of("athletes.txt");
    private static final Path ATHLETES_DIR = Path.of("athletes");
    private static final int MAX_NAME_LENGTH = 60;

    /** One athlete: where their data lives, plus the cached training list the API serves. */
    static final class Athlete {
        final String id;
        String name;
        final Path databaseFile;
        final FitDatabase database;
        final Path uploads;
        /** Start time (epoch ms, the id the web app uses) → database id, for opening one ride. */
        Map<Long, Long> databaseIdByStart = Map.of();
        /** Every ride's summary, oldest first (rides without a start time are left out). */
        List<RideSummary> rides = List.of();
        boolean loaded;

        private Athlete(String id, String name, Path databaseFile, Path uploads) throws IOException {
            this.id = id;
            this.name = name;
            this.databaseFile = databaseFile;
            this.database = new FitDatabase(databaseFile);
            this.uploads = uploads;
        }
    }

    private final Map<String, Athlete> athletes = new LinkedHashMap<>();

    Athletes() throws IOException {
        Map<String, String> names = new LinkedHashMap<>();
        names.put(DEFAULT_ID, "Athlete");
        if (Files.isRegularFile(LIST_FILE)) {
            for (String line : Files.readAllLines(LIST_FILE, StandardCharsets.UTF_8)) {
                int tab = line.indexOf('\t');
                if (tab > 0 && isValidId(line.substring(0, tab))) {
                    names.put(line.substring(0, tab), cleanName(line.substring(tab + 1), "Athlete"));
                }
            }
        }
        for (Map.Entry<String, String> entry : names.entrySet()) {
            athletes.put(entry.getKey(), open(entry.getKey(), entry.getValue()));
        }
    }

    private static Athlete open(String id, String name) throws IOException {
        if (id.equals(DEFAULT_ID)) {
            return new Athlete(id, name, FitDatabase.DEFAULT_FILE, Path.of("uploads"));
        }
        Path folder = ATHLETES_DIR.resolve(id);
        Files.createDirectories(folder);
        return new Athlete(id, name, folder.resolve("training.db"), folder.resolve("uploads"));
    }

    /** The athlete with this id (null or blank means the default one), or null if there is none. */
    Athlete get(String id) {
        return athletes.get(id == null || id.isBlank() ? DEFAULT_ID : id);
    }

    List<Athlete> all() {
        return new ArrayList<>(athletes.values());
    }

    /** Adds an athlete with an id derived from the name, and saves the list. */
    Athlete add(String name) throws IOException {
        String clean = cleanName(name, null);
        if (clean == null) {
            throw new IllegalArgumentException("Please give the athlete a name");
        }
        String base = clean.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty() || base.equals(DEFAULT_ID)) {
            base = "athlete";
        }
        String id = base;
        for (int number = 2; athletes.containsKey(id); number++) {
            id = base + "-" + number;
        }
        Athlete athlete = open(id, clean);
        athletes.put(id, athlete);
        save();
        return athlete;
    }

    void rename(Athlete athlete, String name) throws IOException {
        String clean = cleanName(name, null);
        if (clean == null) {
            throw new IllegalArgumentException("Please give the athlete a name");
        }
        athlete.name = clean;
        save();
    }

    private void save() throws IOException {
        StringBuilder text = new StringBuilder();
        for (Athlete athlete : athletes.values()) {
            text.append(athlete.id).append('\t').append(athlete.name).append(System.lineSeparator());
        }
        Files.writeString(LIST_FILE, text, StandardCharsets.UTF_8);
    }

    private static boolean isValidId(String id) {
        return id.matches("[a-z0-9-]{1,64}");
    }

    /** Trims the name and removes characters that would break the list file; null/fallback if empty. */
    private static String cleanName(String name, String fallback) {
        if (name == null) {
            return fallback;
        }
        String clean = name.replaceAll("[\\p{Cntrl}]", " ").trim().replaceAll("\\s+", " ");
        if (clean.length() > MAX_NAME_LENGTH) {
            clean = clean.substring(0, MAX_NAME_LENGTH).trim();
        }
        return clean.isEmpty() ? fallback : clean;
    }
}
