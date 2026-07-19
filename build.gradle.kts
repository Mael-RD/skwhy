plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("com.gradleup.shadow") version "8.3.9"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.skriptlang.org/releases")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://maven.maxhenkel.de/repository/public")
}
dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    
    compileOnly("com.github.SkriptLang:Skript:2.15.2") {
        exclude(group = "io.papermc.paper", module = "paper-api")
        exclude(group = "org.spigotmc", module = "spigot-api")
    }
    compileOnly("com.github.retrooper:packetevents-spigot:2.11.2") {
        exclude(group = "io.papermc.paper", module = "paper-api")
        exclude(group = "org.spigotmc", module = "spigot-api")
    }

    implementation("org.bouncycastle:bcpkix-jdk18on:1.78")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78")
    implementation("net.bytebuddy:byte-buddy:1.15.11")
    implementation("net.bytebuddy:byte-buddy-agent:1.15.11")
}

tasks.register<Copy>("copyJarToPlugins") {
    val jarTask = tasks.named<Jar>("jar")
    dependsOn(jarTask)
    from(jarTask.flatMap { it.archiveFile })
    into("C:/Users/orimi/Desktop/proxti/plugins")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.shadowJar {
    archiveBaseName.set("skwhy")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("META-INF/LICENSE*", "META-INF/NOTICE*")
}
val pluginVersion = project.version.toString()
