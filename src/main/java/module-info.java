module io.github.qupath.extension.gatetree {
    requires qupath.gui.fx;
    requires qupath.lib;
    requires javafx.controls;
    requires javafx.graphics;
    requires com.google.gson;

    provides qupath.lib.gui.extensions.QuPathExtension
        with qupath.ext.gatetree.GateTreeExtension;

    exports qupath.ext.gatetree;
    exports qupath.ext.gatetree.engine;
    exports qupath.ext.gatetree.model;
    exports qupath.ext.gatetree.io;
    exports qupath.ext.gatetree.ui;
}
