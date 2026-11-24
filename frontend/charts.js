"use strict";

// Ride graphs: one small chart per measure, stacked on a shared time axis, with one
// crosshair + tooltip across all of them. Plain SVG, no dependencies.

const RIDE_SERIES = [
    { key: "power", label: "Power", unit: "W", color: "#e34948", smoothSeconds: 3 },
    { key: "hr", label: "Heart rate", unit: "bpm", color: "#d55181" },
    { key: "speed", label: "Speed", unit: "km/h", color: "#2a78d6", digits: 1 },
    { key: "cadence", label: "Cadence", unit: "rpm", color: "#199e70" },
];

const CHART = { height: 110, left: 44, right: 12, top: 6, bottom: 6, axis: 22 };
const GAP_SECONDS = 10; // a longer pause between samples breaks the line

function formatClock(seconds) {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.floor(seconds % 60);
    return h > 0 ? `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`
        : `${m}:${String(s).padStart(2, "0")}`;
}

/** Trailing moving average over {@code seconds}, matching the console app's 3 s power graph. */
function smooth(t, values, seconds) {
    const out = new Array(values.length).fill(null);
    let start = 0, sum = 0, count = 0;
    for (let i = 0; i < values.length; i++) {
        if (values[i] != null) { sum += values[i]; count++; }
        while (t[i] - t[start] >= seconds) {
            if (values[start] != null) { sum -= values[start]; count--; }
            start++;
        }
        out[i] = values[i] == null ? null : sum / count;
    }
    return out;
}

function niceStep(range, targetTicks) {
    const rough = range / targetTicks;
    const power = 10 ** Math.floor(Math.log10(rough));
    return [1, 2, 2.5, 5, 10].map((f) => f * power).find((step) => step >= rough);
}

function timeStep(totalSeconds) {
    return [60, 300, 600, 900, 1800, 3600, 7200].find((s) => totalSeconds / s <= 8) || 7200;
}

/** One SVG path for a series, averaging samples that land on the same pixel column. */
function buildPath(t, values, x, y) {
    let d = "", column = null, sum = 0, count = 0, lastT = null, penDown = false;
    const flush = () => {
        if (count === 0) return;
        d += `${penDown ? "L" : "M"}${column},${y(sum / count).toFixed(1)}`;
        penDown = true;
    };
    for (let i = 0; i < t.length; i++) {
        if (values[i] == null) continue;
        if (lastT != null && t[i] - lastT > GAP_SECONDS) { flush(); penDown = false; column = null; count = 0; }
        const col = Math.round(x(t[i]));
        if (col !== column) { flush(); column = col; sum = 0; count = 0; }
        sum += values[i];
        count++;
        lastT = t[i];
    }
    flush();
    return d;
}

/**
 * Draws the ride's graphs into {@code container}. Returns nothing; hover state lives in closures.
 * {@code data} is the /api/activities/{id}/records payload.
 */
function renderRideCharts(container, data) {
    container.replaceChildren();
    const t = data.t;
    if (!t.length) {
        container.textContent = "This training has no recorded samples.";
        return;
    }
    const style = getComputedStyle(container);
    const inner = container.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight);
    const width = Math.max(320, Math.floor(inner));
    const plotWidth = width - CHART.left - CHART.right;
    const tMax = Math.max(t[t.length - 1], 1);
    const x = (seconds) => CHART.left + (seconds / tMax) * plotWidth;
    const xStep = timeStep(tMax);

    const series = RIDE_SERIES
        .map((s) => {
            const raw = data[s.key];
            const shown = s.smoothSeconds ? smooth(t, raw, s.smoothSeconds) : raw;
            const present = raw.filter((v) => v != null && v > 0);
            return { ...s, raw, shown, present };
        })
        .filter((s) => s.present.length > 0);

    if (series.length === 0) {
        container.textContent = "This training has no power, heart rate, speed or cadence samples.";
        return;
    }

    const charts = series.map((s, index) => {
        const isLast = index === series.length - 1;
        const height = CHART.height + (isLast ? CHART.axis : 0);
        const plotBottom = CHART.height - CHART.bottom;
        const values = s.shown.filter((v) => v != null);
        const max = Math.max(...values);
        // Average over every recorded sample (zeros included), matching the training summary.
        const recorded = s.raw.filter((v) => v != null);
        const average = recorded.reduce((a, b) => a + b, 0) / recorded.length;
        // Heart rate reads better from a raised floor; the others start at zero.
        const floor = s.key === "hr" ? Math.max(0, Math.floor((Math.min(...s.present) - 10) / 20) * 20) : 0;
        const step = niceStep(Math.max(max - floor, 1), 3);
        const top = floor + Math.ceil((max - floor) / step) * step;
        const y = (v) => plotBottom - ((v - floor) / (top - floor)) * (plotBottom - CHART.top);

        let grid = "";
        for (let v = floor; v <= top + 1e-9; v += step) {
            grid += `<line class="grid" x1="${CHART.left}" x2="${width - CHART.right}" y1="${y(v)}" y2="${y(v)}"/>`
                + `<text class="tick" x="${CHART.left - 6}" y="${y(v) + 4}" text-anchor="end">${Math.round(v)}</text>`;
        }
        let laps = "";
        for (const lapStart of data.lapStarts || []) {
            if (lapStart > 0 && lapStart < tMax) {
                laps += `<line class="lap" x1="${x(lapStart)}" x2="${x(lapStart)}" y1="${CHART.top}" y2="${plotBottom}"/>`;
            }
        }
        let axis = "";
        if (isLast) {
            for (let s2 = 0; s2 <= tMax; s2 += xStep) {
                axis += `<text class="tick" x="${x(s2)}" y="${plotBottom + 17}" text-anchor="middle">${formatClock(s2).replace(/:00$/, "")}</text>`;
            }
        }

        const wrapper = document.createElement("div");
        wrapper.className = "ride-chart";
        const title = document.createElement("div");
        title.className = "ride-chart-title";
        const name = document.createElement("strong");
        name.textContent = s.label;
        const stats = document.createElement("span");
        const digits = s.digits ?? 0;
        stats.textContent = `avg ${average.toFixed(digits)} · max ${Math.max(...s.present).toFixed(digits)} ${s.unit}`;
        title.append(name, stats);
        wrapper.append(title);
        wrapper.insertAdjacentHTML("beforeend", `
            <svg width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" role="img"
                 aria-label="${s.label} over the training">
                ${grid}${laps}${axis}
                <line class="baseline" x1="${CHART.left}" x2="${width - CHART.right}" y1="${plotBottom}" y2="${plotBottom}"/>
                <path d="${buildPath(t, s.shown, x, y)}" fill="none" stroke="${s.color}" stroke-width="2"
                      stroke-linejoin="round" stroke-linecap="round"/>
                <line class="crosshair" y1="${CHART.top}" y2="${plotBottom}" visibility="hidden"/>
                <circle class="hover-dot" r="4" fill="${s.color}" visibility="hidden"/>
                <rect class="hit" x="${CHART.left}" y="0" width="${plotWidth}" height="${CHART.height}" fill="transparent"/>
            </svg>`);
        container.append(wrapper);
        return { s, y, svg: wrapper.querySelector("svg") };
    });

    // ---------- Shared crosshair + tooltip ----------
    const tooltip = document.createElement("div");
    tooltip.className = "ride-tooltip";
    tooltip.hidden = true;
    container.append(tooltip);

    const nearestIndex = (seconds) => {
        let lo = 0, hi = t.length - 1;
        while (lo < hi) {
            const mid = (lo + hi) >> 1;
            if (t[mid] < seconds) lo = mid + 1; else hi = mid;
        }
        return lo > 0 && seconds - t[lo - 1] < t[lo] - seconds ? lo - 1 : lo;
    };

    const show = (event) => {
        const rect = charts[0].svg.getBoundingClientRect();
        const seconds = Math.min(tMax, Math.max(0, ((event.clientX - rect.left - CHART.left) / plotWidth) * tMax));
        const i = nearestIndex(seconds);
        const px = x(t[i]);

        tooltip.replaceChildren();
        const time = document.createElement("div");
        time.className = "ride-tooltip-time";
        time.textContent = formatClock(t[i]);
        tooltip.append(time);

        for (const { s, y, svg } of charts) {
            const value = s.shown[i];
            const line = svg.querySelector(".crosshair");
            const dot = svg.querySelector(".hover-dot");
            line.setAttribute("x1", px);
            line.setAttribute("x2", px);
            line.setAttribute("visibility", "visible");
            if (value != null) {
                dot.setAttribute("cx", px);
                dot.setAttribute("cy", y(value));
                dot.setAttribute("visibility", "visible");
            } else {
                dot.setAttribute("visibility", "hidden");
            }
            const row = document.createElement("div");
            row.className = "ride-tooltip-row";
            const key = document.createElement("span");
            key.className = "key";
            key.style.background = s.color;
            const strong = document.createElement("strong");
            strong.textContent = value == null ? "–" : `${value.toFixed(s.digits ?? 0)} ${s.unit}`;
            const label = document.createElement("span");
            label.textContent = s.label;
            row.append(key, strong, label);
            tooltip.append(row);
        }

        tooltip.hidden = false;
        const box = container.getBoundingClientRect();
        const left = event.clientX - box.left;
        const flip = left > box.width - tooltip.offsetWidth - 24;
        tooltip.style.left = `${flip ? left - tooltip.offsetWidth - 14 : left + 14}px`;
        tooltip.style.top = `${Math.max(0, event.clientY - box.top - tooltip.offsetHeight / 2)}px`;
    };

    const hide = () => {
        tooltip.hidden = true;
        for (const { svg } of charts) {
            svg.querySelector(".crosshair").setAttribute("visibility", "hidden");
            svg.querySelector(".hover-dot").setAttribute("visibility", "hidden");
        }
    };

    for (const { svg } of charts) {
        const hit = svg.querySelector(".hit");
        hit.addEventListener("pointermove", show);
        hit.addEventListener("pointerleave", hide);
    }
}
