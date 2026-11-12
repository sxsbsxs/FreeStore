# 添加应用到 FreeStore

FreeStore 不接收 APK 上传到自己的服务器。每个应用由开发者放在自己的**公开 GitHub 仓库**，通过 GitHub Releases 分发。

## 1. 把项目加入目录来源

在本仓库编辑 `catalog/sources.json`，添加仓库：

```json
{
  "schema_version": 1,
  "repositories": [
    {
      "repository": "owner/project",
      "category": "工具",
      "featured": false
    }
  ]
}
```

`repository` 必须是公开 GitHub 仓库的 `owner/name`。分类和精选标记是可选项。

## 2. 在应用仓库发布 APK

在应用自己的 GitHub 仓库创建一个**正式 Release**（不能是 Draft 或 Pre-release），并将可安装 APK 作为 Release asset 上传。建议每个 Release 提供一个通用 APK；若有多个 APK，目录生成器优先选择 universal，其次 arm64，再按文件名排序。

## 3. 可选：提供应用元数据

在应用仓库根目录添加 `freestore.json`：

```json
{
  "name": "应用名称",
  "description": "应用简介",
  "category": "工具",
  "package_name": "com.example.app",
  "icon_url": "https://example.com/icon.png",
  "screenshots": [],
  "featured": false
}
```

所有字段均可省略。没有清单时，FreeStore 使用 GitHub 仓库名称、简介和仓库所有者头像作为默认信息。

## 4. 自动收录

保存 `catalog/sources.json` 后，GitHub Actions 会自动运行目录生成器。此后每 6 小时检查一次最新正式 Release。你也可以在仓库的 **Actions → Refresh app catalog → Run workflow** 手动刷新。

只有找到 APK asset 的仓库才会进入目录。Release 的下载链接直接指向 GitHub，不经过 FreeStore 中转。

## 注意事项

- 请只添加你有权分发、且来源可信的应用。
- 上传者应提供合法签名的 APK；同一应用后续更新必须使用兼容的签名证书。
- FreeStore 显示开发者声明的介绍，不代表对第三方 APK 的安全审计或背书。
- GitHub API 有速率限制。仓库列表规模较大时，应减少来源数量或降低刷新频率。
