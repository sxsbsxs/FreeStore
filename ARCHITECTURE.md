# Architecture

FreeStore is a static, GitHub-native app marketplace.

- **Android client:** Kotlin, Jetpack Compose, OkHttp and Gson.
- **Web client:** static HTML, CSS and JavaScript.
- **Catalog source of truth:** `catalog/sources.json`, publisher Release metadata, and reviewed `catalog/community-apps.json` entries.
- **Catalog output:** `catalog/apps.json`.
- **APK distribution:** publisher-owned GitHub Releases and FreeStore Releases created after manual approval.
- **Submission path:** the static form creates a prefilled GitHub Issue; the submitter attaches the APK using GitHub's public attachment uploader.
- **Moderation:** an intake workflow labels new submissions; only a maintainer-applied approved label can trigger the publishing workflow.
- **Automation:** GitHub Actions validates APK structure, checks manifest package/version and APK signing integrity, publishes Release assets, refreshes the catalog, builds Android and deploys GitHub Pages.

There is no application server, database, VPS, or paid backend in this design. The submitter must sign in to GitHub to open the Issue and attach the file. GitHub public issue attachments are limited to 25 MB per file; larger APKs use the publisher Release path. Public issue attachments and Release assets are public.

## Trust boundary

The catalog is public, untrusted metadata. The Android client accepts HTTPS APK URLs only and verifies SHA-256 when the release API exposes a digest. GitHub release asset digests may be unavailable for older assets; in that case the client cannot claim a checksum verification. Users should review the publisher and repository before installing. Android's package installer remains responsible for final package signature and compatibility checks.

The first CI APK is a debug build intended for testing. Production releases should be signed with a stable developer-controlled signing key. Never commit a production keystore or its password to the repository.
