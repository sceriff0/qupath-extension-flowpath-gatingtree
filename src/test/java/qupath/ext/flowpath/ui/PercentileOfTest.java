package qupath.ext.flowpath.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PercentileOfTest {

    @Test
    void medianOfOddArray() {
        double[] v = {1, 2, 3, 4, 5};
        assertEquals(3.0, GateEditorPane.percentileOf(v, 50.0), 1e-9);
    }

    @Test
    void minAndMaxPercentiles() {
        double[] v = {10, 20, 30, 40};
        assertEquals(10.0, GateEditorPane.percentileOf(v, 0.0), 1e-9);
        assertEquals(40.0, GateEditorPane.percentileOf(v, 100.0), 1e-9);
    }

    @Test
    void ignoresNaNs() {
        double[] v = {Double.NaN, 1, 2, 3, Double.NaN};
        assertEquals(2.0, GateEditorPane.percentileOf(v, 50.0), 1e-9);
    }

    @Test
    void emptyOrAllNaNReturnsNaN() {
        assertTrue(Double.isNaN(GateEditorPane.percentileOf(new double[0], 50.0)));
        assertTrue(Double.isNaN(GateEditorPane.percentileOf(new double[]{Double.NaN}, 50.0)));
    }
}
