package qupath.ext.flowpath.io;

import qupath.ext.flowpath.engine.GatingEngine;
import qupath.ext.flowpath.model.Branch;
import qupath.ext.flowpath.model.CellIndex;
import qupath.ext.flowpath.model.GateNode;
import qupath.ext.flowpath.model.GateTree;
import qupath.ext.flowpath.model.MarkerStats;
import qupath.ext.flowpath.model.EllipseGate;
import qupath.ext.flowpath.model.PolygonGate;
import qupath.ext.flowpath.model.QuadrantGate;
import qupath.ext.flowpath.model.RectangleGate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exports cell phenotype assignments to a CSV file.
 * <p>
 * Each row represents a single cell. Columns include identity, spatial coordinates,
 * geometry measurements, and per-marker triplets (raw intensity, z-score, gating sign).
 * <p>
 * The {@code <marker>_sign} column reports independent positivity for each marker:
 * a cell is {@code "+"} on a marker if it passes <em>at least one</em> threshold
 * imposed on that marker anywhere in the gate tree (1D thresholds from threshold
 * gates and quadrant gates, plus 2D region containment from polygon/rectangle/
 * ellipse gates). Markers that have no threshold and no region gate anywhere in
 * the tree get a blank sign.
 */
public class PhenotypeCsvExporter {

    private PhenotypeCsvExporter() {
        // static utility class
    }

    /** A 1D threshold imposed on a marker by a ThresholdGate or one axis of a QuadrantGate. */
    private record MarkerThreshold(double threshold, boolean isZScore) {}

    /**
     * Export phenotype assignments to CSV with raw intensities, z-scores, and signs.
     */
    public static void export(File file, CellIndex index, GatingEngine.AssignmentResult result,
                              GateTree tree, MarkerStats stats) throws IOException {

        List<String> markerColumns = collectAllMarkers(tree, index);

        // Threshold inventory: 1D cuts grouped by marker, 2D region gates grouped by each axis channel.
        Map<String, List<MarkerThreshold>> thresholdsByMarker = new LinkedHashMap<>();
        Map<String, List<GateNode>> regionGatesByChannel = new LinkedHashMap<>();
        for (GateNode root : tree.getRoots()) {
            collectThresholdsRecursive(root, thresholdsByMarker, regionGatesByChannel);
        }

        String[] phenotypes = result.getPhenotypes();
        boolean[] outOfAnnotation = result.getOutOfAnnotation();
        boolean[] outlier = result.getOutlier();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            // Header — Out_of_annotation and Outlier flag cells excluded from QuPath
            // visual classification but still written as CSV rows.
            writer.write("cell_id,phenotype,Out_of_annotation,Outlier,centroid_x,centroid_y,area,perimeter,eccentricity,solidity");
            for (String marker : markerColumns) {
                String safe = escapeCsv(marker);
                writer.write("," + safe + "_raw");
                writer.write("," + safe + "_zscore");
                writer.write("," + safe + "_sign");
            }
            writer.newLine();

            int n = index.getSize();
            for (int i = 0; i < n; i++) {
                String phenotype = phenotypes[i] != null ? phenotypes[i] : "";

                writer.write(String.valueOf(i));
                writer.write(',');
                writer.write(escapeCsv(phenotype));
                writer.write(',');
                writer.write(outOfAnnotation[i] ? "True" : "False");
                writer.write(',');
                writer.write(outlier[i] ? "True" : "False");

                writer.write(',' + fmt(index.getCentroidX(i)));
                writer.write(',' + fmt(index.getCentroidY(i)));
                writer.write(',' + fmt(index.getArea(i)));
                writer.write(',' + fmt(index.getPerimeter(i)));
                writer.write(',' + fmt(index.getEccentricity(i)));
                writer.write(',' + fmt(index.getSolidity(i)));

                for (String marker : markerColumns) {
                    int mIdx = index.getMarkerIndex(marker);
                    double raw = (mIdx >= 0) ? index.getMarkerValues(mIdx)[i] : Double.NaN;

                    double zscore;
                    if (Double.isNaN(raw) || stats == null || stats.getStd(marker) <= 1e-10) {
                        zscore = Double.NaN;
                    } else {
                        zscore = stats.toZScore(marker, raw);
                    }

                    String sign = computeSign(i, marker, raw, index, stats,
                                              thresholdsByMarker.get(marker),
                                              regionGatesByChannel.get(marker));

                    writer.write(',' + fmt(raw));
                    writer.write(',' + fmt(zscore));
                    writer.write(',' + escapeCsv(sign));
                }
                writer.newLine();
            }
        }
    }

    /**
     * DFS the tree (enabled gates only), collecting 1D thresholds per marker and
     * 2D region gates per axis channel.
     */
    private static void collectThresholdsRecursive(
            GateNode node,
            Map<String, List<MarkerThreshold>> thresholds,
            Map<String, List<GateNode>> regionGates) {
        if (!node.isEnabled()) return;
        if (node instanceof QuadrantGate qg) {
            boolean z = qg.isThresholdIsZScore();
            thresholds.computeIfAbsent(qg.getChannelX(), k -> new ArrayList<>())
                      .add(new MarkerThreshold(qg.getThresholdX(), z));
            thresholds.computeIfAbsent(qg.getChannelY(), k -> new ArrayList<>())
                      .add(new MarkerThreshold(qg.getThresholdY(), z));
        } else if (node instanceof PolygonGate || node instanceof RectangleGate || node instanceof EllipseGate) {
            List<String> chs = node.getChannels();
            if (chs.size() >= 2) {
                regionGates.computeIfAbsent(chs.get(0), k -> new ArrayList<>()).add(node);
                regionGates.computeIfAbsent(chs.get(1), k -> new ArrayList<>()).add(node);
            }
        } else {
            // ThresholdGate: 1D cut on a single channel
            String ch = node.getChannel();
            if (ch != null && !ch.isEmpty()) {
                thresholds.computeIfAbsent(ch, k -> new ArrayList<>())
                          .add(new MarkerThreshold(node.getThreshold(), node.isThresholdIsZScore()));
            }
        }
        for (Branch b : node.getBranches()) {
            for (GateNode child : b.getChildren()) {
                collectThresholdsRecursive(child, thresholds, regionGates);
            }
        }
    }

    /**
     * Decide marker positivity for a cell by OR-combining every imposed threshold:
     * 1D cuts from ThresholdGate / QuadrantGate axes (compare-mode honors each
     * gate's z-score flag), plus 2D containment from PolygonGate / RectangleGate /
     * EllipseGate (cell inside region → "+" on both axis channels).
     * <p>
     * Returns blank if the marker has no threshold or region anywhere in the tree.
     * Mirrors {@code GatingEngine.walkThresholdNode} and {@code walk2DNode}.
     */
    private static String computeSign(int cellIdx, String marker, double raw,
                                       CellIndex index, MarkerStats stats,
                                       List<MarkerThreshold> thresholds,
                                       List<GateNode> regionGates) {
        boolean hasThresholds = thresholds != null && !thresholds.isEmpty();
        boolean hasRegions = regionGates != null && !regionGates.isEmpty();
        if (!hasThresholds && !hasRegions) return "";
        if (Double.isNaN(raw)) return "";

        if (hasThresholds) {
            for (MarkerThreshold t : thresholds) {
                double cmp;
                if (t.isZScore()) {
                    if (stats == null || stats.getStd(marker) <= 1e-10) continue;
                    cmp = stats.toZScore(marker, raw);
                } else {
                    cmp = raw;
                }
                if (cmp >= t.threshold()) return "+";
            }
        }

        if (hasRegions) {
            for (GateNode gate : regionGates) {
                List<String> chs = gate.getChannels();
                if (chs.size() < 2) continue;
                String chX = chs.get(0);
                String chY = chs.get(1);
                int xIdx = index.getMarkerIndex(chX);
                int yIdx = index.getMarkerIndex(chY);
                if (xIdx < 0 || yIdx < 0) continue;
                double rawX = index.getMarkerValues(xIdx)[cellIdx];
                double rawY = index.getMarkerValues(yIdx)[cellIdx];
                if (Double.isNaN(rawX) || Double.isNaN(rawY)) continue;
                double vx;
                double vy;
                if (gate.isThresholdIsZScore()) {
                    if (stats == null
                            || stats.getStd(chX) <= 1e-10
                            || stats.getStd(chY) <= 1e-10) continue;
                    vx = stats.toZScore(chX, rawX);
                    vy = stats.toZScore(chY, rawY);
                } else {
                    vx = rawX;
                    vy = rawY;
                }
                boolean inside;
                if (gate instanceof PolygonGate pg) inside = pg.contains(vx, vy);
                else if (gate instanceof RectangleGate rg) inside = rg.contains(vx, vy);
                else if (gate instanceof EllipseGate eg) inside = eg.contains(vx, vy);
                else continue;
                if (inside) return "+";
            }
        }

        return "-";
    }

    /** Format a double for CSV; NaN → empty string. Uses US locale to ensure dot decimal separator. */
    private static String fmt(double val) {
        return Double.isNaN(val) ? "" : String.format(java.util.Locale.US, "%.4f", val);
    }

    /**
     * Collect all marker channels: gated ones first (depth-first order),
     * then any remaining markers from the cell index that are not in the tree.
     */
    private static List<String> collectAllMarkers(GateTree tree, CellIndex index) {
        Set<String> seen = new LinkedHashSet<>();
        for (GateNode root : tree.getRoots()) {
            collectChannelsRecursive(root, seen);
        }
        for (String m : index.getMarkerNames()) {
            seen.add(m);
        }
        return new ArrayList<>(seen);
    }

    private static void collectChannelsRecursive(GateNode node, Set<String> seen) {
        seen.addAll(node.getChannels());
        for (Branch branch : node.getBranches()) {
            for (GateNode child : branch.getChildren()) {
                collectChannelsRecursive(child, seen);
            }
        }
    }

    /**
     * Escape a value for CSV output. Wraps in double quotes if it contains a
     * comma, double quote, or newline.
     */
    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
