"use strict";

// Ride page: one training's numbers and graphs. Opened as ride.html?id=<activity id>.

let chartData = null;

function renderStats(activity) {
    const rows = [
        ["Duration", formatDuration(activity.durationSeconds)],
        ["Distance", activity.distanceKm > 0 ? fixed(activity.distanceKm, 2) : null, "km"],
        ["Avg speed", activity.avgSpeedKmh > 0 ? fixed(activity.avgSpeedKmh, 1) : null, "km/h"],
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
    el("ride-stats").innerHTML = rows.map(([label, value, unit]) =>
        `<div><dt>${label}</dt><dd>${value == null ? "–"
            : escapeHtml(value) + (unit ? ` <small>${unit}</small>` : "")}</dd></div>`).join("");
}

/** Points a previous/next button at a neighbouring training, or disables it at either end. */
function linkNeighbour(id, activity) {
    const link = el(id);
    if (activity) {
        link.href = rideUrl(activity);
        link.classList.remove("disabled");
    } else {
        link.removeAttribute("href");
        link.classList.add("disabled");
    }
}

async function loadCharts(activity) {
    const container = el("ride-charts");
    container.textContent = "Loading graphs…";
    try {
        const response = await fetch(apiUrl(`/api/activities/${activity.id}/records`));
        if (!response.ok) throw new Error(`Server answered ${response.status}`);
        const data = await response.json();
        if (!data || !Array.isArray(data.t)) {
            // A server built before the graphs existed answers with the training list instead.
            throw new Error("the server sent no samples. Recompile and restart the server (Server.WebServer)");
        }
        chartData = data;
        renderRideCharts(container, chartData);
    } catch (error) {
        container.textContent = `Could not load graphs: ${error.message}`;
    }
}

async function init() {
    const id = Number(new URLSearchParams(location.search).get("id"));
    let activities;
    try {
        activities = await fetchActivities();
    } catch (error) {
        el("ride-title").textContent = `Could not load trainings: ${error.message}`;
        return;
    }
    const index = activities.findIndex((a) => a.id === id);
    if (index < 0) {
        el("ride-title").textContent = "Training not found";
        return;
    }
    const activity = activities[index];
    const start = new Date(activity.start);

    document.title = `BetterThanBasic · Ride ${dayKey(start)}`;
    el("ride-title").textContent = `Ride · ${timeOfDay.format(start)} · ${longDate.format(start)}`;
    el("ride-sub").textContent = activity.device || "";
    el("back").href = calendarUrl(start);
    el("calendar-link").href = calendarUrl(start);
    linkNeighbours(activities, id);

    renderStats(activity);
    await loadCharts(activity);
}

/** Points Previous/Next at the trainings either side of {@code id} in {@code activities}. */
function linkNeighbours(activities, id) {
    const index = activities.findIndex((a) => a.id === id);
    linkNeighbour("prev-ride", activities[index - 1]);
    linkNeighbour("next-ride", activities[index + 1]);
}

// After uploading, reload the server's list so Previous/Next include the new rides.
setupAthleteMenu(async () => {
    const id = Number(new URLSearchParams(location.search).get("id"));
    linkNeighbours(await fetchActivities(true), id);
});

window.addEventListener("resize", () => {
    if (chartData) renderRideCharts(el("ride-charts"), chartData);
});

document.addEventListener("keydown", (event) => {
    const target = { ArrowLeft: "prev-ride", ArrowRight: "next-ride" }[event.key];
    if (target && el(target).href) location.href = el(target).href;
});

init();
