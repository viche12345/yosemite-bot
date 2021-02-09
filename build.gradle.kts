import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

plugins {
    kotlin("jvm") version "1.4.10"
    application
}

group = "me.vkwok"
version = "1.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test-junit"))
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
    configurations["compileClasspath"].forEach { file: File ->
        from(zipTree(file.absoluteFile))
    }
    doLast {
        File(destinationDirectory.asFile.get(), "yosemite-bot-MAC.command").writeText("""
            #!/bin/bash
            cd "$(dirname ${'$'}BASH_SOURCE)"
            clear
            java -jar yosemite-bot-${project.version}.jar
        """.trimIndent())
        File(destinationDirectory.asFile.get(), "yosemite-bot-WINDOWS.bat").writeText("""
            java -jar yosemite-bot-${project.version}.jar
        """.trimIndent())
    }
}

application {
    mainClassName = "MainKt"
}