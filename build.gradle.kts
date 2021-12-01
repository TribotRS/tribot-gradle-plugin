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
        create("TribotPlugin") {
            id = "org.tribot.tribot-gradle-plugin"
            implementationClass = "org.tribot.gradle.plugin.TribotPlugin"
        }
    }
}


publishing {
    plugins.withType()
    publications {
        val mavenJava by publications.creating(MavenPublication::class) {
            from(components.getByName("java"))
            pom {
                artifactId = "tribot-gradle-plugin"
                name.set("Tribot Scripting Gradle Plugin")
                description.set("Tribot's official scripting grade plugin")
                url.set("https://tribot.org/")
                licenses {
                    license {
                        name.set("TRiBot End-User License Agreement")
                        url.set("https://tribot.org/pages/eula")
                    }
                }
            }
        }
    }
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