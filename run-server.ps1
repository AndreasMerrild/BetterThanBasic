# Compiles BetterThanBasic and starts the web server (frontend + API).
#
#   powershell -ExecutionPolicy Bypass -File .\run-server.ps1            # port 8080
#   powershell -ExecutionPolicy Bypass -File .\run-server.ps1 -Port 9000
#   powershell -ExecutionPolicy Bypass -File .\run-server.ps1 -CompileOnly
#
# Stop the server with Ctrl+C. Uses the JDK 25 that IntelliJ installed in ~\.jdks and the
# FIT SDK + SQLite jars that Maven downloaded to ~\.m2, so Maven itself is not needed.

param(
    [int]$Port = 8080,
    [switch]$CompileOnly
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot   # training.db, uploads\ and frontend\ are found relative to the project

# --- JDK 25 (the project uses Java 25 features) ---
$jdkHome = Join-Path $env:USERPROFILE '.jdks\openjdk-25.0.2'
if (-not (Test-Path (Join-Path $jdkHome 'bin\java.exe'))) {
    $found = Get-ChildItem (Join-Path $env:USERPROFILE '.jdks') -Directory -Filter '*25*' -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
    if (-not $found) {
        throw "No JDK 25 found in $(Join-Path $env:USERPROFILE '.jdks'). Install one via IntelliJ: File > Project Structure > SDKs."
    }
    $jdkHome = $found.FullName
}
$java  = Join-Path $jdkHome 'bin\java.exe'
$javac = Join-Path $jdkHome 'bin\javac.exe'

# --- Libraries from the Maven cache ---
$m2 = Join-Path $env:USERPROFILE '.m2\repository'
$jars = @(
    (Join-Path $m2 'com\garmin\fit\21.218.0\fit-21.218.0.jar'),
    (Join-Path $m2 'org\xerial\sqlite-jdbc\3.49.1.0\sqlite-jdbc-3.49.1.0.jar')
)
foreach ($jar in $jars) {
    if (-not (Test-Path $jar)) {
        throw "Missing library $jar. Open the project in IntelliJ and reload the Maven project so it is downloaded."
    }
}
$classpath = $jars -join ';'

# --- Compile ---
Write-Host "Compiling with $jdkHome ..."
$sources = (Get-ChildItem src -Recurse -Filter *.java).FullName
& $javac -d target\classes -cp $classpath $sources
if ($LASTEXITCODE -ne 0) { throw "Compilation failed (see the errors above)." }
Write-Host "Compiled."
if ($CompileOnly) { return }

# --- Start ---
& $java --enable-native-access=ALL-UNNAMED -cp "target\classes;$classpath" Server.WebServer $Port
