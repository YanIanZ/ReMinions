plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

// JVM 25 compile-classpath attribute + Java 21 release target are configured in the root build.

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

// Bundled NMS adapter modules. Ordered newest-first so the shaded jar mirrors the runtime
// probe order in NMSHandlerProvider. Real-NMS buckets (paperweight) sit at the bottom;
// the rest are API-only and compile against the Paper API alone.
val nmsModules = listOf(
    ":nms:v26_1_2",
    ":nms:v1_21_11",
    ":nms:v1_21_8",
    ":nms:v1_21_6",
    ":nms:v1_21_5",
    ":nms:v1_21_4",
    ":nms:v1_21_2",
    ":nms:v1_21",
    ":nms:v1_20_5",
    ":nms:v1_20_3",
    ":nms:v1_20_2",
    ":nms:v1_20",
)
nmsModules.forEach { evaluationDependsOn(it) }

// ── Dependencies ─────────────────────────────────────────────────────────────

dependencies {
    // Paper API
    compileOnly(libs.paper.api)

    // Core runtime deps (loaded via paper-libraries at server boot)
    compileOnly(libs.hikari)
    compileOnly(libs.jedis)
    compileOnly(libs.gson)
    compileOnly(libs.mongo)

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

// Modules whose Paper dev bundle targets the Spigot-mapped runtime (pre-1.20.5). Their jar
// task emits Mojang-mapped bytecode; the shaded jar must consume reobfJar instead so the
// classes link on the obfuscated server runtime.
val spigotMappedNmsModules = setOf(":nms:v1_20", ":nms:v1_20_2", ":nms:v1_20_3")

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("${rootProject.name}-${project.version}.jar")

    nmsModules.forEach { path ->
        val sub = project(path)
        val taskName = if (path in spigotMappedNmsModules) "reobfJar" else "jar"
        dependsOn(sub.tasks.named(taskName))
        from(sub.tasks.named(taskName).map { zipTree(it.outputs.files.singleFile) })
    }
}

tasks.assemble { dependsOn(tasks.shadowJar) }
tasks.jar { enabled = false }
