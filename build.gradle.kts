plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.0"
}

group = "hanamuramiyu.pawkin"
version = "1.1.1"
description = "NekoList"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.velocitypowered.com/snapshots/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://m2.dv8tion.net/releases")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.5-R0.1-SNAPSHOT")
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")
    implementation("net.dv8tion:JDA:5.0.0-beta.20")
    implementation("org.yaml:snakeyaml:2.2")
}

tasks {
    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        from("src/main/resources") {
            include("**/*.yml")
            include("**/*.yaml")
        }
    }
    
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    
    jar {
        archiveFileName.set("${project.name}-${project.version}.jar")
    }
    
    shadowJar {
        archiveFileName.set("${project.name}-${project.version}.jar")
        minimize()
        
        dependencies {
            exclude(dependency("io.papermc.paper:paper-api:.*"))
            exclude(dependency("com.velocitypowered:velocity-api:.*"))
        }
    }
    
    build {
        dependsOn(shadowJar)
    }
}