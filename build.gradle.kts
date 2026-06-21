import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    alias(libs.plugins.paperweight) apply false
    alias(libs.plugins.shadow)      apply false
}

subprojects {
    apply(plugin = "java-library")

    group   = rootProject.group
    version = rootProject.version

    // Toolchain must be 25 to read Paper 26.1.x API class files (major v69).
    // Release target stays at 21 so the shaded jar still loads on Paper 1.21.11 servers (Java 21).
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    // Paper 26.1.x publishes its API with Gradle Module Metadata pinned to JVM 25. The compile
    // classpath needs to declare it accepts that variant — bytecode emission stays at 21 via
    // `options.release.set(21)`, so the shaded jar still loads on 1.21.11 (Java 21) servers
    // since the 26.1.x paper-api jar is never on their runtime classpath.
    plugins.withType<JavaLibraryPlugin> {
        configurations.named("compileClasspath").configure {
            attributes {
                attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
            }
        }
    }
}
