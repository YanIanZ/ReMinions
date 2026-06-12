plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" apply false
    id("com.gradleup.shadow") version "8.3.5" apply false
}

allprojects {
    apply(plugin = "java-library")

    group = rootProject.group
    version = rootProject.version

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
}
