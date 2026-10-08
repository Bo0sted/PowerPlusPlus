# AGENTS.md — Power++

This is the single source of truth for Power++. An engineer or LLM reading only this file should be able to continue exactly where the last session stopped. Update it whenever a decision is made, a question is asked or answered, or work starts or finishes.

## 0. Resume here

**Session 1 ended 2026-10-08 with everything committed. Pushing to a remote happens at hand-off (§10).**

- **State:** every v1 piece except **OCR text search** is built and **confirmed working by the user on-device**:
  - the panel and trigger (§2)
  - Power menu (§3.1)
  - Screen text (§3.2)
  - Smart screenshot, both the selection and the editor (§3.4)
  - the activation screenshot and Retake (§3.5, §3.5b)
  - the OCR engine/model manager, with ORT telemetry disabled (§4)
- **Next action: build OCR text search (§3.3).** The full plan is in §3.3; build it in this order, with an on-device checkpoint after step 2:
  1. **The pipeline in `:core:ocr`** (`PpOcr` or similar): detection + recognition with the exact parameters in §3.3. Unit-test the pure parts (DB post-processing on a synthetic probability map, CTC decoding).
  2. **On-device checkpoint:** a temporary debug path that runs OCR on the activation screenshot and logs the lines. Ask the user to test, and measure the time per stage.
  3. **`:module:ocrtext`**: cursor → double-tap → drag region → Confirm/Reset/Retake → results as tappable boxes over the frozen image.
  4. Add it to `app/build.gradle.kts`/`settings.gradle.kts`, test with the user, update this file, and commit.
- **How work is done here:**
  - Verify every API against AOSP or the pinned library source before relying on it. §11 lists what's already verified; don't re-research those.
  - Lint must stay at zero issues.
  - Commit after each confirmed step.
  - The user tests on a Pixel 9 Pro XL (Android 17) over **wireless adb**. The address changes per session; ask the user for the `adb connect` line.
  - **Don't send input (taps or swipes) to the user's device.** Read state only (logcat, dumpsys, screencap) and ask the user to interact.
- **Code ownership:** all Power++ code is written from scratch. XGesture is only read to learn behavior and which APIs it uses; nothing is copied from it.
- **Repository:** `/home/mc/Downloads/Github_Projects/Power++`, branch `main`. Commits are authored as `Bo0sted <mc526+github@proton.me>`, set in the repo-local git config (the global identity differs). The remote is recorded in §10 once it's set.
- **Reference app:** a local clone of XGesture at `/home/mc/Downloads/Github_Projects/XGesture` (`github.com/qpst4/XGesture`, AGPL-3.0, package `com.slideindex.app`). Power++ takes ideas and end-user behavior from it but is written independently. Where Android offers a better or official way, use it. Pointers to the relevant XGesture files are in §9.

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
- When the session is shown, it immediately hands off to the overlay (§2.2) and hides itself. `PanelController.show()` returns one of:
  - `Shown`
  - `SystemPowerMenu`: the lock screen is showing (`KeyguardManager.isKeyguardLocked`, which also covers swipe-only and occluded lock screens), so `GLOBAL_ACTION_POWER_DIALOG` is performed instead. It reaches `PhoneWindowManager.showGlobalActionsInternal`, the same path as a stock power long-press, and the system shows its lock-screen variant of the menu.
  - `ServiceUnavailable`: accessibility is off, so the session opens Power++'s settings.
- The panel also closes itself on `ACTION_SCREEN_OFF`, which covers both locking and sleep.
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
- **Implemented** as `:module:screentext` (typeId `screen_text`, "Screen text").
- **XGesture equivalent:** its "Universal copy" (`app/src/main/java/com/slideindex/app/copy/UniversalCopyOverlay.kt` + `service/AccessibilityTextExtractor.kt`). Same UX, written independently:
  - a light scrim with a rounded box around each text item
  - tap toggles; tapping empty space closes
  - a bottom pill toolbar: "Tap text to select it" / "N selected", Select all ↔ Deselect all, Copy (disabled until something is selected), ×
  - Copy joins the selection in reading order, one per line, and closes
  - "No text found on screen" toast when there's nothing
  - XGesture's extra "Segment" button (its word-split panel) is intentionally **not** included; v1 is Copy-only.
- **Reading:** all windows from `service.windows`, except accessibility overlays, the IME and our own package.
  - Per node: `text`, else `contentDescription`, else `stateDescription`.
  - Nodes without `text` must be `isVisibleToUser`; nodes with `text` are kept even when not visible (XGesture found chat apps report message bodies that way). Bounds are clipped to the window.
  - **Password fields are always skipped** (an improvement over XGesture).
  - Traversal runs on `Dispatchers.Default`.
- **Copy:** `ClipData.newPlainText`. A "Copied" toast only on API ≤ 32, because Android 13+ shows its own clipboard confirmation and the official copy/paste guide says not to duplicate it.
- Future actions (search, translate…) may come later.

### 3.3 OCR text search — **NEXT TO BUILD**

A separate module from 3.2: `:module:ocrtext`, typeId `ocr_text`, title "OCR text", `usesActivationScreenshot = true`.

**Decided (user, 2026-10-08):** it works on the **frozen activation capture by default**, exactly like Smart screenshot, with **Retake** (§3.5b, `ScreenRecapture`) next to Confirm/Reset for dynamic content.

**User-specified flow** (verbatim intent; follow it exactly):
1. Tapping the module shows a **draggable cursor** (over the frozen capture).
2. Once the cursor is where the region should start, the user **double-taps** to start the capture, then **drags** to define the region (a rectangle from the start point to the finger).
3. When the drag stops, **Confirm** and **Reset** buttons appear, plus **Retake** on Android 14+. Reset starts a new region; Confirm runs OCR on that region.
4. The OCR results show as **clickable boxes**, the same UI as Screen text (`:core:textpick`), with Copy.

**Implementation plan:**
- **Entry:** like `SmartScreenshotModule`.
  1. `host.activationScreenshot.take()`; if there's none, toast "Couldn't capture the screen".
  2. Make a software copy and run `looksBlocked`, toasting "This app blocks screenshots" if needed. Consider moving these helpers from `:module:screenshot` to a shared place instead of duplicating them.
  3. Show a focusable `OverlayWindow` that draws the frozen image full-screen.
  4. If the OCR model isn't installed (`OcrComponents.isInstalled(OcrCatalog.PP_OCR_V6_MEDIUM)`), show a message pointing to Settings → OCR engine and models.
- **Results:** reuse `TextPickScreen`, but it currently draws over the *live* screen. Add an optional background image parameter so OCR results sit on the frozen capture (the live app may be hidden). Block bounds are screen pixels, and the capture is display-sized, so OCR coordinates (region offset + box) map 1:1.
- **XGesture note:** XGesture's regional pick (`overlay/RegionalPickOverlay.kt`) is driven by its floating-ball drag (a region starts once the finger moves ≥16 dp from the anchor), not by a double-tap. Power++ follows the **user's** double-tap flow above; only the visual idea (cursor + rectangle preview) comes from XGesture.

**Pipeline** (`:core:ocr`). The parameters come from the pinned configs (§4); verify anything not listed against PaddleOCR's source before relying on it:
- **Engine:** `OrtEngine.load(OcrComponents.engineLibrary())`, then create `OrtSession`s for `det.onnx`/`rec.onnx` from `OcrComponents.directory(PP_OCR_V6_MEDIUM)`. Create them once and keep them (model loading is slow). Consider releasing them when the accessibility service is destroyed.
- **Detection (DBNet):**
  - **Input:** the region crop, channels in **BGR order**, each normalised `(x/255 − mean[c]) / std[c]` with mean `[0.485, 0.456, 0.406]` and std `[0.229, 0.224, 0.225]` applied to channels **in BGR order** (PaddleOCR's `NormalizeImage` on a BGR image), CHW, batch 1.
  - **Size:** resize so both sides are multiples of 32. The `DetResizeForTest` limit isn't in the yml: check PaddleOCR's PP-OCRv6 default (`limit_side_len`/`limit_type`) before choosing. Screens are tall (1344×2992) with small text, so too small a limit misses text; measure on-device.
  - **Output:** a probability map `[1,1,H,W]`. Binarise at `thresh` **0.2**, find connected components, and for each compute its box and its mean probability; keep those ≥ `box_thresh` **0.45**, at most **3000** candidates.
  - **Unclip:** expand by `d = area × 1.4 / perimeter` (`unclip_ratio` **1.4**). Axis-aligned boxes are fine for screen text; rotated boxes are optional.
  - Scale the boxes back to the crop, then offset by the region.
- **Recognition (CTC):**
  - For each box, crop from the **original** (not resized) image; if `h ≥ 1.5·w`, rotate 90° (PaddleOCR does this for vertical text).
  - Resize to **height 48**, keeping the aspect ratio; normalise `(x/255 − 0.5)/0.5`, BGR.
  - Batching: sort by aspect ratio, batch about 8, pad the width with zeros to the batch's max width (PaddleOCR behaviour); verify the padding value.
  - **Output `[n, t, 18710]`** (confirmed on-device): index 0 = CTC blank, 1..18708 = `RecognitionDictionary` entries, 18709 = space. Greedy decode: argmax per step, collapse repeats, drop blanks. The score is the mean max probability; drop lines with very low scores (PaddleOCR's `drop_score` default is 0.5 — verify).
- **Threading:** run everything on `Dispatchers.Default` and show progress in the overlay. Measure the time per stage on-device at the checkpoint.
- **Line merging:** each recognised box becomes one `TextBlock`; `normalizeTextBlocks` already sorts them in reading order.

### 3.4 Smart screenshot
- **Step 1 is implemented** as `:module:screenshot` (typeId `smart_screenshot`, `usesActivationScreenshot = true`). **Step 2 (the editor) is implemented.** Both steps are confirmed working by the user on-device.
- **Flow:**
  1. The module `take()`s the activation screenshot (toast "Couldn't capture the screen" if there is none).
  2. It makes a software ARGB copy (`Dispatchers.Default`).
  3. `looksBlocked` (a 48×48 sample grid over the middle 80 % of the height, all near-black) → toast "This app blocks screenshots".
  4. Otherwise it shows `SelectScreen` in a focusable `OverlayWindow`.
- **`SelectScreen`** (XGesture-like):
  - the frozen image with everything outside the selection darkened (58 %), the accent frame, handles and a centre crosshair
  - drag inside to move, drag a handle to resize (32 dp hit slop), tap outside to cancel
  - a floating shape toolbar above the selection (Rectangle · Circle · Full-width strip · Full-height strip) and an action toolbar below it (**Copy · Save · Share · Edit**); both hide while dragging and stay inside the safe-drawing insets
- **`SelectionGeometry`** (pure, unit-tested):
  - defaults: rectangle 60 % × 35 % of the screen, circle 60 % of the short side, horizontal strip 20 % of the height, vertical strip 40 % of the width
  - 48 dp minimum
  - strips move only across their axis and have edge handles only; the circle stays square, anchored at the opposite corner
  - **Switching shape always resets to that shape's default** (the user's rule, 2026-10-08). Carrying the previous size over maxed out an edge when switching to or from a strip.
  - **Max / Default** buttons (user, 2026-10-08) sit in a row directly under the shape toolbar.
    - `max()`: the whole image for a rectangle or strip; for a circle, the largest that fits (the short side), centred on the current selection and kept inside.
    - Default = `default(shape)`.
- **Crop:** the circle uses an antialiased oval clip on a transparent ARGB bitmap (transparent corners, decided by the user).
- **`ScreenshotOutput`:**
  - **Copy/Share** write a PNG to `cacheDir/screenshots/`, served by the module's own FileProvider (`${applicationId}.screenshots`), and keep only the 5 newest files.
  - **Save** writes to `Pictures/Power++` via MediaStore (`IS_PENDING`; no permission needed).
  - The Share chooser is started *before* the overlay closes. While our accessibility overlay is visible, `BackgroundActivityStartController` allows the start (`BAL_ALLOW_NON_APP_VISIBLE_WINDOW`; `WindowState` counts system-type windows ≥ `FIRST_SYSTEM_WINDOW` as non-app visible windows).
- **Step 2: the editor** (`editor/` package), reached via **Edit**.
  - The crop is handed over in-process via `EditorSession` (bitmaps are too big for an Intent), then `EditorActivity` is started while the overlay is still visible (BAL), and the overlay closes.
  - **Why an Activity, not an overlay:** accessibility overlays sit above the IME, and the Text tool needs the keyboard; an activity also gets normal Back. It's declared in the module manifest with `excludeFromRecents`, `taskAffinity=""` and `Theme.Material.NoActionBar`.
- **Editor model** (pure, unit-tested):
  - `Mark` (`Pen`, `Arrow`, `Box`, `Ellipse`, `Label`, `Effect(kind, strength, area)`), with `EffectArea` = `Brush(points, diameter)` | `Box(rect)`. All coordinates are image pixels.
  - `EditHistory`: snapshot-based undo/redo (`commit`, `add`, `updateLabel`, `removeLabel`). Every edit, including moving, recolouring or deleting text, is one undo step; an unchanged commit isn't a step.
  - `Mark.Label` has an `id`, and `background` (a solid box in the label's colour, with the text in black or white by luminance).
- **`EditorViewModel`** holds the image, history and processed effect images, so a rotation doesn't lose work. It recycles everything in `onCleared`.
- **Rendering:** `DrawScope.drawEdits` draws the image and marks. It's used on screen (inside a fit-to-view scale transform) **and** for export (`CanvasDrawScope` on an `ImageBitmap` at 1:1, `Density(1f)`), so the output matches the screen exactly.
  - Text sizes are stored in image pixels and converted to sp via the scope's density.
  - **Effects** draw a pre-processed full-image copy clipped to the area: `clipRect` for a box; for a brush, a `saveLayer` (spanning the *image* bounds), the stroke as a mask, then the processed image with `BlendMode.SrcIn`.
  - Effects use the base image only, so earlier marks under an effect are covered, not blurred.
- **`EditorEffects`** (strength 0..1, exponential, unit-tested):
  - blur radius 1.5→300 px, done by repeated halving then bilinear upscaling
  - pixel blocks 2→200 px, done by a bilinear shrink then a nearest-neighbour enlarge
  - cached per kind and 1 %-quantised strength; the active tool's effect is prepared in the background when the tool or strength changes
- **`EditorSettings`** are persisted with DataStore Preferences (`screenshot_editor`):
  - colour (8-swatch palette), stroke width dp (1–24), text size dp (12–96), brush size dp (8–160), blur and pixelate strength (0–1), effect area mode (Brush or Rectangle)
  - Sliders save on release (`onValueChangeFinished`), not on every frame.
  - The brush-size slider shows a preview circle at the brush's real on-screen size while held.
- **Two drawing layers** (user-reported lag, 2026-10-08):
  - The image plus finished marks are a `Canvas` with `graphicsLayer { compositingStrategy = Offscreen }`, redrawn only when marks or effects change.
  - A live `Canvas` on top draws only the in-progress mark, a label being dragged (hidden from the cached layer via a separate `hiddenLabelId` state, so the cached layer isn't invalidated every frame) and the selection outline.
  - In-progress blur and pixelate draw a translucent placeholder until the processed image is ready.
  - Strokes use `change.historical` samples.
- **⚠️ Pitfall that caused the "0.5 s lag":**
  - **Never put a still-mutating collection into Compose state.** Pen/brush marks were first built with the live `MutableList` of points, which then grew in place. Every new `toList()` copy compared *equal* to the stored value, so `mutableStateOf`'s structural-equality policy dropped the write and nothing redrew.
  - Measured on-device: 1 redraw for a 707-sample stroke; the editor window rendered 4 frames in 8 s.
  - Only point-collecting tools were affected.
  - Fix: always pass `points.toList()`.
  - Diagnostics that found it: `dumpsys gfxinfo <pkg> framestats` (frames per window) plus a temporary redraw counter in the draw lambda.
- **Text:**
  - The Text tool places a label on tap (not on drag) and selects it.
  - With **any** tool, pressing a label selects it, and dragging moves it.
  - While selected, the bottom panel shows `LabelOptions`: palette, size slider, **Background** switch, Edit text (prefilled dialog), Delete.
  - Tapping elsewhere or switching tool deselects.
- **Editor UI:**
  - **action row at the very bottom** (user, 2026-10-08, for thumb reach), below the tool row: Close · Undo · Redo · **Copy · Save · Share** (each renders, outputs, then closes the editor). The whole screen sits in a `Surface(contentColor = White)`; without it the top-bar icons defaulted to black on black and looked missing.
  - bottom: tool options above the tool row (Pen · Arrow · Rectangle · Oval · Text · Blur · Pixelate)
  - Text: tap the image → a dialog with a text field → the label is placed at the tap point

### 3.5 Activation screenshot (user's idea, 2026-10-08; implemented and confirmed on-device)
- **Problem:** some apps (the user's banking apps) hide their content as soon as Power++ activates. A system screenshot taken earlier still captures the real content, because the app only hides *after* the system's screenshot notification.
- **Implemented** in `PanelController.show()`:
  - If any enabled slot's module has `usesActivationScreenshot`, call `takeScreenshot` → `Bitmap.wrapHardwareBuffer` (closing the buffer right away is safe, because the bitmap keeps its own reference) → `host.activationScreenshot.replace(bitmap)` → add the panel. On failure the panel opens anyway.
  - `dismiss(keepScreenshot = module.usesActivationScreenshot)` hands it over on a module tap; every other dismissal discards it.
  - The timing experiment code has been removed.
- **Proposal (original):** capture the screen at activation, before the panel overlay is added, and hand that bitmap to Smart screenshot and OCR text search. It's discarded when the panel closes unless one of those modules took it. Screen text can't use it, since it reads the live node tree.
- **Verified facts** (AOSP `main`):
  - `AccessibilityService.takeScreenshot` → `WindowManagerService.captureDisplay(displayId, null, …)`. The default `CaptureArgs` has `captureSecureLayers = false`, so **`FLAG_SECURE` windows are always black**; no third-party method can avoid that.
  - It does **not** notify the app's `Activity.ScreenCaptureCallback` (Android 14 screenshot detection). That's fired only via `IWindowManager.notifyScreenshotListeners`, which "should be invoked only by SysUI" (the user's hardware-key screenshots).
- **Confirmed on-device (2026-10-08, the user's banking app):** the app hides when it **loses input focus**.
  - With the panel made non-focusable (`FLAG_NOT_FOCUSABLE`), opening the panel no longer hides it.
  - The focusable Screen-text overlay still does.
  - The user then chose to **revert**: the panel is focusable again (Back closes it) and has no Close button. The focus bypass was dropped in favour of the activation screenshot.
- **Timing measured on-device (2026-10-08, Pixel 9 Pro XL, 5 runs, `PowerPP.Timing` log):**

  | Step | Time |
  |---|---|
  | power long-press → `onShow` (SystemUI `invocation_time_ms`, `elapsedRealtime`) | 9–31 ms |
  | `takeScreenshot` (1344×2992, always succeeded) | **9–32 ms** (avg ≈ 21) |
  | capture → panel `addView` | 16–29 ms |
  | power → panel added, total | 49–79 ms |

  The user noticed no slowdown. **Conclusion: capturing at activation is perceptually free.**
  - The temporary timing code has been removed, replaced by the real implementation above.
  - **Confirmed by the user:** the activation capture contains the banking app's real content.
- **Decided (user, 2026-10-08):**
  - Capture at activation, **only when the panel contains a module that uses the screenshot** (Smart screenshot or OCR).
  - If the capture is black (a `FLAG_SECURE` app), show a message (e.g. "This app blocks screenshots") instead of opening the editor.
  - Module overlays stay focusable (Back works).
- **Background:** capture at activation (in `PowerSession.onShow`, before the panel window exists) rather than when a module is tapped.
  - Capturing on tap races our own panel's removal: there's no reliable signal that the removal has been composited, so the blurred panel could end up in the shot.
  - Capturing at activation is strictly ordered: the app still has focus and nothing of ours is on screen yet.
  - Proposed gating: capture only if the panel contains a module that uses the screenshot.

### 3.5b Retake (user, 2026-10-08)
- **Purpose split, as stated by the user:** the frozen activation capture is for apps that hide themselves on focus loss; **Retake** is for dynamic content (video, timers, anything that changed).
  - Retake can't help self-hiding apps: our selection overlay holds focus, so they're already hidden.
- **Smart screenshot:** a **Retake** button in the Max/Default row under the shape toolbar. It replaces the frozen image and keeps the current selection.
  - A blocked (black) result shows the toast and keeps the old image.
  - The replaced bitmap is left to the GC, not recycled, because a frame in flight may still reference it.
- **OCR:** also gets Retake. Placement: the region-selection stage, next to Confirm/Reset.
- **Implementation (`ScreenRecapture`):** Android 14+ only (`takeScreenshotOfWindow`); the button is hidden on 12–13, which have no per-window capture, and the only alternative would be a timing guess.
  - Each window from `getWindows()` except accessibility overlays is captured and stacked bottom-up onto a black display-sized canvas, after converting hardware bitmaps to software.
  - The rate limit is per window id, so the parallel per-window captures don't conflict.
  - The platform Javadoc recommends `takeScreenshotOfWindow` for windows under an accessibility overlay.
- **Verified limitation:** `AccessibilityWindowManager.isReportedWindowType` excludes `TYPE_WALLPAPER` (also non-touchable windows, input consumers, etc.). So a retake shows **black where the wallpaper was** (e.g. behind home-screen icons); app content is unaffected. The same code shows `TYPE_ACCESSIBILITY_OVERLAY` doesn't occlude windows below it in that list.

### 3.6 Smart screenshot selection shapes (user, 2026-10-08)
- Default: a resizable rectangle in the center (like XGesture).
- **Horizontal edge-to-edge strip:** always spans the full screen width. Its height is adjustable (sensible default) and it can be dragged up and down.
- **Vertical edge-to-edge strip:** always spans the full screen height. Its width is adjustable (sensible default) and it can be dragged left and right.
- A circle.
- Plus the editor tools from §3.4 (blur, pixelate, pen, …).

## 4. OCR engines and models

**Implemented 2026-10-08** as `:core:ocr` plus the settings section "OCR engine and models" (`app/settings/OcrSection.kt`). **Verified on-device** (the user downloaded both components; see §10). The OCR *pipeline* and module aren't built yet (§3.3).

- **Nothing is bundled.** The engine and models are downloaded on demand, kept in **`noBackupFilesDir/ocr/<componentId>/`**, and removable (with confirmation). Uninstalling deletes them.
  - **Why no-backup storage:** Auto Backup includes `filesDir`, and with more than 25 MB it "doesn't back up data to the cloud" at all (developer.android.com/identity/data/autobackup). The ~170 MB of OCR files would silently stop the settings from being backed up.
  - `getNoBackupFilesDir()` is excluded by default on every API level, so no XML rules are needed.
  - Downloads from earlier builds in `filesDir/ocr` are moved on first start.
  - XGesture has this problem: it keeps models in `filesDir` and its backup rules don't exclude them.
- **`OcrCatalog`** pins every file with size and SHA-256 and lists URLs in order; any byte-identical mirror is acceptable. All values were verified 2026-10-08:
  - **Engine:** ONNX Runtime **1.30.0** AAR from Maven Central, 53,040,218 B, SHA-256 `e7fb945e…73f1`.
    - Its SHA-1 `656d9413…` equals Maven Central's published `.sha1`.
    - Only `jni/<first supported ABI>/libonnxruntime.so` is extracted; then the AAR is deleted. arm64 lib: 32,990,480 B, SHA-256 `df5d25c7…`.
  - **PP-OCRv6 Medium** (Apache-2.0, official `PaddlePaddle` org):

    | File | Hugging Face commit | Size | SHA-256 |
    |---|---|---|---|
    | detection (`det.onnx`) | `61323801…` | 62,032,837 B | `eb13b44b…` |
    | recognition (`rec.onnx`) | `50c7eaca…` | 76,554,979 B | `9c09abf0…` |
    | recognition `inference.yml` (`rec.yml`, the dictionary) | `50c7eaca…` | 150,580 B | `991b700f…` |

    - **ModelScope** (`PaddlePaddle/…`, revisions `8cb026ec…` for detection and `4b0e4965…` for recognition) serves **byte-identical** files (same SHA-256 per its API) and is the mirror.
    - The same hashes appear in XGesture's catalog: an independent cross-check.
  - All three hosts support HTTP Range (206); Hugging Face and ModelScope redirect to their CDNs.
- **`VerifiedDownloader`:**
  - writes to `<name>.part` and resumes with a Range request (restarting if the host replies 200)
  - tries each URL in order
  - accepts a file only when its size and SHA-256 match, deletes mismatched bytes, then renames it into place
- **`OcrComponents`** (@Singleton):
  - state per component: NotInstalled / Downloading (throttled to 0.1 %) / Verifying / Installed / Failed
  - installing a model installs the engine first if it's missing
  - cancellation propagates (Remove during a download cancels it)
  - **installed = a marker file written last**, after verification **and** a load check
- **Load check (`OrtEngine`):**
  - **Engine:** `System.load(path)` (made read-only first, as Android 17 requires), then `OrtEnvironment.getEnvironment()`.
  - **Models:** detection input `[n,3,h,w]` → output `[n,1,h,w]`; recognition input `[n,3,48,w]` → output `[n,t,C]` with `C` = dictionary + 1 or + 2. The actual `C` is logged (tag `PowerPP.Ocr`). **Confirmed on-device: 18,710** = 18,708 characters + CTC blank + space (PaddleOCR `use_space_char`).
- **How the downloaded engine loads** (verified in sources):
  - On Android, ORT 1.30's `OnnxRuntime.init()` only `loadLibrary`s the JNI glue `libonnxruntime4j_jni.so`.
  - `readelf`: the glue has `NEEDED libonnxruntime.so`; the engine's `SONAME` is `libonnxruntime.so`, and it depends only on libc, libm, libdl, liblog and libandroid.
  - bionic `find_library_internal` first reuses an already-loaded library by SONAME.
  - So: `System.load(engine)` → then any `ai.onnxruntime` use. The app excludes `**/libonnxruntime.so` from packaging; the APK ships only the glue (111 KB arm64).
  - A loaded engine can't be unloaded; removal takes effect after a restart.
- **The version is pinned once:** `libs.versions.toml` `onnxruntime` → `BuildConfig.ORT_VERSION`. `OcrCatalogTest` fails if the catalog's engine version or URL disagrees.
- **Pipeline parameters** (from the pinned configs; the full plan is in §3.3):
  - Detection: BGR; normalise with mean `[0.485, 0.456, 0.406]`, std `[0.229, 0.224, 0.225]` after /255; DB post-processing thresh 0.2, box_thresh 0.45, unclip 1.4, max 3000 candidates; TensorRT optimum shape 736.
  - Recognition: BGR; resize to height 48 (reference width 320); CTC decode.
  - `RecognitionDictionary` parses the dictionary; it's unit-tested against the quoting rules (PyYAML reads 18,708 plain or single-quoted entries).
- **Permission:** the `INTERNET` permission, declared in `:core:ocr`'s manifest, is used **only** for these user-initiated downloads. The final APK requests only `INTERNET` (plus AndroidX's internal dynamic-receiver permission); `ACCESS_NETWORK_STATE`, added by the ORT AAR, is removed with `tools:node="remove"` in the app manifest.
- **⚠️ ONNX Runtime telemetry: disabled three ways** (found on-device 2026-10-08 from `ORTTelemetry` log lines; verified in ORT v1.30.0 sources).
  - **What the AAR ships:** a `ContentProvider` `ai.onnxruntime.TelemetryInitializer` that auto-starts at every app launch and creates a 1DS Java `HttpClient`, plus connectivity and power receivers. In native code, `PosixTelemetry::enabled_` defaults to **true**.
  - **Correction:** an earlier statement to the user that ORT "makes no network calls" was unverified and wrong.
  - **Layer 1:** `Os.setenv("ORT_DISABLE_TELEMETRY", "1")` before `System.load`. Read via `getenv` in `telemetry_environment.h`; this triggers the full, irreversible suppression in `PosixTelemetry::Initialize` (no uploader, no events, no device id).
  - **Layer 2:** the provider is removed from the merged manifest, so the Java transport never exists and Android telemetry is "unavailable".
  - **Layer 3:** `OrtEnvironment.setTelemetry(false)`.
  - **Verified on-device:** after install, the restarted process logs no `ORTTelemetry`/onnxruntime lines, and the APK manifest (aapt2 dump) has no `TelemetryInitializer`.
  - **When bumping ORT, re-check** its telemetry code and manifest.

## 5. Architecture

- **Stack:** Kotlin (AGP 9 built-in Kotlin; never apply `org.jetbrains.kotlin.android`), Jetpack Compose + Material 3 (dynamic color), Hilt via KSP, DataStore with kotlinx.serialization JSON.
- **SDK levels:** minSdk 31, compileSdk 37, targetSdk 37. These live in `build-logic/src/main/kotlin/PowerBuildConfig.kt`.

### Gradle modules

| Module | Role |
|---|---|
| `build-logic` | Included build with the convention plugins: `powerpp.android.application`, `powerpp.android.library`, `powerpp.android.compose`, `powerpp.hilt`, `powerpp.serialization`, `powerpp.panel.module`. Modules apply plugins **only** through these; AGP, KSP and the rest are on build-logic's classpath, not resolvable by id directly. |
| `:core:ui` | `PowerTheme` |
| `:core:module` | The module SPI: `PanelModule` (sealed; `usesActivationScreenshot` defaults to false), `ActionModule` (`run(host)`; the panel is dismissed first), `ToggleModule` (`isOn(host): Flow<Boolean>`, `setOn`; the panel stays open), `ModuleHost` (the connected `AccessibilityService`, a scope tied to the connection, and `activationScreenshot`), `ActivationScreenshot` (single-owner holder: `take()` hands the bitmap over and the caller must recycle it; `replace()` recycles the old one), `ModuleRegistry` |
| `:core:data` | `PanelConfig` (grid columns×rows, swipe axis, backdrop, pages → `ModuleSlot(id, typeId, cell, enabled)`). Pure edit functions are in `PanelEdits.kt` and unit-tested; a rejected edit returns the same instance. `PanelRepository` wraps DataStore (`panel.json`) and replaces the file with defaults if it's corrupt. |
| `:core:overlay` | `OverlayWindow`: a full-screen `TYPE_ACCESSIBILITY_OVERLAY` hosting Compose. It provides its own Lifecycle and SavedState owners and blur with live fallback to dim. It calls `onDismissRequest` on Back **and on `ACTION_SCREEN_OFF`** (a non-exported receiver registered while showing). Single use: create a new one each time. |
| `:core:textpick` | Shared text-selection overlay, used by Screen text and (later) OCR. `TextBlock(text, IntRect screen px)`; `normalizeTextBlocks` drops container summaries (blocks enclosing a smaller block), blanks and duplicates, and sorts in reading order. `hitTest` picks the smallest block under the tap, with 10dp slop. `TextPickScreen` + `showTextPick(service, blocks)`. All of this is unit-tested. |
| `:module:*` | One per panel module. They apply `powerpp.panel.module` and bind themselves with `@Binds @IntoSet PanelModule`. Currently: `:module:powermenu`, `:module:screentext`, `:module:screenshot`. |
| `:app` | Wiring: `PowerAccessibilityService` (publishes a `ModuleHost` via `ModuleHostProvider`), the assistant services (`assistant/`), `PanelController` + `PanelScreen` (`panel/`), and the settings UI (`settings/`: setup checklist, layout editor, appearance). |

### Adding a module type
1. Create `module/<name>` with `plugins { id("powerpp.panel.module") }` and a `namespace`.
2. Implement `ActionModule` or `ToggleModule` with a new, never-changing `typeId`, and bind it `@IntoSet`.
3. Add `include(":module:<name>")` to `settings.gradle.kts` and `implementation(project(":module:<name>"))` to `app/build.gradle.kts`.

Nothing else changes.

### Behavior notes
- The panel reads the config as a hot `StateFlow`, so it opens instantly and reflects edits live.
- Toggle states are collected while the panel is showing.
- Empty cells and unknown `typeId`s (a module that was removed from the build) render as blank space in the panel and as "Unavailable" in the editor. They're never deleted automatically.
- Shrinking the grid reflows overflowing modules into free cells, appending pages as needed. Nothing is dropped.
- The editor can't place modules on the panel's backdrop; tapping any non-module area of the panel dismisses it.

## 6. Build environment

**Commands:**
- `./gradlew :app:assembleDebug` builds the debug APK.
- Unit tests: `./gradlew :core:data:testDebugUnitTest :core:textpick:testDebugUnitTest :module:screenshot:testDebugUnitTest :core:ocr:testDebugUnitTest` (all pass, 2026-10-08).
- Install on the device: `./gradlew :app:installDebug` (needs `adb connect <ip:port>` first).
- `./gradlew :app:lintDebug` (and `:<module>:lintDebug`) runs lint. Keep lint at zero issues.
- The single remaining Gradle deprecation warning (`Configuration.setVisible`) comes from a third-party plugin, not our scripts.
- On first build, AGP auto-installed `build-tools;36.0.0` and `platform-tools` from Google, under the licenses already accepted.

**Machine:** Arch Linux. The user requires tools from **Arch's official repos only, never the AUR**.

**Before setup (historical, 2026-10-08):** only OpenJDK 17 was installed, and there was no Android SDK. `adb` is at `/usr/bin/adb`, from `extra/android-tools`.

**Installed and decided (2026-10-08):**
- **JDK 21 LTS** from `extra/jdk21-openjdk`, at `/usr/lib/jvm/java-21-openjdk`. The system default is still JDK 17. Gradle's daemon JVM is pinned to 21 via `gradle/gradle-daemon-jvm.properties`, and JDK auto-download is disabled (`org.gradle.java.installations.auto-download=false`), since the user allows only Arch-official tooling.
- **Android SDK** directly from Google, never the AUR. It lives in `~/Android/Sdk` (`local.properties` → `sdk.dir`, gitignored). Installed:
  - `cmdline-tools;latest` (23.0, SHA-1 verified against Google's `repository2-3.xml`)
  - `platforms;android-37.0`
  - `build-tools;37.0.0`
  - The NDK and CMake were installed and later removed at the user's request (2026-10-08); nothing needs native code.
  - The SDK licenses were accepted with the user's approval.
- **Gradle:** the wrapper, pinned to **9.8.1** with `distributionSha256Sum`. It was generated from the official distribution, SHA-256 verified.
- **Versions at scaffold time:** see `gradle/libs.versions.toml` (AGP 9.4.1, Kotlin 2.4.21, KSP 2.3.12, Hilt 2.60.1, Compose BOM 2026.09.00).
- **IDE:** Android Studio is optional and up to the user. The project builds from the CLI with `./gradlew`.
- **Device testing:** the Pixel over adb (`/usr/bin/adb` from `extra/android-tools`). Ask the user for device-side information only as a last resort.
- Every Android build pulls AGP, AndroidX and Kotlin libraries from Google's Maven repository and Maven Central. Arch doesn't package these, so this is unavoidable.

## 7. Open questions waiting on the user

1. **Git reflog:** should the reflog entries that still reference the pre-rewrite commits (old author email) be purged? They expire on their own (~90 days) and are local only. Asked 2026-10-08; no answer yet. (Pushing doesn't send the reflog.)

**Answered:**
- **Circle crop:** transparent corners (PNG).
- **Lock screen:** open the system power menu instead (§8).
- **Retake:** placement as in §3.5b.
- **OCR:** uses the frozen capture.
- **Auto Backup:** the user doesn't use it, but downloads stay in no-backup storage regardless.

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
- **Lock screen** (user, 2026-10-08): the panel never shows while the lock screen is up. Instead, a power long-press opens the **system power menu**, with no overlay rendered (see §2.1).
  - Observed before the fix: the assistant session *is* invoked on the lock screen even without `supportsLaunchVoiceAssistFromKeyguard`, and the accessibility overlay draws over the keyguard.

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

**Done, and confirmed working by the user on-device (Pixel 9 Pro XL, Android 17), 2026-10-08:**
- **Toolchain** (§6) and the **Gradle scaffold** with convention plugins.
- **Framework:** assistant trigger; panel with blur/dim, pages, swipe axis and the empty state; dismissal on Back and screen off; lock screen → system power menu.
- **Settings:** setup checklist; layout editor (add/move/remove/disable, pages, grid resize reflow); appearance.
- **Power menu** module (§3.1).
- **Screen text** module (§3.2).
- **Smart screenshot** (§3.4):
  - activation capture, including of the banking app
  - the four shapes, Max/Default and Retake
  - Copy/Save/Share
  - the editor with all tools, text selection/editing/background, undo/redo, persisted sliders and the action row at the bottom
- **OCR engine/model manager** (§4): downloads, verification, load check, no-backup storage, telemetry disabled.
- **Lint:** zero issues across modules. All unit tests pass.

**Not done:**
- **OCR text search** (§3.3): the pipeline and module. **This is the next task.**

**Remote:** not set as of the end of the session; see the hand-off note below once it is.

## 11. Verified platform facts (checked against AOSP `main` and the pinned library sources, 2026-10-08)

Re-verify only if the target platform changes.

**Assistant role** (`AssistantRoleBehavior`, `VoiceInteractionServiceInfo`, `VoiceInteractionManagerService`):
- The service needs `BIND_VOICE_INTERACTION`, and its metadata needs `sessionService`, `recognitionService` and `supportsAssist="true"`.
- The role is `requestable="false"`, so the app can't request it; onboarding sends the user to `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS`.
- Becoming the assistant writes only `Settings.Secure.ASSISTANT` and `VOICE_INTERACTION_SERVICE`. It never changes the default speech recognizer, which is chosen only among services with the `RecognitionService` intent filter.
- From Android 12, `recognitionService` "does nothing" but must be non-null. Ours points to `UnusedRecognitionService`, which has **no intent filter**.

**Session** (`VoiceInteractionSession`):
- `setUiEnabled(false)` belongs in `onPrepareShow`, as its Javadoc says.
- `onShow` still runs with the UI disabled, and no window is ever added.
- Use `hide()`, not `finish()`.
- `startAssistantActivity` launches into a separate assistant layer.

**Overlays:**
- `PhoneWindowManager.checkAddPermission` allows `TYPE_ACCESSIBILITY_OVERLAY` without `SYSTEM_ALERT_WINDOW`; it's gated only by the `OP_CREATE_ACCESSIBILITY_OVERLAY` app-op.
- The WindowManager must be obtained from the `AccessibilityService` itself; `getSystemService` sets the service's window token as the default.
- Windows added via `WindowManager.addView` get hardware acceleration **only** with an explicit `FLAG_HARDWARE_ACCELERATED` (`ViewRootImpl.enableHardwareAcceleration`).
- Blur behind needs `FLAG_BLUR_BEHIND` plus `blurBehindRadius`.
- `addCrossWindowBlurEnabledListener` fires immediately with the current state, then on every change.
- `BlurController` disables blur when: it isn't supported, `disable_window_blurs` is set, battery saver is on, media tunneling is active, or thermal status is ≥ CRITICAL.
- **Legibility over the backdrop:** the blurred backdrop can't be measured (the system composites it after we draw), so panel text uses a soft dark shadow (`TextStyle.onBackdrop()`), page dots have a dark outline, and the blur dim is 0.2. The user confirmed labels are readable over a white banking app.
- **Blur-behind must be paired with a non-zero dim** (`FLAG_DIM_BEHIND`, dimAmount 0.1; 0.4 when blur is unavailable), as in the AOSP window-blurs guide (source.android.com/docs/core/display/window-blurs).
  - What happened (found on-device 2026-10-08, Pixel 9 Pro XL, Android 17): with dimAmount 0, the window's dim layer has alpha 0 and only a blur radius. The display hardware sometimes composited it (`DEVICE` in `dumpsys SurfaceFlinger`), and the hardware can't blur.
  - Symptoms: no blur on screen, yet blur visible in `screencap` (which is always GPU-composited), and blur appearing once anything forced recomposition (e.g. swiping pages).
  - Fixed in `OverlayBackdrop.Blur`.

**Back:**
- With callbacks enabled (targetSdk 36+ on Android 16+, or the manifest `enableOnBackInvokedCallback`), `ViewRootImpl` routes Back to `OnBackInvokedCallback` before views; views never get `KEYCODE_BACK`.
- The app opts in on API 33+. API 31–32 use the key event.

**Accessibility:**
- `GLOBAL_ACTION_POWER_DIALOG` = 6 (API 21).
- `takeScreenshot(displayId, executor, callback)` requires `canTakeScreenshot` in the metadata, and is rate-limited to one call per 333 ms (`ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`).
- `takeScreenshotOfWindow` also exists.

**ONNX Runtime 1.30 Java:** on Android, `OnnxRuntime.init()` only `loadLibrary`s `onnxruntime4j_jni` (see §4).

**Libraries:**
- `hiltViewModel()` is in package `androidx.hilt.lifecycle.viewmodel.compose` (artifact `hilt-lifecycle-viewmodel-compose` 1.4.0).
- AGP 9.4 requires Gradle ≥ 9.6.0.
- Hilt Gradle plugin ≥ 2.59 requires AGP 9.
- KSP 2.3.10+ fixed AGP 9 built-in Kotlin.
- Compose still needs `buildFeatures.compose = true`.

## 11b. Open technical questions

- ~~Is `takeScreenshot` fast enough?~~ Yes: 9–32 ms (§3.5).
- What detection input size gives the best accuracy/speed for full-screen captures on a Pixel 9 Pro XL? Measure at the OCR checkpoint.
- What does the Pixel's power long-press pass in `onShow` args (`invocation_type`)? Not needed yet; would allow distinguishing the power button from other assistant gestures.

## 12. Known risks and potential bugs

- Battery saver turns off cross-window blur. The overlay must switch to dim live, not only on the next open.
- On Android 13+, accessibility for sideloaded apps sits behind "restricted settings". Onboarding must walk the user through *App info → ⋮ → Allow restricted settings*.
- The Digital assistant role replaces Gemini/Assistant, so "Hey Google" stops working. This is accepted.
- The system may remove `TYPE_ACCESSIBILITY_OVERLAY` windows after screen-off or lock (XGesture's `FloatBallOverlay.kt` comments on this). The overlay must handle being removed externally.
