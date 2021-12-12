package org.tribot.gradle.plugin

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.Buffer
import java.util.*


class TribotRepository {

    companion object {
        val instance: TribotRepository by lazy { TribotRepository() }
    }

    private val logger: Logger = LoggerFactory.getLogger(TribotRepository::class.java)

    private val login: TribotLogin = TribotLogin()
    private val cl = OkHttpClient.Builder().addInterceptor {
        login.loginIfNecessary()
        val original = it.request()
        val authorizedBuilder = original.newBuilder()
        login.getCookies().forEach {
            authorizedBuilder.addHeader("Cookie", "${it.name}=${it.value}")
        }
        applyCustomHeaders(original, authorizedBuilder)
        val response = it.proceed(authorizedBuilder.build())
        if (response.code == 404) {
            logger.warn("Received 404 response code from repository, resetting login info (url=${original.url}")
            login.reset()
        }
        return@addInterceptor response
    }.addNetworkInterceptor {
        logger.debug("Request headers: ${it.request().headers}")
        val response = it.proceed(it.request())
        logger.debug("Response headers: ${response.headers}")
        return@addNetworkInterceptor response
    }.build()

    private fun applyCustomHeaders(original: Request, authorizedBuilder: Request.Builder) {
        authorizedBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/55.0.2883.28 Safari/537.36")
        authorizedBuilder.header("Connection", "Keep-Alive")
        authorizedBuilder.header("Origin", "https://repo.tribot.org")
        authorizedBuilder.header("Upgrade-Insecure-Requests", "1")
        authorizedBuilder.header("Accept", "\"text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\"")
        authorizedBuilder.header("Accept-Language", "en-US,en;q=0.8")
        authorizedBuilder.header("Cache-Control", "max-age=0")
        if (original.method.lowercase() == "post") {
            authorizedBuilder.header("Referer", original.url.toString())
        }
    }

    fun getScripts(): List<RepoScript> {
        return retry {
            val scriptData = load("https://repo.tribot.org/data/scripter_panel/published_scripts?_=" + System.currentTimeMillis())
            logger.debug("getScripts response: $scriptData")
            val scripts = Gson().fromJson(scriptData, RepoScripts::class.java)
            scripts.scripts
        }
    }

    fun update(id: String, version: String, script: File) {
        retry {
            val requestBody: RequestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("version", version)
                    .addFormDataPart("file", script.name, script.asRequestBody("application/zip".toMediaType()))
                    .build()
            val request = Request.Builder()
                    .post(requestBody)
                    .url("https://repo.tribot.org/script/edit/$id/source/")
                    .build()
            logger.debug("Updating script id: $id version $version filepath ${script.absolutePath}")
            cl.newCall(request).execute().use {
                if (it.code != 200) {
                    throw IllegalStateException("Failed to update script $id, response code: ${it.code}, response body: ${it.body?.string()}")
                }
            }
            logger.debug("Sent update request")
        }
    }

    private fun load(url: String): String {
        val request = Request.Builder().get().url(url).build()
        return cl.newCall(request).execute().use {
            it.body?.string() ?: throw IllegalStateException("No response body for $url")
        }
    }

    private fun <T> retry(block: () -> T) : T {
        var count = 0
        while (true) {
            try {
                return block()
            }
            catch (e: Exception) {
                count++
                if (count > 1) {
                    throw e
                }
                logger.debug("Failed retry attempt $count", e)
            }
        }
    }

}

data class RepoScripts(@SerializedName("aaData")
                       val scripts: List<RepoScript>)

data class RepoScript(val id: String, val name: String, val version: String)