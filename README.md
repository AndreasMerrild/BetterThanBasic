# BetterThanBasic

A personal power-analysis tool for cycling, inspired by the old Today's Plan calendar.
It reads your FIT and GoldenCheetah JSON ride files and shows them in a web app.

## Start it

From the project folder, in PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\run-server.ps1
```

Then open <http://localhost:8080>. Stop the server with Ctrl+C.
Add `-Port 9000` to use another port. Needs JDK 25 in `~\.jdks` and the Maven libraries
(open the project in IntelliJ once and reload Maven).

## What you can do

- **Calendar** – your rides on their dates with distance, time, speed, power and heart rate,
  plus weekly totals. Scroll to load earlier or later weeks.
- **Ride page** – click a ride to see all its numbers and graphs of power, heart rate,
  speed and cadence. Hover a graph to read the values.
- **Dashboard** – your best power for every duration (1 s – 3 h) for the last 7, 42, 90 or
  365 days, all time or custom dates, compared with your best ever. Also shows an
  estimated FTP (95% of your best 20 minutes).
- **Upload** – in the Athlete menu (top right), upload `.fit` or `.json` files or a whole
  folder. Rides that are already stored are skipped.
- **Athletes** – in the same menu, switch between athletes, add new ones or rename them.
  Each athlete has their own rides.

There is also an older console app (run `Main`) for FTP zones, single-ride analysis,
best power numbers and Critical Power.

## Where your data is

| What | Where |
|---|---|
| First athlete's rides | `training.db` and `uploads/` |
| Other athletes | `athletes/<name>/` |
| List of athletes | `athletes.txt` |

All of these are personal data and are ignored by git.

## How it is built

- **Backend** (`src/`, Java, no web framework): reads ride files, stores them in SQLite and
  does **all** calculations. The web server is in `src/Server`.
- **Frontend** (`frontend/`, plain HTML/CSS/JavaScript, no packages): only fetches the
  results from the backend and draws them.

Only two libraries are used: Garmin's FIT SDK and SQLite.

<details>
<summary>API reference</summary>

Every request takes `?athlete=<id>` (default: the first athlete). A ride's id is its start time
in milliseconds. Changing requests need the header `X-BetterThanBasic-Upload: 1`.

| Request | Answer |
|---|---|
| `GET /api/athletes` | the athletes |
| `GET /api/calendar?from=YYYY-MM-DD&weeks=N` | rides per day and totals per week |
| `GET /api/dashboard?period=7\|42\|90\|365\|all` or `?from=…&to=…` | best power and totals for a period |
| `GET /api/activities` | all ride summaries |
| `GET /api/activities/{id}` | one ride, with the previous and next ride |
| `GET /api/activities/{id}/graph?points=N` | graph data for one ride |
| `POST /api/upload?name=<file>` | upload a `.fit` or `.json` file |
| `POST /api/reload` | re-read the database |
| `POST /api/athletes?name=` | add an athlete |
| `POST /api/athletes/rename?athlete=&name=` | rename an athlete |

</details>
