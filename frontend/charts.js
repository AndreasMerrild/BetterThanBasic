"use strict";

// Ride graphs: one small chart per measure, stacked on a shared time axis, with one crosshair and
// tooltip across all of them. Plain SVG, no dependencies. The server sends the data ready to draw
// (/api/activities/{id}/graph): power already smoothed, every series reduced to one point per
// pixel column, plus each series' average and maximum. This file only draws.

/** How each measure looks; the numbers themselves come from the server. */
const SERIES_STYLE = {
    power: { label: "Power", color: "#e34948" },
    hr: { label: "Heart rate", color: "#d55181" },
    speed: { label: "Speed", color: "#2a78d6" },
    cadence: { label: "Cadence", color: "#199e70" },
};

const CHART = { height: 110, left: 44, right: 12, top: 6, bottom: 6, axis: 22 };

/** The width available for the plot, so the server can send one point per pixel. */
function chartPlotWidth(container) {
    const style = getComputedStyle(container);
    const inner = container.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight);
    return Math.max(320, Math.floor(inner)) - CHART.left - CHART.right;
}

function formatClock(seconds) {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.floor(seconds % 60);
    return h > 0 ? `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`
        : `${m}:${String(s).padStart(2, "0")}`;
}

/** A round gridline spacing giving about {@code ticks} lines over {@code range}. */
function niceStep(range, ticks) {
    const rough = range / ticks;
    const power = 10 ** Math.floor(Math.log10(rough));
    return [1, 2, 2.5, 5, 10].map((f) => f * power).find((step) => step >= rough);
}

/** Grid label spacing on the time axis (1 min ... 2 h) giving at most about 8 labels. */
function timeStep(totalSeconds) {
    return [60, 300, 600, 900, 1800, 3600, 7200].find((s) => totalSeconds / s <= 8) || 7200;
}

/** SVG path through the points; a null value (nothing recorded there) breaks the line. */
function linePath(t, values, x, y) {
    let d = "";
    let penDown = false;
    for (let i = 0; i < values.length; i++) {
        if (values[i] == null) {
            penDown = false;
            continue;
        }
        d += `${penDown ? "L" : "M"}${x(t[i]).toFixed(1)},${y(values[i]).toFixed(1)}`;
        penDown = true;
    }
    return d;
}

/** Draws the ride's graphs into {@code container} from the server's graph data. */
function renderRideCharts(container, graph) {
    container.replaceChildren();
    const t = graph.t;
    if (!t.length) {
        container.textContent = "This training has no recorded samples.";
        return;
    }
    if (graph.series.length === 0) {
        container.textContent = "This training has no power, heart rate, speed or cadence samples.";
        return;
    }
    const plotWidth = chartPlotWidth(container);
    const width = plotWidth + CHART.left + CHART.right;
    const total = graph.durationSeconds;
    const x = (seconds) => CHART.left + (seconds / total) * plotWidth;
    const xStep = timeStep(total);

    const charts = graph.series.map((series, index) => {
        const style = SERIES_STYLE[series.key] || { label: series.key, color: "#666" };
        const isLast = index === graph.series.length - 1;
        const height = CHART.height + (isLast ? CHART.axis : 0);
        const plotBottom = CHART.height - CHART.bottom;
        // Scale: heart rate reads better from a raised floor; the others start at zero.
        const floor = series.key === "hr" ? Math.max(0, Math.floor((series.min - 10) / 20) * 20) : 0;
        const step = niceStep(Math.max(series.shownMax - floor, 1), 3);
        const top = floor + Math.ceil((series.shownMax - floor) / step) * step;
        const y = (v) => plotBottom - ((v - floor) / (top - floor)) * (plotBottom - CHART.top);

        let grid = "";
        for (let v = floor; v <= top + 1e-9; v += step) {
            grid += `<line class="grid" x1="${CHART.left}" x2="${width - CHART.right}" y1="${y(v)}" y2="${y(v)}"/>`
                + `<text class="tick" x="${CHART.left - 6}" y="${y(v) + 4}" text-anchor="end">${v}</text>`;
        }
        const laps = graph.lapStarts.map((at) =>
            `<line class="lap" x1="${x(at)}" x2="${x(at)}" y1="${CHART.top}" y2="${plotBottom}"/>`).join("");
        let axis = "";
        if (isLast) {
            for (let s = 0; s <= total; s += xStep) {
                axis += `<text class="tick" x="${x(s)}" y="${plotBottom + 17}" text-anchor="middle">${formatClock(s).replace(/:00$/, "")}</text>`;
            }
        }

        const wrapper = document.createElement("div");
        wrapper.className = "ride-chart";
        const title = document.createElement("div");
        title.className = "ride-chart-title";
        const name = document.createElement("strong");
        name.textContent = style.label;
        const stats = document.createElement("span");
        stats.textContent = `avg ${fixed(series.average, series.digits)} · max ${fixed(series.max, series.digits)} ${series.unit}`;
        title.append(name, stats);
        wrapper.append(title);
        wrapper.insertAdjacentHTML("beforeend", `
            <svg width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" role="img"
                 aria-label="${style.label} over the training">
                ${grid}${laps}${axis}
                <line class="baseline" x1="${CHART.left}" x2="${width - CHART.right}" y1="${plotBottom}" y2="${plotBottom}"/>
                <path d="${linePath(t, series.values, x, y)}" fill="none" stroke="${style.color}" stroke-width="2"
                      stroke-linejoin="round" stroke-linecap="round"/>
                <line class="crosshair" y1="${CHART.top}" y2="${plotBottom}" visibility="hidden"/>
                <circle class="hover-dot" r="4" fill="${style.color}" visibility="hidden"/>
                <rect class="hit" x="${CHART.left}" y="0" width="${plotWidth}" height="${CHART.height}" fill="transparent"/>
            </svg>`);
        container.append(wrapper);
        return { series, style, y, svg: wrapper.querySelector("svg") };
    });

    // ---------- Shared crosshair + tooltip ----------
    const tooltip = document.createElement("div");
    tooltip.className = "ride-tooltip";
    tooltip.hidden = true;
    container.append(tooltip);

    const show = (event) => {
        const box = charts[0].svg.getBoundingClientRect();
        // Each point covers an equal slice of the ride, so the pointer's position picks the point.
        const share = (event.clientX - box.left - CHART.left) / plotWidth;
        const i = Math.min(t.length - 1, Math.max(0, Math.floor(share * t.length)));
        const px = x(t[i]);

        tooltip.replaceChildren();
        const time = document.createElement("div");
        time.className = "ride-tooltip-time";
        time.textContent = formatClock(t[i]);
        tooltip.append(time);

        for (const { series, style, y, svg } of charts) {
            const value = series.values[i];
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
            key.style.background = style.color;
            const strong = document.createElement("strong");
            strong.textContent = value == null ? "–" : `${fixed(value, series.digits)} ${series.unit}`;
            const label = document.createElement("span");
            label.textContent = style.label;
            row.append(key, strong, label);
            tooltip.append(row);
        }

        tooltip.hidden = false;
        const area = container.getBoundingClientRect();
        const left = event.clientX - area.left;
        const flip = left > area.width - tooltip.offsetWidth - 24;
        tooltip.style.left = `${flip ? left - tooltip.offsetWidth - 14 : left + 14}px`;
        tooltip.style.top = `${Math.max(0, event.clientY - area.top - tooltip.offsetHeight / 2)}px`;
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
