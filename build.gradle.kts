plugins {
    id("com.gradleup.shadow") version "8.3.5"
    id("qupath-conventions")
}

qupathExtension {
    name = "qupath-extension-gatetree"
    group = "io.github.qupath"
    version = "0.2.0"
    description = "Interactive tree-based cell phenotyping gating for multiplexed imaging"
    automaticModule = "io.github.qupath.extension.gatetree"
}

dependencies {
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)
    testImplementation(libs.bundles.qupath)
    testImplementation(libs.junit)
}
