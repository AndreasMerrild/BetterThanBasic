package Server;

import Fit.FitActivity;
import Fit.FitDatabase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Serves the BetterThanBasic web frontend and a small JSON API over the training database.
 * Uses only the JDK's built-in HTTP server, so no extra dependencies are needed.
 *
 * Usage: WebServer [port] [fit-directory-to-import]
 */
public final class WebServer {
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "ico", "image/x-icon");

    /** Largest FIT file accepted by an upload (a long ride is a few MB). */
    private static final int MAX_UPLOAD_BYTES = 50 * 1024 * 1024;
    /** Header every changing request must carry; see {@link #handleUpload}. */
    private static final String WRITE_HEADER = "X-BetterThanBasic-Upload";

    private final Athletes athletes;
    private final Path frontend;

    public WebServer(Athletes athletes, Path frontend) {
        this.athletes = athletes;
        this.frontend = frontend.toAbsolutePath().normalize();
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        Athletes athletes = new Athletes();
        if (args.length > 1) {
            int added = athletes.get(Athletes.DEFAULT_ID).database.importDirectory(Path.of(args[1]));
            System.out.println("Imported " + added + " new FIT file(s).");
        }
        new WebServer(athletes, Path.of("frontend")).start(port);
    }

    /**
     * The JDK server runs one request at a time (no executor is set), so the athlete caches and the
     * SQLite files are never used by two requests at once.
     */
    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/athletes", safe(this::handleAthletes));
        server.createContext("/api/activities", safe(this::handleActivities));
        server.createContext("/api/calendar", safe(this::handleCalendar));
        server.createContext("/api/dashboard", safe(this::handleDashboard));
        server.createContext("/api/reload", safe(this::handleReload));
        server.createContext("/api/upload", safe(this::handleUpload));
        server.createContext("/", safe(this::handleStatic));
        server.start();
        System.out.println("BetterThanBasic running at http://localhost:" + port);
        for (Athletes.Athlete athlete : athletes.all()) {
            System.out.println("  " + athlete.name + ": database " + athlete.databaseFile.toAbsolutePath()
                    + ", uploads " + athlete.uploads.toAbsolutePath());
        }
    }

    /** Answers with a JSON error instead of dropping the connection when a handler fails unexpectedly. */
    private static HttpHandler safe(HttpHandler handler) {
        return exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception exception) {
                System.err.println("Request " + exchange.getRequestURI() + " failed: " + exception);
                try {
                    sendJson(exchange, 500, JsonText.write(Map.of("error", "Server error: " + exception.getMessage())));
                } catch (IOException | RuntimeException ignored) {
                    // The response had already started; nothing more can be sent.
                }
            } finally {
                exchange.close();
            }
        };
    }

    /**
     * Version of the per-ride summary saved in the database. Raise it whenever {@link RideSummary}
     * changes, so every ride's summary is worked out again on the next load.
     */
    private static final int SUMMARY_VERSION = 1;

    /**
     * Rebuilds an athlete's ride list. Each ride's summary (including its best-power curve) is
     * saved in the database the first time it is worked out, so later loads read only those small
     * summaries instead of every recorded second, and rides are read one at a time when a summary
     * is missing. That keeps loading fast and memory low even with a thousand rides.
     */
    private static void reload(Athletes.Athlete athlete) throws IOException {
        FitDatabase database = athlete.database;
        Map<Long, String> saved = database.summaries(SUMMARY_VERSION);
        Map<Long, Long> idByStart = new java.util.HashMap<>();
        List<RideSummary> rides = new java.util.ArrayList<>();
        int computed = 0;
        for (FitDatabase.StoredActivity stored : database.list()) {
            String json = saved.get(stored.id());
            RideSummary summary;
            if (json != null) {
                summary = RideSummary.fromJson(json);
            } else {
                FitActivity activity = database.load(stored.id());
                if (activity == null) {
                    continue;
                }
                summary = RideSummary.of(activity);
                database.storeSummary(stored.id(), SUMMARY_VERSION, summary.toJson());
                computed++;
            }
            if (stored.startTime() != null) {
                rides.add(summary);
                idByStart.put(stored.startTime().toEpochMilli(), stored.id());
            }
        }
        if (computed > 0) {
            System.out.println("Worked out summaries for " + computed + " ride(s) of " + athlete.name + ".");
        }
        athlete.rides = List.copyOf(rides);
        athlete.databaseIdByStart = idByStart;
        athlete.loaded = true;
    }

    /** The viewer's time zone from {@code ?tz=} (e.g. Europe/Copenhagen), or the server's own. */
    private static ZoneId zone(HttpExchange exchange) {
        String tz = query(exchange, "tz");
        try {
            return tz == null || tz.isBlank() ? ZoneId.systemDefault() : ZoneId.of(tz);
        } catch (java.time.DateTimeException exception) {
            return ZoneId.systemDefault();
        }
    }

    private static LocalDate date(String text) {
        try {
            return text == null ? null : LocalDate.parse(text);
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }

    private static void badRequest(HttpExchange exchange, String message) throws IOException {
        sendJson(exchange, 400, JsonText.write(Map.of("error", message)));
    }

    /**
     * {@code GET /api/calendar?athlete=&from=YYYY-MM-DD&weeks=N&tz=} — N weeks from the week containing
     * {@code from}: each week's ISO number and totals, and the rides on each day.
     */
    private void handleCalendar(HttpExchange exchange) throws IOException {
        Athletes.Athlete athlete = athlete(exchange);
        if (athlete == null) {
            return;
        }
        LocalDate from = date(query(exchange, "from"));
        String weeks = query(exchange, "weeks");
        if (from == null || weeks == null || !weeks.matches("\\d{1,3}")) {
            badRequest(exchange, "Use ?from=YYYY-MM-DD&weeks=N");
            return;
        }
        Map<String, Object> view = CalendarView.weeks(loaded(athlete).rides, from, Integer.parseInt(weeks), zone(exchange));
        sendJson(exchange, 200, JsonText.write(view));
    }

    /**
     * {@code GET /api/dashboard?athlete=&period=7|42|90|365|all} or {@code &from=YYYY-MM-DD&to=YYYY-MM-DD},
     * with {@code &tz=}: totals, best power per duration, best ever and estimated FTP for that period.
     */
    private void handleDashboard(HttpExchange exchange) throws IOException {
        Athletes.Athlete athlete = athlete(exchange);
        if (athlete == null) {
            return;
        }
        DashboardView.Period period;
        String chosen = query(exchange, "period");
        LocalDate from = date(query(exchange, "from"));
        LocalDate to = date(query(exchange, "to"));
        if (from != null && to != null) {
            period = DashboardView.Period.between(from, to);
        } else if (chosen == null || chosen.equals("all")) {
            period = DashboardView.Period.allTime();
        } else if (chosen.matches("\\d{1,5}") && Integer.parseInt(chosen) > 0) {
            period = DashboardView.Period.lastDays(Integer.parseInt(chosen));
        } else {
            badRequest(exchange, "Use ?period=<days>|all or ?from=YYYY-MM-DD&to=YYYY-MM-DD");
            return;
        }
        Map<String, Object> view = DashboardView.build(loaded(athlete).rides, period, zone(exchange), java.time.Instant.now());
        sendJson(exchange, 200, JsonText.write(view));
    }

    /** Loads an athlete's trainings the first time they are needed (or again after an upload). */
    private static Athletes.Athlete loaded(Athletes.Athlete athlete) throws IOException {
        if (!athlete.loaded) {
            reload(athlete);
        }
        return athlete;
    }

    /** The athlete named by {@code ?athlete=}, or null after answering 404 when there is no such athlete. */
    private Athletes.Athlete athlete(HttpExchange exchange) throws IOException {
        Athletes.Athlete athlete = athletes.get(query(exchange, "athlete"));
        if (athlete == null) {
            sendJson(exchange, 404, "{\"error\":\"Unknown athlete\"}");
        }
        return athlete;
    }

    private static String query(HttpExchange exchange, String key) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) {
            return null;
        }
        for (String pair : raw.split("&")) {
            if (pair.startsWith(key + "=")) {
                return java.net.URLDecoder.decode(pair.substring(key.length() + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /**
     * Changing requests must be POSTs carrying {@link #WRITE_HEADER}. Other websites cannot send that
     * header without the browser first asking permission (CORS preflight), which this server never grants.
     */
    private static boolean allowedToWrite(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"POST only\"}");
            return false;
        }
        if (!"1".equals(exchange.getRequestHeaders().getFirst(WRITE_HEADER))) {
            sendJson(exchange, 403, "{\"error\":\"Missing " + WRITE_HEADER + " header\"}");
            return false;
        }
        return true;
    }

    /**
     * {@code GET /api/athletes} lists athletes; {@code POST /api/athletes?name=} adds one;
     * {@code POST /api/athletes/rename?athlete=&name=} renames one.
     */
    private void handleAthletes(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("GET".equals(exchange.getRequestMethod()) && path.equals("/api/athletes")) {
            sendJson(exchange, 200, JsonText.write(athletes.all().stream()
                    .map(athlete -> Map.of("id", athlete.id, "name", athlete.name)).toList()));
            return;
        }
        if (!allowedToWrite(exchange)) {
            return;
        }
        try {
            Athletes.Athlete athlete;
            if (path.equals("/api/athletes")) {
                athlete = athletes.add(query(exchange, "name"));
            } else if (path.equals("/api/athletes/rename")) {
                athlete = athlete(exchange);
                if (athlete == null) {
                    return;
                }
                athletes.rename(athlete, query(exchange, "name"));
            } else {
                sendJson(exchange, 404, "{\"error\":\"Not found\"}");
                return;
            }
            sendJson(exchange, 200, JsonText.write(Map.of("id", athlete.id, "name", athlete.name)));
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, JsonText.write(Map.of("error", exception.getMessage())));
        }
    }

    /**
     * {@code POST /api/upload?athlete=<id>&name=<file name>} with the raw FIT or GoldenCheetah JSON file as the body. The file
     * is saved in that athlete's uploads folder and imported into their database. The training list is
     * rebuilt on the next request, so a batch of uploads only pays for that once.
     */
    private void handleUpload(HttpExchange exchange) throws IOException {
        if (!allowedToWrite(exchange)) {
            return;
        }
        // Read the whole body before answering: replying while part of it is unread can break the next
        // request on the same keep-alive connection, which matters when a batch of files is uploaded.
        byte[] body = exchange.getRequestBody().readNBytes(MAX_UPLOAD_BYTES + 1);
        Athletes.Athlete athlete = athlete(exchange);
        if (athlete == null) {
            return;
        }
        String name = uploadName(query(exchange, "name"));
        if (name == null) {
            sendJson(exchange, 400, "{\"status\":\"failed\",\"error\":\"Only .fit and .json files can be uploaded\"}");
            return;
        }
        if (body.length > MAX_UPLOAD_BYTES) {
            sendJson(exchange, 413, uploadResult(name, "failed", "File is larger than 50 MB"));
            return;
        }
        FitDatabase database = athlete.database;
        if (database.contains(name)) {
            sendJson(exchange, 200, uploadResult(name, "exists", null));
            return;
        }
        Files.createDirectories(athlete.uploads);
        Path file = athlete.uploads.resolve(name);
        Files.write(file, body);
        FitDatabase.ImportStatus status;
        try {
            status = database.importNew(file);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(file);
            String reason = String.valueOf(exception.getMessage());
            reason = reason.substring(reason.lastIndexOf(": ") + 1).trim(); // drop the server-side path
            sendJson(exchange, 400, uploadResult(name, "failed", reason.isEmpty() ? "Could not read the training file" : reason));
            return;
        }
        if (status != FitDatabase.ImportStatus.ADDED) {
            // Same ride already stored from another file (e.g. FIT and JSON of one ride): keep only the first.
            Files.deleteIfExists(file);
            sendJson(exchange, 200, uploadResult(name, "exists", null));
            return;
        }
        // Work out the new ride's summary now, so the reload after a big batch only reads saved summaries.
        Long id = database.idOf(name);
        FitActivity added = id == null ? null : database.load(id);
        if (added != null) {
            database.storeSummary(id, SUMMARY_VERSION, RideSummary.of(added).toJson());
        }
        athlete.loaded = false; // the cached training list is rebuilt on the next request
        sendJson(exchange, 200, uploadResult(name, "added", null));
    }

    /** The uploaded file's name reduced to safe characters, or null if it is not a .fit or .json file. */
    private static String uploadName(String decoded) {
        if (decoded == null) {
            return null;
        }
        String base = decoded.substring(Math.max(decoded.lastIndexOf('/'), decoded.lastIndexOf('\\')) + 1);
        String safe = base.replaceAll("[^A-Za-z0-9._() -]", "_");
        boolean ok = FitDatabase.isTrainingFile(Path.of(safe)) && !safe.startsWith(".") && safe.length() <= 200;
        return ok ? safe : null;
    }

    /** {@code {"name": ..., "status": ..., "error": ...}} for upload answers (error left out when null). */
    private static String uploadResult(String name, String status, String error) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("name", name);
        result.put("status", status);
        if (error != null) {
            result.put("error", error);
        }
        return JsonText.write(result);
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * {@code GET /api/activities} — every ride's summary, oldest first;
     * {@code GET /api/activities/{id}} — one ride's summary with the ids of the rides before and after it;
     * {@code GET /api/activities/{id}/graph?points=N} — its graph data (see {@link RideGraph}).
     * The id is the ride's start time in epoch ms.
     */
    private void handleActivities(HttpExchange exchange) throws IOException {
        Athletes.Athlete athlete = athlete(exchange);
        if (athlete == null) {
            return;
        }
        List<RideSummary> rides = loaded(athlete).rides;
        String[] parts = exchange.getRequestURI().getPath().split("/");
        if (parts.length == 3) {
            sendJson(exchange, 200, JsonText.write(rides.stream().map(RideSummary::toMap).toList()));
            return;
        }
        int index = parts.length >= 4 && parts[3].matches("\\d{1,18}") ? indexOf(rides, Long.parseLong(parts[3])) : -1;
        if (index < 0) {
            sendJson(exchange, 404, JsonText.write(Map.of("error", "No such training")));
            return;
        }
        RideSummary ride = rides.get(index);
        if (parts.length == 4) {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("ride", ride.toMap());
            result.put("previousId", index > 0 ? rides.get(index - 1).id() : null);
            result.put("nextId", index < rides.size() - 1 ? rides.get(index + 1).id() : null);
            sendJson(exchange, 200, JsonText.write(result));
            return;
        }
        if (parts.length == 5 && parts[4].equals("graph")) {
            String points = query(exchange, "points");
            int count = points != null && points.matches("\\d{1,5}") ? Integer.parseInt(points) : 1000;
            Long databaseId = athlete.databaseIdByStart.get(ride.id());
            FitActivity activity = databaseId == null ? null : athlete.database.load(databaseId);
            if (activity != null) {
                sendJson(exchange, 200, JsonText.write(RideGraph.build(activity, count)));
                return;
            }
        }
        sendJson(exchange, 404, JsonText.write(Map.of("error", "Not found")));
    }

    /** Position of the ride with this id in the (start-time ordered) list, or -1. */
    private static int indexOf(List<RideSummary> rides, long id) {
        int low = 0;
        int high = rides.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            long start = rides.get(middle).id();
            if (start < id) {
                low = middle + 1;
            } else if (start > id) {
                high = middle - 1;
            } else {
                return middle;
            }
        }
        return -1;
    }

    /** {@code POST /api/reload?athlete=} re-reads that athlete's database and returns the ride list. */
    private void handleReload(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain", "POST only".getBytes(StandardCharsets.UTF_8));
            return;
        }
        Athletes.Athlete athlete = athlete(exchange);
        if (athlete != null) {
            reload(athlete);
            sendJson(exchange, 200, JsonText.write(athlete.rides.stream().map(RideSummary::toMap).toList()));
        }
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        String requested = exchange.getRequestURI().getPath();
        if (requested.equals("/")) {
            requested = "/index.html";
        }
        Path file = frontend.resolve(requested.substring(1)).normalize();
        if (!file.startsWith(frontend) || !Files.isRegularFile(file)) {
            send(exchange, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String name = file.getFileName().toString();
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        send(exchange, 200, CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"),
                Files.readAllBytes(file));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
