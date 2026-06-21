plugins {
    `java-library`
    alias(libs.plugins.paperweight)
}

// Real NMS adapter for 1.20.3 – 1.20.4. Paper used the Spigot-mapped runtime here, so the shadowJar
// pulls the {@code reobfJar} output of this module (Mojang names relocated back to versioned
// org.bukkit.craftbukkit.v_*).

dependencies {
    paperweight.paperDevBundle("1.20.4-R0.1-SNAPSHOT")
    compileOnly(project(":plugin"))
}
