plugins {
    `java-library`
    alias(libs.plugins.paperweight)
}

// Real NMS adapter for the 1.20.5 - 1.20.6 range. Compiled against Paper 1.20.6 so the impl can
// address Mojang-mapped net.minecraft.* classes. NMSHandlerProvider's LinkageError fallback
// routes the adapter on adjacent server builds that share the same NMS surface.

dependencies {
    paperweight.paperDevBundle("1.20.6-R0.1-SNAPSHOT")
    compileOnly(project(":plugin"))
}
