package qupath.ext.gatetree.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import qupath.ext.gatetree.model.GateNode;

/**
 * Custom TreeCell for rendering gate nodes in the tree view.
 * Shows a color indicator, gate name, channel, and cell count.
 */
public class GateTreeCell extends TreeCell<Object> {

    /**
     * Wrapper for a branch (positive or negative side of a gate).
     */
    public static class BranchItem {
        public final GateNode parentGate;
        public final boolean isPositive;

        public BranchItem(GateNode parentGate, boolean isPositive) {
            this.parentGate = parentGate;
            this.isPositive = isPositive;
        }
    }

    @Override
    protected void updateItem(Object item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        if (item instanceof GateNode node) {
            // Gate node: show channel name + threshold
            HBox box = new HBox(6);
            box.setPadding(new Insets(2));

            Label gateLabel = new Label(String.format("⊕ %s (%.3f)", node.getChannel(), node.getThreshold()));
            gateLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #dddddd;");

            box.getChildren().add(gateLabel);
            setGraphic(box);
            setText(null);

        } else if (item instanceof BranchItem branch) {
            GateNode gate = branch.parentGate;
            boolean isPos = branch.isPositive;

            HBox box = new HBox(6);
            box.setPadding(new Insets(1, 1, 1, 4));

            int colorInt = isPos ? gate.getPositiveColor() : gate.getNegativeColor();
            Color color = GateEditorPane.intToColor(colorInt);
            Circle dot = new Circle(5, color);

            String name = isPos ? gate.getPositiveName() : gate.getNegativeName();
            int count = isPos ? gate.getPosCount() : gate.getNegCount();

            Label nameLabel = new Label(name);
            nameLabel.setStyle("-fx-text-fill: " + toHex(color) + ";");

            Label countLabel = new Label(String.format("(%,d)", count));
            countLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 10;");

            // Check if this is a leaf (no children on this branch)
            boolean isLeaf = isPos ? gate.getPositiveChildren().isEmpty() : gate.getNegativeChildren().isEmpty();
            if (isLeaf) {
                nameLabel.setStyle(nameLabel.getStyle() + " -fx-font-weight: bold;");
            }

            box.getChildren().addAll(dot, nameLabel, countLabel);
            setGraphic(box);
            setText(null);

        } else {
            setText(item.toString());
            setGraphic(null);
        }
    }

    private String toHex(Color c) {
        return String.format("#%02x%02x%02x",
            (int) (c.getRed() * 255),
            (int) (c.getGreen() * 255),
            (int) (c.getBlue() * 255));
    }
}
