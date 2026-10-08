# AGENTS.md — Power++

This is the single source of truth for Power++. An engineer or LLM reading only this file should be able to continue exactly where the last session stopped. Update it whenever a decision is made, a question is asked or answered, or work starts or finishes.

## 0. Resume here

- **Current phase:** scaffolding the Gradle project (§5). The toolchain is installed (§6).
- **Next action:** finish the scaffold and get it compiling. Then build the overlay + page/module framework (§2) before any v1 module (§3).
- **Code ownership:** all Power++ code is written from scratch. XGesture is only read to learn behavior and which APIs it uses; nothing is copied from it.
- **Repository:** `/home/mc/Downloads/Github_Projects/Power++`, branch `main`, no remote yet. Commits are authored as `Bo0sted <mat+github@inboxmc.com>` (the machine's global git identity).
- **Reference app:** a local clone of XGesture at `/home/mc/Downloads/Github_Projects/XGesture` (`github.com/qpst4/XGesture`, AGPL-3.0, package `com.slideindex.app`). Power++ takes ideas and end-user behavior from it but is written independently. No XGesture code is copied verbatim; where Android offers a better or official way, use it. Pointers to the relevant XGesture files are in §9.

## 1. What the app is

Power++ makes the power button more powerful. **Pressing and holding power** opens a full-screen Power++ overlay instead of the assistant or the system power menu. The overlay shows **pages of modules**, laid out like a home screen. Each module is a button that triggers a feature, or a toggle.

- **Package:** `com.bo0sted.powerplusplus`
- **Language:** English only for v1.
- **Primary test device:** Pixel 9 Pro XL, Android 17 (API 37).
- **Compatibility goal:** run on as many devices as possible, but never by compromising the design.

## 2. Core design

### 2.1 Trigger: power-button long-press
- Power++ registers as the device's **Digital assistant** (`VoiceInteractionService` + `VoiceInteractionSessionService`, role `android.app.role.ASSISTANT`).
- The user sets *Settings → System → Gestures → Press & hold power button → Digital assistant*. On Pixel and AOSP-like Android 12+ devices, a long-press then starts our session.
- When the session is shown, it immediately hands off to the overlay (§2.2) and finishes itself.
- **No root and no LSPosed.** The user rejected them because the platform supports this natively.
- **Devices where power long-press can't open a third-party assistant (e.g. Samsung One UI) are not supported in v1.** There is no fallback trigger. A later version may add one.
- Taking over the assistant role replaces Gemini/Google Assistant. The user accepts this.

### 2.2 Overlay
- **Blurred background** by default, with an optional setting for a **dimmed black** background (better performance).
- The overlay must automatically fall back to dim whenever cross-window blur is unavailable, for example in battery saver. Use `WindowManager.isCrossWindowBlurEnabled` and `addCrossWindowBlurEnabledListener`, and switch live.
- Dismissed by tapping outside the modules or pressing Back.
- **Pages:** each page is a **grid of single-cell modules**, like a launcher home screen. Swipe between pages, up/down by default; left/right is a setting. Each page holds the modules the user assigns to it. Keep the data model open to multi-cell modules later, but v1 is 1×1 only.
- **Window type (decided):** `TYPE_ACCESSIBILITY_OVERLAY`, added through the WindowManager of our connected AccessibilityService. **Power++ does not request `SYSTEM_ALERT_WINDOW`.** This was verified in AOSP:
  - `PhoneWindowManager.checkAddPermission` lets `TYPE_ACCESSIBILITY_OVERLAY` through, gated only by the `OP_CREATE_ACCESSIBILITY_OVERLAY` app-op on newer releases, and never by `SYSTEM_ALERT_WINDOW`.
  - The `TYPE_APPLICATION_OVERLAY` Javadoc is what requires `SYSTEM_ALERT_WINDOW`.
  - XGesture asks for both only because many of its features work with accessibility turned off, so it falls back to `TYPE_APPLICATION_OVERLAY` (see `overlay/OverlayWindowTypes.kt`). Power++ can't run at all without its accessibility service, so it has no such fallback.

### 2.3 Modules
- A module is **a button (action) or a toggle**.
- Every module can be **enabled or disabled** individually.
- Modules are **added, configured, removed and moved** from inside the app.
- **Extensibility is a hard requirement.** A module = a stable type id, a config schema, and a handler, registered in a registry. Nothing else in the app should know about specific module types. Adding a module type must not require touching the overlay, pages or settings code.

### 2.4 Settings
- Every setting saves and **takes effect immediately**. There are no "Save" or "Apply" buttons.
- Settings are stored in DataStore and exposed as `Flow`. The running overlay and services observe them live.

## 3. v1 modules

### 3.1 Power menu
- Opens the **system** power menu through `AccessibilityService.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)`.
- Power off, restart, lockdown and emergency are **not** reimplemented; the system menu provides them. (§8 explains why.)
- It's an ordinary module, placed on a page like any other.

### 3.2 Accessibility text search
- Reads on-screen text from the accessibility node tree. Tappable boxes are drawn over each text item, and the user selects text and acts on it.
- The final look and behavior should be **very similar to XGesture's** text pick and result panel (§9).
- **v1 offers a single action on selected text: Copy.** More actions (search, translate…) may come later.

### 3.3 OCR text search
A separate module from 3.2. The flow:
1. Tapping the module shows a **draggable cursor**.
2. **Double-tapping** the screen starts screenshot capture. The user drags to choose the region, the same way XGesture does it.
3. When dragging stops, **Confirm** and **Reset** buttons appear. Reset starts a new capture; Confirm runs OCR.
4. The results show as **clickable boxes**, the same as accessibility text search.

### 3.4 Smart screenshot
- Based on XGesture's smart screenshot, especially **Copy**: the image goes to the clipboard without being saved to the gallery.
  - How it works: the PNG is written to the app-private `cacheDir/screenshots/`, exposed via `FileProvider`, and the clipboard gets `ClipData.newUri`. Android's clipboard can't hold raw bitmap bytes. XGesture does exactly this (`ScreenshotStorage.kt`); the user had doubted it, and the code confirmed it.
  - Improvement over XGesture, which never deletes these files: prune old clipboard images automatically.
- **Editor tools (v1):** pen (color and width), blur, pixelate, undo/redo, crop, arrows, shapes, text.
- **Capture method:** `AccessibilityService.takeScreenshot` (API 30+). It needs no MediaProjection consent prompt. Hide the overlay before capture. Secure (FLAG_SECURE) windows capture as black.

## 4. OCR engines and models

- **Nothing is bundled.** Both the inference runtime and every model are downloaded, kept on disk, and removable in the app.
- **First model:** PP-OCRv6 Medium (detection ONNX + recognition ONNX + `inference.yml` dictionary, about 139 MB).
  - **Primary source:** `huggingface.co/PaddlePaddle`, the official PaddlePaddle organization. URLs should be pinned to a commit, not `main`. Current XGesture URLs and hashes (useful as a starting point and for cross-checking) are in XGesture's `core/ocr/src/main/assets/ocr_models.json`, entry `ppocrv6-medium`.
  - **Mirror:** ModelScope (also official PaddlePaddle). It's unconfirmed whether its files are byte-identical to Hugging Face's; this must be checked.
- **Adding sources:** the catalog lets a model list several mirror URLs, so new sites can be added later. The user wants this.
- **Runtime (proposed; the user questioned it, see §7):** an on-device inference engine is required to run any `.onnx` model.
  - The proposal is ONNX Runtime: an open-source (MIT) library published by Microsoft on Maven Central (`com.microsoft.onnxruntime:onnxruntime-android`). It runs entirely in-process, offline.
  - It's proposed because PaddlePaddle publishes PP-OCRv6 officially as ONNX, so the official files and hashes are used as-is, with no conversion.
  - **Decided: ONNX Runtime** (user accepted 2026-10-08).
  - **How the downloaded engine is loaded.** This follows XGesture's approach, verified against ORT 1.30's `OnnxRuntime.java`:
    - On Android, `OnnxRuntime.init()` only calls `System.loadLibrary("onnxruntime4j_jni")`. It never loads `libonnxruntime.so` by name; the glue library pulls it in as a `DT_NEEDED` dependency.
    - So the APK ships the ORT Java classes plus the small glue library, and packaging excludes `**/libonnxruntime.so`.
    - Before first use, the downloaded `libonnxruntime.so` (arm64, taken from the official `onnxruntime-android` AAR on Maven Central and checked by SHA-256) is made read-only and loaded with `System.load(absolutePath)`.
    - When the glue library loads afterwards, the dynamic linker finds an already-loaded library with that soname and reuses it.
    - The glue library has no inference ability without the downloaded engine.
    - **No NDK or own JNI is needed.** (The NDK and CMake were installed into `~/Android/Sdk` before this was understood; they're unused and can be removed.)
    - The glue library and the engine **must come from the same ORT version**. Pin both in one place.
  - Pre- and post-processing (DBNet boxes, CTC decoding) is done in Kotlin, so OpenCV isn't needed.
  - Alternatives, each requiring converted, non-official model files:
    - Paddle Lite (Baidu, PaddleOCR's own mobile engine, `.nb` format)
    - NCNN (Tencent)
    - MNN (Alibaba)
    - LiteRT (Google)
- **Checking downloads (two layers).** The user doesn't know the model file formats and wants every download checked.
  1. **Hash pinning:** every file in the catalog has a fixed SHA-256 and size; a download that doesn't match is discarded. This is what XGesture does (per-file SHA-256 plus a minimum size, then a `manifest.json` written on install). It means a file can only be a known-good artifact, whichever mirror served it.
  2. **Load check:** after download, an ONNX Runtime session is opened and the input/output signature is checked against what the pipeline expects. The recognition output size must match the dictionary. The model is marked installed only if this passes.
- **Android 17 note:** with `targetSdk >= 37`, `System.load()` rejects writable files. Make downloaded `.so` files read-only before loading.

## 5. Tech stack

- Kotlin, Jetpack Compose, Hilt, DataStore, Gradle version catalog.
- Multi-module: `app`, `core:*`, and one `module:*` per feature.
- minSdk 31, compileSdk 37, targetSdk 37.
- Use the latest stable AGP, Kotlin and Compose BOM at scaffold time; check them, don't copy versions from XGesture.

## 6. Build environment

**Machine:** Arch Linux. The user requires tools from **Arch's official repos only, never the AUR**.

**State as of 2026-10-08:**
- Only OpenJDK 17 is installed (the default).
- No Android SDK (`~/Android/Sdk` doesn't exist, `ANDROID_HOME` is unset).
- `adb` is at `/usr/bin/adb`, from `extra/android-tools`.
- No device is attached.

**Installed and decided (2026-10-08):**
- **JDK 21 LTS** from `extra/jdk21-openjdk`, at `/usr/lib/jvm/java-21-openjdk`. The system default is still JDK 17. Gradle's daemon JVM is pinned to 21 via `gradle/gradle-daemon-jvm.properties`, and JDK auto-download is disabled (`org.gradle.java.installations.auto-download=false`), since the user allows only Arch-official tooling.
- **Android SDK** directly from Google, never the AUR. It lives in `~/Android/Sdk` (`local.properties` → `sdk.dir`, gitignored). Installed:
  - `cmdline-tools;latest` (23.0, SHA-1 verified against Google's `repository2-3.xml`)
  - `platforms;android-37.0`
  - `build-tools;37.0.0`
  - `ndk;30.0.16248370` and `cmake;4.1.2`, which are unused (§4)
  - The SDK licenses were accepted with the user's approval.
- **Gradle:** the wrapper, pinned to **9.8.1** with `distributionSha256Sum`. It was generated from the official distribution, SHA-256 verified.
- **Versions at scaffold time:** see `gradle/libs.versions.toml` (AGP 9.4.1, Kotlin 2.4.21, KSP 2.3.12, Hilt 2.60.1, Compose BOM 2026.09.00).
- **IDE:** Android Studio is optional and up to the user. The project builds from the CLI with `./gradlew`.
- **Device testing:** the Pixel over adb (`/usr/bin/adb` from `extra/android-tools`). Ask the user for device-side information only as a last resort.
- Every Android build pulls AGP, AndroidX and Kotlin libraries from Google's Maven repository and Maven Central. Arch doesn't package these, so this is unavoidable.

## 7. Open questions waiting on the user

None right now.

## 8. Decision log

All decisions are from 2026-10-08.

- **Root/LSPosed hook of power long-press:** rejected. The Digital-assistant role is used instead.
- **Power, restart, lockdown and emergency as separate Power++ buttons:** dropped. The user first wanted Power++'s own buttons for these, calling the system's own functions and placeable top/left/right/bottom. Those need Shizuku/root (`IPowerManager.shutdown/reboot`, and `LockPatternUtils.requireStrongAuth` for lockdown). The user then chose a single module that opens the system power menu ("no need to reinvent the wheel"). The top/left/right/bottom positioning is gone with it.
- **Reusing XGesture code:** no. XGesture is inspiration only.
- **Bundling an OCR engine:** no. Everything is downloaded.
- **Accessibility and OCR text search:** two separate modules.
- **Background:** blur by default, dim as an option.
- **Language:** English only.
- **Agent notes file:** named `AGENTS.md`.
- **Overlay permission:** no `SYSTEM_ALERT_WINDOW`; use an accessibility overlay. The user's condition was that accessibility had to be enough, and this was verified in AOSP (§2.2).
- **Page grid:** single-cell modules in a launcher-like grid; swipe direction configurable.
- **Unsupported devices:** no fallback trigger in v1 (e.g. Samsung).
- **Text search actions in v1:** Copy only.
- **Build tools:** Arch official repos only, never the AUR. The Android SDK comes directly from Google, the only non-AUR source, which the user approved. Gradle comes from the wrapper.
- **OCR inference engine:** ONNX Runtime.
- **Gemini/Assistant:** losing them is accepted.

## 9. XGesture reference map

Paths are relative to `/home/mc/Downloads/Github_Projects/XGesture/app/src/main/java/com/slideindex/app/`. Read them for behavior; don't copy them.

| Feature | Where to look |
|---|---|
| Text pick: on-screen boxes, cursor, region | `overlay/FloatBallTextPick*.kt`, `overlay/RegionalPickOverlay.kt`, `overlay/FloatBallOcrRegions.kt`, `overlay/FloatBallCursorPreviewView.kt`, `overlay/OverlayAccessibility.kt` |
| Result panel: selectable words, actions | `overlay/FloatBallPickResult*.kt`, `overlay/pickresult/` |
| Smart screenshot | `overlay/screenshot/` (`SmartScreenshotOverlay.kt`, `SmartScreenshotEditor.kt`, `ScreenshotCropper.kt`, `ScreenshotStorage.kt`) |
| Screen capture | `util/ScreenCaptureHelper.kt`, `service/RegionalScreenshotOcr.kt` |
| OCR model download and checks | `core/ocr/src/main/java/com/slideindex/app/ocr/` (`OcrModelCatalog.kt`, `OcrModelDownloader.kt`, `OcrModelRepository.kt`, `OcrModelDownloadSupport.kt`) and `core/ocr/src/main/assets/ocr_models.json` (paths from the repo root) |

**Security lessons from reviewing XGesture. Don't repeat them:**
- Its root mode runs an **unauthenticated localhost RPC as root** (`privilege/root/TaskManagerRpcServer.kt`), so any app can run root commands.
- Its Lite build downloads native libraries from the developer's personal GitHub releases. Power++ downloads only from official publishers.
- Its reverse image search uploads screenshots to catbox.moe without saying so clearly. Any upload feature in Power++ must ask the user first.

## 10. Status

- **Done:** requirements gathered; XGesture reviewed; repository initialized; this file written.
- **In progress:** waiting on §7. The user needs to install `jdk21-openjdk`.
- **Not started:** everything in code.

## 11. Open technical questions

- Is a `RecognitionService` stub required for Power++ to appear in the Digital assistant picker on Android 17? Verify against AOSP and on the device.
- Is `takeScreenshot` latency and rate-limiting (about 1 call per 333 ms) good enough for XGesture-like capture?

## 12. Known risks and potential bugs

- Battery saver turns off cross-window blur. The overlay must switch to dim live, not only on the next open.
- On Android 13+, accessibility for sideloaded apps sits behind "restricted settings". Onboarding must walk the user through *App info → ⋮ → Allow restricted settings*.
- The Digital assistant role replaces Gemini/Assistant, so "Hey Google" stops working. This is accepted.
- The system may remove `TYPE_ACCESSIBILITY_OVERLAY` windows after screen-off or lock (XGesture's `FloatBallOverlay.kt` comments on this). The overlay must handle being removed externally.
