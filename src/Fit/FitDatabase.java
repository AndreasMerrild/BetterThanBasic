package Fit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * SQLite store for parsed FIT activities, so training data is remembered between runs.
 * A FIT file is parsed only the first time its file name is imported.
 */
public final class FitDatabase {
    public static final Path DEFAULT_FILE = Path.of("training.db");

    private final String url;

    public FitDatabase() throws IOException {
        this(DEFAULT_FILE);
    }

    public FitDatabase(Path file) throws IOException {
        this.url = "jdbc:sqlite:" + file.toAbsolutePath();
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS activities ("
                    + "id INTEGER PRIMARY KEY, source TEXT UNIQUE, device_name TEXT,"
                    + " start_time INTEGER, end_time INTEGER, ftp INTEGER)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS records ("
                    + "activity_id INTEGER, timestamp INTEGER, power INTEGER, heart_rate INTEGER,"
                    + " speed REAL, cadence INTEGER)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS laps ("
                    + "activity_id INTEGER, number INTEGER, start_time INTEGER, duration_ms INTEGER,"
                    + " distance REAL, avg_speed REAL, max_speed REAL, avg_power INTEGER, max_power INTEGER,"
                    + " avg_hr INTEGER, max_hr INTEGER, avg_cadence INTEGER, max_cadence INTEGER)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS records_activity ON records(activity_id)");
            // Pre-computed summaries (e.g. the web app's per-ride numbers), so a training list does not
            // need every recorded second in memory. "version" lets the owner recompute after changes.
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS summaries ("
                    + "activity_id INTEGER PRIMARY KEY, version INTEGER, json TEXT)");
        } catch (SQLException exception) {
            throw new IOException("Could not open training database: " + exception.getMessage(), exception);
        }
    }

    /** Parses and stores {@code file} unless it was imported before, then returns it from the database. */
    public FitActivity importFile(Path file) throws IOException {
        String source = file.getFileName().toString();
        try (Connection connection = connect()) {
            Long id = findId(connection, source);
            if (id == null) {
                id = insert(connection, source, parseFile(file));
            }
            return load(connection, id).get(0);
        } catch (SQLException exception) {
            throw new IOException("Database error for " + source + ": " + exception.getMessage(), exception);
        }
    }

    /** What {@link #importNew} did with a file. */
    public enum ImportStatus {
        /** The training was stored. */
        ADDED,
        /** A file with this name was imported before. */
        SAME_FILE,
        /** Another file holds the same ride (it starts within {@link #SAME_RIDE_WINDOW}), e.g. a FIT and a JSON copy. */
        SAME_RIDE
    }

    /** Rides that start this close together are treated as the same ride recorded in two files. */
    public static final Duration SAME_RIDE_WINDOW = Duration.ofMinutes(2);

    /** Reads a training file: GoldenCheetah {@code .json} or FIT (anything else). */
    public static FitActivity parseFile(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".json") ? new GoldenCheetahParser().parse(file) : new FitParser().parse(file);
    }

    /** Whether this is a file type that {@link #parseFile} can read. */
    public static boolean isTrainingFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".fit") || name.endsWith(".json");
    }

    /**
     * Stores {@code file} unless the same file, or the same ride from another file, is already stored.
     * Throws IOException when the file cannot be read.
     */
    public ImportStatus importNew(Path file) throws IOException {
        String source = file.getFileName().toString();
        try (Connection connection = connect()) {
            if (findId(connection, source) != null) {
                return ImportStatus.SAME_FILE;
            }
            FitActivity activity = parseFile(file);
            if (hasRideStartingNear(connection, activity.startTime())) {
                return ImportStatus.SAME_RIDE;
            }
            insert(connection, source, activity);
            return ImportStatus.ADDED;
        } catch (SQLException exception) {
            throw new IOException("Database error for " + source + ": " + exception.getMessage(), exception);
        }
    }

    private static boolean hasRideStartingNear(Connection connection, Instant start) throws SQLException {
        if (start == null) {
            return false;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM activities WHERE start_time BETWEEN ? AND ? LIMIT 1")) {
            statement.setLong(1, start.minus(SAME_RIDE_WINDOW).toEpochMilli());
            statement.setLong(2, start.plus(SAME_RIDE_WINDOW).toEpochMilli());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    /** Whether a training file with this file name has already been imported. */
    public boolean contains(String fileName) throws IOException {
        try (Connection connection = connect()) {
            return findId(connection, fileName) != null;
        } catch (SQLException exception) {
            throw new IOException("Database error for " + fileName + ": " + exception.getMessage(), exception);
        }
    }

    /**
     * Imports every new training file (FIT or GoldenCheetah JSON) directly inside {@code directory};
     * returns how many were added. Files holding a ride that is already stored are skipped.
     */
    public int importDirectory(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IOException("FIT file directory does not exist: " + directory);
        }
        List<Path> files;
        try (Stream<Path> paths = Files.list(directory)) {
            files = paths.filter(Files::isRegularFile)
                    .filter(FitDatabase::isTrainingFile)
                    .toList();
        }
        int added = 0;
        for (Path file : files) {
            if (importNew(file) == ImportStatus.ADDED) {
                added++;
            }
        }
        return added;
    }

    /** The database id of the activity imported from the file with this name, or null. */
    public Long idOf(String fileName) throws IOException {
        try (Connection connection = connect()) {
            return findId(connection, fileName);
        } catch (SQLException exception) {
            throw new IOException("Database error for " + fileName + ": " + exception.getMessage(), exception);
        }
    }

    /** A stored activity's database id and start time, without its data. */
    public record StoredActivity(long id, Instant startTime) {
    }

    /** Every stored activity's id and start time, oldest first. Cheap: no records are read. */
    public List<StoredActivity> list() throws IOException {
        try (Connection connection = connect(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT id, start_time FROM activities ORDER BY start_time")) {
            List<StoredActivity> list = new ArrayList<>();
            while (result.next()) {
                list.add(new StoredActivity(result.getLong(1), instant(result, 2)));
            }
            return list;
        } catch (SQLException exception) {
            throw new IOException("Could not read training database: " + exception.getMessage(), exception);
        }
    }

    /** One stored activity with all its records and laps, or null if there is none with this id. */
    public FitActivity load(long id) throws IOException {
        try (Connection connection = connect()) {
            List<FitActivity> found = load(connection, id);
            return found.isEmpty() ? null : found.get(0);
        } catch (SQLException exception) {
            throw new IOException("Could not read training database: " + exception.getMessage(), exception);
        }
    }

    /** All stored summaries that were saved with {@code version}, keyed by activity id. */
    public Map<Long, String> summaries(int version) throws IOException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT activity_id, json FROM summaries WHERE version = ?")) {
            statement.setInt(1, version);
            Map<Long, String> summaries = new HashMap<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    summaries.put(result.getLong(1), result.getString(2));
                }
            }
            return summaries;
        } catch (SQLException exception) {
            throw new IOException("Could not read training database: " + exception.getMessage(), exception);
        }
    }

    public void storeSummary(long id, int version, String json) throws IOException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR REPLACE INTO summaries (activity_id, version, json) VALUES (?,?,?)")) {
            statement.setLong(1, id);
            statement.setInt(2, version);
            statement.setString(3, json);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IOException("Could not write training database: " + exception.getMessage(), exception);
        }
    }

    /** Every stored activity, oldest first. Reads every record into memory, so prefer {@link #list()} for many rides. */
    public List<FitActivity> loadAll() throws IOException {
        try (Connection connection = connect()) {
            return load(connection, null);
        } catch (SQLException exception) {
            throw new IOException("Could not read training database: " + exception.getMessage(), exception);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url);
    }

    private static Long findId(Connection connection, String source) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM activities WHERE source = ?")) {
            statement.setString(1, source);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : null;
            }
        }
    }

    private static long insert(Connection connection, String source, FitActivity activity) throws SQLException {
        connection.setAutoCommit(false);
        try {
            long id;
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO activities (source, device_name, start_time, end_time, ftp) VALUES (?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, source);
                statement.setString(2, activity.deviceName());
                set(statement, 3, millis(activity.startTime()));
                set(statement, 4, millis(activity.endTime()));
                set(statement, 5, activity.functionalThresholdPower());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    id = keys.getLong(1);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO records VALUES (?,?,?,?,?,?)")) {
                for (FitRecord record : activity.records()) {
                    set(statement, 1, id, millis(record.timestamp()), record.power(),
                            record.heartRate(), record.speed(), record.cadence());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO laps VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                for (FitLap lap : activity.laps()) {
                    set(statement, 1, id, lap.number(), millis(lap.startTime()), lap.duration().toMillis(),
                            lap.distanceMeters(), lap.averageSpeed(), lap.maxSpeed(), lap.averagePower(),
                            lap.maxPower(), lap.averageHeartRate(), lap.maxHeartRate(),
                            lap.averageCadence(), lap.maxCadence());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
            return id;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /** Loads one activity by id, or all activities when {@code id} is null. */
    private static List<FitActivity> load(Connection connection, Long id) throws SQLException {
        Map<Long, List<FitRecord>> records = new HashMap<>();
        Map<Long, List<FitLap>> laps = new HashMap<>();
        String filter = id == null ? "" : "WHERE activity_id = " + id;
        String where = id == null ? "" : "WHERE id = " + id;
        try (Statement statement = connection.createStatement()) {
            try (ResultSet r = statement.executeQuery(
                    "SELECT * FROM records " + filter + " ORDER BY activity_id, timestamp")) {
                while (r.next()) {
                    records.computeIfAbsent(r.getLong(1), key -> new ArrayList<>()).add(new FitRecord(
                            instant(r, 2), integer(r, 3), integer(r, 4), decimal(r, 5), integer(r, 6)));
                }
            }
            try (ResultSet r = statement.executeQuery(
                    "SELECT * FROM laps " + filter + " ORDER BY activity_id, number")) {
                while (r.next()) {
                    laps.computeIfAbsent(r.getLong(1), key -> new ArrayList<>()).add(new FitLap(
                            r.getInt(2), instant(r, 3), Duration.ofMillis(r.getLong(4)),
                            decimal(r, 5), decimal(r, 6), decimal(r, 7), integer(r, 8), integer(r, 9),
                            integer(r, 10), integer(r, 11), integer(r, 12), integer(r, 13)));
                }
            }
            List<FitActivity> activities = new ArrayList<>();
            try (ResultSet r = statement.executeQuery(
                    "SELECT id, device_name, start_time, end_time, ftp FROM activities "
                            + where + " ORDER BY start_time")) {
                while (r.next()) {
                    long activityId = r.getLong(1);
                    activities.add(new FitActivity(r.getString(2), instant(r, 3), instant(r, 4), integer(r, 5),
                            records.getOrDefault(activityId, List.of()), laps.getOrDefault(activityId, List.of())));
                }
            }
            return activities;
        }
    }

    private static Long millis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    private static void set(PreparedStatement statement, int first, Object... values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == null) {
                statement.setNull(first + index, Types.NULL);
            } else {
                statement.setObject(first + index, values[index]);
            }
        }
    }

    private static Instant instant(ResultSet result, int column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    private static Integer integer(ResultSet result, int column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private static Double decimal(ResultSet result, int column) throws SQLException {
        double value = result.getDouble(column);
        return result.wasNull() ? null : value;
    }
}
