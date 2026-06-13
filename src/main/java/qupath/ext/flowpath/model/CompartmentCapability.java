package qupath.ext.flowpath.model;

import qupath.lib.objects.PathObject;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Detects which per-compartment measurements a loaded set of cells actually
 * carries, by scanning measurement keys for the
 * {@code "<marker>: <Compartment>: <Stat>"} pattern.
 * <p>
 * Drives the UI: when {@link #isRich()} is false the GeoJSON is "legacy" and the
 * compartment/statistic selectors are disabled and pinned to whole-cell / mean.
 * Per-marker queries let the editor offer only the compartments/statistics that
 * exist for a given channel.
 */
public final class CompartmentCapability {

    private final Map<String, EnumSet<Compartment>> compartments = new HashMap<>();
    private final Map<String, EnumSet<Statistic>> statistics = new HashMap<>();
    private boolean rich = false;

    private CompartmentCapability() {}

    /** An empty capability (legacy: nothing rich). */
    public static CompartmentCapability empty() {
        return new CompartmentCapability();
    }

    /** Build from a collection of measurement keys. */
    public static CompartmentCapability fromKeys(Collection<String> keys) {
        CompartmentCapability cap = new CompartmentCapability();
        for (String key : keys) {
            MeasurementKeys.Parsed parsed = MeasurementKeys.parse(key);
            if (parsed == null) continue;
            cap.rich = true;
            cap.compartments
                    .computeIfAbsent(parsed.marker(), k -> EnumSet.noneOf(Compartment.class))
                    .add(parsed.compartment());
            cap.statistics
                    .computeIfAbsent(parsed.marker(), k -> EnumSet.noneOf(Statistic.class))
                    .add(parsed.statistic());
        }
        return cap;
    }

    /** Scan up to {@code sampleLimit} detections' measurement keys. */
    public static CompartmentCapability scan(Collection<PathObject> detections, int sampleLimit) {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        int sampled = 0;
        for (PathObject obj : detections) {
            try {
                var m = obj.getMeasurements();
                if (m != null) keys.addAll(m.keySet());
            } catch (Exception ignored) {
            }
            if (++sampled >= sampleLimit) break;
        }
        return fromKeys(keys);
    }

    /** True if any per-compartment measurement key was found (rich GeoJSON). */
    public boolean isRich() {
        return rich;
    }

    /** True if the given marker has per-compartment keys. */
    public boolean hasCompartments(String marker) {
        Set<Compartment> set = compartments.get(MeasurementKeys.stripLayerPrefix(marker));
        return set != null && !set.isEmpty();
    }

    /** Available compartments for a marker (empty if none / legacy). */
    public Set<Compartment> compartmentsFor(String marker) {
        EnumSet<Compartment> set = compartments.get(MeasurementKeys.stripLayerPrefix(marker));
        return set == null ? EnumSet.noneOf(Compartment.class) : EnumSet.copyOf(set);
    }

    /** Available statistics for a marker (empty if none / legacy). */
    public Set<Statistic> statisticsFor(String marker) {
        EnumSet<Statistic> set = statistics.get(MeasurementKeys.stripLayerPrefix(marker));
        return set == null ? EnumSet.noneOf(Statistic.class) : EnumSet.copyOf(set);
    }
}
