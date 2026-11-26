# Water Genius

Water Genius is an offline Android pipe puzzle. Restore 50 gardens and habitats across Green Garden, Magic Forest, Desert Oasis, Frozen Valley and Tropical Paradise.

Tap or click a movable pipe to rotate it clockwise. Connect the tanks to every plant and seal all outlets carrying water. Use the arrow keys to move the tile selection, Enter or Space to rotate, Tab to navigate between controls, and Escape to pause or go back. Gold markers indicate open outlets in the active water network. Hints point to a useful pipe and show how many clockwise turns to make.

The game stores best stars and audio settings in Android DataStore. Every completed level unlocks the next, and replaying can improve a previous score. A hint-free run at or below the reference solution's move count earns three stars. One hint or a few extra moves can still earn two.

## Build

Install JDK 17, Android SDK Platform 36 and Android Build Tools 36.0.0. Use Android Studio or set `ANDROID_HOME` to the SDK folder.

```sh
./gradlew testReleaseUnitTest lintRelease
./gradlew assembleDebug
```

The app ID is `com.watergenius.water`. Version 1.0.0 uses version code 1 and requires Android 8.0 or later. Portrait is the primary layout and there is a separate landscape layout.

To sign a release, supply `WATER_SIGNING_STORE` as the absolute path to a private PKCS#12 keystore and set `WATER_STORE_PASSWORD`, `WATER_KEY_ALIAS` and `WATER_KEY_PASSWORD` in your local environment.

```sh
./gradlew assembleRelease bundleRelease
tools/verify-apk.sh app/build/outputs/apk/release/app-release.apk
jarsigner -verify app/build/outputs/bundle/release/app-release.aab
```

The signed APK and AAB are under `app/build/outputs/apk/release` and `app/build/outputs/bundle/release`.

## GitHub Actions

The workflow tests and lints on pushes to main, on version tags, and when started manually. Add repository Actions secrets called `WATER_KEYSTORE_BASE64`, `WATER_STORE_PASSWORD`, `WATER_KEY_ALIAS` and `WATER_KEY_PASSWORD` to enable signed builds and artifact uploads. Use a single-line base64 encoding of the PKCS#12 file for the first secret. Keep a private backup of the signing files. Publish signed artifacts from a verified run on the corresponding GitHub release.

## Assets and privacy

The launcher uses the supplied Water Genius illustration. Chapter scenes, logo and decorative sprites were generated for Water Genius. The project includes source scripts for the original metallic pipe renders and original synthesized tracks and effects. Gradle and Android dependencies retain their upstream licenses. No third-party game assets are required.

Water Genius does not request Android permissions and does not use a network, analytics, advertisements, purchases, accounts, vibration or external storage. Progress and settings stay in app-private storage. Clearing the app's storage removes that progress.

## Validation

The tests exercise all 50 playable boards, solution paths, hint convergence, leak and valve handling, scoring, progression and persistence across a DataStore reopen. The release APK verification checks its package, SDK versions, signature, Android hardware declarations, permissions and native library contents. Play Console Device Catalog results, device visuals, input behavior and frame rates require independent device or Play Console validation.
