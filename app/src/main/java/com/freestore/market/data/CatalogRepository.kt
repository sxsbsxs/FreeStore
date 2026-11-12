package com.freestore.market.data

import com.freestore.market.BuildConfig
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class CatalogRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    fun load(): AppCatalog {
        val request = Request.Builder()
            .url(BuildConfig.CATALOG_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "FreeStore-Android")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("目录加载失败：HTTP ${response.code}")
            }
            val body = response.body?.string()
                ?: throw IOException("服务器返回了空目录")
            val catalog = Gson().fromJson(body, AppCatalog::class.java)
                ?: throw IOException("目录格式无效")
            if (catalog.schemaVersion != 1) {
                throw IOException("暂不支持目录版本：${catalog.schemaVersion}")
            }
            return catalog.copy(apps = catalog.apps.filter {
                it.name.isNotBlank() && it.downloadUrl.startsWith("https://")
            })
        }
    }
}
