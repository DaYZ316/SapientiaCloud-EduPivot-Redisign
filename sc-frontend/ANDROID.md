# Android internal-test build

The Android client bundles the Vite app and connects to `https://edupivot.xyz` from Capacitor's `https://localhost` WebView origin.

## Build

```powershell
pnpm dist:android
```

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. Install it on an Android device that permits internal-test APKs.

## Included capabilities

- Password login and registration
- Courses, AI streaming, file upload/download, notifications, LiveKit, and 3D classroom assets
- Android camera and microphone permission declarations

GitHub OAuth is deliberately hidden in this first APK. It needs a mobile deep-link callback and a matching GitHub OAuth callback registration before it can safely return to the app.
