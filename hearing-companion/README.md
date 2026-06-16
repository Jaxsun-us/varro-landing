# Hearing Companion (Android)

A native Android app for controlling Bluetooth LE hearing aids — built around the
**standard** Volume Control (`0x1844`), Hearing Access (`0x1854`) and Battery (`0x180f`)
services, plus background automation the manufacturer apps don't offer.

> Android only (by design — no iPhone support). Requires aids that expose the standard
> LE Audio services (e.g. newer Phonak/Sonova, ReSound, etc.). It does **not** touch the
> locked fitting/prescription layer.

## Features

| Area | Status | Notes |
|------|--------|-------|
| Connect to paired aids | ✅ Working | Bonded-device picker on the Remote tab |
| Volume up/down/mute/unmute/set level | ✅ Working | Standard VCS, change-counter handled automatically |
| Program prev/next/set/list | ✅ Working | Standard HAS preset control point |
| Battery level | ✅ Working | Standard Battery service, read + live notify |
| Usage diary | ✅ Working | Every change (manual + automated) saved locally (Room) |
| Time schedules (runs when app closed) | ✅ Working | `AlarmManager` + foreground service; re-armed on reboot |
| Location automation (geofences) | ✅ Working | Enter a saved place → apply an action |
| A/B blind program comparison | ✅ Working | Plays one of two programs at random, then reveals |
| Tone / chime / sweep generator | ✅ Working | Plays through the phone's audio output (the aids if they're the active device) — **not** a GATT command |
| Tinnitus masking noise | ✅ Working | Comfort tool only, not treatment |
| Capture & replay (Wireshark hex → aids) | 🔌 Hook present | `HearingAidManager.writeRaw()` exists; no UI yet |
| Equalizer / noise-reduction sliders | ⛔ Not built | Phonak-proprietary; needs a capture session first, and may be encryption-gated |

## Architecture

```
ui/            Jetpack Compose screens + HearingViewModel
ble/           HearingAidManager (GATT engine), BleService (foreground)
data/          Room: DiaryEntry, Schedule, Place + DAOs
automation/    Scheduler (AlarmManager), GeofenceManager, receivers, ActionRunner
audio/         ToneGenerator (AudioTrack)
```
One shared GATT connection lives in `HearingApp.aids`, used by the UI and by background
triggers alike. All data stays on the device — no server, no cloud.

## Build & run

1. Open the folder in **Android Studio** (Koala / 2024.1+). It will download Gradle 8.9
   and sync automatically. *(No Gradle wrapper jar is bundled — Android Studio supplies
   Gradle. From the CLI you can run `gradle wrapper` once with a local Gradle 8.9, then
   `./gradlew assembleDebug`.)*
2. Plug in an Android phone (API 26+) with USB debugging on.
3. Pair your hearing aids in **Android Settings → Bluetooth** first.
4. Run the app, grant Bluetooth + Location + Notification permissions.
5. Remote tab → Connect → use the controls. For background automation, also grant
   **“Allow all the time”** location and disable battery optimization for the app.

## Honest limits

- Works only if your aids implement the standard services. Some Phonak firmware gates
  even these behind its bonded/encrypted link — if writes fail with an auth error, the
  standard path won't work and you'd need the capture-and-replay route.
- The prescription/fitting (gain, MPO, compression) is **not** here and shouldn't be —
  that's the calibrated, safety-critical layer owned by Phonak Target + a programmer.
- Tone tools and tinnitus noise are comfort/test utilities, **not** medical devices.
