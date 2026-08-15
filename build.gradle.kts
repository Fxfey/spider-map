plugins {
    kotlin("jvm") version "2.4.10"
}

group = "com.spidermap"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Provided by the server at runtime — never bundled into our jar.
    compileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
}

kotlin {
    // Paper 26.2 ships Java 25 bytecode and refuses to start on older JDKs (SDLC §3).
    jvmToolchain(25)
}

tasks.processResources {
    // Lets plugin.yml carry the version from one place instead of duplicating it.
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}
