"use strict";

// BetterThanBasic calendar. The server works out everything shown (rides per day, week numbers and
// week totals) via /api/calendar; this file only fetches weeks, formats them and handles scrolling.

const WEEKS_AROUND = 8;      // weeks shown before/after the target when jumping
const WEEKS_PER_LOAD = 8;    // weeks fetched when scrolling near the top or bottom
const LOAD_MARGIN_PX = 400;  // how close to an edge the scroll must get before loading more
const DAY_NAMES = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const BIKE_ICON = '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="5.5" cy="16.5" r="3.5"/>'
    + '<circle cx="18.5" cy="16.5" r="3.5"/><path d="M5.5 16.5 9 9h6l3.5 7.5M9 9l3 7.5h-6.5M15 9l-1.5-3H11'
    + 'M13.5 6h2"/></svg>';

const state = {
    first: startOfWeek(new Date()), // Monday of the first week on screen
    last: startOfWeek(new Date()),  // Monday of the last week on screen
    latestRideDate: null,           // "YYYY-MM-DD" of the newest ride, from the server
    loading: false,                 // a week fetch is running (scrolling waits for it)
};

// ---------- Data ----------

const WEEKS_PER_REQUEST = 52; // the server answers at most 60 weeks per request

/** {@code count} weeks from the server, starting with the week of {@code monday}. */
async function fetchWeeks(monday, count) {
    const weeks = [];
    for (let done = 0; done < count; done += WEEKS_PER_REQUEST) {
        const result = await apiGet("/api/calendar", {
            from: dayKey(addDays(monday, 7 * done)),
            weeks: Math.min(WEEKS_PER_REQUEST, count - done),
        });
        state.latestRideDate = result.latestRideDate;
        el("status").textContent = `${result.rideCount} training${result.rideCount === 1 ? "" : "s"} stored`;
        weeks.push(...result.weeks);
    }
    return weeks;
}

/** Runs one week fetch at a time, reporting problems in the status line. */
async function guarded(task) {
    if (state.loading) return;
    state.loading = true;
    try {
        await task();
    } catch (error) {
        el("status").textContent = `Could not load the calendar: ${error.message}`;
    } finally {
        state.loading = false;
    }
}

// ---------- Rendering (formatting only) ----------

function weeksHtml(weeks) {
    const today = dayKey(new Date());
    return weeks.map((week) => {
        const days = week.days.map((day, i) => {
            const date = parseKey(day.date);
            const classes = ["day"];
            if (day.date === today) classes.push("today");
            if (i >= 5) classes.push("weekend");
            const label = date.getDate() === 1 || i === 0 ? shortDate.format(date) : date.getDate();
            return `<div class="${classes.join(" ")}">
                <div class="band">${day.date === today ? "Today" : label}</div>
                <div class="workouts">${day.rides.map(workoutCard).join("")}</div>
            </div>`;
        }).join("");
        return `<section class="week" data-monday="${week.monday}">${weekSummary(week)}${days}</section>`;
    }).join("");
}

function weekSummary(week) {
    const totals = week.totals;
    const rows = totals.rides === 0 ? "" : `
        <table>
            <tr><td>Trainings</td><td>${totals.rides}</td></tr>
            <tr><td>Duration</td><td>${formatDuration(totals.durationSeconds)}</td></tr>
            <tr><td>Distance</td><td>${fixed(totals.distanceKm, 1)} km</td></tr>
            <tr><td>TSS</td><td>${totals.tss == null ? "–" : fixed(totals.tss)}</td></tr>
        </table>`;
    return `<div class="week-summary"><div class="band">Week ${week.isoWeek}</div>${rows}</div>`;
}

/** A training in the calendar; it links to that training's own page (ride.html). */
function workoutCard(a) {
    const start = new Date(a.start);
    const part = (value, unit) => (value == null ? "–" : `${value}${unit}`);
    const line1 = [
        formatDuration(a.durationSeconds),
        part(a.distanceKm > 0 ? fixed(a.distanceKm, 1) : null, " km"),
        part(fixed(a.tss), " TSS"),
    ].join(" / ");
    const line2 = [
        part(a.avgSpeedKmh > 0 ? fixed(a.avgSpeedKmh, 1) : null, " km/h"),
        part(fixed(a.avgPower), " W"),
        `<span class="hr">${part(fixed(a.avgHeartRate), "&#9829;")}</span>`,
    ].join(" / ");
    return `<a class="workout" href="${rideUrl(a)}"
            title="Time / distance / TSS · avg speed / avg power / avg heart rate">
        <span class="workout-icon">${BIKE_ICON}</span>
        <span class="workout-text">
            <div class="workout-name">Ride ${timeOfDay.format(start)}</div>
            <div class="workout-line">${line1}</div>
            <div class="workout-line">${line2}</div>
        </span>
    </a>`;
}

const weeksBetween = (from, to) => Math.round((to - from) / (7 * 86400000)) + 1;

async function renderRange(first, last) {
    const weeks = await fetchWeeks(first, weeksBetween(first, last));
    state.first = first;
    state.last = last;
    el("weeks").innerHTML = weeksHtml(weeks);
}

/** Adds earlier weeks above the current ones without moving what the user is looking at. */
async function prependWeeks(count) {
    const newFirst = addDays(state.first, -7 * count);
    const weeks = await fetchWeeks(newFirst, count);
    const container = el("calendar");
    const heightBefore = container.scrollHeight;
    el("weeks").insertAdjacentHTML("afterbegin", weeksHtml(weeks));
    state.first = newFirst;
    container.scrollTop += container.scrollHeight - heightBefore;
}

async function appendWeeks(count) {
    const weeks = await fetchWeeks(addDays(state.last, 7), count);
    el("weeks").insertAdjacentHTML("beforeend", weeksHtml(weeks));
    state.last = addDays(state.last, 7 * count);
}

// ---------- Navigation ----------

function headHeight() {
    return el("cal-head").offsetHeight;
}

/** Monday of the week currently at the top of the scroll area. */
function topWeek() {
    const top = el("calendar").scrollTop + headHeight();
    for (const section of el("weeks").children) {
        if (section.offsetTop + section.offsetHeight > top + 1) return parseKey(section.dataset.monday);
    }
    return state.first;
}

async function scrollToWeek(monday) {
    if (monday < state.first) await prependWeeks(weeksBetween(monday, state.first) - 1 + WEEKS_PER_LOAD);
    if (monday > state.last) await appendWeeks(weeksBetween(state.last, monday) - 1 + WEEKS_PER_LOAD);
    const section = el("weeks").querySelector(`[data-monday="${dayKey(monday)}"]`);
    if (section) el("calendar").scrollTop = section.offsetTop - headHeight();
    updateLabels();
}

function goTo(date) {
    return guarded(async () => {
        const target = startOfWeek(date);
        await renderRange(addDays(target, -7 * WEEKS_AROUND), addDays(target, 7 * WEEKS_AROUND));
        await scrollToWeek(addDays(target, -7)); // keep one week of context above the target
    });
}

function shift(weeks) {
    return guarded(() => scrollToWeek(addDays(topWeek(), weeks * 7)));
}

/**
 * Keeps the header in sync with what is visible. The URL hash remembers the position, so coming
 * back from a ride page returns to the same weeks.
 */
function updateLabels() {
    const top = topWeek();
    el("range-label").textContent = monthYear.format(addDays(top, 3));
    el("jump-date").value = dayKey(top);
    history.replaceState(null, "", `#${dayKey(addDays(top, 7))}`); // goTo() shows this week second
}

/** Loads more weeks when the scroll gets near either edge. */
function onScroll() {
    updateLabels();
    const container = el("calendar");
    if (container.scrollTop < LOAD_MARGIN_PX) {
        guarded(() => prependWeeks(WEEKS_PER_LOAD));
    } else if (container.scrollTop + container.clientHeight > container.scrollHeight - LOAD_MARGIN_PX) {
        guarded(() => appendWeeks(WEEKS_PER_LOAD));
    }
}

/** Fetches the weeks on screen again (after uploads or the reload button), staying on the same weeks. */
async function refresh(reload = false) {
    const top = topWeek();
    el("reload").classList.add("spinning");
    try {
        if (reload) await reloadTrainings();
        await guarded(async () => {
            await renderRange(state.first, state.last);
            await scrollToWeek(top);
        });
    } finally {
        el("reload").classList.remove("spinning");
    }
}

function wireEvents() {
    el("prev").addEventListener("click", () => shift(-1));
    el("next").addEventListener("click", () => shift(1));
    el("today").addEventListener("click", () => goTo(new Date()));
    el("latest").addEventListener("click", () => {
        if (state.latestRideDate) goTo(parseKey(state.latestRideDate));
    });
    el("jump-date").addEventListener("change", (event) => {
        if (event.target.value) goTo(parseKey(event.target.value));
    });
    el("reload").addEventListener("click", () => refresh(true));
    el("calendar").addEventListener("scroll", onScroll, { passive: true });
    document.addEventListener("keydown", (event) => {
        if (event.target.tagName === "INPUT") return;
        if (event.key === "ArrowLeft") shift(-1);
        if (event.key === "ArrowRight") shift(1);
    });
    setupAthleteMenu(() => refresh());
}

async function init() {
    el("cal-head").innerHTML = "<div></div>" + DAY_NAMES.map((d) => `<div>${d}</div>`).join("");
    wireEvents();
    el("status").textContent = "Loading…";
    // index.html#YYYY-MM-DD opens on that week (used by the ride page's "Back to calendar").
    const hash = location.hash.slice(1);
    if (/^\d{4}-\d{2}-\d{2}$/.test(hash)) {
        await goTo(parseKey(hash));
        return;
    }
    await goTo(new Date());
    // Nothing ridden in the last four weeks: show the latest training instead of empty weeks.
    const latest = state.latestRideDate && parseKey(state.latestRideDate);
    if (latest && new Date() - latest > 28 * 86400000) await goTo(latest);
}

init();
