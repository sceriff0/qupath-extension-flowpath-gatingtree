package qupath.ext.flowpath.engine;

import qupath.ext.flowpath.model.Branch;
import qupath.ext.flowpath.model.CellIndex;
import qupath.ext.flowpath.model.GateNode;
import qupath.ext.flowpath.model.GateTree;
import qupath.ext.flowpath.model.MarkerStats;
import qupath.ext.flowpath.model.QualityFilter;
import qupath.ext.flowpath.model.EllipseGate;
import qupath.ext.flowpath.model.PolygonGate;
import qupath.ext.flowpath.model.QuadrantGate;
import qupath.ext.flowpath.model.RectangleGate;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Core gating logic that walks a {@link GateTree} and assigns phenotype labels
 * and colors to every cell in a {@link CellIndex}.
 */
public final class GatingEngine {

    private static final Logger logger = LoggerFactory.getLogger(GatingEngine.class);

    private GatingEngine() {
        // static utility class
    }

    /**
     * Result of running the gating engine over all cells.
     */
    public static final class AssignmentResult {
        private final String[] phenotypes;
        private final boolean[] excluded;
        private final int[] colors;
        private final List<int[]> perRootColors;
        private final List<String> rootLabels;

        AssignmentResult(String[] phenotypes, boolean[] excluded, int[] colors,
                         List<int[]> perRootColors, List<String> rootLabels) {
            this.phenotypes = phenotypes;
            this.excluded = excluded;
            this.colors = colors;
            this.perRootColors = perRootColors;
            this.rootLabels = rootLabels;
        }

        /** Phenotype label per cell, {@code null} for excluded cells. */
        public String[] getPhenotypes() {
            return phenotypes;
        }

        /** {@code true} for cells removed by the quality filter or outlier exclusion. */
        public boolean[] getExcluded() {
            return excluded;
        }

        /** Packed RGB color per cell (default: last root's color), 0 for excluded cells. */
        public int[] getColors() {
            return colors;
        }

        /**
         * Per-root color arrays for multi-root trees.
         * Each entry is a {@code int[cellCount]} with that root's leaf branch colors.
         * {@code null} when only a single enabled root exists.
         */
        public List<int[]> getPerRootColors() {
            return perRootColors;
        }

        /**
         * Names of enabled root gates, matching the order of {@link #getPerRootColors()}.
         * {@code null} when only a single enabled root exists.
         */
        public List<String> getRootLabels() {
            return rootLabels;
        }
    }

    /**
     * Assign phenotypes to every cell by walking the gate tree.
     * Delegates to {@link #assignAll(GateTree, CellIndex, MarkerStats, boolean[])}
     * with no ROI mask.
     */
    public static AssignmentResult assignAll(GateTree tree, CellIndex index, MarkerStats stats) {
        return assignAll(tree, index, stats, null);
    }

    /**
     * Assign phenotypes to every cell by walking the gate tree.
     *
     * @param tree      the gate tree (roots + quality filter)
     * @param index     columnar cell data
     * @param stats     per-marker statistics (mean, std, percentiles)
     * @param roiMask   optional boolean mask where {@code true} means the cell is inside the ROI;
     *                  {@code null} means no ROI filtering
     * @return assignment result with phenotypes, exclusion flags, and colors
     */
    public static AssignmentResult assignAll(GateTree tree, CellIndex index, MarkerStats stats,
                                              boolean[] roiMask) {
        int n = index.size();
        String[] phenotypes = new String[n];
        boolean[] excluded = new boolean[n];
        int[] colors = new int[n];

        // 1. Initialize all as Unclassified
        for (int i = 0; i < n; i++) {
            phenotypes[i] = "Unclassified";
        }

        // 2. Apply quality filter
        QualityFilter qf = tree.getQualityFilter();
        if (qf != null) {
            for (int i = 0; i < n; i++) {
                if (!qf.passes(index.getArea(i), index.getEccentricity(i),
                        index.getSolidity(i), index.getTotalIntensity(i), index.getPerimeter(i))) {
                    excluded[i] = true;
                    phenotypes[i] = null;
                }
            }
        }

        // 2b. Apply ROI mask
        if (roiMask != null) {
            for (int i = 0; i < n; i++) {
                if (!roiMask[i]) {
                    excluded[i] = true;
                    phenotypes[i] = null;
                }
            }
        }

        // 3. Walk gate tree for non-excluded cells
        // Reset transient counts on all nodes before walking
        List<GateNode> roots = tree.getRoots();
        resetCounts(roots);

        // Detect duplicate leaf names across roots
        Map<String, List<Integer>> duplicates = tree.findDuplicateLeafNames();
        if (!duplicates.isEmpty()) {
            logger.warn("Duplicate leaf branch names across roots: {}", duplicates.keySet());
        }

        // Count enabled roots to decide single vs. multi-root mode
        List<GateNode> enabledRoots = new ArrayList<>();
        for (GateNode root : roots) {
            if (root.isEnabled()) enabledRoots.add(root);
        }
        boolean multiRoot = enabledRoots.size() > 1;

        // Allocate per-root color arrays for multi-root mode
        List<int[]> perRootColors = null;
        List<String> rootLabels = null;
        if (multiRoot) {
            perRootColors = new ArrayList<>();
            rootLabels = new ArrayList<>();
            for (GateNode root : enabledRoots) {
                perRootColors.add(new int[n]);
                rootLabels.add(root.getChannels().isEmpty() ? "Root" : root.getChannels().get(0));
            }
        }

        for (int i = 0; i < n; i++) {
            if (excluded[i]) {
                continue;
            }
            walkRoots(roots, i, index, stats, phenotypes, excluded, colors, perRootColors);
        }

        // Null out phenotypes for cells that got excluded during outlier checks
        for (int i = 0; i < n; i++) {
            if (excluded[i]) {
                phenotypes[i] = null;
                colors[i] = 0;
                if (perRootColors != null) {
                    for (int[] rootColors : perRootColors) {
                        rootColors[i] = 0;
                    }
                }
            }
        }

        return new AssignmentResult(phenotypes, excluded, colors, perRootColors, rootLabels);
    }

    /**
     * Compute a boolean mask indicating which cells pass the quality filter.
     *
     * @param index  columnar cell data
     * @param filter quality filter criteria
     * @return boolean array where {@code true} means the cell passes
     */
    public static boolean[] computeQualityMask(CellIndex index, QualityFilter filter) {
        int n = index.size();
        boolean[] mask = new boolean[n];
        for (int i = 0; i < n; i++) {
            mask[i] = filter.passes(
                    index.getArea(i),
                    index.getEccentricity(i),
                    index.getSolidity(i),
                    index.getTotalIntensity(i),
                    index.getPerimeter(i));
        }
        return mask;
    }

    /**
     * Compute a boolean mask indicating which cells fall inside the given ROI.
     * If {@code roi} is {@code null}, all cells pass.
     */
    public static boolean[] computeRoiMask(CellIndex index, ROI roi) {
        int n = index.size();
        boolean[] mask = new boolean[n];
        if (roi == null) {
            Arrays.fill(mask, true);
            return mask;
        }
        for (int i = 0; i < n; i++) {
            PathObject obj = index.getObject(i);
            ROI cellRoi = (obj != null) ? obj.getROI() : null;
            mask[i] = (cellRoi != null) && roi.contains(cellRoi.getCentroidX(), cellRoi.getCentroidY());
        }
        return mask;
    }

    /**
     * Compute a boolean mask indicating which cells fall inside any of the given ROIs.
     * If the collection is empty, all cells are excluded.
     */
    public static boolean[] computeRoiMask(CellIndex index, Collection<ROI> rois) {
        int n = index.size();
        boolean[] mask = new boolean[n];
        if (rois.isEmpty()) return mask;
        for (int i = 0; i < n; i++) {
            PathObject obj = index.getObject(i);
            ROI cellRoi = (obj != null) ? obj.getROI() : null;
            if (cellRoi == null) continue;
            double cx = cellRoi.getCentroidX();
            double cy = cellRoi.getCentroidY();
            for (ROI roi : rois) {
                if (roi.contains(cx, cy)) {
                    mask[i] = true;
                    break;
                }
            }
        }
        return mask;
    }

    /**
     * Combine two boolean masks with logical AND. Both arrays must have the same length.
     */
    public static boolean[] combineMasks(boolean[] a, boolean[] b) {
        boolean[] result = new boolean[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i] && b[i];
        }
        return result;
    }

    /**
     * Compute a boolean mask indicating which cells would reach a specific gate node
     * by passing through all ancestor gates/branches in the tree hierarchy.
     * Root gates get all non-excluded cells. Child gates only get cells that passed
     * through their parent gate's branch.
     *
     * @param tree      the gate tree
     * @param target    the gate node to compute the ancestor mask for
     * @param index     columnar cell data
     * @param stats     per-marker statistics
     * @param baseMask  optional base mask (ROI + quality); null means all cells pass
     * @return boolean array where {@code true} means the cell reaches this gate
     */
    public static boolean[] computeAncestorMask(GateTree tree, GateNode target,
                                                 CellIndex index, MarkerStats stats,
                                                 boolean[] baseMask) {
        int n = index.size();
        boolean[] mask = new boolean[n];

        // Find the path from root to the target node
        java.util.List<Object> path = new java.util.ArrayList<>();
        if (!findPath(tree.getRoots(), target, path)) {
            // findPath returns false for both root nodes and nodes not in the tree.
            // Only fill with true if the target is actually a root node.
            if (tree.getRoots().contains(target)) {
                if (baseMask != null) {
                    System.arraycopy(baseMask, 0, mask, 0, n);
                } else {
                    Arrays.fill(mask, true);
                }
            }
            // If target is not in the tree at all, mask stays all-false
            return mask;
        }

        // Start with base mask (all cells that pass QF + ROI)
        if (baseMask != null) {
            System.arraycopy(baseMask, 0, mask, 0, n);
        } else {
            Arrays.fill(mask, true);
        }

        // Walk path: each entry is alternating GateNode, Branch (the branch the child is under)
        // For each ancestor gate+branch pair, keep only cells that land in that branch
        for (int p = 0; p < path.size(); p += 2) {
            GateNode gate = (GateNode) path.get(p);
            Branch branch = (Branch) path.get(p + 1);
            if (!gate.isEnabled()) continue;

            int branchIdx = gate.getBranches().indexOf(branch);
            // Use each ancestor gate's own z-score flag instead of the global one
            boolean gateUseZScore = gate.isThresholdIsZScore();
            for (int i = 0; i < n; i++) {
                if (!mask[i]) continue;
                int result = evaluateGate(gate, i, index, stats, gateUseZScore);
                if (result < 0 || result != branchIdx) {
                    mask[i] = false;
                }
            }
        }

        return mask;
    }

    /**
     * Find the path of (GateNode, Branch) pairs from a root to the target node.
     * Returns true if target is found as a child (not a root).
     */
    private static boolean findPath(List<GateNode> nodes, GateNode target, List<Object> path) {
        for (GateNode node : nodes) {
            for (Branch branch : node.getBranches()) {
                if (branch.getChildren().contains(target)) {
                    path.add(node);
                    path.add(branch);
                    return true;
                }
                // Recurse deeper
                if (findPath(branch.getChildren(), target, path)) {
                    path.add(0, node);
                    path.add(1, branch);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Evaluate which branch index a single cell falls into for a gate.
     * Returns -1 if the cell would be excluded (outlier).
     */
    private static int evaluateGate(GateNode node, int cellIdx,
                                     CellIndex index, MarkerStats stats, boolean useZScore) {
        if (node instanceof QuadrantGate qg) {
            int mxIdx = index.getMarkerIndex(qg.getChannelX());
            int myIdx = index.getMarkerIndex(qg.getChannelY());
            if (mxIdx < 0 || myIdx < 0) return -1;
            double rawX = index.getMarkerValues(mxIdx)[cellIdx];
            double rawY = index.getMarkerValues(myIdx)[cellIdx];
            if (node.isExcludeOutliers()) {
                double loX = stats.getPercentileValue(qg.getChannelX(), node.getClipPercentileLow());
                double hiX = stats.getPercentileValue(qg.getChannelX(), node.getClipPercentileHigh());
                if (!Double.isNaN(loX) && !Double.isNaN(hiX) && (rawX < loX || rawX > hiX)) return -1;
                double loY = stats.getPercentileValue(qg.getChannelY(), node.getClipPercentileLow());
                double hiY = stats.getPercentileValue(qg.getChannelY(), node.getClipPercentileHigh());
                if (!Double.isNaN(loY) && !Double.isNaN(hiY) && (rawY < loY || rawY > hiY)) return -1;
            }
            // Use each gate's own z-score flag for consistent evaluation
            boolean gateZScore = qg.isThresholdIsZScore();
            double cx = gateZScore ? stats.toZScore(qg.getChannelX(), rawX) : rawX;
            double cy = gateZScore ? stats.toZScore(qg.getChannelY(), rawY) : rawY;
            return qg.evaluateQuadrant(cx, cy);
        } else if (node instanceof PolygonGate || node instanceof RectangleGate || node instanceof EllipseGate) {
            List<String> channels = node.getChannels();
            if (channels.size() < 2) return -1;
            int mxIdx = index.getMarkerIndex(channels.get(0));
            int myIdx = index.getMarkerIndex(channels.get(1));
            if (mxIdx < 0 || myIdx < 0) return -1;
            double rawX = index.getMarkerValues(mxIdx)[cellIdx];
            double rawY = index.getMarkerValues(myIdx)[cellIdx];
            if (node.isExcludeOutliers()) {
                double loX = stats.getPercentileValue(channels.get(0), node.getClipPercentileLow());
                double hiX = stats.getPercentileValue(channels.get(0), node.getClipPercentileHigh());
                if (!Double.isNaN(loX) && !Double.isNaN(hiX) && (rawX < loX || rawX > hiX)) return -1;
                double loY = stats.getPercentileValue(channels.get(1), node.getClipPercentileLow());
                double hiY = stats.getPercentileValue(channels.get(1), node.getClipPercentileHigh());
                if (!Double.isNaN(loY) && !Double.isNaN(hiY) && (rawY < loY || rawY > hiY)) return -1;
            }
            // Use each gate's own z-score flag — boundaries match the scatter plot coordinate space
            boolean gateZScore = node.isThresholdIsZScore();
            double vx = gateZScore ? stats.toZScore(channels.get(0), rawX) : rawX;
            double vy = gateZScore ? stats.toZScore(channels.get(1), rawY) : rawY;
            boolean inside;
            if (node instanceof PolygonGate pg) inside = pg.contains(vx, vy);
            else if (node instanceof RectangleGate rg) inside = rg.contains(vx, vy);
            else inside = ((EllipseGate) node).contains(vx, vy);
            return inside ? 0 : 1;
        } else {
            // Threshold gate
            String channel = node.getChannel();
            int mIdx = index.getMarkerIndex(channel);
            if (mIdx < 0) return -1;
            double rawValue = index.getMarkerValues(mIdx)[cellIdx];
            if (node.isExcludeOutliers()) {
                double lo = stats.getPercentileValue(channel, node.getClipPercentileLow());
                double hi = stats.getPercentileValue(channel, node.getClipPercentileHigh());
                if (!Double.isNaN(lo) && !Double.isNaN(hi) && (rawValue < lo || rawValue > hi)) return -1;
            }
            // Use each gate's own z-score flag for consistent evaluation
            boolean gateZScore = node.isThresholdIsZScore();
            double compareValue = gateZScore ? stats.toZScore(channel, rawValue) : rawValue;
            return compareValue >= node.getThreshold() ? 0 : 1;
        }
    }

    // ---- private helpers ----

    private static void walkRoots(List<GateNode> roots, int cellIdx,
                                  CellIndex index, MarkerStats stats,
                                  String[] phenotypes, boolean[] excluded, int[] colors,
                                  List<int[]> perRootColors) {
        if (perRootColors == null) {
            // Single-root fast path — original behavior
            for (GateNode root : roots) {
                if (excluded[cellIdx]) return;
                walkNode(root, cellIdx, index, stats, phenotypes, excluded, colors);
            }
            return;
        }

        // Multi-root: collect per-root phenotypes, then build composite
        List<String> contributions = new ArrayList<>();
        List<Integer> contributedColors = new ArrayList<>();
        int enabledIdx = 0;

        for (GateNode root : roots) {
            if (excluded[cellIdx]) return;
            if (!root.isEnabled()) continue;

            // Clean slate for this root's walk
            phenotypes[cellIdx] = "Unclassified";
            colors[cellIdx] = 0;

            walkNode(root, cellIdx, index, stats, phenotypes, excluded, colors);

            // If cell got excluded during this root (outlier), stop entirely
            if (excluded[cellIdx]) return;

            // Capture this root's per-cell color
            perRootColors.get(enabledIdx)[cellIdx] = colors[cellIdx];

            // Collect non-Unclassified phenotypes for composite
            String rootPheno = phenotypes[cellIdx];
            if (rootPheno != null && !"Unclassified".equals(rootPheno)) {
                contributions.add(rootPheno);
                contributedColors.add(colors[cellIdx]);
            }
            enabledIdx++;
        }

        // Build composite phenotype using QuPath derived PathClass separator ": "
        if (contributions.isEmpty()) {
            phenotypes[cellIdx] = "Unclassified";
            colors[cellIdx] = 0;
        } else if (contributions.size() == 1) {
            phenotypes[cellIdx] = contributions.get(0);
            colors[cellIdx] = contributedColors.get(0);
        } else {
            phenotypes[cellIdx] = String.join(": ", contributions);
            // Default color: last contributing root
            colors[cellIdx] = contributedColors.get(contributedColors.size() - 1);
        }
    }

    private static void walkNode(GateNode node, int cellIdx,
                                 CellIndex index, MarkerStats stats,
                                 String[] phenotypes, boolean[] excluded, int[] colors) {
        if (!node.isEnabled()) return;
        if (node instanceof QuadrantGate qg) {
            walkQuadrantNode(qg, cellIdx, index, stats, phenotypes, excluded, colors);
        } else if (node instanceof PolygonGate || node instanceof RectangleGate || node instanceof EllipseGate) {
            walk2DNode(node, cellIdx, index, stats, phenotypes, excluded, colors);
        } else {
            walkThresholdNode(node, cellIdx, index, stats, phenotypes, excluded, colors);
        }
    }

    private static void walkThresholdNode(GateNode node, int cellIdx,
                                           CellIndex index, MarkerStats stats,
                                           String[] phenotypes, boolean[] excluded, int[] colors) {
        String channel = node.getChannel();
        int markerIdx = index.getMarkerIndex(channel);
        if (markerIdx < 0) {
            return;
        }

        double rawValue = index.getMarkerValues(markerIdx)[cellIdx];

        // Outlier exclusion based on percentile clip bounds
        if (node.isExcludeOutliers()) {
            double lo = stats.getPercentileValue(channel, node.getClipPercentileLow());
            double hi = stats.getPercentileValue(channel, node.getClipPercentileHigh());
            if (!Double.isNaN(lo) && !Double.isNaN(hi) && (rawValue < lo || rawValue > hi)) {
                excluded[cellIdx] = true;
                return;
            }
        }

        // Use each gate's own z-score flag for consistent evaluation
        boolean gateZScore = node.isThresholdIsZScore();
        double compareValue = gateZScore ? stats.toZScore(channel, rawValue) : rawValue;
        double threshold = node.getThreshold();

        Branch branch;
        if (compareValue >= threshold) {
            branch = node.getBranches().get(0); // positive
        } else {
            branch = node.getBranches().get(1); // negative
        }
        branch.setCount(branch.getCount() + 1);
        assignBranch(branch, cellIdx, index, stats, phenotypes, excluded, colors);
    }

    private static void walkQuadrantNode(QuadrantGate gate, int cellIdx,
                                          CellIndex index, MarkerStats stats,
                                          String[] phenotypes, boolean[] excluded, int[] colors) {
        int markerIdxX = index.getMarkerIndex(gate.getChannelX());
        int markerIdxY = index.getMarkerIndex(gate.getChannelY());
        if (markerIdxX < 0 || markerIdxY < 0) {
            return;
        }

        double rawX = index.getMarkerValues(markerIdxX)[cellIdx];
        double rawY = index.getMarkerValues(markerIdxY)[cellIdx];

        // Outlier exclusion on X channel
        if (gate.isExcludeOutliers()) {
            double loX = stats.getPercentileValue(gate.getChannelX(), gate.getClipPercentileLow());
            double hiX = stats.getPercentileValue(gate.getChannelX(), gate.getClipPercentileHigh());
            if (!Double.isNaN(loX) && !Double.isNaN(hiX) && (rawX < loX || rawX > hiX)) {
                excluded[cellIdx] = true;
                return;
            }
            double loY = stats.getPercentileValue(gate.getChannelY(), gate.getClipPercentileLow());
            double hiY = stats.getPercentileValue(gate.getChannelY(), gate.getClipPercentileHigh());
            if (!Double.isNaN(loY) && !Double.isNaN(hiY) && (rawY < loY || rawY > hiY)) {
                excluded[cellIdx] = true;
                return;
            }
        }

        // Use each gate's own z-score flag for consistent evaluation
        boolean gateZScore = gate.isThresholdIsZScore();
        double compareX = gateZScore ? stats.toZScore(gate.getChannelX(), rawX) : rawX;
        double compareY = gateZScore ? stats.toZScore(gate.getChannelY(), rawY) : rawY;

        int quadrant = gate.evaluateQuadrant(compareX, compareY);
        Branch branch = gate.getBranches().get(quadrant);
        branch.setCount(branch.getCount() + 1);
        assignBranch(branch, cellIdx, index, stats, phenotypes, excluded, colors);
    }

    private static void walk2DNode(GateNode node, int cellIdx,
                                      CellIndex index, MarkerStats stats,
                                      String[] phenotypes, boolean[] excluded, int[] colors) {
        List<String> channels = node.getChannels();
        if (channels.size() < 2) return;
        String chX = channels.get(0);
        String chY = channels.get(1);
        int mxIdx = index.getMarkerIndex(chX);
        int myIdx = index.getMarkerIndex(chY);
        if (mxIdx < 0 || myIdx < 0) return;

        double rawX = index.getMarkerValues(mxIdx)[cellIdx];
        double rawY = index.getMarkerValues(myIdx)[cellIdx];

        // Outlier exclusion (same semantics as threshold/quadrant gates)
        if (node.isExcludeOutliers()) {
            double loX = stats.getPercentileValue(chX, node.getClipPercentileLow());
            double hiX = stats.getPercentileValue(chX, node.getClipPercentileHigh());
            if (!Double.isNaN(loX) && !Double.isNaN(hiX) && (rawX < loX || rawX > hiX)) { excluded[cellIdx] = true; return; }
            double loY = stats.getPercentileValue(chY, node.getClipPercentileLow());
            double hiY = stats.getPercentileValue(chY, node.getClipPercentileHigh());
            if (!Double.isNaN(loY) && !Double.isNaN(hiY) && (rawY < loY || rawY > hiY)) { excluded[cellIdx] = true; return; }
        }

        // Use each gate's own z-score flag — boundaries match the scatter plot coordinate space
        boolean gateZScore = node.isThresholdIsZScore();
        double vx = gateZScore ? stats.toZScore(chX, rawX) : rawX;
        double vy = gateZScore ? stats.toZScore(chY, rawY) : rawY;
        boolean inside;
        if (node instanceof PolygonGate pg) {
            inside = pg.contains(vx, vy);
        } else if (node instanceof RectangleGate rg) {
            inside = rg.contains(vx, vy);
        } else if (node instanceof EllipseGate eg) {
            inside = eg.contains(vx, vy);
        } else {
            inside = false;
        }

        Branch branch = inside ? node.getBranches().get(0) : node.getBranches().get(1);
        branch.setCount(branch.getCount() + 1);
        assignBranch(branch, cellIdx, index, stats, phenotypes, excluded, colors);
    }

    private static void assignBranch(Branch branch, int cellIdx,
                                      CellIndex index, MarkerStats stats,
                                      String[] phenotypes,
                                      boolean[] excluded, int[] colors) {
        phenotypes[cellIdx] = branch.getName();
        colors[cellIdx] = branch.getColor();
        if (!branch.getChildren().isEmpty()) {
            for (GateNode child : branch.getChildren()) {
                if (excluded[cellIdx]) return;
                walkNode(child, cellIdx, index, stats, phenotypes, excluded, colors);
            }
        }
    }

    private static void resetCounts(List<GateNode> nodes) {
        if (nodes == null) {
            return;
        }
        for (GateNode node : nodes) {
            for (Branch branch : node.getBranches()) {
                branch.setCount(0);
                resetCounts(branch.getChildren());
            }
        }
    }
}
