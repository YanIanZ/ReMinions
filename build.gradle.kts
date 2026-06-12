plugins {
    alias(libs.plugins.paperweight) apply false
    alias(libs.plugins.shadow)      apply false
}

subprojects {
    apply(plugin = "java-library")

    group   = rootProject.group
    version = rootProject.version

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
}
