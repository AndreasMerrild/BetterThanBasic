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
                id = insert(connection, source, new FitParser().parse(file));
            }
            return load(connection, id).get(0);
        } catch (SQLException exception) {
            throw new IOException("Database error for " + source + ": " + exception.getMessage(), exception);
        }
    }

    /** Imports every new FIT file directly inside {@code directory}; returns how many were added. */
    public int importDirectory(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IOException("FIT file directory does not exist: " + directory);
        }
        List<Path> files;
        try (Stream<Path> paths = Files.list(directory)) {
            files = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".fit"))
                    .toList();
        }
        int added = 0;
        try (Connection connection = connect()) {
            FitParser parser = new FitParser();
            for (Path file : files) {
                String source = file.getFileName().toString();
                if (findId(connection, source) == null) {
                    insert(connection, source, parser.parse(file));
                    added++;
                }
            }
        } catch (SQLException exception) {
            throw new IOException("Database error: " + exception.getMessage(), exception);
        }
        return added;
    }

    /** Every stored activity, oldest first. */
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
