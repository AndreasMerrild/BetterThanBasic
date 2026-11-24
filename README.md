# BetterThanBasic

This is a project which will at some point make a power analytics software. 
It right now has the functions:
- Get training zones based on best 20 minutes power numbers (FTP - Coggan 7 zone)
- Look at a single training and getting averages of that file, look at the power graph, get the best power numbers for durations
- Look at many trainings and getting a summary of the data, look at the best power numbers for durations.


Run the Main application and input the number for the part you want. 

## FIT files

The project uses Garmin's official FIT Java SDK through Maven. Import the project
from `pom.xml` in IntelliJ, then reload the Maven project so the dependency is
downloaded.

Choose `3 : Parse a .fit activity file` in the application and enter the path to
the FIT file. `FitParser` decodes the activity records and exposes timestamps,
power, heart rate, speed, and cadence through `FitActivity`. The application
also prints a summary containing duration and the average power, speed, heart
rate, and cadence. Speed is converted from the FIT file's m/s value and reported in km/h.
When the file contains the session's configured threshold power, the application also
prints the head unit FTP and intensity factor (normalized power divided by that FTP).
The application also opens a power-over-time line graph for records containing
both a timestamp and power value. The graph uses a 3-second moving average,
shows time in minutes, uses 100 W intervals on the power axis with headroom
above the maximum power, and draws only the smoothed line.
The single-activity menu also offers a heart-rate-over-time graph. It uses the
timestamped heart-rate samples directly, without smoothing.
The menu also offers a cadence-over-time graph using timestamped cadence
samples directly, without smoothing, with the axis shown in revolutions per
minute.
The single-activity menu also offers a combined graph with power, heart rate,
and cadence overlaid using separate Y-axis scales and a color legend.
When the FIT file contains lap messages, a lap summary table is printed with
each lap's duration, distance, and average/max power, heart rate, and cadence.

Choose `4 : Parse multiple .fit activity files` to enter a directory containing
FIT files. The application keeps all parsed activities in memory and prints the
duration and distance for each file, followed by the combined total time and
distance. Distance is calculated from the recorded FIT speeds and timestamps.

Best-power graphs, tables, and the best-20-minute FTP calculation can be
limited to activities from the last N days. Enter `7`, `90`, or another
non-negative number when prompted; enter `0` to use all activities (the
default all-time behavior).

The multi-activity menu also offers Critical Power from the best three-minute
and twenty-minute efforts. It uses the two-parameter model implemented by
`CP.cp`; enter the same day window to calculate it, or `0` for all activities.

## Web frontend (calendar)

The `frontend/` directory holds a dependency-free web frontend (plain HTML, CSS
and JavaScript) styled after the old Today's Plan calendar. Each week row shows
the stored trainings on their dates with distance, time, average speed, average
power and average heart rate, plus a weekly summary (count, TSS, time, distance).
Click a training to open its own page (`ride.html?id=N`) with more details (NP,
IF, TSS, cadence, laps) and graphs of
power (3 s smoothed), heart rate, speed and cadence over the whole training,
stacked on one time axis with dashed lap markers. Hover a graph to read every
value at that moment. Previous/Next (or ← →) step through trainings, and "Back to
calendar" returns to that week.

The **Dashboard** tab shows your best average power for every duration (1 s to 3 h)
as a power curve and a table, for the last 7, 42, 90 or 365 days, all time, or custom
dates. For any period other than all time, your best ever is drawn alongside (grey,
dashed) and the table shows each number as a percentage of it, marking new records with
★. Each best links to the ride it came from. Tiles above sum up the period (rides, time,
distance, TSS) and estimate FTP as 95% of the best 20 minutes. The chosen period is kept
in the address (`dashboard.html#days=42`, `#from=2025-01-01&to=2025-03-31`).

Each ride's summary, including its best-power curve, is worked out once and saved in the
database (table `summaries`), so the server only keeps these small summaries in memory and
reads a ride's full data only when its page is opened. With about 1,000 rides the training
list loads in well under a second; the first start after this change works out every
summary once (about a minute for 1,000 rides, a few seconds for 100).

Frontend files: `index.html` + `app.js` (calendar), `dashboard.html` + `dashboard.js` +
`dashboard.css` (dashboard), `ride.html` + `ride.js` + `ride.css` (training page),
`charts.js` (the ride graphs), `common.js` (helpers, athlete menu and upload, used by
every page) and `styles.css` (shared look).

Start it with `powershell -ExecutionPolicy Bypass -File .
un-server.ps1` (compiles and
starts on port 8080; add `-Port 9000` for another port), with the `BetterThanBasic Web`
run configuration, or run the main class
`Server.WebServer` from the project root (so `frontend/` and `training.db` are
found), then open <http://localhost:8080>.

Arguments: `[port] [fit-directory]`. When a directory is given, new FIT files in
it are imported into `training.db` before the server starts. The ↻ button in the
top bar re-reads the database, e.g. after importing files through the console app.

To add trainings from the browser, open the **Athlete** menu (top right) and choose
**Upload training files (.fit, .json)…** (select as many as you like) or **Upload a folder…**
(every `.fit` and `.json` file in the folder and its subfolders). `.json` files are
GoldenCheetah ride files (`RIDE` → `SAMPLES` with `SECS`, `WATTS`, `HR`, `KPH`, `CAD`, `KM`);
they are read by `Fit.GoldenCheetahParser` (with a small built-in JSON reader, no library)
into the same data as FIT files, so they appear in the calendar, ride page and graphs the
same way. Laps come from the `Lap N` intervals and FTP from the session data. Files are sent one at a time with automatic
retries, the page warns before you leave it mid-upload, and a report at the end lists
how many were added, already stored (skipped) or failed, and why. A file counts as already
stored when a file with the same name was imported before, or when a stored ride starts
within 2 minutes of it (the same ride saved as both FIT and JSON is only kept once).

The same menu switches between athletes, adds a new one and renames the current one.
Every athlete has their own trainings: the first athlete uses `training.db` and
`uploads/` (the same data the console app uses); athletes added later get
`athletes/<id>/training.db` and `athletes/<id>/uploads/`. The list of athletes is
kept in `athletes.txt`. All of these are personal data and ignored by git.

The server uses only the JDK's built-in `com.sun.net.httpserver` and listens on
localhost only. API: `GET /api/athletes` lists athletes; every training request takes `?athlete=<id>`
(default: the first athlete). `GET /api/activities` returns per-training summaries as JSON;
`GET /api/activities/{id}/records` returns one training's samples for the graphs
(the id is the training's start time in epoch milliseconds); `POST /api/upload?name=<file>.fit|.json`
with the raw file as the body and the header `X-BetterThanBasic-Upload: 1` imports a FIT file.
