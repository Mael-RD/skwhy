plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.23"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.skriptlang.org/releases")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://maven.maxhenkel.de/repository/public")
    flatDir {
        dirs("libs")
    }
}

dependencies {
    // Paper complet (NMS, noms Mojang) : l'IA des custom entities utilise les vrais mobs du serveur
    paperweight.paperDevBundle("26.2.build.129-stable")
    
    compileOnly("com.github.SkriptLang:Skript:2.15.2")

    compileOnly("com.github.retrooper:packetevents-spigot:2.11.2")
    compileOnly(files("libs/nuvotifier.jar"))
    // API REST (certificat)
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Les classes du plugin utilisent Bukkit/PacketEvents ; MockBukkit fournit un serveur simule (Material, Tag...)
    testImplementation("io.papermc.paper:paper-api:26.2.build.129-stable")
    testImplementation("com.github.retrooper:packetevents-spigot:2.11.2")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.2:4.117.0")
}

tasks.register<Copy>("copyJarToPlugins") {
    val jarTask = tasks.named<Jar>("jar")
    dependsOn(jarTask)
    from(jarTask.flatMap { it.archiveFile })
    into("C:/Users/orimi/Desktop/proxti/serveur vide/plugins")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}



tasks.test {
    useJUnitPlatform()
    // paperweight ajoute le serveur Paper complet (NMS) au classpath d'execution : MockBukkit doit rester sur paper-api seul
    classpath = classpath.filter { it.name != "mappedServerJar.jar" }
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
        links("https://docs.oracle.com/en/java/javase/25/docs/api/")
    }
}
