plugins {
    `java-library`
    alias(libs.plugins.paperweight)
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly(project(":plugin"))
}
