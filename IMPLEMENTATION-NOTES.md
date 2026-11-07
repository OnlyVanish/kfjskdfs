# PinBom Android – implementation notes

## Changes

- Added ACTION_SEND and ACTION_SEND_MULTIPLE `image/*` share targets while retaining the existing launcher and `pinbom://` / `bumo://` VIEW filters. The received content URI is MIME-checked and copied to private cache with a strict 10 MiB stream limit; multi-share uses the first URI and displays a localized notice.
- The cached image path is handed to Create Pin and persisted across authentication/setup. Upload success/cancellation and leaving/cancelling the Create screen clean up temporary imports. Cache cleanup on app startup removes old `share_target` and `share_images` files older than 24 hours.
- Added image sharing from pin detail: resolves the `large` variant through the central `imageUrl` function/downloader helper, overlays the `ic_pinbom` logo and semi-transparent brand label, JPEG-encodes at quality 90, and shares using a FileProvider read grant/chooser.
- Added interests onboarding/editing, selection rules (3–10), DataStore completion flag (existing installs default to complete), and bilingual resources.
- Added data saver image variants, unmetered-upload constraint and waiting status, cache size/clear, notification type preferences, link to system notification settings when permission is denied, active-session screens and API calls.
- Login/register requests include a `X-Device-Name` header derived from manufacturer/model.
- No dependencies were added. New routes are additive; existing destination values and deep-link patterns remain unchanged.

## Build and tests

From this directory on a machine with the configured Android SDK and network access:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

or on macOS/Linux:

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

The wrapper distribution is Gradle 9.6.0. The current environment cannot resolve `services.gradle.org` to download it, so the Gradle unit-test task could not run here. Static verification succeeded: XML files parse, both locales contain the same 276 resource keys, all Kotlin/Java `R.string` references are declared, and the manifest/resource XML is well-formed.

## Manual test checklist

1. In Gallery, Telegram, and Rubika, share one JPEG/PNG to PinBom. The Create Pin screen should appear with the image. Repeat from a logged-out install: after login (and required setup), the same image should be present.
2. Share multiple images: only the first must be prefilled and a localized notice must explain the limit. Try a non-image or image over 10 MiB: a suitable localized error should appear and no partial cache file should remain.
3. Cancel/remove the imported image and leave Create Pin; check its `cache/share_target` file is deleted. Publish successfully and confirm the source is deleted after upload completes.
4. Open a pin detail screen, select “Send image”, wait for the chooser and inspect the result: it must be the image file, with small lower-right PinBom mark, not a text URL. Test both languages and a small source image.
5. Register a new account: interest selection appears only after initial setup. Continue remains disabled below three choices; 3–10 succeeds; Skip dismisses it. Re-open it from Settings and verify the chosen interests load.
6. Toggle Data Saver and inspect network image paths: cards use `_small`, detail uses `_medium`, while Send Image requests `_large`. Toggle upload restriction: a queued upload should wait with the localized Wi-Fi message until an unmetered network is available. Confirm off mode permits ordinary connected networks.
7. Check reported cache size, clear with confirmation, and verify sign-in/account data remains. Toggle likes/comments/follows independently and validate system notification permission settings link on Android 13+.
8. Open Settings → Active devices with two clients logged in. Verify the current device label, relative last-active time and masked IP; revoke one device, then revoke others. The revoked client should return to login on its next authenticated use.

## Assumptions/limits

- “Wi-Fi only” uses Android WorkManager's `NetworkType.UNMETERED`, as requested; unmetered networks other than Wi-Fi can also satisfy that Android constraint.
- Old installations default the interest-onboarding flag to complete, so the selection screen is shown only to newly registered users or when opened manually from Settings.
- Notifications remain polled (the server has no push service); category filters affect local system notifications only, not the Notifications page feed.
- The configured server remains plain HTTP at its existing IP URL; no domain, public share URL, or TLS assumption was introduced.
