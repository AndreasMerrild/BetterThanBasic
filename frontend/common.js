"use strict";

// Helpers shared by the calendar (app.js) and the ride page (ride.js).

const el = (id) => document.getElementById(id);

// ---------- Dates (all local time) ----------

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

/** "YYYY-MM-DD" for a local date. */
function dayKey(date) {
    const pad = (n) => String(n).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function parseKey(key) {
    const [y, m, d] = key.split("-").map(Number);
    return new Date(y, m - 1, d);
}

const shortDate = new Intl.DateTimeFormat(undefined, { day: "numeric", month: "short" });
const longDate = new Intl.DateTimeFormat(undefined, { weekday: "long", day: "numeric", month: "long", year: "numeric" });
const timeOfDay = new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" });
const monthYear = new Intl.DateTimeFormat(undefined, { month: "long", year: "numeric" });

// ---------- Numbers ----------

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

/** All training summaries from the server, oldest first. */
async function fetchActivities(reload = false) {
    const response = await fetch(reload ? "/api/reload" : "/api/activities", { method: reload ? "POST" : "GET" });
    if (!response.ok) throw new Error(`Server answered ${response.status}`);
    const list = await response.json();
    return list.filter((a) => a.start != null).sort((a, b) => a.start - b.start);
}

/** Link to a training's own page. */
function rideUrl(activity) {
    return `ride.html?id=${activity.id}`;
}

/** Link back to the calendar, opened on the week of {@code date}. */
function calendarUrl(date) {
    return `index.html#${dayKey(date)}`;
}
