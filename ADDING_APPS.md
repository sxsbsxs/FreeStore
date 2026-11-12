# 添加应用到 FreeStore

FreeStore 支持两种上架方式：网页直接投稿（适合 25 MB 以内的 APK）和开发者 GitHub Release 收录（适合大文件或已有项目仓库的应用）。

## 方式一：从网页提交 APK

1. 打开 FreeStore 网页，点击右上角「提交应用」。
2. 填写名称、版本、Android 包名、分类和简介，选择 APK 文件。
3. 点击「继续提交」。浏览器会下载一个名为 freestore-apk-submission.zip 的副本，并打开预填好的 GitHub 投稿页面。
4. 在 GitHub 投稿页面点击附件按钮，选择刚刚下载的 ZIP 文件，然后创建 Issue。**不要解压或再次压缩**；该文件里的字节仍是原始 APK，只更改了文件名以匹配 GitHub 支持的附件类型。
5. 投稿进入审核队列。维护者检查应用资料和 APK 后，会给 Issue 添加 approved 标签。GitHub Actions 随后自动验证 APK、创建 FreeStore Release、更新应用目录，并触发网页部署。

**限制：** 用户需要登录 GitHub 才能提交 Issue 和附件。GitHub 免费公共仓库的 Issue 附件单个文件上限为 25 MB。FreeStore 的静态网页不能在没有服务器或用户授权凭证的情况下，直接把任意大小文件写入仓库；因此上传最后一步由 GitHub 自己完成。管理员 Token 不会暴露在网页中。

## 方式二：通过自己的 GitHub Release 收录（适合大 APK）

### 1. 把项目加入目录来源

在本仓库编辑 catalog/sources.json，添加仓库：

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

repository 必须是公开 GitHub 仓库的 owner/name。分类和精选标记是可选项。

### 2. 在应用仓库发布 APK

在应用自己的 GitHub 仓库创建一个**正式 Release**（不能是 Draft 或 Pre-release），并将可安装 APK 作为 Release asset 上传。建议每个 Release 提供一个通用 APK；若有多个 APK，目录生成器优先选择 universal，其次 arm64，再按文件名排序。

### 3. 可选：提供应用元数据

在应用仓库根目录添加 freestore.json：

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

没有清单时，FreeStore 使用 GitHub 仓库名称、简介和仓库所有者头像作为默认信息。

### 4. 自动收录

保存 catalog/sources.json 后，GitHub Actions 会自动运行目录生成器。此后每 6 小时检查一次最新正式 Release。你也可以在仓库的 **Actions → Refresh app catalog → Run workflow** 手动刷新。

只有找到 APK asset 的仓库才会进入目录。Release 的下载链接直接指向 GitHub，不经过 FreeStore 中转。

## 审核与安全

- 投稿 Issue 公开后，附件可被公开访问；请勿上传私密或未经授权分发的 APK。
- 维护者应检查应用来源、许可、包名、版本和 APK 签名后再批准。
- 自动流程检查 APK 结构、Android Manifest 包名和版本、APK 签名完整性，并计算 SHA-256；这不是恶意软件扫描或安全审计。
- 上传者应提供合法签名的 APK；同一应用后续更新必须使用兼容的签名证书。
- GitHub API、Release、Actions 和 Pages 仍受平台规则与限额约束。
