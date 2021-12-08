package org.tribot.gradle.plugin

import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.security.MessageDigest
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class TribotLogin {

    private val pattern = Pattern.compile("SaveLogin:(?<saveLogin>true|false),Cookies:(?<cookies>.*)")

    private val logger = LoggerFactory.getLogger(TribotLogin::class.java)
    private val cacheFile = getTribotDirectory().resolve("settings").resolve("repo.dat")

    private var cookies: String? = null

    @Synchronized
    fun loginIfNecessary() {
        if (cookies != null) {
            return
        }
        cookies = getLoginCookies()
    }

    @Synchronized
    fun reset() {
        cookies = null
        cacheFile.delete()
    }

    private fun getLoginCookies() : String {
        return cacheFile.takeIf { it.exists() }
                ?.let { readCacheFile() }
                ?: login()
    }

    private fun readCacheFile() : String? {
        return try {
            getMacAddress()
                    ?.let {
                        val bytes = cacheFile.readBytes()
                        decrypt(bytes, it)
                    }
        }
        catch  (e: Exception) {
            logger.debug("Failed to read cache file", e)
            null
        }
    }



    private fun getCipher(key: String, encryptionMode: Boolean): Cipher {
        val digest = MessageDigest.getInstance("SHA")
        digest.update(key.toByteArray())
        val skeySpec = SecretKeySpec(digest.digest(), 0, 16, "AES")
        val cipher = Cipher.getInstance("AES")
        if (encryptionMode) {
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec)
        }
        else {
            cipher.init(Cipher.DECRYPT_MODE, skeySpec)
        }
        return cipher
    }

    private fun encrypt(text: String, key: String): ByteArray {
        val cipher: Cipher = getCipher(key, true)
        return cipher.doFinal(text.toByteArray())
    }

    private fun decrypt(textBytes: ByteArray, key: String): String {
        val cipher: Cipher = getCipher(key, false)
        return String(cipher.doFinal(textBytes))
    }

    private fun login() : String {
        val loginResponse = doLogin()
        if (loginResponse.saveLogin) {
            try {
                getMacAddress()?.let {
                    cacheFile.parentFile.mkdirs()
                    cacheFile.createNewFile()
                    val encrypted = encrypt(loginResponse.cookies, it)
                    cacheFile.writeBytes(encrypted)
                }
            }
            catch (e: Exception) {
                logger.debug("Failed to save login", e)
            }
        }
        return loginResponse.cookies
    }

    private fun doLogin() : LoginResponse {
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
                        .filter { it.startsWith("SaveLogin:") }
                        .map {
                            val matcher = pattern.matcher(it)
                            if (!matcher.matches()) {
                                throw IllegalStateException("Didn't match $it")
                            }
                            val saveLogin = matcher.group("saveLogin").toBoolean()
                            val cookies = matcher.group("cookies")
                            LoginResponse(cookies, saveLogin)
                        }
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

data class LoginResponse(val cookies: String, val saveLogin: Boolean)