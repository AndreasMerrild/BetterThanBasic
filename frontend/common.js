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

// ---------- Athlete + data ----------

const ATHLETE_STORAGE_KEY = "betterthanbasic.athlete";

/**
 * The athlete whose data the page shows: ?athlete= in the address wins (so ride links keep pointing
 * at the right person), otherwise the last one chosen in the menu, otherwise the default athlete.
 */
function currentAthleteId() {
    const fromUrl = new URLSearchParams(location.search).get("athlete");
    if (fromUrl) {
        localStorage.setItem(ATHLETE_STORAGE_KEY, fromUrl);
        return fromUrl;
    }
    return localStorage.getItem(ATHLETE_STORAGE_KEY) || "default";
}

/** An API address for the current athlete, e.g. apiUrl("/api/upload", { name: "ride.fit" }). */
function apiUrl(path, params = {}) {
    return `${path}?${new URLSearchParams({ athlete: currentAthleteId(), ...params })}`;
}

/** Header for requests that change data (the server refuses changes without it). */
const WRITE_HEADERS = { "X-BetterThanBasic-Upload": "1" };

/** All training summaries of the current athlete, oldest first. */
async function fetchActivities(reload = false) {
    const response = await fetch(apiUrl(reload ? "/api/reload" : "/api/activities"), { method: reload ? "POST" : "GET" });
    if (!response.ok) throw new Error(`Server answered ${response.status}`);
    const list = await response.json();
    return list.filter((a) => a.start != null).sort((a, b) => a.start - b.start);
}

/** Link to a training's own page. */
function rideUrl(activity) {
    return `ride.html?${new URLSearchParams({ athlete: currentAthleteId(), id: activity.id })}`;
}

/** Link back to the calendar, opened on the week of {@code date}. */
function calendarUrl(date) {
    return `index.html?${new URLSearchParams({ athlete: currentAthleteId() })}#${dayKey(date)}`;
}

// ---------- Messages ----------

/** A message box in the bottom-right corner; click it to close. */
function showToast(text, { sticky = false } = {}) {
    let toast = el("toast");
    if (!toast) {
        toast = document.createElement("div");
        toast.id = "toast";
        toast.className = "toast";
        toast.setAttribute("role", "status");
        toast.title = "Click to close";
        toast.addEventListener("click", () => { toast.hidden = true; });
        document.body.append(toast);
    }
    clearTimeout(toast.timer);
    toast.textContent = text;
    toast.hidden = false;
    if (!sticky) toast.timer = setTimeout(() => { toast.hidden = true; }, 8000);
    return toast;
}

// ---------- FIT upload ----------

const UPLOAD_RETRIES = 3; // extra attempts when the connection fails or the server has a hiccup

/** FIT files and GoldenCheetah JSON ride files. */
const isTrainingFile = (file) => /\.(fit|json)$/i.test(file.name);
const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** Sends one training file (.fit or .json); resolves to {name, status: "added"|"exists"|"failed", error?}. Retries on network errors. */
async function uploadTrainingFile(file) {
    let lastError = "";
    for (let attempt = 0; attempt <= UPLOAD_RETRIES; attempt++) {
        if (attempt > 0) await pause(1000 * attempt);
        try {
            const response = await fetch(apiUrl("/api/upload", { name: file.name }), {
                method: "POST",
                headers: { ...WRITE_HEADERS, "Content-Type": "application/octet-stream" },
                body: file,
            });
            const result = await response.json().catch(() => ({}));
            if (response.ok && result.status) return result;
            lastError = result.error || `Server answered ${response.status}`;
            if (response.status < 500) break; // the file itself was refused: retrying will not help
        } catch (error) {
            lastError = `Could not reach the server (${error.message}). Is it still running?`;
        }
    }
    return { name: file.name, status: "failed", error: lastError };
}

let uploadInProgress = false;

// Leaving the page stops the upload loop, so the browser asks first while files are still being sent.
window.addEventListener("beforeunload", (event) => {
    if (uploadInProgress) {
        event.preventDefault();
        event.returnValue = "";
    }
});

/**
 * Uploads many files one after another (the server handles one at a time anyway), showing progress,
 * then reloads the training list once and shows a full report that stays until clicked.
 */
async function uploadTrainingFiles(chosen, onUploaded) {
    if (uploadInProgress) {
        showToast("An upload is already running. Wait for it to finish first.");
        return;
    }
    const files = chosen.filter(isTrainingFile);
    const ignored = chosen.length - files.length;
    if (files.length === 0) {
        showToast(`No .fit or .json files found among the ${chosen.length} selected file(s).`);
        return;
    }
    uploadInProgress = true;
    const counts = { added: 0, exists: 0, failed: 0 };
    const failures = [];
    try {
        for (let i = 0; i < files.length; i++) {
            showToast(`Uploading ${i + 1} of ${files.length} (keep this page open)\n`
                + `${counts.added} added · ${counts.exists} already stored · ${counts.failed} failed\n${files[i].name}`,
            { sticky: true });
            const result = await uploadTrainingFile(files[i]);
            counts[result.status] = (counts[result.status] || 0) + 1;
            if (result.status === "failed") failures.push(result);
        }
        if (counts.added > 0) {
            showToast("Updating trainings…", { sticky: true });
            await onUploaded();
        }
    } finally {
        uploadInProgress = false;
    }

    const lines = [
        `Upload finished: ${files.length} training file${files.length === 1 ? "" : "s"}`,
        `${counts.added} added · ${counts.exists} already stored (skipped) · ${counts.failed} failed`,
    ];
    if (ignored) lines.push(`${ignored} other file${ignored === 1 ? "" : "s"} ignored (not .fit or .json)`);
    for (const f of failures.slice(0, 10)) lines.push(`✗ ${f.name}: ${f.error}`);
    if (failures.length > 10) lines.push(`…and ${failures.length - 10} more failed`);
    lines.push("(click to close)");
    showToast(lines.join("\n"), { sticky: true });
}

// ---------- Athlete menu ----------

async function apiPost(path, params) {
    const response = await fetch(`${path}?${new URLSearchParams(params)}`, { method: "POST", headers: WRITE_HEADERS });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.error || `Server answered ${response.status}`);
    return result;
}

/** Opens another athlete's calendar. */
function switchAthlete(id) {
    localStorage.setItem(ATHLETE_STORAGE_KEY, id);
    location.href = `index.html?${new URLSearchParams({ athlete: id })}`;
}

const ICONS = {
    check: '<svg viewBox="0 0 24 24"><path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z"/></svg>',
    add: '<svg viewBox="0 0 24 24"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>',
    edit: '<svg viewBox="0 0 24 24"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>',
    upload: '<svg viewBox="0 0 24 24"><path d="M5 20h14v-2H5v2zm7-16-5.5 5.5 1.42 1.42L11 7.84V16h2V7.84l3.08 3.08 1.42-1.42L12 4z"/></svg>',
    folder: '<svg viewBox="0 0 24 24"><path d="M10 4H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-8l-2-2z"/></svg>',
};

function menuItem(label, icon, onClick, checked) {
    const item = document.createElement("button");
    item.type = "button";
    item.setAttribute("role", checked === undefined ? "menuitem" : "menuitemradio");
    if (checked !== undefined) item.setAttribute("aria-checked", String(checked));
    const mark = document.createElement("span");
    mark.className = "menu-icon";
    mark.innerHTML = icon || ""; // icons are fixed markup from ICONS above, never data
    const text = document.createElement("span");
    text.textContent = label; // athlete names are typed by the user, so they go in as text only
    item.append(mark, text);
    item.addEventListener("click", onClick);
    return item;
}

/**
 * Wires the Athlete dropdown in the top bar: switch / add / rename athletes, and upload training files
 * or a whole folder. {@code onUploaded} runs after new trainings were added and should reload the
 * page's data (it must call /api/reload, e.g. via fetchActivities(true)).
 */
async function setupAthleteMenu(onUploaded) {
    const button = el("athlete-button");
    const dropdown = el("athlete-dropdown");
    const fileInput = el("upload-input");
    const folderInput = el("upload-folder-input");

    const setOpen = (open) => {
        dropdown.hidden = !open;
        button.setAttribute("aria-expanded", String(open));
    };
    button.addEventListener("click", (event) => {
        event.stopPropagation();
        setOpen(dropdown.hidden);
    });
    document.addEventListener("click", (event) => {
        if (!dropdown.contains(event.target)) setOpen(false);
    });
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") setOpen(false);
    });
    for (const input of [fileInput, folderInput]) {
        input.addEventListener("change", async () => {
            const files = [...input.files];
            input.value = ""; // so choosing the same files again still triggers a change
            if (files.length) await uploadTrainingFiles(files, onUploaded);
        });
    }

    let athletes = [];
    try {
        const response = await fetch("/api/athletes");
        if (!response.ok) throw new Error(`Server answered ${response.status}`);
        athletes = await response.json();
    } catch (error) {
        showToast(`Could not load athletes: ${error.message}`);
    }
    const current = athletes.find((a) => a.id === currentAthleteId());
    if (!current && athletes.length) { // e.g. a remembered athlete that no longer exists
        switchAthlete(athletes[0].id);
        return;
    }
    el("athlete-name").textContent = current ? current.name : "Athlete";

    const label = document.createElement("div");
    label.className = "menu-label";
    label.textContent = "Switch athlete";
    dropdown.append(label);
    for (const athlete of athletes) {
        const isCurrent = athlete.id === current.id;
        dropdown.append(menuItem(athlete.name, isCurrent ? ICONS.check : "", () => {
            setOpen(false);
            if (!isCurrent) switchAthlete(athlete.id);
        }, isCurrent));
    }
    dropdown.append(menuItem("Add athlete…", ICONS.add, async () => {
        setOpen(false);
        const name = prompt("Name of the new athlete:");
        if (!name || !name.trim()) return;
        try {
            switchAthlete((await apiPost("/api/athletes", { name })).id);
        } catch (error) {
            showToast(`Could not add athlete: ${error.message}`);
        }
    }));
    if (current) {
        dropdown.append(menuItem(`Rename ${current.name}…`, ICONS.edit, async () => {
            setOpen(false);
            const name = prompt("New name:", current.name);
            if (!name || !name.trim() || name === current.name) return;
            try {
                await apiPost("/api/athletes/rename", { athlete: current.id, name });
                location.reload();
            } catch (error) {
                showToast(`Could not rename athlete: ${error.message}`);
            }
        }));
    }
    dropdown.append(document.createElement("hr"));
    dropdown.append(menuItem("Upload training files (.fit, .json)…", ICONS.upload, () => { setOpen(false); fileInput.click(); }));
    dropdown.append(menuItem("Upload a folder…", ICONS.folder, () => { setOpen(false); folderInput.click(); }));
}
