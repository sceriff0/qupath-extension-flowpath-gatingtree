package qupath.ext.flowpath.model;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MarkerStats {

    private static final int HISTOGRAM_BINS = 200;

    // Keyed by the *resolved* measurement key: a base marker name ("CD3") for
    // whole-cell/mean, or a compartment key ("CD3: Nucleus: Mean") otherwise.
    // ConcurrentHashMap so columns can be registered lazily from the background
    // live-preview thread (see ensureColumn).
    private final Map<String, double[]> sortedValues = new ConcurrentHashMap<>();
    private final Map<String, Double> means = new ConcurrentHashMap<>();
    private final Map<String, Double> stds = new ConcurrentHashMap<>();
    private final Map<String, Double> mins = new ConcurrentHashMap<>();
    private final Map<String, Double> maxs = new ConcurrentHashMap<>();
    private final Map<String, double[]> histogramBins = new ConcurrentHashMap<>();
    private final Map<String, double[]> histogramCounts = new ConcurrentHashMap<>();

    // Quality mask captured at compute time, so lazily-registered compartment
    // columns are summarised over the same cell population as the base markers.
    private boolean[] qualityMask;

    private MarkerStats() {}

    public static MarkerStats compute(CellIndex index, boolean[] qualityMask) {
        MarkerStats s = new MarkerStats();
        s.qualityMask = qualityMask;

        String[] markers = index.getMarkerNames();
        for (int m = 0; m < markers.length; m++) {
            s.putColumnStats(markers[m], index.getMarkerValues(m), qualityMask);
        }
        return s;
    }

    /**
     * Compute and store summary statistics (sorted values, mean, std, min/max,
     * histogram) for a single column under {@code name}.
     */
    private void putColumnStats(String name, double[] raw, boolean[] mask) {
        int n = raw.length;

        int actualCount = 0;
        for (int i = 0; i < n; i++) {
            if ((mask == null || mask[i]) && !Double.isNaN(raw[i])) actualCount++;
        }
        double[] passing = new double[actualCount];
        int idx = 0;
        for (int i = 0; i < n; i++) {
            if ((mask == null || mask[i]) && !Double.isNaN(raw[i])) {
                passing[idx++] = raw[i];
            }
        }

        Arrays.sort(passing);
        sortedValues.put(name, passing);

        if (actualCount == 0) {
            means.put(name, 0.0);
            stds.put(name, 0.0);
            mins.put(name, 0.0);
            maxs.put(name, 0.0);
            histogramBins.put(name, new double[HISTOGRAM_BINS + 1]);
            histogramCounts.put(name, new double[HISTOGRAM_BINS]);
            return;
        }

        double min = passing[0];
        double max = passing[actualCount - 1];
        mins.put(name, min);
        maxs.put(name, max);

        double sum = 0;
        for (double v : passing) sum += v;
        double mean = sum / actualCount;
        means.put(name, mean);

        double sumSq = 0;
        for (double v : passing) sumSq += (v - mean) * (v - mean);
        double std = Math.sqrt(sumSq / actualCount);
        stds.put(name, std);

        // Histogram
        double[] bins = new double[HISTOGRAM_BINS + 1];
        double[] counts = new double[HISTOGRAM_BINS];
        double range = max - min;
        if (range < 1e-10) range = 1.0;
        double binWidth = range / HISTOGRAM_BINS;

        for (int b = 0; b <= HISTOGRAM_BINS; b++) {
            bins[b] = min + b * binWidth;
        }
        for (double v : passing) {
            int bin = (int) ((v - min) / binWidth);
            if (bin >= HISTOGRAM_BINS) bin = HISTOGRAM_BINS - 1;
            if (bin < 0) bin = 0;
            counts[bin]++;
        }

        histogramBins.put(name, bins);
        histogramCounts.put(name, counts);
    }

    /**
     * Ensure statistics exist for a resolved compartment column, computed with the
     * same quality mask used at {@link #compute}. Idempotent and safe to call from
     * the gating pre-pass on any thread. No-op for keys already present (including
     * the base markers).
     */
    public void ensureColumn(String key, double[] rawColumn) {
        if (key == null || rawColumn == null) return;
        if (means.containsKey(key)) return;
        putColumnStats(key, rawColumn, qualityMask);
    }

    /** True if statistics for the given resolved key are available. */
    public boolean hasColumn(String key) {
        return means.containsKey(key);
    }

    public double toZScore(String channel, double rawValue) {
        double mean = means.getOrDefault(channel, 0.0);
        double std = stds.getOrDefault(channel, 0.0);
        if (std < 1e-10) return 0.0;
        return (rawValue - mean) / std;
    }

    public double fromZScore(String channel, double zScore) {
        double mean = means.getOrDefault(channel, 0.0);
        double std = stds.getOrDefault(channel, 0.0);
        return zScore * std + mean;
    }

    public double getPercentileValue(String channel, double percentile) {
        double[] sorted = sortedValues.get(channel);
        if (sorted == null || sorted.length == 0) return Double.NaN;
        double idx = (percentile / 100.0) * (sorted.length - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo < 0) lo = 0;
        if (hi >= sorted.length) hi = sorted.length - 1;
        double frac = idx - lo;
        return sorted[lo] + frac * (sorted[hi] - sorted[lo]);
    }

    public double[] getHistogramBins(String channel) {
        return histogramBins.get(channel);
    }

    public double[] getHistogramCounts(String channel) {
        return histogramCounts.get(channel);
    }

    public double getMean(String channel) {
        return means.getOrDefault(channel, 0.0);
    }

    public double getStd(String channel) {
        return stds.getOrDefault(channel, 0.0);
    }

    public double getMin(String channel) {
        return mins.getOrDefault(channel, 0.0);
    }

    public double getMax(String channel) {
        return maxs.getOrDefault(channel, 0.0);
    }

    public Set<String> getMarkerNames() {
        return means.keySet();
    }
}
