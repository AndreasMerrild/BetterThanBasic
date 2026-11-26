package Server;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The calendar's weeks: the rides on each day and each week's totals, in the viewer's time zone. */
final class CalendarView {
    static final int MAX_WEEKS = 60;

    private CalendarView() {
    }

    /** Monday of the week containing {@code date}. */
    static LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * {@code weekCount} weeks starting with the week that contains {@code from}.
     *
     * @param rides all rides of the athlete, oldest first
     */
    static Map<String, Object> weeks(List<RideSummary> rides, LocalDate from, int weekCount, ZoneId zone) {
        LocalDate firstMonday = mondayOf(from);
        int count = Math.max(1, Math.min(weekCount, MAX_WEEKS));
        long rangeStart = firstMonday.atStartOfDay(zone).toInstant().toEpochMilli();
        long rangeEnd = firstMonday.plusWeeks(count).atStartOfDay(zone).toInstant().toEpochMilli();

        // Group the rides in range by their local start date.
        Map<LocalDate, List<RideSummary>> byDay = new LinkedHashMap<>();
        for (RideSummary ride : rides) {
            if (ride.start() != null && ride.start() >= rangeStart && ride.start() < rangeEnd) {
                LocalDate day = Instant.ofEpochMilli(ride.start()).atZone(zone).toLocalDate();
                byDay.computeIfAbsent(day, key -> new ArrayList<>()).add(ride);
            }
        }

        List<Map<String, Object>> weeks = new ArrayList<>();
        for (int week = 0; week < count; week++) {
            LocalDate monday = firstMonday.plusWeeks(week);
            List<Map<String, Object>> days = new ArrayList<>();
            List<RideSummary> weekRides = new ArrayList<>();
            for (int offset = 0; offset < 7; offset++) {
                LocalDate date = monday.plusDays(offset);
                List<RideSummary> dayRides = byDay.getOrDefault(date, List.of());
                weekRides.addAll(dayRides);
                Map<String, Object> day = new LinkedHashMap<>();
                day.put("date", date.toString());
                day.put("rides", dayRides.stream().map(RideSummary::toMap).toList());
                days.add(day);
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("monday", monday.toString());
            entry.put("isoWeek", monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            entry.put("totals", totals(weekRides));
            entry.put("days", days);
            weeks.add(entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("weeks", weeks);
        result.put("rideCount", rides.size());
        RideSummary latest = rides.isEmpty() ? null : rides.get(rides.size() - 1);
        result.put("latestRideDate", latest == null || latest.start() == null ? null
                : Instant.ofEpochMilli(latest.start()).atZone(zone).toLocalDate().toString());
        return result;
    }

    /** Count, time, distance and TSS of some rides (TSS null when none of them has one). */
    static Map<String, Object> totals(List<RideSummary> rides) {
        long seconds = 0;
        double distance = 0;
        double tss = 0;
        boolean anyTss = false;
        for (RideSummary ride : rides) {
            seconds += ride.durationSeconds();
            distance += ride.distanceKm();
            if (ride.tss() != null) {
                tss += ride.tss();
                anyTss = true;
            }
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("rides", rides.size());
        totals.put("ridesWithPower", rides.stream().filter(RideSummary::hasPower).count());
        totals.put("durationSeconds", seconds);
        totals.put("distanceKm", distance);
        totals.put("tss", anyTss ? tss : null);
        return totals;
    }
}
