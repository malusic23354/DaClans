import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    kotlin("jvm") version "2.4.0"
    id("com.gradleup.shadow") version "9.4.2"
    id("com.modrinth.minotaur") version "2.10.0"
}

group = "net.malusic"
version = file("src/main/resources/paper-plugin.yml")
    .readLines()
    .first { it.startsWith("version:") }
    .substringAfter(":")
    .trim()
    .trim('"', '\'')

fun changelogFor(version: String): String {
    val changelog = file("CHANGELOG.md")
    if (!changelog.exists()) return "No changelog was specified."

    val lines = changelog.readLines()
    val start = lines.indexOfFirst { it.trim() == "## $version" }
    if (start < 0) return "No changelog entry found for $version."

    val nextHeading = lines.drop(start + 1).indexOfFirst { it.startsWith("## ") }
    val end = if (nextHeading < 0) lines.size else start + 1 + nextHeading

    return lines.subList(start + 1, end).joinToString("\n").trim()
}

val localTokenFile = file(".gradle/token.properties")
val localToken =
    if (!localTokenFile.exists()) {
        null
    } else {
        Properties()
            .apply { localTokenFile.inputStream().use { load(it) } }
            .getProperty("modrinthToken")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

modrinth {
    token.set(localToken)
    projectId.set("afNc21Ey")
    versionNumber.set(project.version.toString())
    versionType.set("beta")
    changelog.set(changelogFor(project.version.toString()))
    uploadFile.set(tasks.shadowJar)
    gameVersions.addAll(listOf("1.21.11", "26.1", "26.2", "26.3"))
    loaders.add("paper")
    loaders.add("purpur")
    dependencies {
        required.project("ayRaM8J7")
        optional.project("lKEzGugV")
        optional.project("7k37mlVm")
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.12.3")
    compileOnly("de.hexaoxi:carbonchat-api:3.0.0-beta.39")

    implementation("org.xerial:sqlite-jdbc:3.49.1.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("com.mysql:mysql-connector-j:26.7.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    jar {
        archiveClassifier.set("plain")
    }

    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
    }

    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }

    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }

    val copyPlugin = register<Copy>("copyPlugin") {
        from(shadowJar.flatMap { it.archiveFile })
        into("C:\\Users\\SKYMIL\\Desktop\\Main\\Work\\Coding\\Java\\MagicKPvP\\run\\plugins")
    }

    register<Exec>("runServer") {
        dependsOn(copyPlugin)
        workingDir = file("C:/Users/SKYMIL/Desktop/Main/Work/Coding/Java/MagicKPvP/run")
        // Minecraft 26.3 requires Java 25+, so the test server runs on Java 26.
        // Change serverJavaHome in gradle.properties if Java 26 is installed elsewhere.
        val serverJavaHome = providers.gradleProperty("serverJavaHome")
            .getOrElse("C:/Program Files/Java/jdk-26.0.1")
        commandLine(
            "$serverJavaHome/bin/java.exe",
            "-Xms1G",
            "-Xmx4G",
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
            "-Daikars.new.flags=true",
            "-jar", "paper.jar", "--nogui"
        )
        standardInput = System.`in`
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.register("bumpVersion") {
    group = "release"
    description = "Bump the patch version in paper-plugin.yml and README.md."
    doLast {
        val pluginYml = file("src/main/resources/paper-plugin.yml")
        val readme = file("README.md")
        val pattern = Regex("(?m)^version:\\s*\"?([0-9]+\\.[0-9]+\\.[0-9]+)\"?\\s*$")
        val current = pattern.find(pluginYml.readText())?.groupValues?.get(1)
            ?: error("Could not find a version in ${pluginYml.path}")
        val parts = current.split(".")
        val next = "${parts[0]}.${parts[1]}.${parts[2].toInt() + 1}"
        pluginYml.writeText(pluginYml.readText().replaceFirst(pattern, "version: \"$next\""))
        if (readme.exists()) {
            readme.writeText(readme.readText().replace("version-$current-", "version-$next-"))
        }
        println("Bumped version $current -> $next")
    }
}
