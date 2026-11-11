package Application;

import Fit.FitActivity;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Graph extends JPanel {
    private static final int AXIS_TICK_COUNT = 5;
    private static final int LEFT_MARGIN = 70;
    private static final int RIGHT_MARGIN = 25;
    private static final int TOP_MARGIN = 30;
    private static final int BOTTOM_MARGIN = 55;

    private final List<PowerPoint> points;
    private final boolean bestPowerGraph;
    private final boolean powerWeightGraph;
    private final boolean heartRateGraph;
    private final boolean cadenceGraph;
    private final List<GraphSeries> series;

    public Graph(List<PowerPoint> points) {
        this(points, false, false, false, false);
    }

    private Graph(List<PowerPoint> points, boolean bestPowerGraph,
                  boolean powerWeightGraph, boolean heartRateGraph, boolean cadenceGraph) {
        this.bestPowerGraph = bestPowerGraph;
        this.powerWeightGraph = powerWeightGraph;
        this.heartRateGraph = heartRateGraph;
        this.cadenceGraph = cadenceGraph;
        this.points = List.copyOf(points);
        this.series = List.of();
        setPreferredSize(new Dimension(900, 550));
        setBackground(Color.WHITE);
    }

    private Graph(List<GraphSeries> series, boolean combinedGraph) {
        this.points = List.of();
        this.bestPowerGraph = false;
        this.powerWeightGraph = false;
        this.heartRateGraph = false;
        this.cadenceGraph = false;
        this.series = List.copyOf(series);
        setPreferredSize(new Dimension(900, 550));
        setBackground(Color.WHITE);
    }
    public static void showPowerWeightGraph(FitActivity activity, int w) {
        showPowerWeightGraph(Collections.singletonList(activity), w);
    }
    public static void showPowerGraph(FitActivity activity) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("Cannot display the power graph because Java is running in headless mode.");
            return;
        }

        javax.swing.SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Power over time");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(new Graph(BestPowerNumbers.powerPoints(activity)));
            frame.pack();
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        });
    }

    public static void showHeartRateGraph(FitActivity activity) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("Cannot display the heart-rate graph because Java is running in headless mode.");
            return;
        }

        javax.swing.SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Heart rate over time");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(new Graph(BestPowerNumbers.heartRatePoints(activity),
                    false, false, true, false));
            frame.pack();
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        });
    }

    public static void showCadenceGraph(FitActivity activity) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("Cannot display the cadence graph because Java is running in headless mode.");
            return;
        }

        javax.swing.SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Cadence over time");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(new Graph(BestPowerNumbers.cadencePoints(activity),
                    false, false, false, true));
            frame.pack();
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        });
    }

    public static void showCombinedGraph(FitActivity activity) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("Cannot display the combined graph because Java is running in headless mode.");
            return;
        }

        List<GraphSeries> series = new ArrayList<>();
        series.add(new GraphSeries("Power (W)", BestPowerNumbers.powerPoints(activity),
                new Color(35, 100, 190)));
        series.add(new GraphSeries("Heart rate (bpm)", BestPowerNumbers.heartRatePoints(activity),
                new Color(190, 65, 50)));
        series.add(new GraphSeries("Cadence (rpm)", BestPowerNumbers.cadencePoints(activity),
                new Color(45, 145, 75)));

        javax.swing.SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Power, heart rate and cadence over time");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(new Graph(series, true));
            frame.pack();
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        });
    }

    public static void showBestPowerGraph(FitActivity activity) {
        showBestPowerGraph(Collections.singletonList(activity));
    }

    public static void showBestPowerGraph(List<FitActivity> activities) {
        showBestPowerGraph(activities, null);
    }

    public static void showBestPowerGraph(List<FitActivity> activities, Integer days) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("Cannot display the power graph because Java is running in headless mode.");
            return;
        }

        javax.swing.SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Best power duration curve");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(new Graph(bestPowerPoints(activities, days), true, false, false, false));
            frame.pack();
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        });
    }

    public static void showPowerWeightGraph(List<FitActivity> activities, int weight) {
        showPowerWeightGraph(activities, weight, null);
    }

    public static void showPowerWeightGraph(List<FitActivity> activities, int weight, Integer days) {
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be greater than zero.");
        }
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("Cannot display the power graph because Java is running in headless mode.");
            return;
        }

        javax.swing.SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Power/Weight over time");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(new Graph(bestPowerPoints(activities, weight, days), true, true, false, false));
            frame.pack();
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        });
    }

    private static List<PowerPoint> bestPowerPoints(List<FitActivity> activities, Integer days) {
        return days == null
                ? BestPowerNumbers.bestPowerPoints(activities)
                : BestPowerNumbers.bestPowerPoints(activities, days);
    }

    private static List<PowerPoint> bestPowerPoints(
            List<FitActivity> activities, int weight, Integer days) {
        return days == null
                ? BestPowerNumbers.bestPowerPointsWeight(activities, weight)
                : BestPowerNumbers.bestPowerPointsWeight(activities, weight, days);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (!series.isEmpty()) {
                paintCombinedGraph(g);
                return;
            }
            if (points.isEmpty()) {
                drawCenteredMessage(g, bestPowerGraph
                        ? "No power data for the requested intervals available."
                        : heartRateGraph
                        ? "No heart-rate data with timestamps available."
                        : cadenceGraph
                        ? "No cadence data with timestamps available."
                        : "No power data with timestamps available.");
                return;
            }

            int plotWidth = getWidth() - LEFT_MARGIN - RIGHT_MARGIN;
            int plotHeight = getHeight() - TOP_MARGIN - BOTTOM_MARGIN;
            double maxTimeMinutes = Math.max(points.get(points.size() - 1).seconds() / 60.0, 1);
            double maxPower = Math.max(points.stream().mapToDouble(PowerPoint::power).max().orElse(1), 1);
            double chartMaxPower;
            if (heartRateGraph || cadenceGraph) {
                chartMaxPower = Math.ceil(maxPower / 10.0) * 10.0 + 10.0;
            } else {
                chartMaxPower = powerWeightGraph
                        ? Math.ceil(maxPower) + 1
                        : Math.ceil(maxPower / 100.0) * 100.0 + 100.0;
            }

            g.setColor(Color.DARK_GRAY);
            g.drawLine(LEFT_MARGIN, TOP_MARGIN, LEFT_MARGIN, TOP_MARGIN + plotHeight);
            g.drawLine(LEFT_MARGIN, TOP_MARGIN + plotHeight,
                    LEFT_MARGIN + plotWidth, TOP_MARGIN + plotHeight);
            if (bestPowerGraph) {
                drawBestPowerAxisTicks(g, maxTimeMinutes, chartMaxPower, plotWidth, plotHeight);
            } else {
                drawAxisTicks(g, maxTimeMinutes, chartMaxPower, plotWidth, plotHeight,
                        heartRateGraph || cadenceGraph ? 10 : 100);
            }

            g.setColor(new Color(35, 100, 190));
            g.setStroke(new BasicStroke(2f));
            for (int i = 1; i < points.size(); i++) {
                PowerPoint previous = points.get(i - 1);
                PowerPoint current = points.get(i);
                double previousX = bestPowerGraph
                        ? x(i - 1, points.size() - 1, plotWidth)
                        : x(previous.seconds() / 60.0, maxTimeMinutes, plotWidth);
                double currentX = bestPowerGraph
                        ? x(i, points.size() - 1, plotWidth)
                        : x(current.seconds() / 60.0, maxTimeMinutes, plotWidth);
                g.draw(new Line2D.Double(
                        previousX,
                        y(previous.power(), chartMaxPower, plotHeight),
                        currentX,
                        y(current.power(), chartMaxPower, plotHeight)));
            }

            g.setColor(Color.DARK_GRAY);
            FontMetrics metrics = g.getFontMetrics();
            String xAxisLabel = bestPowerGraph ? "Interval" : "Time (minutes)";
            g.drawString(xAxisLabel, LEFT_MARGIN + (plotWidth - metrics.stringWidth(xAxisLabel)) / 2,
                    getHeight() - 15);
            g.rotate(-Math.PI / 2);
            String yAxisLabel = heartRateGraph
                    ? "Heart rate (bpm)"
                    : cadenceGraph ? "Cadence (rpm)"
                    : powerWeightGraph ? "Power/Weight (W/kg)" : "Power (W)";
            g.drawString(yAxisLabel, -(TOP_MARGIN + plotHeight / 2 + metrics.stringWidth(yAxisLabel) / 2), 20);
        } finally {
            g.dispose();
        }
    }

    private void paintCombinedGraph(Graphics2D g) {
        List<GraphSeries> visibleSeries = series.stream()
                .filter(graphSeries -> !graphSeries.points().isEmpty())
                .toList();
        if (visibleSeries.isEmpty()) {
            drawCenteredMessage(g, "No power, heart-rate, or cadence data with timestamps available.");
            return;
        }

        int plotWidth = getWidth() - LEFT_MARGIN - 140;
        int plotHeight = getHeight() - TOP_MARGIN - BOTTOM_MARGIN;
        double maxTimeMinutes = visibleSeries.stream()
                .mapToDouble(graphSeries -> graphSeries.points().getLast().seconds() / 60.0)
                .max()
                .orElse(1);
        maxTimeMinutes = Math.max(maxTimeMinutes, 1);
        double[] maxima = visibleSeries.stream()
                .mapToDouble(graphSeries -> Math.max(graphSeries.points().stream()
                        .mapToDouble(PowerPoint::power).max().orElse(1), 1))
                .toArray();

        drawCombinedAxisTicks(g, maxTimeMinutes, plotWidth, plotHeight, visibleSeries, maxima);
        for (int seriesIndex = 0; seriesIndex < visibleSeries.size(); seriesIndex++) {
            GraphSeries graphSeries = visibleSeries.get(seriesIndex);
            double maximum = maxima[seriesIndex];
            g.setColor(graphSeries.color());
            g.setStroke(new BasicStroke(2f));
            List<PowerPoint> graphPoints = graphSeries.points();
            for (int pointIndex = 1; pointIndex < graphPoints.size(); pointIndex++) {
                PowerPoint previous = graphPoints.get(pointIndex - 1);
                PowerPoint current = graphPoints.get(pointIndex);
                g.draw(new Line2D.Double(
                        x(previous.seconds() / 60.0, maxTimeMinutes, plotWidth),
                        y(previous.power(), maximum, plotHeight),
                        x(current.seconds() / 60.0, maxTimeMinutes, plotWidth),
                        y(current.power(), maximum, plotHeight)));
            }
        }

        g.setColor(Color.DARK_GRAY);
        FontMetrics metrics = g.getFontMetrics();
        String xAxisLabel = "Time (minutes)";
        g.drawString(xAxisLabel, LEFT_MARGIN + (plotWidth - metrics.stringWidth(xAxisLabel)) / 2,
                getHeight() - 15);
        int legendX = LEFT_MARGIN;
        int legendY = TOP_MARGIN - 10;
        for (GraphSeries graphSeries : visibleSeries) {
            g.setColor(graphSeries.color());
            g.fillRect(legendX, legendY - metrics.getAscent() + 2, 12, 12);
            g.setColor(Color.DARK_GRAY);
            g.drawString(graphSeries.label(), legendX + 16, legendY + 2);
            legendX += metrics.stringWidth(graphSeries.label()) + 38;
        }
    }

    private void drawCombinedAxisTicks(Graphics2D g, double maxTimeMinutes, int plotWidth,
                                       int plotHeight, List<GraphSeries> visibleSeries,
                                       double[] maxima) {
        FontMetrics metrics = g.getFontMetrics();
        g.setColor(Color.DARK_GRAY);
        g.drawLine(LEFT_MARGIN, TOP_MARGIN, LEFT_MARGIN, TOP_MARGIN + plotHeight);
        g.drawLine(LEFT_MARGIN, TOP_MARGIN + plotHeight,
                LEFT_MARGIN + plotWidth, TOP_MARGIN + plotHeight);
        for (int i = 0; i <= AXIS_TICK_COUNT; i++) {
            double fraction = (double) i / AXIS_TICK_COUNT;
            int x = (int) Math.round(LEFT_MARGIN + fraction * plotWidth);
            g.setColor(new Color(225, 225, 225));
            g.drawLine(x, TOP_MARGIN, x, TOP_MARGIN + plotHeight);
            g.setColor(Color.DARK_GRAY);
            String label = Integer.toString((int) Math.round(fraction * maxTimeMinutes));
            g.drawString(label, x - metrics.stringWidth(label) / 2,
                    TOP_MARGIN + plotHeight + metrics.getAscent() + 8);
        }

        for (int seriesIndex = 0; seriesIndex < visibleSeries.size(); seriesIndex++) {
            GraphSeries graphSeries = visibleSeries.get(seriesIndex);
            double maximum = maxima[seriesIndex];
            g.setColor(graphSeries.color());
            for (int tick = 0; tick <= maximum; tick += combinedTickSize(maximum)) {
                double fraction = tick / maximum;
                int y = (int) Math.round(TOP_MARGIN + plotHeight - fraction * plotHeight);
                String label = Integer.toString(tick);
                int labelX = seriesIndex == 0
                        ? LEFT_MARGIN - metrics.stringWidth(label) - 8
                        : LEFT_MARGIN + plotWidth + 8 + (seriesIndex - 1) * 55;
                g.drawString(label, labelX, y + metrics.getAscent() / 2);
            }
            String axisLabel = graphSeries.label();
            int labelX = seriesIndex == 0
                    ? 12
                    : LEFT_MARGIN + plotWidth + 8 + (seriesIndex - 1) * 55;
            g.rotate(-Math.PI / 2);
            g.drawString(axisLabel, -(TOP_MARGIN + plotHeight / 2
                    + metrics.stringWidth(axisLabel) / 2), labelX);
            g.rotate(Math.PI / 2);
        }
    }

    private int combinedTickSize(double maximum) {
        return maximum <= 200 ? 20 : 100;
    }

    private void drawAxisTicks(Graphics2D g, double maxTimeMinutes, double maxPower,
                               int plotWidth, int plotHeight, int tickSize) {
        FontMetrics metrics = g.getFontMetrics();
        for (int i = 0; i <= AXIS_TICK_COUNT; i++) {
            double fraction = (double) i / AXIS_TICK_COUNT;
            int x = (int) Math.round(LEFT_MARGIN + fraction * plotWidth);

            g.setColor(new Color(225, 225, 225));
            g.drawLine(x, TOP_MARGIN, x, TOP_MARGIN + plotHeight);

            g.setColor(Color.DARK_GRAY);
            String timeLabel = Integer.toString((int) Math.round(fraction * maxTimeMinutes));
            g.drawString(timeLabel, x - metrics.stringWidth(timeLabel) / 2,
                    TOP_MARGIN + plotHeight + metrics.getAscent() + 8);
        }

        for (int power = 0; power <= maxPower; power += tickSize) {
            double fraction = power / maxPower;
            int y = (int) Math.round(TOP_MARGIN + plotHeight - fraction * plotHeight);

            g.setColor(new Color(225, 225, 225));
            g.drawLine(LEFT_MARGIN, y, LEFT_MARGIN + plotWidth, y);

            g.setColor(Color.DARK_GRAY);
            String powerLabel = Integer.toString(power);
            g.drawString(powerLabel, LEFT_MARGIN - metrics.stringWidth(powerLabel) - 8,
                    y + metrics.getAscent() / 2);
        }

    }

    private void drawBestPowerAxisTicks(Graphics2D g, double maxTimeMinutes, double maxPower,
                                        int plotWidth, int plotHeight) {
        FontMetrics metrics = g.getFontMetrics();
        for (int index = 0; index < points.size(); index++) {
            PowerPoint point = points.get(index);
            int x = (int) Math.round(x(index, Math.max(points.size() - 1, 1), plotWidth));
            g.setColor(new Color(225, 225, 225));
            g.drawLine(x, TOP_MARGIN, x, TOP_MARGIN + plotHeight);
            g.setColor(Color.DARK_GRAY);
            String label = formatDurationLabel((int) point.seconds());
            g.drawString(label, x - metrics.stringWidth(label) / 2,
                    TOP_MARGIN + plotHeight + metrics.getAscent() + 8);
        }

        drawPowerTicks(g, maxPower, plotWidth, plotHeight);
    }

    private void drawPowerTicks(Graphics2D g, double maxPower, int plotWidth, int plotHeight) {
        FontMetrics metrics = g.getFontMetrics();
        double tickSize = powerWeightGraph ? 1 : 100;
        for (double power = 0; power <= maxPower; power += tickSize) {
            double fraction = power / maxPower;
            int y = (int) Math.round(TOP_MARGIN + plotHeight - fraction * plotHeight);
            g.setColor(new Color(225, 225, 225));
            g.drawLine(LEFT_MARGIN, y, LEFT_MARGIN + plotWidth, y);
            g.setColor(Color.DARK_GRAY);
            String powerLabel = powerWeightGraph
                    ? String.format("%.0f", power)
                    : Integer.toString((int) power);
            g.drawString(powerLabel, LEFT_MARGIN - metrics.stringWidth(powerLabel) - 8,
                    y + metrics.getAscent() / 2);
        }
    }

    private String formatDurationLabel(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds % 3600 == 0) {
            return seconds / 3600 + "h";
        }
        return seconds / 60 + "m";
    }

    private double x(double minutes, double maxTimeMinutes, int plotWidth) {
        return LEFT_MARGIN + minutes / maxTimeMinutes * plotWidth;
    }

    private double x(int index, int lastIndex, int plotWidth) {
        return LEFT_MARGIN + (double) index / lastIndex * plotWidth;
    }

    private double y(double power, double maxPower, int plotHeight) {
        return TOP_MARGIN + plotHeight - power / maxPower * plotHeight;
    }

    private void drawCenteredMessage(Graphics2D g, String message) {
        FontMetrics metrics = g.getFontMetrics();
        g.setColor(Color.DARK_GRAY);
        g.drawString(message, (getWidth() - metrics.stringWidth(message)) / 2, getHeight() / 2);
    }

    private record GraphSeries(String label, List<PowerPoint> points, Color color) {
        private GraphSeries {
            points = List.copyOf(points);
        }
    }

}
