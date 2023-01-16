import java.io.OutputStream

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.johnrengelman.shadow")
    `maven-publish`
}

val relocate = (findProperty("relocate") as? String)?.toBoolean() ?: true

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven {
        name = "Mojang"
        url = uri("https://libraries.minecraft.net/")
    }
    maven {
        name = "Spigot"
        url = uri("https://repo.dmulloy2.net/repository/public/")
    }
    maven {
        url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        content {
            group = includeGroup("org.bukkit")
            group = includeGroup("org.spigotmc")
        }
    }
    maven {
        url = uri("https://repo.spring.io/plugins-release/")
    }


}


dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:1.5.10")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1-native-mt")
    compileOnly("io.papermc.paper:paper-api:1.17.1-R0.1-SNAPSHOT")
    compileOnly("org.spigotmc:spigot-api:1.17.1-R0.1-SNAPSHOT")

    implementation("com.github.monun:tap:3.4.0")
    implementation("com.github.monun:kommand:0.8.0")
}
tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "16"
    }
    processResources {
        filesMatching("**/*.yml") {
            expand(project.properties)
        }
    }
    test {
        useJUnitPlatform()
        doLast {
            file("logs").deleteRecursively()
        }
    }
    create<Jar>("sourcesJar") {
        from(sourceSets["main"].allSource)
        archiveClassifier.set("sources")
    }
    shadowJar {
        archiveBaseName.set(project.property("pluginName").toString())
        archiveVersion.set("") // For bukkit plugin update
        archiveClassifier.set("") // Remove 'all'

        if (relocate) {
            relocate("com.github.monun.kommand", "${rootProject.group}.${rootProject.name}.kommand")
            relocate("com.github.monun.tap", "${rootProject.group}.${rootProject.name}.tap")
        }

        doFirst {
            println("relocate = $relocate")
        }
    }
    build {
        dependsOn(shadowJar)
    }
    create<Copy>("copyToServer") {
        from(shadowJar)
        var dest = File(rootDir, ".server/plugins")
        // if plugin.jar exists in plugins change dest to plugins/update
        if (File(dest, shadowJar.get().archiveFileName.get()).exists()) dest = File(dest, "update")
        into(dest)
    }
    create<DefaultTask>("setupWorkspace") {
        doLast {
            val versions = arrayOf(
                "1.16.5"
            )
            val buildtoolsDir = file(".buildtools")
            val buildtools = File(buildtoolsDir, "BuildTools.jar")

            val maven = File(System.getProperty("user.home"), ".m2/repository/org/spigotmc/spigot/")
            val repos = maven.listFiles { file: File -> file.isDirectory } ?: emptyArray()
            val missingVersions = versions.filter { version ->
                repos.find { it.name.startsWith(version) }?.also { println("Skip downloading spigot-$version") } == null
            }.also { if (it.isEmpty()) return@doLast }

            val download by registering(de.undercouch.gradle.tasks.download.Download::class) {
                src("https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar")
                dest(buildtools)
            }
            download.get().download()

            runCatching {
                for (v in missingVersions) {
                    println("Downloading spigot-$v...")

                    javaexec {
                        workingDir(buildtoolsDir)
                        main = "-jar"
                        args = listOf("./${buildtools.name}", "--rev", v)
                        // Silent
                        standardOutput = OutputStream.nullOutputStream()
                        errorOutput = OutputStream.nullOutputStream()
                    }
                }
            }.onFailure {
                it.printStackTrace()
            }
            buildtoolsDir.deleteRecursively()
        }
    }
}

//publishing {
//    publications {
//        create<MavenPublication>(project.property("pluginName").toString()) {
//            artifactId = project.name
//            from(components["java"])
//            artifact(tasks["sourcesJar"])
//        }
//    }
//}
tasks.jar {
    destinationDirectory.set(file("$rootDir/jars"))
    archiveName = rootProject.name + '-' + "1.17" + '-' + version + ".jar"
}