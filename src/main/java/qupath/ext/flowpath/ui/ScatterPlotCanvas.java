package qupath.ext.flowpath.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.List;

/**
 * Canvas-based 2D scatter plot for visualizing cells on two marker axes.
 * Supports overlay of gate boundaries (polygon, rectangle, ellipse).
 * Subsamples for display if cell count exceeds MAX_DISPLAY_POINTS.
 */
public class ScatterPlotCanvas extends Canvas {

    private static final int MAX_DISPLAY_POINTS = 20000;
    private static final double PADDING_LEFT = 45;
    private static final double PADDING_RIGHT = 10;
    private static final double PADDING_TOP = 10;
    private static final double PADDING_BOTTOM = 30;
    private static final double DOT_SIZE = 2.0;

    private double[] xValues;
    private double[] yValues;
    private double minX, maxX, minY, maxY;
    private String labelX = "X";
    private String labelY = "Y";

    // Gate overlays
    private List<double[]> polygonVertices;
    private double[] rectBounds;  // [minX, maxX, minY, maxY]
    private double[] ellipseParams; // [centerX, centerY, radiusX, radiusY]

    private Color insideColor = Color.rgb(0, 200, 0, 0.6);
    private Color outsideColor = Color.rgb(128, 128, 128, 0.3);

    public ScatterPlotCanvas() {
        super(380, 300);
        widthProperty().addListener((obs, o, n) -> repaint());
        heightProperty().addListener((obs, o, n) -> repaint());
    }

    @Override public boolean isResizable() { return true; }
    @Override public double prefWidth(double h) { return 380; }
    @Override public double prefHeight(double w) { return 300; }
    @Override public double minWidth(double h) { return 200; }
    @Override public double minHeight(double w) { return 150; }
    @Override public double maxWidth(double h) { return Double.MAX_VALUE; }
    @Override public double maxHeight(double w) { return Double.MAX_VALUE; }

    @Override
    public void resize(double width, double height) {
        setWidth(width);
        setHeight(height);
        repaint();
    }

    public void setData(double[] xValues, double[] yValues, String labelX, String labelY) {
        this.xValues = xValues;
        this.yValues = yValues;
        this.labelX = labelX;
        this.labelY = labelY;

        if (xValues == null || yValues == null || xValues.length == 0) {
            repaint();
            return;
        }

        // Compute ranges
        minX = Double.MAX_VALUE; maxX = -Double.MAX_VALUE;
        minY = Double.MAX_VALUE; maxY = -Double.MAX_VALUE;
        for (int i = 0; i < xValues.length; i++) {
            if (!Double.isNaN(xValues[i]) && !Double.isNaN(yValues[i])) {
                minX = Math.min(minX, xValues[i]);
                maxX = Math.max(maxX, xValues[i]);
                minY = Math.min(minY, yValues[i]);
                maxY = Math.max(maxY, yValues[i]);
            }
        }
        if (maxX <= minX) maxX = minX + 1;
        if (maxY <= minY) maxY = minY + 1;

        // Add 5% padding
        double padX = (maxX - minX) * 0.05;
        double padY = (maxY - minY) * 0.05;
        minX -= padX; maxX += padX;
        minY -= padY; maxY += padY;

        repaint();
    }

    public void setPolygonOverlay(List<double[]> vertices) {
        this.polygonVertices = vertices;
        this.rectBounds = null;
        this.ellipseParams = null;
        repaint();
    }

    public void setRectangleOverlay(double minX, double maxX, double minY, double maxY) {
        this.rectBounds = new double[]{minX, maxX, minY, maxY};
        this.polygonVertices = null;
        this.ellipseParams = null;
        repaint();
    }

    public void setEllipseOverlay(double cx, double cy, double rx, double ry) {
        this.ellipseParams = new double[]{cx, cy, rx, ry};
        this.polygonVertices = null;
        this.rectBounds = null;
        repaint();
    }

    public void clearOverlay() {
        this.polygonVertices = null;
        this.rectBounds = null;
        this.ellipseParams = null;
        repaint();
    }

    public void setInsideColor(Color c) { this.insideColor = c; repaint(); }
    public void setOutsideColor(Color c) { this.outsideColor = c; repaint(); }

    private void repaint() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();

        gc.setFill(Color.rgb(30, 30, 30));
        gc.fillRect(0, 0, w, h);

        if (xValues == null || yValues == null || xValues.length == 0) {
            gc.setFill(Color.gray(0.5));
            gc.setFont(Font.font(12));
            gc.fillText("No data", w / 2 - 20, h / 2);
            return;
        }

        double plotW = w - PADDING_LEFT - PADDING_RIGHT;
        double plotH = h - PADDING_TOP - PADDING_BOTTOM;

        // Draw dots
        int step = Math.max(1, xValues.length / MAX_DISPLAY_POINTS);
        for (int i = 0; i < xValues.length; i += step) {
            if (Double.isNaN(xValues[i]) || Double.isNaN(yValues[i])) continue;
            double px = PADDING_LEFT + ((xValues[i] - minX) / (maxX - minX)) * plotW;
            double py = PADDING_TOP + plotH - ((yValues[i] - minY) / (maxY - minY)) * plotH;

            boolean inside = isInsideOverlay(xValues[i], yValues[i]);
            gc.setFill(inside ? insideColor : outsideColor);
            gc.fillOval(px - DOT_SIZE / 2, py - DOT_SIZE / 2, DOT_SIZE, DOT_SIZE);
        }

        // Draw gate overlay
        drawOverlay(gc, plotW, plotH);

        // Border
        gc.setStroke(Color.gray(0.3));
        gc.setLineWidth(1);
        gc.strokeRect(PADDING_LEFT, PADDING_TOP, plotW, plotH);

        // Axis labels
        gc.setFill(Color.gray(0.7));
        gc.setFont(Font.font(9));
        gc.fillText(String.format("%.2f", minX), PADDING_LEFT, h - 3);
        gc.fillText(String.format("%.2f", maxX), PADDING_LEFT + plotW - 30, h - 3);
        gc.fillText(labelX, PADDING_LEFT + plotW / 2 - 10, h - 3);

        gc.save();
        gc.translate(10, PADDING_TOP + plotH / 2);
        gc.rotate(-90);
        gc.fillText(labelY, 0, 0);
        gc.restore();
    }

    private boolean isInsideOverlay(double x, double y) {
        if (polygonVertices != null && polygonVertices.size() >= 3) {
            return pointInPolygon(x, y, polygonVertices);
        }
        if (rectBounds != null) {
            return x >= rectBounds[0] && x <= rectBounds[1] && y >= rectBounds[2] && y <= rectBounds[3];
        }
        if (ellipseParams != null) {
            double dx = (x - ellipseParams[0]) / ellipseParams[2];
            double dy = (y - ellipseParams[1]) / ellipseParams[3];
            return dx * dx + dy * dy <= 1.0;
        }
        return true; // No overlay = all inside
    }

    private void drawOverlay(GraphicsContext gc, double plotW, double plotH) {
        gc.setStroke(Color.YELLOW);
        gc.setLineWidth(1.5);

        if (polygonVertices != null && polygonVertices.size() >= 3) {
            double[] xp = new double[polygonVertices.size()];
            double[] yp = new double[polygonVertices.size()];
            for (int i = 0; i < polygonVertices.size(); i++) {
                xp[i] = PADDING_LEFT + ((polygonVertices.get(i)[0] - minX) / (maxX - minX)) * plotW;
                yp[i] = PADDING_TOP + plotH - ((polygonVertices.get(i)[1] - minY) / (maxY - minY)) * plotH;
            }
            gc.strokePolygon(xp, yp, xp.length);
        }

        if (rectBounds != null) {
            double rx = PADDING_LEFT + ((rectBounds[0] - minX) / (maxX - minX)) * plotW;
            double ry = PADDING_TOP + plotH - ((rectBounds[3] - minY) / (maxY - minY)) * plotH;
            double rw = ((rectBounds[1] - rectBounds[0]) / (maxX - minX)) * plotW;
            double rh = ((rectBounds[3] - rectBounds[2]) / (maxY - minY)) * plotH;
            gc.strokeRect(rx, ry, rw, rh);
        }

        if (ellipseParams != null) {
            double cx = PADDING_LEFT + ((ellipseParams[0] - minX) / (maxX - minX)) * plotW;
            double cy = PADDING_TOP + plotH - ((ellipseParams[1] - minY) / (maxY - minY)) * plotH;
            double erx = (ellipseParams[2] / (maxX - minX)) * plotW;
            double ery = (ellipseParams[3] / (maxY - minY)) * plotH;
            gc.strokeOval(cx - erx, cy - ery, erx * 2, ery * 2);
        }
    }

    private static boolean pointInPolygon(double x, double y, List<double[]> vertices) {
        boolean inside = false;
        int n = vertices.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = vertices.get(i)[0], yi = vertices.get(i)[1];
            double xj = vertices.get(j)[0], yj = vertices.get(j)[1];
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) {
                inside = !inside;
            }
        }
        return inside;
    }
}
