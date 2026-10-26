# Daycompass Android Client

Android client for [Daycompass](https://github.com/Daycompass/Daycompass), a
self-hosted, open-source screen time backend for students.

**Status: first complete flow, not yet tested on a real device.** Pairing,
usage collection and sync are built and covered by unit tests, but nothing
has run on a phone or against a real server yet. There is no release build.

## What it does

1. **Pair** the device with your server: enter the server URL, a pairing code
   from the Daycompass dashboard and a device name (prefilled with the device
   model).
2. **Allow usage access** in the Android settings. The app explains first what
   is collected and what is not.
3. **Collect** which app is in the foreground and for how long, from the
   moment of pairing onwards. One entry per app session; this app and the
   launcher are left out. Collection runs hourly and works offline.
4. **Sync** the collected entries to your server whenever it is reachable:
   right away when the app is opened, hourly in the background, and with
   backoff after a failure. There is no Wi-Fi detection, so the home network,
   a VPN such as Tailscale and public servers all work. Offline entries stay
   on the device until the server has confirmed them.
5. **Check the local buffer** from the connected screen: a list of the
   entries still waiting to be sent (package name, collected time, duration).
   This reads only the device's own buffer — the app never reads events back
   from the server.

The connected screen always says in words when the device last synced, how
many entries are waiting, and whether the device clock is off.

## What is sent

Per app session: the app's package name (for example `org.mozilla.firefox`),
the start time and the duration. Nothing else: no app names, no website
domains, no content, no location, no categories, nothing from before pairing.

## Principles

- No Google Play Services, no Firebase, no analytics, no crash reporting, no
  telemetry.
- The only network destination is the Daycompass server you configure
  yourself, over HTTPS. Only certificates trusted by the system are
  accepted; cleartext traffic is disabled.
- The device token is encrypted with an Android Keystore key and excluded
  from backups, like all other app data.
- Dependencies are free and open source (F-Droid friendly).

## Requirements

- Android 8.0 (API 26) or newer.
- A Daycompass server reachable over HTTPS with a certificate the device
  trusts (for example from Let's Encrypt). Self-signed certificates and
  user-installed CAs are not accepted.

## Backend

The API this client talks to is documented in the backend repository:
`docs/wire-format.md` (normative) and `docs/openapi-wire-v1.json`.

## License

[AGPL-3.0-only](LICENSE). Bundled fonts: Fredoka and Nunito (SIL Open Font
License 1.1). Icon: Phosphor Icons (MIT). The license texts ship in the app
under `assets/licenses/`.

## Development

Requirements: JDK 21 and an Android SDK (set `sdk.dir` in `local.properties`).

```
./gradlew ktlintCheck detekt lint testDebugUnitTest assembleDebug
```

See `CLAUDE.md` for conventions and for how to regenerate
`gradle/verification-metadata.xml` after changing dependencies.
