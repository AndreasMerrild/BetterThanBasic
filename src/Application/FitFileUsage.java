package Application;

import java.awt.*;
import java.nio.file.Path;

public class FitFileUsage {

    public static void printAverage(String label, Double value, String unit) {
        if (value == null) {
            System.out.println(label + ": unavailable");
        } else {
            System.out.printf("%s: %.2f%s%n", label, value, unit);
        }
    }

    public static String formatDuration(java.time.Duration duration) {
        long seconds = duration.getSeconds();
        return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    public static Path chooseFitFile() {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Cannot open the FIT file chooser because Java is running in headless mode.");
            return null;
        }

        FileDialog fileDialog = new FileDialog(
                (Frame) null, "Select a FIT activity file", FileDialog.LOAD);
        fileDialog.setFilenameFilter((directory, name) ->
                name.toLowerCase(java.util.Locale.ROOT).endsWith(".fit"));
        fileDialog.setAlwaysOnTop(true);
        fileDialog.setVisible(true);

        if (fileDialog.getFile() == null) {
            return null;
        }

        return Path.of(fileDialog.getDirectory(), fileDialog.getFile());
    }
}
