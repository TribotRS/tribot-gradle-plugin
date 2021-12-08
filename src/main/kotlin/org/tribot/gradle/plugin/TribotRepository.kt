package org.tribot.gradle.plugin

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.*
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
        val original = it.request()
        val authorizedBuilder = original.newBuilder()
        login.getCookies().forEach {
            authorizedBuilder.addHeader("Cookie", "${it.name}=${it.value}")
        }
        return@addInterceptor it.proceed(authorizedBuilder.build())
    }.build()

    fun getScripts(): List<RepoScript> {
        val scriptData = load("https://repo.tribot.org/data/scripter_panel/published_scripts?_=" + System.currentTimeMillis())
        logger.debug("getScripts response: $scriptData")
        val scripts = Gson().fromJson(scriptData, RepoScripts::class.java)
        return scripts.scripts
    }

    fun update(id: String, version: String, script: File) {
        login.loginIfNecessary()
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
        login.loginIfNecessary()
        val request = Request.Builder().get().url(url).build()
        return cl.newCall(request).execute().use {
            it.body?.string() ?: throw IllegalStateException("No response body for $url")
        }
    }

}

data class RepoScripts(@SerializedName("aaData")
                       val scripts: List<RepoScript>)

data class RepoScript(val id: String, val name: String, val version: String)