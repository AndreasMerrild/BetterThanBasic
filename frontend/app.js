"use strict";

// BetterThanBasic calendar: plain JavaScript, no dependencies.

const WEEKS_AROUND = 8;      // weeks rendered before/after the target when jumping
const WEEKS_PER_LOAD = 8;    // weeks added when scrolling near the top or bottom
const LOAD_MARGIN_PX = 400;  // how close to an edge the scroll must get before loading more
const DAY_NAMES = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const BIKE_ICON = '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="5.5" cy="16.5" r="3.5"/>'
    + '<circle cx="18.5" cy="16.5" r="3.5"/><path d="M5.5 16.5 9 9h6l3.5 7.5M9 9l3 7.5h-6.5M15 9l-1.5-3H11'
    + 'M13.5 6h2"/></svg>';

const state = {
    activities: [],
    byDay: new Map(),   // "YYYY-MM-DD" -> activities that started that (local) day
    first: startOfWeek(new Date()), // Monday of the first rendered week
    last: startOfWeek(new Date()),  // Monday of the last rendered week
};

const el = (id) => document.getElementById(id);

// ---------- Date helpers (all local time) ----------

function startOfWeek(date) {
    const d = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
    return d;
}

function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
}

function dayKey(date) {
    const pad = (n) => String(n).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function isoWeek(date) {
    const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
    d.setUTCDate(d.getUTCDate() + 4 - (d.getUTCDay() || 7));
    const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
    return Math.ceil(((d - yearStart) / 86400000 + 1) / 7);
}

const shortDate = new Intl.DateTimeFormat(undefined, { day: "numeric", month: "short" });
const longDate = new Intl.DateTimeFormat(undefined, { weekday: "long", day: "numeric", month: "long", year: "numeric" });
const timeOfDay = new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" });

// ---------- Formatting ----------

function formatDuration(seconds) {
    if (seconds == null) return null;
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    return h > 0 ? `${h}h:${String(m).padStart(2, "0")}m` : `${m}m:${String(Math.round(seconds % 60)).padStart(2, "0")}s`;
}

function fixed(value, digits = 0) {
    return value == null ? null : value.toFixed(digits);
}

function escapeHtml(text) {
    return String(text).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

// ---------- Data ----------

async function loadActivities(reload = false) {
    el("status").textContent = "Loading…";
    el("reload").classList.add("spinning");
    try {
        const response = await fetch(reload ? "/api/reload" : "/api/activities", { method: reload ? "POST" : "GET" });
        if (!response.ok) throw new Error(`Server answered ${response.status}`);
        setActivities(await response.json());
        el("status").textContent = `${state.activities.length} trainings stored`;
    } catch (error) {
        el("status").textContent = `Could not load trainings: ${error.message}`;
    } finally {
        el("reload").classList.remove("spinning");
    }
}

function setActivities(list) {
    state.activities = list.filter((a) => a.start != null).sort((a, b) => a.start - b.start);
    state.byDay = new Map();
    for (const activity of state.activities) {
        const key = dayKey(new Date(activity.start));
        if (!state.byDay.has(key)) state.byDay.set(key, []);
        state.byDay.get(key).push(activity);
    }
}

// ---------- Rendering ----------

/** HTML for every week from Monday {@code from} through Monday {@code to}, inclusive. */
function weeksHtml(from, to) {
    const today = dayKey(new Date());
    let html = "";
    for (let monday = from; monday <= to; monday = addDays(monday, 7)) {
        let days = "";
        const weekActivities = [];
        for (let i = 0; i < 7; i++) {
            const date = addDays(monday, i);
            const key = dayKey(date);
            const activities = state.byDay.get(key) || [];
            weekActivities.push(...activities);
            const classes = ["day"];
            if (key === today) classes.push("today");
            if (i >= 5) classes.push("weekend");
            const label = date.getDate() === 1 || i === 0 ? shortDate.format(date) : date.getDate();
            days += `<div class="${classes.join(" ")}">
                <div class="band">${key === today ? "Today" : label}</div>
                <div class="workouts">${activities.map(workoutCard).join("")}</div>
            </div>`;
        }
        html += `<section class="week" data-monday="${dayKey(monday)}">`
            + `${weekSummary(monday, weekActivities)}${days}</section>`;
    }
    return html;
}

function renderRange(first, last) {
    state.first = first;
    state.last = last;
    el("weeks").innerHTML = weeksHtml(first, last);
}

/** Adds weeks above the current ones without moving what the user is looking at. */
function prependWeeks(count) {
    const container = el("calendar");
    const heightBefore = container.scrollHeight;
    const newFirst = addDays(state.first, -7 * count);
    el("weeks").insertAdjacentHTML("afterbegin", weeksHtml(newFirst, addDays(state.first, -7)));
    state.first = newFirst;
    container.scrollTop += container.scrollHeight - heightBefore;
}

function appendWeeks(count) {
    const newLast = addDays(state.last, 7 * count);
    el("weeks").insertAdjacentHTML("beforeend", weeksHtml(addDays(state.last, 7), newLast));
    state.last = newLast;
}

function weekSummary(monday, activities) {
    const sum = (field) => activities.reduce((total, a) => total + (a[field] || 0), 0);
    const tss = sum("tss");
    const rows = activities.length === 0 ? "" : `
        <table>
            <tr><td>Trainings</td><td>${activities.length}</td></tr>
            <tr><td>Duration</td><td>${formatDuration(sum("durationSeconds"))}</td></tr>
            <tr><td>Distance</td><td>${sum("distanceKm").toFixed(1)} km</td></tr>
            <tr><td>TSS</td><td>${tss > 0 ? Math.round(tss) : "–"}</td></tr>
        </table>`;
    return `<div class="week-summary"><div class="band">Week ${isoWeek(monday)}</div>${rows}</div>`;
}

function workoutCard(a) {
    const start = new Date(a.start);
    const part = (value, unit) => (value == null ? "–" : `${value}${unit}`);
    const line1 = [
        formatDuration(a.durationSeconds),
        part(a.distanceKm > 0 ? fixed(a.distanceKm, 1) : null, " km"),
        part(a.tss ? Math.round(a.tss) : null, " TSS"),
    ].join(" / ");
    const line2 = [
        part(a.avgSpeedKmh > 0 ? fixed(a.avgSpeedKmh, 1) : null, " km/h"),
        part(fixed(a.avgPower), " W"),
        `<span class="hr">${part(fixed(a.avgHeartRate), "&#9829;")}</span>`,
    ].join(" / ");
    return `<button class="workout" data-id="${a.id}" type="button"
            title="Time / distance / TSS · avg speed / avg power / avg heart rate">
        <span class="workout-icon">${BIKE_ICON}</span>
        <span class="workout-text">
            <div class="workout-name">Ride ${timeOfDay.format(start)}</div>
            <div class="workout-line">${line1}</div>
            <div class="workout-line">${line2}</div>
        </span>
    </button>`;
}

function showDetails(activity) {
    const start = new Date(activity.start);
    el("details-title").textContent = `Ride · ${timeOfDay.format(start)}`;
    el("details-sub").textContent = longDate.format(start) + (activity.device ? ` · ${activity.device}` : "");
    const rows = [
        ["Duration", formatDuration(activity.durationSeconds)],
        ["Distance", fixed(activity.distanceKm, 2), "km"],
        ["Avg speed", fixed(activity.avgSpeedKmh, 1), "km/h"],
        ["Avg power", fixed(activity.avgPower), "W"],
        ["Normalized power", fixed(activity.normalizedPower), "W"],
        ["Avg heart rate", fixed(activity.avgHeartRate), "bpm"],
        ["Max heart rate", activity.maxHeartRate, "bpm"],
        ["Avg cadence", fixed(activity.avgCadence), "rpm"],
        ["FTP (head unit)", activity.ftp, "W"],
        ["Intensity factor", fixed(activity.intensityFactor, 2)],
        ["TSS", fixed(activity.tss)],
        ["Laps", activity.laps],
    ];
    el("details-list").innerHTML = rows.map(([label, value, unit]) =>
        `<dt>${label}</dt><dd>${value == null ? "–" : escapeHtml(value) + (unit ? ` ${unit}` : "")}</dd>`).join("");
    el("details").showModal();
}

// ---------- Navigation ----------

const monthYear = new Intl.DateTimeFormat(undefined, { month: "long", year: "numeric" });

function parseKey(key) {
    const [y, m, d] = key.split("-").map(Number);
    return new Date(y, m - 1, d);
}

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

function scrollToWeek(monday) {
    while (monday < state.first) prependWeeks(WEEKS_PER_LOAD);
    while (monday > state.last) appendWeeks(WEEKS_PER_LOAD);
    const section = el("weeks").querySelector(`[data-monday="${dayKey(monday)}"]`);
    el("calendar").scrollTop = section.offsetTop - headHeight();
    onScroll();
}

function goTo(date) {
    const target = startOfWeek(date);
    renderRange(addDays(target, -7 * WEEKS_AROUND), addDays(target, 7 * WEEKS_AROUND));
    scrollToWeek(addDays(target, -7)); // keep one week of context above the target
}

function shift(weeks) {
    scrollToWeek(addDays(topWeek(), weeks * 7));
}

/** Loads more weeks near either edge and keeps the header label in sync with what is visible. */
function onScroll() {
    const container = el("calendar");
    if (container.scrollTop < LOAD_MARGIN_PX) prependWeeks(WEEKS_PER_LOAD);
    if (container.scrollTop + container.clientHeight > container.scrollHeight - LOAD_MARGIN_PX) {
        appendWeeks(WEEKS_PER_LOAD);
    }
    const top = topWeek();
    el("range-label").textContent = monthYear.format(addDays(top, 3));
    el("jump-date").value = dayKey(top);
}

function wireEvents() {
    el("prev").addEventListener("click", () => shift(-1));
    el("next").addEventListener("click", () => shift(1));
    el("today").addEventListener("click", () => goTo(new Date()));
    el("latest").addEventListener("click", () => {
        const latest = state.activities.at(-1);
        if (latest) goTo(new Date(latest.start));
    });
    el("jump-date").addEventListener("change", (event) => {
        if (event.target.value) {
            const [y, m, d] = event.target.value.split("-").map(Number);
            goTo(new Date(y, m - 1, d));
        }
    });
    el("reload").addEventListener("click", async () => {
        const top = topWeek();
        await loadActivities(true);
        renderRange(state.first, state.last);
        scrollToWeek(top);
    });
    el("calendar").addEventListener("scroll", onScroll, { passive: true });
    el("weeks").addEventListener("click", (event) => {
        const card = event.target.closest(".workout");
        if (!card) return;
        const activity = state.activities.find((a) => a.id === Number(card.dataset.id));
        if (activity) showDetails(activity);
    });
    el("details").addEventListener("click", (event) => {
        if (event.target === el("details")) el("details").close(); // click on backdrop
    });
    document.addEventListener("keydown", (event) => {
        if (event.target.tagName === "INPUT" || el("details").open) return;
        if (event.key === "ArrowLeft") shift(-1);
        if (event.key === "ArrowRight") shift(1);
    });
}

async function init() {
    el("cal-head").innerHTML = "<div></div>" + DAY_NAMES.map((d) => `<div>${d}</div>`).join("");
    wireEvents();
    await loadActivities();
    // Open on today, unless nothing has been ridden recently: then show the latest training.
    const latest = state.activities.at(-1);
    const recent = latest && Date.now() - latest.start < 28 * 86400000;
    goTo(latest && !recent ? new Date(latest.start) : new Date());
}

init();
