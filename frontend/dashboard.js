"use strict";

// Dashboard: best average power for each duration over a chosen period, compared with the best ever.
// The server works out every number (period filtering, totals, bests, percentages, records and the
// FTP estimate) via /api/dashboard; this file only asks for a period, formats and draws the answer.

const PERIOD_COLOR = "#e34948";   // the selected period
const ALL_TIME_COLOR = "#8c8c8c"; // best ever, for comparison

const state = {
    period: { kind: "all" }, // {kind:"days", days} | {kind:"all"} | {kind:"custom", from, to} (from/to: "YYYY-MM-DD")
    view: null,              // the latest /api/dashboard answer
    request: 0,              // so a slow answer for an older period is ignored
};

// ---------- Formatting ----------

/** 1 → "1 s", 90 → "1 min 30 s", 1200 → "20 min", 7200 → "2 h". */
function formatDurationLabel(seconds) {
    if (seconds < 60) return `${seconds} s`;
    if (seconds < 3600) {
        const minutes = Math.floor(seconds / 60);
        const rest = seconds % 60;
        return rest ? `${minutes} min ${rest} s` : `${minutes} min`;
    }
    const hours = seconds / 3600;
    return Number.isInteger(hours) ? `${hours} h` : `${Math.floor(hours)} h ${Math.round((seconds % 3600) / 60)} min`;
}

/** Short axis label: 5s, 1m, 20m, 1h. */
function shortDurationLabel(seconds) {
    if (seconds < 60) return `${seconds}s`;
    if (seconds < 3600) return `${seconds / 60}m`;
    return `${seconds / 3600}h`;
}

const dateOnly = new Intl.DateTimeFormat(undefined, { day: "numeric", month: "short", year: "numeric" });
const wholeNumber = new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 });
const watts = (value) => `${wholeNumber.format(value)} W`;

// ---------- Period (what to ask the server for) ----------

function periodLabel(period) {
    if (period.kind === "days") return `Last ${period.days} days`;
    if (period.kind === "custom") return `${dateOnly.format(parseKey(period.from))} – ${dateOnly.format(parseKey(period.to))}`;
    return "All time";
}

/** Reads the period from the address (#days=42, #from=2025-01-01&to=2025-03-31, or nothing = all time). */
function periodFromHash() {
    const params = new URLSearchParams(location.hash.slice(1));
    const days = Number(params.get("days"));
    if (days > 0) return { kind: "days", days };
    const from = params.get("from");
    const to = params.get("to");
    const isDate = (text) => /^\d{4}-\d{2}-\d{2}$/.test(text || "");
    if (isDate(from) && isDate(to)) return { kind: "custom", from, to };
    return { kind: "all" };
}

function periodToHash(period) {
    if (period.kind === "days") return `#days=${period.days}`;
    if (period.kind === "custom") return `#from=${period.from}&to=${period.to}`;
    return "#all";
}

function periodParams(period) {
    if (period.kind === "days") return { period: period.days };
    if (period.kind === "custom") return { from: period.from, to: period.to };
    return { period: "all" };
}

function setPeriod(period) {
    state.period = period;
    history.replaceState(null, "", periodToHash(period));
    load();
}

// ---------- Data ----------

async function load() {
    const request = ++state.request;
    el("status").textContent = "Loading…";
    try {
        const view = await apiGet("/api/dashboard", periodParams(state.period));
        if (request !== state.request) return; // a newer period was chosen meanwhile
        state.view = view;
        // The server may have tidied the period (e.g. swapped from/to), so show what it used.
        state.period = view.period.kind === "custom" ? { kind: "custom", from: view.period.from, to: view.period.to }
            : view.period.kind === "days" ? { kind: "days", days: view.period.days } : { kind: "all" };
        history.replaceState(null, "", periodToHash(state.period));
        el("status").textContent = `${view.rideCount} rides stored, ${view.ridesWithPower} with power`;
        render();
    } catch (error) {
        if (request === state.request) el("status").textContent = `Could not load the dashboard: ${error.message}`;
    }
}

// ---------- Rendering ----------

function render() {
    const view = state.view;
    if (!view) return;
    const period = state.period;
    const comparing = period.kind !== "all";

    el("period-label").textContent = periodLabel(period);
    for (const button of el("periods").querySelectorAll("button")) {
        const days = button.dataset.days;
        const active = (period.kind === "days" && Number(days) === period.days)
            || (period.kind === "all" && days === "all") || (period.kind === "custom" && days === "custom");
        button.classList.toggle("active", active);
        button.setAttribute("aria-pressed", String(active));
    }
    el("custom-range").hidden = period.kind !== "custom" && el("custom-range").dataset.open !== "true";
    if (period.kind === "custom") {
        el("custom-from").value = period.from;
        el("custom-to").value = period.to;
    }

    renderTiles(view);
    el("compare-note").textContent = comparing ? ", compared with your best ever (grey)" : "";
    renderLegend(comparing);
    renderCurve(view.bests, comparing);
    renderTable(view.bests, comparing);
}

function renderTiles(view) {
    const totals = view.totals;
    const ftp = view.estimatedFtp;
    const tiles = [
        ["Rides", wholeNumber.format(totals.rides), totals.ridesWithPower < totals.rides ? `${totals.ridesWithPower} with power` : ""],
        ["Riding time", formatDuration(totals.durationSeconds) || "0m:00s", ""],
        ["Distance", wholeNumber.format(totals.distanceKm), "km"],
        ["TSS", totals.tss == null ? "–" : wholeNumber.format(totals.tss), ""],
        ["Estimated FTP", ftp ? wholeNumber.format(ftp.watts) : "–",
            ftp ? `W · ${Math.round(ftp.factor * 100)}% of best ${formatDurationLabel(ftp.fromSeconds)}` : "needs a 20 min effort"],
    ];
    el("tiles").replaceChildren(...tiles.map(([label, value, note]) => {
        const tile = document.createElement("div");
        tile.className = "tile";
        const name = document.createElement("div");
        name.className = "tile-label";
        name.textContent = label;
        const number = document.createElement("div");
        number.className = "tile-value";
        number.textContent = value;
        const small = document.createElement("div");
        small.className = "tile-note";
        small.textContent = note;
        tile.append(name, number, small);
        return tile;
    }));
}

function renderLegend(comparing) {
    const legend = el("legend");
    legend.replaceChildren();
    if (!comparing) return; // one line needs no legend: the panel title names it
    for (const [label, color, dashed] of [[periodLabel(state.period), PERIOD_COLOR, false], ["Best ever", ALL_TIME_COLOR, true]]) {
        const item = document.createElement("span");
        item.className = "legend-item";
        const key = document.createElement("span");
        key.className = "legend-key" + (dashed ? " dashed" : "");
        key.style.borderColor = color;
        const text = document.createElement("span");
        text.textContent = label;
        item.append(key, text);
        legend.append(item);
    }
}

function rideLink(best) {
    const link = document.createElement("a");
    link.href = rideUrl(best.rideId);
    link.textContent = dateOnly.format(new Date(best.rideStart));
    return link;
}

function renderTable(rows, comparing) {
    const table = el("bests");
    table.replaceChildren();
    const head = table.createTHead().insertRow();
    const headings = ["Duration", "Best power", "Ride"];
    if (comparing) headings.push("Best ever", "Of best ever");
    for (const heading of headings) {
        const th = document.createElement("th");
        th.textContent = heading;
        head.append(th);
    }
    const body = table.createTBody();
    if (rows.length === 0) {
        const cell = body.insertRow().insertCell();
        cell.colSpan = headings.length;
        cell.className = "empty";
        cell.textContent = "No rides with power data yet.";
        return;
    }
    const anyInPeriod = rows.some((row) => row.best);
    for (const row of rows) {
        const tr = body.insertRow();
        tr.insertCell().textContent = formatDurationLabel(row.seconds);

        const best = tr.insertCell();
        best.className = "num";
        best.textContent = row.best ? watts(row.best.watts) : "–";

        const ride = tr.insertCell();
        if (row.best) {
            ride.append(rideLink(row.best));
        } else {
            ride.textContent = anyInPeriod ? "no ride long enough" : "no rides with power";
            ride.className = "muted";
        }

        if (comparing) {
            const ever = tr.insertCell();
            ever.className = "num";
            ever.textContent = watts(row.bestEver.watts);
            const share = tr.insertCell();
            share.className = "num";
            if (row.isBestEver) {
                share.textContent = "★ best ever";
                share.classList.add("record");
            } else {
                share.textContent = row.percentOfBestEver == null ? "–" : `${row.percentOfBestEver}%`;
            }
        }
    }
}

// ---------- Power curve (SVG drawing, log time axis) ----------

const CURVE = { height: 310, left: 52, right: 20, top: 24, bottom: 30 };
const AXIS_TICKS = [1, 5, 15, 30, 60, 120, 300, 600, 1200, 3600, 7200, 10800];

function renderCurve(rows, comparing) {
    const container = el("curve");
    container.replaceChildren();
    if (rows.length === 0) {
        container.textContent = "No rides with power data in this period.";
        return;
    }
    const width = Math.max(360, container.clientWidth);
    const plotWidth = width - CURVE.left - CURVE.right;
    const plotBottom = CURVE.height - CURVE.bottom;
    const durations = rows.map((row) => row.seconds);
    const first = durations[0];
    const last = Math.max(durations[durations.length - 1], first + 1);
    const x = (seconds) => CURVE.left + (Math.log(seconds / first) / Math.log(last / first)) * plotWidth;

    // Scale: the highest value drawn, rounded up to a gridline.
    const highest = Math.max(...rows.map((row) => (comparing ? row.bestEver.watts : row.best ? row.best.watts : 0)));
    const step = [50, 100, 200, 250, 500].find((s) => highest / s <= 6) || 500;
    const top = Math.max(step, Math.ceil(highest / step) * step);
    const y = (value) => plotBottom - (value / top) * (plotBottom - CURVE.top);

    let grid = "";
    for (let w = 0; w <= top; w += step) {
        grid += `<line class="grid" x1="${CURVE.left}" x2="${width - CURVE.right}" y1="${y(w)}" y2="${y(w)}"/>`
            + `<text class="tick" x="${CURVE.left - 8}" y="${y(w) + 4}" text-anchor="end">${w}</text>`;
    }
    for (const seconds of AXIS_TICKS.filter((s) => s >= first && s <= durations[durations.length - 1])) {
        grid += `<line class="grid" x1="${x(seconds)}" x2="${x(seconds)}" y1="${CURVE.top}" y2="${plotBottom}"/>`
            + `<text class="tick" x="${x(seconds)}" y="${plotBottom + 18}" text-anchor="middle">${shortDurationLabel(seconds)}</text>`;
    }
    const line = (pick) => rows.filter(pick).map((row, i) => `${i ? "L" : "M"}${x(row.seconds).toFixed(1)},${y(pick(row).watts).toFixed(1)}`).join("");
    const dots = rows.filter((row) => row.best).map((row) =>
        `<circle cx="${x(row.seconds)}" cy="${y(row.best.watts)}" r="3" fill="${PERIOD_COLOR}"/>`).join("");

    container.innerHTML = `
        <svg width="${width}" height="${CURVE.height}" viewBox="0 0 ${width} ${CURVE.height}" role="img"
             aria-label="Best power for each duration">
            ${grid}
            <text class="tick" x="${CURVE.left - 8}" y="${CURVE.top - 12}" text-anchor="end">W</text>
            <line class="baseline" x1="${CURVE.left}" x2="${width - CURVE.right}" y1="${plotBottom}" y2="${plotBottom}"/>
            ${comparing ? `<path d="${line((row) => row.bestEver)}" fill="none" stroke="${ALL_TIME_COLOR}" stroke-width="2" stroke-dasharray="5 4"/>` : ""}
            <path d="${line((row) => row.best)}" fill="none" stroke="${PERIOD_COLOR}" stroke-width="2" stroke-linejoin="round"/>
            ${dots}
            <line class="crosshair" y1="${CURVE.top}" y2="${plotBottom}" visibility="hidden"/>
            <circle class="hover-dot" r="5" fill="${PERIOD_COLOR}" visibility="hidden"/>
            <rect class="hit" x="${CURVE.left}" y="0" width="${plotWidth}" height="${plotBottom}" fill="transparent"/>
        </svg>
        <div class="curve-tooltip" hidden></div>`;

    // Hover: the crosshair snaps to the nearest duration and the tooltip lists the values.
    const svg = container.querySelector("svg");
    const crosshair = svg.querySelector(".crosshair");
    const dot = svg.querySelector(".hover-dot");
    const tooltip = container.querySelector(".curve-tooltip");
    const hit = svg.querySelector(".hit");
    hit.addEventListener("pointermove", (event) => {
        const px = event.clientX - svg.getBoundingClientRect().left;
        const row = rows.reduce((near, r) => (Math.abs(x(r.seconds) - px) < Math.abs(x(near.seconds) - px) ? r : near));
        const cx = x(row.seconds);
        crosshair.setAttribute("x1", cx);
        crosshair.setAttribute("x2", cx);
        crosshair.setAttribute("visibility", "visible");
        if (row.best) {
            dot.setAttribute("cx", cx);
            dot.setAttribute("cy", y(row.best.watts));
            dot.setAttribute("visibility", "visible");
        } else {
            dot.setAttribute("visibility", "hidden");
        }
        tooltip.replaceChildren();
        const title = document.createElement("div");
        title.className = "curve-tooltip-title";
        title.textContent = formatDurationLabel(row.seconds);
        tooltip.append(title);
        const lines = [[periodLabel(state.period), PERIOD_COLOR, row.best]];
        if (comparing) lines.push(["Best ever", ALL_TIME_COLOR, row.bestEver]);
        for (const [label, color, value] of lines) {
            const item = document.createElement("div");
            item.className = "curve-tooltip-row";
            const key = document.createElement("span");
            key.className = "key";
            key.style.background = color;
            const strong = document.createElement("strong");
            strong.textContent = value ? watts(value.watts) : "–";
            const text = document.createElement("span");
            text.textContent = value ? `${label} · ${dateOnly.format(new Date(value.rideStart))}` : label;
            item.append(key, strong, text);
            tooltip.append(item);
        }
        tooltip.hidden = false;
        const flip = cx > width - tooltip.offsetWidth - 30;
        tooltip.style.left = `${flip ? cx - tooltip.offsetWidth - 12 : cx + 12}px`;
        tooltip.style.top = `${Math.max(0, (row.best ? y(row.best.watts) : CURVE.top) - tooltip.offsetHeight / 2)}px`;
    });
    hit.addEventListener("pointerleave", () => {
        crosshair.setAttribute("visibility", "hidden");
        dot.setAttribute("visibility", "hidden");
        tooltip.hidden = true;
    });
}

// ---------- Setup ----------

function wireEvents() {
    el("periods").addEventListener("click", (event) => {
        const button = event.target.closest("button");
        if (!button) return;
        const days = button.dataset.days;
        if (days === "custom") {
            const form = el("custom-range");
            form.dataset.open = "true";
            form.hidden = false;
            if (!el("custom-from").value) el("custom-from").value = dayKey(addDays(new Date(), -30));
            if (!el("custom-to").value) el("custom-to").value = dayKey(new Date());
            el("custom-from").focus();
        } else {
            el("custom-range").dataset.open = "false";
            setPeriod(days === "all" ? { kind: "all" } : { kind: "days", days: Number(days) });
        }
    });
    el("custom-range").addEventListener("submit", (event) => {
        event.preventDefault();
        setPeriod({ kind: "custom", from: el("custom-from").value, to: el("custom-to").value });
    });
    // Back/forward or an edited address (#days=90 ...) changes the period without reloading the page.
    window.addEventListener("hashchange", () => {
        state.period = periodFromHash();
        load();
    });
    let resizeTimer;
    window.addEventListener("resize", () => {
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(render, 150);
    });
    setupAthleteMenu(load);
}

state.period = periodFromHash();
wireEvents();
load();
