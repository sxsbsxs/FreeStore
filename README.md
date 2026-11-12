# FreeStore

**FreeStore** is a free, open-source Android + web app marketplace built on GitHub. It is designed to run without a self-hosted server, VPS, paid database or paid backend.

- Android app: Kotlin + Jetpack Compose
- Web store: static HTML, CSS and JavaScript
- Catalog: JSON generated from public GitHub repositories and Releases
- APK hosting: each publisher's own GitHub Release assets
- Automation: GitHub Actions builds Android, refreshes the catalog and deploys GitHub Pages

## Repository layout

```text
app/                        Android application
website/                    Static web marketplace
catalog/sources.json        Approved GitHub repositories to index
catalog/apps.json           Generated app catalog
scripts/build_catalog.py    Reads GitHub Releases and builds the catalog
.github/workflows/          Android build, catalog refresh, Pages and Release workflows
docs/                       Setup and publishing guides
```

## How to add an app

1. Add a public repository to `catalog/sources.json` (see [Adding apps](docs/ADDING_APPS.md)).
2. Publish a stable GitHub Release in that repository and attach an installable `.apk` file.
3. Optionally add `freestore.json` to the app repository to customize its name, description, category and icon.
4. The catalog workflow refreshes automatically every six hours. You can also run it from **Actions → Refresh app catalog → Run workflow**.

Only public repositories with a latest stable Release containing an APK asset are indexed. APK files remain hosted by the app developers on GitHub.

## Android build

The Android project uses Java 17, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21 and Android SDK 35.

GitHub Actions builds a debug APK on pushes and pull requests. Open **Actions → Android CI** and download the `FreeStore-debug` artifact.

For a local build with Gradle 8.9 installed:

```bash
gradle --no-daemon :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Web deployment

The Pages workflow deploys the contents of `website/` to GitHub Pages. The workflow attempts to enable Pages automatically. If GitHub requires a one-time repository setting, open **Settings → Pages → Build and deployment → Source** and select **GitHub Actions**.

After the first successful deployment, the site is normally available at:

`https://sxsbsxs.github.io/FreeStore/`

## Security and signing

- The catalog is public metadata and must be treated as untrusted input.
- The Android client only accepts HTTPS APK URLs. It verifies SHA-256 when GitHub Release metadata exposes an asset digest.
- Android's package installer validates APK signatures. Updating an installed application requires the same application ID and a compatible signing certificate.
- The CI APK is a **debug build for testing**. For production distribution, developers must sign releases with a stable private keystore. Never commit a production keystore or signing password to Git.
- FreeStore does not perform malware analysis or security audits of third-party APKs. Review the publisher and source repository before installing.

## Free-service limitations

This project avoids operating a backend, but GitHub remains an external platform with file-size, API-rate, Actions-minute and Pages limits. Public repository contents and release assets are public. Availability and quotas are controlled by GitHub and may change.

## License

MIT. See [LICENSE](LICENSE).
