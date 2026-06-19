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
- Decode received text as `EUC-KR` by default, which supports Korean POS receipts
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

Install and run the debug app, then press `Start` in the app. The app shows the reachable endpoint at the top, for example:

```text
TCP connection
tcp://10.10.70.136:9100
```

`9100` is a common raw TCP printing/AppSocket/JetDirect port, not an ESC/POS protocol requirement. The app keeps the port configurable.

### Option 1: USB device with adb forward

Use this when the Android device is connected by USB and you want to send test data from the development machine without relying on Wi-Fi routing.

1. Confirm that the device is connected:

```sh
adb devices -l
```

2. Install and launch the app:

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.escposvirtualprinter/.adapter.inbound.ui.MainActivity
```

3. Press `Start` in the app.

4. Forward host port `9100` to device port `9100`:

```sh
adb forward tcp:9100 tcp:9100
```

5. Send a sample ESC/POS receipt to `127.0.0.1:9100`:

```sh
printf '\x1b@\x1ba\x01ADB FORWARD TEST\n\x1ba\x00ORDER #USB-001\n--------------------------------\nAmericano          1   4,000\nLatte              1   5,500\n--------------------------------\n\x1bE\x01TOTAL               9,500\n\x1bE\x00\x1ba\x01Thank you\n\x1ba\x00\x1dV' | nc 127.0.0.1 9100
```

6. Remove the forwarding rule when finished:

```sh
adb forward --remove tcp:9100
```

If multiple devices are connected, add the device serial:

```sh
adb -s <device-serial> forward tcp:9100 tcp:9100
adb -s <device-serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

### Option 2: Direct TCP over the same network

Use this when the POS/client machine and Android device are on the same network.

1. Run the app and press `Start`.

2. Read the endpoint shown in the app header, for example:

```text
tcp://10.10.70.136:9100
```

3. Send ESC/POS bytes to that IP and port:

```sh
printf '\x1b@\x1ba\x01NETWORK TEST\n\x1ba\x00ORDER #LAN-001\n--------------------------------\nBurger             2  15,800\nFries              1   3,500\nCola               2   4,000\n--------------------------------\n\x1d\x21\x11TOTAL              23,300\n\x1d\x21\x00\x1ba\x01PAID CARD\n\x1ba\x00\x1dV' | nc 10.10.70.136 9100
```

Replace `10.10.70.136` with the IP address shown by the app.

### Send three sample receipts

The app treats `GS V` cut commands as receipt boundaries, so multiple receipts can be sent through one TCP connection:

```sh
{
printf '\x1b@\x1ba\x01CAFE CODEx\n\x1ba\x00ORDER #A-1001\n2026-06-19 13:45\n--------------------------------\nAmericano          1   4,000\nCroissant          2   7,600\n--------------------------------\n\x1bE\x01TOTAL              11,600\n\x1bE\x00\x1ba\x01Thank you\n\x1ba\x00\x1dV';
sleep 0.2;
printf '\x1b@\x1ba\x01BURGER LAB\n\x1ba\x00ORDER #B-2048\nTAKE OUT\n--------------------------------\nClassic Burger     2  15,800\nFrench Fries       1   3,500\nCola               2   4,000\n--------------------------------\n\x1d\x21\x11TOTAL              23,300\n\x1d\x21\x00\x1ba\x01PAID CARD\n\x1ba\x00\x1dV';
sleep 0.2;
printf '\x1b@\x1ba\x01BOOK STORE\n\x1ba\x00RECEIPT #C-3099\n--------------------------------\nNotebook           3   9,000\nGel Pen            5   6,500\nBookmark           1   1,200\n--------------------------------\nSUBTOTAL              16,700\nTAX                    1,670\n\x1bE\x01TOTAL              18,370\n\x1bE\x00\x1ba\x01See you again\n\x1ba\x00\x1dV';
} | nc 127.0.0.1 9100
```

For direct network testing, replace `127.0.0.1` with the Android device IP shown in the app.

## Parser warning debug workflow

When a receipt shows `parser warning(s)`, the receipt card displays a debug panel under the preview.

The panel includes:

- warning count
- captured raw byte count
- parser warning offset
- unknown command bytes
- a hex window around the warning offset
- an ASCII preview for printable bytes

Example:

```text
5 parser warning(s) · raw 790/790 bytes
@5: Unknown command: 1b 21
000000: 1B 61 01 1B 21 >30 ...
ascii: .a..!0...
```

Use the byte after `>` as the parser offset focus. In this example, `1B 21` is `ESC !`, the ESC/POS print mode command.

### Capture raw TCP input on macOS

If the POS client connects to `127.0.0.1:9100` on the Mac, stop `adb forward` temporarily and listen directly:

```sh
adb forward --remove tcp:9100
nc -l 127.0.0.1 9100 > artifacts/tcp_9100_capture.bin
```

After sending a receipt from the POS client, inspect the bytes:

```sh
xxd -g 1 -l 512 artifacts/tcp_9100_capture.bin
```

Restore forwarding afterward:

```sh
adb forward tcp:9100 tcp:9100
```

For direct network testing, send the POS output to the Android device IP shown in the app, for example `10.10.70.136:9100`. In that mode, the Android app captures the same raw bytes in each receipt and shows warning offsets in the UI.

### Send a Korean EUC-KR receipt

The TCP server decodes text bytes as `EUC-KR` by default. Use `iconv` to generate EUC-KR bytes from a UTF-8 terminal:

```sh
{
printf '\x1b@';
printf '한글 영수증\n주문번호: KR-0001\n--------------------------------\n아메리카노        1   4,000\n카페라떼          2  11,000\n--------------------------------\n합계                 15,000\n감사합니다\n' | iconv -f UTF-8 -t EUC-KR;
printf '\x1dV';
} | nc 127.0.0.1 9100
```

For direct network testing, replace `127.0.0.1` with the Android device IP shown in the app.
