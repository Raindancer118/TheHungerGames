plugins {
    `java-library`
}

description = "Public API for the HungerGames plugin: interfaces, immutable data models and Bukkit events."

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addStringOption("Xdoclint:none", "-quiet")
    }
}
