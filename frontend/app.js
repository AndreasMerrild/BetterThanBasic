"use strict";

// BetterThanBasic calendar: plain JavaScript, no dependencies.

const WEEKS_SHOWN = 6;
const DAY_NAMES = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const BIKE_ICON = '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="5.5" cy="16.5" r="3.5"/>'
    + '<circle cx="18.5" cy="16.5" r="3.5"/><path d="M5.5 16.5 9 9h6l3.5 7.5M9 9l3 7.5h-6.5M15 9l-1.5-3H11'
    + 'M13.5 6h2"/></svg>';

const state = {
    activities: [],
    byDay: new Map(),   // "YYYY-MM-DD" -> activities that started that (local) day
    anchor: startOfWeek(new Date()), // Monday of the first week shown
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

function metric(value, unit, cls = "") {
    if (value == null) return `<span class="metric na"><b>–</b><span class="unit">${unit}</span></span>`;
    return `<span class="metric"><b class="${cls}">${value}</b><span class="unit">${unit}</span></span>`;
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

function render() {
    const today = dayKey(new Date());
    const first = state.anchor;
    const last = addDays(first, WEEKS_SHOWN * 7 - 1);
    el("range-label").textContent = `${shortDate.format(first)} – ${shortDate.format(last)} ${last.getFullYear()}`;
    el("jump-date").value = dayKey(first);

    el("cal-head").innerHTML = "<div></div>" + DAY_NAMES.map((d) => `<div>${d}</div>`).join("");

    const focusMonth = addDays(first, 14).getMonth();
    let html = "";
    for (let w = 0; w < WEEKS_SHOWN; w++) {
        const monday = addDays(first, w * 7);
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
            if (date.getMonth() !== focusMonth) classes.push("other-month");
            const label = date.getDate() === 1 || i === 0 ? shortDate.format(date) : date.getDate();
            days += `<div class="${classes.join(" ")}">
                <div class="band">${key === today ? "Today" : label}</div>
                <div class="workouts">${activities.map(workoutCard).join("")}</div>
            </div>`;
        }
        html += `<section class="week">${weekSummary(monday, weekActivities)}${days}</section>`;
    }
    el("weeks").innerHTML = html;
}

function weekSummary(monday, activities) {
    const sum = (field) => activities.reduce((total, a) => total + (a[field] || 0), 0);
    const tss = sum("tss");
    const rows = activities.length === 0 ? "" : `
        <table>
            <tr><td>Trainings</td><td>${activities.length}</td></tr>
            <tr><td>TSS</td><td>${tss > 0 ? `<span class="tss">${Math.round(tss)}</span>` : "–"}</td></tr>
            <tr><td>Time</td><td>${formatDuration(sum("durationSeconds"))}</td></tr>
            <tr><td>Distance</td><td>${sum("distanceKm").toFixed(1)} km</td></tr>
        </table>`;
    return `<div class="week-summary"><div class="band">Week ${isoWeek(monday)}</div>${rows}</div>`;
}

function workoutCard(a) {
    const start = new Date(a.start);
    return `<button class="workout" data-id="${a.id}" type="button">
        <div class="workout-title">
            <span class="workout-icon">${BIKE_ICON}</span>
            <span class="workout-name">Ride<small>${timeOfDay.format(start)}${a.tss ? ` · ${Math.round(a.tss)} TSS` : ""}</small></span>
        </div>
        <div class="metrics">
            ${metric(a.distanceKm > 0 ? fixed(a.distanceKm, 1) : null, "km")}
            ${metric(formatDuration(a.durationSeconds), "")}
            ${metric(a.avgSpeedKmh > 0 ? fixed(a.avgSpeedKmh, 1) : null, "km/h")}
            ${metric(fixed(a.avgPower), "W")}
            ${metric(fixed(a.avgHeartRate), "bpm", "hr")}
        </div>
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

function goTo(date) {
    state.anchor = addDays(startOfWeek(date), -7); // keep one week of context above the target
    render();
}

function shift(weeks) {
    state.anchor = addDays(state.anchor, weeks * 7);
    render();
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
        await loadActivities(true);
        render();
    });
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
    wireEvents();
    await loadActivities();
    // Open on today, unless nothing has been ridden recently: then show the latest training.
    const latest = state.activities.at(-1);
    const recent = latest && Date.now() - latest.start < 28 * 86400000;
    goTo(latest && !recent ? new Date(latest.start) : new Date());
}

init();
