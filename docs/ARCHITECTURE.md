# Architecture

FreeStore is a static, GitHub-native app marketplace.

- **Android client:** Kotlin, Jetpack Compose, OkHttp and Gson.
- **Web client:** static HTML, CSS and JavaScript.
- **Catalog source of truth:** `catalog/sources.json` plus GitHub Releases metadata.
- **Catalog output:** `catalog/apps.json`.
- **APK distribution:** release asset URLs on each publisher's GitHub repository.
- **Automation:** GitHub Actions refreshes the catalog, builds Android and deploys GitHub Pages.

There is no application server, database, VPS, or paid backend in this design.

## Trust boundary

The catalog is public, untrusted metadata. The Android client accepts HTTPS APK URLs only and verifies SHA-256 when the release API exposes a digest. GitHub release asset digests may be unavailable for older assets; in that case the client cannot claim a checksum verification. Users should review the publisher and repository before installing. Android's package installer remains responsible for final package signature and compatibility checks.

The first CI APK is a debug build intended for testing. Production releases should be signed with a stable developer-controlled signing key. Never commit a production keystore or its password to the repository.
