plugins {
    `java-library`
    alias(libs.plugins.paperweight)
}

dependencies {
    // Same CalVer dev bundle the plugin module compiles against. paperweight resolves it through
    // the paper-public Maven repo declared in settings.gradle.kts.
    paperweight.paperDevBundle(libs.versions.paper.get())
    compileOnly(project(":plugin"))
}
