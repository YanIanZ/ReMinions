plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.5"
}

sourceSets {
    create("stubs") {
        java.srcDir("src/stubs/java")
    }
    main {
        compileClasspath += sourceSets["stubs"].output
    }
}

val nmsModules = listOf(":nms:v1_21_11")

nmsModules.forEach { evaluationDependsOn(it) }

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // Resolvable softdeps
    compileOnly("net.luckperms:api:5.4")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("me.clip:placeholderapi:2.11.6")

    // Optional softdeps — coords/versions are best-effort; bump to match the version
    // your server actually runs if the API surface diverges. Anything that fails to
    // resolve in your environment can be replaced by dropping the jar in `plugin/libs/`.
    compileOnly(fileTree("libs") { include("*.jar") })

    compileOnly("dev.aurelium:auraskills-api-bukkit:2.3.12")
    compileOnly("net.Indyuce:MMOItems-API:6.10.1-SNAPSHOT") { isTransitive = false }
    compileOnly("io.lumine:MythicLib-dist:1.7.1-SNAPSHOT")
    compileOnly("com.willfp:eco:7.6.5")
    compileOnly("com.willfp:libreforge:5.7.0:all")
    compileOnly("com.willfp:libreforge-loader:5.7.0:all")
    compileOnly("com.willfp:EcoItems:6.8.0")
    compileOnly("com.willfp:EcoSkills:4.9.0")
    compileOnly("com.github.LoneDev6:api-itemsadder:3.6.1")
    compileOnly("net.momirealms:craft-engine-core:0.0.62")
    compileOnly("net.momirealms:craft-engine-bukkit:0.0.62")

    compileOnly("com.zaxxer:HikariCP:5.0.1")
    compileOnly("redis.clients:jedis:5.1.3")
    compileOnly("com.google.code.gson:gson:2.10.1")
    compileOnly("org.jetbrains:annotations:24.1.0")
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("${rootProject.name}-${project.version}.jar")

    nmsModules.forEach { path ->
        val sub = project(path)
        dependsOn(sub.tasks.named("jar"))
        from(sub.tasks.named("jar").map { zipTree(it.outputs.files.singleFile) })
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.jar { enabled = false }
