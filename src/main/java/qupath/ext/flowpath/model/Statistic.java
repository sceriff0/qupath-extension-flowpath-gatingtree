package qupath.ext.flowpath.model;

/**
 * A per-compartment summary statistic stored by the MIRAGE pipeline.
 * <p>
 * Maps to the statistic token in the measurement key
 * {@code "<marker>: <Compartment>: <Stat>"}. {@link #MEAN} is always present;
 * {@link #MEDIAN} and {@link #SUM} are only emitted with
 * {@code --expanded_quantification}. This is orthogonal to FlowPath's live
 * z-score toggle, which is applied on top of whichever statistic is selected.
 */
public enum Statistic {

    MEAN("Mean"),
    MEDIAN("Median"),
    SUM("Sum");

    private final String token;

    Statistic(String token) {
        this.token = token;
    }

    /** Token used inside the measurement key, e.g. {@code "Mean"}. */
    public String token() {
        return token;
    }

    /** Human-readable name for UI (same as the token). */
    public String displayName() {
        return token;
    }

    /** Default statistic, always available. */
    public static Statistic defaultStatistic() {
        return MEAN;
    }

    /** Parse a statistic token (case-insensitive); returns null if unrecognised. */
    public static Statistic fromToken(String token) {
        if (token == null) return null;
        for (Statistic s : values()) {
            if (s.token.equalsIgnoreCase(token)) return s;
        }
        return null;
    }
}
