# Android ESC/POS Virtual Printer

Native Android app that opens a TCP socket, receives ESC/POS bytes, parses basic print commands, and renders received receipts on screen.

## Stack

- Kotlin
- Foreground Service
- Jetpack Compose
- Coroutines and Flow
- Hexagonal architecture

## Architecture

```text
app/src/main/java/com/example/escposvirtualprinter/
  domain/
    model/          Receipt, text blocks, styles
    parser/         Pure Kotlin ESC/POS parser and receipt builder
  application/
    port/inbound/   Use cases consumed by UI/service
    port/outbound/  Repository and TCP server ports
    service/        Application service wiring ports
  adapter/
    inbound/
      ui/           Jetpack Compose Activity
      service/      Android Foreground Service
    outbound/
      network/      TCP ESC/POS server adapter
      persistence/  In-memory receipt repository
```

The domain parser has no Android dependency. TCP and Android lifecycle concerns stay in adapters.

## Current Features

- Start/stop foreground TCP server from the app UI
- Configurable TCP port, defaulting to `9100`
- Receive ESC/POS byte streams from TCP clients
- Parse basic commands:
  - `ESC @` initialize
  - `LF`, `CR`
  - `ESC a n` alignment
  - `ESC E n` bold
  - `ESC - n` underline
  - `GS ! n` character size
  - `ESC d n` feed lines
  - `DLE EOT n` status request event
  - `GS V` cut and complete receipt
- Auto-complete a receipt after 2 seconds of idle input
- Show multiple receipts in a scrollable Compose UI
- Keep the latest 100 receipts in memory

## Build

```sh
./gradlew testDebugUnitTest assembleDebug
```

## Manual TCP Test

Install and run the debug app, press `Start`, then send sample ESC/POS data from a machine on the same network:

```sh
printf '\x1b@Hello ESC/POS\n\x1ba\x01CENTER\n\x1ba\x00\x1bE\x01BOLD\n\x1bE\x00\x1dV' | nc <android-device-ip> 9100
```

`9100` is a common raw TCP printing/AppSocket/JetDirect port, not an ESC/POS protocol requirement. The app keeps the port configurable.
