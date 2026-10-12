# AGENTS.md — Power++

This is the single source of truth for Power++. An engineer or LLM reading only this file should be able to continue exactly where the last session stopped. Update it whenever a decision is made, a question is asked or answered, or work starts or finishes.

## 0. Resume here

**Session 4 (2026-10-12, the work machine):** checked this file against the code; three statements were out of date and are fixed (§3.2 copy order, §3.5b capture order, §10 shared bars). Then built the **Whisper assistant** (§3.8):
- on-device speech recognition with whisper.cpp and FUTO's base.en model
- a voice wave at the bottom of the panel
- a shared command modal
- **Reminders** delivered by **ntfy**
- a "Whisper assistant" settings section

The user confirmed it works on the phone. Then added **Upcoming reminders** (§3.8): a screen that reads the reminders back from ntfy each time it opens, and edits, deletes and adds them with the same modal. Built, tested and installed; not yet tried by the user. Not pushed.

**Session 3 (2026-10-09, the user's home machine):** reviewed every pushed commit against this file; they matched apart from one error (§3.5b, fixed). Every user-facing message now shows inside Power++'s own windows instead of as a toast (§3.2, §3.4). Then built the **Media extractor** module and a **Scan** button in Smart screenshot (§3.7). Pushed by the user on 2026-10-10 (§10).

- **State:** every v1 module is built and **confirmed working by the user on-device**:
  - the panel and trigger (§2)
  - Power menu (§3.1)
  - Screen text (§3.2); its new selection list and editing aren't verified on-device yet (§10)
  - OCR text (§3.3), fully offline, with the selection list and editing
  - Smart screenshot, both the selection and the editor (§3.4); not re-checked since its selection moved to `:core:selection` (§10)
  - the activation screenshot and Retake (§3.5, §3.5b)
  - the OCR engine/model manager, with ORT telemetry disabled (§4)
- **Next action:** have the user try **Upcoming reminders** (Settings → Whisper assistant): list, edit (including times and early reminders), delete, and New reminder. The user is raising their own ntfy server's `message-delay-limit` (§11); afterwards **Re-check limit** should show the new value. Transcripts are logged at info level under `PowerPP.Assistant` / `PowerPP.Whisper`.

  Still pending from session 3: Media extractor and Smart screenshot's Scan on real apps (the user mostly browses in **Firefox**; tune `MediaScan` from that, §3.7), and re-checking Smart screenshot and Screen text (§10). The open question for the user is in §7 (Small model).
- **Home machine before building:**
  - `git submodule update --init` (whisper.cpp, §6)
  - install the NDK and CMake with `~/.android/bin/android-cli --no-metrics sdk install 'ndk/28.2.13676358' 'cmake/3.31.6'` (§6)
- **How work is done here:**
  - Verify every API against AOSP or the pinned library source before relying on it. §11 lists what's already verified; don't re-research those.
  - Lint must stay at zero issues.
  - Commit after each confirmed step.
  - The user tests on a Pixel 9 Pro XL (Android 17) over **wireless adb**. The address changes per session; ask the user for the `adb connect` line.
  - **Don't send input (taps or swipes) to the user's device.** Read state only (logcat, dumpsys, screencap) and ask the user to interact.
  - The phone's notifications are off for Power++, so **toasts don't show** (`NotificationService: Suppressing toast … by user request`). Read results from logcat. Every user-facing message is shown inside Power++'s own windows (`MessageBar`/`MessageScreen` in `:core:ui`, §5); don't add toasts.
- **Code ownership:** all Power++ code is written from scratch. XGesture is only read to learn behavior and which APIs it uses; nothing is copied from it.
- **Repository:** `~/github-clones/PowerPlusPlus` on the user's home machine and `~/Downloads/Github_Projects/Power++` on the work machine (both in use), branch `main`. Commits are authored as `Bo0sted <mc526+github@proton.me>`, set in the repo-local git config of each clone (the global identity differs), with no Claude co-author trailer. The remote and how to push are in §10.
- **Reference app:** a local clone of XGesture next to the repo, at `~/github-clones/XGesture` at home and `~/Downloads/Github_Projects/XGesture` at work (`github.com/qpst4/XGesture`, AGPL-3.0, package `com.slideindex.app`). Power++ takes ideas and end-user behavior from it but is written independently. Where Android offers a better or official way, use it. Pointers to the relevant XGesture files are in §9.

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
  - Copy joins the picks in the order they were tapped (edits included, §3.3), one per line, and closes
  - "No text found on screen" when there's nothing: the window opens with only that message (`MessageScreen` over the live screen); tapping anywhere or × closes it
  - XGesture's extra "Segment" button (its word-split panel) is intentionally **not** included; v1 is Copy-only.
- **Reading:** all windows from `service.windows`, except accessibility overlays, the IME and our own package.
  - Per node: `text`, else `contentDescription`, else `stateDescription`.
  - Nodes without `text` must be `isVisibleToUser`; nodes with `text` are kept even when not visible (XGesture found chat apps report message bodies that way). Bounds are clipped to the window.
  - **Password fields are always skipped** (an improvement over XGesture).
  - Traversal runs on `Dispatchers.Default`.
- **Copy:** `ClipData.newPlainText`. `copyToClipboard` returns whether to confirm: only on API ≤ 32 does the window show "Copied" for `MESSAGE_CLOSE_DELAY_MS` (1.5 s) before closing, because Android 13+ shows its own clipboard confirmation and the official copy/paste guide says not to duplicate it. OCR text does the same.
- **Selection list and editing** (user, 2026-10-09): shared with OCR text, described in §3.3.
- Future actions (search, translate…) may come later.

### 3.3 OCR text search

**Implemented** as `:module:ocrtext` (typeId `ocr_text`, "OCR text", `usesActivationScreenshot = true`) on the pipeline in `:core:ocr`. **Confirmed by the user on-device, 2026-10-09.** Everything runs offline.

**Flow** (the user's design, 2026-10-09; it replaced a first cursor + double-tap + drag version the user didn't like):
1. Tapping the module opens a focusable `OverlayWindow` **immediately**, with the frozen activation capture and Smart screenshot's resizable selection (`:core:selection`) at the same default size.
   - The models start loading in the background (`TextRecognizer.preload()`) while the user adjusts the selection.
   - No capture, a blocked (black) capture, or a missing model shows a **message in the window** (with "Open settings" for the model), never a toast.
2. **Two centred rows at the bottom:** row 1 has the shapes (Rectangle · Full-width strip · Full-height strip, but no Circle, which would cut words off), Max and Default; row 2 has Retake (Android 14+) and Confirm. Both rows hide while dragging. Tapping outside the selection closes the window, as in Smart screenshot.
3. **Confirm** shows the selection with a spinner and "Recognizing text…", then runs OCR on the crop.
4. **Results** use `TextPickScreen` over the frozen capture (`background`). OCR coordinates are region offset + box, which map 1:1 to screen pixels.
   - **Tapping outside a box never closes OCR results** (user: OCR takes long). Only Back and × close (`closeOnTapOutside = false`).
   - No text, or a failure, shows a message with "Try again", which returns to the selection unchanged.

**Selection list and editing** (user, 2026-10-09), in `:core:textpick`, so Screen text has it too:
- Tapping a box appends its text to a list in tap order; tapping it again removes it. `PickList` is pure and unit-tested.
- The bottom bar shows "N selected ▴", which opens the list **above** the bar, without hiding the results.
  - The list shows about **3 entries**, then scrolls (user: it mustn't cover the screen). It follows each new pick.
  - The text is large (22 sp, letter-spaced 0.5 sp) so individual characters can be checked (user).
- **Picked boxes are green** and **edited picks are red** (box and list entry), each with a dark- and light-theme shade chosen by the surface's luminance. The overlays currently always use the dark theme.
  - Green: `#6FCF7F` dark, `#2E7D32` light. Red: `#FF6B6B` dark, `#C62828` light.
  - The theme's own colours were too easy to miss, and a fixed bright red didn't blend (user, 2026-10-09).
- Each entry has Edit and Remove.
- **Copy** copies the list in pick order, edits included, one per line. Select all appends the rest in reading order.
- **Editing:** the window shrinks to a card at the top (`OverlayWindow.setCompact`) with "Original: …", a text field, Restore, Cancel and Done.
  - Why: the keyboard is drawn *below* accessibility overlays and can't be lifted above them (§11).
  - Back first leaves editing (`OverlayWindow(onBackRequest = …)`); screen off still closes.
- **Pinch-zoom** (user, 2026-10-09), only over a frozen `background` (OCR). Screen text's boxes sit over the live app, so they can't zoom.
  - The image and boxes zoom together (1–6×) via `graphicsLayer`, so the boxes stay on their text.
  - Two-finger pan. Outlines keep their on-screen width.
  - Taps are mapped back through `ZoomViewport` (pure, unit-tested), so they still pick the right box.
  - Zoom survives editing.
  - **Reset zoom** appears whenever zoom > 1×, centred just above the selection list (or the toolbar when the list is closed) (user, 2026-10-09).

**Pipeline** (`PpOcr`, reproducing PaddleX's general OCR pipeline: `paddlex/configs/pipelines/OCR.yaml` and the predictors it uses, read in the PaddleX/PaddleOCR sources 2026-10-08):
- **BGR for both models.** PaddleX reads images as BGR, the models' `DecodeImage` says BGR, and the PP-OCRv6 *training* configs use BGR. PaddleX's `onnxruntime` engine uses the Runner predictors, which keep BGR. XGesture feeds recognition RGB, which deviates from training; not followed.
- **Detection:**
  - `DetResizeForTest`, `limit_type: min`, `limit_side_len 64`, `max_side_limit 4000`, both sides rounded to a multiple of 32 with Python's half-to-even `round`. A 1344×2992 screen therefore runs at 1344×3008.
  - Normalise `(x/255 − mean)/std` per BGR channel index (mean `[0.485, 0.456, 0.406]`, std `[0.229, 0.224, 0.225]`).
  - `DbPostProcess`: thresh **0.3**, box_thresh **0.6**, unclip **1.5**, max 3000 candidates, "fast" score, quad boxes.
  - The pipeline's values override the model's own `inference.yml` (0.2 / 0.45 / 1.4, which an earlier version of this file listed). XGesture uses the same pipeline values.
  - Simplifications for upright screen text: regions are axis-aligned bounding boxes of 8-connected components, and holes are ignored.
- **Recognition:**
  - Crop each box; if `h ≥ 1.5·w`, rotate 90° anticlockwise (`np.rot90`).
  - Resize to height 48 keeping the aspect ratio. Pad each line to `max(320, 48·w/h)` (at most 3200), then pad the batch to its widest line with zeros.
  - Normalise `x/127.5 − 1`.
  - Batches of **6**, sorted by aspect ratio.
- **CTC:** argmax, collapse repeats, drop blank 0. The score is the mean of the kept probabilities. The pipeline's `score_thresh` is **0.0**, so no line is dropped. Classes are `["blank"] + dictionary + [" "]`.
- Not used: PaddleX's optional document unwarping and text-line orientation models.
- **Engine settings:** plain CPU, `setIntraOpNumThreads(min(4, cores))`, the same thread count as XGesture. Measured on the Pixel 9 Pro XL (2026-10-08, full screen, Medium):

  | Setup | Detection | Recognition |
  |---|---|---|
  | ORT default (8 threads, including the 4 slow cores) | 11.8–12.4 s | 3.4 s (17 lines) |
  | **4 threads** | **6.9–7.4 s** | 4.6 s (21 lines) |
  | XNNPACK EP (ORT's recommended setup) | **SIGSEGV** inside `libonnxruntime.so` on the first run | |

  - Model load: 1.1 s once per process. A selected region is proportionally faster.
  - The user reports XGesture with Medium takes about 10 s.
- **Threading:** `TextRecognizer` (@Singleton) loads `PpOcr` once and runs calls one at a time on `Dispatchers.Default`.

### 3.4 Smart screenshot
- **Step 1 is implemented** as `:module:screenshot` (typeId `smart_screenshot`, `usesActivationScreenshot = true`). **Step 2 (the editor) is implemented.** Both steps are confirmed working by the user on-device.
- **Flow:**
  1. The module `take()`s the activation screenshot. If there is none, a window opens with only "Couldn't capture the screen" (`MessageScreen`; tap anywhere, × or Back closes).
  2. It makes a software ARGB copy (`Dispatchers.Default`).
  3. `ScreenImage.looksBlocked` (`:core:capture`; a 48×48 sample grid over the middle 80 % of the height, all near-black) → the same kind of window with "This app blocks screenshots".
  4. Otherwise it shows `SelectScreen` in a focusable `OverlayWindow`.
- **Messages in `SelectScreen`** (user, 2026-10-09: no toasts; the features only run in the overlay, so their messages belong there): a `MessageBar` at the bottom (`notice`).
  - While an action runs (`busy`), the toolbars hide and the selection is fixed.
  - **Save** shows "Saved to Pictures/Power++" for 1.5 s, then closes. **Copy** closes at once (on API ≤ 32 it shows "Copied" first). **Share** starts the chooser and closes.
  - A failure ("Couldn't create the image") keeps the window open with the toolbars back, so the user can try again. A failed Retake shows its message the same way.
- **`SelectScreen`** (XGesture-like), built on the shared `SelectionCanvas`, `ShapeToggle` and `SelectionGeometry` in `:core:selection` (moved there 2026-10-09 so OCR text reuses them):
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
- **`ImageOutput`** (in `:core:media` since 2026-10-09, shared with Media extractor; it was `ScreenshotOutput`):
  - **Copy/Share** write a PNG to `cacheDir/screenshots/`, served by `:core:media`'s FileProvider (`${applicationId}.screenshots`, name kept so existing clipboard links still resolve), and keep only the 5 newest files.
  - **Save** writes to `Pictures/Power++` via MediaStore (`IS_PENDING`; no permission needed). File names are timestamps that never repeat, even for several images saved in the same millisecond.
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
  - **Messages** (2026-10-09): a `MessageBar` at the bottom of the image. Save shows "Saved to Pictures/Power++" for 1.5 s, then the editor closes; Copy (API ≥ 33) and Share close at once; a failure keeps the editor open with the message so the user can try again.

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
  - A blocked (black) result shows "This app blocks screenshots" in the window and keeps the old image.
  - The replaced bitmap is left to the GC, not recycled, because a frame in flight may still reference it.
- **OCR:** also gets Retake. Placement: the region-selection stage, in the second bottom row next to Confirm (`RegionScreen`).
- **Implementation (`ScreenRecapture`, in `:core:capture`):** Android 14+ only (`takeScreenshotOfWindow`); the button is hidden on 12–13, which have no per-window capture, and the only alternative would be a timing guess.
  - Each window from `getWindows()` except accessibility overlays is captured and stacked bottom-up onto a black display-sized canvas, after converting hardware bitmaps to software.
  - The windows are captured one after another; the rate limit is per window id, so they don't conflict.
  - The platform Javadoc recommends `takeScreenshotOfWindow` for windows under an accessibility overlay.
- **Verified limitation:** `AccessibilityWindowManager.isReportedWindowType` excludes `TYPE_WALLPAPER` (also non-touchable windows, input consumers, etc.). So a retake shows **black where the wallpaper was** (e.g. behind home-screen icons); app content is unaffected. The same code shows `TYPE_ACCESSIBILITY_OVERLAY` doesn't occlude windows below it in that list.

### 3.6 Smart screenshot selection shapes (user, 2026-10-08)
- Default: a resizable rectangle in the center (like XGesture).
- **Horizontal edge-to-edge strip:** always spans the full screen width. Its height is adjustable (sensible default) and it can be dragged up and down.
- **Vertical edge-to-edge strip:** always spans the full screen height. Its width is adjustable (sensible default) and it can be dragged left and right.
- A circle.
- Plus the editor tools from §3.4 (blur, pixelate, pen, …).

### 3.7 Media extractor and Scan (user, 2026-10-09)

**Implemented** as `:module:mediaextractor` (typeId `media_extractor`, "Media extractor", `usesActivationScreenshot = true`) on `:core:media`, plus a **Scan** button in Smart screenshot. Built, unit-tested and installed; **not yet tried by the user on-device**, and the scan thresholds have only been checked on synthetic images and one real (media-free) lock-screen capture.

**How media are found** (the user asked "math or a model?"; decided: math):
- **Accessibility** (`ScreenMediaFinder`): nodes whose class is `android.widget.ImageView`, `android.widget.Image`, `android.widget.ImageButton` or `android.widget.VideoView`, or whose view id (after the `/`) matches `exo_content_frame|video|player`. They must be visible, are clipped to their window, and need at least `MediaFinder.MIN_SIDE_DP` (72 dp) on each side, so icons and avatars drop out. Exact bounds, but it's the live tree, not the capture. Why each name: §11.
- **Scan** (`MediaScan`, pixels only; no OCR, no model, no download):
  - Why not a model: PP-OCR finds text, not pictures. Layout and UI-detector models are 50–200 MB, take seconds, are trained on other kinds of images, and some are AGPL (OmniParser). ML Kit needs Play Services. On-screen media are upright rectangles on app backgrounds, which is a geometry problem.
  - Steps:
    1. Shrink the capture to about 360 px wide (a quarter on the Pixel).
    2. **Background** = flat regions touching the area's edge: neighbours within 6 per channel, within 12 of the region's first pixel (so gradients inside photos can't creep), and at least `minSide` pixels (so a 1 px divider counts, instead of joining what touches it).
    3. Every other 8-connected component ≥ `minSide` both ways is a block. It counts as media if it has ≥ 24 distinct colours (4 bits per channel), or no colour covers more than half of it. Buttons and text-only cards fail both.
    4. A block whose outline is ≥ 50 % background-flat (a card with padding, a letterboxed player) is split again the same way, up to depth 3. The card and the picture inside are both kept; tapping (smallest under the finger) or cycling chooses.
    5. Edges are snapped at full resolution to the strongest row/column contrast within ±2× the shrink factor, which undoes the rounding.
  - Measured: 15 ms at working size on the PC for a 1344×2992 lock screen (found nothing, correctly). Not yet timed on the phone.
  - Known limits: a picture whose edge matches the app around it (a white meme on a white app, a black-and-white comic on white) has no edge to find. A full-bleed photo with flat sky touching the screen edge can lose that strip. Apps that draw everything themselves (games) report nothing through accessibility either.
- `MediaFinder.all` = accessibility + scan, near-duplicates removed (IoU ≥ 0.9, first kept, so app bounds win). `distinctRegions`, `orderedAround`, `regionAt` and `RegionCycle` are pure and unit-tested.

**Media extractor UI** (`MediaScreen`, the user's design):
- The frozen capture with a box around each region. Tapping a box toggles it (green, `pickedColor()`); tapping elsewhere does nothing (a scan mustn't be lost to a stray tap). Back and × close.
- Bottom, from the top (`OverlayBottomBars`):
  1. a notice (`MessageBar`) for confirmations and failures
  2. the status/actions bar: "Looking for media…"/"Scanning…" with a spinner; in Accessibility with no media, "No media detected. Try Scan" with a **Scan** button (the × is always there); in Scan with none, "Scan found no media"; with nothing selected, "Tap media to select it"; otherwise "N selected" with **Copy** (exactly one selected) and **Save** (any number)
  3. the view row (segmented): **Accessibility** (default, read when the window opens) · **Scan** (runs the first time it's opened, then cached)
  4. **Retake** (Scan view only, Android 14+): recaptures under the overlay (§3.5b) and rescans
- Switching view clears the selection.
- **Copy** puts the one crop on the clipboard and closes ("Copied" first only on API ≤ 32). **Save** writes each crop as its own PNG, shows "Saved to Pictures/Power++" or "Saved N images to Pictures/Power++" for 1.5 s, then closes. A failure keeps the window open.
- What's saved is the capture cut to each region: screen resolution, and for a video, the frame that was on screen. Accessibility doesn't expose image files or URLs.

**Smart screenshot Scan** (user: same detection, but it only resizes the selection and doesn't confirm; rectangle only):
- The button sits in the Max/Default/Retake row and shows only while the shape is Rectangle.
- The first tap finds `MediaFinder.all` for the current image (cached; Retake clears it). It moves the selection to the smallest region under the selection's centre. Each further tap steps to the next region (larger ones around the centre, then the rest, nearest first), wrapping around. Moving or resizing the selection starts over from there (`RegionCycle`).
- No media → "No media found" in the window's notice.

### 3.8 Whisper assistant and voice commands (user, 2026-10-12)

**Implemented**, not a module: the assistant is built into the panel. Built, unit-tested, lint clean, installed; **not yet tried by the user on-device.**

**What the user asked for:**
- Opening the panel starts listening. The modules stay usable.
- A voice wave across the bottom, with a "Whisper logo". There's no official one, so the user chose our own glyph: three breath strokes in a glowing orb.
- A valid command opens a **shared modal** to configure it. Back, Cancel and Confirm close only the modal; the panel stays open for more.
- No valid command shows **"Command not recognized"** in the window (no toast, §0), plus what was heard, and listening continues.
- A "Whisper assistant" settings section: download models, configure ntfy (its own switchable part), and a list of commands with a switch each.
- **Dependency-free by design:** commands declare what they need. Reminders needs ntfy; future commands may not. ntfy settings are saved **only after a successful post**; any error refuses the save.

**Speech** (`:core:whisper`, whisper.cpp v1.9.5 as the submodule `third_party/whisper.cpp`, MIT):
- FUTO Keyboard uses a modified whisper.cpp (GGML), **not ONNX Runtime**. Its code is FUTO Source First licensed, so nothing is taken from it.
- **Model:** FUTO's ACFT base.en q8_0 (the user's choice; FUTO bundles tiny.en and offers base/small).
  - From FUTO's own host: `keyboard.futo.org/voice-input-english-74.bin`, which redirects to `dl.keyboard.futo.org`.
  - 81,781,811 B, SHA-256 `e9b4b7b8…f1c8`. This is the hash FUTO Keyboard itself pins for `base_en_acft_q8_0.bin`.
  - The host supports Range (206).
  - The model card is Apache-2.0 on Hugging Face; the `whisper-acft` repo says MIT.
  - Installed through `:core:download` to `noBackupFilesDir/whisper/`. Installed = it loads.
- **ACFT:** the encoder context is `clamp(ceil(samples/320) + 32, 160, 1500)`; FUTO uses the same margin and minimum. `SpeechModel.audioContext` is unit-tested.
- **Parameters:** greedy, `language` from the model ("en"), single segment, no timestamps, `suppress_nst`, no temperature fallback, threads `min(4, cores)` like OCR.
- **JNI:** `powerpp_whisper.cpp` returns UTF-8 bytes; `NewStringUTF` aborts on 4-byte sequences.
- **ggml CPU variants:**
  - On arm64, `GGML_BACKEND_DL` + `GGML_CPU_ALL_VARIANTS` builds 7 Android CPU variants (ARMv8.0 … ARMv9.2+SME). `ggml_backend_load_all_from_path(nativeLibraryDir)` loads the best one.
  - That needs extracted native libraries: the app uses `jniLibs.useLegacyPackaging = true`.
  - armeabi-v7a and x86_64 get one generic CPU backend; x86 isn't built and reports "not available".
  - The native code is always compiled as Release (`-O3`); a debug build at `-O0` would be unusably slow.
  - **APK cost:** 6.8 MB compressed for all ABIs, 9.1 MB installed on arm64.
  - whisper.cpp's unused Parakeet library (~150 KB) is packaged because AGP packages every library target and whisper.cpp has no option to skip it. Limiting AGP to our own target would also drop the runtime-loaded backends.
- whisper.cpp's own Android example builds an fp16 variant whose flags never reach ggml (FetchContent runs once), so it wasn't followed.

**Session** (`:core:assistant`, `AssistantSession`, one per panel opening; `PanelController` opens and closes it):
- Off (no bar, no microphone) unless a model is installed **and** a command is on. Without the microphone permission, the bar says so with a Settings button.
- **Listening:**
  - `AudioRecord` with `VOICE_RECOGNITION`, 16 kHz mono float, opened per utterance.
  - `Endpointer` (pure, unit-tested) works on 20 ms frames with an adaptive noise floor (×2.5, at least 0.004 RMS).
  - Speech starts after 3 loud frames and ends after 0.8 s of quiet. At least 0.24 s, at most 20 s, plus a 0.3 s pre-roll.
- The model loads in parallel with the first utterance and is freed when the panel closes (after any running transcription).
- The first active command whose `draftFor(transcript, now)` returns a draft wins. Listening pauses while the modal is open.
- **Modal:** a card at the top over a scrim; the scrim swallows taps so a command isn't lost.
  - While a text field has focus, the window goes compact (`OverlayWindow.setCompact`, which now also drops the dim/blur, since that would cover the keyboard) and only the card is drawn. The card stays at the same place in the composition, so focus survives.
  - Back order: leave typing → the draft's own back (e.g. close a picker) → close the modal → close the panel.
  - Confirm runs `CommandDraft.confirm()`. On failure the error shows and the modal stays.
  - **No Compose dialogs or popups in overlays:** they need an activity window token. Date and time pickers are pages inside the card.

**Commands** (`AssistantCommand` SPI, `@IntoSet`, like panel modules):
- Each command has `id`, `title`, `description`, `available: Flow<Boolean>` (its requirements), `requirement` (what to set up) and optionally `screen: CommandScreen` (a title, a description and `open(context)`). The settings section lists each screen under its command, enabled while the command is available.
- `AssistantCommands` stores the user's switches in `assistant.json`. A command is on only while switched on **and** available; the switch itself is kept.

**ntfy** (`:core:ntfy`, verified in the ntfy server source on 2026-10-12, §11):
- `NtfySettings(enabled, server?)` in `noBackupFilesDir/ntfy.json` (it holds a token).
- The server address is https only; a scheme is added if missing. Topic `[-_A-Za-z0-9]{1,64}`. Optional access token (Bearer); no username/password.
- **Test and save:**
  1. A JSON publish of "ntfy is set up for Power++."
  2. Measure the delay limit.
  3. Save only if both succeed.
- **Delay limit:** ntfy has no API for `message-delay-limit` (ntfy.sh: 3 days).
  - Each probe is a publish with `X-Delay: <n>s` and an invalid `X-Actions`. The server checks the delay first (40006 = too far) and the actions after it (40018 = the delay was fine), so nothing is ever stored or sent.
  - A binary search over minutes, up to 10 years, takes about 23 probes (`findDelayLimit`, unit-tested).
  - **Re-check limit** measures it again (the user's request: after changing the server's limit).

**Reminders** (`:command:reminders`, id `reminders`, available while ntfy is ready):
- **`ReminderParser`** (pure, 12 tests): a trigger ("remind me", "set a reminder", polite prefixes), time phrases found anywhere and removed, and the rest is the task.
  - Understands relative times, today/tonight/tomorrow/the day after, weekdays (always the next one), month+day, noon/midnight, h[:mm] am/pm, 24-hour times, "at h", and parts of the day.
  - An ambiguous "at 8" today is the next 8 o'clock. On a named day, 1–6 means pm and 7–11 am. A date without a time means 9:00.
  - No time spoken: the modal starts at the next full hour.
- **Modal:**
  - Text, date and time (pages with M3 `DatePicker`/`TimePicker`).
  - **Unlimited early reminders:** each is an amount plus minutes/hours/days ("Reminder coming up in 2 hours").
  - **Priority** (user, 2026-10-12): a row of buttons for ntfy's five priorities, Min · Low · Default · High · Max, with **Max selected by default**. It applies to the reminder and its early reminders.
    - ntfy's levels are 1–5 (`util.ParsePriority`: min, low, default, high, max/urgent).
    - Polled messages omit the field when none was set, so a reminder scheduled before priorities existed reads back as Max; an explicit Default comes back as 3.
  - Confirm is disabled, with the reason, if the text is empty, the time is less than 10 s ahead (ntfy's minimum), it's past the server's limit, or an early reminder is invalid or in the past.
- **Sending:** one scheduled JSON publish per notification (`delay` = Unix seconds).
  - Every message carries the chosen `priority`.
  - The main notification is titled "Reminder" (tag `alarm_clock`); early ones "Reminder coming up in …" (`hourglass_flowing_sand`).
  - Sequence IDs `powerpp-<id>-<n>`, so confirming again after a partial failure replaces instead of duplicating.
  - Power++ keeps no reminder list, alarms or notifications of its own (the user: ntfy handles notifications, and Power++ stores nothing).

**Upcoming reminders** (user, 2026-10-12; `RemindersActivity`, opened from the settings row that `AssistantCommand.screen` provides):
- **Read from ntfy every time the screen starts** (`ON_START`), and after each change: `GET /<topic>/json?poll=1&sched=1&since=<now>`.
- `Reminders.group` (pure, unit-tested) rebuilds reminders from that:
  - keeps `event == "message"`, time after now, sequence ID `powerpp-<12 hex>-<n>`
  - n = 0 is the reminder; n ≥ 1 are early reminders, whose lead time is the reminder's time minus theirs
  - a group without n = 0 is dropped
  - early reminders already delivered aren't listed
- **Edit** opens the same `ReminderDraft` in the same `CommandModal`, titled "Edit reminder", with early reminders shown in their largest exact unit. Confirm republishes under IDs 0…k, which replaces those messages, and deletes the reminder's other old IDs.
- **Delete** (asks first) sends `DELETE /<topic>/<id>` for each of its IDs.
- **New reminder** opens an empty draft at the current date and time (the user's choice, since nothing was spoken). It needs moving forward before Confirm enables.
- The modal is a Compose `Dialog` there: activities allow dialogs, and the keyboard resizes the window normally. Back steps back within the draft, then closes the modal; taps outside are ignored.
- **Shared with the panel:** `CommandModalState` (draft, confirming, error; `open`/`cancel`/`confirm(scope)`/`onBack`) and the `CommandModal` card live in `:core:assistant`. The panel wraps the card with focus tracking for compact mode.

**Settings** (`AssistantSection`, `AssistantViewModel`):
- The model rows reuse `ComponentRow`, now shared with the OCR section.
- Microphone: Allow, or App info once Android stops asking.
- ntfy: switch, server/topic/token, Test and save, the saved server and its limit, Re-check limit, and "Not saved: <reason>".
- Commands: one switch each, disabled with the requirement shown while unavailable.

## 4. OCR engines and models

**Implemented 2026-10-08** as `:core:ocr` plus the settings section "OCR engine and models" (`app/settings/OcrSection.kt`). **Verified on-device** (the user downloaded both components; see §10). The pipeline and module are in §3.3.

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
- **Shared with Whisper (2026-10-12):** `RemoteFile`, `VerifiedDownloader`, `ComponentState` and the install/remove machinery now live in `:core:download` (`ComponentStore`, below). `OcrComponents` is a `ComponentStore` that adds the engine-first rule and the AAR extraction.
- **`VerifiedDownloader`:**
  - writes to `<name>.part` and resumes with a Range request (restarting if the host replies 200)
  - tries each URL in order
  - accepts a file only when its size and SHA-256 match, deletes mismatched bytes, then renames it into place
- **`OcrComponents`** (@Singleton, on `ComponentStore`):
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
- **Pipeline parameters:** see §3.3. They come from PaddleX's OCR pipeline, not the models' `inference.yml`. `RecognitionDictionary` parses the dictionary; it's unit-tested against the quoting rules (PyYAML reads 18,708 plain or single-quoted entries).
- **Other models:** the pipeline reads the class count and dictionary from the model files, so other PP-OCR (DBNet + CTC) models should work. Check their configs against §3.3 before adding them to `OcrCatalog`. Non-PP-OCR engines would need their own pipeline.
- **Permissions:** `INTERNET` (declared by `:core:download` and `:core:ntfy`) is used **only** for user-initiated downloads and for posting to the user's ntfy server; `RECORD_AUDIO` (`:core:assistant`) only while the panel is open (§3.8). The final APK requests only those two (plus AndroidX's internal dynamic-receiver permission); `ACCESS_NETWORK_STATE`, added by the ORT AAR, is removed with `tools:node="remove"` in the app manifest.
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
| `build-logic` | Included build with the convention plugins: `powerpp.android.application`, `powerpp.android.library`, `powerpp.android.compose`, `powerpp.hilt`, `powerpp.serialization`, `powerpp.panel.module`, `powerpp.assistant.command`. `PowerBuildConfig` also pins the NDK and CMake versions. Modules apply plugins **only** through these; AGP, KSP and the rest are on build-logic's classpath, not resolvable by id directly. |
| `:core:ui` | `PowerTheme`; the overlay building blocks `OverlayBar` (the rounded bottom pill; swallows taps), `OverlayBottomBars` (stacks bars at the bottom centre), `OverlayBarText`, `OverlayCloseButton`, `MessageBar`, `MessageScreen` (a window showing only a message; tap anywhere closes) and `MESSAGE_CLOSE_DELAY_MS` (1.5 s, how long a confirmation shows before its window closes); `pickedColor()`/`editedColor()` (§3.3's green and red); `onBackdrop()`/`BackdropOutline` (legibility over the blurred backdrop, used by the panel and the voice bar). Messages replace toasts everywhere. |
| `:core:module` | The module SPI: `PanelModule` (sealed; `usesActivationScreenshot` defaults to false), `ActionModule` (`run(host)`; the panel is dismissed first), `ToggleModule` (`isOn(host): Flow<Boolean>`, `setOn`; the panel stays open), `ModuleHost` (the connected `AccessibilityService`, a scope tied to the connection, and `activationScreenshot`), `ActivationScreenshot` (single-owner holder: `take()` hands the bitmap over and the caller must recycle it; `replace()` recycles the old one), `ModuleRegistry` |
| `:core:data` | `PanelConfig` (grid columns×rows, swipe axis, backdrop, pages → `ModuleSlot(id, typeId, cell, enabled)`). Pure edit functions are in `PanelEdits.kt` and unit-tested; a rejected edit returns the same instance. `PanelRepository` wraps DataStore (`panel.json`) and replaces the file with defaults if it's corrupt. `jsonDataStore(file, serializer, default)` is that pattern for any `@Serializable` value (also used by `:core:assistant` and `:core:ntfy`). |
| `:core:overlay` | `OverlayWindow`: a full-screen `TYPE_ACCESSIBILITY_OVERLAY` hosting Compose. It provides its own Lifecycle and SavedState owners and blur with live fallback to dim. It calls `onDismissRequest` on Back **and on `ACTION_SCREEN_OFF`** (a non-exported receiver registered while showing); an optional `onBackRequest` takes Back instead. `setCompact` shrinks it to its content at the top (for keyboard input) and drops the dim/blur meanwhile. Single use: create a new one each time. `showMessageWindow(service, message)` opens a window that only shows a message. |
| `:core:capture` | `ScreenImage` (`toSoftware`, `looksBlocked`; unit-tested), `ScreenRecapture` (Retake) and `forEachScreenNode` (walks every app window's accessibility nodes, skipping overlays, the IME and our own package; used by Screen text and `ScreenMediaFinder`). |
| `:core:media` | Finding and saving on-screen media (§3.7): `MediaFinder` (`accessibility`, `scan`, `all`, `MIN_SIDE_DP`), `ScreenMediaFinder`, `MediaScan` (unit-tested), the region helpers in `MediaRegions.kt` (unit-tested), and `ImageOutput` (copy/share/save PNGs) with the FileProvider. Used by Smart screenshot and Media extractor. |
| `:core:selection` | `SelectionGeometry` (unit-tested), `SelectionShape`, `SelectionCanvas` (frozen image, scrim, frame, handles, move/resize gestures; state hoisted), `ShapeToggle`, `rememberSelectionGeometry`, and the shape and Max/Default labels. Used by Smart screenshot and OCR text. |
| `:core:download` | Verified downloads (§4): `RemoteFile`, `Downloadable`, `VerifiedDownloader`, `ComponentState`, and `ComponentStore` (state per component, install/remove, resume, marker written last, hooks for prerequisites, skipping and the load check). Used by OCR and Whisper. |
| `:core:ocr` | The engine/model manager (§4) and the pipeline (§3.3): `PpOcr`, `DbPostProcess`, `CtcDecoder` (pure parts unit-tested), `TextRecognizer`. |
| `:core:whisper` | Speech recognition (§3.8): whisper.cpp from `third_party/whisper.cpp` built by CMake with a small JNI bridge (`src/main/cpp`), `SpeechModel` (load/transcribe/close; `audioContext` unit-tested), `WhisperCatalog` and `WhisperModels` (a `ComponentStore`). |
| `:core:ntfy` | `NtfyClient` (JSON publish, delay-limit probe; `findDelayLimit` unit-tested), `NtfyRepository`/`NtfySettings` (saved only after a successful post). |
| `:core:assistant` | The assistant (§3.8): the `AssistantCommand`/`CommandDraft` SPI, `AssistantCommands` (registry and switches), `Assistant`/`AssistantSession`, `VoiceCapture` + `Endpointer` (unit-tested), and `AssistantOverlay` (voice wave, whisper glyph, command modal). Depends on nothing command-specific. |
| `:core:textpick` | Shared text-selection overlay, used by Screen text and OCR text. `TextBlock(text, IntRect screen px)`; `normalizeTextBlocks` drops container summaries (blocks enclosing a smaller block), blanks and duplicates, and sorts in reading order. `hitTest` picks the smallest block under the tap, with 10dp slop. `TextPickState` + `PickList` (the selection list with edits; unit-tested), `ZoomViewport` (pinch-zoom maths; unit-tested), `TextPickScreen` (list panel, edit card, optional background with pinch-zoom, `closeOnTapOutside`), `showTextPick(service, blocks)`, `copyToClipboard`. |
| `:command:*` | One per assistant command. They apply `powerpp.assistant.command` and bind themselves with `@Binds @IntoSet AssistantCommand`. Currently: `:command:reminders`. |
| `:module:*` | One per panel module. They apply `powerpp.panel.module` and bind themselves with `@Binds @IntoSet PanelModule`. Currently: `:module:powermenu`, `:module:screentext`, `:module:screenshot`, `:module:ocrtext`, `:module:mediaextractor`. |
| `:app` | Wiring: `PowerAccessibilityService` (publishes a `ModuleHost` via `ModuleHostProvider`), the assistant services (`assistant/`), `PanelController` + `PanelScreen` (`panel/`; the controller wraps the panel in `AssistantOverlay`), and the settings UI (`settings/`: setup checklist, layout editor, appearance, OCR, Whisper assistant; `ComponentRow` is shared by the download sections). |

### Adding a module type
1. Create `module/<name>` with `plugins { id("powerpp.panel.module") }`, a `namespace`, and an empty `consumer-rules.pro` (the library convention requires one).
2. Implement `ActionModule` or `ToggleModule` with a new, never-changing `typeId`, and bind it `@IntoSet`.
3. Add `include(":module:<name>")` to `settings.gradle.kts` and `implementation(project(":module:<name>"))` to `app/build.gradle.kts`.

Nothing else changes.

### Adding an assistant command
1. Create `command/<name>` with `plugins { id("powerpp.assistant.command") }`, a `namespace`, and an empty `consumer-rules.pro`.
2. Implement `AssistantCommand` (a new, never-changing `id`; `available` says what it needs, e.g. `flowOf(true)` for nothing) and a `CommandDraft` for its modal, and bind it `@IntoSet`.
3. Add `include(":command:<name>")` to `settings.gradle.kts` and `implementation(project(":command:<name>"))` to `app/build.gradle.kts`.

The settings list, the switch and the modal pick it up automatically.

### Behavior notes
- The panel reads the config as a hot `StateFlow`, so it opens instantly and reflects edits live.
- Toggle states are collected while the panel is showing.
- Empty cells and unknown `typeId`s (a module that was removed from the build) render as blank space in the panel and as "Unavailable" in the editor. They're never deleted automatically.
- Shrinking the grid reflows overflowing modules into free cells, appending pages as needed. Nothing is dropped.
- The editor can't place modules on the panel's backdrop; tapping any non-module area of the panel dismisses it.

## 6. Build environment

**Commands:**
- `./gradlew :app:assembleDebug` builds the debug APK.
- Unit tests: `./gradlew testDebugUnitTest` (91 tests, all pass, 2026-10-12).
- **First checkout:** `git submodule update --init` fetches whisper.cpp (`third_party/whisper.cpp`, pinned to v1.9.5). The first build compiles it for three ABIs (a few minutes); later builds reuse it.
- Lint for everything: `./gradlew lintDebug`.
- Install on the device: `./gradlew :app:installDebug` (needs `adb connect <ip:port>` first).
- `./gradlew :app:lintDebug` (and `:<module>:lintDebug`) runs lint. Keep lint at zero issues.
- The single remaining Gradle deprecation warning (`Configuration.setVisible`) comes from a third-party plugin, not our scripts.
- On first build, AGP auto-installed `build-tools;36.0.0` and `platform-tools` from Google, under the licenses already accepted.

**Machines:** the user works on two Arch Linux machines (work and home, EndeavourOS at home), both set up the same way. The user requires tools from **Arch's official repos only, never the AUR**; what Arch doesn't package comes directly from Google.

**Installed and decided (2026-10-08):**
- **From Arch's repos:** `extra/jdk21-openjdk` and `extra/android-tools` (`/usr/bin/adb`). Never install with plain `pacman -S` on a stale system: Arch doesn't support partial upgrades, and at home `android-tools` failed to load `libprotobuf` until a full `pacman -Syu`.
- **JDK 21 LTS** at `/usr/lib/jvm/java-21-openjdk` (system default at home; at work the default stayed JDK 17). Gradle's daemon JVM is pinned to 21 via `gradle/gradle-daemon-jvm.properties`, and JDK auto-download is disabled (`org.gradle.java.installations.auto-download=false`), since the user allows only Arch-official tooling.
- **Android SDK** directly from Google, never the AUR. It lives in `~/Android/Sdk` (`local.properties` → `sdk.dir`, gitignored). Installed:
  - `cmdline-tools;latest` (23.0, SHA-1 verified against Google's `repository2-3.xml`)
  - `platforms;android-37.0`
  - `build-tools;37.0.0`
  - `build-tools;36.0.0` and `platform-tools` (AGP installs these on the first build if they're missing)
  - The NDK and CMake were removed at the user's request on 2026-10-08, then **installed again on 2026-10-12** for whisper.cpp (user-approved), on the work PC only so far.
    - NDK `28.2.13676358` is AGP 9.4.1's default. CMake `3.31.6`.
    - Both are pinned in `PowerBuildConfig`.
    - Installed with `~/.android/bin/android-cli --no-metrics sdk install 'ndk/28.2.13676358' 'cmake/3.31.6'`.
  - The SDK licenses were accepted with the user's approval.
  - **⚠️ Google's Android CLI telemetry:** in cmdline-tools 23.0 (found at home, 2026-10-08), `sdkmanager` is a deprecated wrapper that downloads Google's "Android CLI" to `~/.android/bin/android-cli` and runs it **with usage metrics on** (uploaded to `play.google.com/log`). Verified in its bytecode (`AndroidCLI.initMetrics`): only the per-run `--no-metrics` flag disables them; `~/.android/analytics.settings` `hasOptedIn:false` is ignored. Gradle never uses it. **Never run `sdkmanager`;** run `~/.android/bin/android-cli --no-metrics sdk …` instead.
- **Gradle:** the wrapper, pinned to **9.8.1** with `distributionSha256Sum`. It was generated from the official distribution, SHA-256 verified.
- **Versions at scaffold time:** see `gradle/libs.versions.toml` (AGP 9.4.1, Kotlin 2.4.21, KSP 2.3.12, Hilt 2.60.1, Compose BOM 2026.09.00).
- **IDE:** Android Studio is optional and up to the user. The project builds from the CLI with `./gradlew`.
- **Device testing:** the Pixel over adb (`/usr/bin/adb` from `extra/android-tools`). Ask the user for device-side information only as a last resort.
  - Each PC needs pairing once (*Wireless debugging → Pair device with pairing code*, then `adb pair`). The home PC reaches the phone over Tailscale (`100.x` address).
- **Debug signing key:** both PCs must use the **same** `~/.android/debug.keystore` (SHA-256 `7fa2fe57…d21864`), or `installDebug` can't update the app without uninstalling it, which would wipe its data and the ~170 MB OCR download. The home PC's copy came from the work PC on 2026-10-09; the home PC's own generated key is kept as `~/.android/debug.keystore.home-generated.bak`.
- Every Android build pulls AGP, AndroidX and Kotlin libraries from Google's Maven repository and Maven Central. Arch doesn't package these, so this is unavoidable.

## 7. Open questions waiting on the user

1. **PP-OCRv6 Small model:** add it as a downloadable option (XGesture recommends Small for speed; Medium stays)? Asked 2026-10-08; not answered. It needs pinning in `OcrCatalog` from PaddlePaddle's official files.
2. **Git reflog (work PC only):** should the reflog entries that still reference the pre-rewrite commits (old author email) be purged? They expire on their own (~90 days) and are local only. Asked 2026-10-08; no answer yet. (Pushing doesn't send the reflog.)

**Answered:**
- **Circle crop:** transparent corners (PNG).
- **Lock screen:** open the system power menu instead (§8).
- **Retake:** placement as in §3.5b.
- **OCR:** uses the frozen capture.
- **Auto Backup:** the user doesn't use it, but downloads stay in no-backup storage regardless.
- **OCR region selection:** reuse Smart screenshot's resizable selection; shapes row at the bottom with Retake · Confirm centred under it (2026-10-09).
- **OCR results:** only Back and × close them (2026-10-09).
- **Toasts:** none anywhere; every message goes in the overlay (or the editor) (2026-10-09).
- **Whisper assistant (2026-10-12):**
  - whisper.cpp + NDK (not ONNX Runtime).
  - base.en only.
  - Our own glyph, not a third-party logo.
  - ntfy delivers the notifications (no notifications, alarms or exact-alarm permission in Power++).
  - The ntfy delay limit is measured and enforced, with a re-check button.
  - Built in, not a module.
  - Details in §3.8.
- **OCR selection screen:** tapping outside the selection still closes it. What the user cares about is the results screen, which already ignores outside taps (2026-10-09).

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
- **OCR engine settings** (2026-10-08): CPU, 4 threads. XNNPACK crashed (§3.3).
- **OCR model in use:** PP-OCRv6 Medium; the user's XGesture Medium speed (~10 s) is the reference.
- **Visual feedback** (user, 2026-10-09): an action must open its window at once; long work shows progress in it. Messages go in the window, not toasts.
- **Media extractor** (user, 2026-10-09): a module with an Accessibility view (default) and a Scan view; Scan is pixel geometry, not OCR or a model (§3.7). Smart screenshot gets the same detection as a Scan button that only resizes the rectangle. Shared code lives in `:core:media`; no duplicated blocks across modules (user's requirement).
- **No toasts anywhere** (user, 2026-10-09): the features can only be used while an overlay is up, so their messages belong in it. Confirmations show for 1.5 s, then the window closes; failures keep it open.
- **Selection list + editing** (user, 2026-10-09): in the shared results screen, so Screen text has it too.
- **OCR results zoom** (user, 2026-10-09): pinch-zoom like a photo, the boxes zooming with the image, with Reset zoom just above the selection list.
- **Pick colours** (user, 2026-10-09): picks green, edited picks red, each with a dark- and light-mode shade that blends with the theme (§3.3), not a fixed bright colour.
- **Lock screen** (user, 2026-10-08): the panel never shows while the lock screen is up. Instead, a power long-press opens the **system power menu**, with no overlay rendered (see §2.1).
  - Observed before the fix: the assistant session *is* invoked on the lock screen even without `supportsLaunchVoiceAssistFromKeyguard`, and the accessibility overlay draws over the keyguard.
- **Whisper assistant** (user, 2026-10-12, §3.8):
  - It's built into the panel, not a module. It listens whenever the panel is open (if set up).
  - Engine: whisper.cpp with the NDK, as FUTO Keyboard does. Not ONNX Runtime: FUTO's fast short-audio models are GGML, and ONNX versions of Whisper always process 30 s.
  - Model: base.en.
  - Our own glyph.
  - Commands are separate plugins that declare their requirements; the assistant and future commands don't need ntfy.
- **Notifications through ntfy** (user, 2026-10-12): Power++ posts scheduled messages; ntfy delivers them. Power++ has no notification channel, alarm or exact-alarm permission.
  - Reminders are limited to the server's delay limit, which Power++ measures (ntfy has no API for it) and re-measures on request (user).
- **ntfy settings are saved only after a successful test post** (user); any error refuses the save. Access token only, https only.
- **Reminder priority** (user, 2026-10-12): pick it from a row of buttons in the modal, Max by default for every notification.
- **Upcoming reminders come only from ntfy** (user, 2026-10-12): Power++ stores nothing and queries the server each time the list opens. Editing and deleting go through sequence IDs; new and edited reminders use the same modal as spoken ones; a new one starts at the current date and time.
- **Reuse over duplication** (user): the OCR downloader became `:core:download`, the panel's JSON DataStore became `jsonDataStore`, the OCR component row became `ComponentRow`, and the backdrop text style moved to `:core:ui`.

## 9. XGesture reference map

Paths are relative to `~/github-clones/XGesture/app/src/main/java/com/slideindex/app/`. Read them for behavior; don't copy them.

| Feature | Where to look |
|---|---|
| Text pick: on-screen boxes, cursor, region | `overlay/FloatBallTextPick*.kt`, `overlay/RegionalPickOverlay.kt`, `overlay/FloatBallOcrRegions.kt`, `overlay/FloatBallCursorPreviewView.kt`, `overlay/OverlayAccessibility.kt` |
| Result panel: selectable words, actions | `overlay/FloatBallPickResult*.kt`, `overlay/pickresult/` |
| Smart screenshot | `overlay/screenshot/` (`SmartScreenshotOverlay.kt`, `SmartScreenshotEditor.kt`, `ScreenshotCropper.kt`, `ScreenshotStorage.kt`) |
| Screen capture | `util/ScreenCaptureHelper.kt`, `service/RegionalScreenshotOcr.kt` |
| PP-OCR inference (for comparison only; Power++ follows PaddleX, §3.3) | `vendor/ppocr-sdk/src/main/java/com/paddle/ocr/` (`PaddleOCRConfig.kt`, `engine/ORTSessionManager.kt`, `preprocess/`, `postprocess/`; paths from the repo root) |
| OCR "Recognizing…" state | `overlay/pickresult/PickResultInteractiveText.kt` (`PickResultOcrLoadingBody`) |
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
- **OCR text** (§3.3), 2026-10-09: resizable selection, progress, results over the frozen capture, the selection list with editing (the keyboard appears in compact mode), the 3-entry list, pinch-zoom and Reset zoom.
- **Lint:** zero issues across modules. All unit tests pass.

**Session 3 (2026-10-09):** in-window messages replace every toast (Smart screenshot, its editor, Screen text, the "Copied" confirmation on API ≤ 32); the unused first-design OCR strings (`ocr_hint_cursor`, `ocr_hint_drag`, `ocr_reset`) were removed. Built, unit-tested, lint clean and installed on the phone; **not yet checked by the user on-device.** Then Media extractor and Smart screenshot's Scan (§3.7), with the shared `:core:media`, `OverlayBar` family and `forEachScreenNode`. Screen text now reads nodes through `forEachScreenNode`, and OCR text's selection screen uses the `OverlayBar` family (no behaviour change intended). `TextPickScreen`'s toolbar and Smart screenshot's floating toolbars still draw their own pills. Same status: built, tested, lint clean, installed, not yet checked by the user.

**Session 4 (2026-10-12):** the Whisper assistant, ntfy and Reminders (§3.8), with the shared `:core:download`, `jsonDataStore`, `ComponentRow` and `OverlayWindow.setCompact` dropping the backdrop. Built, 88 unit tests passing, lint at zero issues, installed on the phone. Read-only checks: the native libraries were extracted, all 7 ggml CPU variants are present, and the service process runs with no crashes. **Confirmed working by the user** (the assistant and a spoken reminder). Then Upcoming reminders: built, 90 tests, lint clean, installed, not yet tried. Also needs re-checking: the OCR downloads (moved onto `ComponentStore`) and OCR's edit card (`setCompact` changed).

**Not verified on-device since the 2026-10-09 refactor:** Smart screenshot after moving its selection to `:core:selection` and Retake to `:core:capture` (the behaviour should be unchanged). Screen text's new list and editing.

**Remote:** `origin` = `git@github.com:Bo0sted/PowerPlusPlus.git` (SSH, private repo `github.com/Bo0sted/PowerPlusPlus`). `main` tracks `origin/main`; first pushed 2026-10-08.
- Session 2's commits (`ecbd612` onward) were pushed by the user on 2026-10-09. Session 3's commits (`dafe3fc` to `44c90f9`) were pushed by the user on 2026-10-10.
- The user's SSH key (`~/.ssh/github`) has a passphrase, so the agent can't push from its own shell (batch mode). Run `git push` in the user's terminal panel, where the user enters the passphrase themselves. Push only when the user says so.

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
- **Blur-behind must be paired with a non-zero dim** (`FLAG_DIM_BEHIND`, dimAmount 0.2; 0.4 when blur is unavailable), as in the AOSP window-blurs guide (source.android.com/docs/core/display/window-blurs).
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

**Keyboard and accessibility overlays** (AOSP `main`, 2026-10-09):
- `WindowManagerPolicy.getWindowLayerFromTypeLw`: `TYPE_INPUT_METHOD` is layer 13, `TYPE_ACCESSIBILITY_OVERLAY` is layer 31, so the keyboard is drawn **under** our overlays.
- `WindowState.canBeImeTarget` doesn't exclude accessibility overlays, so a focusable overlay can take text input.
- `DisplayContent.shouldImeAttachedToApp` lifts the keyboard above its target only when the target has an `ActivityRecord`, so it is never lifted above an overlay.
- Hence `OverlayWindow.setCompact` while editing. Confirmed working on-device.

**Media class names in the accessibility tree** (2026-10-09; AOSP `main`, androidx-main, Chromium `main`, Firefox `main`):
- `ImageView.getAccessibilityClassName` → `android.widget.ImageView`; `VideoView` → `android.widget.VideoView`. `SurfaceView` and `TextureView` don't override it, so they report `View.getAccessibilityClassName` = `android.view.View`.
- Compose: `Role.Image.toLegacyClassName()` = `android.widget.ImageView` (`SemanticsUtils.android.kt`), but foundation's `Image` sets `Role.Image` only when `contentDescription != null` (`Image.kt`), and the delegate applies the class only to leaf or merging nodes.
- Chrome/WebView: images are `android.widget.Image` (`kAXImageClassname`); clickable images `android.widget.ImageButton` (`BrowserAccessibilityAndroid::GetClassName`).
- Firefox (GeckoView): `CLASSNAMES[CLASSNAME_IMAGE]` = `android.widget.Image` (`SessionAccessibility.java`), used for the roles GRAPHIC, DIAGRAM, CANVAS, IMAGE_MAP and MATHML_GLYPH (`accessible/base/RoleMap.inc`).

**ONNX Runtime 1.30 Java:** on Android, `OnnxRuntime.init()` only `loadLibrary`s `onnxruntime4j_jni` (see §4).
- API checked in the pinned AAR: `OnnxTensor.createTensor(env, FloatBuffer, long[])`, `OrtSession.run(Map)`, `SessionOptions.setIntraOpNumThreads`/`addXnnpack`/`addConfigEntry`.
- The engine includes the XNNPACK and NNAPI providers. XNNPACK crashes with PP-OCRv6 Medium (§3.3).

**Android SDK tools:** `sdkmanager` (cmdline-tools 23.0) runs Google's Android CLI with metrics on (§6).

**Libraries:**
- core-ktx 1.19.1 is an empty shim: the KTX extensions (e.g. `Bitmap.scale`, which wraps `createScaledBitmap`) now live in `androidx.core:core`.
- `hiltViewModel()` is in package `androidx.hilt.lifecycle.viewmodel.compose` (artifact `hilt-lifecycle-viewmodel-compose` 1.4.0).
- AGP 9.4 requires Gradle ≥ 9.6.0.
- Hilt Gradle plugin ≥ 2.59 requires AGP 9.
- KSP 2.3.10+ fixed AGP 9 built-in Kotlin.
- Compose still needs `buildFeatures.compose = true`.

**ntfy server** (source at `github.com/binwiederhier/ntfy`, `main` of 2026-10-12, `server/server.go`, `server/errors.go`, `util/time.go`):
- **Scheduled delivery:**
  - `X-Delay` (aliases `Delay`, `X-At`, `At`, `X-In`, `In`; JSON `delay`) takes Unix seconds, durations (`time.ParseDuration`, or n + s/m/h/d), RFC 3339 or natural language.
  - Minimum `message-delay-min` 10 s; maximum `message-delay-limit`, default 3 days (`DefaultMessageDelayMax`).
  - No API exposes the limit.
- **Validation order in `parsePublishParams`:** sequence ID → … → tags → **delay** (40005 too small, 40006 too large) → **actions** (40018 invalid). The rejected request is never stored.
- **JSON publishing:** `POST /` with `topic`, `message`, `title`, `tags`, `delay`, `sequence_id`, …. It's converted into the same headers internally, so UTF-8 titles are safe.
- **Sequence IDs:**
  - Match the topic rule `^[-_A-Za-z0-9]{1,64}$`.
  - Publishing with an existing sequence ID deletes the scheduled message with that ID first (`DeleteScheduledBySequenceID`).
  - `DELETE /<topic>/<sequence_id>` cancels one.
- **Reading scheduled messages back:** `GET /<topic>/json?poll=1&sched=1&since=<unix>` (aliases `sched`/`scheduled`) returns cached messages with `time >= since`, including unpublished (scheduled) ones. That's the SQLite/Postgres `…IncludeScheduled` query. They're NDJSON objects with `event`, `time`, `message`, `title`, `sequence_id` (omitted if equal to the ID), sorted by time. A large topic may be cut to the newest (`X-Messages-Truncated`).
- `DELETE /<topic>/<sequence_id>` (or `GET …/<sequence_id>/delete`) needs write access. It removes a scheduled message and sends a `message_delete` event to subscribers.
- **The delay limit can't be turned off:** `message-delay-limit` in `server.yml` (env `NTFY_MESSAGE_DELAY_LIMIT`) is a duration such as `"3650d"`, and the check is always applied. A scheduled message's expiry counts from its delivery time (`m.Time` + the cache duration), so long delays are safe. Power++ measures at most 10 years.
- Errors are JSON `{"code", "http", "error", "link"}`. Access tokens are sent as `Authorization: Bearer tk_…`.

**whisper.cpp v1.9.5 and ggml** (`third_party/whisper.cpp`):
- `whisper_full_params.audio_ctx` ("overwrite the audio context size"), `suppress_nst`, `single_segment`, `no_timestamps`.
- whisper.cpp doesn't load backends itself; with `GGML_BACKEND_DL`, call `ggml_backend_load_all_from_path` first.
  - `ggml_backend_load_best` enumerates a directory with `std::filesystem` and scores each `libggml-cpu-*.so` through its `ggml_backend_score`.
  - So the libraries must be real files (legacy packaging).
- `ggml/src/CMakeLists.txt` has an Android-specific variant list under `GGML_CPU_ALL_VARIANTS`.
- AGP packages MODULE libraries of every built target. Its `targets` filter keeps only linked dependencies, so it would drop the backends.

**Microphone from the accessibility service** (AOSP `main`):
- `AccessibilityServiceConnection` binds the service with `BIND_INCLUDE_CAPABILITIES` (plus `BIND_FOREGROUND_SERVICE_WHILE_AWAKE`).
- `OomAdjuster` passes the client's (system_server's) while-in-use capabilities through that flag.
- So the process should be allowed to record without a foreground service. **Unconfirmed on-device.**

**FUTO Keyboard** (`github.com/futo-org/android-keyboard`, FUTO Source First License 1.1-kb; read for behaviour only):
- Voice input is its own copy of whisper.cpp (`native/jni/src/ggml/`, `org_futo_voiceinput_WhisperGGML`).
- Models: `voiceinput-shared/.../Models.kt`. tiny.en is bundled; base.en/small.en download with SHA-256 pins.
- `audio_ctx = clamp(ceil(n/320)+32, 160, 1500)`.

## 11b. Open technical questions

- ~~Is `takeScreenshot` fast enough?~~ Yes: 9–32 ms (§3.5).
- ~~What detection input size?~~ Follow PaddleX's pipeline (§3.3); measured times are in §3.3.
- What does the Pixel's power long-press pass in `onShow` args (`invocation_type`)? Not needed yet; would allow distinguishing the power button from other assistant gestures.

## 12. Known risks and potential bugs

- Battery saver turns off cross-window blur. The overlay must switch to dim live, not only on the next open.
- On Android 13+, accessibility for sideloaded apps sits behind "restricted settings". Onboarding must walk the user through *App info → ⋮ → Allow restricted settings*.
- The Digital assistant role replaces Gemini/Assistant, so "Hey Google" stops working. This is accepted.
- In OCR's fixed bottom rows, a selection reaching the bottom of the screen (e.g. after Max) has its bottom handles under the toolbars. It can still be resized from the top or moved up.
- **Microphone from the accessibility service** is reasoned from AOSP, not yet seen working (§11). If recording fails (silence or `AudioRecord` not initialized), the fallback is a microphone-type foreground service, which needs `FOREGROUND_SERVICE_MICROPHONE` and while-in-use eligibility.
- **Upcoming reminders exist only in the ntfy server's message cache.** Without a `cache-file`, ntfy keeps it in memory, so a server restart drops every scheduled reminder (they would never be delivered either). Self-hosted servers should set `cache-file`.
- **The ntfy delay probe** relies on the server checking the delay before the actions (true in current ntfy). On a server where that order differs, every probe would look "accepted" and the limit would read as 10 years; scheduling past the real limit would then fail on Confirm with the server's error.
- **Transcripts are logged** (`PowerPP.Assistant`, info level) to help tune the parser and endpointing. Logcat is readable only over adb or by system apps. Drop the line once the assistant is tuned.
- **Listening starts every time the panel opens** (when set up), so Android's microphone indicator shows each time. That's by design (user).
- **The modal on small screens:** M3's `DatePicker` needs about 360 dp of width. The card leaves 376 dp on the Pixel 9 Pro XL, but would clip on 360 dp-wide phones.
- The system may remove `TYPE_ACCESSIBILITY_OVERLAY` windows after screen-off or lock (XGesture's `FloatBallOverlay.kt` comments on this). The overlay must handle being removed externally.
