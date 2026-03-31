package qupath.ext.flowpath.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import qupath.ext.flowpath.model.Branch;
import qupath.ext.flowpath.model.ColorUtils;
import qupath.ext.flowpath.model.GateNode;
import qupath.ext.flowpath.model.GateTree;
import qupath.ext.flowpath.model.EllipseGate;
import qupath.ext.flowpath.model.PolygonGate;
import qupath.ext.flowpath.model.QualityFilter;
import qupath.ext.flowpath.model.QuadrantGate;
import qupath.ext.flowpath.model.RectangleGate;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializes and deserializes {@link GateTree} instances to/from JSON files.
 */
public class FlowPathSerializer {

    private static final int CURRENT_VERSION = 1;

    private FlowPathSerializer() {
        // static utility class
    }

    /**
     * Save a gate tree to a JSON file.
     *
     * @param tree the gate tree to save
     * @param file the destination file
     * @throws IOException if writing fails
     */
    public static void save(GateTree tree, File file) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", CURRENT_VERSION);
        root.add("qualityFilter", serializeQualityFilter(tree.getQualityFilter()));
        root.addProperty("roiFilterEnabled", tree.isRoiFilterEnabled());
        root.add("gates", serializeNodeList(tree.getRoots()));

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(gson.toJson(root));
        }
    }

    /**
     * Load a gate tree from a JSON file.
     *
     * @param file the source file
     * @return the deserialized gate tree
     * @throws IOException if reading fails or the format is invalid
     */
    public static GateTree load(File file) throws IOException {
        JsonObject root;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }

        // Version check (currently only version 1 is supported)
        int version = root.has("version") ? root.get("version").getAsInt() : 1;
        if (version > CURRENT_VERSION) {
            throw new IOException("Unsupported gate tree version: " + version
                    + " (maximum supported: " + CURRENT_VERSION + ")");
        }

        GateTree tree = new GateTree();

        if (root.has("qualityFilter")) {
            tree.setQualityFilter(deserializeQualityFilter(root.getAsJsonObject("qualityFilter")));
        }

        if (root.has("roiFilterEnabled")) {
            tree.setRoiFilterEnabled(root.get("roiFilterEnabled").getAsBoolean());
        }

        if (root.has("gates")) {
            tree.setRoots(deserializeNodeList(root.getAsJsonArray("gates")));
        }

        return tree;
    }

    // -----------------------------------------------------------------------
    //  Quality filter
    // -----------------------------------------------------------------------

    private static JsonObject serializeQualityFilter(QualityFilter qf) {
        JsonObject obj = new JsonObject();
        obj.addProperty("minArea", qf.getMinArea());
        obj.addProperty("maxArea", qf.getMaxArea());
        obj.addProperty("minEccentricity", qf.getMinEccentricity());
        obj.addProperty("maxEccentricity", qf.getMaxEccentricity());
        obj.addProperty("minSolidity", qf.getMinSolidity());
        obj.addProperty("maxSolidity", qf.getMaxSolidity());
        obj.addProperty("minTotalIntensity", qf.getMinTotalIntensity());
        obj.addProperty("maxTotalIntensity", qf.getMaxTotalIntensity());
        obj.addProperty("minPerimeter", qf.getMinPerimeter());
        obj.addProperty("maxPerimeter", qf.getMaxPerimeter());
        return obj;
    }

    private static QualityFilter deserializeQualityFilter(JsonObject obj) {
        QualityFilter qf = new QualityFilter();
        if (obj.has("minArea"))
            qf.setMinArea(obj.get("minArea").getAsDouble());
        if (obj.has("maxArea"))
            qf.setMaxArea(obj.get("maxArea").getAsDouble());
        if (obj.has("minEccentricity"))
            qf.setMinEccentricity(obj.get("minEccentricity").getAsDouble());
        if (obj.has("maxEccentricity"))
            qf.setMaxEccentricity(obj.get("maxEccentricity").getAsDouble());
        if (obj.has("minSolidity"))
            qf.setMinSolidity(obj.get("minSolidity").getAsDouble());
        if (obj.has("maxSolidity"))
            qf.setMaxSolidity(obj.get("maxSolidity").getAsDouble());
        if (obj.has("minTotalIntensity"))
            qf.setMinTotalIntensity(obj.get("minTotalIntensity").getAsDouble());
        if (obj.has("maxTotalIntensity"))
            qf.setMaxTotalIntensity(obj.get("maxTotalIntensity").getAsDouble());
        if (obj.has("minPerimeter"))
            qf.setMinPerimeter(obj.get("minPerimeter").getAsDouble());
        if (obj.has("maxPerimeter"))
            qf.setMaxPerimeter(obj.get("maxPerimeter").getAsDouble());
        // "hideFiltered" silently ignored for backward compat with v1 files
        return qf;
    }

    // -----------------------------------------------------------------------
    //  Gate nodes
    // -----------------------------------------------------------------------

    private static void serializeTwoBranches(JsonObject obj, List<Branch> branches) {
        JsonArray arr = new JsonArray();
        for (Branch b : branches) {
            JsonObject bo = new JsonObject();
            bo.addProperty("name", b.getName());
            bo.add("color", ColorUtils.toJsonArray(b.getColor()));
            bo.add("children", serializeNodeList(b.getChildren()));
            arr.add(bo);
        }
        obj.add("branches", arr);
    }

    private static JsonArray serializeNodeList(List<GateNode> nodes) {
        JsonArray array = new JsonArray();
        for (GateNode node : nodes) {
            array.add(serializeNode(node));
        }
        return array;
    }

    private static JsonObject serializeNode(GateNode node) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", node.getGateType());
        if (!node.isEnabled()) {
            obj.addProperty("enabled", false);
        }
        obj.addProperty("clipPercentileLow", node.getClipPercentileLow());
        obj.addProperty("clipPercentileHigh", node.getClipPercentileHigh());
        obj.addProperty("excludeOutliers", node.isExcludeOutliers());

        if (node instanceof PolygonGate pg) {
            obj.addProperty("channelX", pg.getChannelX());
            obj.addProperty("channelY", pg.getChannelY());
            obj.addProperty("thresholdIsZScore", pg.isThresholdIsZScore());
            JsonArray verts = new JsonArray();
            for (double[] v : pg.getVertices()) {
                JsonArray pt = new JsonArray();
                pt.add(v[0]); pt.add(v[1]);
                verts.add(pt);
            }
            obj.add("vertices", verts);
            serializeTwoBranches(obj, pg.getBranches());
        } else if (node instanceof RectangleGate rg) {
            obj.addProperty("channelX", rg.getChannelX());
            obj.addProperty("channelY", rg.getChannelY());
            obj.addProperty("thresholdIsZScore", rg.isThresholdIsZScore());
            obj.addProperty("minX", rg.getMinX());
            obj.addProperty("maxX", rg.getMaxX());
            obj.addProperty("minY", rg.getMinY());
            obj.addProperty("maxY", rg.getMaxY());
            serializeTwoBranches(obj, rg.getBranches());
        } else if (node instanceof EllipseGate eg) {
            obj.addProperty("channelX", eg.getChannelX());
            obj.addProperty("channelY", eg.getChannelY());
            obj.addProperty("thresholdIsZScore", eg.isThresholdIsZScore());
            obj.addProperty("centerX", eg.getCenterX());
            obj.addProperty("centerY", eg.getCenterY());
            obj.addProperty("radiusX", eg.getRadiusX());
            obj.addProperty("radiusY", eg.getRadiusY());
            serializeTwoBranches(obj, eg.getBranches());
        } else if (node instanceof QuadrantGate qg) {
            obj.addProperty("channelX", qg.getChannelX());
            obj.addProperty("channelY", qg.getChannelY());
            obj.addProperty("thresholdX", qg.getThresholdX());
            obj.addProperty("thresholdY", qg.getThresholdY());
            obj.addProperty("thresholdIsZScore", qg.isThresholdIsZScore());
            // Serialize 4 branches
            JsonArray branches = new JsonArray();
            for (Branch b : qg.getBranches()) {
                JsonObject bo = new JsonObject();
                bo.addProperty("name", b.getName());
                bo.add("color", ColorUtils.toJsonArray(b.getColor()));
                bo.add("children", serializeNodeList(b.getChildren()));
                branches.add(bo);
            }
            obj.add("branches", branches);
        } else {
            // Threshold gate (default) — backward-compatible format
            obj.addProperty("channel", node.getChannel());
            obj.addProperty("threshold", node.getThreshold());
            obj.addProperty("thresholdIsZScore", node.isThresholdIsZScore());
            obj.addProperty("positiveName", node.getPositiveName());
            obj.addProperty("negativeName", node.getNegativeName());
            obj.add("positiveColor", ColorUtils.toJsonArray(node.getPositiveColor()));
            obj.add("negativeColor", ColorUtils.toJsonArray(node.getNegativeColor()));
            obj.add("positiveChildren", serializeNodeList(node.getPositiveChildren()));
            obj.add("negativeChildren", serializeNodeList(node.getNegativeChildren()));
        }

        return obj;
    }

    private static List<GateNode> deserializeNodeList(JsonArray array) throws IOException {
        List<GateNode> nodes = new ArrayList<>();
        for (JsonElement elem : array) {
            nodes.add(deserializeNode(elem.getAsJsonObject()));
        }
        return nodes;
    }

    private static GateNode deserializeNode(JsonObject obj) throws IOException {
        String type = obj.has("type") ? obj.get("type").getAsString() : "threshold";

        // Shared fields
        boolean enabled = !obj.has("enabled") || obj.get("enabled").getAsBoolean();
        double clipLow = obj.has("clipPercentileLow") ? obj.get("clipPercentileLow").getAsDouble() : 1.0;
        double clipHigh = obj.has("clipPercentileHigh") ? obj.get("clipPercentileHigh").getAsDouble() : 99.0;
        boolean excludeOutliers = false;
        if (obj.has("excludeOutliers"))
            excludeOutliers = obj.get("excludeOutliers").getAsBoolean();
        else if (obj.has("hideOutliers"))
            excludeOutliers = obj.get("hideOutliers").getAsBoolean();

        GateNode result;
        if ("quadrant".equals(type)) {
            result = deserializeQuadrantNode(obj, clipLow, clipHigh, excludeOutliers);
        } else if ("polygon".equals(type)) {
            result = deserialize2DNode(new PolygonGate(), obj, clipLow, clipHigh, excludeOutliers);
        } else if ("rectangle".equals(type)) {
            result = deserialize2DNode(new RectangleGate(), obj, clipLow, clipHigh, excludeOutliers);
        } else if ("ellipse".equals(type)) {
            result = deserialize2DNode(new EllipseGate(), obj, clipLow, clipHigh, excludeOutliers);
        } else if ("threshold".equals(type)) {
            result = deserializeThresholdNode(obj, clipLow, clipHigh, excludeOutliers);
        } else {
            throw new IOException("Unknown gate type: \"" + type + "\". "
                    + "This file may have been created by a newer version of FlowPath.");
        }
        result.setEnabled(enabled);
        return result;
    }

    private static GateNode deserializeThresholdNode(JsonObject obj,
                                                      double clipLow, double clipHigh, boolean excludeOutliers) throws IOException {
        GateNode node = new GateNode();
        node.setClipPercentileLow(clipLow);
        node.setClipPercentileHigh(clipHigh);
        node.setExcludeOutliers(excludeOutliers);

        if (obj.has("channel"))
            node.setChannel(obj.get("channel").getAsString());
        if (obj.has("threshold"))
            node.setThreshold(obj.get("threshold").getAsDouble());
        if (obj.has("thresholdIsZScore"))
            node.setThresholdIsZScore(obj.get("thresholdIsZScore").getAsBoolean());
        if (obj.has("positiveName"))
            node.setPositiveName(obj.get("positiveName").getAsString());
        if (obj.has("negativeName"))
            node.setNegativeName(obj.get("negativeName").getAsString());
        if (obj.has("positiveColor"))
            node.setPositiveColor(ColorUtils.fromJsonArray(obj.getAsJsonArray("positiveColor")));
        if (obj.has("negativeColor"))
            node.setNegativeColor(ColorUtils.fromJsonArray(obj.getAsJsonArray("negativeColor")));
        if (obj.has("positiveChildren"))
            node.setPositiveChildren(deserializeNodeList(obj.getAsJsonArray("positiveChildren")));
        if (obj.has("negativeChildren"))
            node.setNegativeChildren(deserializeNodeList(obj.getAsJsonArray("negativeChildren")));

        return node;
    }

    private static QuadrantGate deserializeQuadrantNode(JsonObject obj,
                                                         double clipLow, double clipHigh, boolean excludeOutliers) throws IOException {
        QuadrantGate gate = new QuadrantGate();
        gate.setClipPercentileLow(clipLow);
        gate.setClipPercentileHigh(clipHigh);
        gate.setExcludeOutliers(excludeOutliers);

        if (obj.has("channelX"))
            gate.setChannelX(obj.get("channelX").getAsString());
        if (obj.has("channelY"))
            gate.setChannelY(obj.get("channelY").getAsString());
        if (obj.has("thresholdX"))
            gate.setThresholdX(obj.get("thresholdX").getAsDouble());
        if (obj.has("thresholdY"))
            gate.setThresholdY(obj.get("thresholdY").getAsDouble());
        if (obj.has("thresholdIsZScore"))
            gate.setThresholdIsZScore(obj.get("thresholdIsZScore").getAsBoolean());

        if (obj.has("branches")) {
            JsonArray branches = obj.getAsJsonArray("branches");
            List<Branch> gateBranches = gate.getBranches();
            for (int i = 0; i < branches.size() && i < gateBranches.size(); i++) {
                JsonObject bo = branches.get(i).getAsJsonObject();
                Branch b = gateBranches.get(i);
                if (bo.has("name")) b.setName(bo.get("name").getAsString());
                if (bo.has("color")) b.setColor(ColorUtils.fromJsonArray(bo.getAsJsonArray("color")));
                if (bo.has("children")) b.setChildren(deserializeNodeList(bo.getAsJsonArray("children")));
            }
        }

        return gate;
    }

    private static GateNode deserialize2DNode(GateNode gate, JsonObject obj,
                                                double clipLow, double clipHigh, boolean excludeOutliers) throws IOException {
        gate.setClipPercentileLow(clipLow);
        gate.setClipPercentileHigh(clipHigh);
        gate.setExcludeOutliers(excludeOutliers);

        String chX = obj.has("channelX") ? obj.get("channelX").getAsString() : null;
        String chY = obj.has("channelY") ? obj.get("channelY").getAsString() : null;

        // Shared: z-score flag for all 2D gate types
        if (obj.has("thresholdIsZScore"))
            gate.setThresholdIsZScore(obj.get("thresholdIsZScore").getAsBoolean());

        if (gate instanceof PolygonGate pg) {
            pg.setChannelX(chX); pg.setChannelY(chY);
            if (obj.has("vertices")) {
                List<double[]> verts = new ArrayList<>();
                for (JsonElement elem : obj.getAsJsonArray("vertices")) {
                    JsonArray pt = elem.getAsJsonArray();
                    verts.add(new double[]{pt.get(0).getAsDouble(), pt.get(1).getAsDouble()});
                }
                pg.setVertices(verts);
            }
        } else if (gate instanceof RectangleGate rg) {
            rg.setChannelX(chX); rg.setChannelY(chY);
            if (obj.has("minX")) rg.setMinX(obj.get("minX").getAsDouble());
            if (obj.has("maxX")) rg.setMaxX(obj.get("maxX").getAsDouble());
            if (obj.has("minY")) rg.setMinY(obj.get("minY").getAsDouble());
            if (obj.has("maxY")) rg.setMaxY(obj.get("maxY").getAsDouble());
        } else if (gate instanceof EllipseGate eg) {
            eg.setChannelX(chX); eg.setChannelY(chY);
            if (obj.has("centerX")) eg.setCenterX(obj.get("centerX").getAsDouble());
            if (obj.has("centerY")) eg.setCenterY(obj.get("centerY").getAsDouble());
            if (obj.has("radiusX")) eg.setRadiusX(obj.get("radiusX").getAsDouble());
            if (obj.has("radiusY")) eg.setRadiusY(obj.get("radiusY").getAsDouble());
        }

        // Deserialize branches
        if (obj.has("branches")) {
            JsonArray branches = obj.getAsJsonArray("branches");
            List<Branch> gateBranches = gate.getBranches();
            for (int i = 0; i < branches.size() && i < gateBranches.size(); i++) {
                JsonObject bo = branches.get(i).getAsJsonObject();
                Branch b = gateBranches.get(i);
                if (bo.has("name")) b.setName(bo.get("name").getAsString());
                if (bo.has("color")) b.setColor(ColorUtils.fromJsonArray(bo.getAsJsonArray("color")));
                if (bo.has("children")) b.setChildren(deserializeNodeList(bo.getAsJsonArray("children")));
            }
        }

        return gate;
    }

}
