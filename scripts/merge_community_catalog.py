#!/usr/bin/env python3
"""Merge reviewed community submissions into the generated FreeStore catalog."""
from __future__ import annotations
import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
COMMUNITY = ROOT / "catalog" / "community-apps.json"
OUTPUTS = [ROOT / "catalog" / "apps.json", ROOT / "website" / "catalog" / "apps.json"]

def main() -> int:
    base_path = ROOT / "catalog" / "apps.json"
    base = json.loads(base_path.read_text(encoding="utf-8")) if base_path.exists() else {"apps": []}
    community = json.loads(COMMUNITY.read_text(encoding="utf-8")) if COMMUNITY.exists() else {"apps": []}
    if not isinstance(base.get("apps"), list) or not isinstance(community.get("apps"), list):
        raise RuntimeError("Catalog files must contain an apps array")
    selected: dict[str, dict[str, Any]] = {}
    for app in [*base["apps"], *community["apps"]]:
        if not isinstance(app, dict):
            continue
        url = str(app.get("download_url") or "")
        if not url.startswith("https://") or not url.lower().endswith(".apk"):
            continue
        if not app.get("id") or not app.get("name"):
            continue
        package = str(app.get("package_name") or "").strip().lower()
        key = f"package:{package}" if package else f"id:{app['id']}"
        old = selected.get(key)
        if old is None or str(app.get("updated_at") or "") >= str(old.get("updated_at") or ""):
            selected[key] = app
    apps = list(selected.values())
    apps.sort(key=lambda item: (
        not bool(item.get("featured")),
        str(item.get("name") or "").casefold(),
        str(item.get("repository") or "").casefold(),
    ))
    serialized = json.dumps({"schema_version": 1, "apps": apps}, ensure_ascii=False, indent=2) + "\n"
    for output in OUTPUTS:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(serialized, encoding="utf-8")
    print(f"Catalog merged: {len(apps)} app(s)")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
