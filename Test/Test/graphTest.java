package Test;

import Application.Graph;
import Application.BestPowerNumbers;
import Fit.FitActivity;
import Fit.FitParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class graphTest {
    private FitActivity activity;

    @BeforeEach
    void loadFitActivity() throws IOException {
        Path fitFile = Path.of("Test", "Test", "2026-01-20-125514-ELEMNT ROAM EFB1-17-0.fit");
        activity = new FitParser().parse(fitFile);
    }

    @Test
    void testPowerGraphRendersLoadedActivity() {
        Graph graph = new Graph(BestPowerNumbers.powerPoints(activity));
        BufferedImage image = new BufferedImage(900, 550, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        assertDoesNotThrow(() -> graph.paint(graphics));
        graphics.dispose();
    }
}
