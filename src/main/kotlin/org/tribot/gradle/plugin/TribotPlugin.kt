package org.tribot.gradle.plugin

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.openjfx.gradle.JavaFXOptions
import java.io.File

class TribotPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        if (project.parent == null) {
            applyRoot(project)
        }
        else {
            applyScript(project)
        }
    }

    private fun applyRoot(project: Project) {

        val outputDir = getTribotDirectory().resolve("bin")

        project.allprojects.forEach {

            it.pluginManager.apply("java")
            it.pluginManager.apply("kotlin")

            it.extensions.configure(JavaPluginExtension::class.java) {
                it.sourceCompatibility = JavaVersion.VERSION_11
                it.targetCompatibility = JavaVersion.VERSION_11
            }

            it.repositories.add(it.repositories.mavenCentral())
            it.repositories.add(it.repositories.maven {
                it.setUrl("https://gitlab.com/api/v4/projects/20741387/packages/maven")
            })
            it.repositories.add(it.repositories.google())
            it.repositories.add(it.repositories.maven {
                it.setUrl("https://maven.pkg.jetbrains.space/public/p/compose/dev")
            })

            it.dependencies.add("api", "org.tribot:tribot-script-sdk:+")

            it.dependencies.add("api", it.files("${it.projectDir.absolutePath}/allatori-annotations-7.5.jar"))
            getTribotDirectory().resolve("thirdparty").listFiles()?.forEach { f ->
                it.dependencies.add("api", it.file(f))
            }

            val sourceSets = it.properties["sourceSets"] as SourceSetContainer
            val main = sourceSets.getByName("main")

            main.java.setSrcDirs(listOf("src"))
            main.resources.setSrcDirs(listOf("src"))
            main.resources.exclude("**/*.java", "**/*.kt")

            it.tasks.getAt("clean").doFirst {
                outputDir.listFiles()?.forEach { it.deleteRecursively() }
            }

            // Gradle doesn't like multiple projects pointing to the same output location, so copy the files we need
            it.tasks.getAt("classes").doLast { _ ->
                val copy = {f : File ->
                    f.walkTopDown()
                    .filter { it.isDirectory && it.absolutePath.endsWith("scripts") }
                    .forEach {
                        it.walkTopDown().filter { !it.isDirectory }.forEach { classFile ->
                            val outputFile = File(classFile.absolutePath.replace(it.absolutePath, outputDir.absolutePath))
                            classFile.copyTo(outputFile, true)
                        }
                    }
                }
                copy(it.buildDir.resolve("classes"))
                copy(it.buildDir.resolve("resources"))
            }

            val kotlinCompile = it.tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java)
            kotlinCompile.forEach {
                it.kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
            }

            it.configurations.all {
                // Ensures that our dependencies will update timely
                it.resolutionStrategy.cacheDynamicVersionsFor(5, "minutes")
                it.resolutionStrategy.cacheChangingModulesFor(5, "minutes")
            }
        }

        project.pluginManager.apply("org.openjfx.javafxplugin")
        project.extensions.configure<JavaFXOptions>("javafx") {
            it.version = "15"
            it.modules = listOf("javafx.controls", "javafx.fxml",
                    "javafx.graphics", "javafx.media", "javafx.swing", "javafx.web")
        }

        project.tasks.create("runTribotWithDebugger") { task ->
            task.group = "tribot"

            task.doLast {
                val splash = TribotSplash()
                splash.ensureUpdated()
                splash.filePath
                ProcessBuilder(
                        System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                        "-jar",
                        splash.filePath,
                        "--remote-debugger",
                        "--debug")
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start()
                        .waitFor()
            }
        }

        project.tasks.create("repoCopy") { task ->
            task.group = "tribot"
            project.subprojects.forEach{
                it.tasks.getByName("build").let { task.dependsOn(it) }
            }
            task.doLast {
                val repoDeployDir = task.project.projectDir.resolve("build/repo-deploy").also { it.mkdirs() }
                task.project.allprojects
                        .filter { it.path != project.path }
                        .map { it.projectDir.resolve("build/repo-deploy") }
                        .filter { it.exists() }
                        .mapNotNull { it.listFiles()?.getOrNull(0) }
                        .forEach { it.copyTo(repoDeployDir.resolve(it.name), overwrite = true) }
            }
        }
    }

    private fun applyScript(project: Project) {
        project.tasks.create("repoPackage") { task ->
            task.group = "tribot"
            task.dependsOn(project.tasks.getByName("assemble"))
            task.doLast {

                val projectDir = project.projectDir
                val dirsToPackage = mutableListOf(projectDir.resolve("src"))

                fun getDependenciesRecursive(config: Configuration?, dirs: MutableList<File>): Unit? =
                        config?.dependencies
                                ?.mapNotNull { it as? DefaultProjectDependency }
                                ?.forEach { d ->
                                    dirs += d.dependencyProject.projectDir.resolve("src")
                                    getDependenciesRecursive(d.dependencyProject.configurations.asMap["implementation"], dirs)
                                }

                getDependenciesRecursive(project.configurations.asMap["implementation"], dirsToPackage)

                val zipFile = project.buildDir
                        .resolve("repo-deploy")
                        .also { it.mkdirs() }
                        .resolve("${project.name}.zip")

                if (zipFile.exists())
                    zipFile.delete()

                ZipFile(zipFile).also { zip ->
                    dirsToPackage.filter { it.exists() }.distinctBy { it.canonicalPath }.forEach { srcDir ->
                        srcDir.listFiles()?.forEach {
                            if (it.isFile)
                                zip.addFile(it, ZipParameters().apply { isOverrideExistingFilesInZip = false })
                            else
                                zip.addFolder(it, ZipParameters().apply { isOverrideExistingFilesInZip = false })
                        }
                    }
                }
            }
        }
    }

}