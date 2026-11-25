# J2ME Multi-Box Runner & Account Manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a secure, multi-process Android application based on the J2ME-Loader core that runs multiple isolated `.jar`/`.jad` Java game accounts ("Boxes") concurrently in the background, supports universal login/reconnect keyboard macros, hardware-encrypted credential storage, and seamless Dashboard <-> Full-Screen Zoom <-> Minimize UX.

**Architecture:** Main UI process (`:main`) hosts the Jetpack Compose Dashboard, encrypted Account Vault, and Macro Editor. Up to 8 concurrent Box Worker processes (`:box_1`..`:box_8`) each host a `BoxRunnerService` (Foreground Service + Partial WakeLock + WifiLock) and a dedicated `BoxActivity` for zero-latency native rendering, touchscreen input, and virtual keypad overlay. When minimized or screen-off, `BoxActivity` detaches the Surface and `BoxRunnerService` switches to 0 FPS headless mode while keeping MIDlet logic and network sockets alive.

**Tech Stack:** Kotlin, Java (J2ME-Loader Core / MIDP / CLDC / M3G), Android SDK (API 26–35), Jetpack Compose, Room + SQLCipher, AndroidKeyStore (AES-256-GCM), Kotlin Coroutines, JUnit4/5 + MockK.

**Spec:** [2026-11-25-j2me-multi-box-design.md](file:///e:/appgame/docs/superpowers/specs/2026-11-25-j2me-multi-box-design.md)

## Global Constraints
- Minimum SDK: API 26 (Android 8.0); Target SDK: API 34/35.
- Zero third-party telemetry, analytics, crash reporting (no Firebase, no ACRA), or ad SDKs.
- Minimal permissions only: `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`, `VIBRATE`, `POST_NOTIFICATIONS`. Never request `SYSTEM_ALERT_WINDOW`, `READ_EXTERNAL_STORAGE`, or location/contacts.
- Credentials must never be stored in plaintext on disk or in `/sdcard/`. Decrypted credentials in RAM must use `CharArray` and be zeroed out immediately after macro injection.
- Macro engine v1 is strictly one-shot sequential login & auto-reconnect (no continuous combat/farming loops).
- Captcha intervention is Type A (pauses macro at `AWAIT_MANUAL_INPUT`, notifies user, opens `BoxActivity` in-app).

## Review Focus
- **Corrupted or unreadable `.jar`/`.jad` files**: Importing a malformed archive must fail gracefully with a user-visible error without creating an orphaned Box directory.
- **Process slot exhaustion**: Launching a 9th Box when all 8 `:box_1..:box_8` process slots are occupied must return a clear capacity error rather than crashing or overwriting an active Box.
- **Credential memory wiping on macro abort**: If a macro is cancelled or throws mid-execution (e.g., during `AWAIT_MANUAL_INPUT` or disconnect), decrypted `CharArray` buffers must still be zeroed out in a `finally` block.
- **Rapid Zoom-In / Minimize toggling**: Repeatedly attaching and detaching `BoxActivity` Surface while `BoxRunnerService` is running must not deadlock the MIDlet render loop or leak `Surface` references.
- **Clean MIDlet relaunch loop**: If network is completely down, `ReconnectWatchdog` must use exponential backoff rather than tight-looping MIDlet restarts and draining battery.

---

### Task 1: Project Scaffolding, Gradle Setup & Manifest Process Slots

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/j2me/multibox/MultiBoxApp.kt`

**Interfaces:**
- Produces: Gradle project structure, `com.j2me.multibox` namespace, 8 isolated `:box_1`..`:box_8` Service/Activity pairs in `AndroidManifest.xml`.

- [ ] **Step 1: Create root Gradle configuration**
  Write `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` with Kotlin 1.9+, Android Gradle Plugin 8.x, Compose compiler, and standard repositories (`google()`, `mavenCentral()`).

- [ ] **Step 2: Create `app/build.gradle.kts`**
  Configure `minSdk = 26`, `targetSdk = 34`, `compileSdk = 34`, Jetpack Compose, Room, SQLCipher (`net.zetetic:android-database-sqlcipher`), Coroutines, Security-Crypto, and JUnit/MockK test dependencies. Exclude any telemetry/analytics artifacts.

- [ ] **Step 3: Create `app/src/main/AndroidManifest.xml`**
  Declare minimal permissions (`INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`, `VIBRATE`, `POST_NOTIFICATIONS`), `MainActivity` in default process, and 8 pairs of `BoxRunnerService1..8` + `BoxActivity1..8` mapped to `android:process=":box_1"` through `":box_8"`.

- [ ] **Step 4: Create `MultiBoxApp.kt`**
  Implement `Application` subclass with NotificationChannel creation (`box_runner_channel`, `captcha_alert_channel`) and process-aware initialization (skipping main UI database setup inside `:box_N` worker processes).

- [ ] **Step 5: Verify project structure & commit**
  Run `./gradlew tasks` (if Gradle wrapper/SDK available) or verify syntax, then commit:
  `git add settings.gradle.kts build.gradle.kts gradle.properties app/`
  `git commit -m "chore: scaffold Android multi-process project with minimal permissions"`

---

### Task 2: Box Storage Sandbox & Isolation Layer (`BoxStorageManager`)

**Files:**
- Create: `app/src/main/kotlin/com/j2me/multibox/storage/BoxStorageManager.kt`
- Create: `app/src/main/kotlin/com/j2me/multibox/storage/BoxConfig.kt`
- Test: `app/src/test/kotlin/com/j2me/multibox/storage/BoxStorageManagerTest.kt`

**Interfaces:**
- Produces:
  - `BoxStorageManager(rootDir: File)`
  - `createBoxSandbox(boxId: String): BoxPaths`
  - `deleteBoxSandbox(boxId: String): Boolean`
  - `getRmsDir(boxId: String): File`
  - `saveConfig(boxId: String, config: BoxConfig)`
  - `loadConfig(boxId: String): BoxConfig`
  - `importJarFile(gameId: String, inputStream: InputStream): File` (validates ZIP/JAR header `PK` before saving)

- [ ] **Step 1: Write failing tests in `BoxStorageManagerTest.kt`**
  - `test_rms_isolation_between_boxes`: Creating `box-1` and `box-2` returns distinct `rms/` directories; writing a file to `box-1` does not appear in `box-2`.
  - `test_delete_box_removes_all_data`: Deleting `box-1` recursively deletes `rms/` and `config.json` without affecting `box-2`.
  - `test_import_corrupted_jar_throws_and_cleans_up`: Passing non-ZIP bytes to `importJarFile` throws `IllegalArgumentException` and leaves no orphaned file on disk.

- [ ] **Step 2: Run tests to verify failure**
  Run: `./gradlew testDebugUnitTest --tests "com.j2me.multibox.storage.BoxStorageManagerTest"`

- [ ] **Step 3: Implement `BoxConfig.kt` and `BoxStorageManager.kt`**
  Implement sandbox directory creation under `rootDir/boxes/{boxId}/rms`, JSON config serialization, and JAR magic-byte (`0x50 0x4B`) validation.

- [ ] **Step 4: Run tests to verify pass**
  Run: `./gradlew testDebugUnitTest --tests "com.j2me.multibox.storage.BoxStorageManagerTest"` -> Expected: PASS.

- [ ] **Step 5: Commit**
  `git commit -m "feat: add isolated BoxStorageManager with JAR validation"`

---

### Task 3: Hardware-Backed Encrypted Account Vault (`AccountVault`)

**Files:**
- Create: `app/src/main/kotlin/com/j2me/multibox/security/KeystoreCrypto.kt`
- Create: `app/src/main/kotlin/com/j2me/multibox/data/db/AppDatabase.kt`
- Create: `app/src/main/kotlin/com/j2me/multibox/data/db/Entities.kt`
- Create: `app/src/main/kotlin/com/j2me/multibox/data/db/Daos.kt`
- Create: `app/src/main/kotlin/com/j2me/multibox/security/AccountVault.kt`
- Test: `app/src/test/kotlin/com/j2me/multibox/security/AccountVaultTest.kt`

**Interfaces:**
- Consumes: `BoxStorageManager`
- Produces:
  - `CryptoProvider` interface (`encrypt(plain: CharArray): EncryptedPayload`, `decrypt(payload: EncryptedPayload): CharArray`)
  - `KeystoreCrypto : CryptoProvider` (AndroidKeyStore AES-256-GCM)
  - `AccountVault(accountDao: AccountDao, crypto: CryptoProvider)`
  - `saveCredentials(boxId: String, username: CharArray, password: CharArray, note: String)`
  - `withDecryptedCredentials(boxId: String, block: (username: CharArray, password: CharArray) -> Unit)` (guarantees `Arrays.fill(..., '\0')` in `finally`)

- [ ] **Step 1: Write failing tests in `AccountVaultTest.kt`**
  - `test_credentials_encrypted_on_save_and_zeroed_after_use`: Uses an in-memory `CryptoProvider` fake; verifies `AccountEntity` stored in DAO contains ciphertext (not plaintext), `withDecryptedCredentials` passes accurate credentials to lambda, and zeroes both `CharArray`s immediately after `block` completes (even if `block` throws).

- [ ] **Step 2: Run tests to verify failure**

- [ ] **Step 3: Implement `KeystoreCrypto.kt`, `Entities.kt`, `Daos.kt`, `AppDatabase.kt`, and `AccountVault.kt`**
  Use `AES/GCM/NoPadding` with 12-byte random IV per encryption and `try { block(...) } finally { username.fill('\u0000'); password.fill('\u0000') }`.

- [ ] **Step 4: Run tests to verify pass**

- [ ] **Step 5: Commit**
  `git commit -m "feat: add hardware-backed AES-256-GCM AccountVault with memory wiping"`

---

### Task 4: Universal Keyboard Macro Engine (`MacroRunner`)

**Files:**
- Create: `app/src/main/kotlin/com/j2me/multibox/macro/MacroStep.kt`
- Create: `app/src/main/kotlin/com/j2me/multibox/macro/KeyInjector.kt`
- Create: `app/src/main/kotlin/com/j2me/multibox/macro/MacroRunner.kt`
- Test: `app/src/test/kotlin/com/j2me/multibox/macro/MacroRunnerTest.kt`

**Interfaces:**
- Consumes: `AccountVault.withDecryptedCredentials`
- Produces:
  - `sealed class MacroStep`: `Wait(durationMs: Long)`, `KeyPress(keyCode: Int, holdMs: Long = 80)`, `TypeText(template: String)`, `AwaitManualInput(prompt: String)`
  - `interface KeyInjector { fun pressKey(keyCode: Int); fun releaseKey(keyCode: Int); fun typeChar(ch: Char) }`
  - `MacroRunner(injector: KeyInjector)`
  - `suspend fun executeLoginMacro(steps: List<MacroStep>, username: CharArray, password: CharArray, onAwaitManualInput: suspend (String) -> Unit)`

- [ ] **Step 1: Write failing tests in `MacroRunnerTest.kt`**
  - `test_login_macro_substitutes_credentials_and_injects_keys`: Verifies `${USERNAME}` and `${PASSWORD}` are typed character-by-character via `KeyInjector` and `KeyPress` fires press+release.
  - `test_await_manual_input_pauses_until_resumed`: Verifies `onAwaitManualInput` suspends execution until user signals resume.
  - `test_cancellation_during_macro_does_not_leak_credentials`: Cancelling coroutine mid-macro still completes cleanly.

- [ ] **Step 2: Run tests to verify failure**

- [ ] **Step 3: Implement `MacroStep.kt`, `KeyInjector.kt`, `MacroRunner.kt`**
  Include JSON serialization/deserialization helpers (`fromJson` / `toJson`).

- [ ] **Step 4: Run tests to verify pass**

- [ ] **Step 5: Commit**
  `git commit -m "feat: add universal keyboard MacroRunner with AwaitManualInput support"`

---

### Task 5: Multi-Process Slot Manager, `BoxRunnerService` & `ReconnectWatchdog`

**Files:**
- Create: `app/src/main/kotlin/com/j2me/multibox/ipc/BoxSlotManager.kt`
- Create: `app/src/main/kotlin/com/j2me/multibox/ipc/BoxState.kt`
- Create: `app/src/main/kotlin/com/j2me/multibox/service/HeadlessRenderController.kt`
- Create: `app/src/main/kotlin/com/j2me/multibox/service/ReconnectWatchdog.kt`
- Create: `app/src/main/kotlin/com/j2me/multibox/service/BoxRunnerService.kt`
- Test: `app/src/test/kotlin/com/j2me/multibox/ipc/BoxSlotManagerTest.kt`
- Test: `app/src/test/kotlin/com/j2me/multibox/service/ReconnectWatchdogTest.kt`

**Interfaces:**
- Consumes: `BoxStorageManager`, `MacroRunner`
- Produces:
  - `BoxSlotManager(maxSlots = 8)`: `allocateSlot(boxId: String): Int?`, `releaseSlot(boxId: String)`, `getSlotForBox(boxId: String): Int?`
  - `HeadlessRenderController`: `attachSurface(surface)`, `detachSurface()`, `isHeadless(): Boolean`
  - `ReconnectWatchdog`: `onSocketDisconnected()`, `onNetworkActivity()`, exponential backoff (2s, 4s, 8s, max 60s) triggering `onRelaunchRequested()`.
  - `BoxRunnerService` (abstract base + `BoxRunnerService1..8` subclasses) with `PARTIAL_WAKE_LOCK`, `WifiLock`, Foreground notification, and Messenger/Broadcast IPC status updates.

- [ ] **Step 1: Write failing tests in `BoxSlotManagerTest.kt` & `ReconnectWatchdogTest.kt`**
  - `test_allocate_up_to_8_slots_9th_returns_null`
  - `test_release_slot_allows_reallocation`
  - `test_watchdog_exponential_backoff_on_repeated_disconnects`

- [ ] **Step 2: Run tests to verify failure**

- [ ] **Step 3: Implement `BoxSlotManager.kt`, `BoxState.kt`, `HeadlessRenderController.kt`, `ReconnectWatchdog.kt`, `BoxRunnerService.kt`**

- [ ] **Step 4: Run tests to verify pass**

- [ ] **Step 5: Commit**
  `git commit -m "feat: add multi-process BoxSlotManager, BoxRunnerService, and ReconnectWatchdog"`

---

### Task 6: J2ME-Loader Core Integration & `BoxActivity` (Zoom-In / Minimize)

**Files:**
- Create: `app/src/main/java/javax/microedition/...` (or `j2me-core` module / source integration from clean upstream J2ME-Loader)
- Create: `app/src/main/kotlin/com/j2me/multibox/emulator/J2meEngineAdapter.kt`
- Create: `app/src/main/kotlin/com/j2me/multibox/ui/box/BoxActivity.kt` (and `BoxActivity1..8`)
- Create: `app/src/main/kotlin/com/j2me/multibox/ui/box/VirtualKeypadOverlay.kt`

**Interfaces:**
- Consumes: `BoxStorageManager.getRmsDir`, `HeadlessRenderController`, `KeyInjector`, `MacroRunner`
- Produces:
  - `J2meEngineAdapter`: Boots MIDlet from `.jar`/`.jad` using isolated RMS directory, exposes `KeyInjector`, `attachSurface()`, `detachSurface()`, `captureSnapshot(): Bitmap?`, and `relaunchMidlet()`.
  - `BoxActivity`: Full-screen game view supporting direct touchscreen pointer events, toggleable/customizable `VirtualKeypadOverlay`, per-box resolution settings sheet, and **Minimize** button (captures thumbnail, switches engine to headless mode, returns user to `MainActivity` Dashboard without stopping `BoxRunnerService`).

- [ ] **Step 1: Integrate clean J2ME-Loader core (MIDP/CLDC/M3G/Mascot Capsule)**
  Ensure RMS `RecordStore` file path resolves via `BoxStorageManager.getRmsDir(boxId)` instead of global static path, and strip any crash/analytics hooks.

- [ ] **Step 2: Implement `J2meEngineAdapter.kt`**
  Connect `KeyInjector` directly to `Canvas.keyPressed`/`keyReleased` and wire `HeadlessRenderController` so `repaint()` skips GPU draw when `isHeadless() == true`.

- [ ] **Step 3: Implement `VirtualKeypadOverlay.kt` & `BoxActivity.kt`**
  Support both touchscreen pass-through to J2ME Canvas and virtual D-Pad/NumPad/SoftKeys overlay, plus the Minimize button and per-box resolution dialog.

- [ ] **Step 4: Commit**
  `git commit -m "feat: integrate J2ME core adapter and BoxActivity with touch + virtual keypad"`

---

### Task 7: Jetpack Compose Dashboard, Account Manager & Macro Editor UI

**Files:**
- Create: `app/src/main/kotlin/com/j2me/multibox/ui/MainActivity.kt`
- Create: `app/src/main/kotlin/com/j2me/multibox/ui/dashboard/DashboardScreen.kt`
- Create: `app/src/main/kotlin/com/j2me/multibox/ui/dashboard/DashboardViewModel.kt`
- Create: `app/src/main/kotlin/com/j2me/multibox/ui/accounts/AccountVaultScreen.kt`
- Create: `app/src/main/kotlin/com/j2me/multibox/ui/macro/MacroEditorScreen.kt`

**Interfaces:**
- Consumes: `BoxSlotManager`, `BoxStorageManager`, `AccountVault`, `AppDatabase`
- Produces:
  - `DashboardScreen`: Grid/list of Boxes showing status badge (Online / Reconnecting / Captcha Needed / Offline), static last-seen snapshot, notes, and actions (Start/Stop, Zoom In -> `BoxActivity`, Rename, Delete, Import `.jar`).
  - `AccountVaultScreen`: Encrypted account/password manager per Box with notes and one-tap "Auto-Login" trigger.
  - `MacroEditorScreen`: Visual editor for creating/editing login macro steps (`WAIT`, `KEY_PRESS`, `TYPE_TEXT`, `AWAIT_MANUAL_INPUT`).

- [ ] **Step 1: Implement `DashboardViewModel.kt` & `DashboardScreen.kt`**
  Wire IPC status updates from active `BoxRunnerService` processes and handle Captcha notification deep-links straight to the target `BoxActivity`.

- [ ] **Step 2: Implement `AccountVaultScreen.kt` & `MacroEditorScreen.kt`**

- [ ] **Step 3: Commit**
  `git commit -m "feat: add Jetpack Compose Dashboard, AccountVault, and MacroEditor screens"`

---

### Task 8: Security Audit Verification, Build Check & Push to GitHub

**Files:**
- Verify: `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, all network/storage files.

- [ ] **Step 1: Run all unit tests**
  Run: `./gradlew testDebugUnitTest` -> Verify all tests pass.

- [ ] **Step 2: Security Audit Check**
  Verify zero occurrences of third-party analytics, `SYSTEM_ALERT_WINDOW`, plaintext credential writes, or unauthorized network hosts.

- [ ] **Step 3: Build Debug APK**
  Run: `./gradlew assembleDebug`

- [ ] **Step 4: Configure Git Remote & Push**
  `git remote add origin https://github.com/anhanh777/game.git`
  `git push -u origin main`
