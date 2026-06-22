# Copilot Pulse Android — Implementation Plan

## Overview

Android home-screen widget that mirrors **orty/copilot-usage-widget** (Windows) and follows the design of **G-biggy/claude-pulse-android** (Android), adapted for GitHub Copilot Enterprise premium interaction credits.

---

## Architecture

### Package: `com.orty.copilotpulse`

| File | Role |
|------|------|
| `TokenManager.kt` | Stores/retrieves GitHub PAT in SharedPreferences |
| `UsageData.kt` | Data models for Copilot `quota_snapshots` |
| `ApiClient.kt` | Calls `https://api.github.com/copilot_internal/user` |
| `RefreshWorker.kt` | WorkManager background refresh every 15 minutes |
| `PulseWidget.kt` | AppWidgetProvider — renders RemoteViews |
| `SetupActivity.kt` | Launcher activity — GitHub PAT onboarding |

---

## API Integration

**Endpoint**: `GET https://api.github.com/copilot_internal/user`  
**Auth**: `Authorization: Bearer <github_pat>`  
**Key response fields**:
```json
{
  "quota_snapshots": {
    "premium_interactions": {
      "unlimited": false,
      "entitlement": 300,
      "remaining": 250,
      "percent_remaining": 83.33,
      "overage_count": 0,
      "overage_permitted": false
    }
  },
  "quota_reset_date_utc": "2026-07-01T00:00:00.000Z"
}
```

**Color thresholds** (matching the Windows widget):
- Blue `#0969da` — < 75% used
- Yellow `#d4a017` — 75–89% used
- Red `#cf222e` — ≥ 90% used

---

## Widget Design

Two layouts (matching claude-pulse-android):
1. **Full** (`widget_layout.xml`, minWidth ≥ 200dp): Header + up to 3 quota rows + footer buttons
2. **Compact** (`widget_layout_small.xml`, minWidth < 200dp): Progress bars + percentages only

Each quota row: label · progress bar · % used · reset countdown  
Quotas sorted: `premium_interactions` first, then alphabetically.

---

## Auth Flow

GitHub PAT (Personal Access Token) with `copilot` scope:
1. User opens app → `SetupActivity`
2. Pastes PAT → validated against API
3. Token stored in `SharedPreferences` (key: `copilot_pulse_credentials`)
4. On 401/403: `auth_error` flag set → widget shows "Token expired · Tap to fix"
5. User taps widget → opens `SetupActivity` for re-auth

No refresh token needed — GitHub PATs are long-lived unless explicitly expired.

---

## CI/CD

### CI (`ci.yml`)
- Triggers: push to `main` / `claude/**`, PRs to `main`
- Builds debug APK
- Uploads as workflow artifact (7-day retention)

### Release (`release.yml`)
- Triggers: `v*` tag push or manual dispatch
- Patches `versionName` in `app/build.gradle.kts`
- Builds release APK
- Signs with keystore (requires secrets below) — falls back to unsigned
- Creates GitHub Release with APK attached

### Required Secrets (for signed releases)

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded Android keystore `.jks` |
| `KEY_ALIAS` | Key alias in keystore |
| `KEY_PASSWORD` | Key password |
| `STORE_PASSWORD` | Keystore password |

---

## Task Checklist

- [x] `PLAN.md` written
- [x] Build files: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`
- [x] `AndroidManifest.xml`
- [x] `TokenManager.kt`
- [x] `UsageData.kt`
- [x] `ApiClient.kt`
- [x] `RefreshWorker.kt`
- [x] `PulseWidget.kt`
- [x] `SetupActivity.kt`
- [x] `widget_layout.xml`
- [x] `widget_layout_small.xml`
- [x] `activity_setup.xml`
- [x] Drawables: `widget_background.xml`, `progress_bar_blue.xml`, launcher icons
- [x] Values: `colors.xml`, `strings.xml`
- [x] `xml/pulse_widget_info.xml`
- [x] `.github/workflows/ci.yml`
- [x] `.github/workflows/release.yml`
- [x] `README.md`
