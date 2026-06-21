plugins {
    `java-library`
    alias(libs.plugins.paperweight)
}

dependencies {
    // Legacy bundle — this adapter targets Minecraft 1.21.11 NMS internals. Bumping to a 26.1.x
    // CalVer bundle would change the reobf'd `net.minecraft.server.*` shape and break the bridges.
    paperweight.paperDevBundle(libs.versions.paperLegacy.get())
    compileOnly(project(":plugin"))
}
