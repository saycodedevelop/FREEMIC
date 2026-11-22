# Release process

Published builds use semantic versions: **KaraokeLink v0.2.0**, `v0.2.0`, and `KaraokeLink-0.2.0.apk`.
They are signed release APKs, accompanied by SHA-256 checksums and release notes in Thai, English, Simplified Chinese, Vietnamese, and Indonesian.

## Automatic publishing

A push to `main` or a manual run on `main` runs Android build/lint, UI and microphone-service
regression tests, portable C++ sanitizer tests, audio jitter/clock simulations, and release-policy tests.
Only after all checks pass does GitHub publish a normal release and mark it latest.
Pull requests run validation without signing secrets or publishing.

`scripts/release_version.py` reads `appVersion` from `gradle.properties` as the minimum version.
It ignores old `preview-*` tags, increments the latest official patch version, and reuses a version
already tagged on the same commit for reruns. Increase `appVersion` to request a new minor/major version.
The Android versionCode is `major * 1,000,000 + minor * 1,000 + patch` and increases with the version.
Published release assets are immutable; reruns only repair unpublished drafts.

Update the Unreleased section in `CHANGELOG.md` before pushing. Keep all five translation blocks: `### [th]`, `### [en]`, `### [zh-CN]`, `### [vi]`, and `### [id]`. Each block must contain the same changes in its own language. Historical release sections remain unchanged.

The generator combines those changes with the installation and validation templates in `docs/release-notes/`. Missing, empty, duplicate, or unknown translation blocks fail generation. Review all five translations whenever release content changes; generation does not translate new prose automatically.

Preview all languages with `python scripts/release_notes.py 0.4.1 --output release-notes-preview.md`. Use `--language en` (or `th`, `zh-CN`, `vi`, `id`) for a single-language preview. The workflow uses `--output` to write UTF-8 Markdown and publishes all five languages in one release with language navigation links.

README translations are `README.md` (Thai), `README.en.md`, `README.zh-CN.md`, `README.vi.md`, and `README.id.md`. Keep their setup, device requirements, and limitations aligned. Supporting technical documents retain their existing languages, noted in the translated README links.

## Signing

GitHub Actions requires these repository secrets:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded PKCS12 keystore
- `ANDROID_KEYSTORE_PASSWORD`: keystore password
- `ANDROID_KEY_ALIAS`: signing alias
- `ANDROID_KEY_PASSWORD`: signing key password

The workflow decodes the key only inside the signed-build step, deletes the temporary keystore
when the step exits, and verifies the resulting APK signature before publishing. No debug-key fallback.
Actions are pinned to verified commit SHAs.

The initial permanent key is backed up locally in `.signing/karaokelink-release.p12`, excluded from Git.
Its credential is in `.signing/signing-credential.xml`, encrypted for the current Windows user.
That encrypted credential cannot simply be restored under another Windows account: back up the key
and retain its password securely before replacing the development machine. Never commit either file.

Local signed builds use `KARAOKELINK_KEYSTORE_PATH`, `KARAOKELINK_KEYSTORE_PASSWORD`,
`KARAOKELINK_KEY_ALIAS`, and `KARAOKELINK_KEY_PASSWORD` environment variables with `:app:assembleRelease`.

## Device verification

An upgrade from an old preview requires one uninstall because preview debug keys differ from the
permanent release certificate. Later official releases can upgrade in place.

Automated tests do not prove Bluetooth quality or OEM battery-policy behavior. Test screen-off
microphone streaming, notification Stop, two microphones, disconnection, wired/USB/Bluetooth routes,
and long sessions using the checklist in `TESTING.md` before broad distribution.