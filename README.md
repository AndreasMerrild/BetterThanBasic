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

Choose `4 : Parse multiple .fit activity files` to enter a directory containing
FIT files. The application keeps all parsed activities in memory and prints the
duration and distance for each file, followed by the combined total time and
distance. Distance is calculated from the recorded FIT speeds and timestamps.

Best-power graphs, tables, and the best-20-minute FTP calculation can be
limited to activities from the last N days. Enter `7`, `90`, or another
non-negative number when prompted; enter `0` to use all activities (the
default all-time behavior).
