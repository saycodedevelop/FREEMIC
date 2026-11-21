# Automatic preview releases

Push to `main` or run **Android checks and automatic prerelease** from GitHub Actions.
The workflow builds the universal APK, runs Android lint and portable C++ tests,
then creates a `preview-<run number>` GitHub prerelease with the APK and SHA-256 checksums.
Pull requests run checks without publishing. Failed checks prevent publishing.

No personal access token is needed: only the release job receives `contents: write`
using the repository's GitHub Actions token. Actions must be enabled for the repository.

These are development previews, signed with the runner's Android debug key.
Signing keys can differ across runs, so upgrading may require uninstalling the previous
preview. They are not production/store releases. Keep a permanent private signing key
and configure a signed release build before distributing stable upgrades.
Never commit keystores, passwords, or tokens.

Local build (JDK 17 or 21, Android SDK 35, NDK 27.0.12077973, CMake 3.22.1):

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Use `docs/TESTING.md` for real-device checks before wider distribution.