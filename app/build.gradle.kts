import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

version = "1.0.0" 

plugins {
    java
    application
    id("com.gradleup.shadow") version "9.0.0-beta15"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.skriptlang.org/releases")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://maven.pvphub.me/tofaa")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    
    compileOnly("com.github.SkriptLang:Skript:2.15.2")

    compileOnly("com.github.retrooper:packetevents-spigot:2.11.2")

    implementation("io.github.tofaa2:spigot:3.0.3-SNAPSHOT")
}

tasks.register<Copy>("copyJarToPlugins") {
    from(tasks.shadowJar)
    into("C:/Users/orimi/Desktop/proxti/plugins") 
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("skwhy.SkWhy")
}

tasks.build {
    finalizedBy("copyJarToPlugins")
}

tasks.jar {
    archiveBaseName.set("skwhy")
    archiveVersion.set(project.version.toString())
}

tasks {
    startScripts { enabled = false }
    distZip { enabled = false }
    distTar { enabled = false }
    shadowJar {
        archiveBaseName.set("skwhy")
        archiveVersion.set(version.toString())
        archiveClassifier.set("")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        relocate("me.tofaa.entitylib", "skwhy.libs.entitylib")
        archiveClassifier.set("")
    }

    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }
}

tasks.javadoc {
    options.encoding = "UTF-8"

    (options as StandardJavadocDocletOptions).apply {
        author(true)
        version(true)
        links("https://docs.oracle.com/en/java/javase/21/docs/api/")
    }
}
