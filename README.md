# squeen

**squeen** is an unofficial, reverse-engineered Android client for Speed Queen laundry rooms
(Alliance Laundry Systems). It was rebuilt from scratch in Kotlin + Jetpack Compose after
reverse-engineering the official 4.2.2 app, and it talks to the same public laundry API.

> Not affiliated with, endorsed by, or connected to Alliance Laundry Systems. "Speed Queen" is a
> trademark of its owner. Use at your own risk.

## Features

* Sign in with e-mail or phone, sign-up, password reset, e-mail/phone verification.
* Locations (PIN, QR or value-centre codes), rooms and **live** machine grids over WebSocket.
* Machine pages: availability, pricing, fabric/heat selection, dryer **time selection with live
  pricing**, top-off, rewards credit, wash alerts (e-mail **and SMS**, delivered server-side).
* Payments: balance top-up with saved cards, value-station (kiosk) deposits, auto-recharge,
  payment-method management, hosted gateway forms.
* History with per-transaction e-mail receipts, promotions, mailbox notifications, favourites
  (client-side), Scan & pay QR codes.
* Material 3 UI with brand theming, optional Material You, light/dark, per-machine animations.

## Install

* **Obtainium** (recommended): tap *Get with Obtainium* in the app's About section, or add
  `https://github.com/drakeerv/squeen` to Obtainium, releases are picked up automatically.
* Or grab an APK from the [releases page](https://github.com/drakeerv/squeen/releases).

## Build

Requires JDK 17+ and an Android SDK with platform 37 or 36 (`compileSdk` is set in
`app/build.gradle.kts`). The Gradle wrapper is committed:

```bash
./gradlew :app:assembleDebug     # debug build
./gradlew :app:assembleRelease   # release build (signed when keystore.properties exists)
```

Optional release signing: create `keystore.properties` (git-ignored) with `storeFile`,
`storePassword`, `keyAlias`, `keyPassword`. Without it, release builds fall back to the debug key.

## Architecture

```
app/src/main/kotlin/com/drakeerv/squeen/
├── data/     API interface + request DTOs, JSON:API reader, domain models/mappers,
│             OkHttp/Retrofit factory, converter, WebSocket client, Room payload cache,
│             session + favourites stores, repository (ApiResult based)
├── ui/       theme, shared components, navigation, screens (Home, Locations, Rooms,
│             Machines, Add funds, Card link/Scan & pay, History, Profile, Settings, …)
└── di/       Koin modules
```
