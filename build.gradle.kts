plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.3.1"
}

group = "hanamuramiyu.pawkin"
version = "2.0.0"
description = "NekoList"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://jitpack.io")
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("com.github.dv8fromtheworld:JDA:5.0.0-beta.20")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.3")
    implementation("net.kyori:adventure-api:4.17.0")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("net.kyori:adventure-text-serializer-legacy:4.17.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation("io.papermc:paperlib:1.0.8")
}

tasks {
    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        from("src/main/resources") {
            include("**/*.yml")
            expand("version" to project.version)
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
        minimize {
            exclude(dependency("org.mariadb.jdbc:mariadb-java-client:.*"))
        }
        relocate("com.zaxxer.hikari", "hanamuramiyu.pawkin.net.lib.hikari")
        relocate("org.bstats", "hanamuramiyu.pawkin.net.lib.bstats")
        relocate("io.papermc.lib", "hanamuramiyu.pawkin.net.lib.paperlib")
    }
    
    build {
        dependsOn(shadowJar)
    }
}