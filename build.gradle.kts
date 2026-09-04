import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    java
    kotlin("jvm") version "2.4.10"

    id("com.gradleup.shadow") version "9.0.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("de.eldoria.plugin-yml.paper") version "0.9.0"
}

group = "com.glomdom"
version = "1.0-SNAPSHOT"

val rebarVersion = providers.gradleProperty("rebar.version").get()
val pylonVersion = providers.gradleProperty("pylon.version").get()

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.xenondevs.xyz/releases")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("io.github.pylonmc:rebar:$rebarVersion")
    compileOnly("io.github.pylonmc:pylon:$pylonVersion")
}

kotlin {
    jvmToolchain(25)

    compilerOptions.freeCompilerArgs = listOf("-XXLanguage:+UnnamedLocalVariables")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}


tasks.build {
    dependsOn("shadowJar")
}

tasks.shadowJar {
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib*"))
        exclude(dependency("org.jetbrains:annotations*"))
    }
}

tasks.runServer {
    minecraftVersion("26.2")

    doFirst {
        project.projectDir.resolve("run/plugins").deleteRecursively()
    }

    downloadPlugins {
        github("pylonmc", "rebar", rebarVersion, "rebar-$rebarVersion.jar")
        github("pylonmc", "pylon", pylonVersion, "pylon-$pylonVersion.jar")
    }

    maxHeapSize = "6G"
    jvmArgs = listOf(
        "-XX:+UseG1GC",
        "-XX:+ParallelRefProcEnabled",
        "-XX:MaxGCPauseMillis=200",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+DisableExplicitGC",
        "-XX:+AlwaysPreTouch",
        "-XX:G1NewSizePercent=30",
        "-XX:G1MaxNewSizePercent=40",
        "-XX:G1HeapRegionSize=8M",
        "-XX:G1ReservePercent=20",
        "-XX:G1HeapWastePercent=5",
        "-XX:G1MixedGCCountTarget=4",
        "-XX:InitiatingHeapOccupancyPercent=15",
        "-XX:G1MixedGCLiveThresholdPercent=90",
        "-XX:G1RSetUpdatingPauseTimePercent=5",
        "-XX:SurvivorRatio=32",
        "-XX:+PerfDisableSharedMem",
        "-XX:MaxTenuringThreshold=1",
        "-Dusing.aikars.flags=https://mcflags.emc.gs",
        "-Daikars.new.flags=true"
    )
}

paper {
    name = rootProject.name
    version = project.version.toString()
    main = "com.glomdom.splinter.Splinter"
    bootstrapper = "com.glomdom.splinter.Bootstrapper"
    loader = "com.glomdom.splinter.Loader"
    apiVersion = "26.2"
    authors = listOf("glomdom")
    description = "Logistics for your factory"
    generateLibrariesJson = true

    bootstrapDependencies {
        register("Rebar") {
            required = true
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
    }

    serverDependencies {
        register("Rebar") {
            required = true
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }

        register("Pylon") {
            required = true
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
    }
}

tasks.generatePaperPluginDescription {
    useDefaultCentralProxy()
}