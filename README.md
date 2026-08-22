# Redline Music

A dark, Material 3 music player for Android that plays your local library **and** streams from
YouTube Music — no API key required.

## Features

- **Local music library** — songs, albums, artists, favorites, playlists and history, with a
  combined Home screen and quick "Shuffle all" / "Play all".
- **YouTube Music** — search the full catalog and stream songs, albums and artists.
- **Google sign-in (no API key)** — sign in with your Google account via the public
  device-authorization flow to see your liked songs and playlists.
- **Audio effects** — 5-band equalizer with presets, bass boost, virtualizer and loudness.
- **Tempo & pitch** — one-tap presets (Nightcore, Deep, Daycore, Vaporwave, Slowed, Chipmunk)
  plus fine-grained pitch/speed control.
- **Background playback** — media notification, lock-screen and Bluetooth controls.
- **Extras** — synchronized `.lrc` lyrics, sleep timer, queue reordering, visualizers and
  accent/background themes.

## Building

Requires JDK 17 and the Android SDK (compileSdk 36).

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. CI builds and uploads a debug APK on every
push (`.github/workflows/build-apk.yml`).

## Privacy

No YouTube Data API key is used. Search and playback use the same anonymous InnerTube clients
that ship inside YouTube's own web/app clients; signing in uses Google's device-authorization
flow and stores only an OAuth access/refresh token on-device. Nothing is ever uploaded.
