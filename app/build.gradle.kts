plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.skriptlang.org/releases")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://maven.maxhenkel.de/repository/public")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    
    compileOnly("com.github.SkriptLang:Skript:2.15.2")

    compileOnly("com.github.retrooper:packetevents-spigot:2.11.2")
    
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.5.0")
    
    // API Vosk (Reconnaissance vocale)
    implementation("com.alphacephei:vosk:0.3.45")
    implementation("net.java.dev.jna:jna:5.14.0")
    // API REST (certificat)
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78")
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



tasks.jar {
    archiveBaseName.set("skwhy")
    archiveVersion.set(project.version.toString())

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE", "META-INF/NOTICE.txt")
}

val pluginVersion = project.version.toString()

tasks.processResources {
    expand("version" to pluginVersion)
}

tasks {
    build {
        finalizedBy("copyJarToPlugins")
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
