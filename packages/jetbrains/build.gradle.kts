import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.intellij.platform")
}

group = "com.openchamber"
version = properties("pluginVersion") as String

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(properties("platformType") as String, properties("platformVersion") as String)
        bundledPlugins(
            "Git4Idea",
        )
        testFramework(TestFrameworkType.Platform)
    }

    // IntelliJ supplies its own coroutine runtime. Bundling another copy causes
    // ServiceLoader to mix platform coroutine handlers with plugin classes.
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
}

kotlin {
    jvmToolchain((properties("javaVersion") as String).toInt())
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = properties("pluginSinceBuild") as String
            untilBuild = provider { null }
        }
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = properties("javaVersion") as String
        targetCompatibility = properties("javaVersion") as String
    }

    // Copy built webview assets into plugin resources before packaging
    processResources {
        dependsOn("buildWebview")
        from("dist/webview") {
            into("webview")
        }
    }

    register<Exec>("buildWebview") {
        group = "build"
        description = "Build the webview frontend with Vite"
        commandLine("bun", "run", "build:webview")
        workingDir = projectDir
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

fun properties(key: String): Any {
    return project.findProperty(key) ?: error("Property $key not found")
}
