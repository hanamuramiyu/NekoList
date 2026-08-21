import org.apache.tools.ant.filters.ReplaceTokens

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
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":presentation"))
    implementation(project(":config:file"))
    implementation(project(":storage:file"))

    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val pluginVersion = project.version.toString()
val generatedPluginMetadataDir = layout.buildDirectory.dir("generated/sources/plugin-metadata/java/main")

val generatePluginMetadata by tasks.registering(Copy::class) {
    from("src/main/templates/MonbanVelocityPluginMetadata.java.template")
    into(generatedPluginMetadataDir.map { it.dir("hanamuramiyu/monban/velocity") })
    filteringCharset = "UTF-8"
    inputs.property("pluginVersion", pluginVersion)
    filter<ReplaceTokens>("tokens" to mapOf("version" to pluginVersion))
    rename { "MonbanVelocityPluginMetadata.java" }
}

sourceSets {
    main {
        java.srcDir(generatedPluginMetadataDir)
    }
}


tasks {
    compileJava {
        dependsOn(generatePluginMetadata)
    }

    test {
        useJUnitPlatform()
    }

    jar {
        enabled = false
    }

    shadowJar {
        archiveFileName.set("monban-velocity-${project.version}.jar")
        relocate("org.yaml.snakeyaml", "hanamuramiyu.monban.internal.libs.snakeyaml")
    }

    assemble {
        dependsOn(shadowJar)
    }
}
