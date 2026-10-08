# AGENTS.md — Power++

Everything an engineer or LLM needs to pick up work on Power++. Keep this file current: update it whenever a decision is made, a question is answered, or work starts or finishes.

## 1. What the app is

Power++ makes the power button more powerful. **Pressing and holding power** opens a full-screen Power++ overlay instead of Google's assistant or the system power menu. The overlay shows **pages of modules**, laid out like a home screen. Each module is a button that triggers a feature, or a toggle.

- **Package:** `com.bo0sted.powerplusplus`
- **Language:** English only for v1.
- **Primary test device:** Pixel 9 Pro XL, Android 17 (API 37).
- **Compatibility goal:** run on as many devices as possible, but never by compromising the design.
- **Inspiration:** XGesture (`github.com/qpst4/XGesture`, AGPL-3.0). Power++ takes ideas and end-user behavior from it but is written independently; no XGesture code is copied verbatim.

## 2. Core design

### 2.1 Trigger: power-button long-press
- Power++ registers as the device's **Digital assistant** (`VoiceInteractionService` + `VoiceInteractionSessionService`, role `android.app.role.ASSISTANT`). The user sets *Settings → System → Gestures → Press & hold power button → Digital assistant*. On Pixel and AOSP-like Android 12+ devices, a long-press then starts our session.
- No root and no LSPosed. The platform supports this natively.
- **Known limit:** Samsung One UI's side key can't be assigned to a third-party assistant there. On such devices the overlay must be reachable some other way (TBD; see §6).
- When the session is shown, it hands off to the overlay (see 2.2) and finishes itself.

### 2.2 Overlay
- **Blurred background** by default, with an optional setting for a **dimmed black** background (better performance).
- The overlay must automatically fall back to dim when cross-window blur isn't available, for example in battery saver (`WindowManager.isCrossWindowBlurEnabled` / `addCrossWindowBlurEnabledListener`).
- Dismissed by tapping outside the modules or pressing Back.
- **Pages:** swipe between pages, vertically by default; horizontal is a setting. Each page holds whichever modules the user assigns to it.
- **Window type:** proposed `TYPE_ACCESSIBILITY_OVERLAY`, drawn by our AccessibilityService. The service is required anyway (text search, screenshots, power menu), and this window type needs no "Display over other apps" permission. **This needs the user's sign-off; see §6.**

### 2.3 Modules
- A module is **a button (action) or a toggle**.
- Every module can be **enabled or disabled** individually.
- Modules are **added, configured, removed and moved** from inside the app.
- The architecture must make new module types easy to add: a module = a stable type id, a config schema, and a handler, registered with a registry. Nothing else in the app should know about specific module types.

### 2.4 Settings
- Every setting saves and **takes effect immediately**. There are no "Save" or "Apply" buttons.
- Settings are stored in DataStore and exposed as `Flow`, and the running overlay and services observe them live.

## 3. v1 modules

### 3.1 Power menu
- Opens the **system** power menu through `AccessibilityService.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)`.
- Power off, restart, lockdown and emergency are **not** reimplemented. The system menu provides them.
- It's an ordinary module, placed on a page like any other.

### 3.2 Accessibility text search
- Reads on-screen text from the accessibility node tree. Tappable boxes are drawn over each text item, and the user selects text and acts on it.
- The final look and behavior should closely match XGesture's accessibility text search.

### 3.3 OCR text search
A separate module from 3.2. The flow:
1. Tapping the module shows a **draggable cursor**.
2. **Double-tapping** the screen starts screenshot capture. The user drags to choose the region, the same way XGesture does it.
3. When dragging stops, **Confirm** and **Reset** buttons appear. Reset starts a new capture; Confirm runs OCR.
4. The results show as **clickable boxes**, the same as accessibility text search.

### 3.4 Smart screenshot
- Based on XGesture's smart screenshot, especially **Copy**: the image goes to the clipboard without being saved to the gallery.
  - How it works: the PNG is written to the app-private `cacheDir/screenshots/`, exposed via `FileProvider`, and the clipboard gets `ClipData.newUri`. Android's clipboard can't hold raw bitmap bytes; XGesture does exactly this.
  - Improvement over XGesture, which never deletes these files: prune old clipboard images automatically.
- **Editor tools:** pen (color and width), blur, pixelate, undo/redo, crop, arrows, shapes, text.
- **Capture method:** `AccessibilityService.takeScreenshot` (API 30+). It needs no MediaProjection consent prompt. The overlay is hidden before capture. Secure (FLAG_SECURE) windows will capture as black.

## 4. OCR engines and models

- **Nothing is bundled.** Both the inference runtime and the models are downloaded, kept on disk, and removable in the app.
- **First model:** PP-OCRv6 Medium (detection + recognition ONNX + `inference.yml` dictionary, about 139 MB).
  - **Primary source:** `huggingface.co/PaddlePaddle`, the official PaddlePaddle organization. URLs should be pinned to a commit, not `main`.
  - **Mirror:** ModelScope (also official PaddlePaddle). It's unconfirmed whether its files are byte-identical; this needs checking.
- **Adding sources:** the catalog lets a model list several mirror URLs, so new sites can be added later.
- **Runtime:** ONNX Runtime from Microsoft's official Maven Central artifact (`com.microsoft.onnxruntime:onnxruntime-android`). Only the arm64 `.so` files are downloaded and verified by SHA-256. We avoid OpenCV by doing pre- and post-processing in Kotlin. How the ORT Java API is made to load a library from a non-standard directory is still open (see §7).
- **Checking downloads (two layers):**
  1. **Hash pinning:** every file in the catalog has a fixed SHA-256 and size. A download that doesn't match is discarded. This is what XGesture does (`ocr_models.json` plus per-file SHA-256 and a minimum size). It means a file can only be a known-good artifact, whichever mirror served it.
  2. **Load check:** after download, an ONNX Runtime session is opened and the input and output signature is checked against what the pipeline expects. The recognition output size must match the dictionary. The model is marked installed only if this passes.
- **Android 17 note:** `System.load()` rejects writable files when `targetSdk >= 37`. Downloaded `.so` files must be made read-only before loading.

## 5. Tech stack and environment

- Kotlin, Jetpack Compose, Hilt, DataStore, Gradle version catalog, multi-module (`app`, `core:*`, one `module:*` per feature).
- minSdk 31, compileSdk 37, targetSdk 37.
- Build environment: proposed, not yet approved (see §6).

## 6. Open questions for the user

1. **Overlay permission:** the user expected the "Display over other apps" permission. Can we use an accessibility overlay instead, which needs one permission fewer?
2. **Page layout:** is it a grid? With how many columns and rows, and are they configurable? Are modules always 1×1, or can some be larger (like widgets)?
3. **Samsung and other devices** where power long-press can't open a third-party assistant: what fallback trigger should they get?
4. **Build environment:** see the proposal given to the user on 2026-10-08. It needs approval to download the Android SDK and accept its licenses.
5. **Text search actions:** XGesture offers copy, search and translate on selected text. Which are in v1?

## 7. Open technical questions

- Is a `RecognitionService` stub required for Power++ to appear in the Digital assistant picker on Android 17? Verify against AOSP and on the device.
- ONNX Runtime's Java API on Android loads native libraries with `System.loadLibrary`. How do we load downloaded libraries from app storage? Options: our own thin JNI over the ORT C API, or preloading. Needs investigation.
- How close is `takeScreenshot` latency and rate-limiting to XGesture's capture feel?

## 8. Status

- **Done:** requirements gathered; XGesture reviewed (see §9); repository initialized.
- **In progress:** agreeing on the build environment and the open questions above.
- **Next:** scaffold the project, then build the overlay and page/module framework, then the four v1 modules.

## 9. Notes from the XGesture review (2026-10-08)

- XGesture's root mode runs an **unauthenticated localhost RPC as root**, which lets any app run root commands (`TaskManagerRpcServer`). Never build anything like that.
- XGesture's Lite build downloads native engine libraries from the developer's personal GitHub releases. Power++ downloads only from official publishers.
- XGesture's reverse image search uploads screenshots to catbox.moe. Power++ has no such feature, and anything like it must ask the user first.

## 10. Known risks and potential bugs

- Battery saver turns off cross-window blur. The overlay must switch to dim live, not only on the next open.
- On Android 13+, accessibility for sideloaded apps sits behind "restricted settings". Onboarding must walk the user through *App info → ⋮ → Allow restricted settings*.
- The Digital assistant role replaces Gemini/Assistant. Users lose the "Hey Google" hotword unless another path exists. Mention this in onboarding.
