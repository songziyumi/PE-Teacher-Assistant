# Miniapp Release Checklist

## Code and project config

- Fixed production API base URL: `https://www.jsqyty.com`
- Login page no longer allows manual server address input
- `project.config.json` is set for release:
  - `urlCheck: true`
  - `minified: true`
  - `uploadWithSourceMap: false`

## WeChat admin console

- Confirm the mini program `appid` matches the release target
- Configure request legal domain:
  - `https://www.jsqyty.com`
- If profile photo upload is used, confirm upload legal domain covers the same host
- If exported files are opened in mini program, confirm download legal domain covers the same host
- Complete privacy policy and data collection declaration before submission
- Confirm category, subject, app name, logo, and filing subject are consistent

## Server readiness

- HTTPS certificate is valid for `www.jsqyty.com`
- Backend miniapp APIs are reachable:
  - `/api/miniapp/auth/login`
  - `/api/miniapp/auth/me`
  - `/api/miniapp/home`
- Teacher APIs used by miniapp are reachable with JWT auth
- Production env vars are set:
  - `APP_JWT_SECRET`
  - `DB_USERNAME`
  - `DB_PASSWORD`
  - `APP_ADMIN_DEFAULT_PASSWORD`
  - `APP_SUPER_ADMIN_DEFAULT_PASSWORD`
- Upload directory is writable and exposed by `/uploads/**`

## Device verification

- Login works on real device
- Home page loads without devtools-only settings
- Attendance entry works
- Student list works
- Physical entry and detail pages work
- Term grade entry works
- Profile photo upload works
- Export/download flows work on real device
- Token expiration redirects back to login correctly

## Submission notes

- Import the `miniapp` directory directly in WeChat DevTools
- Use release build, not preview-only local overrides
- Remove any test accounts, test copy, and temporary assets before upload
