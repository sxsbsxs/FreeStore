#!/usr/bin/env python3
"""Publish a moderator-approved FreeStore APK issue as a GitHub Release asset."""
from __future__ import annotations
import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.parse
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
COMMUNITY_FILE = ROOT / "catalog" / "community-apps.json"
MAX_BYTES = 25 * 1000 * 1000

def fail(message: str) -> None:
    raise RuntimeError(message)

def run(args: list[str], *, capture: bool = False) -> str:
    result = subprocess.run(args, check=True, text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None)
    return result.stdout.strip() if capture else ""

def field(body: str, key: str) -> str:
    pattern = rf"<!--\s*FREESTORE:{re.escape(key)}:START\s*-->(.*?)<!--\s*FREESTORE:{re.escape(key)}:END\s*-->"
    matches = re.findall(pattern, body, flags=re.IGNORECASE | re.DOTALL)
    if len(matches) != 1:
        fail(f"投稿资料缺少字段或字段重复：{key}")
    return matches[0].strip()

def validate_attachment(body: str) -> str:
    urls = re.findall(r"https://github\.com/user-attachments/(?:files|assets)/[^\s)<>]+", body)
    urls = [url.rstrip(".,;!?，。；！）】") for url in urls]
    if len(urls) != 1:
        fail("请在投稿正文中附加且只附加一个 APK 文件（请上传网页下载的 zip 文件）。")
    parsed = urllib.parse.urlparse(urls[0])
    if parsed.scheme != "https" or parsed.hostname != "github.com":
        fail("附件地址不是受支持的 GitHub 附件链接。")
    return urls[0]

def validate_apk(path: Path) -> None:
    if not path.exists() or path.stat().st_size <= 0:
        fail("附件下载为空。")
    if path.stat().st_size > MAX_BYTES:
        fail("APK 超过 GitHub 公共 Issue 附件的 25 MiB 限制。")
    if not zipfile.is_zipfile(path):
        fail("附件不是有效的 APK 文件。请不要把 APK 再次压缩，只需按网页提示将文件名改为 zip。")
    try:
        with zipfile.ZipFile(path) as archive:
            names = set(archive.namelist())
            if "AndroidManifest.xml" not in names:
                fail("附件中没有 AndroidManifest.xml，无法识别为 Android APK。")
            bad = archive.testzip()
            if bad:
                fail(f"APK 压缩数据校验失败：{bad}")
    except zipfile.BadZipFile as exc:
        fail(f"APK 文件损坏：{exc}")

def main() -> int:
    event_path = Path(os.environ["GITHUB_EVENT_PATH"])
    event = json.loads(event_path.read_text(encoding="utf-8"))
    issue = event.get("issue") or {}
    issue_number = int(issue.get("number") or 0)
    issue_title = str(issue.get("title") or "")
    issue_body = str(issue.get("body") or "")
    if issue_number <= 0 or not issue_title.startswith("[FreeStore APK]"):
        fail("这不是有效的 FreeStore APK 投稿 Issue。")

    name = field(issue_body, "name")
    version = field(issue_body, "version")
    package = field(issue_body, "package")
    category = field(issue_body, "category")
    description = field(issue_body, "description")
    project_url = field(issue_body, "project_url")
    if not name or len(name) > 80 or "\n" in name:
        fail("应用名称不能为空，且最多 80 个字符。")
    if not re.fullmatch(r"[0-9A-Za-z][0-9A-Za-z.+_-]{0,63}", version):
        fail("版本号格式不正确。请使用例如 1.0.0、2.1.0-beta1。")
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*){1,8}", package):
        fail("Android 包名格式不正确。")
    if not category or len(category) > 30 or "\n" in category:
        fail("应用分类不能为空，且最多 30 个字符。")
    if not description or len(description) > 1000:
        fail("应用简介不能为空，且最多 1000 个字符。")
    if project_url and project_url != "无":
        parsed_project = urllib.parse.urlparse(project_url)
        if parsed_project.scheme != "https" or not parsed_project.hostname or parsed_project.username or parsed_project.password:
            fail("项目主页必须是有效的 HTTPS 地址。")
    else:
        project_url = ""

    attachment_url = validate_attachment(issue_body)
    download_path = ROOT / "submission-upload.zip"
    apk_path = ROOT / "submission.apk"
    subprocess.run([
        "curl", "--fail", "--location", "--silent", "--show-error",
        "--max-filesize", str(MAX_BYTES), "--proto", "=https",
        "--proto-redir", "=https", attachment_url, "--output", str(download_path)
    ], check=True)
    validate_apk(download_path)
    download_path.replace(apk_path)

    digest = hashlib.sha256(apk_path.read_bytes()).hexdigest()
    size_bytes = apk_path.stat().st_size
    package_slug = re.sub(r"[^a-z0-9]+", "-", package.lower()).strip("-")[:70]
    asset_name = f"{package_slug}-{version}.apk"
    tag = f"community-submission-{issue_number}"
    repo = os.environ["GITHUB_REPOSITORY"]
    release_title = f"{name} {version}"
    issue_url = str(issue.get("html_url") or f"https://github.com/{repo}/issues/{issue_number}")
    notes = (
        f"Community APK submission reviewed by FreeStore maintainers.\n\n"
        f"- App: {name}\n- Version: {version}\n- Package: {package}\n"
        f"- SHA-256: {digest}\n- Submission: {issue_url}\n"
        + (f"- Project: {project_url}\n" if project_url else "")
    )

    existing = subprocess.run(["gh", "release", "view", tag, "--repo", repo],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    upload_arg = f"{apk_path}#{asset_name}"
    if existing.returncode == 0:
        run(["gh", "release", "upload", tag, upload_arg, "--clobber", "--repo", repo])
        run(["gh", "release", "edit", tag, "--title", release_title, "--notes", notes, "--repo", repo])
    else:
        run(["gh", "release", "create", tag, upload_arg, "--title", release_title,
             "--notes", notes, "--repo", repo])

    release_data = json.loads(run(["gh", "api", f"repos/{repo}/releases/tags/{tag}"], capture=True))
    release_url = str(release_data.get("html_url") or f"https://github.com/{repo}/releases/tag/{tag}")
    asset = next((item for item in release_data.get("assets", []) if item.get("name") == asset_name), None)
    if not asset or not str(asset.get("browser_download_url") or "").startswith("https://"):
        fail("Release 已创建，但没有找到可公开下载的 APK 资产。")
    download_url = asset["browser_download_url"]

    project_repo = "FreeStore 投稿"
    if project_url:
        parsed = urllib.parse.urlparse(project_url)
        if parsed.hostname and parsed.hostname.lower() == "github.com":
            parts = [part for part in parsed.path.split("/") if part]
            if len(parts) >= 2:
                project_repo = f"{parts[0]}/{parts[1]}"

    COMMUNITY_FILE.parent.mkdir(parents=True, exist_ok=True)
    community = json.loads(COMMUNITY_FILE.read_text(encoding="utf-8")) if COMMUNITY_FILE.exists() else {"apps": []}
    if not isinstance(community.get("apps"), list):
        fail("catalog/community-apps.json 格式错误。")
    record: dict[str, Any] = {
        "id": f"community-{package_slug}",
        "name": name,
        "description": description,
        "category": category,
        "version": version,
        "package_name": package,
        "repository": project_repo,
        "repository_url": project_url or issue_url,
        "release_url": release_url,
        "download_url": download_url,
        "icon_url": "",
        "screenshots": [],
        "size_bytes": size_bytes,
        "sha256": digest,
        "updated_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "download_count": int(asset.get("download_count") or 0),
        "featured": False,
        "changelog": f"经审核的社区投稿。投稿编号：#{issue_number}",
    }
    community["schema_version"] = 1
    community["apps"] = [
        item for item in community["apps"]
        if not (isinstance(item, dict) and str(item.get("package_name") or "").lower() == package.lower())
    ]
    community["apps"].append(record)
    COMMUNITY_FILE.write_text(json.dumps(community, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    run([sys.executable, "scripts/build_catalog.py"])
    run([sys.executable, "scripts/merge_community_catalog.py"])
    run(["git", "config", "user.name", "github-actions[bot]"])
    run(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"])
    run(["git", "add", "catalog/community-apps.json", "catalog/apps.json", "website/catalog/apps.json"])
    staged = subprocess.run(["git", "diff", "--cached", "--quiet"])
    if staged.returncode != 0:
        run(["git", "commit", "-m", f"catalog: publish community APK submission #{issue_number}"])
        run(["git", "push"])

    branch = os.environ.get("GITHUB_REF_NAME", "main")
    run(["gh", "workflow", "run", "pages.yml", "--ref", branch])
    run(["gh", "issue", "comment", str(issue_number), "--repo", repo,
         "--body", f"✅ 已审核并发布 **{name} {version}**。\n\n[下载 APK]({download_url}) · [查看 Release]({release_url})"])
    run(["gh", "issue", "edit", str(issue_number), "--repo", repo,
         "--add-label", "published", "--remove-label", "pending-review", "--remove-label", "approved"])
    run(["gh", "issue", "close", str(issue_number), "--repo", repo])
    print(f"Published {name} {version}: {download_url}")
    return 0

if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"Submission publishing failed: {exc}", file=sys.stderr)
        raise
