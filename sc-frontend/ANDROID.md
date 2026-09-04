# Android internal-test and release build

The Android client bundles the Vite app and connects to `https://edupivot.xyz` from Capacitor's `https://localhost` WebView origin. GitHub sign-in opens the system browser, uses PKCE, and returns through the Android-only callback `https://edupivot.xyz/oauth/github/mobile/callback`.

## Debug APK

```powershell
pnpm dist:android
```

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. It uses the `edupivot://oauth/callback` fallback because Android App Links only verify a release signing certificate.

## Signed release APK

The signing key and its password file must stay outside the repository. This workstation's controlled key is kept under `%USERPROFILE%\.edupivot\android`; back up that directory securely before distributing a release.

```powershell
$env:EDUPIVOT_ANDROID_SIGNING_PROPERTIES = "$env:USERPROFILE\.edupivot\android\release-signing.properties"
pnpm dist:android:release
```

The signed APK is written to `android/app/build/outputs/apk/release/app-release.apk`. Its certificate fingerprint is published in `public/.well-known/assetlinks.json`; deploy the frontend before testing verified App Links.

## Required GitHub and Android configuration

- Register the exact Android callback `https://edupivot.xyz/oauth/github/mobile/callback` in the GitHub OAuth App. Do not use a wildcard callback.
- Deploy `https://edupivot.xyz/.well-known/assetlinks.json` over HTTPS with `application/json` before release testing.
- Keep the custom `edupivot://oauth/callback` fallback enabled for debug/internal builds and devices where domain verification is unavailable.

## Included capabilities

- Password and GitHub login, registration, and Android Keystore-protected session restoration
- Courses, AI streaming, file upload/download, notifications, LiveKit, and 3D classroom assets
- Android camera and microphone permission declarations
