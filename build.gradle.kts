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

ext {
    set("retrofit-version", "2.9.0")
}

dependencies {
    testImplementation(kotlin("test-junit"))
    implementation("com.squareup.retrofit2:retrofit:${project.ext["retrofit-version"]}")
    implementation("com.squareup.retrofit2:converter-gson:${project.ext["retrofit-version"]}")
    implementation("com.squareup.retrofit2:adapter-rxjava3:${project.ext["retrofit-version"]}")
    implementation("io.reactivex.rxjava3:rxjava:3.0.10")
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