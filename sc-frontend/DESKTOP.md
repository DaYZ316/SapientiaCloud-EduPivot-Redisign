# EduPivot Windows desktop client

The desktop package is an Electron client for Windows 10/11 x64. It embeds the Vite build, uses the `edupivot://app` protocol, and connects to the configured HTTPS EduPivot service.

## Development and package build

```powershell
pnpm install
pnpm dev:desktop
pnpm dist:win
```

`dist:win` produces a per-user NSIS installer in `release/`. The installer is intentionally unsigned for the internal pilot, so Windows SmartScreen can show a warning.

Set `VITE_GITHUB_CLIENT_ID` to the GitHub **public** OAuth client ID before a release build. The client secret must stay on the server; `VITE_DESKTOP_GITHUB_REDIRECT_URI` defaults to `https://edupivot.xyz/login`.

## Administrator environment profiles

The production profile is built in. Additional profiles must be signed with the Ed25519 private key paired with the public key supplied to the package build:

```powershell
$env:EDUPIVOT_DESKTOP_PROFILE_PUBLIC_KEY = Get-Content -Raw C:\secure\edupivot-profile-public.pem
$env:EDUPIVOT_DESKTOP_PROFILE_SIGNING_KEY = Get-Content -Raw C:\secure\edupivot-profile-private.pem
node scripts/sign-desktop-profile.mjs desktop-profile.example.json campus-test.signed.json
pnpm dist:win
```

The public key is embedded in the Electron main process; only the signed JSON is imported through the Desktop App settings page. Do not commit either key or an unsigned production credential.

## Deployment prerequisites

- Configure GitHub OAuth with the HTTPS callback used by the selected profile, normally `https://edupivot.xyz/login`.
- Deploy the web callback branch before the desktop client so a `desktop.<nonce>` OAuth state opens `edupivot://oauth/callback`.
- Permit the exact origin `edupivot://app` for API and MinIO CORS. Keep all other origins denied.
