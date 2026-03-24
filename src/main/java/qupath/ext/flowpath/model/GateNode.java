package qupath.ext.flowpath.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all gate types in the gating hierarchy.
 * <p>
 * Every gate has clipping/outlier parameters and produces N output branches
 * (accessible via {@link #getBranches()}). Subclasses define the gate-specific
 * parameters and evaluation logic.
 * <p>
 * For backward compatibility, this class can be instantiated directly as a
 * threshold gate. Prefer using {@link ThresholdGate} for new code.
 */
public class GateNode {

    // --- Shared fields (all gate types) ---
    private double clipPercentileLow = 1.0;
    private double clipPercentileHigh = 99.0;
    private boolean excludeOutliers = false;

    // --- ThresholdGate-specific fields (kept here for backward compat) ---
    private String channel;
    private double threshold;
    private boolean thresholdIsZScore;

    // Branches: index 0 = positive, index 1 = negative
    private final Branch positiveBranch;
    private final Branch negativeBranch;

    /**
     * No-arg constructor for deserialization.
     */
    public GateNode() {
        this.positiveBranch = new Branch("", 0);
        this.negativeBranch = new Branch("", 0);
    }

    public GateNode(String channel) {
        this(channel, 0.0);
    }

    public GateNode(String channel, double threshold) {
        this.channel = channel;
        this.threshold = threshold;
        this.thresholdIsZScore = true;
        int defaultPosColor = (0 << 16) | (200 << 8) | 0; // green
        int defaultNegColor = (128 << 16) | (128 << 8) | 128; // gray
        this.positiveBranch = new Branch(channel + "+", defaultPosColor);
        this.negativeBranch = new Branch(channel + "-", defaultNegColor);
    }

    // ========== Branch-based API (new, generic) ==========

    /**
     * Return the output branches of this gate.
     * Threshold gates return [positive, negative].
     * Override in subclasses for different branch counts.
     */
    public List<Branch> getBranches() {
        return List.of(positiveBranch, negativeBranch);
    }

    /**
     * Return the marker channels this gate operates on.
     * Override in subclasses that use multiple channels.
     */
    public List<String> getChannels() {
        return channel != null ? List.of(channel) : List.of();
    }

    /**
     * Gate type discriminator for serialization.
     */
    public String getGateType() {
        return "threshold";
    }

    // ========== Shared getters/setters ==========

    public double getClipPercentileLow() { return clipPercentileLow; }
    public void setClipPercentileLow(double v) { this.clipPercentileLow = v; }

    public double getClipPercentileHigh() { return clipPercentileHigh; }
    public void setClipPercentileHigh(double v) { this.clipPercentileHigh = v; }

    public boolean isExcludeOutliers() { return excludeOutliers; }
    public void setExcludeOutliers(boolean v) { this.excludeOutliers = v; }

    // ========== ThresholdGate-specific getters/setters ==========

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }

    public boolean isThresholdIsZScore() { return thresholdIsZScore; }
    public void setThresholdIsZScore(boolean v) { this.thresholdIsZScore = v; }

    // ========== Backward-compatible branch accessors ==========

    public String getPositiveName() { return positiveBranch.getName(); }
    public void setPositiveName(String name) { positiveBranch.setName(name); }

    public String getNegativeName() { return negativeBranch.getName(); }
    public void setNegativeName(String name) { negativeBranch.setName(name); }

    public int getPositiveColor() { return positiveBranch.getColor(); }
    public void setPositiveColor(int color) { positiveBranch.setColor(color); }

    public int getNegativeColor() { return negativeBranch.getColor(); }
    public void setNegativeColor(int color) { negativeBranch.setColor(color); }

    public List<GateNode> getPositiveChildren() { return positiveBranch.getChildren(); }
    public void setPositiveChildren(List<GateNode> children) { positiveBranch.setChildren(children); }

    public List<GateNode> getNegativeChildren() { return negativeBranch.getChildren(); }
    public void setNegativeChildren(List<GateNode> children) { negativeBranch.setChildren(children); }

    public int getPosCount() { return positiveBranch.getCount(); }
    public void setPosCount(int count) { positiveBranch.setCount(count); }

    public int getNegCount() { return negativeBranch.getCount(); }
    public void setNegCount(int count) { negativeBranch.setCount(count); }

    // ========== Generic methods using branches ==========

    public boolean isLeaf() {
        for (Branch b : getBranches()) {
            if (!b.isLeaf()) return false;
        }
        return true;
    }

    public void collectLeafNames(List<String> out) {
        for (Branch branch : getBranches()) {
            if (branch.isLeaf()) {
                out.add(branch.getName());
            } else {
                for (GateNode child : branch.getChildren()) {
                    child.collectLeafNames(out);
                }
            }
        }
    }

    /**
     * Create a deep copy of this node and all descendants.
     */
    public GateNode deepCopy() {
        GateNode copy = new GateNode();
        copy.channel = this.channel;
        copy.threshold = this.threshold;
        copy.thresholdIsZScore = this.thresholdIsZScore;
        copy.clipPercentileLow = this.clipPercentileLow;
        copy.clipPercentileHigh = this.clipPercentileHigh;
        copy.excludeOutliers = this.excludeOutliers;
        // Copy branch metadata and children
        copy.positiveBranch.setName(this.positiveBranch.getName());
        copy.positiveBranch.setColor(this.positiveBranch.getColor());
        copy.positiveBranch.setChildren(new ArrayList<>());
        for (GateNode child : this.positiveBranch.getChildren()) {
            copy.positiveBranch.getChildren().add(child.deepCopy());
        }
        copy.negativeBranch.setName(this.negativeBranch.getName());
        copy.negativeBranch.setColor(this.negativeBranch.getColor());
        copy.negativeBranch.setChildren(new ArrayList<>());
        for (GateNode child : this.negativeBranch.getChildren()) {
            copy.negativeBranch.getChildren().add(child.deepCopy());
        }
        return copy;
    }

    /**
     * Copy transient counts from another node's branches into this one's.
     */
    public void transferCountsFrom(GateNode source) {
        List<Branch> myBranches = this.getBranches();
        List<Branch> srcBranches = source.getBranches();
        for (int i = 0; i < myBranches.size() && i < srcBranches.size(); i++) {
            myBranches.get(i).transferCountFrom(srcBranches.get(i));
        }
    }
}
