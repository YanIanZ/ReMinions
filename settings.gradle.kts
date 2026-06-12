rootProject.name = "ReMinions"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://jitpack.io")
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        maven("https://repo.auxilor.io/repository/maven-public/")
        maven("https://nexus.phoenixdevt.fr/repository/maven-public/")
        maven("https://repo.momirealms.net/releases/")
    }
}

include(":plugin")
include(":nms:v1_21_11")

// Older NMS targets disabled — project focuses on Minecraft 1.21.11+ via
// paperweight-userdev 2.x (Mojang-mapped production).
// include(":nms:v1_20_1")
// include(":nms:v1_20_4")
// include(":nms:v1_21_1")
