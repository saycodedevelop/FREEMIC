Wireless microphones and a karaoke player for Android phones on the same Wi-Fi.

### What's changed

{changes}

### Download and install

- Download `KaraokeLink-{version}.apk` from the assets below. Requires Android 9 or later.
- Install the same APK on the player and every microphone phone. One player supports two microphones at once.
- Old preview builds require one uninstall because their signing key differs. Later official releases can update in place.

### Validation

The publishing workflow checks Android build/lint, player UI, microphone foreground service, discovery and two-microphone behavior, plus C++ sanitizer tests and jitter/clock-drift simulations.
Bluetooth quality, battery management, and simultaneous audio still need verification on real devices.

Use `SHA256SUMS.txt` to verify the APK integrity.
