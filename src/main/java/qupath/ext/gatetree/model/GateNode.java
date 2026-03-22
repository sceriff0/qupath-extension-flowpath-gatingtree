package qupath.ext.gatetree.model;

import java.util.ArrayList;
import java.util.List;

public class GateNode {

    private String channel;
    private double threshold;
    private boolean thresholdIsZScore;
    private String positiveName;
    private String negativeName;
    private int positiveColor = (0 << 16) | (200 << 8) | 0; // green
    private int negativeColor = (128 << 16) | (128 << 8) | 128; // gray
    private double clipPercentileLow = 1.0;
    private double clipPercentileHigh = 99.0;
    private boolean excludeOutliers = false;
    private List<GateNode> positiveChildren = new ArrayList<>();
    private List<GateNode> negativeChildren = new ArrayList<>();

    private transient int posCount;
    private transient int negCount;

    public GateNode() {
    }

    public GateNode(String channel) {
        this(channel, 0.0);
    }

    public GateNode(String channel, double threshold) {
        this.channel = channel;
        this.threshold = threshold;
        this.thresholdIsZScore = true;
        this.positiveName = channel + "+";
        this.negativeName = channel + "-";
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public boolean isThresholdIsZScore() {
        return thresholdIsZScore;
    }

    public void setThresholdIsZScore(boolean thresholdIsZScore) {
        this.thresholdIsZScore = thresholdIsZScore;
    }

    public String getPositiveName() {
        return positiveName;
    }

    public void setPositiveName(String positiveName) {
        this.positiveName = positiveName;
    }

    public String getNegativeName() {
        return negativeName;
    }

    public void setNegativeName(String negativeName) {
        this.negativeName = negativeName;
    }

    public int getPositiveColor() {
        return positiveColor;
    }

    public void setPositiveColor(int positiveColor) {
        this.positiveColor = positiveColor;
    }

    public int getNegativeColor() {
        return negativeColor;
    }

    public void setNegativeColor(int negativeColor) {
        this.negativeColor = negativeColor;
    }

    public double getClipPercentileLow() {
        return clipPercentileLow;
    }

    public void setClipPercentileLow(double clipPercentileLow) {
        this.clipPercentileLow = clipPercentileLow;
    }

    public double getClipPercentileHigh() {
        return clipPercentileHigh;
    }

    public void setClipPercentileHigh(double clipPercentileHigh) {
        this.clipPercentileHigh = clipPercentileHigh;
    }

    public boolean isExcludeOutliers() {
        return excludeOutliers;
    }

    public void setExcludeOutliers(boolean excludeOutliers) {
        this.excludeOutliers = excludeOutliers;
    }

    public List<GateNode> getPositiveChildren() {
        return positiveChildren;
    }

    public void setPositiveChildren(List<GateNode> positiveChildren) {
        this.positiveChildren = positiveChildren;
    }

    public List<GateNode> getNegativeChildren() {
        return negativeChildren;
    }

    public void setNegativeChildren(List<GateNode> negativeChildren) {
        this.negativeChildren = negativeChildren;
    }

    public int getPosCount() {
        return posCount;
    }

    public void setPosCount(int posCount) {
        this.posCount = posCount;
    }

    public int getNegCount() {
        return negCount;
    }

    public void setNegCount(int negCount) {
        this.negCount = negCount;
    }

    public boolean isLeaf() {
        return positiveChildren.isEmpty() && negativeChildren.isEmpty();
    }

    public void collectLeafNames(List<String> out) {
        if (positiveChildren.isEmpty()) {
            out.add(positiveName);
        } else {
            for (GateNode child : positiveChildren) {
                child.collectLeafNames(out);
            }
        }
        if (negativeChildren.isEmpty()) {
            out.add(negativeName);
        } else {
            for (GateNode child : negativeChildren) {
                child.collectLeafNames(out);
            }
        }
    }
}
