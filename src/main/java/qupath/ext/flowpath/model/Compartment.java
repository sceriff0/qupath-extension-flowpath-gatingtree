package qupath.ext.flowpath.model;

/**
 * A subcellular compartment a marker measurement can refer to.
 * <p>
 * Maps to the compartment token used in the QuPath-native measurement key
 * {@code "<marker>: <Compartment>: <Stat>"} produced by the MIRAGE pipeline
 * (e.g. {@code "CD3: Nucleus: Mean"}). {@link #WHOLE_CELL} is the default and is
 * what legacy GeoJSONs (bare {@code "CD3"} keys) resolve to.
 */
public enum Compartment {

    NUCLEAR("Nucleus", "Nuclear", "N"),
    CYTOPLASMIC("Cytoplasm", "Cytoplasmic", "C"),
    WHOLE_CELL("Cell", "Whole-cell", "W");

    private final String token;
    private final String displayName;
    private final String abbreviation;

    Compartment(String token, String displayName, String abbreviation) {
        this.token = token;
        this.displayName = displayName;
        this.abbreviation = abbreviation;
    }

    /** Token used inside the measurement key, e.g. {@code "Nucleus"}. */
    public String token() {
        return token;
    }

    /** Human-readable name for UI, e.g. {@code "Nuclear"}. */
    public String displayName() {
        return displayName;
    }

    /** One-letter badge code for the gating tree (N / C / W). */
    public String abbreviation() {
        return abbreviation;
    }

    /** Default compartment used when none is selected or the GeoJSON is legacy. */
    public static Compartment defaultCompartment() {
        return WHOLE_CELL;
    }

    /** Parse a compartment token (case-insensitive); returns null if unrecognised. */
    public static Compartment fromToken(String token) {
        if (token == null) return null;
        for (Compartment c : values()) {
            if (c.token.equalsIgnoreCase(token)) return c;
        }
        return null;
    }
}
