plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

// ── Source sets ──────────────────────────────────────────────────────────────

sourceSets {
    // Compile-only stubs for old BeeMinions data migration (not shaded into output)
    create("stubs") {
        java.srcDir("src/stubs/java")
    }
    main {
        compileClasspath += sourceSets["stubs"].output
    }
}

// ── NMS modules to bundle ────────────────────────────────────────────────────

val nmsModules = listOf(":nms:v1_21_11")
nmsModules.forEach { evaluationDependsOn(it) }

// ── Dependencies ─────────────────────────────────────────────────────────────

dependencies {
    // Paper API
    compileOnly(libs.paper.api)

    // Core runtime deps (loaded via paper-libraries at server boot)
    compileOnly(libs.hikari)
    compileOnly(libs.jedis)
    compileOnly(libs.gson)

    // Required soft deps
    compileOnly(libs.luckperms)
    compileOnly(libs.vault) { exclude(group = "org.bukkit", module = "bukkit") }
    compileOnly(libs.placeholderapi)

    // Optional soft deps
    compileOnly(libs.mmoitems)   { isTransitive = false }
    compileOnly(libs.mythiclib)
    compileOnly(libs.eco)
    compileOnly(libs.libreforge)
    compileOnly(libs.libreforge.loader)
    compileOnly(libs.ecoitems)
    compileOnly(libs.ecoskills)
    compileOnly(libs.itemsadder)
    compileOnly(libs.craftengine.core)
    compileOnly(libs.craftengine.bukkit)

    // Drop-in jars for soft-deps unavailable on public repos
    compileOnly(fileTree("libs") { include("*.jar") })

    // Annotation processor
    compileOnly(libs.jetbrains)
}

// ── Resources ─────────────────────────────────────────────────────────────────

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

// ── Shadow JAR ───────────────────────────────────────────────────────────────

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("${rootProject.name}-${project.version}.jar")

    // Bundle each active NMS adapter's reobfuscated jar
    nmsModules.forEach { path ->
        val sub = project(path)
        dependsOn(sub.tasks.named("jar"))
        from(sub.tasks.named("jar").map { zipTree(it.outputs.files.singleFile) })
    }
}

tasks.assemble { dependsOn(tasks.shadowJar) }
tasks.jar { enabled = false }
