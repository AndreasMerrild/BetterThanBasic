"use strict";

// Ride page: one training's numbers and graphs. Opened as ride.html?id=<ride id>.
// The server provides the ride's summary, its neighbours and the ready-to-draw graph data.

const rideId = new URLSearchParams(location.search).get("id");

function renderStats(ride) {
    const rows = [
        ["Duration", formatDuration(ride.durationSeconds)],
        ["Distance", ride.distanceKm > 0 ? fixed(ride.distanceKm, 2) : null, "km"],
        ["Avg speed", ride.avgSpeedKmh > 0 ? fixed(ride.avgSpeedKmh, 1) : null, "km/h"],
        ["Avg power", fixed(ride.avgPower), "W"],
        ["Normalized power", fixed(ride.normalizedPower), "W"],
        ["Avg heart rate", fixed(ride.avgHeartRate), "bpm"],
        ["Max heart rate", ride.maxHeartRate, "bpm"],
        ["Avg cadence", fixed(ride.avgCadence), "rpm"],
        ["FTP (head unit)", ride.ftp, "W"],
        ["Intensity factor", fixed(ride.intensityFactor, 2)],
        ["TSS", fixed(ride.tss)],
        ["Laps", ride.laps],
    ];
    el("ride-stats").innerHTML = rows.map(([label, value, unit]) =>
        `<div><dt>${label}</dt><dd>${value == null ? "–"
            : escapeHtml(value) + (unit ? ` <small>${unit}</small>` : "")}</dd></div>`).join("");
}

/** Points a previous/next button at a neighbouring training, or disables it at either end. */
function linkNeighbour(elementId, neighbourId) {
    const link = el(elementId);
    if (neighbourId != null) {
        link.href = rideUrl(neighbourId);
        link.classList.remove("disabled");
    } else {
        link.removeAttribute("href");
        link.classList.add("disabled");
    }
}

/** Fetches the ride's summary and neighbours, and fills in everything but the graphs. */
async function loadRide() {
    const { ride, previousId, nextId } = await apiGet(`/api/activities/${encodeURIComponent(rideId)}`);
    const start = new Date(ride.start);
    document.title = `BetterThanBasic · Ride ${dayKey(start)}`;
    el("ride-title").textContent = `Ride · ${timeOfDay.format(start)} · ${longDate.format(start)}`;
    el("ride-sub").textContent = ride.device || "";
    el("back").href = calendarUrl(start);
    el("calendar-link").href = calendarUrl(start);
    linkNeighbour("prev-ride", previousId);
    linkNeighbour("next-ride", nextId);
    renderStats(ride);
}

let graphRequest = 0;

/** Asks the server for graph data with one point per pixel of the available width, then draws it. */
async function loadGraphs() {
    const container = el("ride-charts");
    const request = ++graphRequest;
    if (!container.querySelector("svg")) container.textContent = "Loading graphs…";
    try {
        const graph = await apiGet(`/api/activities/${encodeURIComponent(rideId)}/graph`, { points: chartPlotWidth(container) });
        if (request === graphRequest) renderRideCharts(container, graph);
    } catch (error) {
        if (request === graphRequest) container.textContent = `Could not load graphs: ${error.message}`;
    }
}

async function init() {
    try {
        await loadRide();
    } catch (error) {
        el("ride-title").textContent = error.message === "No such training" ? "Training not found"
            : `Could not load the training: ${error.message}`;
        return;
    }
    await loadGraphs();
}

// After uploading, fetch the neighbours again so Previous/Next include the new rides.
setupAthleteMenu(() => loadRide());

let resizeTimer;
window.addEventListener("resize", () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(loadGraphs, 200); // a new width needs a matching number of points
});

document.addEventListener("keydown", (event) => {
    const target = { ArrowLeft: "prev-ride", ArrowRight: "next-ride" }[event.key];
    if (target && el(target).href) location.href = el(target).href;
});

init();
