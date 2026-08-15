plugins {
    kotlin("jvm") version "2.4.10"
    // Generates the serializers at compile time, so no reflection is needed at
    // runtime — one less thing to go wrong under Paper's plugin classloader.
    kotlin("plugin.serialization") version "2.4.10"
    id("com.gradleup.shadow") version "9.6.1"
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

    implementation("io.javalin:javalin:7.2.3") {
        // Paper supplies slf4j-api *and* a binding to its own logger (see
        // Plugin#getSLF4JLogger). Bundling either here would shadow that: the
        // api alone leaves Javalin with no provider, and adding slf4j-simple
        // would fight Paper's logging. Excluding both lets Jetty's logs route
        // into the server log like any other plugin's.
        exclude(group = "org.slf4j", module = "slf4j-simple")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Polygon geometry. Used only to tell "these claims share a boundary"
    // (allowed) from "these claims share actual ground" (rejected) — a
    // distinction SDLC §3 specifically warns against hand-rolling.
    implementation("org.locationtech.jts:jts-core:1.20.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    // Paper 26.2 ships Java 25 bytecode and refuses to start on older JDKs (SDLC §3).
    jvmToolchain(25)
}

// ---------------------------------------------------------------- web UI
//
// The React app is built by Vite and copied into the jar, so deployment stays
// a single artifact and the UI is served from the same origin as the API.

val webDir = layout.projectDirectory.dir("web")
val webDist = webDir.dir("dist")

/** npm ships as a .cmd shim on Windows, which Exec will not resolve on its own. */
val npm = if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm"

val npmInstall = tasks.register<Exec>("npmInstall") {
    description = "Installs the web UI's dependencies."
    workingDir = webDir.asFile
    commandLine(npm, "install")

    // Re-runs only when the manifest changes, not on every build.
    inputs.file(webDir.file("package.json"))
    inputs.file(webDir.file("package-lock.json"))
    outputs.dir(webDir.dir("node_modules"))
}

val buildWeb = tasks.register<Exec>("buildWeb") {
    description = "Builds the React app into web/dist."
    dependsOn(npmInstall)
    workingDir = webDir.asFile
    commandLine(npm, "run", "build")

    inputs.dir(webDir.dir("src"))
    inputs.dir(webDir.dir("public"))
    inputs.file(webDir.file("index.html"))
    inputs.file(webDir.file("vite.config.ts"))
    inputs.file(webDir.file("tsconfig.app.json"))
    outputs.dir(webDist)
}

tasks.processResources {
    // Lets plugin.yml carry the version from one place instead of duplicating it.
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }

    // Lands at /web inside the jar, where WebServer already points Javalin.
    dependsOn(buildWeb)
    from(webDist) {
        into("web")
    }
}

// The plugin needs Javalin, Jetty and the Kotlin stdlib inside the jar — Paper
// provides none of them. Only shadowJar is emitted, so build/libs holds exactly
// one jar and there is no way to deploy the dependency-less one by mistake.
tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveClassifier.set("")
    // Jetty discovers components via META-INF/services; without merging, each
    // jar's descriptor overwrites the last and startup fails. INCLUDE is
    // required so every copy reaches the transformer — under the default
    // EXCLUDE the duplicates are dropped before it can merge them.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
