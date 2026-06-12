plugins {
    `java-library`
    id("io.papermc.paperweight.userdev")
}

dependencies {
    paperweight.paperDevBundle("1.20.4-R0.1-SNAPSHOT")
    compileOnly(project(":plugin"))
}

paperweight.reobfArtifactConfiguration.set(
    io.papermc.paperweight.userdev.ReobfArtifactConfiguration.REOBF_PRODUCTION
)
