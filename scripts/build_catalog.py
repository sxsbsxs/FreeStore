#!/usr/bin/env python3
"""Build FreeStore's public catalog from an explicit list of GitHub repositories.

Only public GitHub Releases with at least one APK asset are listed. No server,
database, third-party package or paid API is used.
"""
from __future__ import annotations

import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "catalog" / "sources.json"
OUTPUTS = [
    ROOT / "catalog" / "apps.json",
    ROOT / "website" / "catalog" / "apps.json",
]
API = "https://api.github.com"
TOKEN = os.environ.get("GITHUB_TOKEN", "").strip()


def request_json(url: str, *, optional: bool = False) -> Any:
    headers = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "FreeStore-Catalog-Bot",
    }
    if TOKEN:
        headers["Authorization"] = f"Bearer {TOKEN}"
    request = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        if optional and exc.code in (404, 410):
            return None
        raise RuntimeError(f"GitHub API returned HTTP {exc.code} for {url}") from exc
    except (urllib.error.URLError, TimeoutError) as exc:
        raise RuntimeError(f"Could not reach GitHub for {url}: {exc}") from exc


def read_manifest(owner: str, repo: str, branch: str) -> dict[str, Any]:
    url = f"https://raw.githubusercontent.com/{owner}/{repo}/{branch}/freestore.json"
    request = urllib.request.Request(url, headers={"User-Agent": "FreeStore-Catalog-Bot"})
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            data = json.loads(response.read().decode("utf-8"))
            return data if isinstance(data, dict) else {}
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, json.JSONDecodeError):
        return {}


def main() -> int:
    config = json.loads(SOURCES.read_text(encoding="utf-8"))
    entries = config.get("repositories", [])
    if not isinstance(entries, list):
        raise RuntimeError("catalog/sources.json: repositories must be an array")

    apps: list[dict[str, Any]] = []
    seen: set[str] = set()

    for entry in entries:
        if isinstance(entry, str):
            full_name = entry.strip()
            defaults: dict[str, Any] = {}
        elif isinstance(entry, dict):
            full_name = str(entry.get("repository", "")).strip()
            defaults = entry
        else:
            print(f"Skipping invalid source entry: {entry!r}", file=sys.stderr)
            continue

        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", full_name):
            print(f"Skipping invalid repository name: {full_name!r}", file=sys.stderr)
            continue
        normalized = full_name.lower()
        if normalized in seen:
            continue
        seen.add(normalized)

        repo_data = request_json(f"{API}/repos/{full_name}", optional=True)
        if not repo_data or repo_data.get("private"):
            print(f"Skipping unavailable or private repository: {full_name}")
            continue

        release = request_json(f"{API}/repos/{full_name}/releases/latest", optional=True)
        if not release or release.get("draft") or release.get("prerelease"):
            print(f"No published stable release: {full_name}")
            continue

        assets = [
            item for item in release.get("assets", [])
            if str(item.get("name", "")).lower().endswith(".apk")
            and str(item.get("browser_download_url", "")).startswith("https://")
        ]
        if not assets:
            print(f"No APK asset in latest release: {full_name}")
            continue

        # Prefer a universal APK when a release contains multiple variants.
        assets.sort(key=lambda item: (
            0 if "universal" in item.get("name", "").lower() else 1,
            0 if "arm64" in item.get("name", "").lower() else 1,
            item.get("name", "").lower(),
        ))
        asset = assets[0]
        owner_name, repo_name = full_name.split("/", 1)
        manifest = read_manifest(owner_name, repo_name, repo_data.get("default_branch") or "main")

        category = manifest.get("category") or defaults.get("category") or "其他"
        digest = str(asset.get("digest") or "")
        if digest.startswith("sha256:"):
            digest = digest.removeprefix("sha256:")
        else:
            digest = ""

        app = {
            "id": normalized.replace("/", "-"),
            "name": manifest.get("name") or repo_data.get("name") or repo_name,
            "description": manifest.get("description") or repo_data.get("description") or "暂无应用介绍",
            "category": category,
            "version": release.get("tag_name") or "latest",
            "package_name": manifest.get("package_name") or "",
            "repository": full_name,
            "repository_url": repo_data.get("html_url", f"https://github.com/{full_name}"),
            "release_url": release.get("html_url", f"https://github.com/{full_name}/releases"),
            "download_url": asset["browser_download_url"],
            "icon_url": manifest.get("icon_url") or (repo_data.get("owner") or {}).get("avatar_url"),
            "screenshots": manifest.get("screenshots") or [],
            "size_bytes": int(asset.get("size") or 0),
            "sha256": digest,
            "updated_at": release.get("published_at") or release.get("created_at") or "",
            "download_count": int(asset.get("download_count") or 0),
            "featured": bool(manifest.get("featured", defaults.get("featured", False))),
            "changelog": (release.get("body") or "")[:12000],
        }
        apps.append(app)
        print(f"Added {full_name} ({app['version']})")

    apps.sort(key=lambda item: (
        not item["featured"],
        str(item["name"]).casefold(),
        item["repository"].casefold(),
    ))
    document = {
        "schema_version": 1,
        "apps": apps,
    }
    serialized = json.dumps(document, ensure_ascii=False, indent=2) + "\n"
    for output in OUTPUTS:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(serialized, encoding="utf-8")
    print(f"Catalog built: {len(apps)} app(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
