package qupath.ext.flowpath.model;

/**
 * Builds and parses the QuPath-native per-compartment measurement keys
 * {@code "<marker>: <Compartment>: <Stat>"} emitted by the MIRAGE pipeline.
 * <p>
 * Parsing tolerates an optional layer prefix (e.g. {@code "[Layer0] CD3: Nucleus: Mean"});
 * the layer prefix is stripped so the returned marker matches the channel names
 * discovered from image metadata.
 */
public final class MeasurementKeys {

    private MeasurementKeys() {}

    /** The separator between marker, compartment and statistic tokens. */
    private static final String SEP = ": ";

    /** Parsed components of a per-compartment measurement key. */
    public record Parsed(String marker, Compartment compartment, Statistic statistic) {}

    /** Build a measurement key, e.g. {@code build("CD3", NUCLEAR, MEAN) -> "CD3: Nucleus: Mean"}. */
    public static String build(String marker, Compartment compartment, Statistic statistic) {
        return marker + SEP + compartment.token() + SEP + statistic.token();
    }

    /**
     * Parse a measurement key of the form {@code "<marker>: <Compartment>: <Stat>"}.
     *
     * @return the parsed components, or {@code null} if the key is not a recognised
     *         per-compartment key (e.g. a bare marker name or a morphology field).
     */
    public static Parsed parse(String key) {
        if (key == null) return null;
        // Match the last two ": <token>" segments against known compartment/statistic tokens.
        for (Compartment c : Compartment.values()) {
            for (Statistic s : Statistic.values()) {
                String suffix = SEP + c.token() + SEP + s.token();
                if (key.endsWith(suffix)) {
                    String marker = key.substring(0, key.length() - suffix.length());
                    marker = stripLayerPrefix(marker).trim();
                    if (marker.isEmpty()) return null;
                    return new Parsed(marker, c, s);
                }
            }
        }
        return null;
    }

    /** Remove a leading {@code "[...] "} layer prefix if present. */
    public static String stripLayerPrefix(String name) {
        if (name != null && name.startsWith("[")) {
            int idx = name.indexOf("] ");
            if (idx >= 0) return name.substring(idx + 2);
        }
        return name;
    }
}
