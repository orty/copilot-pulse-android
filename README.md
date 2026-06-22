# Copilot Pulse Android

Android home-screen widget that monitors GitHub Copilot Enterprise **premium interaction credits** — monthly limit, remaining count, and reset countdown — in a compact always-on-top display.

Inspired by [claude-pulse-android](https://github.com/G-biggy/claude-pulse-android) and [copilot-usage-widget](https://github.com/orty/copilot-usage-widget).

## Requirements

- Android 8.0+ (API 26)
- GitHub Copilot Enterprise seat
- GitHub Personal Access Token with `copilot` scope

## Installation

1. Download `CopilotPulse-<version>.apk` from [Releases](../../releases)
2. Enable "Install from unknown sources" in Android settings
3. Install the APK
4. Open **Copilot Pulse** → paste your GitHub PAT → tap **Connect**
5. Long-press your home screen → Widgets → add **Copilot Pulse**

## Creating a GitHub Token

Go to **github.com/settings/tokens** → Generate new token (classic)  
Required scope: `copilot` (or `manage_billing:copilot` for Enterprise)

## Display

| Color | Meaning |
|-------|---------|
| Blue | < 75% of monthly credits used |
| Yellow | 75–89% used |
| Red | ≥ 90% used |

Widget refreshes every 15 minutes when network is available. Tap **Refresh Now** for immediate update.

## Build From Source

Requires Android Studio or JDK 17 + Gradle 8.5.

```bash
git clone https://github.com/orty/copilot-pulse-android
cd copilot-pulse-android
gradle assembleDebug
```

## Privacy

No data is sent anywhere except `https://api.github.com/copilot_internal/user`.  
Your GitHub token is stored locally in Android SharedPreferences.
