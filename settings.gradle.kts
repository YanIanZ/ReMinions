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
        maven("https://repo.extendedclip.com/releases/")
        maven("https://repo.auxilor.io/repository/maven-public/")
        maven("https://nexus.phoenixdevt.fr/repository/maven-public/")
        maven("https://repo.momirealms.net/releases/")
    }
}

include(":plugin")
// NMS adapter modules — probed at runtime in order. API-only buckets (1.20.x, 1.21.0–1.21.10)
// share the plugin's ApiBackedNmsAdapter; v1_21_11 + v26_1_2 use real NMS internals via
// paperweight dev bundles.
include(":nms:v1_20")
include(":nms:v1_20_2")
include(":nms:v1_20_3")
include(":nms:v1_20_5")
include(":nms:v1_21")
include(":nms:v1_21_2")
include(":nms:v1_21_4")
include(":nms:v1_21_5")
include(":nms:v1_21_6")
include(":nms:v1_21_8")
include(":nms:v1_21_11")
include(":nms:v26_1_2")
