package qupath.ext.gatetree.io;

import qupath.ext.gatetree.engine.GatingEngine;
import qupath.ext.gatetree.model.CellIndex;
import qupath.ext.gatetree.model.GateNode;
import qupath.ext.gatetree.model.GateTree;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Exports cell phenotype assignments to a CSV file.
 * <p>
 * Each row represents a single cell. Columns include the cell identifier,
 * the assigned phenotype, and one column per marker channel indicating
 * whether the cell was classified as positive (+) or negative (-) for
 * that marker.
 */
public class PhenotypeCsvExporter {

    private PhenotypeCsvExporter() {
        // static utility class
    }

    /**
     * Export phenotype assignments to CSV.
     *
     * @param file   destination CSV file
     * @param index  the cell index containing objects and marker data
     * @param result the gating assignment result
     * @param tree   the gate tree used for the assignment
     * @throws IOException if writing fails
     */
    public static void export(File file, CellIndex index, GatingEngine.AssignmentResult result,
                              GateTree tree) throws IOException {

        // 1. Collect all unique marker channels in the gate tree (preserving order)
        List<String> markerColumns = collectMarkerChannels(tree);

        // 2. Build a lookup from phenotype name -> marker sign map
        //    so we can determine +/- for each marker given a phenotype
        Map<String, Map<String, String>> phenotypeMarkerSigns = new LinkedHashMap<>();
        for (GateNode root : tree.getRoots()) {
            traceMarkerSigns(root, new LinkedHashMap<>(), phenotypeMarkerSigns);
        }

        boolean excludeFiltered = tree.getQualityFilter().isExcludeFromCsv();
        String[] phenotypes = result.getPhenotypes();
        boolean[] excluded = result.getExcluded();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            // Write header
            writer.write("cell_id,phenotype,centroid_x,centroid_y,area");
            for (String marker : markerColumns) {
                writer.write(',');
                writer.write(escapeCsv(marker));
            }
            writer.newLine();

            // Write one row per non-excluded cell
            int n = index.getSize();
            for (int i = 0; i < n; i++) {
                if (excludeFiltered && excluded[i]) {
                    continue;
                }

                String phenotype = phenotypes[i] != null ? phenotypes[i] : "";

                writer.write(String.valueOf(i));
                writer.write(',');
                writer.write(escapeCsv(phenotype));
                writer.write(',');
                writer.write(String.format("%.4f", index.getCentroidX(i)));
                writer.write(',');
                writer.write(String.format("%.4f", index.getCentroidY(i)));
                writer.write(',');
                writer.write(String.format("%.4f", index.getArea(i)));

                // Look up marker signs for this phenotype
                Map<String, String> signs = phenotypeMarkerSigns.get(phenotype);

                for (String marker : markerColumns) {
                    writer.write(',');
                    if (signs != null && signs.containsKey(marker)) {
                        writer.write(signs.get(marker));
                    }
                }
                writer.newLine();
            }
        }
    }

    /**
     * Collect all unique marker channel names used across the gate tree,
     * preserving the order of first encounter (depth-first).
     */
    private static List<String> collectMarkerChannels(GateTree tree) {
        Set<String> seen = new LinkedHashSet<>();
        for (GateNode root : tree.getRoots()) {
            collectChannelsRecursive(root, seen);
        }
        return new ArrayList<>(seen);
    }

    private static void collectChannelsRecursive(GateNode node, Set<String> seen) {
        if (node.getChannel() != null) {
            seen.add(node.getChannel());
        }
        for (GateNode child : node.getPositiveChildren()) {
            collectChannelsRecursive(child, seen);
        }
        for (GateNode child : node.getNegativeChildren()) {
            collectChannelsRecursive(child, seen);
        }
    }

    /**
     * Recursively trace all paths through the gate tree. At each leaf (a node
     * with no children on a given branch), record the accumulated marker signs
     * keyed by the leaf phenotype name.
     *
     * @param node         current gate node
     * @param currentSigns accumulated marker -> "+" or "-" along the current path
     * @param result       map from phenotype name to marker sign map
     */
    private static void traceMarkerSigns(GateNode node,
                                         Map<String, String> currentSigns,
                                         Map<String, Map<String, String>> result) {
        // Positive branch
        Map<String, String> positivePath = new LinkedHashMap<>(currentSigns);
        positivePath.put(node.getChannel(), "+");

        if (node.getPositiveChildren().isEmpty()) {
            // This is a leaf on the positive side
            result.put(node.getPositiveName(), new LinkedHashMap<>(positivePath));
        } else {
            for (GateNode child : node.getPositiveChildren()) {
                traceMarkerSigns(child, positivePath, result);
            }
        }

        // Negative branch
        Map<String, String> negativePath = new LinkedHashMap<>(currentSigns);
        negativePath.put(node.getChannel(), "-");

        if (node.getNegativeChildren().isEmpty()) {
            // This is a leaf on the negative side
            result.put(node.getNegativeName(), new LinkedHashMap<>(negativePath));
        } else {
            for (GateNode child : node.getNegativeChildren()) {
                traceMarkerSigns(child, negativePath, result);
            }
        }
    }

    /**
     * Escape a value for CSV output. Wraps the value in double quotes if it
     * contains a comma, double quote, or newline.
     */
    private static String escapeCsv(String value) {
        if (value == null)
            return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
