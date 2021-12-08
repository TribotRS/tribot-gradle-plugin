package org.tribot.gradle.plugin

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File


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
        authorizedBuilder.header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36")
        authorizedBuilder.header("Connection", "Keep-Alive")
        authorizedBuilder.header("Origion", "https://repo.tribot.org")
        authorizedBuilder.header("upgrade-insecure-requests", "1")
        authorizedBuilder.header("sec-ch-ua", "\" Not A;Brand\";v=\"99\", \"Chromium\";v=\"96\", \"Google Chrome\";v=\"96\"")
        authorizedBuilder.header("sec-ch-ua-mobile", "?0")
        authorizedBuilder.header("sec-ch-ua-platform", "\"Windows\"")
        authorizedBuilder.header("sec-fetch-dest", "document")
        authorizedBuilder.header("sec-fetch-mode", "navigate")
        authorizedBuilder.header("sec-fetch-site", "same-origin")
        authorizedBuilder.header("sec-fetch-user", "?1")
        val response = it.proceed(authorizedBuilder.build())
        if (response.code == 404) {
            login.reset()
        }
        return@addInterceptor response
    }.build()

    fun getScripts(): List<RepoScript> {
        val scriptData = load("https://repo.tribot.org/data/scripter_panel/published_scripts?_=" + System.currentTimeMillis())
        logger.debug("getScripts response: $scriptData")
        val scripts = Gson().fromJson(scriptData, RepoScripts::class.java)
        return scripts.scripts
    }

    fun update(id: String, version: String, script: File) {
        val requestBody: RequestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", script.name, script.asRequestBody())
                .addFormDataPart("version", version)
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

    private fun load(url: String): String {
        val request = Request.Builder().get().url(url).build()
        return cl.newCall(request).execute().use {
            it.body?.string() ?: throw IllegalStateException("No response body for $url")
        }
    }

}

data class RepoScripts(@SerializedName("aaData")
                       val scripts: List<RepoScript>)

data class RepoScript(val id: String, val name: String, val version: String)