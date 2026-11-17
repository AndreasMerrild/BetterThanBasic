package Test;

import CP.cp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class cpTest {
    @Test
    void calculatesCriticalPowerFromThreeAndTwentyMinuteEfforts() {
        assertEquals(250.0, new cp().calcCP(280.0, 450.0, 0), 0.000001);
    }

    @Test
    void rejectsInvalidEfforts() {
        assertThrows(IllegalArgumentException.class, () -> new cp().calcCP(0.0, 450.0, 0));
        assertThrows(IllegalArgumentException.class, () -> new cp().calcCP(280.0, 200.0, 0));
    }
}
