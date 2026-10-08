# AGENTS.md — Power++

This is the single source of truth for Power++. An engineer or LLM reading only this file should be able to continue exactly where the last session stopped. Update it whenever a decision is made, a question is asked or answered, or work starts or finishes.

## 0. Resume here

- **Current phase:** pre-scaffold. Requirements are agreed; the build environment and the questions in §7 are waiting on the user.
- **Next action once the user answers §7:** set up the environment (§6), scaffold the Gradle project (§5), then build the overlay + page/module framework (§2) before any v1 module (§3).
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
- **Known limit:** Samsung One UI's side key can't be assigned to a third-party assistant there. A fallback trigger for such devices is an open question (§7).

### 2.2 Overlay
- **Blurred background** by default, with an optional setting for a **dimmed black** background (better performance).
- The overlay must automatically fall back to dim whenever cross-window blur is unavailable, for example in battery saver. Use `WindowManager.isCrossWindowBlurEnabled` and `addCrossWindowBlurEnabledListener`, and switch live.
- Dismissed by tapping outside the modules or pressing Back.
- **Pages:** swipe between pages, vertically by default; horizontal is a setting. Each page holds the modules the user assigns to it. The grid shape is still open (§7).
- **Window type (proposed, awaiting sign-off in §7):** `TYPE_ACCESSIBILITY_OVERLAY`, drawn by our AccessibilityService. The service is required anyway (text search, screenshots, power menu), and this window type needs no "Display over other apps" permission. The user originally expected that permission to be required.

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
- Which actions the result panel offers in v1 is open (§7).

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
- **Runtime:** ONNX Runtime from Microsoft's official Maven Central artifact (`com.microsoft.onnxruntime:onnxruntime-android`). Only the arm64 `.so` files are downloaded and verified by SHA-256. Avoid OpenCV by doing pre- and post-processing (DBNet boxes, CTC decoding) in Kotlin. How the ORT Java API is made to load libraries from app storage is still open (§7).
- **Checking downloads (two layers).** The user doesn't know the model file formats and wants every download checked.
  1. **Hash pinning:** every file in the catalog has a fixed SHA-256 and size; a download that doesn't match is discarded. This is what XGesture does (per-file SHA-256 plus a minimum size, then a `manifest.json` written on install). It means a file can only be a known-good artifact, whichever mirror served it.
  2. **Load check:** after download, an ONNX Runtime session is opened and the input/output signature is checked against what the pipeline expects. The recognition output size must match the dictionary. The model is marked installed only if this passes.
- **Android 17 note:** with `targetSdk >= 37`, `System.load()` rejects writable files. Make downloaded `.so` files read-only before loading.

## 5. Tech stack

- Kotlin, Jetpack Compose, Hilt, DataStore, Gradle version catalog.
- Multi-module: `app`, `core:*`, and one `module:*` per feature.
- minSdk 31, compileSdk 37, targetSdk 37.
- Use the latest stable AGP, Kotlin and Compose BOM at scaffold time; check them, don't copy versions from XGesture.

## 6. Build environment (proposed, awaiting approval)

**Machine:** Arch Linux. Facts as of 2026-10-08:
- Only OpenJDK 17 is installed (the default).
- No Android SDK (`~/Android/Sdk` doesn't exist, `ANDROID_HOME` is unset).
- `adb` is at `/usr/bin/adb`.
- No device is attached.

**Proposal:**
- **JDK:** Gradle runs on the existing JDK 17. Compilation uses **JDK 21 LTS** through Gradle toolchains with the foojay resolver, which provisions it automatically, so nothing is installed system-wide.
- **Android SDK:** Google's command-line tools in `~/Android/Sdk` (no sudo), with `platform-tools`, `platforms;android-37` and the latest `build-tools`. Installing it means downloading from dl.google.com and accepting the Android SDK licenses, which needs **explicit user approval**.
- **IDE:** Android Studio is optional and up to the user (for previews and debugging). The project builds from the CLI with `./gradlew`.
- **Device testing:** the Pixel over adb (USB or wireless debugging). Ask the user for device-side information only as a last resort.

## 7. Open questions waiting on the user

1. **Overlay permission:** can we use an accessibility overlay (§2.2) instead of "Display over other apps", which needs one permission fewer?
2. **Page layout:** a grid? How many columns and rows, and are they configurable? Are modules always 1×1, or can some be larger (like widgets)? Proposed: a configurable grid of 1×1 cells, with a data model that allows spans later.
3. **Fallback trigger** for Samsung and other devices where power long-press can't open a third-party assistant.
4. **Build environment:** approve §6, including the SDK download and license acceptance.
5. **Text search actions:** XGesture's result panel offers copy, web search, translate and more. Which are in v1?

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
- **In progress:** waiting on §7.
- **Not started:** everything in code.

## 11. Open technical questions

- Is a `RecognitionService` stub required for Power++ to appear in the Digital assistant picker on Android 17? Verify against AOSP and on the device.
- ONNX Runtime's Java API on Android loads native libraries with `System.loadLibrary`. How do we load downloaded libraries from app storage? Options: our own thin JNI over the ORT C API, or preloading. Needs investigation.
- Is `takeScreenshot` latency and rate-limiting (about 1 call per 333 ms) good enough for XGesture-like capture?

## 12. Known risks and potential bugs

- Battery saver turns off cross-window blur. The overlay must switch to dim live, not only on the next open.
- On Android 13+, accessibility for sideloaded apps sits behind "restricted settings". Onboarding must walk the user through *App info → ⋮ → Allow restricted settings*.
- The Digital assistant role replaces Gemini/Assistant. Users lose the "Hey Google" hotword unless another path exists. Say so in onboarding.
