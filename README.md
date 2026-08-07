# Workoutapp

A learning-focused Android app that simulates a gym **step machine** workout.

Today it covers:

1. **Discover / connect** to a BLE device (or a demo device on emulator)
2. **Workout UI** with live step count + elapsed time (locally simulated ticks)

Kotlin + Jetpack Compose. Bluetooth Low Energy (BLE) client only — no GATT server.

---

## High-level architecture

```mermaid
flowchart TB
  subgraph ui [UI layer - Compose]
    MA[MainActivity]
    WA[WorkoutApp]
    DS[DevicesScreen]
    WS[WorkoutScreen]
  end

  subgraph domain [App logic]
    BWC[BleWorkoutConnector]
    WD[WorkoutDevice model]
  end

  subgraph android [Android platform]
    BLE[BluetoothLeScanner]
    GATT[BluetoothGatt]
    PERMS[Runtime permissions]
  end

  MA --> WA
  WA --> DS
  WA --> WS
  WA --> BWC
  DS --> BWC
  DS --> WD
  WS --> WD
  BWC --> BLE
  BWC --> GATT
  BWC --> PERMS
```

| Piece | Role |
|-------|------|
| `MainActivity` | Android entry point only (`setContent`) |
| `WorkoutApp` | Owns navigation state + `BleWorkoutConnector` lifetime |
| `DevicesScreen` | Scan UI, permissions, connect / demo connect |
| `WorkoutScreen` | Steps + timer while “connected” |
| `BleWorkoutConnector` | Real BLE scan + GATT connect; exposes `StateFlow`s |
| `WorkoutDevice` | Device id/name + `isSimulated` flag |

---

## Screen flow

Simple state navigation (no Navigation Compose yet): if a device address is saved → workout, else → devices.

```mermaid
stateDiagram-v2
  [*] --> Devices
  Devices --> Connecting: tap real BLE device
  Devices --> Workout: tap demo device
  Connecting --> Workout: GATT Connected
  Connecting --> Devices: Failed / timeout
  Workout --> Devices: Disconnect
```

```text
DevicesScreen  --onDeviceConnected-->  WorkoutScreen
WorkoutScreen  --onDisconnect------>  DevicesScreen
```

---

## BLE connection state machine

`BleConnectionState` mirrors a real GATT attempt:

```mermaid
stateDiagram-v2
  [*] --> Idle
  Idle --> Connecting: connect(address)
  Connecting --> Connected: onConnectionStateChange SUCCESS + STATE_CONNECTED
  Connecting --> Failed: GATT error / drop / UI timeout
  Connected --> Idle: disconnect()
  Failed --> Idle: clearFailure() or new attempt
```

### Scan vs connect (two different BLE jobs)

```mermaid
sequenceDiagram
  participant UI as DevicesScreen
  participant C as BleWorkoutConnector
  participant S as BluetoothLeScanner
  participant G as BluetoothGatt

  UI->>C: startScan() / clearDevices()
  C->>S: startScan(callback)
  S-->>C: onScanResult (many times)
  C-->>UI: devices StateFlow updates

  UI->>C: connect(device)
  C->>S: stopScan()
  C->>G: connectGatt(...)
  Note over C: state = Connecting
  G-->>C: onConnectionStateChange
  alt success
    C-->>UI: Connected → navigate to Workout
  else failure / timeout
    C-->>UI: Failed (message in UI)
  end
```

**Android learning points**

- **Scan** discovers advertisers. It does **not** open a data link.
- **GATT connect** opens a client session to one device’s GATT server.
- Results arrive in **callbacks** (`ScanCallback`, `BluetoothGattCallback`), not as return values from `startScan()` / `connectGatt()`.
- We publish those callbacks into **`StateFlow`** so Compose can collect and recompose.

---

## Package layout

```text
app/src/main/java/com/rick/workoutapp/
├── MainActivity.kt              # Activity shell
├── bluetooth/
│   ├── BleWorkoutConnector.kt   # scan + GATT
│   └── BleConnectionState.kt    # Idle/Connecting/Connected/Failed
├── model/
│   └── WorkoutDevice.kt         # device + demo list
└── ui/
    ├── WorkoutApp.kt            # root composable + nav state
    ├── DevicesScreen.kt
    ├── WorkoutScreen.kt
    └── theme/
```

---

## Android-specific concepts used here

### 1. Activity + Compose

```mermaid
flowchart LR
  OS[Android OS] --> MA[MainActivity.onCreate]
  MA --> SC[setContent]
  SC --> WA[WorkoutApp composable tree]
```

`MainActivity` is the system-visible component. UI lives in Compose, not XML layouts.

### 2. Permissions (Android 12+)

| API level | What we request |
|-----------|-----------------|
| 31+ (S) | `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` |
| 30 and below | `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION` |

Declared in `AndroidManifest.xml`, requested at runtime from `DevicesScreen` via `ActivityResultContracts`.

`BLUETOOTH_SCAN` uses `neverForLocation` so scanning is not treated as a location feature on newer APIs.

### 3. Why demo devices exist

The **official emulator does not pass through PC Bluetooth**. Real scans are usually empty; connects time out.

```mermaid
flowchart TD
  Scan[User taps Scan] --> Clear[Clear scanned list]
  Clear --> BLE{Adapter + permissions OK?}
  BLE -->|yes| Live[Show live BLE results while scanning]
  BLE -->|no / empty after scan| Demo[Show demo step machines]
  Live --> Tap{User taps row}
  Demo --> Tap
  Tap -->|isSimulated| Fake[UI fake delay → Connected]
  Tap -->|real MAC| Gatt[BleWorkoutConnector.connect]
```

### 4. Compose state that survives rotation

`WorkoutApp` stores the connected device with `rememberSaveable` so a configuration change does not kick you back to the device list mid-workout.

### 5. Connector lifetime

```text
remember { BleWorkoutConnector(appContext) }
DisposableEffect → onDispose { connector.release() }
```

Uses `applicationContext` to avoid leaking an Activity. `release()` stops scan and closes GATT.

---

## Planned architecture (not built yet)

Target design when the workout becomes “real”:

```mermaid
stateDiagram-v2
  [*] --> Ready
  Ready --> Active: start workout
  Active --> Active: Foreground Service records metrics
  Active --> PendingSync: finish workout
  PendingSync --> Uploading: WorkManager
  Uploading --> Synced: server accepts
  Uploading --> PendingSync: retry
```

| Piece | Responsibility |
|-------|----------------|
| **Foreground Service** | Owns live session + equipment link while working out |
| **Room** | Durable source of truth (session status, metrics) |
| **WorkManager** | Reliable upload after workout ends |
| **UI** | Observes Room / flows; does not own the session |

Today, steps/timer run **inside `WorkoutScreen`** with a `LaunchedEffect` — fine for UI learning, not for a real background session.

---

## How to run

1. Open the project in Android Studio.
2. Run on an **emulator** → use a **demo** step machine after Scan.
3. Run on a **physical phone** → grant Bluetooth permissions → Scan → tap a real BLE advertiser to attempt GATT connect.

```bash
./gradlew :app:assembleDebug
```

---

## Branch status (learning path)

| Area | Status |
|------|--------|
| Compose workout meter | Done |
| Device list + demo connect | Done |
| Real BLE scan + GATT connect | Done (link only) |
| `discoverServices` / characteristics | Not yet |
| Foreground Service | Not yet |
| Room + WorkManager upload | Not yet |

---

## References

- [BLE overview](https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview)
- Platform samples: `platform-samples/samples/connectivity/bluetooth/ble` (`FindBLEDevicesSample`, `ConnectGATTSample`)
- Older samples: `connectivity-samples/BluetoothLeGatt`
