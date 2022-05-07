import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.20"
    kotlin("plugin.serialization") version "1.6.20"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    application
}

val okHttpVersion: String by ext
val cliktVersion: String by ext
val gsonVersion: String by ext
val coroutinesVersion: String by ext
val okioVersion: String by ext
val logbackVersion: String by ext
val kotlinLoggingVersion: String by ext
val slf4jVersion:String by ext
val koinVersion:String by ext
val hopliteVersion : String by ext
val kotlinVersion : String by ext

group = "io.sharptree"

val vendor = "Sharptree"
val product = "label-print-agent"
val projectName = "label-print-agent"
version = "1.0.0"

repositories {
    mavenCentral()
}

tasks.compileJava {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<Jar> {
    manifest {
        attributes(
            mapOf(
                "Main-Class" to application.mainClass,
                "Implementation-Title" to product,
                "Created-By" to vendor,
                "Implementation-Version" to project.version
            )
        )
    }
}

// Configure the distribution task to tar and gzip the results.
tasks.distTar {
    rootSpec
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")
}

tasks.assembleDist {
    finalizedBy("pack-linux")
}

tasks.register("pack-linux"){
    dependsOn( "tar-linux")

    doLast{
        copy {
            from(layout.buildDirectory.asFile.get().path + File.separator + "libs" + File.separator + project.name + "-" + version + "-all.jar")
            into("${layout.projectDirectory}${File.separator}resources${File.separator}windows")
            rename { filename: String ->
                println(filename)
                if (filename == "${project.name}-${version}-all.jar") {
                    "${project.name}.jar"
                } else {
                    filename
                }
            }
        }
        delete(layout.buildDirectory.asFile.get().path + File.separator + "distributions" + File.separator + "tmp")
    }

}

tasks.register("unzip") {
    val distDir = layout.buildDirectory.asFile.get().path + File.separator + "distributions"

    doLast {
        copy {
            from(layout.projectDirectory.asFile.path + File.separator + "resources" + File.separator + "linux")
            into(distDir + File.separator + "tmp")
        }

        copy{
            from(layout.buildDirectory.asFile.get().path + File.separator + "libs" + File.separator + project.name + "-" + version + "-all.jar")
            into("${distDir}${File.separator}tmp")
            rename { filename: String ->
                println(filename)
                if(filename == "${project.name}-${version}-all.jar" ){
                    "${project.name}.jar"
                }else {
                    filename
                }
            }
        }
    }
}

tasks.register<Tar>("tar-linux"){
    dependsOn("unzip")

    val distDir = layout.buildDirectory.asFile.get().path + File.separator + "distributions"
    val baseDir = File(distDir + File.separator + "tmp" )
    archiveFileName.set("${project.name}-linux.tar.gz")
    compression = Compression.GZIP
    archiveExtension.set("tar.gz")

    from(baseDir) {
        into("/")
    }
}

tasks.getByName("unzip").dependsOn("assembleDist")

dependencies {

    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    /**
     * Http client
     */
    implementation("com.squareup.okhttp3:logging-interceptor:$okHttpVersion")
    implementation("com.squareup.okhttp3:okhttp:$okHttpVersion")
    implementation("com.squareup.okio:okio:$okioVersion")

    /**
     * Command line parsing
     */
    implementation("com.github.ajalt:clikt:$cliktVersion")

    /**
     * Logging services
     */
    implementation("io.github.microutils:kotlin-logging:$kotlinLoggingVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")

    /**
     * Hoplite provides YAML handling
     */
    implementation("com.sksamuel.hoplite:hoplite-core:$hopliteVersion")
    implementation("com.sksamuel.hoplite:hoplite-yaml:$hopliteVersion")

    /**
     * Koin provides dependency injection
     */
    implementation("io.insert-koin:koin-core:3.1.6")

    /**
     * YAML and JSON serialization
     */
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.3.2")

    implementation("com.google.code.gson:gson:$gsonVersion")

    /*
     * Library used for validating host names and IP addresses.
     */
    implementation("com.google.guava:guava:31.0.1-jre")

    /**
     * Daemon interface
     */
    implementation("commons-daemon:commons-daemon:1.2.4")

}

application {
    mainClass.set("io.sharptree.maximo.app.label.ApplicationKt")
    applicationName = "label-print-agent"
}