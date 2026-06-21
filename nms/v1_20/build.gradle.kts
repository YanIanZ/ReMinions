plugins {
    `java-library`
}

// Pure API-only adapter — no paperweight dev bundle. Shared {@code ApiBackedNmsAdapter}
// in the plugin module provides the actual behaviour; the per-version classes here exist so
// NMSHandlerProvider can report which bucket resolved at boot.
// Supported server range: 1.20.0 – 1.20.1.

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(project(":plugin"))
}
