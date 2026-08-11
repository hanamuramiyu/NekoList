import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.GradleException
import java.util.zip.ZipFile

plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.6.1"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":config:file"))
    implementation(project(":storage:file"))
    compileOnly("org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.spigotmc:spigot-api:1.21-R0.1-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets {
    main {
        resources {
            exclude("plugin.yml")
        }
    }
}

val pluginVersion = project.version.toString()
val generatedPluginDescriptorDir = layout.buildDirectory.dir("generated/plugin-descriptor")
val distributionJar = layout.buildDirectory.file("libs/monban-bukkit-spigot-${project.version}.jar")

val processPluginDescriptor by tasks.registering(Copy::class) {
    from("src/main/resources/plugin.yml")
    into(generatedPluginDescriptorDir)
    filteringCharset = "UTF-8"
    inputs.property("pluginVersion", pluginVersion)
    filter<ReplaceTokens>("tokens" to mapOf("version" to pluginVersion))
}

val verifyDistributionJar by tasks.registering {
    group = "verification"
    description = "Verifies the Bukkit/Spigot distribution JAR packaging invariants."
    dependsOn("shadowJar")
    inputs.file(distributionJar)

    doLast {
        val jarFile = inputs.files.singleFile
        ZipFile(jarFile).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()

            fun count(path: String): Int = entries.count { it == path }

            if (count("plugin.yml") != 1) {
                throw GradleException("Bukkit/Spigot distribution must contain exactly one plugin.yml.")
            }
            if (count("paper-plugin.yml") != 0) {
                throw GradleException("Bukkit/Spigot distribution must not contain paper-plugin.yml.")
            }
            if (count("org/yaml/snakeyaml/Yaml.class") != 0) {
                throw GradleException("Bukkit/Spigot distribution contains unrelocated SnakeYAML classes.")
            }
            if (count("hanamuramiyu/monban/internal/libs/snakeyaml/Yaml.class") != 1) {
                throw GradleException("Bukkit/Spigot distribution must contain relocated SnakeYAML exactly once.")
            }
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    processResources {
        dependsOn(processPluginDescriptor)
        from(generatedPluginDescriptorDir)
    }

    jar {
        enabled = false
    }

    shadowJar {
        archiveFileName.set("monban-bukkit-spigot-${project.version}.jar")
        relocate("org.yaml.snakeyaml", "hanamuramiyu.monban.internal.libs.snakeyaml")
    }

    assemble {
        dependsOn(shadowJar)
    }

    check {
        dependsOn(verifyDistributionJar)
    }
}
