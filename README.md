# Reich 317 — Server

The Elvarg / Reich `317` game server, originally a desktop Java project and
ported to Android so it can run from a phone or tablet.

```
.
├── app/      Android application (UI + foreground service)
├── server/   The game server itself (plain Java library)
├── data/     Definitions, clipping maps and saves (packaged into the APK)
├── build.gradle
└── settings.gradle
```

## Requirements

| Target | Needs |
| --- | --- |
| Server (JVM) | JDK 17, Gradle wrapper 8.10.2 (bundled) |
| Android app | JDK 17, Android SDK 35, Gradle wrapper 8.10.2 (bundled) |

## Build — Android app

The Android SDK location is read from `local.properties` (`sdk.dir=...`).

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Build — desktop JVM

The `server` module is a plain Java library with `com.elvarg.Server` as its
main class, so it can still be run on a desktop JVM against the repository
`data` folder:

```bash
./gradlew :server:build
java -cp server/build/libs/*:data com.elvarg.Server
```

## Data

- `data/definitions/` — item, NPC and object definitions
- `data/clipping/` — clipping / region maps
- `data/saves/` — player saves (`data/saves/characters/` is gitignored)

## Features

- Elvarg core: player/NPC updating, combat, skills, region and clipping systems
- Content defined by JSON definitions, loaded from `data/`
- Runs headless as a Java library or inside an Android foreground service so
  the server keeps running while the app is backgrounded
- Single code base for desktop and Android (`com.reich.server` app module
  wraps the same `server` module)
