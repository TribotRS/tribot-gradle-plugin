import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.31"
    `java-gradle-plugin`
    `maven-publish`
}

group = "org.tribot"
version = project.version

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("net.lingala.zip4j:zip4j:2.6.4")
    implementation("com.google.code.gson:gson:2.8.9")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.10")
    implementation("org.openjfx.javafxplugin:org.openjfx.javafxplugin.gradle.plugin:0.0.10")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = org.gradle.api.JavaVersion.VERSION_11.toString()
}

gradlePlugin {
    plugins {
        create("Tribot Plugin") {
            id = "tribot-gradle-plugin"
            displayName = "Tribot Scripting Gradle Plugin"
            implementationClass = "org.tribot.gradle.plugin.TribotPlugin"
            description = ("Tribot's official scripting grade plugin")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitLab"
            url = uri("https://gitlab.com/api/v4/projects/20741387/packages/maven")
            credentials(HttpHeaderCredentials::class.java) {
                name = "Deploy-Token"
                value = if (project.hasProperty("tribotDeployToken"))
                    project.property("tribotDeployToken") as String
                else ""
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
}