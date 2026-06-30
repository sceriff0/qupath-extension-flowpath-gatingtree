# FlowPath - GatingTree

[![QuPath](https://img.shields.io/badge/QuPath-%E2%89%A50.7.0-blue.svg)](https://qupath.github.io/)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://jdk.java.net/25/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Docs](https://img.shields.io/badge/docs-flowpath.readthedocs.io-success.svg)](https://flowpath.readthedocs.io/)

Interactive **tree-based cell phenotyping** for [QuPath](https://qupath.github.io/).
Build hierarchical marker gates (e.g. `CD45+ → CD3+ → CD8+ = "T cytotoxic"`), drag
thresholds, draw 2D regions, and see cells recolor in real time on multiplexed
imaging data (CODEX, MIBI, mIF).

Part of the [FlowPath suite](https://flowpath.readthedocs.io/), alongside
[qUMAP](https://github.com/sceriff0/qupath-extension-flowpath-qumap) and
[AnnoMask](https://github.com/sceriff0/qupath-extension-annomask). Designed to
work with the [MIRAGE](https://mirage-pipeline.readthedocs.io/) pipeline.

## Install

In QuPath, add the FlowPath catalog and install **GatingTree**:

```
https://raw.githubusercontent.com/sceriff0/flowpath-catalog/main/catalog.json
```

(Extensions → Manage extensions → Manage extension catalogs → Add.) Launch with
**Extensions → FlowPath - GatingTree** (`Ctrl+G`). Full install options — JAR
drop, build from source — are in the [docs](https://flowpath.readthedocs.io/installation/).

## Build from source

```bash
git clone https://github.com/sceriff0/qupath-extension-flowpath-gatingtree.git
cd qupath-extension-flowpath-gatingtree
./gradlew build   # JAR lands in build/libs/ → drag onto QuPath
```

## 📖 Documentation

Features, the gating workflow, output formats, and troubleshooting are all at
**<https://flowpath.readthedocs.io/>**.

## Citation

> FlowPath: Interactive tree-based cell phenotyping for QuPath. (2026).
> https://github.com/sceriff0/qupath-extension-flowpath-gatingtree

See the [citation page](https://flowpath.readthedocs.io/citation/) for QuPath and
MIRAGE references.

## License

MIT. See [LICENSE](LICENSE).
