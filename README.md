# Auto Math Solver — Android (Java)

Source project for a portrait arithmetic game with four colored answer buttons, matching the user's sample screenshot (417×695). No root, cloud API, or AI key is used. Uses bundled on-device Google ML Kit Latin OCR 16.0.1 (no model download required) and the Android Accessibility gesture API.

## Quickly get an APK with GitHub

1. Extract the ZIP. Upload the **CONTENTS** of `auto-math-solver` to the root of a new GitHub repository. Include hidden `.github/` directory.
2. Open **Actions → Build and smoke-test Android APK → Run workflow**. A push also triggers build.
3. Wait for the `build` job. It runs lint, unit tests, builds APK, validates its actual package metadata and cryptographic signature. `emulator-smoke` installs and opens the APK in Android Emulator API 29.
4. Download artifact **AutoMathSolver-debug-apk** from the workflow run. It contains `app-debug.apk` (Android 8.0+).

**SDK installation error fix:** The workflow does **not** execute `sdkmanager tools`. That obsolete SDK package no longer exists. It installs `platform-tools`, `platforms;android-35`, and `build-tools;35.0.0` only. Gradle 8.9 is installed by `gradle/actions/setup-gradle`; a Gradle Wrapper JAR is not required and is intentionally not included. If building locally, install JDK 17, Android SDK API 35, build tools 35.0.0, Gradle 8.9, then execute `gradle :app:assembleDebug` from project root.

## Fix for AAPT resource linking error (`android:flags`)

**Fixed in this ZIP:** `app/src/main/res/xml/accessibility_config.xml` now declares
`android:accessibilityFlags="flagReportViewIds"` instead of the invalid
`android:flags="flagReportViewIds"`. The invalid XML attribute caused
`:app:processDebugResources FAILED` before Java compilation. The build workflow
runs `python3 scripts/check-project.py` first to catch this regression.

## Fix for Java compilation error (`setTextAllCaps`)

**Fixed:** `MainActivity.java` creates a platform `android.widget.Button`,
which inherits `setAllCaps(boolean)` from `TextView`. It does not have
`setTextAllCaps(boolean)` (the latter caused `:app:compileDebugJavaWithJavac FAILED`
at line 92). Replaced the invalid call with `b.setAllCaps(false)`. The
pre-build check now detects accidental reuse of the invalid method.

## Device setup / operation

1. Launch `Auto Math Solver`.
2. Tap **CLICK** and enable `Auto Math Solver` under Android Accessibility settings.
3. Tap **FLOAT** and allow display over other apps.
4. You can optionally run **TEST ẢNH MẪU** on the first screen. This uses real bundled ML Kit OCR against the included user-provided screenshot and displays detected text and the proposed answer. It needs no screen capture permission.
5. Tap **START** in app main screen; Android will ask consent for screen sharing. Pick **entire screen** on Android versions that show a capture-region choice. A floating bar appears.
6. Open the arithmetic game. Drag the bar by its **⠿ handle** to a blank area (ideally near top). Tap **▶** on the bar to auto-solve and auto-tap.
7. Tap **■** to STOP immediately (stale OCR callbacks cannot tap after STOP). Tap **×** to close overlay and end screen capture. The app STOP button also stops the service.

UI shows CLICK / FLOAT / CAPTURE availability; permissions are requested through official Android dialogs. Android 13+ notifications may be requested for the ongoing capture foreground service.

## Technical approach

- The app tries to read real UI text nodes via Accessibility first. Most games render text on Canvas/OpenGL, so it also captures the full screen using MediaProjection and performs **on-device OCR**.
- A single in-flight OCR operation at a time; newest screenshots replace obsolete frames. Minimum frame interval about 90ms, but OCR and device rendering time can be longer. No realistic zero-latency guarantee.
- Recognizes integer addition, subtraction, multiplication (×, x, *), exact integer division (÷, /). Unsupported/non-integer or ambiguous calculations are skipped.
- Four answer positions currently match the screenshot: top left ≈(30.5%,68.5%), top right ≈(70.5%,68.5%), bottom left ≈(30.5%,85%), bottom right ≈(70.5%,85%) of the captured portrait screen. Each OCR text element is associated to the nearest button area; **never guesses** if the result is unreadable or two matching answers appear.
- Prevents rapid repeat taps on unchanged screen, combines conservative delay and question fingerprint. STOP increments a generation counter to invalidate pending OCR tasks.
- Input works only if the game is visible and Android allows capturing its pixels; DRM/FLAG_SECURE protection, device-specific MediaProjection restrictions, or significant changes to layout can prevent solving.

## Tests and limits

- `ArithmeticTest` tests arithmetic cases, invalid data, and placement for the actual screenshot's coordinates using synthetic OCR tokens.
- The build workflow verifies package validity, minimum API, debug signature with `apksigner verify`, then installs/launches it in an emulator. This is stronger than checking whether an APK file exists, but the emulator launch smoke test **does not** prove runtime OCR, overlay permission, game recognition, or gesture injection are correct. Those require a user-authorized session on a real device/game.
- **No APK/device test was performed while creating this ZIP** because this environment does not have Android SDK, Gradle or an Android emulator. Build and launch checks execute only after you push this project to GitHub Actions.
- The app is tailored to the shown game and **not** a universal solver for arbitrary layouts. If other UI / sizes change position of answer buttons, `OcrLayout.java` requires adaptation.

## Privacy / security

MediaProjection permission is granted by Android for the current session, not hidden or bypassed. No network permission is requested and captured images are not transmitted or persisted. The foreground service displays an Android notification; on-screen bar shows STOP and CLOSE.
