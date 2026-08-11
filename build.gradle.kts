plugins {
    base
}

allprojects {
    group = "hanamuramiyu.monban"
    version = "3.0.0"
}

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
}

tasks.build {
    dependsOn(
        ":common:build",
        ":config:file:build",
        ":storage:file:build",
        ":platforms:bukkit:build",
        ":platforms:paper:build",
        ":platforms:velocity:build",
    )
}
