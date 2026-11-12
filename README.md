# FreeStore

**FreeStore** is a free, open-source Android + web app marketplace built on GitHub. It is designed to run without a self-hosted server, VPS, paid database or paid backend.

- Android app: Kotlin + Jetpack Compose
- Web store: static HTML, CSS and JavaScript
- Catalog: JSON generated from public GitHub repositories and Releases
- APK hosting: publisher GitHub Releases plus reviewed community submissions
- Automation: GitHub Actions builds Android, refreshes the catalog and deploys GitHub Pages

## Repository layout

```text
app/                        Android application
website/                    Static web marketplace
catalog/sources.json        Approved GitHub repositories to index
catalog/apps.json           Generated app catalog
catalog/community-apps.json  Reviewed community submissions
scripts/build_catalog.py    Reads publisher GitHub Releases
scripts/merge_community_catalog.py  Merges reviewed community APKs
scripts/publish_submission.py  Publishes approved APK submissions
.github/workflows/          Android build, catalog refresh, Pages and Release workflows
docs/                       Setup and publishing guides
```

## How to add an app

1. Add a public repository to `catalog/sources.json` (see [Adding apps](docs/ADDING_APPS.md)).
2. Publish a stable GitHub Release in that repository and attach an installable `.apk` file.
3. Optionally add `freestore.json` to the app repository to customize its name, description, category and icon.
4. The catalog workflow refreshes automatically every six hours. You can also run it from **Actions → Refresh app catalog → Run workflow**.

Public publisher repositories with a stable Release and reviewed community APK submissions are indexed. APKs are distributed directly from GitHub Release assets.

## Community APK submissions

The web store includes a **Submit app** form. A submitter selects an APK (up to 25 MB), fills in its metadata, and is guided to create a GitHub Issue with the APK attached. The browser creates a renamed copy of the APK with a .zip filename because GitHub's public issue attachment uploader supports ZIP files; the APK bytes are not recompressed or modified.

New submissions enter a moderation queue. A maintainer reviews the metadata and APK, then applies the **approved** label. GitHub Actions validates the APK archive, verifies its Android package name and signing integrity, publishes it as an asset in a FreeStore GitHub Release, updates the community catalog, and triggers the Pages deployment. The web form never contains or receives a maintainer token.

**Important limits:** submitters must sign in to GitHub to create the Issue and attach the file. GitHub public issue attachments are limited to 25 MB per file. Larger APKs must use the existing publisher-owned public GitHub Release workflow. Browsing and downloading from the store do not require a GitHub account.

The project still has no self-hosted server, VPS, database, or paid backend. APK submissions are public once attached to a public issue. FreeStore checks APK structure, Android package name, version and signing integrity, with human moderation; this is not a malware scan or security audit.

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
