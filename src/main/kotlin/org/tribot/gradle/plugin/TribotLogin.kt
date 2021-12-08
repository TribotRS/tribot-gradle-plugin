package org.tribot.gradle.plugin

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files

import org.slf4j.LoggerFactory

class TribotLogin {

    private val logger = LoggerFactory.getLogger(TribotLogin::class.java)

    private var cookies: String? = null

    @Synchronized
    fun loginIfNecessary() {
        if (cookies != null) {
            return
        }
        cookies = login()
    }

    fun login() : String {
        // Gradle really doesn't like showing a webview inside the process for some reason - it kept crashing.
        // So we create a new process to isolate it.
        val ui = javaClass.classLoader
                .getResourceAsStream("org/tribot/gradle/plugin/LoginPrompt.class")!!
                .readAllBytes()
        val dir = Files.createTempDirectory("tribot-gradle-plugin").toFile()
        val classDir = dir
                .resolve("org")
                .resolve("tribot")
                .resolve("gradle")
                .resolve("plugin")
        classDir.mkdirs()
        val loginPrompt = classDir.resolve("LoginPrompt.class")
        loginPrompt.createNewFile()
        loginPrompt.writeBytes(ui)
        val openJfx = getTribotDirectory()
                .resolve("install")
                .resolve("openjfx")
                .resolve("javafx-sdk-15" + (if (System.getProperty("sun.arch.data.model") == "32") "-x86" else ""))
                .resolve("lib")
        if (!openJfx.exists()) {
            // We could try to resolve through maven or something...
            throw IllegalStateException("No JavaFX install found. Please run the TRiBot client once to install." +
                    " (java arch: ${System.getProperty("sun.arch.data.model")}")
        }
        val process = ProcessBuilder(
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                "--module-path",
                openJfx.absolutePath,
                "--add-modules=javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.swing,javafx.media,javafx.web",
                "-Dprism.verbose=true",
                "-Djavafx.verbose=true",
                "org.tribot.gradle.plugin.LoginPrompt",
        )
                .directory(dir)
                .redirectErrorStream(true)
                .start()
        try {
            BufferedReader(InputStreamReader(process.inputStream)).use {
                return it.lines()
                        .peek { logger.debug("Output from LoginPrompt: $it") }
                        .filter { it != null }
                        .filter { it.startsWith("Cookies:") }
                        .map { it.substring(7) }
                        .findFirst()
                        .orElseThrow { IllegalStateException("Failed to login") }
            }
        }
        finally {
            process.destroy()
        }
    }

    fun getCookies(): List<Cookie> {
        return cookies?.split(";")?.map { it.split(":") }?.map { Cookie(it[0], it[1]) } ?: listOf()
    }

}

data class Cookie(val name: String, val value: String)