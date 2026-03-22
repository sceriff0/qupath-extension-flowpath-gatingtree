# QuPath Extension: Gate Tree

[![Nextflow](https://img.shields.io/badge/QuPath-%E2%89%A50.7.0-blue.svg)](https://qupath.github.io/)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://jdk.java.net/25/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

Interactive tree-based cell phenotyping gating extension for [QuPath](https://qupath.github.io/) 0.7.0+. Build hierarchical marker gates with live histogram visualization, drag thresholds, and see cells update in real time.

Designed for multiplexed imaging data (e.g., CODEX, MIBI, mIF) where cell detections have been imported with per-cell marker intensity measurements.

---

## Features

- **Hierarchical Gate Tree** — Build multi-level gating strategies (e.g., CD45+ > CD3+ > CD8+ = "T cytotoxic") with a visual tree UI
- **Live Histogram** — Canvas-rendered histogram per marker with a draggable red threshold line; percentile clipping to handle outliers
- **Real-Time Preview** — Cell contours/circles update color and phenotype within ~100ms of threshold changes (debounced, background-threaded)
- **Raw / Z-score Toggle** — Switch between raw intensity values and z-score normalized values per gate
- **Quality Filters** — Pre-gating QC panel with area (min/max), eccentricity, solidity, and total intensity filters
- **Outlier Exclusion** — Per-gate percentile clipping with optional cell exclusion
- **Save/Load** — Persist gate tree configurations as JSON for reproducibility
- **CSV Export** — Export `gate_pheno.csv` with cell phenotypes and per-marker +/- status columns
- **Floating Window** — Separate movable/resizable window that never obscures the image viewer

---

## Requirements

| Requirement | Version |
|-------------|---------|
| QuPath      | 0.7.0+  |
| Java        | 25+     |
| Gradle      | 9.4+ (included via wrapper) |

---

## Installation

### Option 1: Pre-built JAR (Recommended)

1. Download the latest JAR from the [Releases](../../releases) page
2. Drag the JAR file onto the QuPath window
3. Restart QuPath

### Option 2: Build from Source

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/qupath-extension-gatetree.git
cd qupath-extension-gatetree

# Build (requires Java 25+)
./gradlew build

# The JAR will be at build/libs/qupath-extension-gatetree-0.1.0-SNAPSHOT.jar
# Drag it onto QuPath to install
```

### Cluster Installation (no GUI)

If building on a headless cluster:

```bash
# Ensure Java 25+ is available
module load java/25  # or equivalent for your cluster

# Build
./gradlew build

# Copy JAR to QuPath extensions directory
cp build/libs/qupath-extension-gatetree-*.jar /path/to/QuPath/extensions/
```

---

## Quick Start

1. **Open your image** — Load your pyramidal OME-TIFF in QuPath
2. **Import cell detections** — Import your GeoJSON or use QuPath's cell detection. Cells must have marker intensity measurements (e.g., CD45, CD3, CD8)
3. **Open Gate Tree** — `Extensions > Gate Tree` or press `Ctrl+G`
4. **Set quality filters** — Adjust area, eccentricity, and solidity sliders to remove segmentation artifacts
5. **Add a root gate** — Click "+ Add Root Gate", select a channel (e.g., CD45), drag the threshold on the histogram
6. **Build hierarchy** — Select a gate, click "Add Gate to +" to gate the positive population further
7. **Name your phenotypes** — Edit the positive/negative names on leaf nodes (e.g., "T cytotoxic", "Stroma")
8. **Export** — Click "Export CSV" to save `gate_pheno.csv`

---

## User Interface

```
+------------------------------------------------------------+
|  Gate Tree - Cell Phenotyping                        [_][X] |
+------------------------------------------------------------+
|  +------------------+  +--------------------------------+  |
|  |  TreeView        |  |  Gate Editor                   |  |
|  |  v CD45 gate     |  |  Channel: [CD45 v]             |  |
|  |    + CD45+       |  |  Mode: (o) Raw  ( ) Z-score    |  |
|  |      v CD3 gate  |  |  +----------------------------+|  |
|  |        + CD3+    |  |  | Histogram        |  red bar ||  |
|  |        - CD3-    |  |  +----------------------------+|  |
|  |    - CD45-       |  |  Threshold: [=====O==========] |  |
|  |      v PANCK     |  |  Clip: [1.0]% to [99.0]%      |  |
|  |        + Tumor   |  |  [x] Exclude outliers          |  |
|  |        - Stroma  |  |  Positive: [CD45+  ] [color]   |  |
|  +------------------+  |  Negative: [CD45-  ] [color]   |  |
|  | Quality Filter   |  |  [Add to +] [Add to -] [Remove]|  |
|  | Area: [=O=]-[=O] |  +--------------------------------+  |
|  | Eccent max: [=O] |                                       |
|  | Solidity: [O===] |                                       |
|  | [x] Hide filtered|                                       |
|  | Filtered: 1,234  |                                       |
|  +------------------+                                       |
|  [Save JSON] [Load JSON]              [Export CSV]          |
+------------------------------------------------------------+
```

---

## File Formats

### Gate Tree JSON (`gate_tree.json`)

The native save format for gate configurations:

```json
{
  "version": 1,
  "qualityFilter": {
    "minArea": 50.0,
    "maxArea": 5000.0,
    "minTotalIntensity": 100.0,
    "maxEccentricity": 0.95,
    "minSolidity": 0.5,
    "hideFiltered": true,
    "excludeFromCsv": true
  },
  "gates": [
    {
      "channel": "CD45",
      "threshold": 0.4,
      "thresholdIsZScore": true,
      "positiveName": "CD45+",
      "negativeName": "CD45-",
      "positiveColor": [0, 200, 0],
      "negativeColor": [128, 128, 128],
      "clipPercentileLow": 1.0,
      "clipPercentileHigh": 99.0,
      "excludeOutliers": false,
      "positiveChildren": [],
      "negativeChildren": []
    }
  ]
}
```

### Phenotype CSV (`gate_pheno.csv`)

Exported cell phenotype assignments:

```csv
cell_id,phenotype,CD45,CD3,CD8,PANCK
0,T cytotoxic,+,+,+,-
1,Tumor,-,-,-,+
2,T helper,+,+,-,-
```

- `cell_id` — Cell identifier
- `phenotype` — Assigned phenotype name from the leaf gate node
- Marker columns — `+` (above threshold) or `-` (below threshold) for each gated marker
- Quality-filtered and outlier-excluded cells are omitted when "Exclude from CSV" is enabled

---

## Architecture

```
qupath-extension-gatetree/
├── src/main/java/qupath/ext/gatetree/
│   ├── GateTreeExtension.java       # Extension entry point
│   ├── model/
│   │   ├── GateNode.java            # Single gate (channel, threshold, children)
│   │   ├── GateTree.java            # Root container + quality filter
│   │   ├── CellIndex.java           # Columnar cell data store
│   │   ├── MarkerStats.java         # Per-marker statistics
│   │   └── QualityFilter.java       # Pre-gating QC criteria
│   ├── engine/
│   │   ├── GatingEngine.java        # Tree-walk cell assignment
│   │   └── LivePreviewService.java  # Debounced live preview
│   ├── ui/
│   │   ├── GateTreePane.java        # Main panel
│   │   ├── GateEditorPane.java      # Gate configuration editor
│   │   ├── HistogramCanvas.java     # Canvas-based histogram
│   │   ├── GateTreeCell.java        # Custom tree cell renderer
│   │   └── QualityFilterPane.java   # QC filter controls
│   └── io/
│       ├── GateTreeSerializer.java  # JSON save/load
│       └── PhenotypeCsvExporter.java # CSV export
```

### Performance

The extension is designed for large datasets (100k+ cells):

| Operation | Time (100k cells) |
|-----------|--------------------|
| Gate tree assignment | ~10ms |
| PathClass update loop | ~40ms |
| Viewer refresh | ~30ms |
| **Total threshold change** | **< 100ms** |

Key optimizations:
- **Columnar CellIndex** — `double[marker][cell]` layout for cache-friendly threshold scanning
- **80ms debounce** — Coalesces rapid slider drags into a single update
- **Canvas histogram** — Direct pixel drawing instead of JavaFX BarChart (avoids 200 Node objects)
- **Background threading** — Gating runs off the FX thread; only PathClass mutations happen on FX thread

---

## Quality Filters

The quality filter panel provides pre-gating cell QC:

| Filter | Description | Default |
|--------|-------------|---------|
| Area min | Remove debris and fragments | 0 |
| Area max | Remove merged segments and tissue folds | off |
| Eccentricity max | Remove elongated segmentation artifacts | 0.95 |
| Solidity min | Remove irregular shapes (area/convex_area) | 0.0 |
| Total intensity min | Remove dim/dead cells | 0 |

Filtered cells can be:
- **Hidden** — made transparent in the viewer
- **Excluded from CSV** — omitted from phenotype export

---

## Compatibility

This extension is designed to work with cell detections that have marker intensity measurements stored as QuPath measurements. It works with:

- **[Mirage pipeline](https://github.com/...)** — GeoJSON output with per-cell marker quantification
- **Any QuPath cell detection** — As long as detections have numeric measurements for markers
- **Imported GeoJSON** — Cell contours with measurement properties

---

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Submit a pull request

---

## Citation

If you use this tool in your research, please cite:

> *qupath-extension-gatetree: Interactive tree-based cell phenotyping gating for QuPath.* (2025). GitHub repository.

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
