plugins {
    `java-library`
    alias(libs.plugins.paperweight)
}

// Real NMS adapter for the 1.21.8 – 1.21.10 range. paperweight pins the 1.21.8 dev bundle
// so the impl can address net.minecraft.* classes by their Mojang-mapped names. Linkage on
// 1.21.9 / 1.21.10 servers is verified at runtime via NMSHandlerProvider's LinkageError
// fallback (those versions share the post-1.21.2 SlotDisplay shape used here).

dependencies {
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
    compileOnly(project(":plugin"))
}
