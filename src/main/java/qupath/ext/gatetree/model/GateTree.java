package qupath.ext.gatetree.model;

import java.util.ArrayList;
import java.util.List;

public class GateTree {

    private List<GateNode> roots = new ArrayList<>();
    private QualityFilter qualityFilter = new QualityFilter();

    public GateTree() {
    }

    public void addRoot(GateNode node) {
        roots.add(node);
    }

    public void removeRoot(GateNode node) {
        roots.remove(node);
    }

    public List<GateNode> getRoots() {
        return roots;
    }

    public void setRoots(List<GateNode> roots) {
        this.roots = roots;
    }

    public QualityFilter getQualityFilter() {
        return qualityFilter;
    }

    public void setQualityFilter(QualityFilter qualityFilter) {
        this.qualityFilter = qualityFilter;
    }

    public List<String> collectLeafNames() {
        List<String> names = new ArrayList<>();
        for (GateNode root : roots) {
            root.collectLeafNames(names);
        }
        return names;
    }
}
