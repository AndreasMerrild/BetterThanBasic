package Application;

import Fit.FitActivity;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Compact table view for the best power duration curve.
 *
 * <p>The best-power values are calculated once for all supplied activities.
 * The power-to-weight column is derived from those same values, so a table
 * for one file and a table for several files use the same implementation.</p>
 */
public final class TableBestPowerNumbers extends JPanel {
    private static final String[] COLUMN_NAMES = {"Duration", "Power", "Power to weight"};

    public TableBestPowerNumbers(List<FitActivity> activities, int weight) {
        super(new BorderLayout(0, 4));
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be greater than zero.");
        }

        List<PowerPoint> points = BestPowerNumbers.bestPowerPoints(activities);
        JTable table = new JTable(new BestPowerTableModel(points, weight));
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFont(table.getFont().deriveFont(Font.PLAIN, 12f));
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(85);
        table.getColumnModel().getColumn(1).setPreferredWidth(75);
        table.getColumnModel().getColumn(2).setPreferredWidth(115);

        DefaultTableCellRenderer rightAligned = new DefaultTableCellRenderer();
        rightAligned.setHorizontalAlignment(DefaultTableCellRenderer.RIGHT);
        table.getColumnModel().getColumn(1).setCellRenderer(rightAligned);
        table.getColumnModel().getColumn(2).setCellRenderer(rightAligned);

        JLabel emptyLabel = new JLabel("No power data for the requested intervals available.");
        emptyLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        emptyLabel.setForeground(Color.DARK_GRAY);
        add(points.isEmpty() ? emptyLabel : new JScrollPane(table), BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        setBackground(Color.WHITE);
    }

    public static void showBestPowerTable(FitActivity activity, int weight) {
        showBestPowerTable(Collections.singletonList(activity), weight);
    }

    public static void showBestPowerTable(List<FitActivity> activities, int weight) {
        Objects.requireNonNull(activities, "activities");
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be greater than zero.");
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Cannot display the best power table because Java is running in headless mode.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Best power duration table");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setContentPane(new TableBestPowerNumbers(activities, weight));
            frame.pack();
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        });
    }

    static final class BestPowerTableModel extends DefaultTableModel {
        BestPowerTableModel(List<PowerPoint> points, int weight) {
            super(COLUMN_NAMES, 0);
            for (PowerPoint point : points) {
                addRow(new Object[]{
                        formatDuration((int) point.seconds()),
                        String.format("%.0f W", point.power()),
                        String.format("%.2f W/kg", point.power() / weight)
                });
            }
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        private static String formatDuration(int seconds) {
            if (seconds < 60) {
                return seconds + "s";
            }
            if (seconds % 3600 == 0) {
                return seconds / 3600 + "h";
            }
            return seconds / 60 + "m";
        }
    }
}
