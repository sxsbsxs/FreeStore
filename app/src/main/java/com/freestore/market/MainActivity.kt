package com.freestore.market

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.freestore.market.data.CatalogRepository
import com.freestore.market.data.StoreApp
import com.freestore.market.ui.theme.FreeStoreTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val downloads by lazy { getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }
    private val preferences by lazy { getSharedPreferences("freestore_downloads", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FreeStoreTheme {
                StoreScreen(
                    onDownload = ::downloadApp,
                    onInstall = ::installApp
                )
            }
        }
    }

    private fun downloadApp(app: StoreApp) {
        val url = app.downloadUrl
        if (!url.startsWith("https://")) {
            toast("下载地址不安全或无效")
            return
        }
        try {
            val safeName = app.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { "application" }
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("${app.name} ${app.version}")
                .setDescription("FreeStore APK 下载")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "FreeStore_$safeName-${app.version}.apk"
                )
            val id = downloads.enqueue(request)
            preferences.edit().putLong(app.id, id).apply()
            toast("已开始下载，可在通知栏查看进度")
        } catch (e: Exception) {
            toast("无法开始下载：${e.localizedMessage ?: "未知错误"}")
        }
    }

    private fun installApp(app: StoreApp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            toast("请允许 FreeStore 安装未知应用，然后再次点击安装")
            return
        }

        val id = preferences.getLong(app.id, -1L)
        if (id < 0L) {
            toast("请先下载此应用")
            return
        }

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val query = DownloadManager.Query().setFilterById(id)
                    downloads.query(query).use { cursor ->
                        if (cursor == null || !cursor.moveToFirst()) {
                            error("找不到下载记录，请重新下载")
                        }
                        val status = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        )
                        if (status != DownloadManager.STATUS_SUCCESSFUL) {
                            error(
                                if (status == DownloadManager.STATUS_FAILED) "下载失败，请重新下载"
                                else "文件尚未下载完成"
                            )
                        }
                    }

                    val uri = downloads.getUriForDownloadedFile(id)
                        ?: error("无法读取下载文件")
                    val expected = app.sha256?.removePrefix("sha256:")?.lowercase(Locale.ROOT)
                    if (!expected.isNullOrBlank()) {
                        val actual = contentResolver.openInputStream(uri)?.use { input ->
                            val digest = MessageDigest.getInstance("SHA-256")
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                digest.update(buffer, 0, count)
                            }
                            digest.digest().joinToString("") { "%02x".format(it) }
                        } ?: error("无法校验下载文件")
                        if (!actual.equals(expected, ignoreCase = true)) {
                            error("SHA-256 校验失败，已阻止安装")
                        }
                    }
                    uri
                }
            }

            result.onSuccess { uri ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { startActivity(intent) }
                    .onFailure { toast("无法打开系统安装程序：${it.localizedMessage}") }
            }.onFailure {
                toast(it.message ?: "安装准备失败")
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreScreen(
    onDownload: (StoreApp) -> Unit,
    onInstall: (StoreApp) -> Unit
) {
    var apps by remember { mutableStateOf<List<StoreApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("全部") }
    var selected by remember { mutableStateOf<StoreApp?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching { CatalogRepository().load().apps }
        }
        result.onSuccess { apps = it }
            .onFailure { error = it.message ?: "加载应用目录失败" }
        loading = false
    }

    val categories = remember(apps) {
        listOf("全部") + apps.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val filtered = remember(apps, query, category) {
        apps.filter { app ->
            val matchesCategory = category == "全部" || app.category == category
            val terms = "${app.name} ${app.description} ${app.repository}".lowercase()
            matchesCategory && terms.contains(query.trim().lowercase())
        }.sortedWith(compareByDescending<StoreApp> { it.featured }.thenBy { it.name.lowercase() })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FreeStore", fontWeight = FontWeight.Bold)
                        Text(
                            "自由、开放的应用市场",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val submitUri = Uri.parse("https://sxsbsxs.github.io/FreeStore/?submit=1")
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, submitUri)) }
                            .onFailure { error = "无法打开投稿页面：${it.localizedMessage}" }
                    }) { Text("提交 APK") }
                    TextButton(onClick = { refreshKey++ }) {
                        androidx.compose.material3.Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                leadingIcon = {
                    androidx.compose.material3.Icon(
                        Icons.Default.Search,
                        contentDescription = null
                    )
                },
                placeholder = { Text("搜索应用名称、介绍或仓库") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                items(categories) { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        label = { Text(item) }
                    )
                }
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                error != null -> EmptyState(
                    title = "暂时无法连接应用目录",
                    message = error ?: "请检查网络后重试",
                    action = "重新加载",
                    onAction = { refreshKey++ }
                )
                apps.isEmpty() -> EmptyState(
                    title = "应用市场已就绪",
                    message = "这里还没有上架应用。点击顶部「提交 APK」即可提交应用，审核通过后自动上架；也可通过公开 GitHub Release 收录。",
                    action = "刷新目录",
                    onAction = { refreshKey++ }
                )
                filtered.isEmpty() -> EmptyState(
                    title = "没有找到应用",
                    message = "试试其他关键词或切换分类。",
                    action = "清除搜索",
                    onAction = { query = ""; category = "全部" }
                )
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            if (query.isBlank()) "探索应用" else "搜索结果",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(filtered, key = { it.id }) { app ->
                        AppCard(app = app, onClick = { selected = app })
                    }
                    item {
                        Text(
                            "应用由各自开发者通过 GitHub Releases 发布。安装前请确认来源可信。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }

    selected?.let { app ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = app.iconUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(app.name, fontWeight = FontWeight.Bold)
                        Text(
                            "${app.category} · ${app.version}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(app.description.ifBlank { "暂无应用介绍" })
                    Text(
                        "文件大小：${formatSize(app.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "来源：${app.repository}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!app.changelog.isNullOrBlank()) {
                        Text("更新日志", fontWeight = FontWeight.SemiBold)
                        Text(app.changelog, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "安装 APK 前请确认开发者和应用权限。FreeStore 不对第三方应用作安全背书。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onInstall(app) }) { Text("安装") }
                    Button(onClick = { onDownload(app); selected = null }) {
                        Text("下载 APK")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun AppCard(app: StoreApp, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = app.iconUrl,
                contentDescription = "${app.name} 图标",
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.name,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (app.featured) {
                        Text(
                            "精选",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Text(
                    app.description.ifBlank { "暂无简介" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${app.category} · ${app.version} · ${formatSize(app.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    message: String,
    action: String,
    onAction: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Button(onClick = onAction) { Text(action) }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "未知大小"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
}
