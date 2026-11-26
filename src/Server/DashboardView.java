package Server;

import Application.BestPowerNumbers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The dashboard's numbers for one period: totals, best power per duration and the best ever. */
final class DashboardView {
    /** Estimated FTP = this share of the best 20-minute power (the usual rule of thumb). */
    static final double FTP_FACTOR = 0.95;
    static final int FTP_TEST_SECONDS = 20 * 60;

    /** A period of rides: the last N days, all time, or custom dates (both days included). */
    record Period(String kind, int days, LocalDate from, LocalDate to) {
        static Period lastDays(int days) {
            return new Period("days", days, null, null);
        }

        static Period allTime() {
            return new Period("all", 0, null, null);
        }

        static Period between(LocalDate from, LocalDate to) {
            return from.isAfter(to) ? new Period("custom", 0, to, from) : new Period("custom", 0, from, to);
        }

        boolean contains(RideSummary ride, ZoneId zone, Instant now) {
            if (ride.start() == null) {
                return false;
            }
            long start = ride.start();
            return switch (kind) {
                case "days" -> start >= now.minus(Duration.ofDays(days)).toEpochMilli() && start <= now.toEpochMilli();
                case "custom" -> start >= from.atStartOfDay(zone).toInstant().toEpochMilli()
                        && start < to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
                default -> true;
            };
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("kind", kind);
            if (kind.equals("days")) {
                map.put("days", days);
            }
            if (kind.equals("custom")) {
                map.put("from", from.toString());
                map.put("to", to.toString());
            }
            return map;
        }
    }

    /** The best power for one duration and the ride it came from. */
    private record Best(double watts, RideSummary ride) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("watts", watts);
            map.put("rideId", ride.id());
            map.put("rideStart", ride.start());
            return map;
        }
    }

    private DashboardView() {
    }

    /**
     * Everything the dashboard shows for {@code period}.
     *
     * @param rides all rides of the athlete
     */
    static Map<String, Object> build(List<RideSummary> rides, Period period, ZoneId zone, Instant now) {
        List<RideSummary> inPeriod = rides.stream().filter(ride -> period.contains(ride, zone, now)).toList();
        Map<Integer, Best> periodBests = bests(inPeriod);
        Map<Integer, Best> everBests = bests(rides);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int seconds : BestPowerNumbers.intervalsSeconds()) {
            Best ever = everBests.get(seconds);
            if (ever == null) {
                continue; // no ride is long enough for this duration
            }
            Best best = periodBests.get(seconds);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seconds", seconds);
            row.put("best", best == null ? null : best.toMap());
            row.put("bestEver", ever.toMap());
            row.put("percentOfBestEver", best == null ? null : Math.round(best.watts() / ever.watts() * 100));
            row.put("isBestEver", best != null && best.ride().id() == ever.ride().id());
            rows.add(row);
        }

        Best twenty = periodBests.get(FTP_TEST_SECONDS);
        Map<String, Object> ftp = null;
        if (twenty != null) {
            ftp = new LinkedHashMap<>();
            ftp.put("watts", Math.round(twenty.watts() * FTP_FACTOR));
            ftp.put("factor", FTP_FACTOR);
            ftp.put("fromSeconds", FTP_TEST_SECONDS);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period.toMap());
        result.put("totals", CalendarView.totals(inPeriod));
        result.put("estimatedFtp", ftp);
        result.put("bests", rows);
        result.put("rideCount", rides.size());
        result.put("ridesWithPower", rides.stream().filter(RideSummary::hasPower).count());
        return result;
    }

    /** For every duration, the highest best power among {@code rides} and which ride it is from. */
    private static Map<Integer, Best> bests(List<RideSummary> rides) {
        Map<Integer, Best> bests = new TreeMap<>();
        for (RideSummary ride : rides) {
            for (Map.Entry<Integer, Double> entry : ride.bestPower().entrySet()) {
                Best current = bests.get(entry.getKey());
                if (current == null || entry.getValue() > current.watts()) {
                    bests.put(entry.getKey(), new Best(entry.getValue(), ride));
                }
            }
        }
        return bests;
    }
}
