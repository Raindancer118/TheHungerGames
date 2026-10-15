plugins {
    java
}

subprojects {
    apply(plugin = "java-library")

    group = "com.hungergames"
    version = "2.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.enginehub.org/repo/")
    }

    dependencies {
        "compileOnly"("io.papermc.paper:paper-api:26.2.build.87-stable")

        "testImplementation"("io.papermc.paper:paper-api:26.2.build.87-stable")
        "testImplementation"(platform("org.junit:junit-bom:5.13.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        "testImplementation"("org.mockito:mockito-core:5.20.0")
        "testImplementation"("org.mockito:mockito-junit-jupiter:5.20.0")
    }

    java {
        // Paper 26.2 läuft auf Java 25 und liefert Java-25-Bytecode aus —
        // das Plugin muss entsprechend auf 25 zielen.
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(25)
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
