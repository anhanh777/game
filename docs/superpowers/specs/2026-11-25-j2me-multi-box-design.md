# Technical Design Document: J2ME Multi-Box Runner & Account Manager for Android

**Date**: 2026-11-25  
**Status**: Ready for Final Approval  
**Author**: Antigravity Assistant & User  

---

## 1. Intent & The "Why"
Many users play classic J2ME (Java Micro Edition) mobile online games (e.g., TeaMobi games like Ninja School Online, Knight Online, Dragon Ball, Avatar, etc., as well as other J2ME multiplayer titles). Players often maintain multiple accounts for farming, leveling, and economy building. However:
1. Standard J2ME-Loader emulators only run one game instance at a time and stop when the app is backgrounded or screen turns off.
2. Unofficial custom APKs/MODs distributed in the wild frequently contain malicious keyloggers, backdoor trojans, or hidden credential stealers.
3. Managing dozens of accounts manually with repetitive logins and dealing with disconnections is tedious and error-prone.

This application provides a **clean, secure, open-architecture multi-box runner** based on the open-source J2ME-Loader engine, enabling concurrent multi-account execution, screen-off background running, auto-reconnect, encrypted credential storage, and a universal keyboard macro system.

---

## 2. Goals, Non-Goals, and Anti-Goals

### Goals
- **J2ME Core Compatibility & Input**: Execute standard `.jar` and `.jad` files (both 2D and 3D M3G/Mascot Capsule) with direct touchscreen input (`pointerPressed`/`pointerDragged`/`pointerReleased`), customizable on-screen virtual keypad, and per-game configurable resolutions.
- **Strict Multi-Box Isolation**: Each "Box" functions as an independent sandbox. Game saves, RMS (Record Management System) records, cache, and preferences are strictly isolated per Box ID.
- **Seamless Zoom & Minimize UX**: Main Dashboard displays all active/inactive boxes; tapping a box opens it full-screen (`BoxActivity`) with per-box settings; minimizing returns to Dashboard while keeping the game running in the background.
- **Uninterrupted Background AFK**: Keep games active when minimized, switching applications, or turning off the screen via Foreground Services, Partial WakeLocks, and decoupled headless rendering loops (pausing GPU render while preserving MIDlet tick and TCP/UDP socket network loops).
- **Universal Login Macro Engine**: Generic one-shot keystroke sequence injector (`WAIT`, `PRESS`, `TYPE_TEXT`, `AWAIT_MANUAL_INPUT`) strictly for account login and auto-reconnect, simulating user input directly into the J2ME `Canvas` without modifying game `.jar` bytecode.
- **Enterprise-Grade Security & Account Vault**: Local-only, hardware-backed encrypted storage (Android Keystore + AES-256-GCM / SQLCipher) for game credentials.
- **Multi-Box Dashboard**: Unified screen to monitor status (online/reconnecting/offline, memory, running/paused) + static last-seen snapshot of all active boxes, quick switching, and manual captcha intervention.

### Non-Goals
- Modifying or decompiling/repackaging user `.jar` files on the fly.
- Continuous in-game AFK combat/farming macro loops (v1 macro engine is scoped strictly to one-shot login and reconnect sequences).
- Background anti-bot Captcha detection/OCR while screen is off (Captcha manual intervention triggers only at explicit login macro checkpoints or when user inspects a box).
- Bypassing visual or external Captchas automatically.
- Cloud synchronization of credentials (all data remains 100% on-device).

### Anti-Goals (Security Principles)
- **Zero Third-Party Telemetry**: Absolutely no analytics, advertising SDKs, tracking pixels, or remote telemetry servers.
- **Zero Credential Exfiltration**: Network traffic is strictly limited to the game servers specified inside the `.jar` MIDlet itself. The host app makes zero external calls with user credentials.
- **No Shared Storage for Credentials**: Credentials are never written to external/public storage (`/sdcard/`) or unencrypted shared preferences.

---

## 3. Security & Code Integrity Audit Protocol
As specifically requested by the user, the project enforces strict security verification:
1. **Source Code Auditing (J2ME-Loader Origin)**:
   - Use clean, verified upstream code from `nikita36078/J2ME-Loader` (GPLv3).
   - Strip all default analytics or crash reporting libraries (e.g., ACRA or Firebase).
   - Verify network stack (`javax.microedition.io.Connector`, `SocketConnection`, `HttpConnection`) to ensure socket traffic routes solely to the IP/hostname requested by the MIDlet code.
2. **Account Vault Isolation**:
   - Master keys generated inside Android Keystore (`AndroidKeyStore`), non-exportable.
   - Database encrypted using SQLCipher or EncryptedSharedPreferences (AES-256-GCM).
   - Passwords and usernames are only decrypted in memory at the exact millisecond the macro injector feeds keys to the Canvas, then promptly zeroed out from string buffers.
3. **Permission Minimization**:
   - Only require necessary permissions: `INTERNET` (for online games), `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` / `DATA_SYNC`, `WAKE_LOCK`, `VIBRATE` (haptics), `POST_NOTIFICATIONS`.
   - Never request sensitive Android permissions like `READ_EXTERNAL_STORAGE` (on modern Android, use Storage Access Framework SAF / app-private internal storage), `READ_CONTACTS`, `ACCESS_FINE_LOCATION`, etc.

---

## 4. Approaches Considered & Trade-Offs

### Approach 1: Multi-Process Service Architecture (Selected)
- **Architecture**:
  - Main Process (`:main`): UI, Dashboard, Box Management, Account Vault, Settings.
  - Box Worker Processes (`:box_1`, `:box_2`, ..., `:box_N`): Android Services mapped to distinct process spaces. Each process hosts an instance of the J2ME runtime.
  - Storage: RMS path dynamically parameterized as `/data/user/0/<package>/files/boxes/{box_id}/rms/`.
- **Why Selected**:
  - J2ME-Loader relies on numerous global static singletons (`MIDlet`, `Display`, `Canvas`, sound buffers). Running in separate Android processes provides 100% natural isolation of static memory and JVM states without complex and brittle refactoring.
  - A crash or out-of-memory error in one box will never terminate the main manager or other active farming boxes.
  - Decoupled rendering: when screen turns off, rendering drops to 0 FPS while logic/socket threads continue running under a Partial WakeLock.

### Approach 2: Single-Process In-Memory Context Virtualization (Rejected)
- Requires stripping all static variables across MIDP/CLDC/M3G libraries and passing a synthetic `Context` object everywhere. High risk of memory leaks, race conditions, and an unrecoverable crash in one game terminates all concurrent boxes.

### Approach 3: VirtualApp / Virtual Space Container (Rejected)
- Running an entire second Android runtime inside a virtual container triggers anti-cheat detection, SELinux violations, and breaks on modern Android 12-15 without root.

---

## 5. System Architecture & Detailed Components

```
+-------------------------------------------------------------------------+
|                           ANDROID APPLICATION                           |
+-------------------------------------------------------------------------+
|  [ Main Process :main ]                                                 |
|  - Jetpack Compose UI / Dashboard                                       |
|  - Box Manager (Create, Rename, Delete, Switch, Reorder)                |
|  - Account Vault (Android Keystore + AES-256 Room/SQLCipher)             |
|  - Macro Configuration & Script Editor                                  |
|  - Status Monitor & Captcha Prompt Overlay Manager                      |
+-----------------------------------+-------------------------------------+
                                    | IPC (AIDL / Messenger / Broadcast)
       +----------------------------+----------------------------+
       |                                                         |
+------v-----------------------------+    +----------------------v--------------+
| [ Box Process :box_1 ]             |    | [ Box Process :box_2 ]              |
| - Foreground Service + WakeLock    |    | - Foreground Service + WakeLock     |
| - Decoupled Headless Render Loop   |    | - Decoupled Headless Render Loop    |
| - J2ME Core Engine (MIDlet/Canvas) |    | - J2ME Core Engine (MIDlet/Canvas)  |
| - RMS Sandbox: /boxes/box_1/rms/   |    | - RMS Sandbox: /boxes/box_2/rms/    |
| - Macro Injection Handler          |    | - Macro Injection Handler           |
| - Socket/Network Watchdog          |    | - Socket/Network Watchdog           |
+------------------------------------+    +-------------------------------------+
```

### 5.1. Storage Isolation (Box Sandbox)
- Every Box has a unique UUID / Box ID.
- Storage root:
  - App-private internal storage: `context.getFilesDir() + "/boxes/" + boxId + "/"`
  - RMS records: `.../boxes/{boxId}/rms/`
  - Game configuration (keybindings, resolution, speed): `.../boxes/{boxId}/config.json`
- Total separation ensures no session or account crossover between boxes.

### 5.2. Background Execution & Decoupled Rendering
- **Lifecycle Management**:
  - `BoxRunnerService` runs as an Android `Foreground Service` displaying a persistent notification with box status and quick actions.
  - Acquires a `PowerManager.PARTIAL_WAKE_LOCK` and `WifiManager.WifiLock` to prevent CPU sleep and WiFi disconnection when screen turns off.
- **Render vs. Logic Decoupling**:
  - In standard J2ME Loader, `Canvas` repaints are tied to the Android `SurfaceView` / `TextureView`.
  - When Android triggers `surfaceDestroyed()` (screen off or app switched), standard emulators freeze.
  - In our architecture: When surface is detached, the emulator switches to `HeadlessMode`:
    - `MIDlet` main thread and network worker threads continue ticking normally.
    - Repaint requests (`repaint()`) execute on a 1x1 dummy buffer or are no-oped, consuming near-zero GPU/CPU while maintaining network connection and game state.
    - When user re-opens the Box, the surface re-attaches and rendering resumes seamlessly.

### 5.3. Universal Keyboard Macro Engine
- **Macro Script Format (JSON / DSL)**:
  ```json
  [
    {"action": "WAIT", "durationMs": 2500},
    {"action": "KEY_PRESS", "keyCode": "FIRE"},
    {"action": "WAIT", "durationMs": 800},
    {"action": "KEY_PRESS", "keyCode": "DOWN"},
    {"action": "WAIT", "durationMs": 500},
    {"action": "TYPE_TEXT", "value": "${USERNAME}"},
    {"action": "KEY_PRESS", "keyCode": "FIRE"},
    {"action": "WAIT", "durationMs": 500},
    {"action": "TYPE_TEXT", "value": "${PASSWORD}"},
    {"action": "KEY_PRESS", "keyCode": "FIRE"}
  ]
  ```
- **Text Typing Emulation**:
  - For games that support MIDP `TextBox` or direct Canvas key typing:
  - Macro translates characters into corresponding standard J2ME key codes or fires `keyTyped` / Canvas input hooks.
- **Auto-Reconnect Watchdog (Clean MIDlet Relaunch)**:
  - Watches active socket connection state. If socket closes unexpectedly or idle watchdog triggers timeout without packet exchange:
  - Performs a **clean MIDlet relaunch** (`destroyApp()` -> re-initialize MIDlet -> wait for Canvas mount) so the game deterministically returns to its initial title/login screen without getting stuck on modal error dialogs.
  - Automatically triggers the configured "Auto-Login Macro" to re-authenticate, then zeroes out decrypted credentials from RAM immediately.
- **Captcha Handling (Type A — Login Checkpoint)**:
  - When a login macro hits an explicit `AWAIT_MANUAL_INPUT` step:
  - Main app displays a high-priority heads-up notification: *"Box 1 requires Captcha input"*.
  - User taps notification -> app brings `BoxActivity` for Box 1 to foreground (full-screen) -> user types Captcha via virtual keypad or touchscreen -> user taps Minimize to return Box 1 to headless background AFK mode.

### 5.4. Account Vault & Security Architecture
- **Room Database / EncryptedSharedPreferences** secured with **SQLCipher** and **Android KeyStore**.
- Database schema:
  - `boxes` table: `id`, `name`, `game_id`, `created_at`, `status`, `notes`.
  - `accounts` table: `id`, `box_id`, `encrypted_username`, `encrypted_password`, `extra_fields_json`, `iv`.
- No plain-text passwords stored anywhere on the disk.
- Biometric lock (Fingerprint / PIN) option for opening the Account Vault in the app.

---

## 6. Architectural Decisions (Settled Calls)
1. **Per-Box Interactive Activity (`BoxActivity` inside `:box_N`)**: Each active box runs its own `BoxActivity` in the same `:box_N` process as its `BoxRunnerService`. Tapping a Box on the Dashboard launches its `BoxActivity` full-screen (providing zero-latency J2ME-Loader native rendering, touchscreen input, virtual keypad, and per-box settings). Clicking "Minimize" closes/backgrounds `BoxActivity` back to the Dashboard while `BoxRunnerService` keeps the MIDlet running in headless mode.
2. **Clean MIDlet Relaunch on Disconnect**: Instead of injecting blind keystrokes into unknown modal disconnect dialogs, the watchdog cleanly restarts the MIDlet before running the Auto-Login Macro.
3. **In-App Captcha Flow (No `SYSTEM_ALERT_WINDOW`)**: Captcha notifications open the target `BoxActivity` inside the app directly, keeping permissions minimal and avoiding Android overlay keyboard focus restrictions.
4. **Dashboard Metadata + Static Snapshot**: Dashboard cards show real-time IPC status badges (Online, Reconnecting, Offline, RAM) plus a static thumbnail captured when the user last minimized that Box, allowing background boxes to stay at 0 FPS to maximize battery life.

## 7. What's Left to the Builder (Implementation Decisions)
- Exact IPC mechanism choice between Main and Box processes (Bound Service with AIDL vs. Messenger with IPC Bundles).
- Maximum concurrent active box limit (recommended 4-8 on mid-range devices to balance RAM and battery).
- UI Framework: Jetpack Compose for modern, clean Android UI and smooth Dashboard management.
- Android Target SDK: Android 14+ (API level 34/35), compatible down to Android 8.0 (API 26).

---

## 8. Next Steps
1. User review and approval of this final Design Doc.
2. Writing execution plan with `writing-plans`.
