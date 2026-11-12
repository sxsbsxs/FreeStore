package com.freestore.market.data

import com.google.gson.annotations.SerializedName

data class AppCatalog(
    @SerializedName("schema_version") val schemaVersion: Int = 1,
    @SerializedName("updated_at") val updatedAt: String? = null,
    val apps: List<StoreApp> = emptyList()
)

data class StoreApp(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "其他",
    val version: String = "",
    @SerializedName("package_name") val packageName: String? = null,
    val repository: String = "",
    @SerializedName("repository_url") val repositoryUrl: String = "",
    @SerializedName("release_url") val releaseUrl: String = "",
    @SerializedName("download_url") val downloadUrl: String = "",
    @SerializedName("icon_url") val iconUrl: String? = null,
    val screenshots: List<String> = emptyList(),
    @SerializedName("size_bytes") val sizeBytes: Long = 0L,
    val sha256: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("download_count") val downloadCount: Long = 0L,
    val featured: Boolean = false,
    val changelog: String? = null
)
