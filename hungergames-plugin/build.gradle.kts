plugins {
    `java-library`
}

description = "HungerGames plugin implementation for Paper."

dependencies {
    api(project(":hungergames-api"))

    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.2.14")
    testImplementation("com.sk89q.worldedit:worldedit-bukkit:7.2.14")
}

// Das Plugin-Jar muss die API-Klassen enthalten, damit ein einzelnes Jar
// in den plugins-Ordner gelegt werden kann.
tasks.jar {
    from(project(":hungergames-api").sourceSets.main.get().output)
    archiveBaseName.set("TheHungerGames")
}
