# Daycompass Android Client

Android client for [Daycompass](https://github.com/Daycompass/Daycompass), a
self-hosted, open-source screen time backend for students.

**Status: early scaffolding.** There are no features yet.

## Principles

- No Google Play Services, no Firebase, no analytics, no crash reporting, no
  telemetry.
- The only network destination is the Daycompass server you configure
  yourself, over HTTPS.
- Dependencies are free and open source (F-Droid friendly).

## Backend

The API this client talks to is documented in the backend repository:
`docs/wire-format.md` (normative) and `docs/openapi-wire-v1.json`.

## License

[AGPL-3.0-only](LICENSE)

## Development

Requirements: JDK 21 and an Android SDK (set `sdk.dir` in `local.properties`).

```
./gradlew ktlintCheck detekt lint testDebugUnitTest assembleDebug
```

See `CLAUDE.md` for conventions and for how to regenerate
`gradle/verification-metadata.xml` after changing dependencies.
